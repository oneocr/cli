package xyz.jphil.win11_oneocr.tools.layout;

import java.util.List;

public enum Align {
    LEFT, CENTER, RIGHT;

    public String css() {
        return switch (this) { case LEFT -> "left"; case CENTER -> "center"; case RIGHT -> "right"; };
    }

    /**
     * Alignment of one box inside the region it belongs to, judged on its two side margins. The
     * tolerance also has a floor proportional to the region, because typesetting that is centred by
     * eye - and OCR boxes that are a few pixels loose - drift more on a wide measure than a narrow one.
     */
    public static Align of(Rect item, Rect region, double tol) {
        var slack = Math.max(tol, 0.015 * region.w());
        var leftGap = item.x() - region.x();
        var rightGap = region.right() - item.right();
        if (leftGap <= slack) return LEFT;
        if (Math.abs(leftGap - rightGap) <= slack) return CENTER;
        if (rightGap <= slack) return RIGHT;
        return LEFT;
    }

    public static Align majority(List<Align> aligns) {
        if (aligns.isEmpty()) return LEFT;
        var best = LEFT;
        var bestCount = -1;
        for (var candidate : values()) {
            var count = (int) aligns.stream().filter(a -> a == candidate).count();
            if (count > bestCount) { bestCount = count; best = candidate; }
        }
        return best;
    }
}
