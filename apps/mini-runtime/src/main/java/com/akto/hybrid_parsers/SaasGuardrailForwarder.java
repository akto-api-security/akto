package com.akto.hybrid_parsers;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.SecretUtils;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Ships /chat and /mcp traffic to Akto's SaaS guardrail service for threat detection.
 *
 * Detection only: mini-runtime consumes mirrored traffic after the fact, so nothing here can block
 * a request. The verdict surfaces in the SaaS account, not in the on-prem deployment this runs in.
 *
 * Fire and forget by design - the caller is the ingestion path, so a slow or unreachable guardrail
 * service must cost a dropped sample and never consumer lag.
 */
public class SaasGuardrailForwarder {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SaasGuardrailForwarder.class, LogDb.RUNTIME);

    private static final boolean ENABLED =
            "true".equalsIgnoreCase(System.getenv("AKTO_SAAS_GUARDRAIL_ENABLED"));

    /*
     * The subdomain is per-account, so the host is overridable: without that, every deployment
     * running this build would ship its prompts into whichever account is compiled in.
     * Declared before GUARDRAIL_HOST because static initialisers run in textual order.
     */
    static final String DEFAULT_GUARDRAIL_HOST = "https://1726615470-guardrails.akto.io";

    private static final String GUARDRAIL_PATH =
            "/api/http-proxy?guardrails=true&ingest_data=true&response_guardrails=true";

    private static final String GUARDRAIL_URL =
            normaliseHost(System.getenv("AKTO_SAAS_GUARDRAIL_HOST")) + GUARDRAIL_PATH;

    static String normaliseHost(String host) {
        if (host == null || host.trim().isEmpty()) {
            return DEFAULT_GUARDRAIL_HOST;
        }
        host = host.trim();
        // A trailing slash would produce //api/http-proxy, which the proxy does not route.
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host;
    }

    private static final MediaType JSON = MediaType.parse("application/json");

    // Deliberately short: this budget is what bounds how fast the queue drains, and a stalled
    // request holds a worker that could be shipping the next sample.
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build();

    /*
     * A bounded queue plus DiscardPolicy is what keeps this off the ingestion path's critical
     * section: once the queue is full, execute() drops silently instead of blocking the parser
     * thread (what an unbounded queue would trade for OOM, and CallerRunsPolicy for lag).
     */
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            1, 4, 60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r, "saas-guardrail-forwarder");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardPolicy());

    /**
     * Literal substring match, not a regex - this runs per request on the ingestion path.
     *
     * Known limitation: real chat endpoints frequently don't say "chat" (/backend-api/f/conversation,
     * /v1/messages, /api/generate), and this also matches unrelated paths like /chatbot-config.
     * Switching the selector to the gen-ai / mcp-server tags already computed in HttpCallParser
     * would be both cheaper and more accurate if the POC needs that coverage.
     */
    static boolean isGuardrailCandidate(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String path = url.toLowerCase();
        return path.contains("/chat") || path.contains("/mcp");
    }

    /** Non-blocking. Safe to call per request; does nothing unless the env flag is set. */
    public static void offer(HttpResponseParams httpResponseParams) {
        if (!ENABLED || httpResponseParams == null) {
            return;
        }
        try {
            if (httpResponseParams.getRequestParams() == null
                    || !isGuardrailCandidate(httpResponseParams.getRequestParams().getURL())) {
                return;
            }
            // getOrig() is the raw collector message, which already matches the shape the
            // guardrail endpoint expects - no rebuilding, and nothing extra to keep in sync.
            String body = httpResponseParams.getOrig();
            if (body == null || body.isEmpty()) {
                return;
            }
            POOL.execute(() -> post(body));
        } catch (Exception e) {
            // Forwarding must never break ingestion, so this swallows rather than propagates.
            loggerMaker.debug("saas-guardrail: offer failed " + e.getMessage());
        }
    }

    private static void post(String body) {
        try {
            Request.Builder builder = new Request.Builder()
                    .url(GUARDRAIL_URL)
                    .post(RequestBody.create(body, JSON));

            /*
             * The raw provisioned token, deliberately NOT the module-scoped one ClientActor
             * exchanges for - that exchange is scoped to the database abstractor, and the
             * guardrail service is a different audience.
             *
             * Read per call rather than cached: readSecret only touches disk when the _FILE
             * variant is set, so this is a map lookup in the common case, and it keeps a rotated
             * token from needing a pod restart.
             */
            String token = SecretUtils.readSecret("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
            if (token != null && !token.isEmpty()) {
                builder.header("Authorization", token);
            } else {
                loggerMaker.debug("saas-guardrail: no DATABASE_ABSTRACTOR_SERVICE_TOKEN, sending unauthenticated");
            }

            Request request = builder.build();
            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    loggerMaker.debug("saas-guardrail: non-2xx " + response.code());
                }
            }
        } catch (Exception e) {
            loggerMaker.debug("saas-guardrail: forward failed " + e.getMessage());
        }
    }
}
