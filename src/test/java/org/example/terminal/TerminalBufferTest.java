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


    @Nested
    @DisplayName("Terminal operations")
    class TerminalOpsTests {

        @Test
        @DisplayName("writeChar writes at cursor and advances")
        void writeCharBasic() {
            buffer.writeChar('H');
            buffer.writeChar('i');
            assertEquals("Hi", buffer.getScreenLine(0));
            assertEquals(2, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("writeChar pending wrap: writing at last column defers wrap")
        void writeCharPendingWrap() {
            TerminalBuffer small = new TerminalBuffer(5, 3, 0);
            // Write 5 chars to fill the line
            for (char c = 'A'; c <= 'E'; c++) {
                small.writeChar(c);
            }
            // Cursor should still be at column 4 (last column), row 0
            assertEquals(4, small.getCursorColumn());
            assertEquals(0, small.getCursorRow());
            assertEquals("ABCDE", small.getScreenLine(0));

            // Writing one more char triggers the deferred wrap
            small.writeChar('F');
            assertEquals(1, small.getCursorColumn());
            assertEquals(1, small.getCursorRow());
            assertEquals("F", small.getScreenLine(1).trim());
        }

        @Test
        @DisplayName("writeChar wraps and scrolls at bottom of screen")
        void writeCharScrollsAtBottom() {
            TerminalBuffer small = new TerminalBuffer(3, 2, 5);
            // Fill row 0: "ABC" (pending wrap)
            small.writeChar('A');
            small.writeChar('B');
            small.writeChar('C');
            // Fill row 1: wrap here, then "DEF" (pending wrap)
            small.writeChar('D');
            small.writeChar('E');
            small.writeChar('F');
            // Next char should scroll: row0 -> scrollback, row1 becomes row0
            small.writeChar('G');
            assertEquals(1, small.getScrollbackSize());
            assertEquals("ABC", small.getScrollbackLine(0));
        }

        @Test
        @DisplayName("newLine moves cursor down")
        void newLineBasic() {
            buffer.setCursorPosition(5, 0);
            buffer.newLine();
            assertEquals(1, buffer.getCursorRow());
            assertEquals(5, buffer.getCursorColumn()); // column unchanged
        }

        @Test
        @DisplayName("newLine at bottom scrolls screen")
        void newLineAtBottom() {
            buffer.writeText("top");
            buffer.setCursorPosition(0, buffer.getHeight() - 1);
            buffer.newLine();
            // Should have scrolled — top line pushed to scrollback
            assertEquals(1, buffer.getScrollbackSize());
        }

        @Test
        @DisplayName("carriageReturn moves to column 0")
        void carriageReturnBasic() {
            buffer.setCursorPosition(10, 5);
            buffer.carriageReturn();
            assertEquals(0, buffer.getCursorColumn());
            assertEquals(5, buffer.getCursorRow()); // row unchanged
        }

        @Test
        @DisplayName("backspace moves cursor left by 1")
        void backspaceBasic() {
            buffer.setCursorPosition(5, 0);
            buffer.backspace();
            assertEquals(4, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("backspace at column 0 does nothing")
        void backspaceAtZero() {
            buffer.setCursorPosition(0, 0);
            buffer.backspace();
            assertEquals(0, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("tab advances to next tab stop")
        void tabBasic() {
            buffer.setCursorPosition(0, 0);
            buffer.tab();
            assertEquals(8, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("tab from mid-position rounds up to next tab stop")
        void tabFromMid() {
            buffer.setCursorPosition(3, 0);
            buffer.tab();
            assertEquals(8, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("tab does not exceed last column")
        void tabAtEnd() {
            buffer.setCursorPosition(75, 0);
            buffer.tab();
            assertEquals(79, buffer.getCursorColumn());
        }
    }



    @Nested
    @DisplayName("Delete characters")
    class DeleteCharsTests {

        @Test
        @DisplayName("Delete characters at cursor shifts content left")
        void deleteShiftsLeft() {
            buffer.writeText("ABCDE");
            buffer.setCursorPosition(1, 0);
            buffer.deleteChars(2);
            assertEquals("ADE", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Delete characters clears vacated cells with current attributes")
        void deleteClearsWithAttributes() {
            TerminalBuffer small = new TerminalBuffer(10, 3, 0);
            small.writeText("ABCDEFGHIJ");
            CellAttributes attrs = new CellAttributes(Color.RED, Color.DEFAULT, EnumSet.noneOf(Style.class));
            small.setCurrentAttributes(attrs);
            small.setCursorPosition(0, 0);
            small.deleteChars(3);
            assertEquals(attrs, small.getAttributesAt(7, 0));
            assertEquals(attrs, small.getAttributesAt(8, 0));
            assertEquals(attrs, small.getAttributesAt(9, 0));
        }

        @Test
        @DisplayName("Delete more chars than remaining fills with blanks")
        void deleteMoreThanRemaining() {
            buffer.writeText("ABC");
            buffer.setCursorPosition(0, 0);
            buffer.deleteChars(100);
            assertEquals("", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Delete at end of line clears remaining")
        void deleteAtEnd() {
            buffer.writeText("Hello");
            buffer.setCursorPosition(3, 0);
            buffer.deleteChars(2);
            assertEquals("Hel", buffer.getScreenLine(0));
        }
    }

    @Nested
    @DisplayName("Insert blank characters")
    class InsertBlanksTests {

        @Test
        @DisplayName("Insert blanks shifts content right")
        void insertBlanksShiftsRight() {
            buffer.writeText("ABCDE");
            buffer.setCursorPosition(2, 0);
            buffer.insertBlanks(2);
            String line = buffer.getScreenLine(0);
            assertEquals('A', line.charAt(0));
            assertEquals('B', line.charAt(1));
            assertEquals('C', line.charAt(4));
            assertEquals('D', line.charAt(5));
            assertEquals('E', line.charAt(6));
        }

        @Test
        @DisplayName("Insert blanks pushes content off end")
        void insertBlanksOverflow() {
            TerminalBuffer small = new TerminalBuffer(5, 3, 0);
            small.writeText("ABCDE");
            small.setCursorPosition(1, 0);
            small.insertBlanks(2);
            String line = small.getScreenLine(0);
            assertTrue(line.startsWith("A"));
            assertEquals('B', line.charAt(3));
            assertEquals('C', line.charAt(4));
        }
    }


    @Nested
    @DisplayName("Erase in Display")
    class EraseInDisplayTests {

        @Test
        @DisplayName("Mode 0: erase from cursor to end of screen")
        void eraseCursorToEnd() {
            buffer.writeText("Row 0 text");
            buffer.setCursorPosition(0, 1);
            buffer.writeText("Row 1 text");
            buffer.setCursorPosition(0, 2);
            buffer.writeText("Row 2 text");
            buffer.setCursorPosition(4, 1);
            buffer.eraseInDisplay(0);
            assertEquals("Row 0 text", buffer.getScreenLine(0));
            assertEquals("Row", buffer.getScreenLine(1).trim());
            assertEquals("", buffer.getScreenLine(2));
        }

        @Test
        @DisplayName("Mode 1: erase from start of screen to cursor")
        void eraseStartToCursor() {
            buffer.writeText("Row 0 text");
            buffer.setCursorPosition(0, 1);
            buffer.writeText("Row 1 text");
            buffer.setCursorPosition(0, 2);
            buffer.writeText("Row 2 text");
            buffer.setCursorPosition(5, 1);
            buffer.eraseInDisplay(1);
            assertEquals("", buffer.getScreenLine(0));
            String line1 = buffer.getScreenLine(1);
            assertTrue(line1.stripLeading().startsWith("text"));
            assertEquals("Row 2 text", buffer.getScreenLine(2));
        }

        @Test
        @DisplayName("Mode 2: erase entire screen but cursor stays")
        void eraseEntireScreenCursorStays() {
            buffer.writeText("content");
            buffer.setCursorPosition(5, 3);
            buffer.eraseInDisplay(2);
            for (int row = 0; row < buffer.getHeight(); row++) {
                assertEquals("", buffer.getScreenLine(row));
            }
            assertEquals(5, buffer.getCursorColumn());
            assertEquals(3, buffer.getCursorRow());
        }

        @Test
        @DisplayName("Mode 3: erase screen + scrollback, cursor stays")
        void eraseScreenAndScrollbackCursorStays() {
            buffer.writeText("data");
            buffer.insertNewLineAtBottom();
            assertEquals(1, buffer.getScrollbackSize());
            buffer.setCursorPosition(5, 3);
            buffer.eraseInDisplay(3);
            assertEquals(0, buffer.getScrollbackSize());
            for (int row = 0; row < buffer.getHeight(); row++) {
                assertEquals("", buffer.getScreenLine(row));
            }
            assertEquals(5, buffer.getCursorColumn());
            assertEquals(3, buffer.getCursorRow());
        }

        @Test
        @DisplayName("Invalid erase mode throws exception")
        void invalidEraseMode() {
            assertThrows(IllegalArgumentException.class, () -> buffer.eraseInDisplay(4));
        }

        @Test
        @DisplayName("Erase uses current attributes on cleared cells")
        void eraseUsesCurrentAttributes() {
            buffer.writeText("Hello");
            CellAttributes attrs = new CellAttributes(Color.RED, Color.BLUE, EnumSet.noneOf(Style.class));
            buffer.setCurrentAttributes(attrs);
            buffer.setCursorPosition(2, 0);
            buffer.eraseInLine(0);
            assertEquals(attrs, buffer.getAttributesAt(3, 0));
        }
    }


    @Nested
    @DisplayName("Erase in Line")
    class EraseInLineTests {

        @Test
        @DisplayName("Mode 0: erase from cursor to end of line")
        void eraseCursorToEndOfLine() {
            buffer.writeText("Hello World");
            buffer.setCursorPosition(5, 0);
            buffer.eraseInLine(0);
            assertEquals("Hello", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Mode 1: erase from start of line to cursor")
        void eraseStartToEndOfLine() {
            buffer.writeText("Hello World");
            buffer.setCursorPosition(5, 0);
            buffer.eraseInLine(1);
            String line = buffer.getScreenLine(0);
            assertTrue(line.stripLeading().startsWith("World"));
        }

        @Test
        @DisplayName("Mode 2: erase entire line")
        void eraseEntireLine() {
            buffer.writeText("Hello World");
            buffer.setCursorPosition(3, 0);
            buffer.eraseInLine(2);
            assertEquals("", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Invalid erase mode throws exception")
        void invalidEraseLineMode() {
            assertThrows(IllegalArgumentException.class, () -> buffer.eraseInLine(3));
        }
    }

}
