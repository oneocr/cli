package xyz.jphil.win11_oneocr.tools;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Folder-mode only: Renders multiple progress bars simultaneously.
 * Does not affect single PDF processing mode.
 */
public class DualProgressRenderer {
    private static volatile boolean folderModeActive = false;
    private static final Map<String, ProgressTracker> activeTrackers = new ConcurrentHashMap<>();
    private static final Object renderLock = new Object();
    
    public static void enableFolderMode() {
        folderModeActive = true;
    }
    
    public static void disableFolderMode() {
        folderModeActive = false;
        activeTrackers.clear();
    }
    
    public static boolean isFolderModeActive() {
        return folderModeActive;
    }
    
    public static void register(String id, ProgressTracker tracker) {
        if (folderModeActive) {
            activeTrackers.put(id, tracker);
        }
    }
    
    public static void unregister(String id) {
        activeTrackers.remove(id);
    }
    
    /**
     * Render all active progress bars simultaneously.
     * Called whenever any progress bar needs to update.
     */
    public static void renderAll() {
        if (!folderModeActive) return; // Only render in folder mode
        
        synchronized (renderLock) {
            if (activeTrackers.isEmpty()) return;
            
            // Clear lines for all active progress bars
            int linesToClear = activeTrackers.size();
            for (int i = 0; i < linesToClear; i++) {
                System.err.print("\r\033[K\033[1A"); // Clear line and move up
            }
            
            // Render all progress bars in consistent order
            var trackerList = new ArrayList<>(activeTrackers.values());
            trackerList.sort((a, b) -> a.task().compareTo(b.task())); // Consistent ordering
            
            for (var tracker : trackerList) {
                renderProgressLine(tracker);
            }
        }
    }
    
    private static void renderProgressLine(ProgressTracker tracker) {
        if (tracker.total() == 0 || !tracker.showProgress()) return;
        
        // Use the tracker's toString() method to get enhanced formatting
        // This allows FolderProgressTracker to show bytes+files+pages format
        String progressLine = tracker.toString();
        System.err.println(progressLine);
    }
    
    private static String progressBar(int completed, int total) {
        var width = 25;
        var progress = Math.min(1.0, (double) completed / total);
        var filled = (int) (width * progress);
        
        // ANSI color codes for PDF progress - distinct green theme
        String BRIGHT_GREEN = "\033[92m";    // Bright green for filled
        String DARK_GRAY = "\033[90m";       // Dark gray for empty
        String RESET = "\033[0m";
        
        var sb = new StringBuilder();
        // Filled portion in bright green
        for (int i = 0; i < filled; i++) sb.append(BRIGHT_GREEN).append('█');
        if (filled > 0) sb.append(RESET);
        
        // Empty portion in dark gray
        for (int i = filled; i < width; i++) sb.append(DARK_GRAY).append('░');
        if (filled < width) sb.append(RESET);
        
        return sb.toString();
    }
}