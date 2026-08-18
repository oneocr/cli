package oneocr.cli.folder;

import java.nio.file.Path;

public record WorkItem(
    Path filePath,
    FileProcessor.FileType fileType,
    long fileSizeBytes,
    int estimatedPages,
    boolean isPageCountActual
) {
    
    public String getDisplayName() {
        return filePath.getFileName().toString();
    }
    
    public String getFormattedSize() {
        return String.format("%.0fMB", fileSizeBytes / (1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return String.format("%s (%s, %d pages)", getDisplayName(), getFormattedSize(), estimatedPages);
    }
}