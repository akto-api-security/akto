package com.akto.testing;

import com.akto.dto.testing.TestingRunConfig.StreamingRequestConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class SseStreamingUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SseStreamingUtil.class, LogDb.TESTING);

    public static final class StreamingResult {
        public final Thread thread;
        public final List<String> chunks;

        StreamingResult(Thread thread, List<String> chunks) {
            this.thread = thread;
            this.chunks = chunks;
        }
    }

    public static StreamingResult startStreaming(StreamingRequestConfig config) {
        List<String> chunks = new CopyOnWriteArrayList<>();

        Thread thread = new Thread(() -> {
            try {
                loggerMaker.infoAndAddToDb("SSE streaming started for url: " + config.url);
                boolean isSaasDeployment = "true".equals(System.getenv("IS_SAAS"));
                if (HTTPClientHandler.instance == null) {
                    HTTPClientHandler.initHttpClientHandler(isSaasDeployment);
                }
                boolean isHttps = config.url.startsWith("https");
                OkHttpClient client = HTTPClientHandler.instance.getHTTPClient(isHttps, true, null);

                Request.Builder builder = new Request.Builder().url(config.url);
                if (config.headers != null) {
                    for (Map.Entry<String, String> h : config.headers.entrySet()) {
                        builder.addHeader(h.getKey(), h.getValue());
                    }
                }
                RequestBody body = RequestBody.create(
                        config.body != null ? config.body : "",
                        MediaType.parse("application/json"));
                builder.post(body);

                try (Response response = client.newCall(builder.build()).execute();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()))) {
                    loggerMaker.infoAndAddToDb("SSE connection established, status: " + response.code());
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            chunks.add(line.substring(6));
                        }
                    }
                    loggerMaker.infoAndAddToDb("SSE stream closed, total chunks collected: " + chunks.size());
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "SSE streaming failed for url: " + config.url + " error: " + e.getMessage());
            }
        });

        thread.start();
        return new StreamingResult(thread, chunks);
    }

    public static List<String> joinAndCollect(StreamingResult result) {
        try {
            result.thread.join(60000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new ArrayList<>(result.chunks);
    }
}
