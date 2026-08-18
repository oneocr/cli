package oneocr.cli.folder;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class WorkQueue {
    private final BlockingQueue<ScopeItem> scopeQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean discoveryComplete = new AtomicBoolean(false);
    
    // Total metrics (discovered during scope discovery)
    private final AtomicInteger totalFiles = new AtomicInteger(0);
    private final AtomicLong totalPages = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);
    
    // Completed metrics (updated as work progresses)
    private final AtomicInteger completedFiles = new AtomicInteger(0);
    private final AtomicLong completedPages = new AtomicLong(0);
    private final AtomicLong completedBytes = new AtomicLong(0);
    
    // Started files metrics (for stable total page estimation)  
    private final AtomicLong startedFilesBytes = new AtomicLong(0);
    
    // Cached stable total (calculated once, never changes)
    private volatile long cachedTotalPages = -1;
    
    // Reliability flags
    private final AtomicInteger pagesFromActualCount = new AtomicInteger(0);
    private final AtomicInteger pagesFromEstimate = new AtomicInteger(0);
    
    public void addScopeItem(ScopeItem item) {
        scopeQueue.offer(item);
        totalFiles.incrementAndGet();
        totalBytes.addAndGet(item.fileSizeBytes());
        
        // Don't add pages during scoping - will be added during actual processing
        // This keeps scoping fast (no PDF page count reading)
    }
    
    public WorkItem takeWork() throws InterruptedException {
        // Take ScopeItem from queue and convert to WorkItem (reads PDF info when needed)
        ScopeItem scopeItem = scopeQueue.take();
        WorkItem workItem = scopeItem.toWorkItem();
        
        // Update page count metrics now that we have actual/estimated pages
        totalPages.addAndGet(workItem.estimatedPages());
        if (workItem.isPageCountActual()) {
            pagesFromActualCount.addAndGet(workItem.estimatedPages());
        } else {
            pagesFromEstimate.addAndGet(workItem.estimatedPages());
        }
        
        // Track bytes from started files for stable total pages estimation
        startedFilesBytes.addAndGet(workItem.fileSizeBytes());
        
        return workItem;
    }
    
    public boolean hasWork() {
        return !scopeQueue.isEmpty() || !discoveryComplete.get();
    }
    
    public void markDiscoveryComplete() {
        discoveryComplete.set(true);
    }
    
    public boolean isDiscoveryComplete() {
        return discoveryComplete.get();
    }
    
    public int getTotalFiles() {
        return totalFiles.get();
    }
    
    public long getTotalPages() {
        // Use dynamic estimation based on processed PDFs
        return getEstimatedTotalPages();
    }
    
    private long getEstimatedTotalPages() {
        // User's formula: total_pages = estimated_pages_per_byte × summation(all_pdfs_size)
        // estimated_pages_per_byte = pages_from_started_files ÷ bytes_from_started_files
        
        long totalBytesAll = totalBytes.get(); // summation(all_pdfs_size)
        long pagesFromStartedFiles = totalPages.get(); // pages_count_of_all_started_pdfs_where_page_count_is_known
        long bytesFromStartedFiles = startedFilesBytes.get(); // file_size_of_all_started_pdfs_where_page_count_is_known
        
        if (pagesFromStartedFiles > 0 && bytesFromStartedFiles > 0 && totalBytesAll > 0) {
            // Apply your exact formula
            double estimatedPagesPerByte = (double) pagesFromStartedFiles / bytesFromStartedFiles;
            long estimatedTotal = Math.round(estimatedPagesPerByte * totalBytesAll);
            
            
            return estimatedTotal;
        } else {
            // Fallback until we have started files
            return pagesFromStartedFiles;
        }
    }
    
    // Get total bytes from files where we know the page count (started processing)
    // This provides a stable denominator for the pages-per-byte ratio
    private long getBytesFromStartedFiles() {
        // Return bytes from files where takeWork() was called (page count became known)
        return startedFilesBytes.get();
    }
    
    public long getTotalBytes() {
        return totalBytes.get();
    }
    
    public int getCompletedFiles() {
        return completedFiles.get();
    }
    
    public long getCompletedPages() {
        return completedPages.get();
    }
    
    public long getCompletedBytes() {
        return completedBytes.get();
    }
    
    public void markWorkCompleted(WorkItem item, int actualPagesProcessed) {
        completedFiles.incrementAndGet();
        completedPages.addAndGet(actualPagesProcessed);
        completedBytes.addAndGet(item.fileSizeBytes());
    }
    
    // Track partial progress for a file (page-by-page updates)
    // Since we're using pages-based ETA, we only need to track page progress
    public void markPartialProgress(WorkItem item, int pagesProcessedSoFar) {
        // Only track page progress - bytes-based tracking is not needed for pages-based ETA
        completedPages.addAndGet(1);
    }
    
    // This method is kept for compatibility but not used in pages-based ETA
    @Deprecated
    public long getProportionalBytesProcessed() {
        // Return only completed bytes since we're using pages-based calculations
        return completedBytes.get();
    }
    
    public double getPageCountReliability() {
        long total = pagesFromActualCount.get() + pagesFromEstimate.get();
        return total > 0 ? (double) pagesFromActualCount.get() / total : 0.0;
    }
    
    public ProgressMetrics getProgressMetrics() {
        return new ProgressMetrics(
            totalFiles.get(), completedFiles.get(),
            getTotalPages(), completedPages.get(),  // Use getTotalPages() for estimation
            totalBytes.get(), completedBytes.get(),
            getPageCountReliability()
        );
    }
    
    public int getQueueSize() {
        return scopeQueue.size();
    }
}