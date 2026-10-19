package com.akto.jobs.executors.ibmwatsonx;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The HTTP calls the watsonx client makes; an interface so the client can be tested without IBM.
 */
public interface WatsonxTransport {

    Response send(String method, String url, Map<String, String> headers, String body, String contentType)
            throws IOException;

    final class Response {
        public final int status;
        public final String body;

        public Response(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }

        public boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    /** The real transport (OkHttp, as used by the other connectors in this package). */
    final class OkHttp implements WatsonxTransport {
        private final OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        @Override
        public Response send(String method, String url, Map<String, String> headers, String body, String contentType)
                throws IOException {
            Request.Builder builder = new Request.Builder().url(url);
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    builder.header(header.getKey(), header.getValue());
                }
            }
            if ("POST".equals(method)) {
                builder.post(RequestBody.create(body == null ? "" : body, MediaType.parse(contentType)));
            } else {
                builder.get();
            }
            try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
                ResponseBody responseBody = response.body();
                return new Response(response.code(), responseBody == null ? "" : responseBody.string());
            }
        }
    }
}
