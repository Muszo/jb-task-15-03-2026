package org.example.terminal;

/**
 * Represents a single line (row) of terminal cells.
 * The line has a fixed width matching the terminal width.
 */
public final class TerminalLine {

    private final Cell[] cells;

    /**
     * Indicates whether this line was soft-wrapped (auto-wrapped at the terminal width).
     * {@code true} means this line continues on the next physical line (soft wrap).
     * {@code false} means this line ends with a hard break (\n) or is the last line of output.
     * <p>
     * This flag is essential for correct reflow on resize: soft-wrapped lines are
     * merged back into logical lines and re-wrapped at the new width, while hard
     * breaks are preserved.
     */
    private boolean wrapped;

    /**
     * Creates a new empty line with the given width.
     */
    public TerminalLine(int width) {
        if (width <= 0) throw new IllegalArgumentException("Width must be positive: " + width);
        cells = new Cell[width];
        for (int i = 0; i < width; i++) {
            cells[i] = new Cell();
        }
        this.wrapped = false;
    }

    /**
     * Returns whether this line was soft-wrapped (content continues on the next line).
     */
    public boolean isWrapped() {
        return wrapped;
    }

    /**
     * Sets the soft-wrap flag for this line.
     */
    public void setWrapped(boolean wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * Returns the width (number of columns) of this line.
     */
    public int getWidth() {
        return cells.length;
    }

    /**
     * Gets the cell at the given column index (0-based).
     */
    public Cell getCell(int column) {
        validateColumn(column);
        return cells[column];
    }

    /**
     * Sets the character and attributes of the cell at the given column.
     */
    public void setCell(int column, char character, CellAttributes attributes) {
        validateColumn(column);
        cells[column].set(character, attributes);
    }

    /**
     * Clears all cells in this line to empty with default attributes.
     */
    public void clear() {
        for (Cell cell : cells) {
            cell.clear();
        }
    }

    /**
     * Clears all cells in this line to empty, keeping the given attributes
     * (e.g. current background color for erase operations).
     */
    public void clear(CellAttributes attributes) {
        for (Cell cell : cells) {
            cell.clear(attributes);
        }
    }

    /**
     * Fills the entire line with the given character and attributes.
     */
    public void fill(char character, CellAttributes attributes) {
        for (Cell cell : cells) {
            cell.set(character, attributes);
        }
    }

    /**
     * Returns the line content as a string (trailing spaces trimmed).
     */
    public String getTextTrimmed() {
        StringBuilder sb = new StringBuilder();
        for (Cell cell : cells) {
            sb.append(cell.getDisplayCharacter());
        }
        // Trim trailing spaces
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == ' ') {
            end--;
        }
        return sb.substring(0, end);
    }

    /**
     * Returns the full line content as a string (including trailing spaces, padded to width).
     */
    public String getTextFull() {
        StringBuilder sb = new StringBuilder(cells.length);
        for (Cell cell : cells) {
            sb.append(cell.getDisplayCharacter());
        }
        return sb.toString();
    }

    /**
     * Shifts cells starting at {@code fromColumn} to the right by {@code count} positions.
     * Cells that are pushed beyond the line width are discarded.
     *
     * @param fromColumn the first column to shift (0-based)
     * @param count      the number of positions to shift right
     */
    public void shiftCellsRight(int fromColumn, int count) {
        if (fromColumn < 0 || fromColumn >= cells.length || count <= 0) return;
        // Copy from right to left to avoid overwriting
        for (int i = cells.length - 1; i >= fromColumn + count; i--) {
            cells[i].copyFrom(cells[i - count]);
        }
        // Clear the opened gap (fromColumn .. fromColumn+count-1)
        for (int i = fromColumn; i < Math.min(fromColumn + count, cells.length); i++) {
            cells[i].clear();
        }
    }

    private void validateColumn(int column) {
        if (column < 0 || column >= cells.length) {
            throw new IndexOutOfBoundsException("Column " + column + " out of bounds [0, " + (cells.length - 1) + "]");
        }
    }
}
