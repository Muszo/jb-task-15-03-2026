package org.example.terminal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ANSI escape sequence parser.
 */
class AnsiParserTest {

    private TerminalBuffer buffer;
    private AnsiParser parser;

    @BeforeEach
    void setUp() {
        buffer = new TerminalBuffer(40, 10, 100);
        parser = new AnsiParser(buffer);
    }


    @Nested
    @DisplayName("Plain text")
    class PlainTextTests {

        @Test
        @DisplayName("Simple text is written to buffer")
        void simpleText() {
            parser.feed("Hello");
            assertEquals("Hello", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("CR+LF moves to start of next row")
        void newline() {
            parser.feed("Line 1\r\nLine 2");
            assertEquals("Line 1", buffer.getScreenLine(0));
            assertEquals("Line 2", buffer.getScreenLine(1));
        }

        @Test
        @DisplayName("Bare LF moves cursor down without carriage return")
        void bareLf() {
            parser.feed("ABCD\nXY");
            assertEquals("ABCD", buffer.getScreenLine(0));
            // LF only moves down, cursor stays at column 4
            assertEquals("    XY", buffer.getScreenLine(1));
        }

        @Test
        @DisplayName("Carriage return moves to column 0")
        void carriageReturn() {
            parser.feed("AAAA\rBB");
            assertEquals("BBAA", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Tab advances to next tab stop")
        void tab() {
            parser.feed("A\tB");
            assertEquals(8, indexOf(buffer.getScreenLine(0), 'B'));
        }

        @Test
        @DisplayName("Backspace moves cursor left")
        void backspace() {
            parser.feed("ABC\bX");
            assertEquals("ABX", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Text wraps at line width")
        void lineWrap() {
            // Buffer is 40 wide
            parser.feed("A".repeat(45));
            assertEquals("A".repeat(40), buffer.getScreenLine(0));
            assertEquals("A".repeat(5), buffer.getScreenLine(1));
        }
    }


    @Nested
    @DisplayName("CSI Cursor movement")
    class CursorMovementTests {

        @Test
        @DisplayName("CSI H — cursor home")
        void cursorHome() {
            parser.feed("some text");
            parser.feed("\u001b[H");
            assertEquals(0, buffer.getCursorColumn());
            assertEquals(0, buffer.getCursorRow());
        }

        @Test
        @DisplayName("CSI row;col H — cursor position")
        void cursorPosition() {
            parser.feed("\u001b[5;10H");
            assertEquals(9, buffer.getCursorColumn());  // 1-based → 0-based
            assertEquals(4, buffer.getCursorRow());
        }

        @Test
        @DisplayName("CSI A — cursor up")
        void cursorUp() {
            parser.feed("\u001b[5;1H");  // row 5
            parser.feed("\u001b[2A");     // up 2
            assertEquals(2, buffer.getCursorRow());
        }

        @Test
        @DisplayName("CSI B — cursor down")
        void cursorDown() {
            parser.feed("\u001b[1;1H");
            parser.feed("\u001b[3B");
            assertEquals(3, buffer.getCursorRow());
        }

        @Test
        @DisplayName("CSI C — cursor right")
        void cursorRight() {
            parser.feed("\u001b[1;1H");
            parser.feed("\u001b[5C");
            assertEquals(5, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("CSI D — cursor left")
        void cursorLeft() {
            parser.feed("\u001b[1;10H");
            parser.feed("\u001b[3D");
            assertEquals(6, buffer.getCursorColumn());
        }

        @Test
        @DisplayName("CSI G — cursor horizontal absolute")
        void cursorHorizontalAbsolute() {
            parser.feed("\u001b[15G");
            assertEquals(14, buffer.getCursorColumn());
        }
    }


    @Nested
    @DisplayName("Erase sequences")
    class EraseTests {

        @Test
        @DisplayName("CSI 2J — erase entire screen")
        void eraseScreen() {
            parser.feed("stuff on screen");
            parser.feed("\u001b[2J");
            for (int row = 0; row < buffer.getHeight(); row++) {
                assertEquals("", buffer.getScreenLine(row));
            }
        }

        @Test
        @DisplayName("CSI K — erase from cursor to end of line")
        void eraseToEndOfLine() {
            parser.feed("Hello World");
            parser.feed("\u001b[1;6H");  // position at col 5
            parser.feed("\u001b[K");
            assertEquals("Hello", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("CSI 1K — erase from start to cursor")
        void eraseFromStart() {
            parser.feed("Hello World");
            parser.feed("\u001b[1;6H");  // position at col 5
            parser.feed("\u001b[1K");
            // Columns 0-5 should be cleared, "World" remains at 6-10
            String line = buffer.getScreenLine(0);
            assertTrue(line.stripLeading().startsWith("World"));
        }

        @Test
        @DisplayName("CSI 2K — erase entire line")
        void eraseEntireLine() {
            parser.feed("Hello World");
            parser.feed("\u001b[1;1H");
            parser.feed("\u001b[2K");
            assertEquals("", buffer.getScreenLine(0));
        }
    }


    @Nested
    @DisplayName("SGR — colors and styles")
    class SgrTests {

        @Test
        @DisplayName("Bold text via SGR 1")
        void bold() {
            parser.feed("\u001b[1mBold\u001b[0m");
            CellAttributes attr = buffer.getAttributesAt(0, 0);
            assertTrue(attr.hasStyle(Style.BOLD));
        }

        @Test
        @DisplayName("Italic text via SGR 3")
        void italic() {
            parser.feed("\u001b[3mItalic\u001b[0m");
            assertTrue(buffer.getAttributesAt(0, 0).hasStyle(Style.ITALIC));
        }

        @Test
        @DisplayName("Underline text via SGR 4")
        void underline() {
            parser.feed("\u001b[4mUnder\u001b[0m");
            assertTrue(buffer.getAttributesAt(0, 0).hasStyle(Style.UNDERLINE));
        }

        @Test
        @DisplayName("Foreground color via SGR 31 (red)")
        void foregroundRed() {
            parser.feed("\u001b[31mR\u001b[0m");
            assertEquals(Color.RED, buffer.getAttributesAt(0, 0).getForeground());
        }

        @Test
        @DisplayName("Background color via SGR 44 (blue)")
        void backgroundBlue() {
            parser.feed("\u001b[44mB\u001b[0m");
            assertEquals(Color.BLUE, buffer.getAttributesAt(0, 0).getBackground());
        }

        @Test
        @DisplayName("Bright foreground via SGR 92 (bright green)")
        void brightGreen() {
            parser.feed("\u001b[92mG\u001b[0m");
            assertEquals(Color.BRIGHT_GREEN, buffer.getAttributesAt(0, 0).getForeground());
        }

        @Test
        @DisplayName("Combined styles: bold + red foreground")
        void combined() {
            parser.feed("\u001b[1;31mX\u001b[0m");
            CellAttributes attr = buffer.getAttributesAt(0, 0);
            assertTrue(attr.hasStyle(Style.BOLD));
            assertEquals(Color.RED, attr.getForeground());
        }

        @Test
        @DisplayName("Reset via SGR 0")
        void reset() {
            parser.feed("\u001b[1;31mA\u001b[0mB");
            CellAttributes attrA = buffer.getAttributesAt(0, 0);
            CellAttributes attrB = buffer.getAttributesAt(1, 0);
            assertTrue(attrA.hasStyle(Style.BOLD));
            assertEquals(CellAttributes.DEFAULT, attrB);
        }

        @Test
        @DisplayName("ESC[m is the same as ESC[0m (reset)")
        void implicitReset() {
            parser.feed("\u001b[31mR\u001b[mN");
            assertEquals(Color.RED, buffer.getAttributesAt(0, 0).getForeground());
            assertEquals(Color.DEFAULT, buffer.getAttributesAt(1, 0).getForeground());
        }
    }


    @Nested
    @DisplayName("Scrolling")
    class ScrollingTests {

        @Test
        @DisplayName("Output exceeding screen height causes scrolling")
        void scrollOnOverflow() {
            for (int i = 1; i <= 15; i++) {
                parser.feed("Line " + i + "\r\n");
            }
            // 10-row buffer: lines 1-5 should have scrolled into scrollback
            assertTrue(buffer.getScrollbackSize() > 0);
        }

        @Test
        @DisplayName("CSI S — scroll up")
        void scrollUp() {
            parser.feed("Top line\r\n");
            parser.feed("\u001b[2S"); // scroll up 2
            assertTrue(buffer.getScrollbackSize() >= 2);
        }

        @Test
        @DisplayName("CSI T — scroll down")
        void scrollDown() {
            parser.feed("Row 0\r\nRow 1\r\nRow 2");
            parser.feed("\u001b[1T"); // scroll down 1
            // Top row should now be blank (new line inserted at top)
            assertEquals("", buffer.getScreenLine(0));
            // Previous row 0 content should be at row 1
            assertEquals("Row 0", buffer.getScreenLine(1));
        }
    }

    @Nested
    @DisplayName("Insert and delete characters")
    class InsertDeleteTests {

        @Test
        @DisplayName("CSI @ — insert blanks")
        void insertBlanks() {
            parser.feed("ABCDE");
            parser.feed("\u001b[1;3H");  // cursor at col 2
            parser.feed("\u001b[2@");     // insert 2 blanks
            String line = buffer.getScreenLine(0);
            // "AB" + 2 blanks + "CDE" (possibly truncated)
            assertTrue(line.startsWith("AB"));
            assertEquals('C', line.charAt(4));
        }

        @Test
        @DisplayName("CSI P — delete characters")
        void deleteChars() {
            parser.feed("ABCDE");
            parser.feed("\u001b[1;2H");  // cursor at col 1
            parser.feed("\u001b[2P");     // delete 2 chars
            assertEquals("ADE", buffer.getScreenLine(0));
        }
    }


    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Unknown escape sequences are safely ignored")
        void unknownEscape() {
            parser.feed("\u001b[?25l");  // hide cursor (private mode)
            parser.feed("visible");
            assertEquals("visible", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("OSC sequences are consumed and ignored")
        void oscIgnored() {
            parser.feed("\u001b]0;Window Title\u0007");
            parser.feed("text");
            assertEquals("text", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Incomplete escape at end of feed is handled")
        void incompleteEscape() {
            parser.feed("hello\u001b");
            // Feed more data later
            parser.feed("[31mred");
            assertEquals(Color.RED, buffer.getAttributesAt(5, 0).getForeground());
        }

        @Test
        @DisplayName("Empty feed does nothing")
        void emptyFeed() {
            parser.feed("");
            assertEquals("", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("BEL character is ignored")
        void belIgnored() {
            parser.feed("A\u0007B");
            assertEquals("AB", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Truncated 256-color SGR does not crash or corrupt state")
        void truncated256Color() {
            // ESC[38;5m with no color index — should not crash
            parser.feed("\u001b[38;5m");
            parser.feed("A");
            assertEquals("A", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("Truncated truecolor SGR does not crash")
        void truncatedTrueColor() {
            // ESC[38;2;255m with missing g;b — should not crash
            parser.feed("\u001b[38;2;255m");
            parser.feed("X");
            assertEquals("X", buffer.getScreenLine(0));
        }

        @Test
        @DisplayName("ESC M reverse index at top row scrolls down")
        void reverseIndexAtTop() {
            parser.feed("Row 0\r\nRow 1");
            parser.feed("\u001b[1;1H"); // cursor to top
            parser.feed("\u001bM");      // reverse index
            // Should insert blank line at top, pushing content down
            assertEquals("", buffer.getScreenLine(0));
            assertEquals("Row 0", buffer.getScreenLine(1));
        }

        @Test
        @DisplayName("CSI d — cursor vertical absolute")
        void cursorVerticalAbsolute() {
            parser.feed("\u001b[5d");
            assertEquals(4, buffer.getCursorRow()); // 1-based → 0-based
        }

        @Test
        @DisplayName("CSI E — cursor next line")
        void cursorNextLine() {
            parser.feed("\u001b[3;10H"); // row 3, col 10
            parser.feed("\u001b[2E");     // next line × 2
            assertEquals(4, buffer.getCursorRow());
            assertEquals(0, buffer.getCursorColumn()); // CR to col 0
        }

        @Test
        @DisplayName("CSI F — cursor previous line")
        void cursorPrevLine() {
            parser.feed("\u001b[5;10H"); // row 5, col 10
            parser.feed("\u001b[2F");     // prev line × 2
            assertEquals(2, buffer.getCursorRow());
            assertEquals(0, buffer.getCursorColumn()); // CR to col 0
        }
    }


    @Nested
    @DisplayName("Integration — realistic sequences")
    class IntegrationTests {

        @Test
        @DisplayName("Colored prompt + command output")
        void coloredPrompt() {
            // Simulate: green "user@host" + white ":" + blue "~" + reset "$ " + command output
            // Real terminal output uses \r\n line endings.
            parser.feed("\u001b[32muser@host\u001b[0m:\u001b[34m~\u001b[0m$ ls\r\n");
            parser.feed("file1.txt  file2.txt\r\n");

            String line0 = buffer.getScreenLine(0);
            assertTrue(line0.contains("user@host"));
            assertTrue(line0.contains("ls"));
            assertEquals("file1.txt  file2.txt", buffer.getScreenLine(1));

            // Check colors
            assertEquals(Color.GREEN, buffer.getAttributesAt(0, 0).getForeground());
            assertEquals(Color.BLUE, buffer.getAttributesAt(10, 0).getForeground());
        }

        @Test
        @DisplayName("Clear screen + redraw")
        void clearAndRedraw() {
            parser.feed("old content");
            parser.feed("\u001b[2J\u001b[H"); // clear + home
            parser.feed("new content");
            assertEquals("new content", buffer.getScreenLine(0));
        }
    }

    private int indexOf(String s, char ch) {
        return s.indexOf(ch);
    }
}
