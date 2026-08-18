package xyz.jphil.win11_oneocr.tools.layout;

import java.util.ArrayList;
import java.util.List;
import xyz.jphil.win11_oneocr.OcrLine;
import xyz.jphil.win11_oneocr.OcrResult;

/**
 * One OneOCR line reduced to what layout analysis needs: its text, an axis aligned box, and the mean
 * confidence of its words.
 *
 * <p>{@code index} is the line's position in the OCR result, which is what lets a consumer of the
 * layout export join back to the semantic XHTML for per-word detail rather than have it duplicated
 * here. {@code confidence} is NaN when the line reports none.
 */
public record TextBox(int index, String text, Rect rect, double confidence) {

    public TextBox(int index, String text, Rect rect) {
        this(index, text, rect, Double.NaN);
    }

    public boolean hasConfidence() { return !Double.isNaN(confidence); }

    public static List<TextBox> from(OcrResult result) {
        var out = new ArrayList<TextBox>();
        if (result == null || result.lines() == null) return out;
        var lines = result.lines();
        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (line == null || line.text() == null || line.text().isBlank()) continue;
            var rect = rectOf(line);
            if (rect == null || rect.w() <= 0 || rect.h() <= 0) continue;
            out.add(new TextBox(i, line.text().strip(), rect, meanConfidence(line)));
        }
        return out;
    }

    /** The line box is authoritative when present; otherwise the union of the word boxes. */
    private static Rect rectOf(OcrLine line) {
        if (line.boundingBox() != null) return Rect.of(line.boundingBox());
        if (line.words() == null) return null;
        var wordRects = line.words().stream()
            .filter(wd -> wd.boundingBox() != null)
            .map(wd -> Rect.of(wd.boundingBox()))
            .toList();
        return wordRects.isEmpty() ? null : Rect.unionOf(wordRects);
    }

    private static double meanConfidence(OcrLine line) {
        if (line.words() == null || line.words().isEmpty()) return Double.NaN;
        var total = 0.0;
        var n = 0;
        for (var w : line.words()) {
            if (w == null) continue;
            total += w.confidence();
            n++;
        }
        return n == 0 ? Double.NaN : total / n;
    }
}
