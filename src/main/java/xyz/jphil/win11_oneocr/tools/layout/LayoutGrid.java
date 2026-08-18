package xyz.jphil.win11_oneocr.tools.layout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A {@link ColumnsNode} paired back up into rows: OCR emits the two halves of a cause title as
 * separate lines in whatever order it found them, and this is where they are put back on the same
 * row by vertical overlap.
 */
public record LayoutGrid(List<Rect> columns, List<Align> alignments, List<List<GridCell>> rows, Rect bounds) {

    public int columnCount() { return columns.size(); }

    /**
     * True when the columns really do run in parallel. A newspaper style flow, where each row holds
     * only one column's line, fails this and must be read down one column and then the next.
     */
    public boolean tabular(double frac) {
        if (rows.isEmpty()) return false;
        var shared = rows.stream().filter(r -> r.stream().filter(c -> !c.empty()).count() > 1).count();
        return (double) shared / rows.size() >= frac;
    }

    /** Column widths as percentages that sum to 100, gutters split between their neighbours. */
    public double[] widthPercents() {
        var edges = edges();
        var out = new double[columns.size()];
        for (var i = 0; i < columns.size(); i++) out[i] = 100.0 * (edges[i + 1] - edges[i]) / bounds.w();
        return out;
    }

    /** The slot each column occupies, gutters included - the frame its content is aligned within. */
    public List<Rect> columnRegions() {
        var edges = edges();
        var out = new ArrayList<Rect>();
        for (var i = 0; i < columns.size(); i++)
            out.add(new Rect(edges[i], bounds.y(), edges[i + 1] - edges[i], bounds.h()));
        return out;
    }

    private double[] edges() {
        var edges = new double[columns.size() + 1];
        edges[0] = bounds.x();
        edges[columns.size()] = bounds.right();
        for (var i = 1; i < columns.size(); i++)
            edges[i] = (columns.get(i - 1).right() + columns.get(i).x()) / 2;
        return edges;
    }

    public static LayoutGrid of(ColumnsNode node, LayoutOptions o) {
        var children = node.children();
        var owner = new HashMap<Integer, Integer>();
        var colRects = new ArrayList<Rect>();
        for (var i = 0; i < children.size(); i++) {
            for (var box : children.get(i).boxes()) owner.put(box.index(), i);
            colRects.add(children.get(i).bounds());
        }

        var rows = new ArrayList<List<GridCell>>();
        for (var band : RowBands.of(node.boxes(), o.bandOverlapFrac())) {
            var buckets = new ArrayList<List<TextBox>>();
            for (var i = 0; i < children.size(); i++) buckets.add(new ArrayList<>());
            for (var box : band) buckets.get(owner.get(box.index())).add(box);
            rows.add(buckets.stream().map(GridCell::new).toList());
        }

        // Alignment is judged against the slot a column occupies, gutters included - not against the
        // column's own extent, which for a single-cell column is the cell itself and says nothing.
        var bounds = node.bounds();
        var slots = new LayoutGrid(colRects, List.of(), rows, bounds).columnRegions();
        var tol = o.alignTolEm() * RowBands.em(node.boxes());
        var alignments = new ArrayList<Align>();
        for (var i = 0; i < children.size(); i++) {
            var slot = slots.get(i);
            final var idx = i;
            var perCell = rows.stream().map(r -> r.get(idx)).filter(c -> !c.empty())
                .map(c -> Align.of(c.rect(), slot, tol)).toList();
            alignments.add(Align.majority(perCell));
        }
        return new LayoutGrid(colRects, alignments, rows, bounds);
    }
}
