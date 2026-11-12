package oneocr.cli.engine;

import oneocr.api.BoundingBox;
import oneocr.api.OcrLine;
import oneocr.api.OcrResult;
import oneocr.api.OcrWord;
import oneocr.onnx.Quad;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts {@code oneocr.onnx} results into {@code oneocr.api} results.
 *
 * <p>Nothing is lost in the conversion, which was the open question in PRP 26. That PRP expected the
 * ONNX side to have "a real quadrilateral where the api side has a box" — but
 * {@link BoundingBox} has carried four corners all along, x1 through y4, for exactly this. The two
 * genuinely ONNX-only facts are the per-line script and whether the line is set vertically, and
 * {@link OcrLine} was widened to hold both rather than dropping them here.
 *
 * <p>The one real difference is confidence: OneOCR's DLL reports its own per-word confidence, while
 * the ONNX path derives it from the mean CTC character probability. Both land in 0..1 and neither is
 * calibrated against the other, so a confidence threshold tuned on one backend should be re-checked
 * on the other. That is a reason to record which engine ran, which the metadata now does.
 */
public final class OnnxResults {
    private OnnxResults() {}

    public static OcrResult toApi(oneocr.onnx.OcrResult source) {
        var lines = new ArrayList<OcrLine>(source.getLines().size());
        for (var line : source.getLines()) lines.add(toApi(line));
        return new OcrResult(source.fullText(), angle(source), lines);
    }

    static OcrLine toApi(oneocr.onnx.OcrLine line) {
        var words = new ArrayList<OcrWord>(line.getWords().size());
        for (var word : line.getWords())
            words.add(OcrWord.ocrWord(word.getText(), box(word.getBounds()), word.getConfidence()));
        return new OcrLine(line.getText(), box(line.getBounds()), List.copyOf(words),
                line.getScript(), line.getVertical());
    }

    static BoundingBox box(Quad q) {
        return q == null ? null : new BoundingBox(q.getX1(), q.getY1(), q.getX2(), q.getY2(),
                q.getX3(), q.getY3(), q.getX4(), q.getY4());
    }

    /**
     * The ONNX side reports the rotation it applied, in whole degrees counter-clockwise; the DLL
     * reports a fine-grained skew in {@code textAngle}. They are not the same measurement and this
     * does not pretend otherwise — it carries the rotation across as-is.
     */
    static double angle(oneocr.onnx.OcrResult source) {
        return source.getImageAngle() == null ? 0 : source.getImageAngle();
    }
}
