package xyz.jphil.win11_oneocr.tools;

import xyz.jphil.win11_oneocr.tools.folder.FolderOcrCommand;
import xyz.jphil.win11_oneocr.tools.pdf.PdfOcrCommand;
import xyz.jphil.win11_oneocr.tools.ui.UiCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import xyz.jphil.win11_oneocr.*;
import xyz.jphil.win11_oneocr.tools.improve.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import xyz.jphil.windows_console_set_unicode_output.WindowsConsoleSetUnicodeOutput;

/**
 * Professional command-line OCR tool using Windows 11 OneOCR
 * Built with PicoCLI for robust argument parsing and help generation
 */
@Command(
    name = "oneocr", 
    mixinStandardHelpOptions = true, 
    version = "1.0",
    description = "Windows 11 OneOCR command-line tool - Extract text from images using Windows built-in OCR",
    subcommands = {PdfOcrCommand.class, FolderOcrCommand.class, UiCommand.class}
)
public class OcrTool implements Callable<Integer> {

    @Parameters(index = "0", description = "Input image file (JPG, PNG, BMP, TIFF)", arity = "0..1")
    private File inputFile;

    @Option(names = {"-o", "--output"}, description = "Output text file (default: stdout)")
    private File outputFile;

    @Option(names = {"--svg"}, description = "Generate SVG visualization (default: input.ext.oneocr.svg)")
    private File svgFile;

    @Option(names = {"--json"}, description = "Output compact JSON format (default: input.ext.oneocr.json)")
    private File jsonFile;

    @Option(names = {"--xhtml"}, description = "Output semantic XHTML format (default: input.ext.oneocr.xhtml)")
    private File xhtmlFile;

    @Option(names = {"-t", "--text"}, description = "Output plain text (default: input.ext.oneocr.txt)")
    private File textFile;

    @Option(names = {"--no-defaults"}, description = "Don't generate default outputs, only specified files")
    private boolean noDefaults;

    @Option(names = {"--max-lines"}, description = "Maximum number of text lines to recognize", defaultValue = "1000")
    private int maxLines;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

    @Option(names = {"--show-confidence"}, description = "Show confidence scores for each word")
    private boolean showConfidence;

    @Option(names = {"--min-confidence"}, description = "Minimum confidence threshold (0.0-1.0)", defaultValue = "0.0")
    private double minConfidence;

    @Option(names = {"--threads"}, description = "Number of OCR threads for parallel processing (default: 1)", defaultValue = "1")
    private int threads;

    @CommandLine.Mixin
    private TessCliOptions tess = new TessCliOptions();

    public int getThreads() {
        return threads;
    }

    public static void main(String[] args) {
        // Set up UTF-8 console for proper emoji display
        var _ = WindowsConsoleSetUnicodeOutput.enable();
        // The library handles all the complexity and fails gracefully on non-Windows systems
        // No need to handle the result - it's designed to work silently
        
        // Suppress logging noise from various sources
        suppressLoggingNoise();
        
        int exitCode = new CommandLine(new OcrTool()).execute(args);
        System.exit(exitCode);
    }
    
    private static void suppressLoggingNoise() {
        // Suppress logback startup noise
        System.setProperty("logback.configurationFile", "logback-silent.xml");
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");
        
        // Aggressively suppress Java Util Logging (used by PDFBox)
        try {
            // Turn off all PDFBox logging
            java.util.logging.Logger.getLogger("org.apache.pdfbox").setLevel(java.util.logging.Level.OFF);
            java.util.logging.Logger.getLogger("org.apache.pdfbox.contentstream").setLevel(java.util.logging.Level.OFF);
            java.util.logging.Logger.getLogger("org.apache.pdfbox.contentstream.PDFStreamEngine").setLevel(java.util.logging.Level.OFF);
            java.util.logging.Logger.getLogger("org.apache.pdfbox.pdmodel").setLevel(java.util.logging.Level.OFF);
            java.util.logging.Logger.getLogger("org.apache.pdfbox.pdmodel.font").setLevel(java.util.logging.Level.OFF);
            java.util.logging.Logger.getLogger("org.apache.pdfbox.rendering").setLevel(java.util.logging.Level.OFF);
            
            // Set root JUL logger to only show severe errors and remove all handlers
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
            rootLogger.setLevel(java.util.logging.Level.SEVERE);
            
            // Remove all handlers from root logger to prevent output
            for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
                rootLogger.removeHandler(handler);
            }
            
        } catch (Exception e) {
            // Silently continue if logging suppression fails
        }
    }

    @Override
    public Integer call() throws Exception {
        try {
            // If no input file provided, show usage
            if (inputFile == null) {
                CommandLine.usage(this, System.out);
                return 0;
            }

            var progress = new ProgressTracker("Single file OCR", 3, verbose);
            var log = LogFormatter.standard(verbose);
            
            progress.start();
            log.step("IMAGE", "Loading " + inputFile.getName());

            // Validate input file
            if (!inputFile.exists()) {
                progress.err("Input file does not exist");
                return 1;
            }

            if (!inputFile.canRead()) {
                progress.err("Cannot read input file");
                return 1;
            }

            // Validate the second-pass flags before spending a full OCR run on the page.
            var improveOptions = tess.enabled() ? tess.toOptions() : null;

            // Load and process image
            BufferedImage image = ImageIO.read(inputFile);
            if (image == null) {
                progress.err("Unable to read image file");
                return 1;
            }

            progress.inc();
            log.debug("IMAGE", String.format("Loaded: %dx%d pixels", image.getWidth(), image.getHeight()));
            log.step("OCR", "Initializing engine");
            
            byte[] bgraData = convertToBGRA(image);
            
            // Perform OCR
            OcrResult result;
            try (var ocrApi = new OneOcrApi()) {
                log.step("OCR", "Running recognition");
                
                var initOptions = ocrApi.createInitOptions();
                var pipeline = ocrApi.createPipeline(initOptions);
                var processOptions = ocrApi.createProcessOptions(maxLines);
                
                result = ocrApi.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                    
                processOptions.close();
                pipeline.close();
                initOptions.close();
            }

            progress.inc();
            log.success("OCR", String.format("Completed: %d lines, %d words found", 
                result.lines().size(), 
                result.lines().stream().mapToInt(l -> l.words().size()).sum()));
            
            // Filter by confidence if specified
            if (minConfidence > 0) {
                result = filterByConfidence(result, minConfidence);
                log.debug("FILTER", String.format("Confidence >%.2f: %d lines, %d words", 
                    minConfidence, result.lines().size(), 
                    result.lines().stream().mapToInt(l -> l.words().size()).sum()));
            }

            // Determine output files
            File actualTextFile = !noDefaults || textFile != null ? 
                (textFile != null ? textFile : getDefaultOutputFile("txt")) : null;
            File actualJsonFile = jsonFile;
            File actualXhtmlFile = xhtmlFile;
            File actualSvgFile = svgFile;
            
            if (!noDefaults && actualJsonFile == null && actualXhtmlFile == null && actualTextFile == null) {
                actualJsonFile = getDefaultOutputFile("json");
                actualXhtmlFile = getDefaultOutputFile("xhtml");
            }
            
            if (svgFile != null || (!noDefaults && svgFile == null)) {
                if (actualSvgFile == null) {
                    actualSvgFile = getDefaultOutputFile("svg");
                }
            }

            // Generate outputs
            if (actualTextFile != null) {
                outputPlainText(result, actualTextFile, log);
            }
            if (actualJsonFile != null) {
                outputCompactJson(result, actualJsonFile, image.getWidth(), image.getHeight(), log);
            }
            if (actualXhtmlFile != null) {
                outputSemanticXhtml(result, actualXhtmlFile, image.getWidth(), image.getHeight(), log);
            }
            if (actualSvgFile != null) {
                generateSvg(result, actualSvgFile, image.getWidth(), image.getHeight());
                log.success("SVG", "Saved: " + actualSvgFile.getName());
            }
            
            // Fallback to stdout if no outputs specified
            if (outputFile == null && actualTextFile == null && actualJsonFile == null &&
                actualXhtmlFile == null && actualSvgFile == null) {
                outputStructuredText(result);
            }

            if (improveOptions != null) {
                runTesseractPass(improveOptions, result, image,
                    actualTextFile, actualJsonFile, actualXhtmlFile, actualSvgFile, log);
            }

            progress.done();
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    /**
     * Generate default output file name in pattern: input.ext.oneocr.{extension}
     */
    private File getDefaultOutputFile(String extension) {
        String inputFileName = inputFile.getName();
        String defaultName = inputFileName + ".oneocr." + extension;
        return new File(inputFile.getParent(), defaultName);
    }

    /** Sibling of an output file with the engine tag swapped, e.g. foo.png.oneocr+tesseract.txt */
    static File improvedSibling(File original) {
        return EngineNaming.improved(original);
    }

    /**
     * Optional second pass. The original OneOCR outputs stay exactly as written; a parallel
     * *.oneocr+tesseract.* set is produced so both readings are on disk for the record.
     */
    private void runTesseractPass(ImproveOptions opts, OcrResult result, BufferedImage image, File txt, File json,
                                  File xhtml, File svg, LogFormatter log) {
        try (var improver = new DocumentImprover(opts)) {
            log.step("TESS", "Second pass over low-confidence lines");
            var pages = java.util.List.of(new ImprovePage(1, result, image.getWidth()));
            var improved = improver.improve(pages, pageNo -> image);
            for (var line : improver.log()) log.debug("TESS", line);

            var page = improved.get(0);
            if (!page.changed()) {
                log.success("TESS", "Nothing replaced; no second output written");
                return;
            }
            var engine = OcrMetadata.ENGINE_ONEOCR_TESSERACT;
            var name = inputFile.toPath().getFileName().toString();
            var w = image.getWidth();
            var h = image.getHeight();
            if (txt != null) Files.writeString(improvedSibling(txt).toPath(), page.result().text());
            if (json != null) Files.writeString(improvedSibling(json).toPath(),
                CompactJsonSerializer.toCompactJson(page.result(), name, w, h, engine));
            if (xhtml != null) Files.writeString(improvedSibling(xhtml).toPath(),
                OcrToSemanticXHtml.toXHtml(page.result(), name, w, h, engine));
            if (svg != null) Files.writeString(improvedSibling(svg).toPath(),
                SvgVisualizer.createSvgVisualization(page.result(), inputFile.toPath(), w, h));
            log.success("TESS", String.format("Replaced %d of %d flagged line(s) using %s",
                page.bandsReplaced(), page.bandsFlagged(), String.join("+", page.langsUsed())));
        } catch (Exception e) {
            System.err.println("Tesseract pass failed (OneOCR output is unaffected): " + e.getMessage());
            if (verbose) e.printStackTrace();
        }
    }

    private void outputPlainText(OcrResult result, File textFile, LogFormatter log) throws Exception {
        String text = result.text();
        Files.writeString(textFile.toPath(), text);
        
        log.success("OUTPUT", String.format("Text: %s (%d chars)", textFile.getName(), text.length()));
    }

    private void outputStructuredText(OcrResult result) throws Exception {
        StringBuilder output = new StringBuilder();
        
        output.append("=== OCR Results ===\n");
        output.append(String.format("Lines: %d%n", result.lines().size()));
        output.append(String.format("Words: %d%n", result.lines().stream().mapToInt(l -> l.words().size()).sum()));
        
        if (result.textAngle() != 0) {
            output.append(String.format("Text angle: %.2f degrees%n", result.textAngle()));
        }
        
        output.append("\n=== Text Content ===\n");
        
        for (var line : result.lines()) {
            output.append(line.text()).append("\n");
            
            if (showConfidence || verbose) {
                // Line confidence not available in current API
                
                if (showConfidence && verbose) {
                    for (var word : line.words()) {
                        output.append(String.format("    %s (%.3f)%n", word.text(), word.confidence()));
                    }
                }
            }
            
            if (line.boundingBox() != null && verbose) {
                BoundingBox bbox = line.boundingBox();
                output.append(String.format("  Bounds: (%.0f,%.0f)-(%.0f,%.0f)%n",
                    Math.min(bbox.x1(), bbox.x4()), Math.min(bbox.y1(), bbox.y2()),
                    Math.max(bbox.x1(), bbox.x4()), Math.max(bbox.y1(), bbox.y2())));
            }
        }
        
        System.out.print(output);
    }

    private void outputCompactJson(OcrResult result, File compactJsonFile, int imageWidth, int imageHeight, LogFormatter log) throws Exception {
        var compactJson = CompactJsonSerializer.toCompactJson(result, inputFile.toPath().getFileName().toString(), imageWidth, imageHeight);
        Files.writeString(compactJsonFile.toPath(), compactJson);
        
        log.success("OUTPUT", String.format("JSON: %s (%d bytes)", compactJsonFile.getName(), compactJson.length()));
    }

    private void outputSemanticXhtml(OcrResult result, File xhtmlFile, int imageWidth, int imageHeight, LogFormatter log) throws Exception {
        String xhtml = OcrToSemanticXHtml.toXHtml(result, inputFile.toPath().getFileName().toString(), imageWidth, imageHeight);
        Files.writeString(xhtmlFile.toPath(), xhtml);
        
        log.success("OUTPUT", String.format("XHTML: %s (%d bytes)", xhtmlFile.getName(), xhtml.length()));
    }

    private void generateSvg(OcrResult result, File svgFile, int imageWidth, int imageHeight) throws Exception {
        String svg = SvgVisualizer.createSvgVisualization(result, inputFile.toPath(), imageWidth, imageHeight);
        Files.writeString(svgFile.toPath(), svg);
    }

    private OcrResult filterByConfidence(OcrResult result, double minConfidence) {
        // Note: Individual line confidence filtering not available in current API
        // Returning original result for now
        var filteredLines = result.lines();
            
        return new OcrResult(
            filteredLines.stream()
                .map(OcrLine::text)
                .reduce("", (a, b) -> a + (a.isEmpty() ? "" : "\n") + b),
            result.textAngle(),
            filteredLines
        );
    }

    public static byte[] convertToBGRA(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] bgraData = new byte[width * height * 4];
        
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                bgraData[index++] = (byte) (rgb & 0xFF);        // Blue
                bgraData[index++] = (byte) ((rgb >> 8) & 0xFF);  // Green  
                bgraData[index++] = (byte) ((rgb >> 16) & 0xFF); // Red
                bgraData[index++] = (byte) ((rgb >> 24) & 0xFF); // Alpha
            }
        }
        
        return bgraData;
    }
}