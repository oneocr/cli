package oneocr.cli;

import static java.lang.Integer.parseInt;
import oneocr.api.*;
import org.json.*;
import java.util.*;
import static oneocr.api.OcrWord.ocrWord;

/**
 * Creates ultra-compact JSON output for OCR results using org.json
 Maintains ultra-compact bounds format while using proper JSON library
 */
public class CompactJsonSerializer {
    
    /**
     * Serialize OCR results to ultra-compact bounds-based JSON format
     * @param result OCR results
     * @param imageFile Source image path for metadata
     * @param imageWidth Image width in pixels
     * @param imageHeight Image height in pixels
     * @return Compact single-line JSON string
     */
    public static String toCompactJson(OcrResult result, String imageFile, int imageWidth, int imageHeight) {
        return toCompactJson(result, imageFile, imageWidth, imageHeight, OcrMetadata.ENGINE_ONEOCR);
    }

    public static String toCompactJson(OcrResult result, String imageFile, int imageWidth, int imageHeight, String engine) {
        var root = new JSONObject();

        // Create metadata record
        var metadata = OcrMetadata.create(imageFile, imageWidth, imageHeight, result, engine);

        var metrics = new JSONObject()
            .put("linesCount", metadata.metrics().linesCount())
            .put("wordsCount", metadata.metrics().wordsCount())
            .put("averageOcrConfidence", metadata.metrics().averageOcrConfidence())
            .put("highConfWordsRatio", metadata.metrics().highConfWordsRatio())
            .put("mediumConfWordsRatio", metadata.metrics().mediumConfWordsRatio())
            .put("lowConfWordsRatio", metadata.metrics().lowConfWordsRatio())
        ;
        
        // Metadata section (named properties)
        var meta = new JSONObject()
        .put("file", metadata.file())
        .put("imgSize", metadata.width()+"x"+metadata.height())
        .put("engine", metadata.engine())
        .put("timestampUTCISO", metadata.timestampUTCISO())
        // .put("plainText", result.text()) // Commented out to reduce file size - didn't improve AI comprehension
        .put("metrics", metrics);
        root.put("meta", meta);
        
        // Document-level text angle
        root.put("angle", formatNumber(result.textAngle()));
        
        // Ultra-compact linesCount bounds: each line is [bounds, [wordsCount...]]
        int totalIndx = 0;
        var linesArray = new JSONArray();
        for (var line : result.lines()) {
            var lineArray = new JSONArray();
            
            // Line bounding box (null if not available)
            if (line.boundingBox() != null) {
                lineArray.put(createBoundsArray(line.boundingBox()));
            } else {
                lineArray.put(JSONObject.NULL);
            }
            
            // Words bounds: [[#index,text,confidence,llmCorrection,bounds],...]
            var wordsArray = new JSONArray();
            for (var word : line.words()) {
                var wordArray = new JSONArray();
                wordArray.put("#"+totalIndx); totalIndx++;
                wordArray.put(word.text());
                wordArray.put(formatConfidence(word.confidence()));
                wordArray.put("");//empty llm corrected word
                if (word.boundingBox() != null) {
                    wordArray.put(createBoundsArray(word.boundingBox()));
                } else {
                    wordArray.put(JSONObject.NULL);
                }
                
                wordsArray.put(wordArray);
            }
            
            lineArray.put(wordsArray);
            linesArray.put(lineArray);
        }
        
        root.put("lines", linesArray);
        return root.toString();
    }
    
    private static JSONArray createBoundsArray(BoundingBox bbox) {
        var bounds = new JSONArray();
        var bnds = bbox.bounds();
        for (int i = 0; i < bnds.length; i++) {
            bounds.put(formatNumber(bnds[i]));   
        }
        return bounds;
    }
    
    /**
     * Smart number formatting: integer if no decimal part, double if has decimals
     */
    private static Number formatNumber(double value) {
        if (value == Math.floor(value)) {
            return (int) value; // Integer format
        } else {
            return Double.parseDouble(String.format("%.1f", value)); // Keep 1 decimal
        }
    }
    
    /**
     * Smart confidence formatting: 3 decimals max, remove trailing zeros
     */
    private static Number formatConfidence(double confidence) {
        var formatted = String.format("%.3f", confidence);
        // Remove trailing zeros but keep at least one decimal place
        formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", ".0");
        return Double.parseDouble(formatted);
    }
    
    /**
     * Deserialize JSON string back to OcrResult record
     */
    public static OcrJsonFile fromJson(String jsonString) {
        var root = new JSONObject(jsonString);
        
        // Parse metadata into OcrMetadata record
        var metaJson = root.getJSONObject("meta");
        var sizeStr = metaJson.getString("imgSize");
        int imgWidth = -1, imgHeight = -1;
        if(sizeStr!=null && !sizeStr.isBlank()){
            try {
                var a = sizeStr.split("x");
                imgWidth = parseInt(a[0]);
                imgHeight = parseInt(a[1]);
            } catch (Exception e) {
            }
        }
        var metrics = metaJson.getJSONObject("metrics");
        
        
        // Parse angle
        var angle = root.getDouble("angle");
        
        // Parse linesCount bounds
        var linesArray = root.getJSONArray("lines");
        var lines = new ArrayList<OcrLine>();
        
        for (int i = 0; i < linesArray.length(); i++) {
            var line_A = linesArray.getJSONArray(i);
            
            // Parse line bounds (first element, can be null)
            BoundingBox lineBounds = null;
            if (!line_A.isNull(0)) {
                var bnds = line_A.getJSONArray(0);
                lineBounds = new BoundingBox(
                    bnds.getDouble(0), bnds.getDouble(1),
                    bnds.getDouble(2), bnds.getDouble(3),
                    bnds.getDouble(4), bnds.getDouble(5),
                    bnds.getDouble(6), bnds.getDouble(7)
                );
            }
            
            // Parse wordsCount bounds (second element)
            var wordsArray = line_A.getJSONArray(1);
            var words = new ArrayList<OcrWord>();
            
            for (int j = 0; j < wordsArray.length(); j++) {
                var word_A = wordsArray.getJSONArray(j);
                //int totalIndx = extractWordIndex(word_A.getString(0)); // not used
                var text = word_A.getString(1);
                var confidence = word_A.getDouble(2);
                var llmCorrection = word_A.getString(3);
                // Parse word bounds (third element, can be null)
                BoundingBox wordBounds = null;
                if (!word_A.isNull(4)) {
                    var bnds = word_A.getJSONArray(4);
                    wordBounds = new BoundingBox(
                        bnds.getDouble(0), bnds.getDouble(1),
                        bnds.getDouble(2), bnds.getDouble(3),
                        bnds.getDouble(4), bnds.getDouble(5),
                        bnds.getDouble(6), bnds.getDouble(7)
                    );
                }
                
                words.add(ocrWord(text, wordBounds, confidence, llmCorrection));
            }
            
            // Reconstruct line text from wordsCount
            var lineText = words.stream()
                .map(OcrWord::text)
                .reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b);
            
            lines.add(new OcrLine(lineText, lineBounds, words));
        }
        
        // Reconstruct full text from all linesCount
        var fullText = lines.stream()
            .map(OcrLine::text)
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        var result = new OcrResult(fullText, angle, lines);
        
        // 'plainText' is deliberately not written out (it duplicates the reconstructed text and
        // bloats the file), so it must be read optionally - reading it as required threw on
        // every round-trip and made saved JSON unloadable.
        var metadata = new OcrMetadata(
            metaJson.optString("file", ""),
            imgWidth, imgHeight,
            metaJson.optString("timestampUTCISO", ""),
            metaJson.optString("plainText", result.text()),
            metaJson.optString("engine", OcrMetadata.ENGINE_ONEOCR),
            new OcrMetrics(result)
        );
        return new OcrJsonFile(metadata, result);
                
    }
    
    private static int extractWordIndex(String s){
        if(s==null || s.isBlank() || s.length()<2 || s.charAt(0)!='#'){
            return -1;
        }
        try {
            return Integer.parseInt(s.substring(1));
        } catch (Exception e) {
        }
        return -1;
    }

}