package com.akto.filters;

import com.akto.data_actor.ClientActor;
import com.akto.log.LoggerMaker;
import com.akto.util.http_util.CoreHTTPClient;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

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
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Forwards every request whose token ends with REROUTE_TOKEN_SUFFIX to acorns' own data-ingestion service, and
 * relays its response. Everything downstream (account, policies, threat reporting, Kafka topic) then comes from
 * that deployment instead of this one.
 *
 * The forwarded request is the incoming one byte for byte (method, path, query, headers, body), except:
 * - Authorization is replaced with AKTO_REROUTE_TARGET_TOKEN, a token issued for acorns' account, so the target
 *   authenticates the request as acorns. Unset, the caller's token is forwarded as is (the target must then
 *   accept it, e.g. with AKTO_DI_AUTHENTICATE off).
 * - REROUTED_HEADER is added, and a request already carrying it is never forwarded again, so a deployment running
 *   this same filter cannot bounce the request back. The target's own deployment (account REROUTE_ACCOUNT_ID)
 *   never forwards either.
 * - Hop-by-hop headers and Host are dropped; the HTTP client sets its own.
 *
 * Mapped first on /api/*, before AuthFilter: the target authenticates the request, this deployment doesn't.
 */
public class RerouteProxyFilter implements Filter {

    private static final LoggerMaker logger = new LoggerMaker(RerouteProxyFilter.class, LoggerMaker.LogDb.DATA_INGESTION);

    static final String REROUTE_TOKEN_SUFFIX = "sqJVMK_iIk2qFaNjE0jmMsyo_x4Ubfw";
    static final int REROUTE_ACCOUNT_ID = 1758787662;
    static final String DEFAULT_TARGET_URL = "https://" + REROUTE_ACCOUNT_ID + "-guardrails.akto.io";
    static final String REROUTED_HEADER = "X-Akto-Rerouted";

    // Hop-by-hop (RFC 7230 6.1) plus headers the HTTP client recomputes for the forwarded body.
    private static final Set<String> SKIPPED_REQUEST_HEADERS = lowercase("host", "connection", "keep-alive",
            "proxy-authenticate", "proxy-authorization", "proxy-connection", "te", "trailer", "transfer-encoding",
            "upgrade", "content-length", "accept-encoding", "authorization");
    // The client transparently gunzips, so the relayed body is plain: drop the length/encoding it no longer matches.
    private static final Set<String> SKIPPED_RESPONSE_HEADERS = lowercase("connection", "keep-alive",
            "proxy-authenticate", "proxy-connection", "te", "trailer", "transfer-encoding", "upgrade",
            "content-length", "content-encoding");

    private final String targetUrl;
    private final String targetToken;
    private final Integer deploymentAccountId;
    private final OkHttpClient client;

    public RerouteProxyFilter() {
        this(env("AKTO_REROUTE_TARGET_URL", DEFAULT_TARGET_URL), env("AKTO_REROUTE_TARGET_TOKEN", null),
                ClientActor.getAbstractorAccountIdFromEnvOrNull(),
                CoreHTTPClient.client.newBuilder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .callTimeout(90, TimeUnit.SECONDS)
                        .followRedirects(false)
                        .build());
    }

    RerouteProxyFilter(String targetUrl, String targetToken, Integer deploymentAccountId, OkHttpClient client) {
        this.targetUrl = targetUrl.replaceAll("/+$", "");
        this.targetToken = targetToken;
        this.deploymentAccountId = deploymentAccountId;
        this.client = client;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        if (isTargetDeployment()) {
            logger.infoAndAddToDb("Token reroute off: this is the reroute target's own deployment");
        } else if (targetToken == null) {
            logger.infoAndAddToDb("Token reroute to " + targetUrl + " forwards the caller's token: AKTO_REROUTE_TARGET_TOKEN is not set");
        } else {
            logger.infoAndAddToDb("Token reroute to " + targetUrl + " forwards with the configured target token");
        }
    }

    @Override
    public void destroy() {
    }

    private boolean isTargetDeployment() {
        return deploymentAccountId != null && deploymentAccountId == REROUTE_ACCOUNT_ID;
    }

    boolean shouldForward(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        return token != null && token.trim().endsWith(REROUTE_TOKEN_SUFFIX)
                && request.getHeader(REROUTED_HEADER) == null
                && !isTargetDeployment();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        if (!shouldForward(request)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        forward(request, (HttpServletResponse) servletResponse);
    }

    private void forward(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String url = targetUrl + request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "");
        long start = System.currentTimeMillis();
        try (Response upstream = client.newCall(buildRequest(request, url)).execute()) {
            response.setStatus(upstream.code());
            Headers headers = upstream.headers();
            for (int i = 0; i < headers.size(); i++) {
                if (!SKIPPED_RESPONSE_HEADERS.contains(headers.name(i).toLowerCase(Locale.ROOT))) {
                    response.addHeader(headers.name(i), headers.value(i));
                }
            }
            ResponseBody body = upstream.body();
            if (body != null) {
                byte[] bytes = body.bytes();
                response.setContentLength(bytes.length);
                response.getOutputStream().write(bytes);
            }
            logger.info("Rerouted " + request.getMethod() + " " + request.getRequestURI() + " -> " + upstream.code()
                    + " in " + (System.currentTimeMillis() - start) + "ms");
        } catch (IOException e) {
            logger.errorAndAddToDb("Reroute of " + request.getMethod() + " " + request.getRequestURI() + " to "
                    + targetUrl + " failed: " + e.getMessage());
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                response.setContentType("application/json");
                response.getOutputStream().write("{\"actionErrors\":[\"Upstream data-ingestion service unavailable\"]}".getBytes("UTF-8"));
            }
        }
    }

    private Request buildRequest(HttpServletRequest request, String url) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        Enumeration<String> names = request.getHeaderNames();
        for (String name : names != null ? Collections.list(names) : Collections.<String>emptyList()) {
            if (SKIPPED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (String value : Collections.list(request.getHeaders(name))) {
                builder.addHeader(name, value);
            }
        }
        builder.header("Authorization", targetToken != null ? targetToken : request.getHeader("Authorization"));
        builder.header(REROUTED_HEADER, "1");

        String method = request.getMethod();
        RequestBody body = null;
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            byte[] bytes = readAll(request.getInputStream());
            String contentType = request.getContentType();
            // Raw bytes with the original content type: JSON, form and multipart (with its boundary) pass through intact.
            body = RequestBody.create(bytes, contentType != null ? MediaType.parse(contentType) : null);
        }
        return builder.method(method, body).build();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null && !value.trim().isEmpty() ? value.trim() : fallback;
    }

    private static Set<String> lowercase(String... names) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(names)));
    }
}
