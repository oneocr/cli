package xyz.jphil.win11_oneocr.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import xyz.jphil.win11_oneocr.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Standard test that generates all essential output formats for ocr-book.jpg
 * This test serves as both validation and reference output generation
 */
public class StandardOutputTest {
    
    private static final String TEST_IMAGE = "ocr-book.jpg";
    private Path testImagePath;
    private Path outputDir;
    
    @BeforeEach
    void setup() throws Exception {
        // Use test image from resources or current directory
        testImagePath = Paths.get(TEST_IMAGE);
        if (!Files.exists(testImagePath)) {
            // Try resources
            var resource = getClass().getClassLoader().getResource(TEST_IMAGE);
            if (resource != null) {
                testImagePath = Paths.get(resource.toURI());
            } else {
                // Skip test if image not found
                org.junit.jupiter.api.Assumptions.assumeTrue(false, 
                    "Test image " + TEST_IMAGE + " not found");
            }
        }
        
        // Create output directory
        outputDir = Paths.get(".");//earlier - target/test-outputs
        Files.createDirectories(outputDir);
    }
    
    @Test
    void generateStandardOutputs() throws Exception {
        // for pretty print of unicode characters
        xyz.jphil.windows_console_set_unicode_output.WindowsConsoleSetUnicodeOutput.enable();
        System.out.println("=== Standard OCR Output Generation Test ===");
        System.out.println("Image: " + testImagePath.toAbsolutePath());
        
        // Load and process image
        BufferedImage image = ImageIO.read(testImagePath.toFile());
        byte[] bgraData = convertToBGRA(image);
        
        System.out.printf("Image loaded: %dx%d pixels%n", image.getWidth(), image.getHeight());
        
        // Run OCR
        OcrResult result;
        try (var api = new OneOcrApi()) {
            var initOptions = api.createInitOptions();
            var pipeline = api.createPipeline(initOptions);
            var processOptions = api.createProcessOptions();
            
            result = api.recognizeImage(pipeline, processOptions, 
                image.getWidth(), image.getHeight(), bgraData);
            
            processOptions.close();
            pipeline.close();
            initOptions.close();
        }
        
        System.out.printf("OCR completed: %d lines, %d words found%n", 
            result.lines().size(),
            result.lines().stream().mapToInt(l -> l.words().size()).sum());
        
        // Generate all standard outputs using default naming
        generateStandardOutputsWithDefaults(result, image.getWidth(), image.getHeight());
        
        System.out.println("=== Standard outputs generated successfully ===");
        
        // Basic validation
        assertTrue(result.lines().size() > 0, "Should find some text lines");
        assertTrue(result.lines().stream().anyMatch(l -> !l.words().isEmpty()), 
            "Should find some words");
    }
    
    private void generateStandardOutputsWithDefaults(OcrResult result, int width, int height) throws Exception {
        // Generate default file names following the pattern: input.ext.oneocr.{type}
        String baseName = testImagePath.getFileName().toString();
        
        // Compact JSON with default naming
        String compactJson = CompactJsonSerializer.toCompactJson(result, testImagePath.getFileName().toString(), width, height);
        Path jsonPath = outputDir.resolve(baseName + ".oneocr.json");
        Files.writeString(jsonPath, compactJson);
        System.out.printf("✓ Compact JSON: %s (%d bytes)%n", 
            jsonPath.getFileName(), compactJson.length());
        
        // SVG visualization with default naming
        String svg = SvgVisualizer.createSvgVisualization(result, testImagePath, width, height);
        Path svgPath = outputDir.resolve(baseName + ".oneocr.svg");
        Files.writeString(svgPath, svg);
        System.out.printf("✓ SVG visualization: %s (%d bytes)%n", 
            svgPath.getFileName(), svg.length());
        
        // Plain text with default naming
        String text = result.text();
        Path textPath = outputDir.resolve(baseName + ".oneocr.txt");
        Files.writeString(textPath, text);
        System.out.printf("✓ Plain text: %s (%d chars)%n", 
            textPath.getFileName(), text.length());
        
        // Semantic XHTML with default naming
        String xhtml = OcrToSemanticXHtml.toXHtml(result, testImagePath.getFileName().toString(), width, height);
        Path xhtmlPath = outputDir.resolve(baseName + ".oneocr.xhtml");
        Files.writeString(xhtmlPath, xhtml);
        System.out.printf("✓ Semantic XHTML: %s (%d bytes)%n", 
            xhtmlPath.getFileName(), xhtml.length());
    }
    
    private byte[] convertToBGRA(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] bgraData = new byte[width * height * 4];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int offset = (y * width + x) * 4;
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                bgraData[offset] = (byte) blue;
                bgraData[offset + 1] = (byte) green;
                bgraData[offset + 2] = (byte) red;
                bgraData[offset + 3] = (byte) alpha;
            }
        }
        return bgraData;
    }
}