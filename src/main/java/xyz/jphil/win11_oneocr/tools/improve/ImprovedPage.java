package xyz.jphil.win11_oneocr.tools.improve;

import java.util.List;
import xyz.jphil.win11_oneocr.OcrResult;

public record ImprovedPage(int pageNo, OcrResult result, int bandsFlagged, int bandsReplaced, List<String> langsUsed) {

    public boolean changed() {
        return bandsReplaced > 0;
    }
}
