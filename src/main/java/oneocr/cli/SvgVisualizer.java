package oneocr.cli;

import java.nio.file.Path;
import oneocr.api.BoundingBox;
import oneocr.api.OcrLine;
import oneocr.api.OcrResult;
import oneocr.api.OcrWord;

/**
 * Creates SVG visualization of OCR results with coordinate overlays
 * Fixed version with proper SVG structure and coordinate handling
 */
public class SvgVisualizer {
    
    /**
     * Create SVG visualization with OCR bounding boxes overlaid on the image
     * @param result OCR results with text and coordinates
     * @param imagePath Path to the source image (for reference)
     * @param imageWidth Image width in pixels
     * @param imageHeight Image height in pixels
     * @return SVG markup as string
     */
    public static String createSvgVisualization(OcrResult result, Path imagePath, int imageWidth, int imageHeight) {
        try {
            StringBuilder svg = new StringBuilder();
            svg.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ");
            svg.append("xmlns:xlink=\"http://www.w3.org/1999/xlink\" ");
            svg.append("width=\"").append(imageWidth).append("\" ");
            svg.append("height=\"").append(imageHeight).append("\" ");
            svg.append("viewBox=\"0 0 ").append(imageWidth).append(" ").append(imageHeight).append("\">\n");
            
            // Style definitions and JavaScript for interactive controls
            svg.append("  <defs>\n");
            svg.append("    <style><![CDATA[\n");
            svg.append("      .line-box { fill: none; stroke: #000000; stroke-width: 0.8; stroke-dasharray: 4,2; }\n");
            svg.append("      .word-box-high { fill: none; stroke: #00aa00; stroke-width: 0.6; stroke-dasharray: 2,1; }\n");
            svg.append("      .word-box-med { fill: none; stroke: #ffaa00; stroke-width: 0.6; stroke-dasharray: 2,1; }\n");
            svg.append("      .word-box-low { fill: none; stroke: #ff0000; stroke-width: 0.6; stroke-dasharray: 2,1; }\n");
            svg.append("      .word-text { font-family: Arial, sans-serif; font-size: 12px; fill: #0066cc; font-weight: bold; }\n");
            svg.append("      .metadata { font-family: Arial, sans-serif; font-size: 14px; fill: white; }\n");
            svg.append("      .metadata-bg { fill: rgba(0,0,0,0.7); }\n");
            svg.append("      .control-panel { font-family: Arial, sans-serif; font-size: 12px; fill: black; }\n");
            svg.append("      .control-bg { fill: rgba(255,255,255,0.9); stroke: #ccc; stroke-width: 1; }\n");
            svg.append("      .checkbox { fill: white; stroke: #666; stroke-width: 1; cursor: pointer; }\n");
            svg.append("      .checkbox.checked { fill: #4CAF50; }\n");
            svg.append("      .hidden { display: none; }\n");
            svg.append("      #control-panel { opacity: 0.1; transition: opacity 0.3s ease; }\n");
            svg.append("      #control-panel:hover { opacity: 1; }\n");
            svg.append("      .hover-hint { font-size: 9px; fill: #999; opacity: 0.7; }\n");
            svg.append("    ]]></style>\n");
            svg.append("    <script><![CDATA[\n");
            svg.append("      function toggleLayer(layerId, checkboxId) {\n");
            svg.append("        var layer = document.getElementById(layerId);\n");
            svg.append("        var checkbox = document.getElementById(checkboxId);\n");
            svg.append("        if (layer.style.display === 'none') {\n");
            svg.append("          layer.style.display = '';\n");
            svg.append("          checkbox.classList.add('checked');\n");
            svg.append("        } else {\n");
            svg.append("          layer.style.display = 'none';\n");
            svg.append("          checkbox.classList.remove('checked');\n");
            svg.append("        }\n");
            svg.append("      }\n");
            svg.append("    ]]></script>\n");
            svg.append("  </defs>\n");
            
            // Background image layer
            svg.append("  <g id=\"background-image\">\n");
            String imageFileName = imagePath.getFileName().toString();
            svg.append("    <image x=\"0\" y=\"0\" width=\"").append(imageWidth).append("\" height=\"").append(imageHeight).append("\" ");
            svg.append("href=\"").append(imageFileName).append("\" preserveAspectRatio=\"none\" />\n");
            svg.append("  </g>\n");
            
            // Line boxes layer
            svg.append("  <g id=\"line-boxes\">\n");
            for (int lineIdx = 0; lineIdx < result.lines().size(); lineIdx++) {
                OcrLine line = result.lines().get(lineIdx);
                if (line.boundingBox() != null) {
                    svg.append("  ").append(createBoundingBoxPolygon(line.boundingBox(), "line-box", "line-" + lineIdx));
                }
            }
            svg.append("  </g>\n");
            
            // Word boxes layer
            svg.append("  <g id=\"word-boxes\">\n");
            for (int lineIdx = 0; lineIdx < result.lines().size(); lineIdx++) {
                OcrLine line = result.lines().get(lineIdx);
                for (int wordIdx = 0; wordIdx < line.words().size(); wordIdx++) {
                    OcrWord word = line.words().get(wordIdx);
                    if (word.boundingBox() != null) {
                        BoundingBox bbox = word.boundingBox();
                        String wordId = "word-" + lineIdx + "-" + wordIdx;
                        double confidence = word.confidence();
                        String boxClass = getConfidenceStyle(confidence);
                        svg.append("  ").append(createBoundingBoxPolygon(bbox, boxClass, wordId));
                    }
                }
            }
            svg.append("  </g>\n");
            
            // Text layer
            svg.append("  <g id=\"text-layer\">\n");
            for (int lineIdx = 0; lineIdx < result.lines().size(); lineIdx++) {
                OcrLine line = result.lines().get(lineIdx);
                for (int wordIdx = 0; wordIdx < line.words().size(); wordIdx++) {
                    OcrWord word = line.words().get(wordIdx);
                    if (word.boundingBox() != null) {
                        BoundingBox bbox = word.boundingBox();
                        double angle = calculateBoundingBoxAngle(bbox);
                        double confidence = word.confidence();
                        
                        // Calculate proportional font size based on bounding box height
                        double boxHeight = Math.abs(bbox.y3() - bbox.y1());
                        double fontSize = Math.max(8, Math.min(boxHeight * 0.7, 24));
                        
                        // Calculate text position (centered in bounding box)
                        double textX = Math.min(bbox.x1(), bbox.x4()) + 2;
                        double textY = Math.min(bbox.y1(), bbox.y2()) + (boxHeight * 0.75);
                        
                        // Calculate center of bounding box for rotation origin
                        double centerX = (bbox.x1() + bbox.x2() + bbox.x3() + bbox.x4()) / 4.0;
                        double centerY = (bbox.y1() + bbox.y2() + bbox.y3() + bbox.y4()) / 4.0;
                        
                        // Word text overlay with rotation transform and proportional font size
                        svg.append(String.format("    <text x=\"%.1f\" y=\"%.1f\" class=\"word-text\" style=\"font-size: %.1fpx;\" transform=\"rotate(%.1f %.1f %.1f)\" title=\"Confidence: %.1f%%\">%s</text>\n",
                            textX, textY, fontSize, angle, centerX, centerY, confidence * 100, escapeXml(word.text())));
                    }
                }
            }
            svg.append("  </g>\n");
            
            // Interactive control panel - horizontal layout with hover reveal
            svg.append("  <g id=\"control-panel\">\n");
            svg.append("    <rect x=\"5\" y=\"5\" width=\"").append(imageWidth - 10).append("\" height=\"25\" class=\"control-bg\" rx=\"3\" />\n");
            
            // Hover hint (visible when faded)
            svg.append("    <text x=\"15\" y=\"18\" class=\"hover-hint\">Hover for controls...</text>\n");
            
            // Background image control
            svg.append("    <rect x=\"10\" y=\"12\" width=\"10\" height=\"10\" class=\"checkbox checked\" id=\"cb-background-image\" onclick=\"toggleLayer('background-image', 'cb-background-image')\" />\n");
            svg.append("    <text x=\"25\" y=\"20\" class=\"control-panel\" style=\"font-size: 11px;\">Image</text>\n");
            
            // Line boxes control
            svg.append("    <rect x=\"70\" y=\"12\" width=\"10\" height=\"10\" class=\"checkbox checked\" id=\"cb-line-boxes\" onclick=\"toggleLayer('line-boxes', 'cb-line-boxes')\" />\n");
            svg.append("    <text x=\"85\" y=\"20\" class=\"control-panel\" style=\"font-size: 11px;\">Lines</text>\n");
            
            // Word boxes control  
            svg.append("    <rect x=\"130\" y=\"12\" width=\"10\" height=\"10\" class=\"checkbox checked\" id=\"cb-word-boxes\" onclick=\"toggleLayer('word-boxes', 'cb-word-boxes')\" />\n");
            svg.append("    <text x=\"145\" y=\"20\" class=\"control-panel\" style=\"font-size: 11px;\">Words</text>\n");
            
            // Text control
            svg.append("    <rect x=\"190\" y=\"12\" width=\"10\" height=\"10\" class=\"checkbox checked\" id=\"cb-text-layer\" onclick=\"toggleLayer('text-layer', 'cb-text-layer')\" />\n");
            svg.append("    <text x=\"205\" y=\"20\" class=\"control-panel\" style=\"font-size: 11px;\">Text</text>\n");
            
            // Metadata info - right aligned
            int metadataX = imageWidth - 150;
            svg.append("    <text x=\"").append(metadataX).append("\" y=\"20\" class=\"control-panel\" style=\"font-size: 10px; fill: #666;\">");
            svg.append(result.lines().size()).append(" lines, ");
            svg.append(result.lines().stream().mapToInt(l -> l.words().size()).sum()).append(" words");
            if (result.textAngle() != 0) {
                svg.append(", ∠").append(String.format("%.1f°", result.textAngle()));
            }
            svg.append("</text>\n");
            svg.append("  </g>\n");
            
            svg.append("</svg>");
            return svg.toString();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SVG visualization for: " + imagePath, e);
        }
    }
    
    /**
     * Create a polygon element for a bounding box with proper coordinate handling
     */
    private static String createBoundingBoxPolygon(BoundingBox bbox, String cssClass, String id) {
        // Ensure coordinates are valid and create closed polygon
        return String.format("  <polygon id=\"%s\" points=\"%.1f,%.1f %.1f,%.1f %.1f,%.1f %.1f,%.1f\" class=\"%s\" />\n",
            id,
            bbox.x1(), bbox.y1(), 
            bbox.x2(), bbox.y2(), 
            bbox.x3(), bbox.y3(), 
            bbox.x4(), bbox.y4(), 
            cssClass);
    }
    
    /**
     * Calculate the rotation angle of a bounding box in degrees
     * Uses the vector from point 1 to point 2 to determine text orientation
     */
    private static double calculateBoundingBoxAngle(BoundingBox bbox) {
        // Calculate vector from first corner to second corner (baseline direction)
        double deltaX = bbox.x2() - bbox.x1();
        double deltaY = bbox.y2() - bbox.y1();
        
        // Calculate angle in radians, then convert to degrees
        double angleRadians = Math.atan2(deltaY, deltaX);
        double angleDegrees = Math.toDegrees(angleRadians);
        
        // Normalize to 0-360 range
        if (angleDegrees < 0) {
            angleDegrees += 360;
        }
        
        return angleDegrees;
    }
    
    /**
     * Get CSS class name based on confidence level
     * High confidence (80%+): Green
     * Medium confidence (50-80%): Yellow  
     * Low confidence (<50%): Red
     */
    private static String getConfidenceStyle(double confidence) {
        if (confidence >= 0.8) {
            return "word-box-high";
        } else if (confidence >= 0.5) {
            return "word-box-med";
        } else {
            return "word-box-low";
        }
    }
    
    
    /**
     * Escape special XML characters to prevent malformed SVG
     */
    private static String escapeXml(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;")
                  .replace("\n", " ")  // Convert newlines to spaces
                  .replace("\r", " ")  // Convert carriage returns to spaces
                  .replace("\t", " "); // Convert tabs to spaces
    }
}