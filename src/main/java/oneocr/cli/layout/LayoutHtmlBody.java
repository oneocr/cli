package oneocr.cli.layout;

import java.util.ArrayList;
import java.util.List;
import luvml.E;
import luvx.Frag_I;
import static luvml.A.*;
import static luvml.E.*;
import static luvml.Frags.frags;

/**
 * Renders one cut tree as readable HTML: paragraphs keep their alignment, columns that run in
 * parallel become a table, a lone split row becomes spans on one line, and columns that merely flow
 * past each other are written out one after the other.
 */
public final class LayoutHtmlBody {

    private final LayoutOptions o;
    private final double em;

    LayoutHtmlBody(PageLayout page) {
        this.o = page.options();
        this.em = page.em();
    }

    Frag_I<?> node(LayoutNode n, Rect region) {
        return switch (n) {
            case LinesNode l -> lines(l, region);
            case StackNode s -> frags(coalesce(s).stream().map(c -> node(c, region)).toList());
            case ColumnsNode c -> columns(c, region);
        };
    }

    /**
     * The cut splits a block at any gap wide enough to matter, which on a double spaced page is
     * every line. Runs of plain lines closer together than a paragraph break are put back together
     * so they render as one paragraph rather than as a column of one-line paragraphs.
     */
    private List<LayoutNode> coalesce(StackNode s) {
        var blockGap = BlockGap.forNode(s, o, em);
        var out = new ArrayList<LayoutNode>();
        var run = new ArrayList<TextBox>();
        var previousBottom = 0.0;

        for (var child : s.children()) {
            if (child.isEmpty()) continue;
            var joins = child instanceof LinesNode
                && !run.isEmpty() && child.bounds().y() - previousBottom < blockGap;
            if (!joins && !run.isEmpty()) { out.add(new LinesNode(run)); run = new ArrayList<>(); }
            if (child instanceof LinesNode ln) run.addAll(ln.lines());
            else out.add(child);
            previousBottom = child.bounds().bottom();
        }
        if (!run.isEmpty()) out.add(new LinesNode(run));
        return out;
    }

    private Frag_I<?> columns(ColumnsNode c, Rect region) {
        var grid = LayoutGrid.of(c, o);
        if (grid.columnCount() < 2) return node(c.children().get(0), region);
        if (!grid.tabular(o.tableBandFrac())) return flow(c, grid);
        return grid.rows().size() == 1 ? inlineRow(grid) : gridTable(grid);
    }

    /** Case D - the columns really are parallel, so pairing them by row carries information. */
    private Frag_I<?> gridTable(LayoutGrid grid) {
        var widths = grid.widthPercents();
        var cols = new ArrayList<Frag_I<?>>();
        for (var w : widths) cols.add(col(style("width:" + pct(w) + "%")));

        var tol = o.alignTolEm() * em;
        var rows = new ArrayList<Frag_I<?>>();
        for (var row : grid.rows()) {
            var filled = row.stream().filter(c -> !c.empty()).toList();
            if (filled.size() == 1) {
                var only = filled.get(0);
                rows.add(tr(td(colspan(String.valueOf(row.size())),
                    style("text-align:" + Align.of(only.rect(), grid.bounds(), tol).css()),
                    text(only.text()))));
                continue;
            }
            var cells = new ArrayList<Frag_I<?>>();
            for (var i = 0; i < row.size(); i++) {
                var align = grid.alignments().get(i);
                cells.add(row.get(i).empty() ? td() : td(style("text-align:" + align.css()), text(row.get(i).text())));
            }
            rows.add(tr(frags(cells)));
        }
        return table(class_("layoutTable"), colgroup(frags(cols)), tbody(frags(rows)));
    }

    /** Case E - one row only, so claim nothing about a table; just hold the parts apart. */
    private Frag_I<?> inlineRow(LayoutGrid grid) {
        var widths = grid.widthPercents();
        var row = grid.rows().get(0);
        var spans = new ArrayList<Frag_I<?>>();
        for (var i = 0; i < row.size(); i++) {
            if (i > 0) spans.add(LiteralText.tab());
            spans.add(E.span(
                style("width:" + pct(widths[i]) + "%;text-align:" + grid.alignments().get(i).css()),
                text(row.get(i).text())));
        }
        return p(class_("row"), frags(spans));
    }

    /** Case C - a newspaper style flow: read one column to the end, then the next. */
    private Frag_I<?> flow(ColumnsNode c, LayoutGrid grid) {
        var regions = grid.columnRegions();
        var cols = new ArrayList<Frag_I<?>>();
        for (var i = 0; i < c.children().size(); i++) {
            var child = c.children().get(i);
            if (child.isEmpty()) continue;
            cols.add(div(class_("col"), node(child, regions.get(i))));
        }
        return div(class_("colFlow"), frags(cols));
    }

    /**
     * Case F - consecutive rows sharing an alignment become one paragraph. Pieces of the same
     * visual row are rejoined first: with no column here, they are one line of text.
     */
    private Frag_I<?> lines(LinesNode l, Rect region) {
        var tol = o.alignTolEm() * em;
        var bands = RowBands.of(l.lines(), o.bandOverlapFrac());
        var rects = bands.stream().map(b -> Rect.unionOf(b.stream().map(TextBox::rect).toList())).toList();
        var blockGap = BlockGap.forNode(l, o, em);
        var out = new ArrayList<Frag_I<?>>();
        var buffer = new ArrayList<List<TextBox>>();
        var align = Align.LEFT;
        var previous = (Rect) null;

        for (var i = 0; i < bands.size(); i++) {
            var rect = rects.get(i);
            var rowAlign = Align.of(rect, region, tol);
            var broken = previous != null && (rowAlign != align
                || rect.y() - previous.bottom() >= blockGap);
            if (broken) { out.add(para(buffer, align)); buffer = new ArrayList<>(); }
            buffer.add(bands.get(i));
            align = rowAlign;
            previous = rect;
        }
        if (!buffer.isEmpty()) out.add(para(buffer, align));
        return frags(out);
    }

    /**
     * Each visual row becomes its own span so the line can be addressed. {@code data-i} is the OCR
     * line index, which is what lets a reader join this reordered view back to the semantic XHTML for
     * per-word confidence; {@code data-p} is the line's mean confidence, enough on its own to pick out
     * the lines worth a second look.
     */
    private static Frag_I<?> para(List<List<TextBox>> rows, Align align) {
        var parts = new ArrayList<Frag_I<?>>();
        for (var i = 0; i < rows.size(); i++) {
            if (i > 0) parts.add(br());
            parts.add(lineSpan(rows.get(i)));
        }
        return p(class_("al-" + align.css()), frags(parts));
    }

    private static Frag_I<?> lineSpan(List<TextBox> band) {
        var joined = band.stream().map(TextBox::text)
            .collect(java.util.stream.Collectors.joining(" "));
        var scored = band.stream().filter(TextBox::hasConfidence).toList();
        var attrs = new ArrayList<Frag_I<?>>();
        attrs.add(class_("ln"));
        attrs.add(data("i", band.stream().map(b -> String.valueOf(b.index()))
            .collect(java.util.stream.Collectors.joining(","))));
        if (!scored.isEmpty()) {
            var mean = scored.stream().mapToDouble(TextBox::confidence).average().orElse(Double.NaN);
            attrs.add(data("p", String.format("%.3f", mean)));
        }
        attrs.add(text(joined));
        return E.span(frags(attrs));
    }

    private static String pct(double value) { return String.format("%.2f", value); }
}
