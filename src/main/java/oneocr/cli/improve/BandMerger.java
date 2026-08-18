package oneocr.cli.improve;

import java.util.*;
import oneocr.api.*;

public final class BandMerger {

    private BandMerger() {}

    /**
     * Groups OneOCR lines that share a visual line (vertical overlap greater than half the
     * shorter box) and marks a band suspect when any constituent line trips the confidence
     * guard. The guard tests confidence rather than script because OneOCR's failures are
     * sometimes Latin gibberish, which no script test can see.
     */
    public static List<LineBand> merge(OcrResult result, ImproveOptions o) {
        var boxes = new ArrayList<int[]>();      // x1,y1,x2,y2,lineIndex,suspect
        var lines = result.lines();
        for (var i = 0; i < lines.size(); i++) {
            var bb = lines.get(i).boundingBox();
            if (bb == null) continue;
            var b = bounds(bb);
            boxes.add(new int[]{b[0], b[1], b[2], b[3], i, isSuspect(lines.get(i), o) ? 1 : 0});
        }
        boxes.sort(Comparator.comparingDouble(a -> (a[1] + a[3]) / 2.0));

        var bands = new ArrayList<int[]>();       // x1,y1,x2,y2,suspect
        var members = new ArrayList<List<Integer>>();
        for (var b : boxes) {
            var hit = -1;
            for (var k = 0; k < bands.size(); k++) {
                var g = bands.get(k);
                var overlap = Math.min(g[3], b[3]) - Math.max(g[1], b[1]);
                var minH = Math.min(g[3] - g[1], b[3] - b[1]);
                if (overlap > 0.5 * minH) {
                    hit = k;
                    break;
                }
            }
            if (hit < 0) {
                bands.add(new int[]{b[0], b[1], b[2], b[3], b[5]});
                members.add(new ArrayList<>(List.of(b[4])));
            } else {
                var g = bands.get(hit);
                g[0] = Math.min(g[0], b[0]);
                g[1] = Math.min(g[1], b[1]);
                g[2] = Math.max(g[2], b[2]);
                g[3] = Math.max(g[3], b[3]);
                g[4] = Math.max(g[4], b[5]);
                members.get(hit).add(b[4]);
            }
        }

        var out = new ArrayList<LineBand>();
        for (var k = 0; k < bands.size(); k++) {
            var g = bands.get(k);
            var idx = new ArrayList<>(members.get(k));
            Collections.sort(idx);
            out.add(new LineBand(g[0], g[1], g[2] - g[0], g[3] - g[1], List.copyOf(idx), g[4] == 1));
        }
        return out;
    }

    static boolean isSuspect(OcrLine line, ImproveOptions o) {
        var words = line.words();
        if (words == null || words.isEmpty()) return false;
        var alnum = 0;
        for (var w : words) {
            var t = w.text();
            if (t == null) continue;
            for (var i = 0; i < t.length(); i++) {
                if (Character.isLetterOrDigit(t.charAt(i))) alnum++;
            }
        }
        if (alnum < o.minAlphanumeric) return false;
        var sum = 0.0;
        for (var w : words) sum += w.confidence();
        return sum / words.size() < o.minLineConfidence;
    }

    static int[] bounds(BoundingBox bb) {
        var xs = new double[]{bb.x1(), bb.x2(), bb.x3(), bb.x4()};
        var ys = new double[]{bb.y1(), bb.y2(), bb.y3(), bb.y4()};
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE;
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE;
        for (var v : xs) { minX = Math.min(minX, v); maxX = Math.max(maxX, v); }
        for (var v : ys) { minY = Math.min(minY, v); maxY = Math.max(maxY, v); }
        return new int[]{(int) Math.floor(minX), (int) Math.floor(minY),
                         (int) Math.ceil(maxX), (int) Math.ceil(maxY)};
    }
}
