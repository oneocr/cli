package xyz.jphil.win11_oneocr.tools.folder;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import xyz.jphil.win11_oneocr.tools.pdf.PdfOcrCommand;
import xyz.jphil.win11_oneocr.OcrResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import xyz.jphil.win11_oneocr.OneOcrApi;
import xyz.jphil.win11_oneocr.tools.DualProgressRenderer;
import xyz.jphil.win11_oneocr.tools.OcrTool;
import xyz.jphil.win11_oneocr.tools.ProgressTracker;
import xyz.jphil.win11_oneocr.tools.ProgressAwareLogFormatter;
import xyz.jphil.win11_oneocr.tools.folder.FileProcessor.FileType;

/**
 * Folder OCR processing subcommand for OcrTool
 * Processes all images and PDFs in a folder (optionally recursive)
 */
@Command(
    name = "folder", 
    description = "Process all images and PDFs in a folder with Windows 11 OneOCR",
    mixinStandardHelpOptions = true
)
public class FolderOcrCommand implements Callable<Integer> {

    @picocli.CommandLine.ParentCommand
    private OcrTool parentCommand;
    
    
    @Parameters(
        index = "0", 
        description = "Input folder containing images and/or PDFs"
    )
    private File inputFolder;
    
    @Option(
        names = {"-r", "--recursive"}, 
        description = "Process files in subfolders recursively"
    )
    private boolean recursive = false;
    
    @Option(
        names = {"-o", "--output"}, 
        description = "Output root directory for results. Preserves input folder structure. Useful when source is read-only (default: same as input)"
    )
    private File outputFolder;
    
    @Option(
        names = {"-v", "--verbose"}, 
        description = "Enable verbose output with progress bars"
    )
    private boolean verbose = false;
    
    @Option(
        names = {"--svg"}, 
        description = "Generate SVG overlay files"
    )
    private boolean generateSvg = false;
    
    @Option(
        names = {"--max-lines"}, 
        description = "Maximum lines to extract per file (default: ${DEFAULT-VALUE})"
    )
    private int maxLines = 1000;
    
    // Progress callback for tracking current file processing
    private PdfProgressCallback currentCallback;
    
    // Helper class to track PDF processing progress
    private static class PdfProgressCallback implements PdfOcrCommand.PageProgressCallback {
        private final FolderProgressTracker folderTracker;
        private WorkItem currentWorkItem;
        private long fileStartTimeMs;
        private int totalPagesProcessed = 0;
        
        public PdfProgressCallback(FolderProgressTracker folderTracker) {
            this.folderTracker = folderTracker;
        }
        
        @Override
        public void onFileStarted(String fileName, int totalPages, long fileSizeBytes) {
            fileStartTimeMs = System.currentTimeMillis();
            totalPagesProcessed = 0;
            // System.err.println("DEBUG: onFileStarted - " + fileName + " (" + totalPages + " pages) - currentWorkItem: " + (currentWorkItem != null ? "SET" : "NULL")); // Debug removed
        }
        
        @Override
        public void onPageCompleted(int pageNumber, long pageProcessingTimeMs) {
            // Update real-time progress with each page completion
            if (currentWorkItem != null) {
                folderTracker.markPageProgress(currentWorkItem, pageNumber, pageProcessingTimeMs);
                totalPagesProcessed++;
                if (pageNumber <= 10 && pageProcessingTimeMs > 0) { // Debug first few pages of actual processing
                    System.err.println("DEBUG: onPageCompleted - page: " + pageNumber + ", time: " + pageProcessingTimeMs + "ms, workItem: SET");
                } // Debug to check if real OCR timing reaches folder tracker
            } else {
                // This shouldn't happen if setCurrentWorkItem is called properly
                System.err.println("DEBUG: onPageCompleted called without currentWorkItem - page: " + pageNumber + ", time: " + pageProcessingTimeMs + "ms");
            }
        }
        
        public void setCurrentWorkItem(WorkItem workItem) {
            this.currentWorkItem = workItem;
            if (workItem != null) {
                folderTracker.startFileProgress(workItem);
            }
        }
        
        public long getTotalProcessingTimeMs() {
            return System.currentTimeMillis() - fileStartTimeMs;
        }
        
        public int getTotalPagesProcessed() {
            return totalPagesProcessed;
        }
    }
    
    @Override
    public Integer call() throws Exception {
        // Validate input folder
        if (!inputFolder.exists() || !inputFolder.isDirectory()) {
            System.err.println("Error: Input folder does not exist or is not a directory: " + inputFolder);
            return 1;
        }
        
        // Set default output folder
        Path outputPath = outputFolder != null ? 
            outputFolder.toPath() : inputFolder.toPath();
            
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }
        
        // Initialize file processor
        var fileProcessor = new FileProcessor(inputFolder.toPath(), recursive, verbose);
        
        // Initialize work queue and start background scope discovery
        var workQueue = new WorkQueue();
        var scopeDiscovery = new ScopeDiscoveryTask(workQueue, fileProcessor, verbose);
        var discoveryThread = new Thread(scopeDiscovery, "scope-discovery");
        discoveryThread.setDaemon(true);
        discoveryThread.start();
        
        // Wait for first item or discovery completion
        if (!waitForFirstWork(workQueue)) {
            System.err.println("No supported files found (images: .jpg/.png/.bmp/.tiff/.webp, PDFs: .pdf)");
            return 0;
        }
        
        // Enable folder mode for dual progress support
        DualProgressRenderer.enableFolderMode();
        
        // Initialize folder progress tracker with WorkQueue for bytes-based progress
        var progress = new FolderProgressTracker("Folder OCR Processing", workQueue, verbose, "folder-progress");
        progress.start();
        
        // Initialize progress callback for real-time ETA updates
        currentCallback = new PdfProgressCallback(progress);
        
        // Create progress-aware logger to prevent output interference
        var progressAwareLog = ProgressAwareLogFormatter.create(verbose, progress);
        
        // Process work items as they become available
        int successCount = 0;
        int errorCount = 0;
        
        while (workQueue.hasWork()) {
            WorkItem workItem = null;
            try {
                workItem = workQueue.takeWork();
                
                // Set current work item for progress tracking
                if (workItem.fileType() == FileType.PDF) {
                    currentCallback.setCurrentWorkItem(workItem);
                }
                
                // FolderProgressTracker automatically uses WorkQueue metrics - no manual updates needed
                
                boolean success = switch (workItem.fileType()) {
                    case IMAGE -> processImageFile(workItem.filePath(), outputPath, progressAwareLog);
                    case PDF -> processPdfFile(workItem.filePath(), outputPath, progressAwareLog);
                    default -> {
                        progress.err("Unsupported file type: " + workItem.filePath().getFileName());
                        yield false;
                    }
                };
                
                if (success) {
                    successCount++;
                    // Mark work completed for accurate bytes-based progress
                    if (workItem.fileType() == FileType.PDF && currentCallback != null) {
                        // Use callback data for more accurate tracking
                        long processingTime = currentCallback.getTotalProcessingTimeMs();
                        int pagesProcessed = currentCallback.getTotalPagesProcessed();
                        progress.markWorkCompleted(workItem, pagesProcessed, processingTime);
                    } else {
                        // For images and already completed files, use actual page count if available
                        int actualPages = getActualPageCount(workItem);
                        progress.markWorkCompleted(workItem, actualPages);
                    }
                } else {
                    errorCount++;
                    // Still mark as completed for progress tracking (even if failed)
                    progress.markWorkCompleted(workItem, 0); // 0 pages processed for failed files
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                var fileName = workItem != null ? workItem.getDisplayName() : "unknown file";
                progress.err(String.format("Failed to process %s: %s", fileName, e.getMessage()));
                errorCount++;
                // Mark work completed even for exceptions (for progress tracking)
                if (workItem != null) {
                    progress.markWorkCompleted(workItem, 0); // 0 pages processed for failed files
                }
            }
        }
        
        // Complete progress and show summary
        progress.done();
        System.out.printf("📊 Processing complete: %d success, %d errors%n", successCount, errorCount);
        
        // Disable folder mode to clean up
        DualProgressRenderer.disableFolderMode();
        
        return errorCount > 0 ? 1 : 0;
    }
    
    private boolean processImageFile(Path file, Path outputPath, ProgressAwareLogFormatter log) throws Exception {
        log.step("IMAGE", "Processing " + file.getFileName());
        
        try {
            // Load and process image
            var image = javax.imageio.ImageIO.read(file.toFile());
            if (image == null) {
                log.error("IMAGE", "Unable to read: " + file.getFileName());
                return false;
            }
            
            byte[] bgraData = OcrTool.convertToBGRA(image);
            
            // Perform OCR
            OcrResult result;
            try (var ocrApi = new OneOcrApi()) {
                var initOptions = ocrApi.createInitOptions();
                var pipeline = ocrApi.createPipeline(initOptions);
                var processOptions = ocrApi.createProcessOptions(maxLines);
                
                result = ocrApi.recognizeImage(pipeline, processOptions, 
                    image.getWidth(), image.getHeight(), bgraData);
                    
                processOptions.close();
                pipeline.close();
                initOptions.close();
            }
            
            // Generate outputs preserving relative path structure  
            var textFile = createOutputPath(file, outputPath, ".oneocr.txt");
            var text = result.text();
            
            // Ensure parent directory exists
            Files.createDirectories(textFile.getParent());
            java.nio.file.Files.writeString(textFile, text);
            
            if (generateSvg) {
                var svgFile = createOutputPath(file, outputPath, ".oneocr.svg");
                // TODO: Add SVG generation if needed
            }
            
            log.success("IMAGE", String.format("Completed: %s (%d chars)", 
                file.getFileName(), text.length()));
            return true;
            
        } catch (Exception e) {
            log.error("IMAGE", "Failed " + file.getFileName() + ": " + e.getMessage());
            return false;
        }
    }
    
    private boolean processPdfFile(Path file, Path outputPath, ProgressAwareLogFormatter log) throws Exception {
        log.step("PDF", String.format("Processing %s (%dMB)", 
            file.getFileName(), Files.size(file) / (1024 * 1024)));
        
        try {
            // Create the output directory preserving relative path structure
            var pdfOutputDir = createOutputPath(file, outputPath, ".oneocr");
            
            // EARLY EXIT: Check if PDF processing is already complete (skip expensive operations)
            if (isPdfProcessingComplete(file, pdfOutputDir)) {
                log.success("PDF", "Already completed: " + file.getFileName());
                
                // IMPORTANT: Report pages for already-completed files to folder progress tracker
                // Even though we don't need to process this file, we need to count its pages
                if (currentCallback != null && currentCallback.currentWorkItem != null) {
                    // Get the page count from the work item (estimated pages)
                    WorkItem workItem = currentCallback.currentWorkItem;
                    int pageCount = workItem.estimatedPages();
                    // System.err.println("DEBUG: Already completed file - reporting " + pageCount + " pages"); // Debug removed
                    
                    // Simulate file started and all pages completed (with 0ms processing time)
                    currentCallback.onFileStarted(file.getFileName().toString(), pageCount, workItem.fileSizeBytes());
                    for (int page = 1; page <= pageCount; page++) {
                        currentCallback.onPageCompleted(page, 0); // 0ms = skipped
                    }
                }
                
                return true;
            }
            
            
            // DUAL PROGRESS: Both folder and PDF progress will render simultaneously
            
            // Create and configure PdfOcrCommand instance
            var pdfCommand = new PdfOcrCommand();
            
            // Set up parent command relationship so PdfOcrCommand can access --threads
            pdfCommand.setParentCommand(this.parentCommand);
            
            // Set up page progress callback for real-time ETA updates
            pdfCommand.setPageProgressCallback(currentCallback);
            
            // Create command line args for the PDF command
            java.util.List<String> pdfArgsList = new java.util.ArrayList<>();
            pdfArgsList.add(file.toString());
            pdfArgsList.add("-o");
            pdfArgsList.add(pdfOutputDir.toString());
            
            if (verbose) {
                pdfArgsList.add("--verbose");
            }
            
            // Note: --threads is a global parameter handled by the parent command,
            // not passed directly to PDF subcommand. The PdfOcrCommand will get
            // the threads value through @ParentCommand annotation.
            
            String[] pdfArgs = pdfArgsList.toArray(new String[0]);
            
            // Parse arguments through picocli but call directly to preserve callback
            var cmdLine = new picocli.CommandLine(pdfCommand);
            cmdLine.parseArgs(pdfArgs); // Parse arguments into the command object
            int result = pdfCommand.call(); // Call directly to preserve callback
            
            // DUAL PROGRESS: Continue with simultaneous rendering
            
            if (result == 0) {
                log.success("PDF", "Processed: " + file.getFileName());
                return true;
            } else {
                log.error("PDF", "Failed: " + file.getFileName());
                return false;
            }
            
        } catch (Exception e) {
            // HIERARCHICAL PROGRESS COORDINATION: Resume folder progress even on exception
            ProgressTracker.resumeAll(); // Resume all paused progress bars
            log.error("PDF", "Failed " + file.getFileName() + ": " + e.getMessage());
            return false;
        } finally {
            // SAFETY: Ensure progress bars are always resumed
            ProgressTracker.resumeAll();
        }
    }
    
    /**
     * Check if PDF processing is already complete by looking for final output files
     */
    private boolean isPdfProcessingComplete(Path pdfFile, Path outputDir) {
        try {
            // Check if output directory exists
            if (!Files.exists(outputDir) || !Files.isDirectory(outputDir)) {
                return false;
            }
            
            // Look for combined output files (final result)
            String baseName = pdfFile.getFileName().toString();
            Path combinedTxt = outputDir.resolve(baseName + ".oneocr.txt");
            Path combinedXhtml = outputDir.resolve(baseName + ".oneocr.xhtml");
            
            // Check if both combined files exist and are non-empty
            if (Files.exists(combinedTxt) && Files.size(combinedTxt) > 0 &&
                Files.exists(combinedXhtml) && Files.size(combinedXhtml) > 0) {
                return true;
            }
            
            // Alternative check: Look for range files (pg[001-XXX].txt/xhtml) which indicate completion
            try (var files = Files.list(outputDir)) {
                boolean hasRangeTxt = files.anyMatch(file -> 
                    file.getFileName().toString().matches(".*\\.pg\\[\\d+-\\d+\\]\\.txt$"));
                    
                if (hasRangeTxt) {
                    // Double-check by looking for the corresponding XHTML
                    try (var files2 = Files.list(outputDir)) {
                        return files2.anyMatch(file -> 
                            file.getFileName().toString().matches(".*\\.pg\\[\\d+-\\d+\\]\\.xhtml$"));
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            // If we can't determine, assume not complete to be safe
            return false;
        }
    }
    
    private boolean waitForFirstWork(WorkQueue workQueue) throws InterruptedException {
        var maxWaitMs = 30000;
        var startTime = System.currentTimeMillis();
        
        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            if (workQueue.getQueueSize() > 0 || workQueue.isDiscoveryComplete()) {
                return workQueue.getTotalFiles() > 0;
            }
            Thread.sleep(100);
        }
        
        return workQueue.getTotalFiles() > 0;
    }
    
    // updateProgressScope() removed - FolderProgressTracker now gets totals directly from WorkQueue
    
    private int getActualPageCount(WorkItem workItem) {
        // If we already have actual page count, use it
        if (workItem.isPageCountActual()) {
            return workItem.estimatedPages();
        }
        
        // For already completed PDFs, try to get actual page count
        if (workItem.fileType() == FileProcessor.FileType.PDF) {
            try {
                var pdfInfo = xyz.jphil.win11_oneocr.tools.pdf.PdfInfoUtil.getPdfInfo(workItem.filePath().toFile());
                return pdfInfo.pageCount();
            } catch (Exception e) {
                // Fallback to estimated pages if we can't read PDF
                return workItem.estimatedPages();
            }
        }
        
        return workItem.estimatedPages(); // For images
    }
    
    /**
     * Create output path preserving relative directory structure from input to output root.
     * 
     * Examples:
     * - Input: /source/docs/sub/file.pdf, Output: /target, Suffix: .oneocr
     * - Result: /target/docs/sub/file.pdf.oneocr
     * 
     * - Input: /source/file.pdf, Output: /target, Suffix: .oneocr  
     * - Result: /target/file.pdf.oneocr
     */
    private Path createOutputPath(Path inputFile, Path outputRoot, String suffix) {
        // Calculate relative path from input root to the file
        Path inputRoot = inputFolder.toPath();
        Path relativePath = inputRoot.relativize(inputFile);
        
        // Create output path preserving directory structure
        Path outputFile = outputRoot.resolve(relativePath.toString() + suffix);
        
        return outputFile;
    }
}