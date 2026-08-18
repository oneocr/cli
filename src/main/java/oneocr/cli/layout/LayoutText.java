package oneocr.cli.layout;

import java.util.ArrayList;
import java.util.List;
import static java.util.stream.Collectors.joining;

/**
 * Plain text that keeps the page's shape. Lines that sit beside each other on the page come out
 * beside each other here, instead of interleaved down a single column in whatever order the engine
 * happened to report them.
 */
public final class LayoutText {

    private final LayoutOptions o;
    private final double em;
    private final TextMode mode;

    private LayoutText(PageLayout page, TextMode mode) {
        this.o = page.options();
        this.em = page.em();
        this.mode = mode;
    }

    public static String render(PageLayout page, TextMode mode) {
        return new LayoutText(page, mode).node(page.root()).stripTrailing();
    }

    public static String render(List<PageLayout> pages, TextMode mode) {
        if (pages.size() == 1) return render(pages.get(0), mode);
        return pages.stream()
            .map(p -> "=== Page " + p.pageNumber() + " ===\n" + render(p, mode))
            .collect(joining("\n\n"));
    }

    private String node(LayoutNode n) {
        return switch (n) {
            case LinesNode l -> lines(l);
            case StackNode s -> stack(s);
            case ColumnsNode c -> columns(c);
        };
    }

    /**
     * No column was found here, so anything sharing a visual row is one line of text that the engine
     * happened to report in pieces - a paragraph number, a wide gap left by justification - and it
     * is put back together rather than stacked.
     */
    private String lines(LinesNode l) {
        return RowBands.of(l.lines(), o.bandOverlapFrac()).stream()
            .map(band -> band.stream().map(TextBox::text).collect(joining(" ")))
            .collect(joining("\n"));
    }

    private String stack(StackNode s) {
        var children = s.children().stream().filter(c -> !c.isEmpty()).toList();
        var blockGap = BlockGap.forNode(s, o, em);
        var parts = new ArrayList<String>();
        var previous = (LayoutNode) null;
        for (var child : children) {
            if (previous != null) {
                var gap = child.bounds().y() - previous.bounds().bottom();
                parts.add(gap >= blockGap ? "\n\n" : "\n");
            }
            parts.add(node(child));
            previous = child;
        }
        return String.join("", parts);
    }

    private String columns(ColumnsNode c) {
        var grid = LayoutGrid.of(c, o);
        if (mode == TextMode.READING || !grid.tabular(o.tableBandFrac()))
            return c.children().stream().filter(ch -> !ch.isEmpty()).map(this::node).collect(joining("\n\n"));

        var widths = mode == TextMode.ALIGNED ? cellWidths(grid) : null;
        return grid.rows().stream().map(row -> row(row, grid, widths)).collect(joining("\n"));
    }

    private String row(List<GridCell> cells, LayoutGrid grid, int[] widths) {
        // A heading or a total sitting alone on its row spans the whole width; leading tabs there
        // would only suggest a column that has nothing in it.
        var filled = cells.stream().filter(c -> !c.empty()).toList();
        if (filled.size() <= 1) return filled.isEmpty() ? "" : filled.get(0).text();

        var texts = new ArrayList<String>(cells.stream().map(GridCell::text).toList());
        while (!texts.isEmpty() && texts.get(texts.size() - 1).isEmpty()) texts.remove(texts.size() - 1);
        if (widths == null) return String.join("\t", texts);

        var padded = new ArrayList<String>();
        for (var i = 0; i < texts.size(); i++) {
            var align = grid.alignments().get(i);
            // The last column only needs padding if it is right aligned, where the padding is what
            // makes a column of figures line up.
            var width = i < texts.size() - 1 || align == Align.RIGHT ? widths[i] : 0;
            padded.add(pad(texts.get(i), width, align));
        }
        return String.join("  ", padded).stripTrailing();
    }

    private static int[] cellWidths(LayoutGrid grid) {
        var widths = new int[grid.columnCount()];
        for (var row : grid.rows())
            for (var i = 0; i < row.size(); i++) widths[i] = Math.max(widths[i], row.get(i).text().length());
        return widths;
    }

    private static String pad(String s, int width, Align align) {
        var fill = width - s.length();
        if (fill <= 0) return s;
        return align == Align.RIGHT ? " ".repeat(fill) + s : s + " ".repeat(fill);
    }
}
