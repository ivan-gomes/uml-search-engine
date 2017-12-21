package com.github.ivangomes.pagerank;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Accessors(fluent = true, chain = true)
@Getter
@Setter
public class PageRankCalculator implements Function<PageRankIndex, Map<String, Double>> {
    private double dampingFactor = 0.85;

    @Override
    public Map<String, Double> apply(PageRankIndex pageRankIndex) {
        Map<String, Double> deltas = new HashMap<>(pageRankIndex.size());
        pageRankIndex.getIncomingLinksMap().forEach((targetId, sourceIds) -> {
            double initialPageRank = pageRankIndex.getPageRanks().computeIfAbsent(targetId, id -> 1d / pageRankIndex.getOutgoingLinksMap().size());
            double finalPageRank = (1 - dampingFactor) / pageRankIndex.size() + dampingFactor
                    * sourceIds.stream().mapToDouble(sourceId -> pageRankIndex.getPageRanks().computeIfAbsent(sourceId, id -> 1d / pageRankIndex.size()) / (double) pageRankIndex.getOutgoingLinksMap().get(sourceId).size()).sum();
            pageRankIndex.getPageRanks().put(targetId, finalPageRank);
            deltas.put(targetId, finalPageRank - initialPageRank);
        });
        return deltas;
    }
}
