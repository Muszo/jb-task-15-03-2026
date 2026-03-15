package org.example.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for terminal resize with content reflow.
 *
 * The resize operation must:
 * - Reflow soft-wrapped lines when width changes (merge → re-wrap at new width)
 * - Preserve hard line breaks (\n) through reflow
 * - Move lines between scrollback and screen when height changes
 * - Keep the cursor visible on screen after resize
 * - Preserve cell attributes through reflow
 */
class ResizeTest {

    @Nested
    @DisplayName("Resize validation")
    class ValidationTests {

        @Test
        @DisplayName("Same dimensions is a no-op")
        void sameSize() {
            TerminalBuffer buf = new TerminalBuffer(80, 24, 100);
            buf.writeText("Hello");
            buf.resize(80, 24);
            assertEquals("Hello", buf.getScreenLine(0));
            assertEquals(5, buf.getCursorColumn());
        }

        @Test
        @DisplayName("Invalid dimensions throw exceptions")
        void invalidDimensions() {
            TerminalBuffer buf = new TerminalBuffer(80, 24, 100);
            assertThrows(IllegalArgumentException.class, () -> buf.resize(0, 24));
            assertThrows(IllegalArgumentException.class, () -> buf.resize(80, 0));
            assertThrows(IllegalArgumentException.class, () -> buf.resize(-1, 24));
            assertThrows(IllegalArgumentException.class, () -> buf.resize(80, -1));
        }
    }

    @Nested
    @DisplayName("Height-only resize")
    class HeightOnlyTests {

        @Test
        @DisplayName("Increase height adds empty rows")
        void increaseHeight() {
            TerminalBuffer buf = new TerminalBuffer(10, 3, 100);
            buf.writeText("Row 0");
            buf.setCursorPosition(0, 1);
            buf.writeText("Row 1");
            buf.setCursorPosition(0, 2);
            buf.writeText("Row 2");

            buf.resize(10, 5);

            assertEquals(10, buf.getWidth());
            assertEquals(5, buf.getHeight());
            assertEquals("Row 0", buf.getScreenLine(0));
            assertEquals("Row 1", buf.getScreenLine(1));
            assertEquals("Row 2", buf.getScreenLine(2));
            assertEquals("", buf.getScreenLine(3));
            assertEquals("", buf.getScreenLine(4));
        }

        @Test
        @DisplayName("Decrease height pushes lines to scrollback")
        void decreaseHeight() {
            TerminalBuffer buf = new TerminalBuffer(10, 5, 100);
            for (int i = 0; i < 5; i++) {
                buf.setCursorPosition(0, i);
                buf.writeText("Row " + i);
            }
            buf.setCursorPosition(0, 4); // cursor at bottom

            buf.resize(10, 3);

            assertEquals(3, buf.getHeight());
            // Last 3 lines should be on screen
            assertTrue(buf.getScrollbackSize() > 0);
            // Content should be preserved across scrollback + screen
            String full = buf.getFullContent();
            assertTrue(full.contains("Row 0"));
            assertTrue(full.contains("Row 4"));
        }

        @Test
        @DisplayName("Increase height pulls lines from scrollback")
        void increasePullsFromScrollback() {
            TerminalBuffer buf = new TerminalBuffer(10, 3, 100);
            // Push content to scrollback
            for (int i = 0; i < 6; i++) {
                buf.setCursorPosition(0, 0);
                buf.writeText("Line " + i);
                buf.insertNewLineAtBottom();
            }
            int oldScrollback = buf.getScrollbackSize();

            buf.resize(10, 5);

            // Should have pulled lines from scrollback
            assertTrue(buf.getScrollbackSize() < oldScrollback);
            assertEquals(5, buf.getHeight());
        }
    }

    @Nested
    @DisplayName("Width change — reflow")
    class ReflowTests {

        @Test
        @DisplayName("Soft-wrapped line unwraps when width increases")
        void unwrapOnWidthIncrease() {
            TerminalBuffer buf = new TerminalBuffer(5, 5, 100);
            // Write "ABCDEFGH" — will soft-wrap at column 5
            for (char c = 'A'; c <= 'H'; c++) {
                buf.writeChar(c);
            }
            // Should be: "ABCDE" (wrapped) + "FGH" on next line
            assertEquals("ABCDE", buf.getScreenLine(0));
            assertEquals("FGH", buf.getScreenLine(1));

            // Resize to width 10 — should unwrap into a single line
            buf.resize(10, 5);

            assertEquals("ABCDEFGH", buf.getScreenLine(0));
            assertEquals("", buf.getScreenLine(1));
        }

        @Test
        @DisplayName("Short line re-wraps when width decreases")
        void rewrapOnWidthDecrease() {
            TerminalBuffer buf = new TerminalBuffer(10, 5, 100);
            // Write "ABCDEFGH" (8 chars, fits in width 10)
            for (char c = 'A'; c <= 'H'; c++) {
                buf.writeChar(c);
            }
            assertEquals("ABCDEFGH", buf.getScreenLine(0));

            // Resize to width 5 — "ABCDEFGH" should wrap into 2 lines
            buf.resize(5, 5);

            assertEquals("ABCDE", buf.getScreenLine(0));
            assertEquals("FGH", buf.getScreenLine(1));
        }

        @Test
        @DisplayName("Hard line breaks are preserved through reflow")
        void hardBreaksPreserved() {
            TerminalBuffer buf = new TerminalBuffer(10, 5, 100);
            // Feed through parser to get proper hard breaks
            AnsiParser parser = new AnsiParser(buf);
            parser.feed("Hello\r\nWorld\r\n");

            buf.resize(20, 5);

            // Hard breaks should be preserved — each line stays separate
            assertEquals("Hello", buf.getScreenLine(0));
            assertEquals("World", buf.getScreenLine(1));
        }

        @Test
        @DisplayName("Mixed soft and hard wraps reflow correctly")
        void mixedWraps() {
            TerminalBuffer buf = new TerminalBuffer(5, 10, 100);
            AnsiParser parser = new AnsiParser(buf);
            // "ABCDEFGH" wraps at 5, then hard newline, then "XY"
            parser.feed("ABCDEFGH\r\nXY");

            assertEquals("ABCDE", buf.getScreenLine(0));
            assertEquals("FGH", buf.getScreenLine(1));
            assertEquals("XY", buf.getScreenLine(2));

            // Resize to width 10 — soft wrap should unwrap, hard break stays
            buf.resize(10, 10);

            assertEquals("ABCDEFGH", buf.getScreenLine(0));
            assertEquals("XY", buf.getScreenLine(1));
            assertEquals("", buf.getScreenLine(2));
        }

        @Test
        @DisplayName("Empty buffer resizes cleanly")
        void emptyBufferResize() {
            TerminalBuffer buf = new TerminalBuffer(10, 5, 100);
            buf.resize(20, 10);
            assertEquals(20, buf.getWidth());
            assertEquals(10, buf.getHeight());
            assertEquals(0, buf.getCursorColumn());
            assertEquals(0, buf.getCursorRow());
        }

        @Test
        @DisplayName("Single character buffer resizes")
        void singleCharResize() {
            TerminalBuffer buf = new TerminalBuffer(10, 5, 100);
            buf.writeChar('X');
            buf.resize(5, 3);
            assertEquals('X', buf.getCharAt(0, 0));
        }
    }



    @Nested
    @DisplayName("Attributes through reflow")
    class AttributeReflowTests {

        @Test
        @DisplayName("Cell attributes survive reflow")
        void attributesSurviveReflow() {
            TerminalBuffer buf = new TerminalBuffer(5, 5, 100);
            CellAttributes redBold = new CellAttributes(Color.RED, Color.DEFAULT, EnumSet.of(Style.BOLD));
            buf.setCurrentAttributes(redBold);
            for (char c = 'A'; c <= 'H'; c++) {
                buf.writeChar(c);
            }

            // Resize — reflow
            buf.resize(10, 5);

            // All characters should retain red bold attributes
            for (int col = 0; col < 8; col++) {
                assertEquals(redBold, buf.getAttributesAt(col, 0),
                        "Attribute at column " + col + " should be preserved");
            }
        }

        @Test
        @DisplayName("Mixed attributes survive reflow")
        void mixedAttributesSurviveReflow() {
            TerminalBuffer buf = new TerminalBuffer(5, 5, 100);
            // Write "ABC" in red
            CellAttributes red = new CellAttributes(Color.RED, Color.DEFAULT, EnumSet.noneOf(Style.class));
            buf.setCurrentAttributes(red);
            buf.writeChar('A');
            buf.writeChar('B');
            buf.writeChar('C');
            // Write "DE" in blue (fills the line, triggers soft wrap)
            CellAttributes blue = new CellAttributes(Color.BLUE, Color.DEFAULT, EnumSet.noneOf(Style.class));
            buf.setCurrentAttributes(blue);
            buf.writeChar('D');
            buf.writeChar('E');
            // Write "FG" in green (on next line after wrap)
            CellAttributes green = new CellAttributes(Color.GREEN, Color.DEFAULT, EnumSet.noneOf(Style.class));
            buf.setCurrentAttributes(green);
            buf.writeChar('F');
            buf.writeChar('G');

            buf.resize(10, 5);

            assertEquals("ABCDEFG", buf.getScreenLine(0));
            assertEquals(Color.RED, buf.getAttributesAt(0, 0).getForeground());
            assertEquals(Color.RED, buf.getAttributesAt(2, 0).getForeground());
            assertEquals(Color.BLUE, buf.getAttributesAt(3, 0).getForeground());
            assertEquals(Color.GREEN, buf.getAttributesAt(5, 0).getForeground());
        }
    }

    @Nested
    @DisplayName("Cursor tracking")
    class CursorTrackingTests {

        @Test
        @DisplayName("Cursor stays at correct position after width increase")
        void cursorTrackedOnWidthIncrease() {
            TerminalBuffer buf = new TerminalBuffer(5, 5, 100);
            // Write "ABCDE" (fills line), then "FG" on next line
            for (char c = 'A'; c <= 'G'; c++) {
                buf.writeChar(c);
            }
            // Cursor should be at (2, 1) — after 'G' on second line
            assertEquals(2, buf.getCursorColumn());
            assertEquals(1, buf.getCursorRow());

            buf.resize(10, 5);

            // After unwrap, all on one line: "ABCDEFG" — cursor after 'G' at col 7
            assertEquals("ABCDEFG", buf.getScreenLine(0));
            // Cursor column should map correctly
            assertTrue(buf.getCursorColumn() >= 6 && buf.getCursorColumn() <= 7);
            assertEquals(0, buf.getCursorRow());
        }

        @Test
        @DisplayName("Cursor stays on screen after height decrease")
        void cursorVisibleAfterHeightDecrease() {
            TerminalBuffer buf = new TerminalBuffer(10, 10, 100);
            buf.setCursorPosition(5, 8); // near bottom

            buf.resize(10, 5);

            // Cursor should be clamped to valid screen bounds
            assertTrue(buf.getCursorRow() >= 0 && buf.getCursorRow() < 5);
        }

        @Test
        @DisplayName("Cursor column clamped after width decrease")
        void cursorColumnClampedOnWidthDecrease() {
            TerminalBuffer buf = new TerminalBuffer(80, 24, 100);
            buf.setCursorPosition(75, 5);

            buf.resize(40, 24);

            assertTrue(buf.getCursorColumn() < 40);
        }
    }

    @Nested
    @DisplayName("Scrollback and resize")
    class ScrollbackResizeTests {

        @Test
        @DisplayName("Scrollback content participates in reflow")
        void scrollbackReflows() {
            TerminalBuffer buf = new TerminalBuffer(5, 3, 100);
            AnsiParser parser = new AnsiParser(buf);
            // Write enough to push content into scrollback
            parser.feed("ABCDEFGH\r\n"); // wraps: "ABCDE" + "FGH", then newline
            parser.feed("Line2\r\n");
            parser.feed("Line3\r\n");
            parser.feed("Line4\r\n");

            assertTrue(buf.getScrollbackSize() > 0);

            // Resize width — scrollback should participate in reflow
            buf.resize(10, 3);

            String full = buf.getFullContent();
            assertTrue(full.contains("ABCDEFGH"), "Soft-wrapped content in scrollback should be rejoined");
        }

        @Test
        @DisplayName("Scrollback max size respected after resize")
        void scrollbackMaxRespected() {
            TerminalBuffer buf = new TerminalBuffer(10, 3, 5);
            // Fill scrollback
            for (int i = 0; i < 10; i++) {
                buf.insertNewLineAtBottom();
            }

            buf.resize(5, 3);

            assertTrue(buf.getScrollbackSize() <= 5,
                    "Scrollback should not exceed max size after resize");
        }
    }


    @Nested
    @DisplayName("Both width and height change")
    class BothDimensionsTests {

        @Test
        @DisplayName("Simultaneous width and height change")
        void bothChange() {
            TerminalBuffer buf = new TerminalBuffer(10, 5, 100);
            AnsiParser parser = new AnsiParser(buf);
            parser.feed("Hello World!\r\n");
            parser.feed("Line two\r\n");

            buf.resize(20, 10);

            assertEquals(20, buf.getWidth());
            assertEquals(10, buf.getHeight());
            String full = buf.getFullContent();
            assertTrue(full.contains("Hello World!"));
            assertTrue(full.contains("Line two"));
        }

        @Test
        @DisplayName("Shrink both dimensions")
        void shrinkBoth() {
            TerminalBuffer buf = new TerminalBuffer(20, 10, 100);
            AnsiParser parser = new AnsiParser(buf);
            parser.feed("Short\r\n");
            parser.feed("Also short\r\n");

            buf.resize(10, 5);

            assertEquals(10, buf.getWidth());
            assertEquals(5, buf.getHeight());
        }
    }


    @Nested
    @DisplayName("Resize edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Resize to 1x1")
        void resizeTo1x1() {
            TerminalBuffer buf = new TerminalBuffer(10, 5, 100);
            buf.writeChar('X');
            buf.resize(1, 1);
            assertEquals(1, buf.getWidth());
            assertEquals(1, buf.getHeight());
        }

        @Test
        @DisplayName("Resize from 1x1 to larger")
        void resizeFrom1x1() {
            TerminalBuffer buf = new TerminalBuffer(1, 1, 100);
            buf.writeChar('A');
            buf.resize(10, 5);
            assertEquals('A', buf.getCharAt(0, 0));
        }

        @Test
        @DisplayName("Multiple consecutive resizes")
        void multipleResizes() {
            TerminalBuffer buf = new TerminalBuffer(10, 5, 100);
            for (char c = 'A'; c <= 'H'; c++) {
                buf.writeChar(c);
            }

            buf.resize(5, 5);   // shrink width
            buf.resize(20, 5);  // expand width
            buf.resize(10, 5);  // back to original width

            assertEquals("ABCDEFGH", buf.getScreenLine(0));
        }

        @Test
        @DisplayName("Resize preserves ability to write after resize")
        void writeAfterResize() {
            TerminalBuffer buf = new TerminalBuffer(10, 5, 100);
            buf.writeText("Before");

            buf.resize(20, 10);

            // Should be able to write at the new dimensions
            buf.setCursorPosition(0, 7);
            buf.writeText("After");
            assertEquals("After", buf.getScreenLine(7));
        }

        @Test
        @DisplayName("Long line wraps into many lines on width decrease")
        void longLineWrapsMany() {
            TerminalBuffer buf = new TerminalBuffer(20, 5, 100);
            for (int i = 0; i < 20; i++) {
                buf.writeChar((char) ('A' + (i % 26)));
            }
            assertEquals("ABCDEFGHIJKLMNOPQRST", buf.getScreenLine(0));

            buf.resize(5, 10);

            assertEquals("ABCDE", buf.getScreenLine(0));
            assertEquals("FGHIJ", buf.getScreenLine(1));
            assertEquals("KLMNO", buf.getScreenLine(2));
            assertEquals("PQRST", buf.getScreenLine(3));
        }

        @Test
        @DisplayName("Zero scrollback max — content is lost on scroll during resize")
        void zeroScrollback() {
            TerminalBuffer buf = new TerminalBuffer(10, 3, 0);
            for (int i = 0; i < 3; i++) {
                buf.setCursorPosition(0, i);
                buf.writeText("Row " + i);
            }

            buf.resize(10, 2);

            assertEquals(0, buf.getScrollbackSize());
            assertEquals(2, buf.getHeight());
        }
    }
}
