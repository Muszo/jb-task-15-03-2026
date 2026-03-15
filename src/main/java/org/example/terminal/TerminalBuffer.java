package org.example.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Core terminal text buffer implementation.
 * <p>
 * The buffer consists of:
 * <ul>
 *   <li><b>Screen</b> — the last {@code height} lines visible to the user, editable.</li>
 *   <li><b>Scrollback</b> — lines that scrolled off the top, read-only history.</li>
 * </ul>
 * <p>
 * The cursor position is 0-based (column, row) within the screen area.
 */
public class TerminalBuffer {

    private int width;
    private int height;
    private final int maxScrollbackSize;

    /** Scrollback history — oldest line at index 0, newest at the end. */
    private final List<TerminalLine> scrollback;

    /** Screen lines — index 0 is the top row, index (height-1) is the bottom row. */
    private TerminalLine[] screen;

    /** Current cursor column (0-based). */
    private int cursorColumn;

    /** Current cursor row (0-based, within the screen). */
    private int cursorRow;

    /** Current attributes applied to newly written characters. */
    private CellAttributes currentAttributes;

    /**
     * When the cursor reaches the rightmost column after a write, the terminal
     * enters a "pending wrap" state: the cursor stays at the last column, and
     * the wrap/scroll happens only when the next printable character arrives.
     * This matches real VT100/xterm behavior.
     */
    private boolean pendingWrap;

    /**
     * Creates a new terminal buffer.
     *
     * @param width             number of columns (must be &gt; 0)
     * @param height            number of visible screen rows (must be &gt; 0)
     * @param maxScrollbackSize maximum number of scrollback lines (0 = no scrollback)
     */
    public TerminalBuffer(int width, int height, int maxScrollbackSize) {
        if (width <= 0) throw new IllegalArgumentException("Width must be positive: " + width);
        if (height <= 0) throw new IllegalArgumentException("Height must be positive: " + height);
        if (maxScrollbackSize < 0) throw new IllegalArgumentException("Max scrollback size must be non-negative: " + maxScrollbackSize);

        this.width = width;
        this.height = height;
        this.maxScrollbackSize = maxScrollbackSize;
        this.scrollback = new ArrayList<>();
        this.screen = new TerminalLine[height];
        for (int i = 0; i < height; i++) {
            screen[i] = new TerminalLine(width);
        }
        this.cursorColumn = 0;
        this.cursorRow = 0;
        this.currentAttributes = CellAttributes.DEFAULT;
        this.pendingWrap = false;
    }


    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMaxScrollbackSize() {
        return maxScrollbackSize;
    }

    public int getScrollbackSize() {
        return scrollback.size();
    }


    /**
     * Sets the current attributes used for subsequent writes.
     */
    public void setCurrentAttributes(CellAttributes attributes) {
        this.currentAttributes = Objects.requireNonNull(attributes, "attributes must not be null");
    }

    /**
     * Sets the current attributes using individual components.
     */
    public void setCurrentAttributes(Color foreground, Color background, Set<Style> styles) {
        this.currentAttributes = new CellAttributes(foreground, background, styles);
    }

    public CellAttributes getCurrentAttributes() {
        return currentAttributes;
    }


    public int getCursorColumn() {
        return cursorColumn;
    }

    public int getCursorRow() {
        return cursorRow;
    }

    /**
     * Sets the cursor position, clamped to screen bounds.
     *
     * @param column 0-based column
     * @param row    0-based row (within screen)
     */
    public void setCursorPosition(int column, int row) {
        this.cursorColumn = clampColumn(column);
        this.cursorRow = clampRow(row);
        this.pendingWrap = false;
    }

    /**
     * Moves the cursor up by {@code n} rows, clamped at the top of the screen.
     */
    public void moveCursorUp(int n) {
        cursorRow = clampRow(cursorRow - n);
        pendingWrap = false;
    }

    /**
     * Moves the cursor down by {@code n} rows, clamped at the bottom of the screen.
     */
    public void moveCursorDown(int n) {
        cursorRow = clampRow(cursorRow + n);
        pendingWrap = false;
    }

    /**
     * Moves the cursor left by {@code n} columns, clamped at column 0.
     */
    public void moveCursorLeft(int n) {
        cursorColumn = clampColumn(cursorColumn - n);
        pendingWrap = false;
    }

    /**
     * Moves the cursor right by {@code n} columns, clamped at the last column.
     */
    public void moveCursorRight(int n) {
        cursorColumn = clampColumn(cursorColumn + n);
        pendingWrap = false;
    }


    /**
     * Writes text at the current cursor position, overwriting existing content.
     * The cursor advances to the right. Characters beyond the screen width are discarded.
     * The cursor is clamped to the last column (width-1).
     *
     * @param text the text to write (control characters are written literally, not interpreted)
     */
    public void writeText(String text) {
        if (text == null || text.isEmpty()) return;
        pendingWrap = false;
        TerminalLine line = screen[cursorRow];
        for (int i = 0; i < text.length(); i++) {
            if (cursorColumn >= width) break;
            line.setCell(cursorColumn, text.charAt(i), currentAttributes);
            cursorColumn++;
        }
        cursorColumn = clampColumn(cursorColumn);
    }

    /**
     * Inserts text at the current cursor position, shifting existing content to the right.
     * Content pushed beyond the line width is discarded.
     * The cursor advances past the inserted text.
     *
     * @param text the text to insert
     */
    public void insertText(String text) {
        if (text == null || text.isEmpty()) return;
        pendingWrap = false;
        TerminalLine line = screen[cursorRow];
        int insertLen = Math.min(text.length(), width - cursorColumn);
        line.shiftCellsRight(cursorColumn, insertLen);
        for (int i = 0; i < insertLen; i++) {
            line.setCell(cursorColumn, text.charAt(i), currentAttributes);
            cursorColumn++;
        }
        cursorColumn = clampColumn(cursorColumn);
    }

    /**
     * Fills the current cursor row with the given character using the current attributes.
     * The cursor position is not changed.
     *
     * @param ch the fill character
     */
    public void fillLine(char ch) {
        screen[cursorRow].fill(ch, currentAttributes);
    }

    /**
     * Inserts a new empty line at the bottom of the screen.
     * The top screen line is pushed into scrollback (if scrollback is enabled).
     * All other lines shift up by one.
     */
    public void insertNewLineAtBottom() {
        pushToScrollback(screen[0]);
        for (int i = 0; i < height - 1; i++) {
            screen[i] = screen[i + 1];
        }
        screen[height - 1] = new TerminalLine(width);
    }

    /**
     * Inserts a new empty line at the top of the screen.
     * All lines shift down by one; the bottom line is discarded.
     */
    public void insertNewLineAtTop() {
        for (int i = height - 1; i > 0; i--) {
            screen[i] = screen[i - 1];
        }
        screen[0] = new TerminalLine(width);
    }

    /**
     * Clears the entire screen (all cells become empty with default attributes).
     * The cursor position is reset to (0, 0).
     */
    public void clearScreen() {
        for (int i = 0; i < height; i++) {
            screen[i].clear();
        }
        cursorColumn = 0;
        cursorRow = 0;
        pendingWrap = false;
    }

    /**
     * Clears both the screen and the scrollback history.
     * The cursor position is reset to (0, 0).
     */
    public void clearScreenAndScrollback() {
        clearScreen();
        scrollback.clear();
    }

    /**
     * Gets the character at the given screen position.
     */
    public char getCharAt(int column, int row) {
        validateScreenPosition(column, row);
        return screen[row].getCell(column).getCharacter();
    }

    /**
     * Gets the attributes at the given screen position.
     */
    public CellAttributes getAttributesAt(int column, int row) {
        validateScreenPosition(column, row);
        return screen[row].getCell(column).getAttributes();
    }

    /**
     * Returns a single screen line as a trimmed string.
     */
    public String getScreenLine(int row) {
        validateScreenRow(row);
        return screen[row].getTextTrimmed();
    }

    /**
     * Returns the entire screen content as a multi-line string.
     */
    public String getScreenContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < height; i++) {
            if (i > 0) sb.append('\n');
            sb.append(screen[i].getTextTrimmed());
        }
        return sb.toString();
    }

    /**
     * Gets the character at the given scrollback position.
     */
    public char getScrollbackCharAt(int column, int row) {
        validateScrollbackPosition(column, row);
        return scrollback.get(row).getCell(column).getCharacter();
    }

    /**
     * Gets the attributes at the given scrollback position.
     */
    public CellAttributes getScrollbackAttributesAt(int column, int row) {
        validateScrollbackPosition(column, row);
        return scrollback.get(row).getCell(column).getAttributes();
    }

    /**
     * Returns a single scrollback line as a trimmed string.
     */
    public String getScrollbackLine(int row) {
        if (row < 0 || row >= scrollback.size()) throw new IndexOutOfBoundsException("Scrollback row " + row + " out of bounds");
        return scrollback.get(row).getTextTrimmed();
    }

    /**
     * Returns the entire scrollback + screen content as a multi-line string.
     */
    public String getFullContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < scrollback.size(); i++) {
            sb.append(scrollback.get(i).getTextTrimmed());
            sb.append('\n');
        }
        for (int i = 0; i < height; i++) {
            if (i > 0) sb.append('\n');
            sb.append(screen[i].getTextTrimmed());
        }
        return sb.toString();
    }


    /**
     * Writes a single character at the cursor position with the current attributes,
     * advancing the cursor. If the cursor is in the "pending wrap" state (at the
     * rightmost column after a previous write), it wraps to the next line first,
     * scrolling if necessary. This matches real VT100/xterm behavior.
     */
    public void writeChar(char ch) {
        if (pendingWrap) {
            // Mark the current line as soft-wrapped (content continues on next line)
            screen[cursorRow].setWrapped(true);
            // Perform the deferred wrap
            cursorColumn = 0;
            advanceCursorToNextRow();
            pendingWrap = false;
        }
        screen[cursorRow].setCell(cursorColumn, ch, currentAttributes);
        if (cursorColumn < width - 1) {
            cursorColumn++;
        } else {
            // At the rightmost column — enter pending wrap state
            pendingWrap = true;
        }
    }

    /**
     * Handles a newline: moves cursor down one row. If already at the bottom,
     * scrolls the screen up (top line goes to scrollback).
     */
    public void newLine() {
        advanceCursorToNextRow();
        pendingWrap = false;
    }

    /**
     * Handles a carriage return: moves cursor to column 0 on the current row.
     */
    public void carriageReturn() {
        cursorColumn = 0;
        pendingWrap = false;
    }

    /**
     * Handles a backspace: moves cursor left by 1 (does not erase).
     */
    public void backspace() {
        if (cursorColumn > 0) cursorColumn--;
        pendingWrap = false;
    }

    private static final int TAB_WIDTH = 8;

    /**
     * Handles a horizontal tab: advances cursor to the next tab stop (every {@value TAB_WIDTH} columns).
     */
    public void tab() {
        int nextTab = ((cursorColumn / TAB_WIDTH) + 1) * TAB_WIDTH;
        cursorColumn = Math.min(nextTab, width - 1);
        pendingWrap = false;
    }

    /**
     * Erases part of the current line.
     *
     * @param mode 0 = cursor to end of line, 1 = start of line to cursor, 2 = entire line
     * @throws IllegalArgumentException if mode is not 0, 1, or 2
     */
    public void eraseInLine(int mode) {
        TerminalLine line = screen[cursorRow];
        switch (mode) {
            case 0: // cursor to end
                for (int col = cursorColumn; col < width; col++) {
                    line.getCell(col).clear(currentAttributes);
                }
                break;
            case 1: // start to cursor
                for (int col = 0; col <= cursorColumn && col < width; col++) {
                    line.getCell(col).clear(currentAttributes);
                }
                break;
            case 2: // entire line
                line.clear(currentAttributes);
                break;
            default:
                throw new IllegalArgumentException("Unknown eraseInLine mode: " + mode);
        }
    }

    /**
     * Erases part of the screen.
     * <p>
     * Unlike {@link #clearScreen()}, modes 0-2 do <b>not</b> reset the cursor position,
     * which matches VT100 specification behavior.
     *
     * @param mode 0 = cursor to end of screen, 1 = start to cursor, 2 = entire screen, 3 = screen + scrollback
     * @throws IllegalArgumentException if mode is not 0, 1, 2, or 3
     */
    public void eraseInDisplay(int mode) {
        switch (mode) {
            case 0: // cursor to end of screen
                for (int col = cursorColumn; col < width; col++) {
                    screen[cursorRow].getCell(col).clear(currentAttributes);
                }
                for (int row = cursorRow + 1; row < height; row++) {
                    screen[row].clear(currentAttributes);
                }
                break;
            case 1: // start of screen to cursor
                for (int row = 0; row < cursorRow; row++) {
                    screen[row].clear(currentAttributes);
                }
                for (int col = 0; col <= cursorColumn && col < width; col++) {
                    screen[cursorRow].getCell(col).clear(currentAttributes);
                }
                break;
            case 2: // entire screen (cursor stays)
                for (int row = 0; row < height; row++) {
                    screen[row].clear(currentAttributes);
                }
                break;
            case 3: // screen + scrollback (cursor stays, matching xterm behavior)
                for (int row = 0; row < height; row++) {
                    screen[row].clear(currentAttributes);
                }
                scrollback.clear();
                break;
            default:
                throw new IllegalArgumentException("Unknown eraseInDisplay mode: " + mode);
        }
    }

    /**
     * Deletes N characters at the cursor position, shifting remaining content left.
     */
    public void deleteChars(int n) {
        TerminalLine line = screen[cursorRow];
        for (int col = cursorColumn; col < width; col++) {
            int srcCol = col + n;
            if (srcCol < width) {
                line.getCell(col).copyFrom(line.getCell(srcCol));
            } else {
                line.getCell(col).clear(currentAttributes);
            }
        }
    }

    /**
     * Inserts N blank characters at the cursor position, shifting content right.
     */
    public void insertBlanks(int n) {
        screen[cursorRow].shiftCellsRight(cursorColumn, n);
    }

    /**
     * Advances cursor to the next row, scrolling the screen if at the bottom.
     */
    private void advanceCursorToNextRow() {
        if (cursorRow < height - 1) {
            cursorRow++;
        } else {
            // At bottom — scroll up
            insertNewLineAtBottom();
        }
    }


    private int clampColumn(int column) {
        return Math.max(0, Math.min(column, width - 1));
    }

    private int clampRow(int row) {
        return Math.max(0, Math.min(row, height - 1));
    }

    /**
     * Pushes the given line into scrollback, trimming oldest entries if capacity is exceeded.
     */
    private void pushToScrollback(TerminalLine line) {
        if (maxScrollbackSize <= 0) return;
        scrollback.add(line);
        int excess = scrollback.size() - maxScrollbackSize;
        if (excess > 0) {
            scrollback.subList(0, excess).clear();
        }
    }

    private void validateScreenRow(int row) {
        if (row < 0 || row >= height) throw new IndexOutOfBoundsException("Row " + row + " out of bounds [0, " + (height - 1) + "]");
    }

    private void validateScreenPosition(int column, int row) {
        if (column < 0 || column >= width) throw new IndexOutOfBoundsException("Column " + column + " out of bounds [0, " + (width - 1) + "]");
        validateScreenRow(row);
    }

    private void validateScrollbackPosition(int column, int row) {
        if (row < 0 || row >= scrollback.size()) throw new IndexOutOfBoundsException("Scrollback row " + row + " out of bounds");
        if (column < 0 || column >= width) throw new IndexOutOfBoundsException("Column " + column + " out of bounds");
    }
}
