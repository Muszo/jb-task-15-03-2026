package org.example.terminal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for the TerminalBuffer.
 */
class TerminalBufferTest {

    private TerminalBuffer buffer;

    @BeforeEach
    void setUp() {
        // Default: 80 columns x 24 rows, 1000 lines scrollback
        buffer = new TerminalBuffer(80, 24, 1000);
    }


    @Nested
    @DisplayName("Construction and setup")
    class ConstructionTests {

        @Test
        @DisplayName("Buffer is created with correct dimensions")
        void bufferHasCorrectDimensions() {
            assertEquals(80, buffer.getWidth());
            assertEquals(24, buffer.getHeight());
            assertEquals(1000, buffer.getMaxScrollbackSize());
        }

        @Test
        @DisplayName("Buffer starts with empty screen")
        void bufferStartsEmpty() {
            for (int row = 0; row < buffer.getHeight(); row++) {
                assertEquals("", buffer.getScreenLine(row));
            }
        }

        @Test
        @DisplayName("Cursor starts at (0, 0)")
        void cursorStartsAtOrigin() {
            assertEquals(0, buffer.getCursorColumn());
            assertEquals(0, buffer.getCursorRow());
        }

        @Test
        @DisplayName("Default attributes on creation")
        void defaultAttributes() {
            assertEquals(CellAttributes.DEFAULT, buffer.getCurrentAttributes());
        }

        @Test
        @DisplayName("Invalid dimensions throw exceptions")
        void invalidDimensions() {
            assertThrows(IllegalArgumentException.class, () -> new TerminalBuffer(0, 24, 100));
            assertThrows(IllegalArgumentException.class, () -> new TerminalBuffer(80, 0, 100));
            assertThrows(IllegalArgumentException.class, () -> new TerminalBuffer(80, 24, -1));
        }

        @Test
        @DisplayName("Small buffer dimensions work")
        void smallBuffer() {
            TerminalBuffer small = new TerminalBuffer(1, 1, 0);
            assertEquals(1, small.getWidth());
            assertEquals(1, small.getHeight());
        }
    }


    @Nested
    @DisplayName("Attributes")
    class AttributeTests {

        @Test
        @DisplayName("Set and get current attributes")
        void setAndGetAttributes() {
            CellAttributes attrs = new CellAttributes(Color.RED, Color.BLUE, EnumSet.of(Style.BOLD));
            buffer.setCurrentAttributes(attrs);
            assertEquals(attrs, buffer.getCurrentAttributes());
        }

        @Test
        @DisplayName("Set attributes with individual components")
        void setAttributesComponents() {
            buffer.setCurrentAttributes(Color.GREEN, Color.BLACK, EnumSet.of(Style.ITALIC, Style.UNDERLINE));
            CellAttributes attrs = buffer.getCurrentAttributes();
            assertEquals(Color.GREEN, attrs.getForeground());
            assertEquals(Color.BLACK, attrs.getBackground());
            assertTrue(attrs.hasStyle(Style.ITALIC));
            assertTrue(attrs.hasStyle(Style.UNDERLINE));
            assertFalse(attrs.hasStyle(Style.BOLD));
        }
    }

    @Nested
    @DisplayName("Cursor movement")
    class CursorTests {

        @Test
        @DisplayName("Set cursor position")
        void setCursorPosition() {
            buffer.setCursorPosition(10, 5);
            assertEquals(10, buffer.getCursorColumn());
            assertEquals(5, buffer.getCursorRow());
        }

        @Test
        @DisplayName("Cursor is clamped to screen bounds")
        void cursorClampedToBounds() {
            buffer.setCursorPosition(100, 50);
            assertEquals(79, buffer.getCursorColumn());
            assertEquals(23, buffer.getCursorRow());

            buffer.setCursorPosition(-5, -3);
            assertEquals(0, buffer.getCursorColumn());
            assertEquals(0, buffer.getCursorRow());
        }

        @Test
        @DisplayName("Move cursor up")
        void moveCursorUp() {
            buffer.setCursorPosition(5, 10);
            buffer.moveCursorUp(3);
            assertEquals(7, buffer.getCursorRow());
            assertEquals(5, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("Move cursor up clamped at top")
        void moveCursorUpClamped() {
            buffer.setCursorPosition(5, 2);
            buffer.moveCursorUp(10);
            assertEquals(0, buffer.getCursorRow());
        }

        @Test
        @DisplayName("Move cursor down")
        void moveCursorDown() {
            buffer.setCursorPosition(5, 10);
            buffer.moveCursorDown(5);
            assertEquals(15, buffer.getCursorRow());
        }

        @Test
        @DisplayName("Move cursor down clamped at bottom")
        void moveCursorDownClamped() {
            buffer.setCursorPosition(5, 20);
            buffer.moveCursorDown(100);
            assertEquals(23, buffer.getCursorRow());
        }

        @Test
        @DisplayName("Move cursor left")
        void moveCursorLeft() {
            buffer.setCursorPosition(10, 5);
            buffer.moveCursorLeft(3);
            assertEquals(7, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("Move cursor left clamped at 0")
        void moveCursorLeftClamped() {
            buffer.setCursorPosition(2, 5);
            buffer.moveCursorLeft(10);
            assertEquals(0, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("Move cursor right")
        void moveCursorRight() {
            buffer.setCursorPosition(10, 5);
            buffer.moveCursorRight(5);
            assertEquals(15, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("Move cursor right clamped at width-1")
        void moveCursorRightClamped() {
            buffer.setCursorPosition(75, 5);
            buffer.moveCursorRight(100);
            assertEquals(79, buffer.getCursorColumn());
        }
    }


    @Nested
    @DisplayName("Write text")
    class WriteTextTests {

        @Test
        @DisplayName("Write simple text at origin")
        void writeSimpleText() {
            buffer.writeText("Hello");
            assertEquals("Hello", buffer.getScreenLine(0));
            assertEquals(5, buffer.getCursorColumn());
            assertEquals(0, buffer.getCursorRow());
        }

        @Test
        @DisplayName("Write text overwrites existing content")
        void writeOverwrites() {
            buffer.writeText("AAAA");
            buffer.setCursorPosition(1, 0);
            buffer.writeText("BB");
            assertEquals("ABBA", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Write text with attributes")
        void writeWithAttributes() {
            CellAttributes attrs = new CellAttributes(Color.RED, Color.DEFAULT, EnumSet.of(Style.BOLD));
            buffer.setCurrentAttributes(attrs);
            buffer.writeText("Hi");
            assertEquals(attrs, buffer.getAttributesAt(0, 0));
            assertEquals(attrs, buffer.getAttributesAt(1, 0));
        }

        @Test
        @DisplayName("Text beyond width is truncated")
        void textTruncatedAtWidth() {
            TerminalBuffer small = new TerminalBuffer(5, 3, 0);
            small.writeText("Hello World");
            assertEquals("Hello", small.getScreenLine(0));
        }

        @Test
        @DisplayName("Write empty string does nothing")
        void writeEmptyString() {
            buffer.writeText("");
            assertEquals(0, buffer.getCursorColumn());
            assertEquals("", buffer.getScreenLine(0));
        }
    }


    @Nested
    @DisplayName("Insert text")
    class InsertTextTests {

        @Test
        @DisplayName("Insert text shifts existing content right")
        void insertShiftsRight() {
            buffer.writeText("World");
            buffer.setCursorPosition(0, 0);
            buffer.insertText("Hello ");
            assertEquals("Hello World", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Insert text with overflow truncates at line end")
        void insertOverflow() {
            TerminalBuffer small = new TerminalBuffer(10, 3, 0);
            small.writeText("12345");
            small.setCursorPosition(0, 0);
            small.insertText("ABCDEF");
            assertEquals("ABCDEF1234", small.getScreenLine(0));
        }
    }


    @Nested
    @DisplayName("Fill line")
    class FillLineTests {

        @Test
        @DisplayName("Fill line with a character")
        void fillLineChar() {
            buffer.setCursorPosition(0, 2);
            buffer.fillLine('=');
            String line = buffer.getScreenLine(2);
            assertEquals(80, line.length());
            assertTrue(line.chars().allMatch(c -> c == '='));
        }

        @Test
        @DisplayName("Fill line uses current attributes")
        void fillLineAttributes() {
            CellAttributes attrs = new CellAttributes(Color.CYAN, Color.MAGENTA, EnumSet.of(Style.UNDERLINE));
            buffer.setCurrentAttributes(attrs);
            buffer.setCursorPosition(0, 1);
            buffer.fillLine('X');
            assertEquals(attrs, buffer.getAttributesAt(0, 1));
            assertEquals(attrs, buffer.getAttributesAt(79, 1));
        }

        @Test
        @DisplayName("Cursor position unchanged after fill")
        void fillDoesNotMoveCursor() {
            buffer.setCursorPosition(10, 5);
            buffer.fillLine('-');
            assertEquals(10, buffer.getCursorColumn());
            assertEquals(5, buffer.getCursorRow());
        }
    }

    @Nested
    @DisplayName("Insert new line at bottom / Scrolling")
    class ScrollingTests {

        @Test
        @DisplayName("Insert new line pushes top line to scrollback")
        void insertNewLinePushesToScrollback() {
            buffer.writeText("First line");
            buffer.insertNewLineAtBottom();
            assertEquals(1, buffer.getScrollbackSize());
            assertEquals("First line", buffer.getScrollbackLine(0));
        }

        @Test
        @DisplayName("Bottom line is empty after insert")
        void bottomLineEmptyAfterInsert() {
            buffer.insertNewLineAtBottom();
            assertEquals("", buffer.getScreenLine(buffer.getHeight() - 1));
        }

        @Test
        @DisplayName("Scrollback respects max size")
        void scrollbackMaxSize() {
            TerminalBuffer small = new TerminalBuffer(10, 3, 2);
            for (int i = 0; i < 5; i++) {
                small.setCursorPosition(0, 0);
                small.writeText("Line " + i);
                small.insertNewLineAtBottom();
            }
            assertTrue(small.getScrollbackSize() <= 2);
        }

        @Test
        @DisplayName("No scrollback when max is 0")
        void noScrollback() {
            TerminalBuffer noSb = new TerminalBuffer(10, 3, 0);
            noSb.writeText("test");
            noSb.insertNewLineAtBottom();
            assertEquals(0, noSb.getScrollbackSize());
        }
    }


    @Nested
    @DisplayName("Clear screen")
    class ClearScreenTests {

        @Test
        @DisplayName("Clear screen empties all lines")
        void clearScreenEmptiesAll() {
            buffer.writeText("Some content");
            buffer.clearScreen();
            for (int row = 0; row < buffer.getHeight(); row++) {
                assertEquals("", buffer.getScreenLine(row));
            }
        }

        @Test
        @DisplayName("Clear screen resets cursor to origin")
        void clearScreenResetsCursor() {
            buffer.setCursorPosition(10, 10);
            buffer.clearScreen();
            assertEquals(0, buffer.getCursorColumn());
            assertEquals(0, buffer.getCursorRow());
        }

        @Test
        @DisplayName("Clear screen does not affect scrollback")
        void clearScreenKeepsScrollback() {
            buffer.writeText("First");
            buffer.insertNewLineAtBottom();
            buffer.clearScreen();
            assertEquals(1, buffer.getScrollbackSize());
        }
    }


    @Nested
    @DisplayName("Clear screen and scrollback")
    class ClearAllTests {

        @Test
        @DisplayName("Clears everything")
        void clearAll() {
            buffer.writeText("data");
            buffer.insertNewLineAtBottom();
            buffer.clearScreenAndScrollback();
            assertEquals(0, buffer.getScrollbackSize());
            assertEquals("", buffer.getScreenLine(0));
            assertEquals(0, buffer.getCursorColumn());
            assertEquals(0, buffer.getCursorRow());
        }
    }


    @Nested
    @DisplayName("Content access")
    class ContentAccessTests {

        @Test
        @DisplayName("Get char at position")
        void getCharAtPosition() {
            buffer.writeText("ABC");
            assertEquals('A', buffer.getCharAt(0, 0));
            assertEquals('B', buffer.getCharAt(1, 0));
            assertEquals('C', buffer.getCharAt(2, 0));
        }

        @Test
        @DisplayName("Get char at empty position returns empty char")
        void getCharAtEmptyPosition() {
            assertEquals('\0', buffer.getCharAt(0, 0));
        }

        @Test
        @DisplayName("Out-of-bounds access throws exception")
        void outOfBoundsThrows() {
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getCharAt(-1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getCharAt(0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getCharAt(80, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getCharAt(0, 24));
        }

        @Test
        @DisplayName("Get screen content as string")
        void getScreenContent() {
            buffer.writeText("Line 1");
            buffer.setCursorPosition(0, 1);
            buffer.writeText("Line 2");
            String content = buffer.getScreenContent();
            assertTrue(content.startsWith("Line 1\nLine 2"));
        }

        @Test
        @DisplayName("Get full content includes scrollback")
        void getFullContent() {
            buffer.writeText("Scrolled away");
            buffer.insertNewLineAtBottom();
            buffer.setCursorPosition(0, 0);
            buffer.writeText("Visible");
            String full = buffer.getFullContent();
            assertTrue(full.contains("Scrolled away"));
            assertTrue(full.contains("Visible"));
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("1x1 buffer works correctly")
        void tinyBuffer() {
            TerminalBuffer tiny = new TerminalBuffer(1, 1, 1);
            tiny.writeText("X");
            assertEquals('X', tiny.getCharAt(0, 0));
            assertEquals(0, tiny.getCursorColumn());
        }

        @Test
        @DisplayName("Writing at last column")
        void writeAtLastColumn() {
            buffer.setCursorPosition(79, 0);
            buffer.writeText("Z");
            assertEquals('Z', buffer.getCharAt(79, 0));
        }

        @Test
        @DisplayName("Multiple scrollback rotations")
        void scrollbackRotation() {
            TerminalBuffer sb = new TerminalBuffer(20, 3, 5);
            for (int i = 0; i < 20; i++) {
                sb.setCursorPosition(0, 0);
                sb.writeText("Line " + i);
                sb.insertNewLineAtBottom();
            }
            assertEquals(5, sb.getScrollbackSize());
        }

        @Test
        @DisplayName("setCurrentAttributes rejects null")
        void setCurrentAttributesNull() {
            assertThrows(NullPointerException.class, () -> buffer.setCurrentAttributes((CellAttributes) null));
        }
    }


    @Nested
    @DisplayName("Insert new line at top")
    class InsertNewLineAtTopTests {

        @Test
        @DisplayName("Insert at top pushes content down")
        void insertAtTopPushesDown() {
            buffer.writeText("Row 0");
            buffer.setCursorPosition(0, 1);
            buffer.writeText("Row 1");
            buffer.insertNewLineAtTop();
            assertEquals("", buffer.getScreenLine(0));
            assertEquals("Row 0", buffer.getScreenLine(1));
            assertEquals("Row 1", buffer.getScreenLine(2));
        }

        @Test
        @DisplayName("Insert at top discards bottom line")
        void insertAtTopDiscardsBottom() {
            TerminalBuffer small = new TerminalBuffer(10, 3, 0);
            small.setCursorPosition(0, 0);
            small.writeText("Row 0");
            small.setCursorPosition(0, 1);
            small.writeText("Row 1");
            small.setCursorPosition(0, 2);
            small.writeText("Row 2");
            small.insertNewLineAtTop();
            assertEquals("", small.getScreenLine(0));
            assertEquals("Row 0", small.getScreenLine(1));
            assertEquals("Row 1", small.getScreenLine(2));
        }
    }

    @Nested
    @DisplayName("Scrollback access")
    class ScrollbackAccessTests {

        @Test
        @DisplayName("Scrollback char access out of bounds throws")
        void scrollbackCharOutOfBounds() {
            buffer.writeText("test");
            buffer.insertNewLineAtBottom();
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getScrollbackCharAt(-1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getScrollbackCharAt(80, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getScrollbackCharAt(0, -1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getScrollbackCharAt(0, 1));
        }

        @Test
        @DisplayName("Scrollback attributes access out of bounds throws")
        void scrollbackAttrsOutOfBounds() {
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getScrollbackAttributesAt(0, 0));
        }

        @Test
        @DisplayName("Scrollback line access out of bounds throws")
        void scrollbackLineOutOfBounds() {
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getScrollbackLine(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.getScrollbackLine(0));
        }

        @Test
        @DisplayName("Scrollback preserves attributes of scrolled lines")
        void scrollbackPreservesAttributes() {
            CellAttributes attrs = new CellAttributes(Color.RED, Color.BLUE, EnumSet.of(Style.BOLD));
            buffer.setCurrentAttributes(attrs);
            buffer.writeText("colored");
            buffer.insertNewLineAtBottom();
            assertEquals(attrs, buffer.getScrollbackAttributesAt(0, 0));
            assertEquals('c', buffer.getScrollbackCharAt(0, 0));
        }
    }
}
