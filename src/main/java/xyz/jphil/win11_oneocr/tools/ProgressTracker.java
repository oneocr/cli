package xyz.jphil.win11_oneocr.tools;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

@Getter
@Setter
@Accessors(fluent = true)
public class ProgressTracker {
    private final String task;
    private final int total;
    private final boolean verbose;
    private final Instant start = Instant.now();
    private final Terminal terminal;
    
    private final AtomicInteger completed = new AtomicInteger(0);
    private final AtomicReference<Instant> lastUpdate = new AtomicReference<>(Instant.now());
    private final AtomicLong lastCompleted = new AtomicLong(0);
    
    // ETA calculation based on actual processing time (excludes quick skips)
    private final AtomicInteger actualProcessingCount = new AtomicInteger(0);
    private final AtomicLong actualProcessingTimeMs = new AtomicLong(0);
    
    // Progress coordination
    private volatile boolean paused = false;
    private volatile boolean showProgress = true;
    private static final Set<ProgressTracker> activeTrackers = ConcurrentHashMap.newKeySet();
    
    public record Stats(double pct, Duration elapsed, String eta, String rate) {}
    
    public ProgressTracker(String task, int total, boolean verbose) {
        this.task = task;
        this.total = total;
        this.verbose = verbose;
        try {
            this.terminal = TerminalBuilder.builder().system(true).build();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize terminal", e);
        }
    }
    
    public ProgressTracker start() {
        activeTrackers.add(this);
        if (verbose) System.err.printf("▶ Starting %s (%d items)%n", task, total);
        show();
        return this;
    }
    
    public ProgressTracker update(int n) { 
        completed.set(n); 
        show(); 
        return this;
    }
    
    public ProgressTracker inc() { 
        int current = completed.incrementAndGet();
        
        // In folder mode, trigger coordinated dual progress rendering
        if (DualProgressRenderer.isFolderModeActive()) {
            DualProgressRenderer.renderAll();
        } else {
            show();
        }
        
        return this;
    }
    
    public ProgressTracker done() {
        completed.set(total);
        activeTrackers.remove(this);
        var elapsed = Duration.between(start, Instant.now());
        System.err.printf("✓ %s completed (%d items) in %s%n", task, total, fmt(elapsed));
        return this;
    }
    
    public ProgressTracker err(String msg) {
        System.err.printf("✗ Error in %s: %s%n", task, msg);
        return this;
    }
    
    // Progress coordination methods
    public ProgressTracker pause() {
        this.paused = true;
        return this;
    }
    
    public ProgressTracker resume() {
        this.paused = false;
        show(); // Redraw progress after resuming
        return this;
    }
    
    public void clearLine() {
        if (total == 0) return;
        int termWidth = getEffectiveTerminalWidth();
        if (termWidth > 50) {
            // Clear the entire line with proper terminal width
            System.err.printf("\r%s\r", " ".repeat(termWidth));
            System.err.flush(); // Ensure clearing is applied immediately
        }
    }
    
    public void forceRedraw() {
        if (!paused) {
            show();
        }
    }
    
    // Static coordination methods
    public static void pauseAll() {
        activeTrackers.forEach(ProgressTracker::pause);
    }
    
    public static void resumeAll() {
        activeTrackers.forEach(ProgressTracker::resume);
    }
    
    public static void clearAllLines() {
        activeTrackers.forEach(ProgressTracker::clearLine);
    }
    
    private void show() {
        if (total == 0 || paused || !showProgress) return;
        
        var stats = calcStats();
        int currentCompleted = completed.get();
        
        // Always try to show visual progress bar when possible
        int termWidth = getEffectiveTerminalWidth();
        if (termWidth > 50) {
            // Full progress bar with ETA/rate (verbose) or compact (non-verbose)
            if (verbose) {
                var bar = bar(stats.pct(), Math.min(25, termWidth - 50));
                System.err.printf("\r%s %5.1f%% (%d/%d) %s %s", 
                    bar, stats.pct(), currentCompleted, total, stats.eta(), stats.rate());
            } else {
                var bar = bar(stats.pct(), Math.min(20, termWidth - 25));
                System.err.printf("\r%s %5.1f%% (%d/%d)", 
                    bar, stats.pct(), currentCompleted, total);
            }
            if (currentCompleted == total) {
                System.err.println(); // Final newline when done
            }
        } else {
            // Fallback for narrow terminals: simple text progress
            if (currentCompleted % Math.max(1, total / 10) == 0 || currentCompleted == total) {
                System.err.printf("  Progress: %d/%d (%.1f%%)%n", currentCompleted, total, stats.pct());
            }
        }
    }
    
    private int getEffectiveTerminalWidth() {
        try {
            int jlineWidth = terminal.getWidth();
            // JLine sometimes returns very small values for "dumb" terminals
            // Use a reasonable default if detection fails
            return jlineWidth > 20 ? jlineWidth : 80; // Assume standard 80-char terminal
        } catch (Exception e) {
            return 80; // Safe fallback
        }
    }
    
    Stats calcStats() { // Package-private for dual progress support
        int currentCompleted = completed.get();
        var pct = (double) currentCompleted / total() * 100;
        var elapsed = Duration.between(start, Instant.now());
        var eta = eta(elapsed, currentCompleted);
        var rate = rate(elapsed, currentCompleted);
        return new Stats(pct, elapsed, eta, rate);
    }
    
    private String bar(double pct, int width) {
        var filled = (int) (pct / 100 * width);
        var sb = new StringBuilder("[");
        
        for (int i = 0; i < width; i++) {
            sb.append(i < filled ? "█" : 
                     i == filled && pct % (100.0 / width) > 0 ? "▌" : "░");
        }
        return sb.append("]").toString();
    }
    
    private String eta(Duration elapsed, int currentCompleted) {
        if (currentCompleted == 0) return "ETA: --:--";
        
        var avgSecs = elapsed.getSeconds() / currentCompleted;
        var etaSecs = (total - currentCompleted) * avgSecs;
        return "ETA: " + fmt(Duration.ofSeconds(etaSecs));
    }
    
    private String rate(Duration elapsed, int currentCompleted) {
        if (elapsed.getSeconds() == 0) return "Rate: --/s";
        
        var now = Instant.now();
        var lastUpdateTime = lastUpdate.get();
        var sinceLast = Duration.between(lastUpdateTime, now);
        
        if (sinceLast.getSeconds() >= 2) {
            var itemsSince = currentCompleted - lastCompleted.get();
            var rate = itemsSince / (double) sinceLast.getSeconds();
            lastUpdate.set(now);
            lastCompleted.set(currentCompleted);
            return rate >= 1 ? "Rate: %.1f/s".formatted(rate) : 
                               "Rate: %.1f/min".formatted(rate * 60);
        }
        
        var rate = currentCompleted / (double) elapsed.getSeconds();
        return rate >= 1 ? "Rate: %.1f/s".formatted(rate) : 
                          "Rate: %.1f/min".formatted(rate * 60);
    }
    
    private String fmt(Duration d) {
        var h = d.toHours();
        var m = d.toMinutesPart();
        var s = d.toSecondsPart();
        
        return h > 0 ? "%dh %02dm".formatted(h, m) :
               m > 0 ? "%dm %02ds".formatted(m, s) :
                       "%ds".formatted(s);
    }
    
    @Override
    public String toString() {
        if (total == 0) return String.format("[%s] 0.0%%", "░".repeat(25));
        
        var stats = calcStats();
        int currentCompleted = completed.get();
        var progressBar = bar(stats.pct(), 25);
        
        return String.format("%s %.1f%% (%d/%d) %s %s", 
            progressBar, stats.pct(), currentCompleted, total, stats.eta(), stats.rate());
    }
}