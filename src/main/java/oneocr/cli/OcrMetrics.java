package oneocr.cli;

import oneocr.api.OcrResult;

public class OcrMetrics {
    final OcrResult r;

    public OcrMetrics(OcrResult result) {
        this.r = result;
    }
    
    public int wordsCount(){
        return r.wordsCount();
    }
    
    public int linesCount(){
        return r.linesCount();
    }
    
    public double averageOcrConfidence(){
        double total = 0; double cnt = 0;
        if(r.lines()==null)return 0;
        for (var line : r.lines()) {
            if(line==null)continue;
            var words = line.words();
            if(words==null)continue;
            for(var word : words){
                total += word.confidence();
                cnt++;
            }
        }
        return total/cnt;
    }
    
    public double highConfWordsRatio(){
        return wordsPercentageWithinConfRange(.7, 1);
    }
    
    public double mediumConfWordsRatio(){
        return wordsPercentageWithinConfRange(.6, .7);
    }
    
    public double lowConfWordsRatio(){
        return wordsPercentageWithinConfRange(0, .6);
    }

    public double wordsPercentageWithinConfRange(double above, double below){
        return wordsWithinConfRange(above, below)/wordsCount();
    }
            
    
    public double wordsWithinConfRange(double above, double below){
        double cnt = 0;
        if(r.lines()==null)return 0;
        for (var line : r.lines()) {
            if(line==null)continue;
            var words = line.words();
            if(words==null)continue;
            for(var word : words){
                if(word.confidence() >= above && word.confidence() < below)
                    cnt++;
            }
        }
        return cnt;
    }
    
}
