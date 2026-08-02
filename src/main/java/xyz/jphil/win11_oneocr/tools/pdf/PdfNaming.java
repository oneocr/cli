package xyz.jphil.win11_oneocr.tools.pdf;

public class PdfNaming {
    private final String name;
    //private final int total;
    private final String fmt;
    
    public PdfNaming(String pdfName, int totalPages) {
        this.name = pdfName;
        int total = totalPages;
        int digits = totalPages <= 0 ? 1 : Math.max(1, (int) Math.floor(Math.log10(totalPages)) + 1);
        this.fmt = String.format("%%0%dd", digits);
    }
    
    private String pad(int page) {
        return String.format(fmt, page);
    }
    
    public String page(int pageNum, String ext) {
        return String.format("%s.pg%s.%s", name, pad(pageNum), ext);
    }
    
    public String range(int start, int end, String ext) {
        return String.format("%s.pg[%s-%s].%s", name, pad(start), pad(end), ext);
    }
    
    public String combined(String ext) {
        return String.format("%s.oneocr.%s", name, ext);
    }
}