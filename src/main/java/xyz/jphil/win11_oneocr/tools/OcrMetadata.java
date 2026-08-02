package xyz.jphil.win11_oneocr.tools;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import xyz.jphil.win11_oneocr.OcrResult;

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
        ENGINE_ONEOCR_TESSERACT = "oneocr+tesseract";

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
