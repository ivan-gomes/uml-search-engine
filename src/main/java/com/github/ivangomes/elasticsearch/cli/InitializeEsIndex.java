package com.github.ivangomes.elasticsearch.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.PathConverter;
import com.github.ivangomes.elasticsearch.ElasticsearchConfig;
import lombok.SneakyThrows;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.mapping.put.PutMappingRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.stream.Stream;

public class InitializeEsIndex implements Runnable {
    @Parameter(names = {"--dir", "-d"}, required = true, converter = PathConverter.class)
    private Path directory;

    @SneakyThrows({IOException.class, URISyntaxException.class})
    public void run() {
        ActionResponse response;
        try (TransportClient client = ElasticsearchConfig.getClient()) {
            try {
                DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(ElasticsearchConfig.INDEX);
                response = client.admin().indices().delete(deleteIndexRequest).actionGet();
                System.out.println("[INFO] Deleted index: " + response);
            } catch (ElasticsearchException ignored) {
                System.out.println("[INFO] Index does not already exist.");
            }
            CreateIndexRequest createIndexRequest = new CreateIndexRequest(ElasticsearchConfig.INDEX);
            response = client.admin().indices().create(createIndexRequest).actionGet();
            System.out.println("[INFO] Created index: " + response);
            PutMappingRequest putMappingRequest = new PutMappingRequest(ElasticsearchConfig.INDEX);
            putMappingRequest.type(ElasticsearchConfig.TYPE);

            InputStream mappingInputStream = InitializeEsIndex.class.getResourceAsStream("/mapping.json");
            if (mappingInputStream == null) {
                System.err.println("[ERROR] Mapping resource not found.");
                System.exit(1);
            }
            putMappingRequest.source(new Scanner(mappingInputStream, "UTF-8").useDelimiter("\\A").next(), XContentType.JSON);
            response = client.admin().indices().putMapping(putMappingRequest).actionGet();
            System.out.println("[INFO] Created mapping: " + response);
            try (Stream<Path> paths = Files.walk(directory)) {
                paths.filter(Files::isRegularFile).forEach(file -> {
                    if (!file.getFileName().toString().endsWith(".json")) {
                        return;
                    }
                    String id = file.getFileName().toString().replace(".json", "");
                    try {
                        byte[] source = Files.readAllBytes(file);
                        IndexRequest request = new IndexRequest(ElasticsearchConfig.INDEX, ElasticsearchConfig.TYPE, id);
                        request.source(source, XContentType.JSON);
                        IndexResponse indexResponse = client.index(request).actionGet();
                        if (indexResponse.getResult() == DocWriteResponse.Result.CREATED) {
                            System.out.println("[INFO] " + id + " created.");
                        } else if (indexResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                            System.out.println("[WARNING] " + id + " updated.");
                        } else {
                            System.err.println("[ERROR] " + id + " was not created.");
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
    }

    public static void main(String... args) {
        InitializeEsIndex build = new InitializeEsIndex();
        JCommander.newBuilder().addObject(build).build().parse(args);
        build.run();
    }
}
