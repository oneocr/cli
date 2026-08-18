package oneocr.cli;

import oneocr.api.OcrResult;

public record OcrJsonFile(OcrMetadata metadata, OcrResult data) {
    
}
