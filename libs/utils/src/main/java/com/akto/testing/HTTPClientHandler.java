package com.akto.testing;

import com.akto.dto.RawApi;
import com.akto.dto.testing.TestingRunResult;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.http_util.CoreHTTPClient;

import okhttp3.*;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Arrays;
import java.util.Collections;

public class HTTPClientHandler {
    private int readTimeout = 30;
    private final OkHttpClient clientWithoutFollowRedirect;
    private final OkHttpClient http2ClientWithoutFollowRedirect;
    private final OkHttpClient http2ClientWithFollowRedirect;
    private final OkHttpClient clientWithFollowRedirect;
    private final OkHttpClient http2httpsClientWithoutFollowRedirect;
    private final OkHttpClient http2httpsClientWithFollowRedirect;

    private static OkHttpClient.Builder builder(boolean followRedirects, int readTimeout) {
        return CoreHTTPClient.client.newBuilder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .callTimeout(readTimeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(256, 5L, TimeUnit.MINUTES))
                .sslSocketFactory(CoreHTTPClient.trustAllSslSocketFactory, (X509TrustManager)CoreHTTPClient.trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true)
                .protocols(Arrays.asList(Protocol.HTTP_1_1))
                .followRedirects(followRedirects);
    }

    private HTTPClientHandler(boolean isSaas) {
        if(isSaas) readTimeout = 60;

        clientWithoutFollowRedirect = builder(false, readTimeout).build();
        clientWithFollowRedirect = builder(true, readTimeout).build();
        // gRPC over HTTP/2 -> --plaintext --http2
        http2ClientWithoutFollowRedirect = builder(false, readTimeout).protocols(Collections.singletonList(Protocol.H2_PRIOR_KNOWLEDGE)).build();
        http2ClientWithFollowRedirect = builder(true, readTimeout).protocols(Collections.singletonList(Protocol.H2_PRIOR_KNOWLEDGE)).build();
        // gRPC over HTTP/2 -> --https 
        // http2 , http1.1 => try http2 first, if not supported, fallback to http1.1
        http2httpsClientWithoutFollowRedirect = builder(false, readTimeout).protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1)).build();
        http2httpsClientWithFollowRedirect = builder(true, readTimeout).protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1)).build();
    }

    public OkHttpClient getNewDebugClient(boolean isSaas, boolean followRedirects, List<TestingRunResult.TestLog> testLogs, String contentType, boolean isHttps) {
        if(isSaas) readTimeout = 60;
        OkHttpClient.Builder builder = builder(followRedirects, readTimeout)
                .addInterceptor(new NormalResponseInterceptor(testLogs))
                .addNetworkInterceptor(new NetworkResponseInterceptor(testLogs));
        if (contentType != null && contentType.contains(HttpRequestResponseUtils.GRPC_CONTENT_TYPE)) {
            if (isHttps) {
                builder.protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
            } else {
                builder.protocols(Collections.singletonList(Protocol.H2_PRIOR_KNOWLEDGE));
            }
        }
        return builder.build();
    }

    static class NormalResponseInterceptor implements Interceptor {

        List<TestingRunResult.TestLog> testLogs;
        @Override
        public @NotNull Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            try {
                Buffer buffer = new Buffer();
                RequestBody requestBody = request.body();
                if (requestBody != null && requestBody.contentLength() != -1) {
                    requestBody.writeTo(buffer);
                    String requestBodyString = buffer.readUtf8();
                    testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "Request Body: " + requestBodyString));
                }
            } catch (Exception e) {
                testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, "Error while parsing request body: " + e.getMessage()));
            }

            Response response = chain.proceed(request);

            if (response == null) {
                testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "Response Body: null"));
            } else {
                try {
                    ResponseBody responseBody = response.peekBody(1024*1024);
                    String body = responseBody != null ? responseBody.string() : "null";
                    testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "Response Body: " + body));
                } catch (Exception e) {
                    testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, "Error while parsing resposne body: " + e.getMessage()));
                }
            }

            return response;
        }

        public NormalResponseInterceptor(List<TestingRunResult.TestLog> testLogs) {
            this.testLogs = testLogs;
        }
    }

    static class NetworkResponseInterceptor implements Interceptor {
        List<TestingRunResult.TestLog> testLogs;
        @Override
        public @NotNull Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            try {
                Map<String,List<String>> requestHeadersMap = ApiExecutor.generateHeadersMapFromHeadersObject(request.headers());;
                String requestHeadersString = RawApi.convertHeaders(requestHeadersMap);
                testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "Request Headers: " + requestHeadersString));

                testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "Hitting URL: " + request.url()));
            } catch (Exception e) {
                testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, "Error while parsing headers or url: " + e.getMessage()));
            }

            Response response = chain.proceed(request);

            if (response == null) {
                testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "Response StatusCode: " + 0));
            } else {
                try {
                    testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "Response StatusCode: " + response.code()));
                    Map<String,List<String>> responseHeadersMap = ApiExecutor.generateHeadersMapFromHeadersObject(response.headers());;
                    String responseHeadersString = RawApi.convertHeaders(responseHeadersMap);
                    testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.INFO, "Response Headers: " + responseHeadersString));
                } catch (Exception e) {
                    testLogs.add(new TestingRunResult.TestLog(TestingRunResult.TestLogType.ERROR, "Error while parsing response headers: " + e.getMessage()));
                }
            }

            return response;
        }

        public NetworkResponseInterceptor(List<TestingRunResult.TestLog> testLogs) {
            this.testLogs = testLogs;
        }
    }

    public static HTTPClientHandler instance = null;

    public static void initHttpClientHandler(boolean isSaas) {
        if (instance == null) {
            instance = new HTTPClientHandler(isSaas);
        }
    }

    public OkHttpClient getHTTPClient (boolean isHttps, boolean followRedirect, String contentType) {
        if (contentType != null && contentType.contains(HttpRequestResponseUtils.GRPC_CONTENT_TYPE)) {
            if (followRedirect) {
                if (isHttps) {
                    return http2httpsClientWithFollowRedirect;
                }
                return http2ClientWithFollowRedirect;
            }
            if (isHttps) {
                return http2httpsClientWithoutFollowRedirect;
            }
            return http2ClientWithoutFollowRedirect;
        }
        if (followRedirect) {
            return clientWithFollowRedirect;
        }
        return clientWithoutFollowRedirect;
    }
}
