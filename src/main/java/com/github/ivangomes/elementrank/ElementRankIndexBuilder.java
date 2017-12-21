package com.github.ivangomes.elementrank;

import com.github.ivangomes.elasticsearch.TypeFieldsQuery;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.DisMaxQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@ToString
@RequiredArgsConstructor
@Accessors(fluent = true, chain = true)
@Setter
public class ElementRankIndexBuilder implements Supplier<ElementRankIndex> {
    @NonNull
    private final TransportClient client;
    @NonNull
    private final String index, type;
    private int scrollKeepAlive, size;
    private boolean undirectedGraph;

    @Override
    public ElementRankIndex get() {
        ElementRankIndex elementRankIndex = new ElementRankIndex();
        List<String> idFields = new TypeFieldsQuery(client, index, type).get().entrySet().stream().filter(entry -> !entry.getKey().isEmpty() && entry.getKey().get(entry.getKey().size() - 1).matches("\\w+Id(s)?") && entry.getValue().equals("text")).map(Map.Entry::getKey).map(path -> path.stream().collect(Collectors.joining("."))).collect(Collectors.toList());

        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(index).setTypes(type);
        if (scrollKeepAlive != 0) {
            searchRequestBuilder.setScroll(new TimeValue(scrollKeepAlive));
        }
        if (size != 0) {
            searchRequestBuilder.setSize(size);
        }
        SearchResponse elementsResponse = searchRequestBuilder.get();
        int i = 0;
        do {
            for (SearchHit elementHit : elementsResponse.getHits().getHits()) {
                System.out.println("[INFO] [" + ++i + "] Indexing " + elementHit.getId());
                elementRankIndex.addPage(elementHit.getId());
                DisMaxQueryBuilder disMaxQueryBuilder = QueryBuilders.disMaxQuery();
                idFields.forEach(idField -> disMaxQueryBuilder.add(QueryBuilders.boolQuery().filter(QueryBuilders.termQuery(idField + ".keyword", elementHit.getId()))));
                SearchRequestBuilder incomingLinksBuilder = client.prepareSearch(this.index).setTypes(type).setQuery(disMaxQueryBuilder);
                if (scrollKeepAlive != 0) {
                    incomingLinksBuilder.setScroll(new TimeValue(scrollKeepAlive));
                }
                if (size != 0) {
                    incomingLinksBuilder.setSize(size);
                }
                SearchResponse incomingLinksResponse = incomingLinksBuilder.get();
                do {
                    for (SearchHit incomingLinkHit : incomingLinksResponse.getHits().getHits()) {
                        elementRankIndex.addLink(incomingLinkHit.getId(), elementHit.getId());
                        if (undirectedGraph) {
                            elementRankIndex.addLink(elementHit.getId(), incomingLinkHit.getId());
                        }
                    }
                    SearchScrollRequestBuilder incomingLinksSearchScrollRequestBuilder = client.prepareSearchScroll(incomingLinksResponse.getScrollId());
                    if (scrollKeepAlive != 0) {
                        incomingLinksSearchScrollRequestBuilder.setScroll(new TimeValue(scrollKeepAlive));
                    }
                    incomingLinksResponse = incomingLinksSearchScrollRequestBuilder.execute().actionGet();
                } while (incomingLinksResponse.getHits().getHits().length != 0);
                client.prepareClearScroll().addScrollId(incomingLinksResponse.getScrollId()).get();
            }
            SearchScrollRequestBuilder searchScrollRequestBuilder = client.prepareSearchScroll(elementsResponse.getScrollId());
            if (scrollKeepAlive != 0) {
                searchScrollRequestBuilder.setScroll(new TimeValue(scrollKeepAlive));
            }
            elementsResponse = searchScrollRequestBuilder.execute().actionGet();
        } while (elementsResponse.getHits().getHits().length != 0);
        client.prepareClearScroll().addScrollId(elementsResponse.getScrollId()).get();
        return elementRankIndex;
    }
}
