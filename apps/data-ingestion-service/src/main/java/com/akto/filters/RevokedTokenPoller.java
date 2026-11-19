package com.akto.filters;

import com.akto.data_actor.ClientActor;
import com.akto.log.LoggerMaker;
import com.akto.util.AuthTokenIds;
import com.akto.util.http_util.CoreHTTPClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

// Polls cyborg for revoked auth-token ids so tokens revoked in the dashboard stop working here; fails open.
public class RevokedTokenPoller {

    private static final LoggerMaker logger = new LoggerMaker(RevokedTokenPoller.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final String FETCH_PATH = "/fetchRevokedTokenIds";
    private static final long POLL_INTERVAL_MS = 15 * 60 * 1000L;
    // after a failed poll retry sooner, so a failure at startup does not leave the set empty for a full interval
    private static final long RETRY_INTERVAL_MS = 60 * 1000L;
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Supplier<String> urlSupplier;
    private final Supplier<String> tokenSupplier;
    private final OkHttpClient client;
    private final long pollIntervalMs;
    private final long retryIntervalMs;
    // resolved once; ClientActor.buildDbAbstractorUrl prints on every call
    private volatile String baseUrl;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicReference<Set<String>> revokedIds = new AtomicReference<>(Collections.emptySet());

    private static class Holder {
        static final RevokedTokenPoller INSTANCE = new RevokedTokenPoller(
                ClientActor::buildDbAbstractorUrl,
                () -> System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN"),
                CoreHTTPClient.client.newBuilder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build());
    }

    public static RevokedTokenPoller getInstance() {
        return Holder.INSTANCE;
    }

    RevokedTokenPoller(Supplier<String> urlSupplier, Supplier<String> tokenSupplier, OkHttpClient client) {
        this(urlSupplier, tokenSupplier, client, POLL_INTERVAL_MS, RETRY_INTERVAL_MS);
    }

    RevokedTokenPoller(Supplier<String> urlSupplier, Supplier<String> tokenSupplier, OkHttpClient client, long pollIntervalMs, long retryIntervalMs) {
        this.urlSupplier = urlSupplier;
        this.tokenSupplier = tokenSupplier;
        this.client = client;
        this.pollIntervalMs = pollIntervalMs;
        this.retryIntervalMs = retryIntervalMs;
    }

    // Starts the daemon poller once; stays off (open) when the service token is not configured.
    public void ensureStarted() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        String token = tokenSupplier.get();
        if (token == null || token.trim().isEmpty()) {
            logger.info("Token revocation polling disabled: DATABASE_ABSTRACTOR_SERVICE_TOKEN is not set");
            return;
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "revoked-token-poller");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.execute(() -> pollThenReschedule(scheduler));
    }

    private void pollThenReschedule(ScheduledExecutorService scheduler) {
        long delay = refresh() ? pollIntervalMs : retryIntervalMs;
        scheduler.schedule(() -> pollThenReschedule(scheduler), delay, TimeUnit.MILLISECONDS);
    }

    public boolean isRevoked(String tokenId) {
        return tokenId != null && revokedIds.get().contains(tokenId);
    }

    // Replaces the revoked set on success; any failure keeps the last known set.
    boolean refresh() {
        try {
            String token = tokenSupplier.get();
            if (baseUrl == null) {
                baseUrl = urlSupplier.get();
            }
            Request request = new Request.Builder()
                    .url(baseUrl + FETCH_PATH)
                    .post(RequestBody.create("{}", JSON))
                    .addHeader("Authorization", token)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    logger.warn("Revoked token poll returned status {}; keeping last known set", response.code());
                    return false;
                }
                Set<String> ids = new HashSet<>();
                JsonNode revoked = MAPPER.readTree(response.body().string()).path("revokedTokenIds");
                for (JsonNode id : revoked) {
                    ids.add(id.asText());
                }
                revokedIds.set(ids);
                return true;
            }
        } catch (Exception e) {
            logger.warn("Revoked token poll failed; keeping last known set: {}", e.getMessage());
            return false;
        }
    }

    // The claim tid (exchanged token's parent id), else the sha256 of the raw token's signature.
    public static String tokenIdOf(Claims claims, String rawToken) {
        String tid = claims.get("tid", String.class);
        return tid != null && !tid.isEmpty() ? tid : AuthTokenIds.idOf(rawToken);
    }
}
