package oneocr.cli.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Recursive X-Y cut. A region is split on whitespace that crosses it completely: vertical gutters
 * first, so that columns are recovered before the rows that run through them, then horizontal gaps.
 *
 * <p>A vertical cut is only accepted when at least one visual row actually straddles it. Without
 * that test a page number floating in the top right corner opens a full height gutter down the page
 * and everything below it is silently re-ordered.
 */
public final class XyCut {

    private final LayoutOptions o;
    private final double em;

    private XyCut(LayoutOptions o, double em) { this.o = o; this.em = em; }

    public static LayoutNode analyze(List<TextBox> boxes, LayoutOptions o) {
        if (boxes == null || boxes.isEmpty()) return new LinesNode(List.of());
        return new XyCut(o, RowBands.em(boxes)).cut(boxes, 0);
    }

    private LayoutNode cut(List<TextBox> boxes, int depth) {
        if (boxes.size() < 2 || depth >= o.maxDepth()) return new LinesNode(boxes);
        var region = Rect.unionOf(boxes.stream().map(TextBox::rect).toList());

        var columns = split(boxes, true, Math.max(o.minGutterEm() * em, o.minGutterFrac() * region.w()));
        if (columns.size() > 1 && supported(columns, region) && straddled(columns))
            return new ColumnsNode(columns.stream().map(part -> cut(part, depth + 1)).toList());

        var rows = split(boxes, false, o.minRowGapEm() * em);
        if (rows.size() > 1)
            return new StackNode(rows.stream().map(part -> cut(part, depth + 1)).toList());

        return new LinesNode(boxes);
    }

    /**
     * Splits on every gap that is both wide enough in absolute terms and within
     * {@code siblingGapRatio} of the widest gap present, which is what stops ordinary line leading
     * from being treated as a block boundary on a loosely set page.
     */
    private List<List<TextBox>> split(List<TextBox> boxes, boolean vertical, double minGap) {
        var gaps = gaps(boxes, vertical, minGap);
        if (gaps.isEmpty()) return List.of(boxes);

        var widest = gaps.stream().mapToDouble(g -> g[1] - g[0]).max().orElse(0);
        var floor = Math.max(minGap, o.siblingGapRatio() * widest);
        var cuts = gaps.stream().filter(g -> g[1] - g[0] >= floor).map(g -> (g[0] + g[1]) / 2).sorted().toList();
        if (cuts.isEmpty()) return List.of(boxes);

        var parts = new ArrayList<List<TextBox>>();
        for (var i = 0; i <= cuts.size(); i++) parts.add(new ArrayList<>());
        for (var box : boxes) {
            var start = vertical ? box.rect().x() : box.rect().y();
            var slot = 0;
            while (slot < cuts.size() && start > cuts.get(slot)) slot++;
            parts.get(slot).add(box);
        }
        return parts.stream().filter(p -> !p.isEmpty()).map(p -> (List<TextBox>) p).toList();
    }

    /** Empty runs between the merged projections of every box onto one axis. */
    private static List<double[]> gaps(List<TextBox> boxes, boolean vertical, double minGap) {
        var spans = boxes.stream()
            .map(b -> vertical
                ? new double[]{b.rect().x(), b.rect().right()}
                : new double[]{b.rect().y(), b.rect().bottom()})
            .sorted(Comparator.comparingDouble(s -> s[0]))
            .toList();

        var out = new ArrayList<double[]>();
        var reach = spans.get(0)[1];
        for (var span : spans) {
            if (span[0] - reach >= minGap) out.add(new double[]{reach, span[0]});
            reach = Math.max(reach, span[1]);
        }
        return out;
    }

    /**
     * True when every candidate column is present over a shared span of the region's height. A page
     * number in the top right corner opens a gutter that runs the whole length of the page, and
     * without this the entire body below it is swallowed into the "left column" and re-ordered.
     */
    private boolean supported(List<List<TextBox>> parts, Rect region) {
        var top = region.y();
        var bottom = region.bottom();
        for (var part : parts) {
            var b = Rect.unionOf(part.stream().map(TextBox::rect).toList());
            top = Math.max(top, b.y());
            bottom = Math.min(bottom, b.bottom());
        }
        return bottom - top >= o.gutterSupportFrac() * region.h();
    }

    /** True when some visual row has content in more than one of the candidate columns. */
    private boolean straddled(List<List<TextBox>> parts) {
        var owner = new HashMap<Integer, Integer>();
        for (var i = 0; i < parts.size(); i++)
            for (var box : parts.get(i)) owner.put(box.index(), i);

        var all = parts.stream().flatMap(List::stream).toList();
        for (var band : RowBands.of(all, o.bandOverlapFrac())) {
            var seen = new HashSet<Integer>();
            for (var box : band) seen.add(owner.get(box.index()));
            if (seen.size() > 1) return true;
        }
        return false;
    }
}
