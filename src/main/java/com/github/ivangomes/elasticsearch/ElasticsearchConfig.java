package com.github.ivangomes.elasticsearch;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ElasticsearchConfig {
    public static final String INDEX = "model";
    public static final String TYPE = "element";
    public static final int DEFAULT_SCROLL_KEEP_ALIVE = 60000;
    public static final int DEFAULT_SCROLL_SIZE = 100;

    private static TransportClient CLIENT;

    public static TransportClient getClient() throws UnknownHostException {
        if (CLIENT == null) {
            CLIENT = new PreBuiltTransportClient(Settings.EMPTY).addTransportAddress(new TransportAddress(InetAddress.getByName("localhost"), 9300));
        }
        return CLIENT;
    }
}
