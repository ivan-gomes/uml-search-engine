package com.github.ivangomes.elasticsearch;

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
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

@ToString
@RequiredArgsConstructor
@Accessors(fluent = true, chain = true)
@Setter
public class ElasticsearchSearcher implements Supplier<SearchHits> {
    @NonNull
    private final TransportClient client;
    @NonNull
    private final String index, type, query;
    private int scrollKeepAlive, size;

    @Override
    public SearchHits get() {
        List<SearchHit> hits = new ArrayList<>();
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(index).setTypes(type);
        if (scrollKeepAlive != 0) {
            searchRequestBuilder.setScroll(new TimeValue(scrollKeepAlive));
        }
        if (size != 0) {
            searchRequestBuilder.setSize(size);
        }
        searchRequestBuilder.setQuery(QueryBuilders.queryStringQuery(query));
        SearchResponse elementsResponse = searchRequestBuilder.get();
        do {
            hits.addAll(Arrays.asList(elementsResponse.getHits().getHits()));
            SearchScrollRequestBuilder searchScrollRequestBuilder = client.prepareSearchScroll(elementsResponse.getScrollId());
            if (scrollKeepAlive != 0) {
                searchScrollRequestBuilder.setScroll(new TimeValue(scrollKeepAlive));
            }
            elementsResponse = searchScrollRequestBuilder.execute().actionGet();
        } while (elementsResponse.getHits().getHits().length != 0);
        client.prepareClearScroll().addScrollId(elementsResponse.getScrollId()).get();
        return new SearchHits(hits.toArray(new SearchHit[hits.size()]), elementsResponse.getHits().getTotalHits(), elementsResponse.getHits().getMaxScore());
    }
}
