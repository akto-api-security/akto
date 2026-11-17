package com.akto.filters;

import com.akto.data_actor.ClientActor;
import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import okhttp3.ConnectionPool;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Forwards every request whose token ends with REROUTE_TOKEN_SUFFIX, as is, to acorns' data-ingestion service and
 * relays its response. Acorns' deployment then handles it with its own env (account, policies, Kafka topic).
 * Mapped first on /api/*. REROUTED_HEADER marks a forwarded request so it is never forwarded again.
 */
public class RerouteProxyFilter implements Filter {

    private static final LoggerMaker logger = new LoggerMaker(RerouteProxyFilter.class, LoggerMaker.LogDb.DATA_INGESTION);

    static final String REROUTE_TOKEN_SUFFIX = "sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw";
    static final int REROUTE_ACCOUNT_ID = 1758787662;
    static final String REROUTED_HEADER = "X-Akto-Rerouted";

    // Hop-by-hop headers and those the HTTP client sets itself; it also gunzips responses transparently.
    private static final Set<String> SKIPPED_HEADERS = new HashSet<>(Arrays.asList("host", "connection", "keep-alive",
            "te", "trailer", "transfer-encoding", "upgrade", "content-length", "accept-encoding", "content-encoding"));

    private final String targetUrl;
    private final Integer deploymentAccountId;
    private final OkHttpClient client;

    // Acorns' data-ingestion container, reached directly over the shared Docker network (the one nginx routes
    // 1758787662-guardrails.akto.io to), skipping public DNS/TLS.
    static final String TARGET_URL = "http://acorns-data-ingestion-service:8080";

    public RerouteProxyFilter() {
        this(TARGET_URL, ClientActor.getAbstractorAccountIdFromEnvOrNull(),
                CoreHTTPClient.client.newBuilder()
                        .connectionPool(new ConnectionPool(20, 5, TimeUnit.MINUTES)) // own pool: one connection per in-flight request over HTTP/1.1
                        .callTimeout(90, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build());
    }

    RerouteProxyFilter(String targetUrl, Integer deploymentAccountId, OkHttpClient client) {
        this.targetUrl = targetUrl.replaceAll("/+$", "");
        this.deploymentAccountId = deploymentAccountId;
        this.client = client;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String token = request.getHeader("Authorization");
        boolean forward = token != null && token.trim().endsWith(REROUTE_TOKEN_SUFFIX)
                && request.getHeader(REROUTED_HEADER) == null
                && (deploymentAccountId == null || deploymentAccountId != REROUTE_ACCOUNT_ID);
        if (!forward) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        try (Response upstream = client.newCall(buildRequest(request)).execute()) {
            response.setStatus(upstream.code());
            Headers headers = upstream.headers();
            for (int i = 0; i < headers.size(); i++) {
                if (!SKIPPED_HEADERS.contains(headers.name(i).toLowerCase(Locale.ROOT))) {
                    response.addHeader(headers.name(i), headers.value(i));
                }
            }
            response.getOutputStream().write(upstream.body() != null ? upstream.body().bytes() : new byte[0]);
        } catch (IOException e) {
            logger.errorAndAddToDb("Reroute of " + request.getRequestURI() + " to " + targetUrl + " failed: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        }
    }

    private Request buildRequest(HttpServletRequest request) throws IOException {
        String query = request.getQueryString();
        Request.Builder builder = new Request.Builder().url(targetUrl + request.getRequestURI() + (query != null ? "?" + query : ""));
        for (String name : Collections.list(request.getHeaderNames())) {
            if (!SKIPPED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                for (String value : Collections.list(request.getHeaders(name))) {
                    builder.addHeader(name, value);
                }
            }
        }
        builder.header(REROUTED_HEADER, "1");
        String method = request.getMethod();
        RequestBody body = null;
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            String contentType = request.getContentType();
            body = RequestBody.create(readAll(request.getInputStream()), contentType != null ? MediaType.parse(contentType) : null);
        }
        return builder.method(method, body).build();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) != -1; ) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
