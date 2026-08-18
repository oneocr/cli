package oneocr.cli;

/**
 * Simple progress-aware logging that coordinates with active progress bars.
 * Extends LogFormatter with minimal changes to prevent output interference.
 */
public class ProgressAwareLogFormatter extends LogFormatter {
    
    private final ProgressTracker progressTracker;
    
    public ProgressAwareLogFormatter(boolean verbose, ProgressTracker progressTracker) {
        super(verbose);
        this.progressTracker = progressTracker;
    }
    
    // Override all logging methods to add progress coordination
    @Override
    public void info(String category, String message) {
        logWithCoordination(() -> super.info(category, message));
    }
    
    @Override
    public void success(String category, String message) {
        logWithCoordination(() -> super.success(category, message));
    }
    
    @Override
    public void warning(String category, String message) {
        logWithCoordination(() -> super.warning(category, message));
    }
    
    @Override
    public void error(String category, String message) {
        logWithCoordination(() -> super.error(category, message));
    }
    
    @Override
    public void debug(String category, String message) {
        logWithCoordination(() -> super.debug(category, message));
    }
    
    @Override
    public void step(String category, String message) {
        logWithCoordination(() -> super.step(category, message));
    }
    
    @Override
    public void complete(String category, String message) {
        logWithCoordination(() -> super.complete(category, message));
    }
    
    /**
     * Core coordination: clear progress → log → redraw progress
     */
    private void logWithCoordination(Runnable logAction) {
        if (progressTracker != null) {
            synchronized (ProgressAwareLogFormatter.class) {
                progressTracker.clearLine();  // Clear progress bar
                try { Thread.sleep(1); } catch (InterruptedException ignored) {} // Brief pause for terminal
                logAction.run();              // Print log message
                System.err.flush();           // Ensure log is fully written
                progressTracker.forceRedraw(); // Redraw progress bar
            }
        } else {
            logAction.run(); // Fallback: just log normally
        }
    }
    
    // Factory methods
    public static ProgressAwareLogFormatter create(boolean verbose, ProgressTracker progressTracker) {
        return new ProgressAwareLogFormatter(verbose, progressTracker);
    }
}