package com.akto.testing;

import com.akto.dto.testing.TestingRunConfig.StreamingRequestConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okio.BufferedSource;

public class SseStreamingUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SseStreamingUtil.class, LogDb.TESTING);

    public static final class StreamingResult {
        public final Thread thread;
        public final List<String> chunks;
        public final AtomicReference<Call> callRef;

        StreamingResult(Thread thread, List<String> chunks, AtomicReference<Call> callRef) {
            this.thread = thread;
            this.chunks = chunks;
            this.callRef = callRef;
        }
    }

    public static StreamingResult startStreaming(StreamingRequestConfig config) {
        List<String> chunks = new CopyOnWriteArrayList<>();
        CountDownLatch connected = new CountDownLatch(1);
        AtomicReference<Call> callRef = new AtomicReference<>();

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

                Call call = client.newCall(builder.build());
                callRef.set(call);
                try (Response response = call.execute()) {
                    BufferedSource source = response.body().source();
                    loggerMaker.infoAndAddToDb("SSE connection established, status: " + response.code());
                    connected.countDown();
                    String line;
                    while ((line = source.readUtf8Line()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            chunks.add(data);
                            if (config.lastKey != null && data.contains(config.lastKey)) {
                                loggerMaker.infoAndAddToDb("SSE received lastKey: " + config.lastKey + ", closing stream");
                                break;
                            }
                        }
                    }
                    loggerMaker.infoAndAddToDb("SSE stream closed, total chunks collected: " + chunks.size());
                }
            } catch (Exception e) {
                Call call = callRef.get();
                if (call != null && call.isCanceled()) {
                    loggerMaker.infoAndAddToDb("SSE stream intentionally cancelled for url: " + config.url);
                } else {
                    loggerMaker.errorAndAddToDb(e, "SSE streaming failed for url: " + config.url + " error: " + e.getMessage());
                }
                connected.countDown();
            }
        });

        thread.start();
        try {
            loggerMaker.infoAndAddToDb("Waiting for SSE connection to be established for url: " + config.url);
            boolean established = connected.await(30, TimeUnit.SECONDS);
            if (established) {
                loggerMaker.infoAndAddToDb("SSE connection ready, proceeding with API call for url: " + config.url);
            } else {
                loggerMaker.errorAndAddToDb("SSE connection timed out after 30s for url: " + config.url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            loggerMaker.errorAndAddToDb(e, "SSE connection wait interrupted for url: " + config.url);
        }
        return new StreamingResult(thread, chunks, callRef);
    }

    public static List<String> joinAndCollect(StreamingResult result) {
        loggerMaker.infoAndAddToDb("Joining SSE streaming thread, total chunks collected so far: " + result.chunks.size());
        try {
            result.thread.join(30000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            // cancel() closes the socket, causing readUtf8Line() to throw IOException
            // and actually unblock the thread — thread.interrupt() alone doesn't work with OkHttp
            Call call = result.callRef.get();
            if (call != null) {
                loggerMaker.infoAndAddToDb("SSE cancelling OkHttp call, isCanceled before: " + call.isCanceled());
                call.cancel();
                loggerMaker.infoAndAddToDb("SSE OkHttp call cancelled, isCanceled after: " + call.isCanceled());
            }
            result.thread.interrupt();
        }
        loggerMaker.infoAndAddToDb("SSE streaming thread joined, alive=" + result.thread.isAlive() + ", total chunks collected: " + result.chunks.size());
        return new ArrayList<>(result.chunks);
    }
}
