package xyz.jphil.win11_oneocr.tools;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import xyz.jphil.win11_oneocr.*;
import luvx.*;
import static luvml.E.*;
import static luvml.A.*;
import static luvml.Frags.*;
import static luvml.ProcessingInstruction.xmlDeclaration;
import static xyz.jphil.win11_oneocr.tools.OcrDSL.*;
import luvml.DocType;
import luvml.E;

import java.util.*;
import luvml.element.*;
import luvml.o.XHtmlStringRenderer;
import static xyz.jphil.win11_oneocr.tools.PagedOcrData.*;

/**
 * Creates XHTML output for OCR results using luvml framework
 * Follows luvx/luvml DSL patterns for proper DOM tree construction
 */
public class OcrToSemanticXHtml {
    
    /**
     * Serialize OCR results to XHTML format using luvml DSL
     */
    public static String toXHtml(OcrResult result, String imageFile, int imageWidth, int imageHeight) {
        return toXHtml(result, imageFile, imageWidth, imageHeight, OcrMetadata.ENGINE_ONEOCR);
    }

    public static String toXHtml(OcrResult result, String imageFile, int imageWidth, int imageHeight, String engineName) {
        // Create metadata record
        var metadata = OcrMetadata.create(imageFile, imageWidth, imageHeight, result, engineName);

        // Build OCR content fragments using DSL
        var segments = frags();
        
        // Process segmentsCount and wordsCount
        int totalIndex = 0;
        int segmentIndex = 1;
        for (var ocrLine : result.lines()) {
            // Create wordsCount for this segment using DSL
            var words = ocrLine.words();
            var words_n_sp = new ArrayList<Frag_I>();
            for (int i = 0; i < words.size(); i++) {
                var word = words.get(i);
                words_n_sp.add(
                    // Create word element: <w i="#23" p="0.487" b="bounds">Tulai</w>
                    w(
                        i("#" + totalIndex++),
                        p(formatConfidence(word.confidence())),
                        if_(word.boundingBox() != null,()->
                            b(formatBounds(word.boundingBox()))),
                        text(word.text())
                    )
                );
                if(i!=words.size()-1)
                    // add space padding
                    words_n_sp.add(text(" "));
            }
            
            // Create segment element containing wordsCount
            segments.____(
                segment(
                    num(segmentIndex++),
                    if_(ocrLine.boundingBox() != null,()->
                        b(formatBounds(ocrLine.boundingBox()))),
                    frags(words_n_sp)
                )//,
                //br() // Add semantic line break after each segment for proper browser rendering
                //newline()// removed newline because made segment block
            );
            
            
        }
        
        // Create root OCR element with metadata attributes using DSL
        var ocrSection = section(
            class_("win11OneOcrPage"),
            srcName(metadata.file()),
            imgWidth(metadata.width()),imgHeight(metadata.height()),
            engine(metadata.engine()),
            timestamp(metadata.timestampUTCISO()),
            angle(formatNumber(result.textAngle())),
            ocrSegmentsCount(String.valueOf(metadata.metrics().linesCount())),
            ocrWordsCount(String.valueOf(metadata.metrics().wordsCount())),
            averageOcrConfidence(formatConfidence(metadata.metrics().averageOcrConfidence())),
            highConfWordsRatio(formatConfidence(metadata.metrics().highConfWordsRatio())),
            mediumConfWordsRatio(formatConfidence(metadata.metrics().mediumConfWordsRatio())),
            lowConfWordsRatio(formatConfidence(metadata.metrics().lowConfWordsRatio())),
            div(class_("ocrContent"),frags(segments))
        );
        
        // Build complete XHTML document using luvml DSL (like the example!)
        var xhtmlDoc = frags(
            xmlDeclaration("UTF-8"),
            DocType.html5(),
            html(xmlns("http://www.w3.org/1999/xhtml"),
                head(
                    meta(charset("UTF-8")),
                    meta(name("viewport"),content("width=device-width, initial-scale=1.0")),
                    meta(name("description"),content(OcrSemanticXHtml5.DEFINITION)),
                    meta(name("date"),content(LocalDateTime.now().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toString())),
                    E.title(imageFile+" ("+OcrSemanticXHtml5.TYPE+ ")"),
                    link(rel("stylesheet"), href(OcrSemanticXHtml5.CSS)),
                    script(src(OcrSemanticXHtml5.JS))
                ),
                body(
                    E.style("segment {display: block;}"),
                    ocrSection
                )
            )
        );
        
        // Render using CustomXHtmlRenderer
        return XHtmlStringRenderer.asFormatted(xhtmlDoc,"");//no tabs
    }
    
    private static String formatBounds(BoundingBox bbox) {
        var bounds = bbox.bounds();
        var sb = new StringBuilder();
        for (int idx = 0; idx < bounds.length; idx++) {
            if (idx > 0) sb.append(',');
            sb.append(formatNumber(bounds[idx]));
        }
        return sb.toString();
    }
    
    /**
     * Smart number formatting: integer if no decimal part, double if has decimals
     */
    private static String formatNumber(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((int) value);
        } else {
            return String.format("%.1f", value);
        }
    }
    
    /**
     * Smart confidence formatting: 3 decimals max, remove trailing zeros
     */
    private static String formatConfidence(double confidence) {
        var formatted = String.format("%.3f", confidence);
        // Remove trailing zeros but keep at least one decimal place
        formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", ".0");
        return formatted;
    }
    
    /**
     * Combine multiple OCR results from different pages into a single XHTML document
     */
    public static String combineMultipleResults(List<PagedOcrResult> pagedResults, String documentName) {
        if (pagedResults.isEmpty()) {
            return "";
        }
        
        // Create metadata based on combined results
        var firstResult = pagedResults.get(0);
        var combinedMetadata = OcrMetadata.create(documentName, 
            firstResult.imageWidth(), firstResult.imageHeight(), firstResult.ocrResult());
        
        // Calculate combined metrics
        int totalWords = pagedResults.stream()
            .mapToInt(r -> r.ocrResult().lines().stream().mapToInt(l -> l.words().size()).sum())
            .sum();
        int totalSegments = pagedResults.stream()
            .mapToInt(r -> r.ocrResult().lines().size())
            .sum();
        double avgConfidence = pagedResults.stream()
            .flatMap(r -> r.ocrResult().lines().stream())
            .flatMap(l -> l.words().stream())
            .mapToDouble(OcrWord::confidence)
            .average()
            .orElse(0.0);
        
        // Build sections for each page
        var allPageSections = new ArrayList<Frag_I>();
        int globalWordIndex = 0;
        
        for (var pagedResult : pagedResults) {
            // Build segments for this page
            var pageSegments = new ArrayList<Frag_I>();
            int segmentIndex = 1;
            
            for (var ocrLine : pagedResult.ocrResult().lines()) {
                var words = ocrLine.words();
                var words_n_sp = new ArrayList<Frag_I>();
                
                for (int i = 0; i < words.size(); i++) {
                    var word = words.get(i);
                    words_n_sp.add(
                        w(
                            i("#" + globalWordIndex++),
                            p(formatConfidence(word.confidence())),
                            if_(word.boundingBox() != null, () ->
                                b(formatBounds(word.boundingBox()))),
                            text(word.text())
                        )
                    );
                    if (i != words.size() - 1) {
                        words_n_sp.add(text(" "));
                    }
                }
                
                // Create segment element for this page
                pageSegments.add(
                    segment(
                        num(segmentIndex++),
                        if_(ocrLine.boundingBox() != null, () ->
                            b(formatBounds(ocrLine.boundingBox()))),
                        frags(words_n_sp)
                    )
                );
            }
            
            // Create page section with its own metadata
            var pageWordsCount = pagedResult.ocrResult().lines().stream()
                .mapToInt(l -> l.words().size()).sum();
            var pageSegmentsCount = pagedResult.ocrResult().lines().size();
            var pageAvgConfidence = pagedResult.ocrResult().lines().stream()
                .flatMap(l -> l.words().stream())
                .mapToDouble(OcrWord::confidence)
                .average()
                .orElse(0.0);
            
            allPageSections.add(
                section(
                    class_("win11OneOcrPage"),
                    srcName(pagedResult.imageName()),
                    imgWidth(pagedResult.imageWidth()), imgHeight(pagedResult.imageHeight()),
                    angle(formatNumber(pagedResult.ocrResult().textAngle())),
                    ocrSegmentsCount(String.valueOf(pageSegmentsCount)),
                    ocrWordsCount(String.valueOf(pageWordsCount)),
                    averageOcrConfidence(formatConfidence(pageAvgConfidence)),
                    pageNum(String.valueOf(pagedResult.pageNumber())),
                    div(class_("ocrContent"), frags(pageSegments))
                )
            );
        }
        
        // Build complete XHTML document
        var xhtmlDoc = frags(
            xmlDeclaration("UTF-8"),
            DocType.html5(),
            html(xmlns("http://www.w3.org/1999/xhtml"),
                head(
                    meta(charset("UTF-8")),
                    meta(name("viewport"), content("width=device-width, initial-scale=1.0")),
                    meta(name("description"), content(OcrSemanticXHtml5.DEFINITION)),
                    meta(name("date"), content(LocalDateTime.now().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toString())),
                    E.title(documentName + " (" + OcrSemanticXHtml5.TYPE + ")"),
                    link(rel("stylesheet"), href(OcrSemanticXHtml5.CSS)),
                    script(src(OcrSemanticXHtml5.JS)),
                    // Add document-level metadata
                    meta(name("pagesCount"), content(String.valueOf(pagedResults.size()))),
                    meta(name("totalWords"), content(String.valueOf(totalWords))),
                    meta(name("totalSegments"), content(String.valueOf(totalSegments))),
                    meta(name("averageConfidence"), content(formatConfidence(avgConfidence)))
                ),
                body(
                    // this is a fallback style (if viewing offline)
                    // main styling is in the css file
                    E.style("segment {display: block;}"),
                    frags(allPageSections)
                )
            )
        );
        
        return XHtmlStringRenderer.asFormatted(xhtmlDoc, "");
    }


    /**
     * Deserialize XHTML string back to OcrResult record
     */
    public static OcrJsonFile fromXHtml(String xhtmlString) {
        // TODO: Implement XML parsing to reconstruct OcrResult
        throw new UnsupportedOperationException("XHTML deserialization not yet implemented");
    }
    
    // Custom OCR element classes using existing base classes (no reinventing!)
    
    /**
     * OCR root element
     */
    public static class Ocr_E extends SemanticBlockContainerElement<Ocr_E>   implements SemanticElementTagNameClassNameMapping.CamelCase_E {
        public Ocr_E(Frag_I<?>... fragments) {
            super(Ocr_E.class);
            ____(fragments);
        }
    }
    
    /**
     * Segment element (detected text segment: lines/cells/blocks)
     */
    public static class Segment_E extends SemanticBlockContainerElement<Segment_E> implements SemanticElementTagNameClassNameMapping.CamelCase_E {
        public Segment_E(Frag_I<?>... fragments) {
            super(Segment_E.class);
            ____(fragments);
        }
    }
    
    /**
     * Word element
     */
    public static class W_E extends SemanticInlineContainerElement<W_E> implements SemanticElementTagNameClassNameMapping.CamelCase_E  {
        public W_E(Frag_I<?>... fragments) {
            super(W_E.class);
            ____(fragments);
        }
    }
}