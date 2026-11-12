package oneocr.cli;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import oneocr.api.OcrResult;

/**
 * Metadata information about the OCR processing.
 * {@code engine} records which engines produced the text, so a file improved by the Tesseract
 * second pass is distinguishable from a plain OneOCR one without diffing content.
 */
public record OcrMetadata(
    String file,
    int width, int height,
    String timestampUTCISO,
    String plainText,
    String engine,
    OcrMetrics metrics
) {
    public static final String
        ENGINE_ONEOCR = "oneocr",
        ENGINE_ONEOCR_ONNX = "oneocr-onnx",
        ENGINE_ONEOCR_TESSERACT = "oneocr+tesseract";

    /**
     * The engine tag for a page read by {@code base} and then improved by the Tesseract second pass.
     * Composed rather than constant so that which backend read it first is not lost — the DLL and
     * the ONNX path do not calibrate confidence the same way, and a confidence threshold tuned on
     * one is not evidence about the other.
     */
    public static String improvedBy(String base) {
        var engine = base == null || base.isBlank() ? ENGINE_ONEOCR : base;
        return engine.contains("tesseract") ? engine : engine + "+tesseract";
    }

    public static OcrMetadata create(String imageFile, int imageWidth, int imageHeight, OcrResult or) {
        return create(imageFile, imageWidth, imageHeight, or, ENGINE_ONEOCR);
    }

    public static OcrMetadata create(String imageFile, int imageWidth, int imageHeight, OcrResult or, String engine) {
        var utcTimestamp = Instant.now()
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT);

        return new OcrMetadata(
            imageFile,
            imageWidth, imageHeight,
            utcTimestamp,
            or.text(),
            engine == null || engine.isBlank() ? ENGINE_ONEOCR : engine,
            new OcrMetrics(or)
        );
    }

    public boolean tesseractImproved() {
        return engine != null && engine.contains("tesseract");
    }
}
