package org.example;

import org.example.demo.BufferDemo;
import org.example.demo.EmulatorDemo;

import java.util.Arrays;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String demo = args[0].toLowerCase();
        String[] extra = args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0];

        switch (demo) {
            case "buffer" -> runBuffer();
            case "emulator" -> runEmulator(extra);
            case "all" -> {
                runBuffer();
                separator();
                runEmulator(extra);
            }
            default -> {
                System.err.println("Unknown demo: " + demo);
                printUsage();
            }
        }
    }

    private static void runBuffer() {
        System.out.println("═══ Running BufferDemo ═══\n");
        new BufferDemo().run();
    }

    private static void runEmulator(String[] args) throws Exception {
        System.out.println("═══ Running EmulatorDemo ═══\n");
        new EmulatorDemo(80, 24).run(args);
    }

    private static void separator() {
        System.out.println("\n" + "═".repeat(50) + "\n");
    }

    private static void printUsage() {
        System.out.println("""
                Terminal Emulator — Demo Launcher

                Usage:  java -cp <classpath> org.example.Main <demo> [args...]

                Available demos:
                  buffer      Exercise the TerminalBuffer API directly
                  emulator    Run shell commands through the full pipeline
                  all         Run both demos sequentially

                Examples:
                  java -cp build/classes/java/main org.example.Main buffer
                  java -cp build/classes/java/main org.example.Main emulator "ls -la"
                  java -cp build/classes/java/main org.example.Main all
                """);
    }
}
