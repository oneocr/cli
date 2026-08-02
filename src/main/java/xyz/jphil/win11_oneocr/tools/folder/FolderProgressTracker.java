package xyz.jphil.win11_oneocr.tools.folder;

import xyz.jphil.win11_oneocr.tools.ProgressTracker;
import xyz.jphil.win11_oneocr.tools.DualProgressRenderer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Folder-mode ProgressTracker with dynamic total support.
 * Uses WorkQueue ProgressMetrics for bytes-based progress calculation.
 * Registers with DualProgressRenderer for coordinated display.
 */
public class FolderProgressTracker extends ProgressTracker {
    private final WorkQueue workQueue;
    private final String trackerId;
    
    // Current file progress tracking for per-file ETA
    private volatile WorkItem currentFile = null;
    private volatile int currentFilePages = 0;
    private volatile long currentFileStartTimeMs = 0;
    
    // Direct timing tracking for folder-level calculations
    private final AtomicLong folderTotalProcessingTimeMs = new AtomicLong(0);
    private final AtomicLong folderTotalPagesProcessed = new AtomicLong(0);
    
    public FolderProgressTracker(String task, WorkQueue workQueue, boolean verbose, String trackerId) {
        super(task, 1, verbose); // Use dummy total, we'll use workQueue metrics
        this.workQueue = workQueue;
        this.trackerId = trackerId;
        
        // Register with dual progress renderer
        DualProgressRenderer.register(trackerId, this);
    }
    
    // updateTotal() no longer needed - WorkQueue handles totals automatically
    
    @Override
    public int total() {
        // Return total files for basic compatibility, but percentage uses bytes
        return workQueue.getTotalFiles();
    }
    
    @Override
    public String toString() {
        var metrics = workQueue.getProgressMetrics();
        double progress = metrics.getProgressByBytes(); // ALWAYS use filesize-based progress as requested
        
        String progressBar = renderFolderProgressBar(progress);
        String progressLabel = "bytes"; // Always bytes since we're always using filesize
        
        // Calculate folder-level ETA and rate using user's exact formulas
        String folderEta = calculateFolderEta(metrics);
        String rate = calculateFolderRate(metrics);
        
        // Folder progress bar shows only folder-level metrics (no file ETA)
        if ("bytes".equals(progressLabel) && metrics.totalBytes() > 0) {
            return String.format("%s %5.1f%% (%s/%s) files: %d/%d pages: %d/~%d %s %s", 
                progressBar, progress * 100,
                metrics.formatBytes(metrics.completedBytes()),
                metrics.formatBytes(metrics.totalBytes()),
                metrics.completedFiles(), metrics.totalFiles(),
                metrics.completedPages(), metrics.totalPages(),
                folderEta, rate);
        } else {
            // Fallback when no bytes data available yet
            return String.format("%s %5.1f%% files: %d/%d pages: %d/~%d %s %s (no bytes data yet)", 
                progressBar, progress * 100,
                metrics.completedFiles(), metrics.totalFiles(),
                metrics.completedPages(), metrics.totalPages(),
                folderEta, rate);
        }
    }
    
    private String calculateFolderEta(ProgressMetrics metrics) {
        // Use pages-based ETA calculation as requested
        // Formula: time_remaining = remaining_pages / avg_pages_per_sec
        
        // Use user's exact formula: 
        // avg_ocr_rate_pg_per_sec = pages_processed_by_all_threads_across_multiple_files_during_this_execution / total_time_spent_in_ocr_not_in_just_skipping_pages_for_all_these_files
        // IMPORTANT: Only count pages that had actual processing time (exclude 0ms skipped pages)
        
        long totalPagesProcessed = folderTotalPagesProcessed.get(); // Only pages with actual processing time
        long totalTimeSpentMs = folderTotalProcessingTimeMs.get();  // Only actual processing time
        
        if (totalPagesProcessed > 0 && totalTimeSpentMs > 0) {
            // Calculate avg_ocr_rate_pg_per_sec (excludes skip time as required)
            double avgOcrRatePgPerSec = (double) totalPagesProcessed / (totalTimeSpentMs / 1000.0);
            
            // Calculate remaining pages based on WorkQueue totals
            long totalPages = metrics.totalPages();
            long completedPages = metrics.completedPages();
            long remainingPages = totalPages - completedPages;
            
            
            if (remainingPages > 0 && avgOcrRatePgPerSec > 0) {
                // User's formula: time_remaining = remaining_pages / avg_ocr_rate_pg_per_sec
                long etaSeconds = Math.round(remainingPages / avgOcrRatePgPerSec);
                
                if (etaSeconds > 0) {
                    return "ETA: " + fmt(java.time.Duration.ofSeconds(etaSeconds));
                } else {
                    return "ETA: 0s"; // Everything processed
                }
            } else if (remainingPages <= 0) {
                return "ETA: 0s"; // Everything processed
            } else {
                return "ETA: --:--"; // No rate data yet
            }
        } else {
            return "ETA: --:--"; // No data yet
        }
    }
    
    private String calculateFolderRate(ProgressMetrics metrics) {
        // Use direct timing data for accurate folder-level rate
        long totalPagesProcessed = folderTotalPagesProcessed.get();
        long totalTimeSpentMs = folderTotalProcessingTimeMs.get();
        
        if (totalPagesProcessed > 0 && totalTimeSpentMs > 0) {
            // Pages per second rate from actual processing time - STRICTLY use "pg/s" format as requested
            double pagesPerSec = (double) totalPagesProcessed / (totalTimeSpentMs / 1000.0);
            return String.format("Rate: %.1f pg/s", pagesPerSec);
        } else {
            return "Rate: -- pg/s";
        }
    }
    
    // Helper method for duration formatting with days support
    private String fmt(java.time.Duration d) {
        var days = d.toDays();
        var h = d.toHoursPart();
        var m = d.toMinutesPart();
        var s = d.toSecondsPart();
        
        return days > 0 ? "%dd %02dh %02dm".formatted(days, h, m) :
               h > 0 ? "%dh %02dm".formatted(h, m) :
               m > 0 ? "%dm %02ds".formatted(m, s) :
                       "%ds".formatted(s);
    }
    
    private String renderFolderProgressBar(double progress) {
        int width = 25;
        int filled = (int) Math.round(progress * width);
        
        // ANSI color codes for folder progress - distinct blue theme
        String BRIGHT_CYAN = "\033[96m";    // Bright cyan for filled
        String DARK_BLUE = "\033[94m";      // Dark blue for empty
        String RESET = "\033[0m";
        
        // Use different characters and colors for folder progress: ▓ (filled) and ░ (empty)
        String filledBar = BRIGHT_CYAN + "▓".repeat(Math.max(0, filled)) + RESET;
        String emptyBar = DARK_BLUE + "░".repeat(Math.max(0, width - filled)) + RESET;
        
        return filledBar + emptyBar;
    }
    
    // Mark work completed with actual processed pages for accurate progress tracking
    public void markWorkCompleted(WorkItem workItem, int actualPagesProcessed, long processingTimeMs) {
        workQueue.markWorkCompleted(workItem, actualPagesProcessed);
        
        // Record processing metrics for proper average-based ETA calculation
        // This excludes skipped files and provides accurate time estimates
        recordActualProcessing(processingTimeMs, workItem.fileSizeBytes(), actualPagesProcessed);
        
        // Trigger coordinated rendering in folder mode
        if (DualProgressRenderer.isFolderModeActive()) {
            DualProgressRenderer.renderAll();
        }
    }
    
    // Overloaded method for backward compatibility
    public void markWorkCompleted(WorkItem workItem, int actualPagesProcessed) {
        markWorkCompleted(workItem, actualPagesProcessed, 0L);
    }
    
    // Start tracking a new file for per-file ETA calculation
    public void startFileProgress(WorkItem workItem) {
        this.currentFile = workItem;
        this.currentFilePages = 0;
        this.currentFileStartTimeMs = System.currentTimeMillis();
    }
    
    // Record page-by-page progress for real-time ETA updates
    public void markPageProgress(WorkItem currentFile, int pageNumber, long pageProcessingTimeMs) {
        if (pageProcessingTimeMs > 0) {
            // Record this page's progress in the base progress tracker (pages-based)
            recordPageProgress(pageProcessingTimeMs, 0); // No bytes needed for pages-based ETA
            
            // Track timing directly for folder-level calculations
            folderTotalProcessingTimeMs.addAndGet(pageProcessingTimeMs);
            folderTotalPagesProcessed.incrementAndGet();
            
            // Debug: Show timing updates every 20 pages to diagnose rate issue
            if (folderTotalPagesProcessed.get() % 20 == 0) {
                double currentRate = (double) folderTotalPagesProcessed.get() / (folderTotalProcessingTimeMs.get() / 1000.0);
                System.err.printf("DEBUG FOLDER RATE: totalPages=%d, totalTimeMs=%d, rate=%.1f pg/s%n", 
                    folderTotalPagesProcessed.get(), folderTotalProcessingTimeMs.get(), currentRate);
            }
            
            // Update partial progress in work queue (pages only)
            workQueue.markPartialProgress(currentFile, 1);
            
            // Update current file tracking and set file ETA on PDF progress bar
            if (this.currentFile == currentFile) {
                this.currentFilePages++;
                
                // Calculate and set file ETA on the PDF progress tracker
                String fileEta = calculateCurrentFileEta();
                String pdfProgressId = "pdf(" + currentFile.filePath().getFileName().toString() + ")";
                ProgressTracker pdfTracker = DualProgressRenderer.getTracker(pdfProgressId);
                if (pdfTracker != null) {
                    pdfTracker.setCustomEta(fileEta);
                }
            }
            
            // Trigger coordinated rendering in folder mode for real-time updates
            if (DualProgressRenderer.isFolderModeActive()) {
                DualProgressRenderer.renderAll();
            }
        }
    }
    
    // Calculate per-file ETA using your formula
    private String calculateCurrentFileEta() {
        if (currentFile == null || currentFilePages == 0 || currentFileStartTimeMs == 0) {
            return "File ETA: --:--";
        }
        
        long currentTimeMs = System.currentTimeMillis();
        long timeSpentMs = currentTimeMs - currentFileStartTimeMs;
        
        if (timeSpentMs > 0 && currentFilePages > 0) {
            // Your formula: time remaining to complete this file = 
            // (time-spent-in-processing-n-pages / n-pages-processed) * (total_pages_in_this_file - current_page_number_being_processed)
            double timePerPage = (double) timeSpentMs / currentFilePages;
            int remainingPages = currentFile.estimatedPages() - currentFilePages;
            
            
            if (remainingPages > 0) {
                long fileEtaMs = Math.round(remainingPages * timePerPage);
                
                // Reasonable bounds check for individual file ETA
                if (fileEtaMs > 0 && fileEtaMs < 24 * 3600 * 1000) { // Less than 24 hours
                    return "File ETA: " + fmt(java.time.Duration.ofMillis(fileEtaMs));
                } else {
                    return "File ETA: --:-- (calc)";
                }
            } else {
                return "File ETA: 0s";
            }
        }
        
        return "File ETA: --:--";
    }
    
    @Override
    public ProgressTracker inc() {
        // Don't call super.inc() - we use WorkQueue metrics instead of base counter
        
        // Trigger coordinated rendering in folder mode
        if (DualProgressRenderer.isFolderModeActive()) {
            DualProgressRenderer.renderAll();
        }
        return this;
    }
    
    @Override
    public ProgressTracker done() {
        var result = super.done();
        DualProgressRenderer.unregister(trackerId);
        return result;
    }
}