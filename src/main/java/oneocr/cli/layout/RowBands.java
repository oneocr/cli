package oneocr.cli.layout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Groups boxes that share a visual row. Each band keeps the y-range of the box that opened it and
 * never widens it, so a column of slightly drifting lines cannot chain into one enormous band.
 */
public final class RowBands {

    private RowBands() {}

    public static List<List<TextBox>> of(List<TextBox> boxes, double overlapFrac) {
        var sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingDouble(b -> b.rect().cy()));

        var bands = new ArrayList<List<TextBox>>();
        var seed = (Rect) null;
        var current = (List<TextBox>) null;

        for (var box : sorted) {
            var r = box.rect();
            if (current != null && r.vOverlap(seed) >= overlapFrac * Math.min(seed.h(), r.h())) {
                current.add(box);
                continue;
            }
            current = new ArrayList<>();
            current.add(box);
            bands.add(current);
            seed = r;
        }
        for (var band : bands) band.sort(Comparator.comparingDouble(b -> b.rect().x()));
        return bands;
    }

    /** Median height of the boxes, the unit every vertical threshold is expressed in. */
    public static double em(List<TextBox> boxes) {
        if (boxes.isEmpty()) return 1;
        var heights = boxes.stream().mapToDouble(b -> b.rect().h()).sorted().toArray();
        return Math.max(1, heights[heights.length / 2]);
    }

    /** Median gap between consecutive rows - the page's own leading, whatever its type size. */
    public static double medianGap(List<TextBox> boxes, double overlapFrac) {
        var bands = of(boxes, overlapFrac);
        if (bands.size() < 2) return 0;
        var gaps = new ArrayList<Double>();
        for (var i = 1; i < bands.size(); i++) {
            var above = Rect.unionOf(bands.get(i - 1).stream().map(TextBox::rect).toList());
            var below = Rect.unionOf(bands.get(i).stream().map(TextBox::rect).toList());
            gaps.add(Math.max(0, below.y() - above.bottom()));
        }
        gaps.sort(Double::compare);
        return gaps.get(gaps.size() / 2);
    }
}
