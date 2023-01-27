package com.akto.testing;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

public class HTTPClientHandler {
    private HTTPClientHandler() {}

    private final OkHttpClient clientWithoutFollowRedirect = new OkHttpClient().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(256, 5L, TimeUnit.MINUTES))
            .followRedirects(false)
            .build();

    private final OkHttpClient clientWithFollowRedirect = new OkHttpClient().newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(256, 5L, TimeUnit.MINUTES))
            .followRedirects(true)
            .build();

    public static final HTTPClientHandler instance = new HTTPClientHandler();

    public OkHttpClient getHTTPClient (boolean followRedirect) {
        if (followRedirect) {
            return clientWithFollowRedirect;
        }
        return clientWithoutFollowRedirect;
    }
}
