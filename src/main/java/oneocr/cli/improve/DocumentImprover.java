package oneocr.cli.improve;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import oneocr.api.*;
import oneocr.tesseract.*;

/**
 * The Tesseract second pass. OneOCR stays the spine: its geometry and its (better) Latin
 * recognition are kept, and Tesseract only re-reads the minority of lines the confidence guard
 * flags. See prp/01-prp.03.design.md for the measurements behind every threshold here.
 */
public final class DocumentImprover implements AutoCloseable {

    final ImproveOptions o;
    final TessPool pool;
    final List<String> log = new ArrayList<>();
    List<String> detectedLangs = List.of();

    public DocumentImprover(ImproveOptions options) {
        this.o = options;
        this.pool = new TessPool(options.candidates, options.baseLang, options.renderDpi);
    }

    public List<String> log() {
        return log;
    }

    public List<String> detectedLangs() {
        return detectedLangs;
    }

    public List<ImprovedPage> improve(List<ImprovePage> pages, PageImageSupplier images) throws IOException {
        var flaggedByPage = new LinkedHashMap<Integer, List<LineBand>>();
        for (var p : pages) {
            var bands = BandMerger.merge(p.result(), o);
            var flagged = bands.stream().filter(LineBand::suspect).toList();
            if (!flagged.isEmpty()) flaggedByPage.put(p.pageNo(), flagged);
        }

        var totalFlagged = flaggedByPage.values().stream().mapToInt(List::size).sum();
        if (totalFlagged == 0) {
            log.add("no low-confidence lines found; nothing to re-read");
            return unchanged(pages);
        }
        log.add(totalFlagged + " low-confidence line(s) across " + flaggedByPage.size() + " page(s)");

        detectedLangs = decideLanguages(flaggedByPage, images, pages);
        if (detectedLangs.isEmpty()) {
            log.add("no foreign script identified; leaving OneOCR output unchanged");
            return unchanged(pages);
        }
        log.add("script(s): " + String.join(", ", detectedLangs));

        var out = new ArrayList<ImprovedPage>();
        for (var p : pages) {
            var flagged = flaggedByPage.get(p.pageNo());
            if (flagged == null || flagged.isEmpty()) {
                out.add(new ImprovedPage(p.pageNo(), p.result(), 0, 0, List.of()));
                continue;
            }
            out.add(improvePage(p, flagged, images));
        }
        return out;
    }

    private ImprovedPage improvePage(ImprovePage p, List<LineBand> flagged, PageImageSupplier images) throws IOException {
        var page = images.image(p.pageNo());
        var scale = page.getWidth() / (double) Math.max(1, p.ocrImageWidth());
        var replacements = new HashMap<Integer, OcrLine>();
        var drop = new HashSet<Integer>();
        var used = new LinkedHashSet<String>();
        var replaced = 0;

        for (var band : flagged) {
            var crop = BandCropper.crop(page, band, scale, o);
            var lang = specFor(crop);
            if (lang == null) continue;
            var res = pool.recognizeSpec(crop, lang);
            if (res.isBlank()) continue;
            var origin = BandCropper.origin(page, band, scale, o);
            var line = toOcrLine(res, origin, scale, o.whiteBorderPx);
            if (line.words().isEmpty()) continue;
            replacements.put(band.firstLine(), line);
            for (var i : band.lineIndices()) {
                if (i != band.firstLine()) drop.add(i);
            }
            used.add(lang);
            replaced++;
        }

        return new ImprovedPage(p.pageNo(), splice(p.result(), replacements, drop),
            flagged.size(), replaced, List.copyOf(used));
    }

    /**
     * With one script the whole document uses it; with several, every band picks its own, because
     * a union set of three languages nearly doubles the error rate. Returns a literal Tesseract
     * language spec, or null when this band should be left exactly as OneOCR read it.
     *
     * Every band is confirmed before replacement, even when the document's script is already
     * resolved. The confidence guard that selects candidate lines also catches ordinary
     * low-confidence English - on the reference 25-page order, 45 of 49 flagged lines were such
     * false positives - and replacing those would swap OneOCR's better Latin for Tesseract's.
     */
    private String specFor(BufferedImage crop) throws IOException {
        if (o.allCandidatesAtOnce) {
            var spec = o.allCandidatesSpec();
            return pool.verifySpec(crop, spec, spec).accepted() ? spec : null;
        }
        if (o.probingDisabled()) {
            return pool.verify(crop, o.forcedLang).accepted() ? TessLangs.with(o.forcedLang, o.baseLang) : null;
        }
        if (detectedLangs.size() == 1) {
            var lang = detectedLangs.get(0);
            return pool.verify(crop, lang).accepted() ? TessLangs.with(lang, o.baseLang) : null;
        }
        var vote = pool.probe(crop);
        return vote.accepted() ? TessLangs.with(vote.lang(), o.baseLang) : null;
    }

    private List<String> decideLanguages(Map<Integer, List<LineBand>> flaggedByPage,
                                         PageImageSupplier images, List<ImprovePage> pages) throws IOException {
        if (o.allCandidatesAtOnce) {
            log.add("all-candidates mode requested, probing skipped (costs accuracy, needs no hint)");
            return List.of(o.allCandidatesSpec());
        }
        if (o.probingDisabled()) {
            log.add("language forced to '" + o.forcedLang + "', probing skipped");
            return List.of(o.forcedLang);
        }
        var widths = new HashMap<Integer, Integer>();
        for (var p : pages) widths.put(p.pageNo(), p.ocrImageWidth());

        var samples = ProbeSampler.sample(flaggedByPage, o.maxProbeSamples);
        var votes = new ArrayList<LangVote>();
        for (var s : samples) {
            var page = images.image(s[0]);
            var band = flaggedByPage.get(s[0]).get(s[1]);
            var scale = page.getWidth() / (double) Math.max(1, widths.getOrDefault(s[0], page.getWidth()));
            var vote = pool.probe(BandCropper.crop(page, band, scale, o));
            votes.add(vote);
            log.add("  probe p" + s[0] + " -> " + vote);
        }
        var tally = TessPool.tally(votes);
        log.add("probed " + votes.size() + " line(s), accepted " + votes.stream().filter(LangVote::accepted).count());
        return tally;
    }

    /**
     * Adding a third language nearly doubles the error, so a band is never given a union set:
     * the caller either resolves one script for the document or probes each band separately.
     */
    static OcrLine toOcrLine(TessResult res, int[] origin, double scale, int border) {
        var words = new ArrayList<OcrWord>();
        for (var tw : res.words()) {
            var x = (origin[0] + tw.x() - border) / scale;
            var y = (origin[1] + tw.y() - border) / scale;
            var w = tw.w() / scale;
            var h = tw.h() / scale;
            words.add(OcrWord.ocrWord(tw.text(),
                new BoundingBox(x, y, x + w, y, x + w, y + h, x, y + h), tw.confidence(), ""));
        }
        BoundingBox lineBox = null;
        if (!words.isEmpty()) {
            var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE;
            var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE;
            for (var w : words) {
                var b = w.boundingBox();
                minX = Math.min(minX, b.x1()); minY = Math.min(minY, b.y1());
                maxX = Math.max(maxX, b.x3()); maxY = Math.max(maxY, b.y3());
            }
            lineBox = new BoundingBox(minX, minY, maxX, minY, maxX, maxY, minX, maxY);
        }
        return new OcrLine(res.text(), lineBox, words);
    }

    static OcrResult splice(OcrResult src, Map<Integer, OcrLine> replacements, Set<Integer> drop) {
        var lines = new ArrayList<OcrLine>();
        var all = src.lines();
        for (var i = 0; i < all.size(); i++) {
            if (drop.contains(i)) continue;
            lines.add(replacements.getOrDefault(i, all.get(i)));
        }
        var text = new StringBuilder();
        for (var l : lines) {
            if (text.length() > 0) text.append('\n');
            text.append(l.text());
        }
        return new OcrResult(text.toString(), src.textAngle(), lines);
    }

    static List<ImprovedPage> unchanged(List<ImprovePage> pages) {
        var out = new ArrayList<ImprovedPage>();
        for (var p : pages) out.add(new ImprovedPage(p.pageNo(), p.result(), 0, 0, List.of()));
        return out;
    }

    @Override
    public void close() {
        pool.close();
    }
}
