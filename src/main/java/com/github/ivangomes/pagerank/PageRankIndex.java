package com.github.ivangomes.pagerank;

import lombok.ToString;

import java.util.*;

@ToString
public class PageRankIndex {
    private final Map<String, Set<String>> outgoingLinksMap = new HashMap<>();
    private final Map<String, Set<String>> incomingLinksMap = new HashMap<>();

    private final Map<String, Double> pageRanks = new HashMap<>();

    public void addPage(String id) {
        outgoingLinksMap.computeIfAbsent(id, k -> new HashSet<>());
        incomingLinksMap.computeIfAbsent(id, k -> new HashSet<>());
    }

    public void addLink(String sourceId, String targetId) {
        if (sourceId.equals(targetId)) {
            return;
        }
        outgoingLinksMap.computeIfAbsent(sourceId, k -> new HashSet<>()).add(targetId);
        incomingLinksMap.computeIfAbsent(targetId, k -> new HashSet<>()).add(sourceId);
    }

    public Map<String, Set<String>> getOutgoingLinksMap() {
        return Collections.unmodifiableMap(outgoingLinksMap);
    }

    public Map<String, Set<String>> getIncomingLinksMap() {
        return Collections.unmodifiableMap(incomingLinksMap);
    }

    public Map<String, Double> getPageRanks() {
        return pageRanks;
    }

    public int size() {
        return outgoingLinksMap.size();
    }
}
