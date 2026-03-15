package org.example.demo;

import org.example.terminal.*;

import java.util.EnumSet;

/**
 * Standalone demo of the TerminalBuffer API without the ANSI parser or shell.
 * Exercises buffer operations directly and prints the results.
 */
public class BufferDemo {

    public void run() {
        TerminalBuffer buf = new TerminalBuffer(40, 10, 100);

        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║   Terminal Buffer — API Demo (40×10)     ║");
        System.out.println("╚══════════════════════════════════════════╝\n");

        // 1. Write text
        buf.writeText("Hello, Terminal Buffer!");
        System.out.println("1) Wrote 'Hello, Terminal Buffer!' at row 0");

        // 2. Write with attributes
        buf.setCursorPosition(0, 1);
        buf.setCurrentAttributes(new CellAttributes(Color.GREEN, Color.DEFAULT, EnumSet.of(Style.BOLD)));
        buf.writeText("Green bold text");
        System.out.println("2) Wrote 'Green bold text' at row 1 [GREEN, BOLD]");

        // 3. Insert text (shifts existing content right)
        buf.setCursorPosition(0, 1);
        buf.setCurrentAttributes(CellAttributes.DEFAULT);
        buf.insertText(">> ");
        System.out.println("3) Inserted '>> ' at start of row 1");

        // 4. Fill a line
        buf.setCursorPosition(0, 2);
        buf.setCurrentAttributes(new CellAttributes(Color.CYAN, Color.DEFAULT, EnumSet.noneOf(Style.class)));
        buf.fillLine('-');
        System.out.println("4) Filled row 2 with '-' [CYAN]");

        // 5. More lines with various styles
        buf.setCurrentAttributes(CellAttributes.DEFAULT);
        buf.setCursorPosition(0, 3);
        buf.writeText("Line 3: normal text");
        buf.setCursorPosition(0, 4);
        buf.setCurrentAttributes(new CellAttributes(Color.RED, Color.YELLOW, EnumSet.of(Style.UNDERLINE)));
        buf.writeText("Line 4: red on yellow, underlined");
        buf.setCursorPosition(0, 5);
        buf.setCurrentAttributes(new CellAttributes(Color.MAGENTA, Color.DEFAULT, EnumSet.of(Style.ITALIC)));
        buf.writeText("Line 5: magenta italic");
        System.out.println("5) Wrote lines 3-5 with various styles");

        printScreen(buf);

        // 6. Scroll
        System.out.println("\n6) Scrolling: inserting 3 new lines at bottom...");
        for (int i = 0; i < 3; i++) {
            buf.insertNewLineAtBottom();
        }
        buf.setCursorPosition(0, buf.getHeight() - 1);
        buf.setCurrentAttributes(CellAttributes.DEFAULT);
        buf.writeText("New bottom line after scroll");

        System.out.println("\n--- Screen After Scroll ---");
        printScreen(buf);

        System.out.println("\n--- Scrollback (" + buf.getScrollbackSize() + " lines) ---");
        for (int i = 0; i < buf.getScrollbackSize(); i++) {
            String line = buf.getScrollbackLine(i);
            System.out.printf("  [%d] %s%n", i, line.isEmpty() ? "(empty)" : line);
        }

        // 7. Attributes inspection
        System.out.println("\n--- Attribute Inspection ---");
        CellAttributes attr0 = buf.getScrollbackAttributesAt(0, 0);
        System.out.println("  Scrollback row 0, col 0: " + attr0);
        if (buf.getScrollbackSize() > 1) {
            CellAttributes attr1 = buf.getScrollbackAttributesAt(3, 1);
            System.out.println("  Scrollback row 1, col 3: " + attr1);
        }

        // 8. Clear
        System.out.println("\n8) Clearing screen and scrollback...");
        buf.clearScreenAndScrollback();
        System.out.println("  Screen line 0: '" + buf.getScreenLine(0) + "'");
        System.out.println("  Scrollback size: " + buf.getScrollbackSize());
        System.out.println("  Cursor: (" + buf.getCursorColumn() + ", " + buf.getCursorRow() + ")");

        System.out.println("\nDone!");
    }

    private void printScreen(TerminalBuffer buf) {
        System.out.println("\n--- Screen Content ---");
        for (int row = 0; row < buf.getHeight(); row++) {
            String line = buf.getScreenLine(row);
            System.out.printf("  [%d] %s%n", row, line.isEmpty() ? "(empty)" : line);
        }
    }
}
