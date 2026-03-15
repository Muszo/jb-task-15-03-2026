package org.example.demo;

import org.example.terminal.*;

import java.io.IOException;

/**
 * Interactive demo that runs real shell commands through the terminal emulator
 * pipeline: shell → raw bytes → AnsiParser → TerminalBuffer → rendered output.
 */
public class EmulatorDemo {

    private final int width;
    private final int height;

    public EmulatorDemo(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void run(String[] args) throws IOException, InterruptedException {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   Terminal Emulator — Live Demo          ║");
        System.out.println("╚══════════════════════════════════════════╝\n");

        echoDemo();
        ansiColorsDemo();
        lsDemo();
        scrollingDemo();
        cursorPositioningDemo();

        if (args.length > 0) {
            userCommandDemo(args);
        }

        System.out.println("\nDone! The buffer processed all output through AnsiParser → TerminalBuffer.");
    }

    // ── Demo 1: simple echo ──

    private void echoDemo() throws IOException, InterruptedException {
        System.out.println("▶ Demo 1: echo");
        TerminalEmulator emulator = new TerminalEmulator(width, height);
        emulator.runCommand("sh", "-c", "echo 'Hello from the terminal emulator!'");
        emulator.renderToConsole();
        System.out.println();
    }

    // ── Demo 2: ANSI colors ──

    private void ansiColorsDemo() throws IOException, InterruptedException {
        System.out.println("▶ Demo 2: ANSI color codes");
        TerminalEmulator emulator = new TerminalEmulator(width, height);
        emulator.runCommand("sh", "-c",
                "printf '\\033[1;31mRED BOLD\\033[0m  "
                + "\\033[32mGREEN\\033[0m  "
                + "\\033[1;34mBLUE BOLD\\033[0m  "
                + "\\033[4;33mYELLOW UNDERLINE\\033[0m\\n'"
                + " && printf '\\033[41;37m White on Red \\033[0m "
                + "\\033[44;97m Bright White on Blue \\033[0m\\n'");
        emulator.renderToConsoleWithColors();
        System.out.println();
    }

    // ── Demo 3: ls with colors ──

    private void lsDemo() throws IOException, InterruptedException {
        System.out.println("▶ Demo 3: ls --color=always");
        TerminalEmulator emulator = new TerminalEmulator(width, height);
        emulator.runCommand("sh", "-c",
                "ls --color=always 2>/dev/null || ls -G 2>/dev/null || ls");
        emulator.renderToConsoleWithColors();
        System.out.println();
    }

    // ── Demo 4: scrolling ──

    private void scrollingDemo() throws IOException, InterruptedException {
        System.out.println("▶ Demo 4: seq 1 30 (causes scrolling in a 24-row buffer)");
        TerminalEmulator emulator = new TerminalEmulator(width, height);
        emulator.runCommand("sh", "-c", "seq 1 30");
        emulator.renderToConsole();
        System.out.printf("  (Lines scrolled into scrollback: %d lines stored)%n",
                emulator.getBuffer().getScrollbackSize());
        System.out.println();
    }

    // ── Demo 5: cursor positioning ──

    private void cursorPositioningDemo() throws IOException, InterruptedException {
        System.out.println("▶ Demo 5: cursor positioning escape sequences");
        TerminalEmulator emulator = new TerminalEmulator(width, height);
        emulator.runCommand("sh", "-c",
                "printf '\\033[2J\\033[H'"
                + " && printf '\\033[3;10HX marks the spot'"
                + " && printf '\\033[5;5H\\033[1;36m>>> Cursor positioned here <<<\\033[0m'"
                + " && printf '\\033[1;1HTop-left corner'"
                + " && printf '\\033[24;1HBottom row'");
        emulator.renderToConsoleWithColors();
        System.out.println();
    }

    // ── Demo 6: user-specified command ──

    private void userCommandDemo(String[] args) throws IOException, InterruptedException {
        String userCmd = String.join(" ", args);
        System.out.println("▶ Demo 6: user command — " + userCmd);
        TerminalEmulator emulator = new TerminalEmulator(width, height);
        emulator.runCommand("sh", "-c", userCmd);
        emulator.renderToConsoleWithColors();
        System.out.printf("  Scrollback: %d lines%n", emulator.getBuffer().getScrollbackSize());
    }
}
