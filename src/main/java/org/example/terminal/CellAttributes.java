package org.example.terminal;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable set of attributes for a terminal cell: foreground color, background color, and style flags.
 */
public final class CellAttributes {

    public static final CellAttributes DEFAULT = new CellAttributes(Color.DEFAULT, Color.DEFAULT, EnumSet.noneOf(Style.class));

    private final Color foreground;
    private final Color background;
    private final Set<Style> styles;

    public CellAttributes(Color foreground, Color background, Set<Style> styles) {
        this.foreground = Objects.requireNonNull(foreground);
        this.background = Objects.requireNonNull(background);
        this.styles = styles.isEmpty() ? EnumSet.noneOf(Style.class) : EnumSet.copyOf(styles);
    }

    public Color getForeground() {
        return foreground;
    }

    public Color getBackground() {
        return background;
    }

    public Set<Style> getStyles() {
        return Collections.unmodifiableSet(styles);
    }

    public boolean hasStyle(Style style) {
        return styles.contains(style);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CellAttributes that)) return false;
        return foreground == that.foreground && background == that.background && styles.equals(that.styles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(foreground, background, styles);
    }

    @Override
    public String toString() {
        return "CellAttributes{fg=" + foreground + ", bg=" + background + ", styles=" + styles + "}";
    }
}
