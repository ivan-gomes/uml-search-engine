package com.github.ivangomes.elasticsearch;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;

import java.util.*;
import java.util.function.Supplier;

@ToString
@RequiredArgsConstructor
@Accessors(fluent = true, chain = true)
@Setter
public class TypeFieldsQuery implements Supplier<Map<List<String>, String>> {
    @NonNull
    private final TransportClient client;
    @NonNull
    private final String index, type;

    @SuppressWarnings("unchecked")
    private void collectTypeMappingProperties(Map<String, Object> property, List<String> currentPath, Map<List<String>, String> typeMapping) {
        if (property == null) {
            return;
        }
        Object o = property.get("type");
        if (o != null && o instanceof String) {
            typeMapping.put(new ArrayList<>(currentPath), (String) o);
        }
        o = property.get("properties");
        if (o != null && Map.class.isAssignableFrom(o.getClass())) {
            Map<String, Object> nestedProperties = (Map<String, Object>) o;
            nestedProperties.forEach((key, value) -> {
                List<String> newPath = new ArrayList<>(currentPath);
                newPath.add(key);
                collectTypeMappingProperties((Map<String, Object>) value, newPath, typeMapping);
            });
        }
    }

    @Override
    public Map<List<String>, String> get() {
        GetMappingsResponse getMappingsResponse = client.admin().indices().prepareGetMappings(this.index).addTypes(type).get();
        ImmutableOpenMap<String, MappingMetaData> indexMappings = getMappingsResponse.getMappings().get(this.index);
        if (indexMappings == null) {
            throw new IllegalStateException("Index mappings missing.");
        }
        MappingMetaData mappingMetaData = indexMappings.get(type);
        if (mappingMetaData == null) {
            throw new IllegalStateException("Type mapping missing.");
        }
        Map<List<String>, String> typeMapping = new LinkedHashMap<>();
        collectTypeMappingProperties(mappingMetaData.getSourceAsMap(), Collections.emptyList(), typeMapping);
        return typeMapping;
    }
}
