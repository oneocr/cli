package oneocr.cli.engine;

import oneocr.onnx.OcrOptions;
import oneocr.onnx.OrientationSearch;
import oneocr.onnx.ScriptGroup;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * Which backend to use, and what it may be told to skip.
 *
 * <p>Every default here reproduces what the tool did before any of it existed: the platform's own
 * backend, all four orientations searched, and a script chosen per line. Each flag trades some of
 * that away for speed and each one states what it costs, because two of them can change the text.
 *
 * <p>All of the skipping applies to the ONNX backend only. The DLL exposes no equivalent controls —
 * it decides orientation and script internally and cannot be told either — so these flags are
 * silently irrelevant there rather than an error, and {@code --engine} is the flag that decides
 * whether they mean anything.
 */
public class EngineCliOptions {

    @Option(names = "--engine", description = "OCR backend: AUTO (the DLL on Windows, ONNX elsewhere), "
            + "DLL (Microsoft's oneocr.dll, Windows only), or ONNX (the same models over ONNX Runtime "
            + "in pure Java, works anywhere). Default: ${DEFAULT-VALUE}. Only one backend can be "
            + "loaded per process.")
    public EngineChoice engine = EngineChoice.AUTO;

    @Option(names = {"--upright"}, description = "The pages are the right way up; skip the orientation "
            + "search. The single largest saving available and it costs nothing in accuracy when it is "
            + "true - 1.4x to 2.7x depending on the page. It is NOT implied for PDFs: a page scanned "
            + "sideways is rendered sideways, and assuming otherwise would silently produce nonsense.")
    public boolean upright;

    @Option(names = "--rotation", description = "The pages are at this angle: 0, 90, 180 or 270. Implies "
            + "--upright's saving without assuming 0.")
    public Integer rotation;

    @Option(names = "--orientation-search", description = "How hard to look when the angle is not given: "
            + "FULL tries all four rotations (default), EARLY_EXIT stops at upright when it looks "
            + "upright. EARLY_EXIT is worth about 1.15x to 1.3x and agreed with FULL on every fixture "
            + "tested, but it can in principle pick a different angle. Prefer --rotation when known.")
    public OrientationSearch orientationSearch = OrientationSearch.FULL;

    @Option(names = "--lang", description = "Read every line with this script's recognizer and never run "
            + "the script classifier: cjk, cyrillic, latin, arabic, devanagari, greek, thai, hebrew, "
            + "tamil. Worth 1.5x to 1.7x and free of accuracy cost on a document in one known script - "
            + "the classifier's label is noisy enough that it routes plain English to the cyrillic "
            + "recognizer with no ill effect. RUINS a mixed document: forcing latin on a bilingual "
            + "Chinese page destroyed every CJK line.")
    public String lang;

    @Option(names = "--lang-candidates", split = ",", description = "Confine the classifier to these "
            + "scripts, keeping it per line: e.g. latin,cjk. For a document that is mixed but not "
            + "arbitrary. Fixes misrouting without forcing one script on the whole page. Note it makes "
            + "CJK a likelier winner, and a Latin line read by the CJK recognizer loses its spaces.")
    public List<String> langCandidates;

    /**
     * These options back as command-line arguments, for a subcommand run in the same process —
     * folder mode hands each PDF to {@code PdfOcrCommand} that way.
     *
     * <p>The backend is emitted RESOLVED rather than as {@code AUTO}. Both would resolve the same way
     * on the same machine, but only if nothing else has already been chosen: a folder run told
     * {@code --engine ONNX} would otherwise hand its PDFs an {@code AUTO} that resolves to the DLL,
     * and {@link Engines} would then refuse the second native — which is exactly what happened, and
     * is why this method exists rather than the caller listing the flags it happens to remember.
     */
    public List<String> asArgs() {
        var args = new java.util.ArrayList<String>();
        args.add("--engine");
        args.add(Engines.resolve(engine).name());
        if (rotation != null) {
            args.add("--rotation");
            args.add(String.valueOf(rotation));
        } else if (upright) {
            args.add("--upright");
        }
        if (orientationSearch != OrientationSearch.FULL) {
            args.add("--orientation-search");
            args.add(orientationSearch.name());
        }
        if (lang != null) {
            args.add("--lang");
            args.add(lang);
        }
        if (langCandidates != null && !langCandidates.isEmpty()) {
            args.add("--lang-candidates");
            args.add(String.join(",", langCandidates));
        }
        return args;
    }

    /** True when any flag here would have no effect because the DLL backend is the one running. */
    public boolean ignoredByDll() {
        return upright || rotation != null || lang != null
                || (langCandidates != null && !langCandidates.isEmpty())
                || orientationSearch != OrientationSearch.FULL;
    }

    /** What the ONNX backend should be told. Reproduces the old behaviour when nothing is set. */
    public OcrOptions onnxOptions() {
        var options = OcrOptions.defaults().orientationSearch(orientationSearch);
        if (rotation != null) options = options.rotation(rotation);
        else if (upright) options = options.upright();
        if (lang != null) options = options.script(script(lang));
        if (langCandidates != null && !langCandidates.isEmpty())
            options = options.candidates(langCandidates.stream().map(EngineCliOptions::script)
                    .toArray(ScriptGroup[]::new));
        return options;
    }

    static ScriptGroup script(String name) {
        return ScriptGroup.byName(name).orElseThrow(() -> new IllegalArgumentException(
                "unknown script '" + name + "'. Known: cjk, cyrillic, latin, arabic, devanagari, "
                        + "greek, thai, hebrew, tamil."));
    }
}
