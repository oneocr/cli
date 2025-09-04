package xyz.jphil.win11_oneocr.tools.folder;

import java.nio.file.Path;
import java.util.List;
import xyz.jphil.win11_oneocr.tools.pdf.PdfInfoUtil;

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
    
    private PageCountInfo getPageCountInfo(Path file, FileProcessor.FileType fileType, long sizeBytes) {
        if (fileType == FileProcessor.FileType.IMAGE) {
            return new PageCountInfo(1, true);
        } else {
            // For PDFs, try to get actual page count first
            try {
                var pdfInfo = PdfInfoUtil.getPdfInfo(file.toFile());
                return new PageCountInfo(pdfInfo.pageCount(), true);
            } catch (Exception e) {
                // Fallback: Size-based page estimation for Google Drive/network files
                int estimatedPages = estimatePagesFromSize(sizeBytes);
                if (verbose) {
                    System.err.printf("📊 PDF header unreadable for %s, estimated %d pages from %s%n", 
                        file.getFileName(), estimatedPages, formatSize(sizeBytes));
                }
                return new PageCountInfo(estimatedPages, false);
            }
        }
    }
    
    private int estimatePagesFromSize(long sizeBytes) {
        // Estimation based on typical PDF sizes:
        // - Text-heavy PDFs: ~100-200KB per page
        // - Image-heavy PDFs: ~300-500KB per page  
        // - Mixed content: ~150KB per page (conservative)
        long avgBytesPerPage = 150 * 1024; // 150KB per page
        return Math.max(1, (int)(sizeBytes / avgBytesPerPage));
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
    
    private String getReliabilityLabel(double reliability) {
        if (reliability >= 0.9) return "exact";
        else if (reliability >= 0.7) return "mostly exact";
        else if (reliability >= 0.3) return "mixed";
        else return "estimated";
    }
    
    private record PageCountInfo(int pageCount, boolean isActual) {}
}