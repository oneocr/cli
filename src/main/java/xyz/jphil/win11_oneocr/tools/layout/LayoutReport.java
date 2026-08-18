package xyz.jphil.win11_oneocr.tools.layout;

import java.util.ArrayList;
import java.util.List;

/** A one-glance dump of what the cut decided, for checking thresholds against a real page. */
public final class LayoutReport {

    private LayoutReport() {}

    public static String of(PageLayout page) {
        var lines = new ArrayList<String>();
        lines.add(String.format("page %d  %dx%d  lines=%d  em=%.1f  rowGap=%.1f",
            page.pageNumber(), page.width(), page.height(), page.root().boxes().size(),
            page.em(), page.rowGap()));
        describe(page.root(), page.options(), 1, lines);
        return String.join("\n", lines);
    }

    private static void describe(LayoutNode n, LayoutOptions o, int depth, List<String> out) {
        var pad = "  ".repeat(depth);
        switch (n) {
            case LinesNode l -> out.add(pad + "lines(" + l.lines().size() + ") "
                + preview(l.lines().isEmpty() ? "" : l.lines().get(0).text()));
            case StackNode s -> {
                out.add(pad + "stack(" + s.children().size() + ")");
                for (var c : s.children()) describe(c, o, depth + 1, out);
            }
            case ColumnsNode c -> {
                var grid = LayoutGrid.of(c, o);
                out.add(pad + "columns(" + c.children().size() + ") rows=" + grid.rows().size()
                    + " " + (grid.tabular(o.tableBandFrac()) ? "TABLE" : "flow")
                    + " align=" + grid.alignments());
                for (var child : c.children()) describe(child, o, depth + 1, out);
            }
        }
    }

    private static String preview(String s) {
        var one = s.replaceAll("\\s+", " ").strip();
        return "\"" + (one.length() <= 46 ? one : one.substring(0, 46) + "…") + "\"";
    }
}
