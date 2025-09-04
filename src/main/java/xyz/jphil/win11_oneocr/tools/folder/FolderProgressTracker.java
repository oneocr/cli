package xyz.jphil.win11_oneocr.tools.folder;

import xyz.jphil.win11_oneocr.tools.ProgressTracker;
import xyz.jphil.win11_oneocr.tools.DualProgressRenderer;

/**
 * Folder-mode ProgressTracker with dynamic total support.
 * Uses WorkQueue ProgressMetrics for bytes-based progress calculation.
 * Registers with DualProgressRenderer for coordinated display.
 */
public class FolderProgressTracker extends ProgressTracker {
    private final WorkQueue workQueue;
    private final String trackerId;
    
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
        
        // Always show bytes since we're always using filesize-based progress
        // Format display with brackets for clarity  
        if ("bytes".equals(progressLabel) && metrics.totalBytes() > 0) {
            return String.format("[%s] %5.1f%% (%s/%s) [files: %d/%d] [pages: %d/~%d]", 
                progressBar, progress * 100,
                metrics.formatBytes(metrics.completedBytes()),
                metrics.formatBytes(metrics.totalBytes()),
                metrics.completedFiles(), metrics.totalFiles(),
                metrics.completedPages(), metrics.totalPages());
        } else {
            // Fallback when no bytes data available yet
            return String.format("[%s] %5.1f%% [files: %d/%d] [pages: %d/~%d] (no bytes data yet)", 
                progressBar, progress * 100,
                metrics.completedFiles(), metrics.totalFiles(),
                metrics.completedPages(), metrics.totalPages());
        }
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
    public void markWorkCompleted(WorkItem workItem, int actualPagesProcessed) {
        workQueue.markWorkCompleted(workItem, actualPagesProcessed);
        
        // Trigger coordinated rendering in folder mode
        if (DualProgressRenderer.isFolderModeActive()) {
            DualProgressRenderer.renderAll();
        }
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