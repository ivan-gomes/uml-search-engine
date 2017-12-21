package com.github.ivangomes.elementrank.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.PathConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ivangomes.elasticsearch.ElasticsearchConfig;
import com.github.ivangomes.elasticsearch.ElasticsearchSearcher;
import com.github.ivangomes.elementrank.ElementRankIndex;
import com.github.ivangomes.elementrank.ElementRankIndexBuilder;
import com.github.ivangomes.pagerank.PageRankCalculator;
import lombok.SneakyThrows;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

public class QueryIndices implements Runnable {
    @Parameter(names = {"--dir", "-d"}, required = true, converter = PathConverter.class)
    private Path directory;
    @Parameter(names = {"--query", "-q"})
    private List<String> queries = new ArrayList<>();

    @SneakyThrows(IOException.class)
    @Override
    public void run() {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Directory argument must be a directory.");
        }
        final ElementRankIndex index;
        ObjectMapper mapper = new ObjectMapper();
        Path pageRankIndexPath = directory.resolve("elementrank_index.json");
        try (TransportClient client = ElasticsearchConfig.getClient()) {
            if (!Files.exists(pageRankIndexPath)) {
                System.out.println("[INFO] No cached element rank index found. Building a new one.");
                index = new ElementRankIndexBuilder(client, ElasticsearchConfig.INDEX, ElasticsearchConfig.TYPE).scrollKeepAlive(ElasticsearchConfig.DEFAULT_SCROLL_KEEP_ALIVE).size(ElasticsearchConfig.DEFAULT_SCROLL_SIZE).undirectedGraph(true).get();
                mapper.writerWithDefaultPrettyPrinter().writeValue(pageRankIndexPath.toFile(), index);
            } else {
                System.out.println("[INFO] Using cached element rank index.");
                index = mapper.readValue(pageRankIndexPath.toFile(), ElementRankIndex.class);
            }
            PageRankCalculator calculator = new PageRankCalculator();
            Map<String, Double> deltas;
            do {
                System.out.println("[INFO] Running PageRank calculations.");
                deltas = calculator.apply(index);
            }
            while (deltas.entrySet().stream().anyMatch(entry -> entry.getValue() / index.getPageRanks().get(entry.getKey()) > 0.001));
            mapper.writerWithDefaultPrettyPrinter().writeValue(pageRankIndexPath.toFile(), index);

            InputStream testTemplateInputStream = QueryIndices.class.getResourceAsStream("/test.template.groovy");
            String testTemplate = testTemplateInputStream != null ? new Scanner(testTemplateInputStream, "UTF-8").useDelimiter("\\A").next() : null;

            queries.forEach(query -> {
                SearchHits searchHits = new ElasticsearchSearcher(client, ElasticsearchConfig.INDEX, ElasticsearchConfig.TYPE, query).scrollKeepAlive(ElasticsearchConfig.DEFAULT_SCROLL_KEEP_ALIVE).size(ElasticsearchConfig.DEFAULT_SCROLL_SIZE).get();
                double maxErScore = Arrays.stream(searchHits.getHits()).mapToDouble(hit -> index.getPageRanks().get(hit.getId())).max().orElse(0);

                Map<String, Map<String, Object>> esHitsMap = Arrays.stream(searchHits.getHits()).collect(Collectors.toMap(SearchHit::getId, hit -> {
                    Map<String, Object> map = new LinkedHashMap<>(hit.getSourceAsMap());
                    double esScore = Float.valueOf(hit.getScore()).doubleValue();
                    double esScoreNormalized = esScore / searchHits.getMaxScore();
                    map.put("_esScore", esScore);
                    map.put("_esScoreNormalized", esScoreNormalized);
                    double erScore = index.getPageRanks().get(hit.getId());
                    double erScoreNormalized = erScore / maxErScore;
                    map.put("_erScore", erScore);
                    map.put("_erScoreNormalized", erScoreNormalized);
                    map.put("_combinedScoreNormalized", Math.pow(esScoreNormalized * erScoreNormalized, 0.5));
                    return map;
                }, (u, v) -> v, LinkedHashMap::new));

                Map<String, Map<String, Object>> erHitsMap = esHitsMap.entrySet().stream().sorted(Comparator.comparingDouble((Map.Entry<String, Map<String, Object>> entry) -> (Double) entry.getValue().get("_erScore")).reversed().thenComparing(Comparator.comparingDouble((Map.Entry<String, Map<String, Object>> entry) -> (Double) entry.getValue().get("_esScore")).reversed())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> v, LinkedHashMap::new));
                Map<String, Map<String, Object>> combinedHitsMap = esHitsMap.entrySet().stream().sorted(Comparator.comparingDouble((Map.Entry<String, Map<String, Object>> entry) -> (Double) entry.getValue().get("_combinedScoreNormalized")).reversed()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (u, v) -> v, LinkedHashMap::new));
                Map<String, Object> hitsJsonMap = new LinkedHashMap<>();
                hitsJsonMap.put("es", esHitsMap);
                hitsJsonMap.put("er", erHitsMap);
                hitsJsonMap.put("combined", combinedHitsMap);
                Path hitsPath = directory.resolve("hits_" + query.replace(' ', '_').replaceAll("[^\\w]", "") + ".json");
                try {
                    mapper.writerWithDefaultPrettyPrinter().writeValue(hitsPath.toFile(), hitsJsonMap);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                Path testPath = directory.resolve("test_" + query.replace(' ', '_').replaceAll("[^\\w]", "") + ".groovy");
                if (testTemplate != null) {
                    Set<List<String>> set = new HashSet<>();
                    set.add(esHitsMap.keySet().stream().sequential().limit(100).collect(Collectors.toList()));
                    set.add(erHitsMap.keySet().stream().sequential().limit(100).collect(Collectors.toList()));
                    set.add(combinedHitsMap.keySet().stream().sequential().limit(100).collect(Collectors.toList()));
                    Map<String, Object> testJsonMap = new LinkedHashMap<>();
                    int i = 0;
                    for (List<String> ids : set) {
                        testJsonMap.put(query + " [" + Character.toString((char) ('A' + i++)) + "]", ids);
                    }
                    try {
                        String testContents = testTemplate.replace("{{json}}", mapper.writeValueAsString(testJsonMap));
                        Files.write(testPath, testContents.getBytes());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                System.out.println("--- QUERY=\"" + query + "\" ---");
                System.out.println("Hits: " + NumberFormat.getInstance().format(esHitsMap.size()));
                System.out.println("Path: " + hitsPath.toString());
                System.out.println("Test: " + testPath.toString());
                System.out.println("--- /QUERY ---");
            });
        }
    }

    public static void main(String... args) {
        QueryIndices build = new QueryIndices();
        JCommander.newBuilder().addObject(build).build().parse(args);
        build.run();
    }
}
