package xyz.jphil.win11_oneocr.tools.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.*;
import picocli.CommandLine.*;
import xyz.jphil.win11_oneocr.*;
import xyz.jphil.win11_oneocr.tools.DualProgressRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import xyz.jphil.win11_oneocr.tools.*;
import xyz.jphil.win11_oneocr.tools.improve.*;
import xyz.jphil.win11_oneocr.tools.layout.*;
import static xyz.jphil.win11_oneocr.tools.PagedOcrData.*;

/**
 * PDF OCR processing subcommand for OcrTool
 * Splits PDF into images and processes them with Windows 11 OneOCR
 * 
 */
@Command(
    name = "pdf",
    description = "Process PDF file - extract text from all pages using Windows 11 OneOCR"
)
public class PdfOcrCommand implements Callable<Integer> {

    @picocli.CommandLine.ParentCommand
    private OcrTool parentCommand;
    
    // Interface for page progress callback
    public interface PageProgressCallback {
        void onPageCompleted(int pageNumber, long pageProcessingTimeMs);
        void onFileStarted(String fileName, int totalPages, long fileSizeBytes);
    }
    
    // Progress callback for folder mode integration
    private PageProgressCallback pageProgressCallback;
    
    public void setPageProgressCallback(PageProgressCallback callback) {
        this.pageProgressCallback = callback;
    }
    
    private int getThreads() {
        return parentCommand != null ? parentCommand.getThreads() : 1;
    }
    
    // Public setter for FolderOcrCommand to set parent command relationship
    public void setParentCommand(xyz.jphil.win11_oneocr.tools.OcrTool parentCommand) {
        this.parentCommand = parentCommand;
    }

    @Parameters(index = "0", description = "Input PDF file")
    private File pdfFile;

    @Option(names = {"-o", "--output-dir"}, description = "Output directory (default: PDF filename directory)")
    private File outputDir;

    @Option(names = {"--image-format"}, description = "Image format for extracted pages (jpg, png, webp, avif)", defaultValue = "webp")
    private String imageFormat;


    @Option(names = {"--max-lines"}, description = "Maximum number of text lines to recognize per page", defaultValue = "1000")
    private int maxLines;

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

    @Option(names = {"--min-confidence"}, description = "Minimum confidence threshold (0.0-1.0)", defaultValue = "0.0")
    private double minConfidence;

    @Option(names = {"--target-dpi"}, description = "Force specific DPI for all pages (bypasses auto-detection)")
    private Integer userTargetDpi;

    @Mixin
    private TessCliOptions tess = new TessCliOptions();

    @Mixin
    private LayoutCliOptions layout = new LayoutCliOptions();

    private ImproveOptions improveOptions;
    
    // PDF processing fields
    private int calculatedTargetDpi = 0;  // User-specified or calculated DPI for preview images
    private PdfNaming naming;
    PDFRenderer pdfRenderer;
    PdfInfoUtil.PdfInfo pdfInfo;
    
    @Override
    public Integer call() throws Exception {
        try {
            if (!pdfFile.exists()) {
                System.err.println("Error: PDF file does not exist: " + pdfFile);
                return 1;
            }

            // Validate the second-pass flags before spending a full OCR run on the document.
            improveOptions = tess.enabled() ? tess.toOptions() : null;

            // Get PDF information (pages, dimensions, file size)
            pdfInfo = PdfInfoUtil.getPdfInfo(pdfFile);
            naming = new PdfNaming(pdfFile.getName(), pdfInfo.pageCount());

            // Determine output directory. getParent() is null for a bare relative filename
            // ("order.pdf"), so resolve against the absolute path rather than crashing.
            Path actualOutputDir = outputDir != null ?
                outputDir.toPath() :
                pdfFile.toPath().toAbsolutePath().getParent().resolve(pdfFile.getName() + ".oneocr");
            
            Files.createDirectories(actualOutputDir);

            // Initialize logging early
            var log = LogFormatter.standard(verbose);
            log.step("PDF", "Processing " + pdfFile.getName());
            log.debug("PDF", "Output directory: " + actualOutputDir);
            log.debug("PDF", String.format("Size: %s / %d pages (budget: %s/page)",
                formatBytes(pdfInfo.fileSize()), pdfInfo.pageCount(), formatBytes(pdfInfo.perPageAverageSize())));

            // EARLY EXIT: Check if processing is already complete (skip expensive DPI analysis).
            // Suppressed when a Tesseract pass is requested: the pass needs in-memory OCR results,
            // so an already-processed document must be walked again rather than silently skipped.
            if (improveOptions == null && isProcessingComplete(actualOutputDir, pdfFile.getName())) {
                if (verbose) {
                    System.err.println("✅ Processing already complete - all pages processed!");
                }
                log.success("PDF", "Already completed, skipping DPI analysis and OCR processing");
                
                // Notify callback about already-completed file for proper page accumulation
                // System.err.println("DEBUG: Already completed file - " + pdfFile.getName() + " - callback: " + (pageProgressCallback != null ? "SET" : "NULL")); // Debug removed
                if (pageProgressCallback != null) {
                    pageProgressCallback.onFileStarted(pdfFile.getName(), pdfInfo.pageCount(), pdfInfo.fileSize());
                    // Report all pages as completed with 0ms processing time (they were skipped)
                    // System.err.println("DEBUG: Reporting " + pdfInfo.pageCount() + " skipped pages"); // Debug removed
                    for (int page = 1; page <= pdfInfo.pageCount(); page++) {
                        pageProgressCallback.onPageCompleted(page, 0); // 0ms = skipped
                    }
                } else {
                    System.err.println("WARNING: Cannot report pages for already-completed file - callback is null");
                }
                
                // The layout export works purely off the saved boxes, so a finished document can be
                // re-rendered (or re-tuned) without re-running OCR.
                if (layout.enabled())
                    writeLayout(actualOutputDir.resolve(naming.range(1, pdfInfo.pageCount(), "xhtml")),
                        actualOutputDir, naming.combined("layout.txt"), log);

                // Return early - no need for progress tracking, DPI analysis, or OCR processing
                System.out.println("Combined text file: " + pdfFile.getName() + ".oneocr.txt");
                System.out.println("Combined XHTML file: " + pdfFile.getName() + ".oneocr.xhtml");
                System.out.println("Individual page images and OCR files preserved in: " + actualOutputDir.getFileName());
                return 0;
            }

            // Step 1: Determine target DPI (simplified - preview images only)
            if (userTargetDpi != null) {
                calculatedTargetDpi = Math.max(100, Math.min(300, userTargetDpi)); // Apply quality bounds
                if (verbose) {
                    System.err.printf("Using user-specified DPI: %d (bounded to 100-300)%n", calculatedTargetDpi);
                }
            } else {
                calculatedTargetDpi = PdfInfoUtil.calculateSimpleDpi(pdfInfo, verbose);
                if (verbose) {
                    System.err.printf("Using calculated DPI: %d (for preview images)%n", calculatedTargetDpi);
                }
            }

            // Notify folder progress tracker if callback is set
            if (pageProgressCallback != null) {
                pageProgressCallback.onFileStarted(pdfFile.getName(), pdfInfo.pageCount(), pdfInfo.fileSize());
            }

            // Step 2: Process PDF with progress tracking
            var progress = new ProgressTracker("PDF OCR Processing", pdfInfo.pageCount(), verbose);
            
            // Register with dual progress renderer if in folder mode
            String pdfProgressId = null;
            if (DualProgressRenderer.isFolderModeActive()) {
                pdfProgressId = "pdf(" + pdfFile.getName() + ")";
                DualProgressRenderer.register(pdfProgressId, progress);
            }
            
            progress.start();
            
            System.err.printf("DEBUG DECISION: threads=%d, will call %s%n", getThreads(), 
                (getThreads() == 1) ? "processWithParallelArchitecture" : "processWithMultiThreadedOcr");
                
            List<PagedOcrResult> results = (getThreads() == 1) 
                ? processWithParallelArchitecture(actualOutputDir, progress, log)
                : processWithMultiThreadedOcr(actualOutputDir, progress, log);
                
            System.err.printf("DEBUG DECISION: processing completed, results.size()=%d%n", results.size());
            
            // Handle case where no new pages were processed
            if (results.isEmpty()) {
                // Check which individual page files exist vs missing
                List<Integer> missingPages = new ArrayList<>();
                int existingPages = 0;
                
                for (int page = 1; page <= pdfInfo.pageCount(); page++) {
                    if (allPageFilesExist(pdfFile.getName(), page, actualOutputDir)) {
                        existingPages++;
                    } else {
                        missingPages.add(page);
                    }
                }
                
                if (missingPages.isEmpty()) {
                    // All individual pages exist - create missing range files using existing logic
                    log.success("PDF", "All individual pages exist - creating final combined files");
                    // Continue to the existing final file creation logic below instead of returning early
                } else {
                    // Some pages are missing - report the issue clearly
                    progress.err(String.format("Processing incomplete: %d pages exist, %d pages missing (%s)", 
                        existingPages, missingPages.size(), 
                        missingPages.size() <= 10 ? missingPages.toString() : 
                        missingPages.subList(0, 10) + "... and " + (missingPages.size() - 10) + " more"));
                    return 1;
                }
            }

            log.success("PDF", String.format("Processed %d pages", results.size()));

            // OCR processing and file generation was already completed in parallel architecture
            // Create final combined files from the latest merged files
            if (verbose) {
                System.err.println("Creating final combined files...");
            }
            
            String baseName = pdfFile.getName();
            
            // Copy final merged files to expected output names - use TOTAL pages, not just newly processed pages
            int totalPages = pdfInfo.pageCount();
            Path finalMergedTxt = actualOutputDir.resolve(naming.range(1, totalPages, "txt"));
            Path finalMergedXhtml = actualOutputDir.resolve(naming.range(1, totalPages, "xhtml"));
            Path outputTxt = actualOutputDir.resolve(naming.combined("txt"));
            Path outputXhtml = actualOutputDir.resolve(naming.combined("xhtml"));
            
            
            // Create range files from individual pages if they don't exist
            if (!Files.exists(finalMergedTxt) || !Files.exists(finalMergedXhtml)) {
                createRangeFilesFromIndividualPages(actualOutputDir, baseName, totalPages);
            }
            
            if (Files.exists(finalMergedTxt)) {
                Files.copy(finalMergedTxt, outputTxt, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.exists(finalMergedXhtml)) {
                Files.copy(finalMergedXhtml, outputXhtml, StandardCopyOption.REPLACE_EXISTING);
            }

            if (layout.enabled() && Files.exists(outputXhtml)) {
                writeLayout(outputXhtml, actualOutputDir, naming.combined("layout.txt"), log);
            }

            // Size matrix report removed - preview images now use simple calculated DPI

            if (improveOptions != null && !results.isEmpty()) {
                runTesseractPass(improveOptions, results, actualOutputDir, log);
            }

            // Unregister from dual progress renderer if in folder mode
            if (pdfProgressId != null) {
                DualProgressRenderer.unregister(pdfProgressId);
            }

            progress.done();
            System.out.println("Combined text file: " + baseName + ".oneocr.txt");
            System.out.println("Combined XHTML file: " + baseName + ".oneocr.xhtml");
            System.out.println("Individual page images and OCR files preserved in: " + actualOutputDir.getFileName());

            return 0;

        } catch (Exception e) {
            System.err.println("Error processing PDF: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }


    /**
     * Recovers the page layout from the boxes already saved in the semantic XHTML and writes the
     * reading copies beside it. Working off the file rather than off memory means this covers the
     * resumed case too, where no page was re-OCR'd at all.
     */
    private void writeLayout(Path sourceXhtml, Path outputDir, String textName, LogFormatter log) {
        try {
            if (!Files.exists(sourceXhtml)) return;
            var pages = OcrXHtmlReader.read(Files.readString(sourceXhtml));
            if (pages.isEmpty()) return;
            var analysed = LayoutExport.analyze(pages, layout.options());
            var txt = outputDir.resolve(textName);
            var html = layout.noLayoutHtml ? null : outputDir.resolve(LayoutExport.name(textName, "html"));
            LayoutExport.write(analysed, pdfFile.getName(), txt, html, layout.mode());
            log.success("LAYOUT", "Text: " + txt.getFileName()
                + (html == null ? "" : "  HTML: " + html.getFileName()));
        } catch (Exception e) {
            System.err.println("Layout export failed (other output is unaffected): " + e.getMessage());
            if (verbose) e.printStackTrace();
        }
    }

    /**
     * Optional Tesseract second pass over the whole document. Pages are re-rendered at the
     * requested DPI rather than reusing the OneOCR preview render, which is far too coarse for
     * Tesseract; the saved line boxes are scaled by the width ratio. Originals are never touched -
     * a parallel *.oneocr+tesseract.* set is written alongside them.
     */
    private void runTesseractPass(ImproveOptions opts, List<PagedOcrResult> results,
                                  Path outputDir, LogFormatter log) {
        try (var document = Loader.loadPDF(pdfFile);
             var improver = new DocumentImprover(opts)) {
            var renderer = new PDFRenderer(document);
            log.step("TESS", "Second pass over low-confidence lines across " + results.size() + " page(s)");

            var pages = new ArrayList<ImprovePage>();
            for (var r : results) pages.add(new ImprovePage(r.pageNumber(), r.ocrResult(), r.imageWidth()));

            var cache = new HashMap<Integer, BufferedImage>();
            PageImageSupplier supplier = pageNo -> {
                var img = cache.get(pageNo);
                if (img == null) {
                    img = renderer.renderImageWithDPI(pageNo - 1, opts.renderDpi, ImageType.RGB);
                    cache.clear();               // one page at a time; 300 DPI pages are large
                    cache.put(pageNo, img);
                }
                return img;
            };

            var improved = improver.improve(pages, supplier);
            for (var line : improver.log()) log.debug("TESS", line);

            var changed = improved.stream().filter(ImprovedPage::changed).toList();
            if (changed.isEmpty()) {
                log.success("TESS", "Nothing replaced; no second output written");
                return;
            }

            var engine = OcrMetadata.ENGINE_ONEOCR_TESSERACT;
            var paged = new ArrayList<PagedOcrResult>();
            var replaced = 0;
            for (var i = 0; i < improved.size(); i++) {
                var imp = improved.get(i);
                var src = results.get(i);
                replaced += imp.bandsReplaced();
                paged.add(new PagedOcrResult(imp.pageNo(), imp.result(), src.imageName(),
                    src.imageWidth(), src.imageHeight()));
                // Only pages that actually changed get a per-page file; an untouched page would
                // just duplicate its OneOCR original. The combined output still covers every page.
                if (!imp.changed()) continue;
                var pageTxt = outputDir.resolve(EngineNaming.improved(naming.page(imp.pageNo(), "txt")));
                var pageXhtml = outputDir.resolve(EngineNaming.improved(naming.page(imp.pageNo(), "xhtml")));
                Files.writeString(pageTxt, imp.result().text());
                Files.writeString(pageXhtml, OcrToSemanticXHtml.toXHtml(imp.result(),
                    src.imageName(), src.imageWidth(), src.imageHeight(), engine));
            }

            Files.writeString(outputDir.resolve(EngineNaming.improved(naming.combined("txt"))),
                paged.stream().map(p -> "=== Page " + p.pageNumber() + " ===\n" + p.ocrResult().text())
                    .collect(java.util.stream.Collectors.joining("\n\n")));
            Files.writeString(outputDir.resolve(EngineNaming.improved(naming.combined("xhtml"))),
                OcrToSemanticXHtml.combineMultipleResults(paged, pdfFile.getName()));

            if (layout.enabled())
                writeLayout(outputDir.resolve(EngineNaming.improved(naming.combined("xhtml"))),
                    outputDir, LayoutExport.name(EngineNaming.improved(naming.combined("txt")), "txt"), log);

            log.success("TESS", String.format("Replaced %d line(s) on %d page(s) using %s",
                replaced, changed.size(), String.join(", ", improver.detectedLangs())));
        } catch (Exception e) {
            System.err.println("Tesseract pass failed (OneOCR output is unaffected): " + e.getMessage());
            if (verbose) e.printStackTrace();
        }
    }

    private List<PagedOcrResult> processWithParallelArchitecture(Path outputDir, ProgressTracker progress, LogFormatter log) throws Exception {
        List<PagedOcrResult> results = new ArrayList<>();
        
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            
            String pdfName = pdfFile.getName();
            
            if (verbose) {
                System.err.printf("Processing %d pages with parallel OCR+WebP generation...%n", totalPages);
            }
            
            // Initialize parallel processing
            ExecutorService webpExecutor = Executors.newSingleThreadExecutor();
            List<String> combinedTextLines = new ArrayList<>();

            try {
                for (int page = 0; page < pdfInfo.pageCount(); page++) {
                    int pageNum = page + 1;
                    if (pageNum <= 3) { // Debug first few pages
                        System.err.printf("DEBUG PARALLEL: Starting page %d processing%n", pageNum);
                    }
                    
                    // Check if this page is already processed (resumability)
                    // A Tesseract pass needs this page's OCR result in memory, so resume-skipping
                    // it would leave the second pass with nothing to work on.
                    if (improveOptions == null && allPageFilesExist(pdfName, pageNum, outputDir)) {
                        progress.inc();

                        // Notify folder progress tracker about already-completed page
                        if (pageProgressCallback != null) {
                            pageProgressCallback.onPageCompleted(pageNum, 0); // 0ms processing time for skipped page
                        }
                        
                        continue;
                    }
                    
                    // Start timing for page processing
                    long pageStartTimeMs = System.currentTimeMillis();
                    
                    // Render page to BufferedImage (in-memory, no disk I/O)
                    BufferedImage image = pdfRenderer.renderImageWithDPI(page, calculatedTargetDpi, ImageType.RGB);
                    
                    // Main Thread: OCR Processing
                    OcrResult ocrResult;
                    try {
                        // Convert image to BGRA format for OCR API
                        byte[] bgraData = OcrTool.convertToBGRA(image);
                        
                        // Initialize OCR API (will be moved outside loop later)
                        try (var ocrApi = new OneOcrApi()) {
                            var initOptions = ocrApi.createInitOptions();
                            var pipeline = ocrApi.createPipeline(initOptions);
                            var processOptions = ocrApi.createProcessOptions(maxLines);
                            
                            ocrResult = ocrApi.recognizeImage(pipeline, processOptions, 
                                image.getWidth(), image.getHeight(), bgraData);
                                
                            processOptions.close();
                            pipeline.close();
                            initOptions.close();
                        }
                        combinedTextLines.add(ocrResult.text());
                    } catch (Exception e) {
                        progress.err(String.format("Page %d OCR failed: %s", pageNum, e.getMessage()));
                        ocrResult = new OcrResult("", 0.0, List.of());
                        combinedTextLines.add("");
                    }
                    
                    // Create PagedOcrResult using proper constructor
                    String imageName = naming.page(pageNum, "webp");
                    PageImage pageImage = new PageImage(pageNum, outputDir.resolve(imageName), 
                        image.getWidth(), image.getHeight());
                    PageOcrResult pageOcrResult = new PageOcrResult(pageNum, ocrResult, pageImage);
                    results.add(PagedOcrResult.from(pageOcrResult));
                    
                    progress.inc();
                    
                    // Notify folder progress tracker with page completion and timing
                    if (pageProgressCallback != null) {
                        long pageProcessingTimeMs = System.currentTimeMillis() - pageStartTimeMs;
                        if (pageNum <= 5) { // Debug first 5 pages
                            System.err.printf("DEBUG PDF: Calling onPageCompleted page=%d, time=%dms, callback=%s%n", 
                                pageNum, pageProcessingTimeMs, (pageProgressCallback != null ? "SET" : "NULL"));
                        }
                        pageProgressCallback.onPageCompleted(pageNum, pageProcessingTimeMs);
                    } else {
                        if (pageNum <= 5) { // Debug missing callback
                            System.err.printf("DEBUG PDF: pageProgressCallback is NULL for page %d%n", pageNum);
                        }
                    }
                    
                    // Background Thread: WebP Generation
                    final BufferedImage imageForWebP = image; // Final for lambda
                    Future<?> webpFuture = webpExecutor.submit(() -> {
                        try {
                            Path webpFile = outputDir.resolve(naming.page(pageNum, "webp"));
                            atomicWriteWebP(webpFile, imageForWebP);
                        } catch (IOException e) {
                            System.err.printf("WebP generation failed for page %d: %s%n", pageNum, e.getMessage());
                        }
                    });
                    
                    // Write OCR results (atomic)
                    try {
                        Path txtFile = outputDir.resolve(naming.page(pageNum, "txt"));
                        Path xhtmlFile = outputDir.resolve(naming.page(pageNum, "xhtml"));
                        
                        atomicWriteString(txtFile, ocrResult.text());
                        
                        String xhtmlContent = OcrToSemanticXHtml.toXHtml(
                            ocrResult, imageName, image.getWidth(), image.getHeight());
                        
                        atomicWriteString(xhtmlFile, xhtmlContent);
                        
                    } catch (IOException e) {
                        System.err.printf("Failed to write OCR files for page %d: %s%n", pageNum, e.getMessage());
                    }
                    
                    // Update merged files progressively
                    try {
                        Path mergedTxtPath = outputDir.resolve(naming.range(1, pageNum, "txt"));
                        Path mergedXhtmlPath = outputDir.resolve(naming.range(1, pageNum, "xhtml"));
                        
                        String combinedText = String.join("\n", combinedTextLines);
                        atomicWriteString(mergedTxtPath, combinedText);
                        
                        String combinedXhtml = OcrToSemanticXHtml.combineMultipleResults(results, pdfName);
                        atomicWriteString(mergedXhtmlPath, combinedXhtml);
                        
                        // Clean up previous range file
                        if (pageNum > 1) {
                            Files.deleteIfExists(outputDir.resolve(naming.range(1, pageNum - 1, "txt")));
                            Files.deleteIfExists(outputDir.resolve(naming.range(1, pageNum - 1, "xhtml")));
                        }
                        
                    } catch (IOException e) {
                        System.err.printf("Failed to update merged files for page %d: %s%n", pageNum, e.getMessage());
                    }
                    
                    // Wait for WebP completion before proceeding
                    try {
                        webpFuture.get();
                    } catch (Exception e) {
                        System.err.printf("WebP thread error for page %d: %s%n", pageNum, e.getMessage());
                    }
                    
                    // Progress reporting is handled by ProgressTracker - don't interfere with progress bar
                }
                
            } finally {
                // Ensure WebP executor is properly shut down
                webpExecutor.shutdown();
                try {
                    if (!webpExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                        webpExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    webpExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        return results;
    }

    
    private void combineTxtResults(List<PageOcrResult> results, Path outputFile) throws Exception {
        StringBuilder combined = new StringBuilder();
        
        for (PageOcrResult result : results) {
            String text = result.ocrResult().text().trim();
            if (!text.isEmpty()) {
                combined.append("=== Page ").append(result.pageNumber()).append(" ===\n");
                combined.append(text);
                if (!text.endsWith("\n")) {
                    combined.append("\n");
                }
                combined.append("\n");
            }
        }
        
        Files.writeString(outputFile, combined.toString());
        
        if (verbose) {
            System.err.printf("Combined text written to: %s (%d characters)%n", 
                outputFile.getFileName(), combined.length());
        }
    }

    private void combineXhtmlResults(List<PageOcrResult> results, Path outputFile) throws Exception {
        if (results.isEmpty()) {
            return;
        }

        // Use enhanced XHTML merger
        String combinedXhtml = OcrToSemanticXHtml.combineMultipleResults(
            results.stream()
                .map(PagedOcrResult::from)
                .toList(),
            pdfFile.getName()
        );
        
        Files.writeString(outputFile, combinedXhtml);
        
        if (verbose) {
            System.err.printf("Combined XHTML written to: %s (%d bytes)%n", 
                outputFile.getFileName(), combinedXhtml.length());
        }
    }

    private OcrResult filterByConfidence(OcrResult result, double minConfidence) {
        var filteredLines = result.lines().stream()
            .map(line -> {
                var filteredWords = line.words().stream()
                    .filter(word -> word.confidence() >= minConfidence)
                    .toList();
                
                if (filteredWords.isEmpty()) {
                    return null;
                }
                
                String filteredText = filteredWords.stream()
                    .map(OcrWord::text)
                    .reduce((a, b) -> a + " " + b)
                    .orElse("");
                
                return new OcrLine(filteredText, line.boundingBox(), filteredWords);
            })
            .filter(line -> line != null)
            .toList();

        String filteredFullText = filteredLines.stream()
            .map(OcrLine::text)
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

        return new OcrResult(filteredFullText, result.textAngle(), filteredLines);
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        } else {
            return String.format("%dh %dm %ds", seconds / 3600, (seconds % 3600) / 60, seconds % 60);
        }
    }


    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }
    /**
     * Atomic file write using temp+rename pattern
     */
    private void atomicWriteString(Path outputPath, String content) throws IOException {
        Path tempFile = outputPath.resolveSibling(outputPath.getFileName() + ".tmp");
        try {
            Files.writeString(tempFile, content);
            Files.move(tempFile, outputPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }
    
    /**
     * Atomic WebP file write using temp+rename pattern
     */
    private void atomicWriteWebP(Path outputPath, BufferedImage image) throws IOException {
        Path tempFile = outputPath.resolveSibling(outputPath.getFileName() + ".tmp");
        try {
            ImageIO.write(image, "webp", tempFile.toFile());
            Files.move(tempFile, outputPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }
    
    /**
     * Check if all page files exist and are non-empty
     */
    private boolean allPageFilesExist(String pdfName, int pageNum, Path outputDir) {
        Path webpFile = outputDir.resolve(naming.page(pageNum, "webp"));
        Path txtFile = outputDir.resolve(naming.page(pageNum, "txt"));
        Path xhtmlFile = outputDir.resolve(naming.page(pageNum, "xhtml"));
        
        try {
            boolean webpExists = Files.exists(webpFile);
            long webpSize = webpExists ? Files.size(webpFile) : 0;
            boolean txtExists = Files.exists(txtFile);
            boolean xhtmlExists = Files.exists(xhtmlFile);
            long xhtmlSize = xhtmlExists ? Files.size(xhtmlFile) : 0;
            
            boolean webpOk = webpExists && webpSize > 0;
            boolean txtOk = txtExists;
            boolean xhtmlOk = xhtmlExists && xhtmlSize > 0;
            boolean allOk = webpOk && txtOk && xhtmlOk;
            
            
            // WebP must exist and have content (image preview)
            // TXT only needs to exist (empty pages = 0 bytes, which is valid)
            // XHTML must exist and have content (even empty pages have XHTML structure)
            return allOk;
        } catch (IOException e) {
            return false;
        }
    }
    
    
    /**
     * Check if entire PDF processing is already complete
     */
    private boolean isProcessingComplete(Path outputDir, String pdfName) {
        Path finalTxtFile = outputDir.resolve(naming.range(1, pdfInfo.pageCount(), "txt"));
        Path finalXhtmlFile = outputDir.resolve(naming.range(1, pdfInfo.pageCount(), "xhtml"));
        
        try {
            return Files.exists(finalTxtFile) && Files.size(finalTxtFile) > 0 &&
                   Files.exists(finalXhtmlFile) && Files.size(finalXhtmlFile) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * EXPERIMENTAL Multi-threaded OCR processing - distributes pages across multiple OCR worker threads
     * 
     * ⚠️ THREADING LIMITATION WARNING ⚠️
     * The native Windows 11 OneOCR library has thread safety issues that can cause
     * "Failed to recognize image data" errors when using multiple threads.
     * 
     * RECOMMENDATION: Use --threads 1 for reliable OCR processing.
     * Multi-threading provides minimal performance benefit for OCR operations since:
     * - OCR model loading is the main bottleneck (done once)  
     * - Native OCR execution is already optimized
     * - File I/O is not the limiting factor
     * 
     * If you must use multi-threading, expect some page failures and be prepared
     * to re-run failed pages with --threads 1.
     */
    private List<PagedOcrResult> processWithMultiThreadedOcr(Path outputDir, ProgressTracker progress, LogFormatter log) throws Exception {
        if (verbose) {
            System.err.printf("Processing %d pages with %d OCR threads...%n", pdfInfo.pageCount(), getThreads());
        }
        
        // Initialize PDF renderer (thread-safe for reading)
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            this.pdfRenderer = new PDFRenderer(document);
            
            // Thread-safe result collection
            ConcurrentHashMap<Integer, PagedOcrResult> results = new ConcurrentHashMap<>();
            
            // Work queue for page assignments
            BlockingQueue<Integer> pageQueue = new LinkedBlockingQueue<>();
            for (int page = 0; page < pdfInfo.pageCount(); page++) {
                pageQueue.offer(page);
            }
            
            // CRITICAL FIX: OneOCR requires thread-local initialization!
            // Initialize OCR instances INSIDE worker threads, not in main thread.
            // (Confirmed by ThreadInitializationTest: main thread init → worker thread use = 0% success)
        
        // Create OCR worker threads
        ExecutorService ocrExecutor = Executors.newFixedThreadPool(getThreads());
        ExecutorService webpExecutor = Executors.newCachedThreadPool();
        
        List<Future<Void>> ocrTasks = new ArrayList<>();
        
        // Start OCR worker threads
        for (int threadId = 0; threadId < getThreads(); threadId++) {
            final int workerId = threadId;
            
            Future<Void> task = ocrExecutor.submit(() -> {
                System.err.printf("DEBUG MT-START: Worker %d starting, pageQueue.size()=%d%n", workerId, pageQueue.size());
                String pdfName = pdfFile.getName();
                
                // Initialize OCR INSIDE worker thread (thread-local requirement)
                try (var api = new OneOcrApi()) {
                    System.err.printf("DEBUG MT-OCR: Worker %d OCR initialized successfully%n", workerId);
                    var initOptions = api.createInitOptions();
                    var pipeline = api.createPipeline(initOptions);
                    var processOptions = api.createProcessOptions(maxLines);
                    
                    try {
                        // Process pages from the queue using thread-local OCR instance
                        System.err.printf("DEBUG MT-LOOP: Worker %d entering page processing loop%n", workerId);
                        Integer page;
                        int pagesProcessed = 0;
                        while ((page = pageQueue.poll()) != null) {
                            pagesProcessed++;
                            System.err.printf("DEBUG MT-POLL: Worker %d got page %d (total processed: %d)%n", workerId, page + 1, pagesProcessed);
                            int pageNum = page + 1;
                            if (pageNum <= 3) { // Debug first few pages
                                System.err.printf("DEBUG MT-WORKER: Processing page %d in worker %d%n", pageNum, workerId);
                            }
                            
                            try {
                                // Check if already processed (resumability)
                                if (allPageFilesExist(pdfName, pageNum, outputDir)) {
                                    progress.inc();
                                    continue;
                                }
                                
                                // Start timing for page processing
                                long pageStartTimeMs = System.currentTimeMillis();
                                
                                // Render page to image (thread-safe PDFBox operation)
                                BufferedImage image;
                                synchronized (pdfRenderer) {
                                    image = pdfRenderer.renderImageWithDPI(page, calculatedTargetDpi, ImageType.RGB);
                                }
                                
                                // OCR processing using thread-local instance (no cross-thread sharing)
                                byte[] bgraData = OcrTool.convertToBGRA(image);
                                OcrResult ocrResult = api.recognizeImage(pipeline, processOptions, 
                                    image.getWidth(), image.getHeight(), bgraData);
                            
                            // Create PageImage with proper WebP path
                            String imageName = naming.page(pageNum, "webp");
                            Path webpPath = outputDir.resolve(imageName);
                            PageImage pageImage = new PageImage(pageNum, webpPath, image.getWidth(), image.getHeight());
                            
                            // Create result object
                            PagedOcrResult pagedResult = PagedOcrResult.from(
                                new PageOcrResult(pageNum, ocrResult, pageImage));
                            results.put(pageNum, pagedResult);
                            
                            // Background WebP generation (optional)
                            final BufferedImage imageForWebP = image;
                            webpExecutor.submit(() -> {
                                try {
                                    generateWebPForPage(pageNum, imageForWebP, outputDir, pdfName);
                                } catch (Exception e) {
                                    if (verbose) {
                                        System.err.printf("WebP generation failed for page %d: %s%n", pageNum, e.getMessage());
                                    }
                                }
                            });
                            
                            // Write OCR files
                            writeOcrFilesForPage(pageNum, ocrResult, image, outputDir, pdfName);
                            
                            // Update progress (thread-safe)
                            progress.inc();
                            
                            // Notify folder progress tracker with page completion and timing
                            if (pageProgressCallback != null) {
                                long pageProcessingTimeMs = System.currentTimeMillis() - pageStartTimeMs;
                                if (pageNum <= 5) { // Debug first 5 pages
                                    System.err.printf("DEBUG PDF-MT: Calling onPageCompleted page=%d, time=%dms, callback=%s%n", 
                                        pageNum, pageProcessingTimeMs, (pageProgressCallback != null ? "SET" : "NULL"));
                                }
                                pageProgressCallback.onPageCompleted(pageNum, pageProcessingTimeMs);
                            } else {
                                if (pageNum <= 5) { // Debug missing callback
                                    System.err.printf("DEBUG PDF-MT: pageProgressCallback is NULL for page %d%n", pageNum);
                                }
                            }
                            
                        } catch (Exception e) {
                            progress.err(String.format("Page %d failed: %s", pageNum, e.getMessage()));
                        }
                    }
                    
                    } catch (Exception e) {
                        log.error("OCR", "Worker " + workerId + " failed: " + e.getMessage());
                    } finally {
                        // Cleanup OCR resources within worker thread  
                        try {
                            processOptions.close();
                            pipeline.close();
                            initOptions.close();
                        } catch (Exception e) {
                            if (verbose) {
                                System.err.printf("Failed to cleanup OCR resources in worker %d: %s%n", workerId, e.getMessage());
                            }
                        }
                    }
                    
                } catch (Exception e) {
                    log.error("OCR", "Worker " + workerId + " OCR setup failed: " + e.getMessage());
                }
                
                return null;
            });
            ocrTasks.add(task);
        }
        
        // Wait for all OCR tasks to complete
        try {
            for (Future<Void> task : ocrTasks) {
                task.get(); // Wait for completion
            }
        } finally {
            ocrExecutor.shutdown();
            webpExecutor.shutdown();
            
            // OCR resources are cleaned up within each worker thread (thread-local lifecycle)
            
            // Ensure thread pool cleanup
            try {
                if (!ocrExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    ocrExecutor.shutdownNow();
                }
                if (!webpExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    webpExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Convert results to ordered list
        List<PagedOcrResult> orderedResults = new ArrayList<>();
        for (int pageNum = 1; pageNum <= pdfInfo.pageCount(); pageNum++) {
            PagedOcrResult result = results.get(pageNum);
            if (result != null) {
                orderedResults.add(result);
            }
        }
        
        return orderedResults;
        } // End of try-with-resources for PDDocument
    }

    /**
     * Generate WebP image for a specific page
     */
    private void generateWebPForPage(int pageNum, BufferedImage image, Path outputDir, String pdfName) throws IOException {
        Path webpFile = outputDir.resolve(naming.page(pageNum, "webp"));
        atomicWriteWebP(webpFile, image);
    }
    
    /**
     * Write OCR text and XHTML files for a specific page  
     */
    private void writeOcrFilesForPage(int pageNum, OcrResult ocrResult, BufferedImage image, Path outputDir, String pdfName) throws IOException {
        Path txtFile = outputDir.resolve(naming.page(pageNum, "txt"));
        Path xhtmlFile = outputDir.resolve(naming.page(pageNum, "xhtml"));
        
        atomicWriteString(txtFile, ocrResult.text());
        
        String imageName = naming.page(pageNum, "webp");
        String xhtmlContent = OcrToSemanticXHtml.toXHtml(
            ocrResult, imageName, image.getWidth(), image.getHeight());
        
        atomicWriteString(xhtmlFile, xhtmlContent);
    }
    
    /**
     * Create range files by combining existing individual page files
     */
    private void createRangeFilesFromIndividualPages(Path outputDir, String pdfName, int totalPages) throws IOException {
        // Combine all individual TXT files into final range file
        List<String> allTextLines = new ArrayList<>();
        for (int page = 1; page <= totalPages; page++) {
            Path individualTxtFile = outputDir.resolve(naming.page(page, "txt"));
            try {
                if (Files.exists(individualTxtFile)) {
                    String pageText = Files.readString(individualTxtFile, StandardCharsets.UTF_8);
                    allTextLines.add(pageText);
                } else {
                    allTextLines.add(""); // Empty page
                }
            } catch (IOException e) {
                allTextLines.add(""); // Failed to read, treat as empty
            }
        }
        
        // Create final range TXT file
        Path finalRangeTxt = outputDir.resolve(naming.range(1, totalPages, "txt"));
        String combinedText = String.join("\n", allTextLines);
        atomicWriteString(finalRangeTxt, combinedText);
        
        // Create final range XHTML file by reading all individual XHTML files
        List<PagedOcrResult> allPageResults = new ArrayList<>();
        for (int page = 1; page <= totalPages; page++) {
            Path individualXhtmlFile = outputDir.resolve(naming.page(page, "xhtml"));
            if (Files.exists(individualXhtmlFile)) {
                // For existing pages, create a placeholder result (we don't need full OCR data for final combination)
                OcrResult pageOcrResult = new OcrResult(allTextLines.get(page - 1), 0.0, List.of());
                String imageName = naming.page(page, "webp");
                Path imagePath = outputDir.resolve(imageName);
                PageImage pageImage = new PageImage(page, imagePath, 0, 0); // Width/height not needed for final combination
                PageOcrResult pageResult = new PageOcrResult(page, pageOcrResult, pageImage);
                allPageResults.add(PagedOcrResult.from(pageResult));
            }
        }
        
        String combinedXhtml = OcrToSemanticXHtml.combineMultipleResults(allPageResults, pdfName);
        Path finalRangeXhtml = outputDir.resolve(naming.range(1, totalPages, "xhtml"));
        atomicWriteString(finalRangeXhtml, combinedXhtml);
    }


}