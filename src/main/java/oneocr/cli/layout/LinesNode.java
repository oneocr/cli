package oneocr.cli.layout;

import java.util.List;

/**
 * A run of lines with no further internal structure, top to bottom. Ordering is by visual row and
 * then left to right - sorting on the raw top edge alone puts a paragraph number whose box sits a
 * pixel or two lower than its own text underneath that text.
 */
public record LinesNode(List<TextBox> lines) implements LayoutNode {

    static final double ROW_OVERLAP = 0.5;

    public LinesNode {
        lines = RowBands.of(lines, ROW_OVERLAP).stream().flatMap(List::stream).toList();
    }

    @Override public List<TextBox> boxes() { return lines; }
}
