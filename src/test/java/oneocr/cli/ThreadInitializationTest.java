package oneocr.cli;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import oneocr.api.OcrInitOptions;
import oneocr.api.OcrPipeline;
import oneocr.api.OcrProcessOptions;
import oneocr.api.OcrResult;
import oneocr.api.OneOcrApi;

/**
 * Test to validate thread initialization hypothesis:
 * Does OneOcrApi need to be initialized in the same thread that uses it?
 */
public class ThreadInitializationTest {
    
    private static final int THREAD_COUNT = 2;
    private static final int TEST_ITERATIONS = 5;
    
    public static void main(String[] args) {
        System.out.println("=== Thread Initialization Test ===");
        
        try {
            // Load test image
            var imageUrl = ThreadInitializationTest.class.getResource("/ocr-book.jpg");
            if (imageUrl == null) {
                throw new RuntimeException("Test image not found");
            }
            BufferedImage testImage = ImageIO.read(imageUrl);
            byte[] bgraData = convertToBGRA(testImage);
            
            System.out.printf("Test image: %dx%d%n", testImage.getWidth(), testImage.getHeight());
            
            // Test 1: Initialize in main thread, use in worker threads (like current PDF processing)
            testMainThreadInit(testImage, bgraData);
            
            // Test 2: Initialize in worker threads (like working ThreadSafetyTest)
            testWorkerThreadInit(testImage, bgraData);
            
            // Test 3: Image validation - check if BufferedImage is corrupted
            testImageValidation(testImage, bgraData);
            
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Test 1: Initialize OneOcrApi in main thread, use in worker threads
     * (This mimics the current PDF processing approach)
     */
    private static void testMainThreadInit(BufferedImage image, byte[] bgraData) {
        System.out.println("\nTEST 1: Main Thread Init → Worker Thread Usage");
        
        try {
            // Initialize OCR instances in MAIN thread (like PDF processing)
            OneOcrApi[] apis = new OneOcrApi[THREAD_COUNT];
            OcrInitOptions[] initOptions = new OcrInitOptions[THREAD_COUNT];
            OcrPipeline[] pipelines = new OcrPipeline[THREAD_COUNT];
            OcrProcessOptions[] processOptions = new OcrProcessOptions[THREAD_COUNT];
            
            for (int i = 0; i < THREAD_COUNT; i++) {
                apis[i] = new OneOcrApi();
                initOptions[i] = apis[i].createInitOptions();
                pipelines[i] = apis[i].createPipeline(initOptions[i]);
                processOptions[i] = apis[i].createProcessOptions();
            }
            
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger totalCount = new AtomicInteger(0);
            
            // Use OCR instances in WORKER threads
            for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
                final int tid = threadId;
                executor.submit(() -> {
                    for (int i = 0; i < TEST_ITERATIONS; i++) {
                        totalCount.incrementAndGet();
                        try {
                            OcrResult result = apis[tid].recognizeImage(pipelines[tid], processOptions[tid],
                                image.getWidth(), image.getHeight(), bgraData);
                            
                            if (result != null && !result.text().trim().isEmpty()) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            System.err.printf("  Thread %d, iteration %d failed: %s%n", tid, i+1, e.getMessage());
                        }
                    }
                });
            }
            
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
            
            // Cleanup
            for (int i = 0; i < THREAD_COUNT; i++) {
                try {
                    processOptions[i].close();
                    pipelines[i].close();
                    initOptions[i].close();
                    apis[i].close();
                } catch (Exception e) {
                    System.err.printf("Cleanup failed: %s%n", e.getMessage());
                }
            }
            
            System.out.printf("  Results: %d/%d successful (%.1f%%)%n", 
                successCount.get(), totalCount.get(), 
                (successCount.get() * 100.0) / totalCount.get());
            
        } catch (Exception e) {
            System.err.println("  Test failed: " + e.getMessage());
        }
    }
    
    /**
     * Test 2: Initialize OneOcrApi in worker threads 
     * (This mimics the working ThreadSafetyTest approach)
     */
    private static void testWorkerThreadInit(BufferedImage image, byte[] bgraData) {
        System.out.println("\nTEST 2: Worker Thread Init → Worker Thread Usage");
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalCount = new AtomicInteger(0);
        
        for (int threadId = 0; threadId < THREAD_COUNT; threadId++) {
            final int tid = threadId;
            executor.submit(() -> {
                try (var api = new OneOcrApi()) {
                    var initOptions = api.createInitOptions();
                    var pipeline = api.createPipeline(initOptions);
                    var processOptions = api.createProcessOptions();
                    
                    for (int i = 0; i < TEST_ITERATIONS; i++) {
                        totalCount.incrementAndGet();
                        try {
                            OcrResult result = api.recognizeImage(pipeline, processOptions,
                                image.getWidth(), image.getHeight(), bgraData);
                            
                            if (result != null && !result.text().trim().isEmpty()) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            System.err.printf("  Thread %d, iteration %d failed: %s%n", tid, i+1, e.getMessage());
                        }
                    }
                    
                    processOptions.close();
                    pipeline.close();
                    initOptions.close();
                } catch (Exception e) {
                    System.err.printf("  Thread %d setup failed: %s%n", tid, e.getMessage());
                }
            });
        }
        
        try {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.printf("  Results: %d/%d successful (%.1f%%)%n", 
            successCount.get(), totalCount.get(), 
            (successCount.get() * 100.0) / totalCount.get());
    }
    
    /**
     * Test 3: Validate that BufferedImage data is not corrupted
     */
    private static void testImageValidation(BufferedImage image, byte[] bgraData) {
        System.out.println("\nTEST 3: Image Data Validation");
        
        System.out.printf("  BufferedImage: %dx%d, type=%d%n", 
            image.getWidth(), image.getHeight(), image.getType());
        System.out.printf("  BGRA data length: %d bytes (expected: %d)%n", 
            bgraData.length, image.getWidth() * image.getHeight() * 4);
        
        // Check if image has actual pixel data
        boolean hasNonZeroPixels = false;
        int sampleCount = 0;
        for (int y = 0; y < Math.min(10, image.getHeight()); y++) {
            for (int x = 0; x < Math.min(10, image.getWidth()); x++) {
                int rgb = image.getRGB(x, y);
                if (rgb != 0) {
                    hasNonZeroPixels = true;
                    sampleCount++;
                }
            }
        }
        
        System.out.printf("  Sample pixels (10x10): %d non-zero pixels%n", sampleCount);
        System.out.printf("  Image appears valid: %s%n", hasNonZeroPixels ? "YES" : "NO");
        
        // Check BGRA data
        int nonZeroBytes = 0;
        for (int i = 0; i < Math.min(1000, bgraData.length); i++) {
            if (bgraData[i] != 0) nonZeroBytes++;
        }
        System.out.printf("  BGRA sample (1000 bytes): %d non-zero bytes%n", nonZeroBytes);
    }
    
    private static byte[] convertToBGRA(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] bgraData = new byte[width * height * 4];
        
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                bgraData[index++] = (byte) (rgb & 0xFF);        // Blue
                bgraData[index++] = (byte) ((rgb >> 8) & 0xFF);  // Green  
                bgraData[index++] = (byte) ((rgb >> 16) & 0xFF); // Red
                bgraData[index++] = (byte) ((rgb >> 24) & 0xFF); // Alpha
            }
        }
        
        return bgraData;
    }
}