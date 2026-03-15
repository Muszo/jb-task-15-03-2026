package org.example.terminal;

import java.io.*;

/**
 * A simple terminal emulator that spawns a shell process, pipes its output
 * through the {@link AnsiParser} into a {@link TerminalBuffer}, and renders
 * the buffer to the console.
 * <p>
 * This is a minimal demonstration — it does not handle raw-mode input or
 * full PTY allocation (which requires native code). It works well for
 * non-interactive commands and simple interactive use.
 * <p>
 * <b>Not thread-safe.</b> The parser and buffer are mutated by the reader thread
 * in {@link #runCommand}; do not access the buffer concurrently during command execution.
 */
public class TerminalEmulator {

    private final TerminalBuffer buffer;
    private final AnsiParser parser;

    public TerminalEmulator(int width, int height) {
        this.buffer = new TerminalBuffer(width, height, 5000);
        this.parser = new AnsiParser(buffer);
    }

    public TerminalBuffer getBuffer() {
        return buffer;
    }

    public AnsiParser getParser() {
        return parser;
    }

    /**
     * Runs a command, processes its output through the ANSI parser, and returns
     * the final buffer state as a string.
     */
    public String runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        // Tell programs about our terminal size
        pb.environment().put("COLUMNS", String.valueOf(buffer.getWidth()));
        pb.environment().put("LINES", String.valueOf(buffer.getHeight()));
        // Force color output in many tools
        pb.environment().put("TERM", "xterm-256color");

        Process process = pb.start();

        // Close stdin — we don't send input; prevents programs from blocking on read
        process.getOutputStream().close();

        // Read output in a thread
        Thread reader = new Thread(() -> {
            try (InputStream is = process.getInputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    // Simulate PTY ONLCR: translate bare \n to \r\n.
                    // A real PTY driver does this automatically; since we use
                    // ProcessBuilder (no PTY), we must do it ourselves.
                    String chunk = new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8);
                    chunk = translateNewlines(chunk);
                    parser.feed(chunk);
                }
            } catch (IOException e) {
                // Process ended
            }
        }, "terminal-reader");
        reader.start();

        process.waitFor();
        reader.join();

        return buffer.getScreenContent();
    }

    /**
     * Renders the current buffer state to stdout, including a border frame.
     */
    public void renderToConsole() {
        int width = buffer.getWidth();
        int height = buffer.getHeight();
        // Top border
        System.out.print("┌");
        for (int i = 0; i < width; i++) System.out.print("─");
        System.out.println("┐");

        // Screen rows
        for (int row = 0; row < height; row++) {
            System.out.print("│");
            String text = buffer.getScreenLine(row);
            // Pad to width
            StringBuilder sb = new StringBuilder(text);
            while (sb.length() < width) sb.append(' ');
            System.out.print(sb.substring(0, width));
            System.out.println("│");
        }

        // Bottom border
        System.out.print("└");
        for (int i = 0; i < width; i++) System.out.print("─");
        System.out.println("┘");

        // Status line
        System.out.printf(" Cursor: (%d, %d)  Scrollback: %d lines%n",
                buffer.getCursorColumn(), buffer.getCursorRow(), buffer.getScrollbackSize());
    }

    /**
     * Renders the buffer with ANSI colors to stdout (for terminals that support it).
     */
    public void renderToConsoleWithColors() {
        int width = buffer.getWidth();
        int height = buffer.getHeight();
        // Top border
        System.out.print("┌");
        for (int i = 0; i < width; i++) System.out.print("─");
        System.out.println("┐");

        for (int row = 0; row < height; row++) {
            System.out.print("│");
            for (int col = 0; col < width; col++) {
                char ch = buffer.getCharAt(col, row);
                CellAttributes attrs = buffer.getAttributesAt(col, row);

                // Build ANSI SGR sequence for this cell
                String sgr = buildAnsiSgr(attrs);
                if (!sgr.isEmpty()) System.out.print(sgr);

                System.out.print(ch == '\0' ? ' ' : ch);

                if (!sgr.isEmpty()) System.out.print("\u001b[0m"); // reset
            }
            System.out.println("│");
        }

        // Bottom border
        System.out.print("└");
        for (int i = 0; i < width; i++) System.out.print("─");
        System.out.println("┘");

        System.out.printf(" Cursor: (%d, %d)  Scrollback: %d lines%n",
                buffer.getCursorColumn(), buffer.getCursorRow(), buffer.getScrollbackSize());
    }

    private String buildAnsiSgr(CellAttributes attrs) {
        if (attrs.equals(CellAttributes.DEFAULT)) return "";

        StringBuilder sgr = new StringBuilder("\u001b[");
        boolean first = true;

        if (attrs.hasStyle(Style.BOLD))      { if (!first) sgr.append(';'); sgr.append('1'); first = false; }
        if (attrs.hasStyle(Style.ITALIC))    { if (!first) sgr.append(';'); sgr.append('3'); first = false; }
        if (attrs.hasStyle(Style.UNDERLINE)) { if (!first) sgr.append(';'); sgr.append('4'); first = false; }

        int fgCode = colorToAnsiFg(attrs.getForeground());
        if (fgCode >= 0) { if (!first) sgr.append(';'); sgr.append(fgCode); first = false; }

        int bgCode = colorToAnsiBg(attrs.getBackground());
        if (bgCode >= 0) { if (!first) sgr.append(';'); sgr.append(bgCode); first = false; }

        if (first) return ""; // nothing to set
        sgr.append('m');
        return sgr.toString();
    }

    private int colorToAnsiFg(Color c) {
        return switch (c) {
            case BLACK -> 30; case RED -> 31; case GREEN -> 32; case YELLOW -> 33;
            case BLUE -> 34; case MAGENTA -> 35; case CYAN -> 36; case WHITE -> 37;
            case BRIGHT_BLACK -> 90; case BRIGHT_RED -> 91; case BRIGHT_GREEN -> 92;
            case BRIGHT_YELLOW -> 93; case BRIGHT_BLUE -> 94; case BRIGHT_MAGENTA -> 95;
            case BRIGHT_CYAN -> 96; case BRIGHT_WHITE -> 97;
            case DEFAULT -> -1;
        };
    }

    private int colorToAnsiBg(Color c) {
        return switch (c) {
            case BLACK -> 40; case RED -> 41; case GREEN -> 42; case YELLOW -> 43;
            case BLUE -> 44; case MAGENTA -> 45; case CYAN -> 46; case WHITE -> 47;
            case BRIGHT_BLACK -> 100; case BRIGHT_RED -> 101; case BRIGHT_GREEN -> 102;
            case BRIGHT_YELLOW -> 103; case BRIGHT_BLUE -> 104; case BRIGHT_MAGENTA -> 105;
            case BRIGHT_CYAN -> 106; case BRIGHT_WHITE -> 107;
            case DEFAULT -> -1;
        };
    }

    /**
     * Simulates the PTY ONLCR (Output New Line Carriage Return) setting:
     * translates every bare {@code \n} (not preceded by {@code \r}) into
     * {@code \r\n}. Already-correct {@code \r\n} pairs are left untouched.
     */
    private static String translateNewlines(String data) {
        StringBuilder sb = new StringBuilder(data.length() + 32);
        for (int i = 0; i < data.length(); i++) {
            char ch = data.charAt(i);
            if (ch == '\n') {
                // Only insert \r if not already preceded by one
                if (i == 0 || data.charAt(i - 1) != '\r') {
                    sb.append('\r');
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }
}
