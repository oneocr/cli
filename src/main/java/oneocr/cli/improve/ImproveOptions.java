package oneocr.cli.improve;

import java.util.List;
import oneocr.tesseract.TessLangs;

/**
 * Tuning for the Tesseract second pass. Thresholds are the values calibrated in
 * prp/01-prp.03.design.md; the language set is not defaulted and must come from the caller.
 */
public final class ImproveOptions {

    /** A line whose mean word confidence is below this is a candidate for re-reading. */
    public double minLineConfidence = 0.90;

    /** Punctuation-only boxes (":-", "***") must not trip the guard. */
    public int minAlphanumeric = 4;

    /** Cap on probed lines; they are spread round-robin across pages. */
    public int maxProbeSamples = 10;

    /** OneOCR line boxes tile the text with no gap, so an exact crop clips ai-matras. */
    public int verticalMarginPx = 5;

    /** Whitespace Tesseract wants around a line - added after cropping, never by widening it. */
    public int whiteBorderPx = 30;

    /** Re-render pages at this DPI for the Tesseract pass; low-DPI crops read poorly. */
    public int renderDpi = 300;

    /** Language everything is recognised alongside, normally the document's majority script. */
    public String baseLang = TessLangs.DEFAULT_BASE;

    /** Skip probing entirely and use this language. */
    public String forcedLang;

    /** Recognise every flagged band with all candidates at once instead of probing. */
    public boolean allCandidatesAtOnce;

    /** Scripts to consider. Empty means probing is impossible - the caller must supply these. */
    public List<String> candidates = List.of();

    public String allCandidatesSpec() {
        return String.join("+", candidates) + "+" + baseLang;
    }

    public boolean probingDisabled() {
        return forcedLang != null && !forcedLang.isBlank();
    }
}
