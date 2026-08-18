package xyz.jphil.win11_oneocr.tools.layout;

import java.util.List;
import xyz.jphil.win11_oneocr.BoundingBox;

public record Rect(double x, double y, double w, double h) {

    public double right()  { return x + w; }
    public double bottom() { return y + h; }
    public double cx()     { return x + w / 2; }
    public double cy()     { return y + h / 2; }

    public Rect union(Rect o) {
        var nx = Math.min(x, o.x);
        var ny = Math.min(y, o.y);
        return new Rect(nx, ny, Math.max(right(), o.right()) - nx, Math.max(bottom(), o.bottom()) - ny);
    }

    public double vOverlap(Rect o) { return Math.max(0, Math.min(bottom(), o.bottom()) - Math.max(y, o.y)); }
    public double hOverlap(Rect o) { return Math.max(0, Math.min(right(), o.right()) - Math.max(x, o.x)); }

    public static Rect of(BoundingBox b) {
        var r = b.getAxisAlignedBounds();
        return new Rect(r.x(), r.y(), r.width(), r.height());
    }

    public static Rect unionOf(List<Rect> rects) {
        var it = rects.iterator();
        if (!it.hasNext()) return new Rect(0, 0, 0, 0);
        var acc = it.next();
        while (it.hasNext()) acc = acc.union(it.next());
        return acc;
    }
}
