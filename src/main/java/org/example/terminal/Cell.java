package org.example.terminal;

import java.util.Objects;

/**
 * Represents a single cell in the terminal grid.
 * Each cell holds a character (or empty/null char '\0') and its display attributes.
 * <p>
 * This class is mutable and must <b>not</b> be used as a key in hash-based collections.
 */
public final class Cell {

    private static final char EMPTY_CHAR = '\0';

    private char character;
    private CellAttributes attributes;

    public Cell() {
        this.character = EMPTY_CHAR;
        this.attributes = CellAttributes.DEFAULT;
    }

    public Cell(char character, CellAttributes attributes) {
        this.character = character;
        this.attributes = Objects.requireNonNull(attributes);
    }

    public char getCharacter() {
        return character;
    }

    /**
     * Sets the character of this cell.
     * Note: setting {@code '\0'} makes {@link #isEmpty()} return {@code true},
     * which is semantically equivalent to clearing the character.
     */
    public void setCharacter(char character) {
        this.character = character;
    }

    public CellAttributes getAttributes() {
        return attributes;
    }

    public void setAttributes(CellAttributes attributes) {
        this.attributes = Objects.requireNonNull(attributes);
    }

    public void set(char character, CellAttributes attributes) {
        this.character = character;
        this.attributes = Objects.requireNonNull(attributes);
    }

    /**
     * Copies the character and attributes from another cell into this one.
     */
    public void copyFrom(Cell other) {
        this.character = other.character;
        this.attributes = other.attributes;
    }

    public boolean isEmpty() {
        return character == EMPTY_CHAR;
    }

    /**
     * Resets this cell to empty with default attributes.
     */
    public void clear() {
        this.character = EMPTY_CHAR;
        this.attributes = CellAttributes.DEFAULT;
    }

    /**
     * Resets this cell to empty, keeping the given attributes (e.g. current background color).
     * This matches real terminal erase behavior where erased cells inherit the active SGR state.
     */
    public void clear(CellAttributes attributes) {
        this.character = EMPTY_CHAR;
        this.attributes = Objects.requireNonNull(attributes);
    }

    /**
     * Returns the display character: a space if empty, otherwise the stored character.
     */
    public char getDisplayCharacter() {
        return isEmpty() ? ' ' : character;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Cell cell)) return false;
        return character == cell.character && Objects.equals(attributes, cell.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(character, attributes);
    }

    @Override
    public String toString() {
        return "Cell{'" + (isEmpty() ? "EMPTY" : character) + "', " + attributes + "}";
    }
}
