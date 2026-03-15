package org.example.terminal;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

/**
 * Parses raw terminal output (including ANSI/VT100 escape sequences) and
 * translates them into {@link TerminalBuffer} operations.
 * <p>
 * Supported sequences:
 * <ul>
 *   <li>CSI n S — Scroll Up</li>
 *   <li>CSI n T — Scroll Down</li>
 *   <li>CSI n B — Cursor Down</li>
 *   <li>CSI n C — Cursor Forward</li>
 *   <li>CSI n D — Cursor Back</li>
 *   <li>CSI row ; col H / f — Cursor Position (1-based)</li>
 *   <li>CSI n J — Erase in Display</li>
 *   <li>CSI n K — Erase in Line</li>
 *   <li>CSI n m — Select Graphic Rendition (SGR)</li>
 *   <li>CSI n @ — Insert blank characters</li>
 *   <li>CSI n P — Delete characters</li>
 *   <li>Control chars: \n, \r, \t, \b, BEL</li>
 *   <li>OSC (Operating System Command) — consumed/ignored</li>
 * </ul>
 */
public class AnsiParser {

    private enum State {
        GROUND,
        ESCAPE,          // saw ESC
        CSI_PARAMS,      // saw ESC [, collecting parameters
        OSC,             // Operating System Command — skip until ST
        CHARSET_SELECT   // saw ESC ( or ESC ) — consume the next byte and ignore
    }

    private final TerminalBuffer buffer;
    private State state = State.GROUND;
    private final StringBuilder paramBuffer = new StringBuilder();

    public AnsiParser(TerminalBuffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Feeds raw terminal output into the parser.
     * The parser processes each character, updating the buffer accordingly.
     */
    public void feed(String data) {
        for (int i = 0; i < data.length(); i++) {
            char ch = data.charAt(i);
            switch (state) {
                case GROUND -> handleGround(ch);
                case ESCAPE -> handleEscape(ch);
                case CSI_PARAMS -> handleCsiParam(ch);
                case OSC -> handleOsc(ch);
                case CHARSET_SELECT -> {
                    // Consume the character set designator byte (e.g., 'B' for ASCII)
                    // and return to ground — the byte is intentionally discarded.
                    state = State.GROUND;
                }
            }
        }
    }

    /** Feed a single byte array (e.g. from process output), decoded as UTF-8. */
    public void feed(byte[] data, int offset, int length) {
        feed(new String(data, offset, length, StandardCharsets.UTF_8));
    }

    private void handleGround(char ch) {
        switch (ch) {
            case '\u001b' -> state = State.ESCAPE;    // ESC
            case '\n'     -> buffer.newLine();           // LF — line feed only (no CR)
            case '\r'     -> buffer.carriageReturn();    // CR — carriage return
            case '\t'     -> buffer.tab();
            case '\b'     -> buffer.backspace();
            case '\u0007' -> { /* ignore */ }
            default -> {
                if (ch >= ' ') {
                    buffer.writeChar(ch);
                }
                // Ignore other control chars
            }
        }
    }

    private void handleEscape(char ch) {
        switch (ch) {
            case '[' -> {
                // CSI — Control Sequence Introducer
                paramBuffer.setLength(0);
                state = State.CSI_PARAMS;
            }
            case ']' -> {
                // OSC — Operating System Command
                paramBuffer.setLength(0);
                state = State.OSC;
            }
            case '(' , ')' -> {
                // Designate character set — the next byte is the charset designator
                state = State.CHARSET_SELECT;
            }
            case '=' , '>' -> {
                // Keypad modes — ignore
                state = State.GROUND;
            }
            case 'M' -> {
                // Reverse Index — move cursor up, scroll down if at top
                if (buffer.getCursorRow() > 0) {
                    buffer.moveCursorUp(1);
                } else {
                    // At top row — scroll screen down (insert blank line at top)
                    buffer.insertNewLineAtTop();
                }
                state = State.GROUND;
            }
            case 'D' -> {
                // Index — move cursor down, scroll up if at bottom
                buffer.newLine();
                state = State.GROUND;
            }
            case 'E' -> {
                // Next line
                buffer.carriageReturn();
                buffer.newLine();
                state = State.GROUND;
            }
            case '7' -> {
                // Save cursor — simplified: ignore
                state = State.GROUND;
            }
            case '8' -> {
                // Restore cursor — simplified: ignore
                state = State.GROUND;
            }
            default -> {
                // Unknown escape — drop back to ground
                state = State.GROUND;
            }
        }
    }

    private void handleCsiParam(char ch) {
        if ((ch >= '0' && ch <= '9') || ch == ';' || ch == '?') {
            // Parameter characters — accumulate
            paramBuffer.append(ch);
        } else {
            // Final character — dispatch
            dispatchCsi(ch, paramBuffer.toString());
            state = State.GROUND;
        }
    }

    private void handleOsc(char ch) {
        // OSC terminates with BEL (\x07) or ST (ESC \)
        if (ch == '\u0007') {
            state = State.GROUND;
        } else if (ch == '\u001b') {
            // Start of ESC \ (ST) — transition to ESCAPE state so the '\'
            // is handled there (falls through as unknown escape, returning to GROUND).
            state = State.ESCAPE;
        }
        // Otherwise consume and ignore
    }



    private void dispatchCsi(char command, String params) {
        // Strip leading '?' for private mode sequences
        String cleanParams = params.startsWith("?") ? params.substring(1) : params;

        switch (command) {
            case 'A' -> {
                // Cursor Up
                int n = getParam(cleanParams, 0, 1);
                buffer.moveCursorUp(n);
            }
            case 'B' -> {
                // Cursor Down
                int n = getParam(cleanParams, 0, 1);
                buffer.moveCursorDown(n);
            }
            case 'C' -> {
                // Cursor Forward (Right)
                int n = getParam(cleanParams, 0, 1);
                buffer.moveCursorRight(n);
            }
            case 'D' -> {
                // Cursor Back (Left)
                int n = getParam(cleanParams, 0, 1);
                buffer.moveCursorLeft(n);
            }
            case 'H', 'f' -> {
                // Cursor Position (1-based params → 0-based buffer)
                int row = getParam(cleanParams, 0, 1) - 1;
                int col = getParam(cleanParams, 1, 1) - 1;
                buffer.setCursorPosition(col, row);
            }
            case 'J' -> {
                // Erase in Display
                int mode = getParam(cleanParams, 0, 0);
                buffer.eraseInDisplay(mode);
            }
            case 'K' -> {
                // Erase in Line
                int mode = getParam(cleanParams, 0, 0);
                buffer.eraseInLine(mode);
            }
            case 'm' -> {
                // SGR — Select Graphic Rendition
                handleSgr(cleanParams);
            }
            case '@' -> {
                // Insert blank characters
                int n = getParam(cleanParams, 0, 1);
                buffer.insertBlanks(n);
            }
            case 'P' -> {
                // Delete characters
                int n = getParam(cleanParams, 0, 1);
                buffer.deleteChars(n);
            }
            case 'G' -> {
                // Cursor Horizontal Absolute (1-based)
                int col = getParam(cleanParams, 0, 1) - 1;
                buffer.setCursorPosition(col, buffer.getCursorRow());
            }
            case 'd' -> {
                // Cursor Vertical Absolute (1-based)
                int row = getParam(cleanParams, 0, 1) - 1;
                buffer.setCursorPosition(buffer.getCursorColumn(), row);
            }
            case 'E' -> {
                // Cursor Next Line
                int n = getParam(cleanParams, 0, 1);
                buffer.moveCursorDown(n);
                buffer.carriageReturn();
            }
            case 'F' -> {
                // Cursor Previous Line
                int n = getParam(cleanParams, 0, 1);
                buffer.moveCursorUp(n);
                buffer.carriageReturn();
            }
            case 'h', 'l' -> {
                // Set/Reset mode — ignore (private modes like ?25h cursor visibility)
            }
            case 'r' -> {
                // Set scrolling region — ignore for now
            }
            case 'S' -> {
                // Scroll Up
                int n = getParam(cleanParams, 0, 1);
                for (int i = 0; i < n; i++) {
                    buffer.insertNewLineAtBottom();
                }
            }
            case 'T' -> {
                // Scroll Down
                int n = getParam(cleanParams, 0, 1);
                for (int i = 0; i < n; i++) {
                    buffer.insertNewLineAtTop();
                }
            }
            default -> {
                // Unknown CSI — ignore
            }
        }
    }


    private void handleSgr(String params) {
        int[] codes = parseSgrCodes(params);

        Color fg = buffer.getCurrentAttributes().getForeground();
        Color bg = buffer.getCurrentAttributes().getBackground();
        Set<Style> currentStyles = buffer.getCurrentAttributes().getStyles();
        Set<Style> styles = currentStyles.isEmpty()
                ? EnumSet.noneOf(Style.class)
                : EnumSet.copyOf(currentStyles);

        for (int i = 0; i < codes.length; i++) {
            int code = codes[i];
            switch (code) {
                case 0 -> {
                    // Reset
                    fg = Color.DEFAULT;
                    bg = Color.DEFAULT;
                    styles.clear();
                }
                case 1 -> styles.add(Style.BOLD);
                case 3 -> styles.add(Style.ITALIC);
                case 4 -> styles.add(Style.UNDERLINE);
                case 22 -> styles.remove(Style.BOLD);
                case 23 -> styles.remove(Style.ITALIC);
                case 24 -> styles.remove(Style.UNDERLINE);

                // Standard foreground colors (30–37)
                case 30 -> fg = Color.BLACK;
                case 31 -> fg = Color.RED;
                case 32 -> fg = Color.GREEN;
                case 33 -> fg = Color.YELLOW;
                case 34 -> fg = Color.BLUE;
                case 35 -> fg = Color.MAGENTA;
                case 36 -> fg = Color.CYAN;
                case 37 -> fg = Color.WHITE;
                case 39 -> fg = Color.DEFAULT;

                // Standard background colors (40–47)
                case 40 -> bg = Color.BLACK;
                case 41 -> bg = Color.RED;
                case 42 -> bg = Color.GREEN;
                case 43 -> bg = Color.YELLOW;
                case 44 -> bg = Color.BLUE;
                case 45 -> bg = Color.MAGENTA;
                case 46 -> bg = Color.CYAN;
                case 47 -> bg = Color.WHITE;
                case 49 -> bg = Color.DEFAULT;

                // Bright foreground (90–97)
                case 90 -> fg = Color.BRIGHT_BLACK;
                case 91 -> fg = Color.BRIGHT_RED;
                case 92 -> fg = Color.BRIGHT_GREEN;
                case 93 -> fg = Color.BRIGHT_YELLOW;
                case 94 -> fg = Color.BRIGHT_BLUE;
                case 95 -> fg = Color.BRIGHT_MAGENTA;
                case 96 -> fg = Color.BRIGHT_CYAN;
                case 97 -> fg = Color.BRIGHT_WHITE;

                // Bright background (100–107)
                case 100 -> bg = Color.BRIGHT_BLACK;
                case 101 -> bg = Color.BRIGHT_RED;
                case 102 -> bg = Color.BRIGHT_GREEN;
                case 103 -> bg = Color.BRIGHT_YELLOW;
                case 104 -> bg = Color.BRIGHT_BLUE;
                case 105 -> bg = Color.BRIGHT_MAGENTA;
                case 106 -> bg = Color.BRIGHT_CYAN;
                case 107 -> bg = Color.BRIGHT_WHITE;

                // 256-color and true-color (38;5;n / 38;2;r;g;b) — skip sub-params
                case 38 -> {
                    if (i + 1 < codes.length && codes[i + 1] == 5) {
                        i = Math.min(i + 2, codes.length - 1); // skip 5;n
                    } else if (i + 1 < codes.length && codes[i + 1] == 2) {
                        i = Math.min(i + 4, codes.length - 1); // skip 2;r;g;b
                    }
                }
                case 48 -> {
                    if (i + 1 < codes.length && codes[i + 1] == 5) {
                        i = Math.min(i + 2, codes.length - 1);
                    } else if (i + 1 < codes.length && codes[i + 1] == 2) {
                        i = Math.min(i + 4, codes.length - 1);
                    }
                }

                default -> { /* unknown SGR code — ignore */ }
            }
        }

        buffer.setCurrentAttributes(new CellAttributes(fg, bg, styles));
    }



    /**
     * Parses semicolon-separated CSI parameters.
     * Returns the parameter at the given index, or defaultValue if absent.
     */
    private int getParam(String params, int index, int defaultValue) {
        if (params.isEmpty()) return defaultValue;
        String[] parts = params.split(";", -1);
        if (index >= parts.length || parts[index].isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int[] parseSgrCodes(String params) {
        if (params.isEmpty()) return new int[]{0}; // ESC[m == ESC[0m (reset)
        String[] parts = params.split(";", -1);
        int[] codes = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                codes[i] = 0;
            } else {
                try {
                    codes[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    codes[i] = 0;
                }
            }
        }
        return codes;
    }

}
