package xyz.jphil.win11_oneocr.tools.improve;

import java.util.*;

/**
 * Chooses which flagged bands to probe.
 *
 * Sampling breadth matters twice over. On a single page, three of ten flagged lines were guard
 * false positives that each voted a different wrong script, so probing one line can pick the
 * wrong language outright. Across a document it matters more: a 25-page order carried its only
 * Gujarati on pages 21-22, and a sampler that walked pages in order spent its whole budget on
 * pages 1-11 and concluded there was no foreign script at all. Samples are therefore spaced
 * evenly over every flagged band in the document, so coverage is proportional and reaches the end.
 */
public final class ProbeSampler {

    private ProbeSampler() {}

    public static List<int[]> sample(Map<Integer, List<LineBand>> flaggedByPage, int cap) {
        if (flaggedByPage.isEmpty() || cap <= 0) return List.of();

        var pages = new ArrayList<>(flaggedByPage.keySet());
        Collections.sort(pages);

        var flat = new ArrayList<int[]>();
        for (var pg : pages) {
            var n = flaggedByPage.get(pg).size();
            for (var i = 0; i < n; i++) flat.add(new int[]{pg, i});
        }
        if (flat.size() <= cap) return flat;

        var out = new ArrayList<int[]>(cap);
        var step = flat.size() / (double) cap;
        for (var k = 0; k < cap; k++) {
            out.add(flat.get(Math.min(flat.size() - 1, (int) Math.floor(k * step))));
        }
        return out;
    }
}
