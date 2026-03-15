package org.example.terminal;

import java.util.ArrayList;
import java.util.List;
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
        this.currentAttributes = java.util.Objects.requireNonNull(attributes, "attributes must not be null");
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
     * The cursor is clamped to the last column (width-1). Unlike {@link #writeChar(char)},
     * this method does <b>not</b> wrap text to the next line — it is intended for direct
     * buffer manipulation, not for processing terminal output streams.
     *
     * @param text the text to write (control characters are written literally, not interpreted)
     * @see #writeChar(char) for the wrap-aware variant used by the ANSI parser
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
     * Content pushed beyond the line width is discarded (no wrapping variant) or
     * wraps to the next line (wrapping variant).
     * The cursor advances past the inserted text.
     *
     * @param text the text to insert
     */
    public void insertText(String text) {
        if (text == null || text.isEmpty()) return;
        pendingWrap = false;
        TerminalLine line = screen[cursorRow];
        int insertLen = Math.min(text.length(), width - cursorColumn);
        // Shift existing content to the right to make room
        line.shiftCellsRight(cursorColumn, insertLen);
        // Write the inserted characters into the gap
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
     * @param ch the fill character (use '\0' or ' ' for clearing)
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
        // Push the top screen line into scrollback
        pushToScrollback(screen[0]);
        // Shift all lines up by one
        for (int i = 0; i < height - 1; i++) {
            screen[i] = screen[i + 1];
        }
        // Create a new empty line at the bottom
        screen[height - 1] = new TerminalLine(width);
    }

    /**
     * Inserts a new empty line at the top of the screen.
     * All lines shift down by one; the bottom line is discarded.
     */
    public void insertNewLineAtTop() {
        // Shift all lines down by one (bottom line is lost)
        for (int i = height - 1; i > 0; i--) {
            screen[i] = screen[i - 1];
        }
        // Create a new empty line at the top
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
     *
     * @param column 0-based column
     * @param row    0-based screen row
     * @return the character at that position
     */
    public char getCharAt(int column, int row) {
        validateScreenPosition(column, row);
        return screen[row].getCell(column).getCharacter();
    }

    /**
     * Gets the attributes at the given screen position.
     *
     * @param column 0-based column
     * @param row    0-based screen row
     * @return the attributes at that position
     */
    public CellAttributes getAttributesAt(int column, int row) {
        validateScreenPosition(column, row);
        return screen[row].getCell(column).getAttributes();
    }

    /**
     * Returns a single screen line as a trimmed string.
     *
     * @param row 0-based screen row
     * @return the line text (trailing spaces trimmed)
     */
    public String getScreenLine(int row) {
        validateScreenRow(row);
        return screen[row].getTextTrimmed();
    }

    /**
     * Returns the entire screen content as a multi-line string.
     * Each line is trimmed and separated by '\n'.
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
     *
     * @param column 0-based column
     * @param row    0-based scrollback row (0 = oldest line)
     * @return the character at that position
     */
    public char getScrollbackCharAt(int column, int row) {
        validateScrollbackPosition(column, row);
        return scrollback.get(row).getCell(column).getCharacter();
    }

    /**
     * Gets the attributes at the given scrollback position.
     *
     * @param column 0-based column
     * @param row    0-based scrollback row (0 = oldest line)
     * @return the attributes at that position
     */
    public CellAttributes getScrollbackAttributesAt(int column, int row) {
        validateScrollbackPosition(column, row);
        return scrollback.get(row).getCell(column).getAttributes();
    }

    /**
     * Returns a single scrollback line as a trimmed string.
     *
     * @param row 0-based scrollback row (0 = oldest)
     * @return the line text
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
     * Unlike {@link #clearScreen()}, modes 0–2 do <b>not</b> reset the cursor position,
     * which matches VT100 specification behavior.
     *
     * @param mode 0 = cursor to end of screen, 1 = start to cursor, 2 = entire screen, 3 = screen + scrollback
     * @throws IllegalArgumentException if mode is not 0, 1, 2, or 3
     */
    public void eraseInDisplay(int mode) {
        switch (mode) {
            case 0: // cursor to end of screen
                // Clear rest of current line
                for (int col = cursorColumn; col < width; col++) {
                    screen[cursorRow].getCell(col).clear(currentAttributes);
                }
                // Clear all lines below
                for (int row = cursorRow + 1; row < height; row++) {
                    screen[row].clear(currentAttributes);
                }
                break;
            case 1: // start of screen to cursor
                // Clear all lines above
                for (int row = 0; row < cursorRow; row++) {
                    screen[row].clear(currentAttributes);
                }
                // Clear current line up to and including cursor
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


    /**
     * Resizes the terminal to new dimensions, reflowing soft-wrapped content.
     * <p>
     * When the width changes, soft-wrapped lines (marked by {@link TerminalLine#isWrapped()})
     * are merged back into logical lines and re-wrapped at the new width. Hard line breaks
     * (explicit newlines) are always preserved.
     * <p>
     * When only the height changes, no reflow is needed — lines are moved between
     * scrollback and screen as appropriate.
     * <p>
     * The cursor position is tracked through the transformation and remains on screen.
     *
     * @param newWidth  new number of columns (must be &gt; 0)
     * @param newHeight new number of visible screen rows (must be &gt; 0)
     */
    public void resize(int newWidth, int newHeight) {
        if (newWidth <= 0) throw new IllegalArgumentException("Width must be positive: " + newWidth);
        if (newHeight <= 0) throw new IllegalArgumentException("Height must be positive: " + newHeight);
        if (newWidth == this.width && newHeight == this.height) return;

        boolean widthChanged = (newWidth != this.width);

        // Collect all physical lines: scrollback then screen
        List<TerminalLine> allLines = new ArrayList<>(scrollback.size() + height);
        allLines.addAll(scrollback);
        for (TerminalLine line : screen) {
            allLines.add(line);
        }

        // Cursor's absolute row in allLines
        int cursorAbsRow = scrollback.size() + cursorRow;

        List<TerminalLine> result;
        int newCursorAbsRow;
        int newCursorCol;

        if (widthChanged) {
            result = reflowLines(allLines, newWidth, cursorAbsRow, cursorColumn);
            // Cursor position is returned via the last two elements of the reflowResult helper
            // Using a simpler approach: recompute cursor during reflow
            int[] cursorResult = reflowCursor(allLines, newWidth, cursorAbsRow, cursorColumn);
            newCursorAbsRow = cursorResult[0];
            newCursorCol = cursorResult[1];
        } else {
            // Width unchanged — height-only change, no reflow needed
            result = allLines;
            newCursorAbsRow = cursorAbsRow;
            newCursorCol = cursorColumn;
        }

        // Ensure at least newHeight lines
        while (result.size() < newHeight) {
            result.add(new TerminalLine(newWidth));
        }

        int total = result.size();

        // Anchor screen so the cursor stays visible and at a reasonable position.
        // Try to keep the cursor at the same relative screen row; if that's not
        // possible, place it at the bottom of the screen.
        int desiredCursorScreenRow = Math.min(newCursorAbsRow, newHeight - 1);
        int screenStart = newCursorAbsRow - desiredCursorScreenRow;
        screenStart = Math.max(0, Math.min(screenStart, total - newHeight));

        // Rebuild scrollback
        scrollback.clear();
        for (int j = 0; j < screenStart; j++) {
            scrollback.add(result.get(j));
        }
        // Trim scrollback to max size
        int excess = scrollback.size() - maxScrollbackSize;
        if (excess > 0) {
            scrollback.subList(0, excess).clear();
        }

        // Rebuild screen
        this.screen = new TerminalLine[newHeight];
        for (int j = 0; j < newHeight; j++) {
            this.screen[j] = result.get(screenStart + j);
        }

        // Update dimensions and cursor
        this.width = newWidth;
        this.height = newHeight;

        int screenRow = newCursorAbsRow - screenStart;
        this.cursorRow = Math.max(0, Math.min(screenRow, newHeight - 1));
        this.cursorColumn = Math.max(0, Math.min(newCursorCol, newWidth - 1));
        this.pendingWrap = false;
    }

    /**
     * Reflows all lines at the given new width. Soft-wrapped lines are merged into
     * logical lines and re-wrapped. Hard breaks are preserved.
     */
    private List<TerminalLine> reflowLines(List<TerminalLine> allLines, int newWidth,
                                           int cursorAbsRow, int cursorCol) {
        List<TerminalLine> result = new ArrayList<>();
        int i = 0;

        while (i < allLines.size()) {
            // Find the extent of this logical line (consecutive soft-wrapped lines)
            int logicalStart = i;
            while (i < allLines.size() - 1 && allLines.get(i).isWrapped()) {
                i++;
            }
            int logicalEnd = i; // inclusive
            i++;

            // Flatten cells of this logical line into arrays
            int totalCells = 0;
            for (int j = logicalStart; j <= logicalEnd; j++) {
                TerminalLine line = allLines.get(j);
                totalCells += (j < logicalEnd) ? line.getWidth() : effectiveLength(line);
            }

            char[] chars = new char[totalCells];
            CellAttributes[] attrs = new CellAttributes[totalCells];
            int idx = 0;
            for (int j = logicalStart; j <= logicalEnd; j++) {
                TerminalLine line = allLines.get(j);
                int usable = (j < logicalEnd) ? line.getWidth() : effectiveLength(line);
                for (int col = 0; col < usable; col++) {
                    Cell cell = line.getCell(col);
                    chars[idx] = cell.getCharacter();
                    attrs[idx] = cell.getAttributes();
                    idx++;
                }
            }

            // Re-wrap at newWidth
            if (totalCells == 0) {
                result.add(new TerminalLine(newWidth));
            } else {
                for (int offset = 0; offset < totalCells; offset += newWidth) {
                    TerminalLine newLine = new TerminalLine(newWidth);
                    int end = Math.min(offset + newWidth, totalCells);
                    for (int j = offset; j < end; j++) {
                        newLine.setCell(j - offset, chars[j], attrs[j]);
                    }
                    boolean isLastChunk = (end >= totalCells);
                    newLine.setWrapped(!isLastChunk);
                    result.add(newLine);
                }
            }
        }

        if (result.isEmpty()) {
            result.add(new TerminalLine(newWidth));
        }
        return result;
    }

    /**
     * Computes the new cursor position after reflowing lines at a new width.
     * Returns {newAbsoluteRow, newColumn}.
     */
    private int[] reflowCursor(List<TerminalLine> allLines, int newWidth,
                               int cursorAbsRow, int cursorCol) {
        int newAbsRow = 0;
        int newCol = 0;
        int resultRowCount = 0;
        int i = 0;

        while (i < allLines.size()) {
            int logicalStart = i;
            while (i < allLines.size() - 1 && allLines.get(i).isWrapped()) {
                i++;
            }
            int logicalEnd = i;
            i++;

            // Total cells in this logical line
            int totalCells = 0;
            for (int j = logicalStart; j <= logicalEnd; j++) {
                TerminalLine line = allLines.get(j);
                totalCells += (j < logicalEnd) ? line.getWidth() : effectiveLength(line);
            }

            // Check if cursor falls within this logical line
            if (cursorAbsRow >= logicalStart && cursorAbsRow <= logicalEnd) {
                // Compute cursor's character offset within the logical line
                int cursorOffset = 0;
                for (int j = logicalStart; j < cursorAbsRow; j++) {
                    cursorOffset += allLines.get(j).getWidth();
                }
                cursorOffset += cursorCol;

                // Allow cursor to be at totalCells (one past last character)
                cursorOffset = Math.max(0, Math.min(cursorOffset, totalCells));

                // Map to new grid coordinates
                int linesForLogical = (totalCells == 0) ? 1 : (totalCells + newWidth - 1) / newWidth;
                int targetRow = (totalCells == 0) ? 0 : Math.min(cursorOffset / newWidth, linesForLogical - 1);
                int targetCol = cursorOffset - targetRow * newWidth;
                targetCol = Math.min(targetCol, newWidth - 1);

                newAbsRow = resultRowCount + targetRow;
                newCol = targetCol;
            }

            // Count how many physical lines this logical line produces at newWidth
            int linesProduced = (totalCells == 0) ? 1 : (totalCells + newWidth - 1) / newWidth;
            resultRowCount += linesProduced;
        }

        return new int[]{newAbsRow, newCol};
    }

    /**
     * Returns the effective length of a line (excluding trailing empty cells).
     * Trailing cells with character {@code '\0'} are not counted.
     */
    private static int effectiveLength(TerminalLine line) {
        int len = line.getWidth();
        while (len > 0 && line.getCell(len - 1).isEmpty()) {
            len--;
        }
        return len;
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
