package oneocr.cli.improve;

import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;
import oneocr.tesseract.TessLangs;

/**
 * Shared flags for the optional Tesseract second pass. Which scripts to consider is always an
 * explicit choice: there is no built-in default set, because the right one depends entirely on
 * the corpus being processed.
 */
public class TessCliOptions {

    @Spec
    CommandSpec spec;

    @Option(names = {"--tesseract"},
        description = "Run a Tesseract second pass over low-confidence lines. Writes a parallel set of "
            + "outputs named *.oneocr+tesseract.* and leaves the OneOCR originals untouched. "
            + "Requires --tess-candidates or --tess-lang.")
    public boolean tesseract;

    @Option(names = {"--tess-candidates"},
        description = "Scripts the probe may choose between: a comma separated list of Tesseract "
            + "language codes (e.g. guj,hin,tam), or a preset name ('indic', or 'installed' for every "
            + "model already in the local cache). No default - this is always an explicit choice.")
    public String tessCandidates;

    @Option(names = {"--tess-lang"},
        description = "Skip probing entirely and recognise every flagged line with this language.")
    public String tessLang;

    @Option(names = {"--tess-base"}, defaultValue = TessLangs.DEFAULT_BASE,
        description = "Language every flagged line is recognised alongside, normally the document's "
            + "majority script (default: ${DEFAULT-VALUE}).")
    public String tessBase;

    @Option(names = {"--tess-all"},
        description = "Recognise flagged lines with every candidate at once instead of probing. Needs no "
            + "knowledge of the document but is markedly less accurate than a resolved single script.")
    public boolean tessAll;

    @Option(names = {"--tess-dpi"}, defaultValue = "300",
        description = "DPI to re-render pages at for the Tesseract pass (default: ${DEFAULT-VALUE}).")
    public int tessDpi;

    @Option(names = {"--tess-samples"}, defaultValue = "10",
        description = "How many flagged lines to probe when identifying the script, spread across pages "
            + "(default: ${DEFAULT-VALUE}).")
    public int tessSamples;

    @Option(names = {"--tess-min-confidence"}, defaultValue = "0.90",
        description = "Lines whose mean word confidence is below this are re-read (default: ${DEFAULT-VALUE}).")
    public double tessMinConfidence;

    @Option(names = {"--detect-coverage-gaps"},
        description = "Also look for page regions with ink but no OneOCR line at all - a DETECTION gap "
            + "(e.g. a script OneOCR does not recognise, like Kannada) that the confidence guard above "
            + "cannot see, since it only ever inspects lines OneOCR already emitted. Off by default; when "
            + "on, needs --tess-candidates or --tess-lang exactly as the confidence guard does.")
    public boolean detectCoverageGaps;

    @Option(names = {"--gap-content-margin"}, defaultValue = "0,0,0,0",
        description = "Fractions of page width/height to exclude from coverage-gap scanning, as "
            + "top,bottom,left,right (e.g. a decorative page border: 0.06,0.06,0.08,0.08). Calibrated per "
            + "book template, not auto-detected. Default: ${DEFAULT-VALUE} (whole page). Only used with "
            + "--detect-coverage-gaps.")
    public String gapContentMargin;

    public boolean enabled() {
        return tesseract || tessLang != null || tessCandidates != null || tessAll || detectCoverageGaps;
    }

    /** Fails loudly rather than guessing a script set the caller never asked for. */
    public ImproveOptions toOptions() {
        var o = new ImproveOptions();
        o.baseLang = tessBase == null || tessBase.isBlank() ? TessLangs.DEFAULT_BASE : tessBase.trim();
        o.forcedLang = tessLang == null || tessLang.isBlank() ? null : tessLang.trim();
        o.allCandidatesAtOnce = tessAll;
        o.renderDpi = tessDpi;
        o.maxProbeSamples = tessSamples;
        o.minLineConfidence = tessMinConfidence;
        o.candidates = TessLangs.resolve(tessCandidates, o.baseLang);
        o.detectCoverageGaps = detectCoverageGaps;
        o.contentMargin = parseMargin(gapContentMargin);

        if (o.forcedLang == null && o.candidates.isEmpty()) {
            throw new ParameterException(spec.commandLine(),
                "The Tesseract pass needs to know which scripts to consider. Pass --tess-lang <code> to fix "
                    + "one language, or --tess-candidates <list|preset> to let it probe. "
                    + "Presets: " + TessLangs.presetNames() + ". Example: --tesseract --tess-candidates indic");
        }
        if (o.allCandidatesAtOnce && o.candidates.isEmpty()) {
            throw new ParameterException(spec.commandLine(),
                "--tess-all needs --tess-candidates to know what 'all' means.");
        }
        return o;
    }

    ImproveOptions.ContentMargin parseMargin(String spec) {
        if (spec == null || spec.isBlank()) return ImproveOptions.ContentMargin.NONE;
        var parts = spec.split(",");
        if (parts.length != 4) {
            throw new ParameterException(this.spec.commandLine(),
                "--gap-content-margin needs exactly 4 comma separated fractions: top,bottom,left,right (got '" + spec + "')");
        }
        try {
            return new ImproveOptions.ContentMargin(
                Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException e) {
            throw new ParameterException(this.spec.commandLine(),
                "--gap-content-margin values must be numbers (got '" + spec + "')");
        }
    }
}
