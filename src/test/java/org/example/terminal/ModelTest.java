package org.example.terminal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Cell, CellAttributes, and TerminalLine model classes.
 */
class ModelTest {


    @Test
    @DisplayName("Default CellAttributes")
    void defaultAttributes() {
        CellAttributes attrs = CellAttributes.DEFAULT;
        assertEquals(Color.DEFAULT, attrs.getForeground());
        assertEquals(Color.DEFAULT, attrs.getBackground());
        assertTrue(attrs.getStyles().isEmpty());
    }

    @Test
    @DisplayName("CellAttributes with colors and styles")
    void attributesWithColorsAndStyles() {
        CellAttributes attrs = new CellAttributes(Color.RED, Color.BLUE, EnumSet.of(Style.BOLD, Style.ITALIC));
        assertEquals(Color.RED, attrs.getForeground());
        assertEquals(Color.BLUE, attrs.getBackground());
        assertTrue(attrs.hasStyle(Style.BOLD));
        assertTrue(attrs.hasStyle(Style.ITALIC));
        assertFalse(attrs.hasStyle(Style.UNDERLINE));
    }

    @Test
    @DisplayName("CellAttributes equality")
    void attributesEquality() {
        CellAttributes a = new CellAttributes(Color.RED, Color.BLUE, EnumSet.of(Style.BOLD));
        CellAttributes b = new CellAttributes(Color.RED, Color.BLUE, EnumSet.of(Style.BOLD));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("CellAttributes styles are unmodifiable")
    void attributesStylesUnmodifiable() {
        CellAttributes attrs = new CellAttributes(Color.RED, Color.BLUE, EnumSet.of(Style.BOLD));
        assertThrows(UnsupportedOperationException.class, () -> attrs.getStyles().add(Style.ITALIC));
    }

    @Test
    @DisplayName("Default Cell is empty")
    void defaultCellIsEmpty() {
        Cell cell = new Cell();
        assertTrue(cell.isEmpty());
        assertEquals('\0', cell.getCharacter());
        assertEquals(CellAttributes.DEFAULT, cell.getAttributes());
    }

    @Test
    @DisplayName("Cell with character and attributes")
    void cellWithContent() {
        CellAttributes attrs = new CellAttributes(Color.GREEN, Color.DEFAULT, EnumSet.of(Style.UNDERLINE));
        Cell cell = new Cell('A', attrs);
        assertFalse(cell.isEmpty());
        assertEquals('A', cell.getCharacter());
        assertEquals(attrs, cell.getAttributes());
    }

    @Test
    @DisplayName("Cell clear resets to empty")
    void cellClear() {
        Cell cell = new Cell('X', CellAttributes.DEFAULT);
        cell.clear();
        assertTrue(cell.isEmpty());
        assertEquals(CellAttributes.DEFAULT, cell.getAttributes());
    }

    @Test
    @DisplayName("Cell display character")
    void cellDisplayCharacter() {
        Cell empty = new Cell();
        assertEquals(' ', empty.getDisplayCharacter());

        Cell filled = new Cell('Z', CellAttributes.DEFAULT);
        assertEquals('Z', filled.getDisplayCharacter());
    }


    @Test
    @DisplayName("TerminalLine creation")
    void lineCreation() {
        TerminalLine line = new TerminalLine(80);
        assertEquals(80, line.getWidth());
        assertEquals("", line.getTextTrimmed());
    }

    @Test
    @DisplayName("TerminalLine set and get cell")
    void lineSetGetCell() {
        TerminalLine line = new TerminalLine(10);
        line.setCell(0, 'H', CellAttributes.DEFAULT);
        line.setCell(1, 'i', CellAttributes.DEFAULT);
        assertEquals("Hi", line.getTextTrimmed());
    }

    @Test
    @DisplayName("TerminalLine fill")
    void lineFill() {
        TerminalLine line = new TerminalLine(5);
        line.fill('=', CellAttributes.DEFAULT);
        assertEquals("=====", line.getTextTrimmed());
    }

    @Test
    @DisplayName("TerminalLine clear")
    void lineClear() {
        TerminalLine line = new TerminalLine(5);
        line.fill('X', CellAttributes.DEFAULT);
        line.clear();
        assertEquals("", line.getTextTrimmed());
    }

    @Test
    @DisplayName("TerminalLine out of bounds throws")
    void lineOutOfBounds() {
        TerminalLine line = new TerminalLine(5);
        assertThrows(IndexOutOfBoundsException.class, () -> line.getCell(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> line.getCell(5));
    }

    @Test
    @DisplayName("TerminalLine invalid width")
    void lineInvalidWidth() {
        assertThrows(IllegalArgumentException.class, () -> new TerminalLine(0));
        assertThrows(IllegalArgumentException.class, () -> new TerminalLine(-1));
    }

    @Test
    @DisplayName("TerminalLine getTextFull pads with spaces")
    void lineTextFull() {
        TerminalLine line = new TerminalLine(5);
        line.setCell(0, 'A', CellAttributes.DEFAULT);
        assertEquals("A    ", line.getTextFull());
        assertEquals(5, line.getTextFull().length());
    }


    @Test
    @DisplayName("shiftCellsRight shifts content and clears gap")
    void shiftCellsRightBasic() {
        TerminalLine line = new TerminalLine(10);
        line.setCell(0, 'A', CellAttributes.DEFAULT);
        line.setCell(1, 'B', CellAttributes.DEFAULT);
        line.setCell(2, 'C', CellAttributes.DEFAULT);
        line.shiftCellsRight(1, 2);
        assertEquals('A', line.getCell(0).getCharacter());
        assertTrue(line.getCell(1).isEmpty()); // gap
        assertTrue(line.getCell(2).isEmpty()); // gap
        assertEquals('B', line.getCell(3).getCharacter());
        assertEquals('C', line.getCell(4).getCharacter());
    }

    @Test
    @DisplayName("shiftCellsRight at last column does nothing meaningful")
    void shiftCellsRightAtEnd() {
        TerminalLine line = new TerminalLine(5);
        line.setCell(4, 'Z', CellAttributes.DEFAULT);
        line.shiftCellsRight(4, 1);
        assertTrue(line.getCell(4).isEmpty()); // cleared
    }

    @Test
    @DisplayName("shiftCellsRight with count beyond width discards overflowing content")
    void shiftCellsRightOverflow() {
        TerminalLine line = new TerminalLine(5);
        for (int i = 0; i < 5; i++) {
            line.setCell(i, (char)('A' + i), CellAttributes.DEFAULT);
        }
        line.shiftCellsRight(0, 3);
        // Columns 0-2 are cleared, columns 3-4 have old A and B
        assertTrue(line.getCell(0).isEmpty());
        assertTrue(line.getCell(1).isEmpty());
        assertTrue(line.getCell(2).isEmpty());
        assertEquals('A', line.getCell(3).getCharacter());
        assertEquals('B', line.getCell(4).getCharacter());
    }

    @Test
    @DisplayName("shiftCellsRight with negative fromColumn or count does nothing")
    void shiftCellsRightInvalidArgs() {
        TerminalLine line = new TerminalLine(5);
        line.setCell(0, 'A', CellAttributes.DEFAULT);
        line.shiftCellsRight(-1, 2); // negative from
        assertEquals('A', line.getCell(0).getCharacter());
        line.shiftCellsRight(0, 0); // zero count
        assertEquals('A', line.getCell(0).getCharacter());
        line.shiftCellsRight(0, -1); // negative count
        assertEquals('A', line.getCell(0).getCharacter());
    }


    @Test
    @DisplayName("Cell equals and hashCode")
    void cellEquality() {
        Cell a = new Cell('A', CellAttributes.DEFAULT);
        Cell b = new Cell('A', CellAttributes.DEFAULT);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("Cells with different characters are not equal")
    void cellInequalityChar() {
        Cell a = new Cell('A', CellAttributes.DEFAULT);
        Cell b = new Cell('B', CellAttributes.DEFAULT);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Cells with different attributes are not equal")
    void cellInequalityAttrs() {
        CellAttributes red = new CellAttributes(Color.RED, Color.DEFAULT, EnumSet.noneOf(Style.class));
        Cell a = new Cell('A', CellAttributes.DEFAULT);
        Cell b = new Cell('A', red);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Cell copyFrom copies character and attributes")
    void cellCopyFrom() {
        CellAttributes attrs = new CellAttributes(Color.GREEN, Color.BLUE, EnumSet.of(Style.BOLD));
        Cell source = new Cell('X', attrs);
        Cell target = new Cell();
        target.copyFrom(source);
        assertEquals(source, target);
    }

    @Test
    @DisplayName("TerminalLine clear with attributes preserves attributes on empty cells")
    void lineClearWithAttributes() {
        CellAttributes attrs = new CellAttributes(Color.RED, Color.BLUE, EnumSet.noneOf(Style.class));
        TerminalLine line = new TerminalLine(5);
        line.fill('X', CellAttributes.DEFAULT);
        line.clear(attrs);
        assertEquals("", line.getTextTrimmed());
        assertEquals(attrs, line.getCell(0).getAttributes());
        assertEquals(attrs, line.getCell(4).getAttributes());
    }
}
