package oneocr.cli.folder;

public record ProgressMetrics(
    int totalFiles,
    int completedFiles, 
    long totalPages,
    long completedPages,
    long totalBytes,
    long completedBytes,
    double pageCountReliability
) {
    
    public double getProgressByPages() {
        return totalPages > 0 ? (double) completedPages / totalPages : 0.0;
    }
    
    public double getProgressByBytes() {
        return totalBytes > 0 ? (double) completedBytes / totalBytes : 0.0;
    }
    
    public double getProgressByFiles() {
        return totalFiles > 0 ? (double) completedFiles / totalFiles : 0.0;
    }
    
    public double getBestProgress() {
        if (pageCountReliability > 0.7 && totalPages > 0) {
            return getProgressByPages();
        } else if (totalBytes > 0) {
            return getProgressByBytes();
        } else {
            return getProgressByFiles();
        }
    }
    
    public String getBestProgressLabel() {
        if (pageCountReliability > 0.7 && totalPages > 0) {
            return "pages";
        } else if (totalBytes > 0) {
            return "bytes";
        } else {
            return "files";
        }
    }
    
    public String formatBytes(long bytes) {
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