package xyz.jphil.win11_oneocr.tools.folder;

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
        long knownTotalPages = totalPages.get(); // Pages from PDFs we've read (started processing)
        long totalBytesAll = totalBytes.get(); // Total bytes of all PDFs
        long completedPagesCount = completedPages.get(); // Pages from completed PDFs  
        long completedBytesCount = completedBytes.get(); // Bytes from completed PDFs
        
        if (completedPagesCount > 0 && completedBytesCount > 0) {
            // Use completed PDFs for accurate ratio
            double pagesPerByte = (double) completedPagesCount / completedBytesCount;
            long estimatedTotal = Math.round(totalBytesAll * pagesPerByte);
            return Math.max(knownTotalPages, estimatedTotal);
        } else if (knownTotalPages > 0 && completedBytesCount > 0) {
            // Fallback: Use known pages from started PDFs with completed bytes
            // Your formula: estimated_total_pages = total_pdfs_size * (n_pdf_total_pages / n_pdf_total_filesize)
            double pagesPerByte = (double) knownTotalPages / completedBytesCount;
            long estimatedTotal = Math.round(totalBytesAll * pagesPerByte);
            return Math.max(knownTotalPages, estimatedTotal);
        } else {
            return knownTotalPages; // No data for estimation yet
        }
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