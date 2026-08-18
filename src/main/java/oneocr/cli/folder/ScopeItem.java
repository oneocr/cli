package oneocr.cli.folder;

import java.nio.file.Path;

/**
 * Lightweight item for fast scope discovery phase.
 * Contains only basic file info without expensive PDF page count reading.
 */
public record ScopeItem(
    Path filePath,
    FileProcessor.FileType fileType,
    long fileSizeBytes
) {
    public String getDisplayName() {
        return filePath.getFileName().toString();
    }
    
    /**
     * Convert to WorkItem for actual processing (reads PDF info when needed)
     */
    public WorkItem toWorkItem() {
        if (fileType == FileProcessor.FileType.IMAGE) {
            // Images always have 1 page
            return new WorkItem(filePath, fileType, fileSizeBytes, 1, true);
        } else {
            // For PDFs, read page count only when needed for processing
            try {
                var pdfInfo = oneocr.cli.pdf.PdfInfoUtil.getPdfInfo(filePath.toFile());
                return new WorkItem(filePath, fileType, fileSizeBytes, pdfInfo.pageCount(), true);
            } catch (Exception e) {
                // Fallback: Size-based page estimation
                int estimatedPages = estimatePagesFromSize(fileSizeBytes);
                return new WorkItem(filePath, fileType, fileSizeBytes, estimatedPages, false);
            }
        }
    }
    
    private int estimatePagesFromSize(long sizeBytes) {
        // Conservative estimation: ~150KB per page
        long avgBytesPerPage = 150 * 1024;
        return Math.max(1, (int)(sizeBytes / avgBytesPerPage));
    }
}