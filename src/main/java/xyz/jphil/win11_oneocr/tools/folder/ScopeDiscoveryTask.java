package xyz.jphil.win11_oneocr.tools.folder;

import java.nio.file.Path;

public class ScopeDiscoveryTask implements Runnable {
    private final WorkQueue workQueue;
    private final FileProcessor fileProcessor;
    private final boolean verbose;
    
    public ScopeDiscoveryTask(WorkQueue workQueue, FileProcessor fileProcessor, boolean verbose) {
        this.workQueue = workQueue;
        this.fileProcessor = fileProcessor;
        this.verbose = verbose;
    }
    
    @Override
    public void run() {
        try {
            if (verbose) {
                System.err.println("🔍 Starting background scope discovery...");
            }
            
            var files = fileProcessor.discoverFiles();
            // Skip analyzeFiles() - we don't want premature "Found X files" message on network drives
            
            for (Path file : files) {
                var fileType = fileProcessor.getFileType(file);
                var sizeBytes = getFileSize(file);
                
                // Fast scoping: only collect filename + filesize (no PDF page count reading)
                var scopeItem = new ScopeItem(file, fileType, sizeBytes);
                workQueue.addScopeItem(scopeItem);
            }
            
            workQueue.markDiscoveryComplete();
            
            // Don't output here - it interferes with progress bar rendering
            // Scope info will be shown in the final summary or integrated into progress display
            
        } catch (Exception e) {
            System.err.println("❌ Error in scope discovery: " + e.getMessage());
            workQueue.markDiscoveryComplete();
        }
    }
    
    private long getFileSize(Path file) {
        try {
            return java.nio.file.Files.size(file);
        } catch (Exception e) {
            return 0;
        }
    }
    
    private String formatSize(long bytes) {
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else if (bytes >= 1024 * 1024) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        } else if (bytes >= 1024) {
            return String.format("%.1fKB", bytes / 1024.0);
        } else {
            return bytes + "B";
        }
    }
}