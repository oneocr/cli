package oneocr.cli.improve;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.*;
import oneocr.api.OcrResult;

/**
 * Finds page regions with visible ink that OneOCR's own line detection missed entirely - a
 * DETECTION gap, which {@link BandMerger}'s confidence guard cannot see because it only ever
 * inspects lines OneOCR already emitted. Scans row by row (outside any row an existing OneOCR
 * line already covers, and outside the caller's content margin) for ink, groups consecutive
 * ink rows into bands, and returns them as ordinary {@link LineBand}s - unreplaced originals
 * (empty lineIndices) - so they flow through the exact same Tesseract probing/recognition
 * pipeline as a confidence-flagged band.
 */
public final class GapBandFinder {

    private GapBandFinder() {}

    public static List<LineBand> find(BufferedImage page, OcrResult result, double scale, ImproveOptions o) {
        var m = o.contentMargin;
        var top = (int) Math.round(page.getHeight() * m.top());
        var bottom = page.getHeight() - (int) Math.round(page.getHeight() * m.bottom());
        var left = (int) Math.round(page.getWidth() * m.left());
        var right = page.getWidth() - (int) Math.round(page.getWidth() * m.right());
        if (bottom <= top || right <= left) return List.of();

        var data = grayBytes(page);
        var width = page.getWidth();
        var covered = coveredRows(result, scale, page.getHeight());
        var hasInk = new boolean[bottom - top];
        for (var y = top; y < bottom; y++) {
            if (covered[y]) continue;
            hasInk[y - top] = rowInkFraction(data, width, y, left, right, o.gapInkThreshold) >= o.gapMinInkFraction;
        }

        var out = new ArrayList<LineBand>();
        var i = 0;
        var n = hasInk.length;
        while (i < n) {
            if (!hasInk[i]) { i++; continue; }
            var start = i;
            var lastInk = i;
            var j = i + 1;
            while (j < n) {
                if (hasInk[j]) { lastInk = j; j++; continue; }
                var gapLen = 0;
                var probe = j;
                while (probe < n && !hasInk[probe] && gapLen < o.gapRowTolerance) { gapLen++; probe++; }
                if (gapLen >= o.gapRowTolerance) break;
                j = probe;
            }
            i = j;

            var y1 = top + start;
            var y2 = top + lastInk + 1;
            if (y2 - y1 < o.gapMinHeightPx) continue;
            var xExtent = inkColumnExtent(data, width, y1, y2, left, right, o.gapInkThreshold);
            if (xExtent == null) continue;
            if (xExtent[1] - xExtent[0] < o.gapMinWidthPx) continue;
            if (out.size() >= o.gapMaxBandsPerPage) break;
            out.add(new LineBand(
                (int) Math.round(xExtent[0] / scale), (int) Math.round(y1 / scale),
                (int) Math.round((xExtent[1] - xExtent[0]) / scale), (int) Math.round((y2 - y1) / scale),
                List.of(), true));
        }
        return out;
    }

    static double rowInkFraction(byte[] gray, int width, int y, int left, int right, int threshold) {
        var dark = 0;
        var base = y * width;
        for (var x = left; x < right; x++) {
            if ((gray[base + x] & 0xFF) < threshold) dark++;
        }
        return dark / (double) (right - left);
    }

    static int[] inkColumnExtent(byte[] gray, int width, int y1, int y2, int left, int right, int threshold) {
        var x1 = right;
        var x2 = left;
        for (var y = y1; y < y2; y++) {
            var base = y * width;
            for (var x = left; x < right; x++) {
                if ((gray[base + x] & 0xFF) < threshold) {
                    if (x < x1) x1 = x;
                    if (x > x2) x2 = x;
                }
            }
        }
        return x2 > x1 ? new int[]{x1, x2 + 1} : null;
    }

    /** Marks every page-image row covered by an existing OneOCR line's bounding box. */
    static boolean[] coveredRows(OcrResult result, double scale, int pageHeight) {
        var covered = new boolean[pageHeight];
        for (var line : result.lines()) {
            var bb = line.boundingBox();
            if (bb == null) continue;
            var minY = Math.min(Math.min(bb.y1(), bb.y2()), Math.min(bb.y3(), bb.y4())) * scale;
            var maxY = Math.max(Math.max(bb.y1(), bb.y2()), Math.max(bb.y3(), bb.y4())) * scale;
            var y1 = Math.max(0, (int) Math.floor(minY));
            var y2 = Math.min(pageHeight, (int) Math.ceil(maxY));
            for (var y = y1; y < y2; y++) covered[y] = true;
        }
        return covered;
    }

    static byte[] grayBytes(BufferedImage src) {
        var gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        var g = gray.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return ((DataBufferByte) gray.getRaster().getDataBuffer()).getData();
    }
}
