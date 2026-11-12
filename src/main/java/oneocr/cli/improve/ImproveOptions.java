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

    /**
     * Off by default so every existing caller is unaffected. OneOCR's confidence guard
     * (BandMerger.isSuspect) can only flag a line it already emitted - a script it does not
     * recognise at all (e.g. Kannada) produces no line there to be low-confidence about, so
     * nothing is ever flagged and the second pass never runs. Turning this on adds a second,
     * independent detector (GapBandFinder) that looks for ink OneOCR's own lines do not cover
     * at all, and feeds what it finds through the exact same probing/recognition pipeline as a
     * confidence-flagged band. See prp/01-prp.03.design.md for OneOcrStage's context.
     */
    public boolean detectCoverageGaps = false;

    /** Fractions of page width/height to exclude from coverage-gap scanning - a decorative
     *  border, for instance, which is calibrated per book/template, not auto-detected. */
    public record ContentMargin(double top, double bottom, double left, double right) {
        public static final ContentMargin NONE = new ContentMargin(0, 0, 0, 0);
    }

    public ContentMargin contentMargin = ContentMargin.NONE;

    /** Grayscale value (0-255) below which a pixel counts as ink. */
    public int gapInkThreshold = 180;

    /** A row counts as "has ink" once at least this fraction of its scanned width is ink. */
    public double gapMinInkFraction = 0.02;

    /** Consecutive non-ink rows allowed inside one band before it is closed (bridges minor
     *  anti-aliasing gaps within a single line without merging separate lines together). */
    public int gapRowTolerance = 2;

    /** Minimum band height, in page-image pixels at renderDpi, to count as a real gap rather
     *  than noise. */
    public int gapMinHeightPx = 10;

    /** Minimum band width, in page-image pixels at renderDpi, to count as a real gap rather than
     *  a stray mark or watermark edge - a single character is far wider than this even in a
     *  narrow font, so a surviving 2-3px-wide "line" is noise, not text. Without this floor a
     *  faint watermark can produce dozens of degenerate slivers per page, each a real (if fast)
     *  Tesseract call; at book scale that is enough native calls to run the system out of memory,
     *  which is what happened before this floor was added. */
    public int gapMinWidthPx = 20;

    /** Hard cap on gap bands attempted per page. Real text pages in this pipeline's use never
     *  approach this; hitting it means the ink/margin tuning is picking up noise (a watermark, a
     *  scan artifact), and it is safer to under-read that page than to hammer the native
     *  Tesseract engine with an unbounded number of calls. */
    public int gapMaxBandsPerPage = 60;

    /** A coverage-gap band skips the comparative (vs. base language) verify once the document
     *  script is resolved - see DocumentImprover.specFor - but Tesseract's own mean confidence
     *  for the actual recognition attempt is still checked against this floor (0-100), so a
     *  stray mark or noise fragment does not get force-fed into the result as confident text. */
    public int gapMinRecognitionConfidence = 60;
}
