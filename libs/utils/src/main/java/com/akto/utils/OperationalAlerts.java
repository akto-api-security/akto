package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.data_actor.ClientActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded, best-effort notifications: never perform network I/O on a request/Kafka thread. */
public final class OperationalAlerts {
    private static final LoggerMaker LOG = new LoggerMaker(OperationalAlerts.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final String WEBHOOK = System.getenv("AKTO_SLACK_ALERT_WEBHOOK");
    private static final String CONFIG_PROBLEM = configurationProblem(WEBHOOK);
    private static final AtomicBoolean CONFIG_LOGGED = new AtomicBoolean();
    // Deployment identity comes from the configured abstractor token, never request data.
    private static final String DEPLOYMENT_ACCOUNT_ID = label(ClientActor.getAbstractorAccountIdFromEnvOrNull());
    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS)
            .writeTimeout(3, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(128), runnable -> {
                Thread thread = new Thread(runnable, "operational-slack-alerts");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static final OperationalAlerts INSTANCE = new OperationalAlerts(
            EXECUTOR, OperationalAlerts::deliver, System::currentTimeMillis, cooldownMillis());

    private final Executor executor;
    private final Consumer<String> sender;
    private final LongSupplier clock;
    private final long cooldown;
    private final Map<String, Long> lastAttempt = new LinkedHashMap<String, Long>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> entry) {
            return size() > 1024;
        }
    };

    OperationalAlerts(Executor executor, Consumer<String> sender, LongSupplier clock, long cooldown) {
        this.executor = executor;
        this.sender = sender;
        this.clock = clock;
        this.cooldown = cooldown;
    }

    public static void send(String key, String message) {
        logConfiguration();
        if (CONFIG_PROBLEM != null) return;
        INSTANCE.submit(key, "[data-ingestion] host=" + label(System.getenv("HOSTNAME")) + "\n" + message);
    }

    public static void logConfiguration() {
        if (!CONFIG_LOGGED.compareAndSet(false, true)) return;
        if (CONFIG_PROBLEM != null) {
            LOG.warn("Operational Slack alerts disabled: " + CONFIG_PROBLEM);
        } else {
            LOG.info("Operational Slack alerts configured; account=" + DEPLOYMENT_ACCOUNT_ID
                    + ", cooldownSeconds=" + cooldownMillis() / 1000);
        }
    }

    static String configurationProblem(String webhook) {
        if (webhook == null || webhook.trim().isEmpty()) {
            return "AKTO_SLACK_ALERT_WEBHOOK is not set in the data-ingestion process environment";
        }
        if (HttpUrl.parse(webhook.trim()) == null) {
            return "AKTO_SLACK_ALERT_WEBHOOK is not a valid HTTP(S) URL";
        }
        return null;
    }

    /** accountId from DATABASE_ABSTRACTOR_SERVICE_TOKEN; unknown if missing/invalid. */
    public static String deploymentAccountId() {
        return DEPLOYMENT_ACCOUNT_ID;
    }

    synchronized void submit(String key, String message) {
        long now = clock.getAsLong();
        Long previous = lastAttempt.get(key);
        if (previous != null && now - previous < cooldown) return;
        lastAttempt.put(key, now);
        try {
            executor.execute(() -> {
                try {
                    sender.accept(message);
                } catch (Exception e) {
                    // Do not log webhook URLs, response bodies, or request data.
                    LOG.warn("Operational Slack delivery failed: " + failureDescription(e)
                            + "; another failure after the cooldown will retry");
                }
            });
        } catch (RejectedExecutionException e) {
            // Keep cooldown even when saturated; avoid hot-loop logging during an outage.
            LOG.warn("Operational Slack queue full; notification skipped");
        }
    }

    static String failureDescription(Throwable error) {
        if (error instanceof SlackDeliveryException) return error.getMessage();
        Throwable cause = error;
        // Only exception type names are safe: network exception messages may contain URLs.
        for (int i = 0; i < 10 && cause.getCause() != null && cause.getCause() != cause; i++) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName();
    }

    private static final class SlackDeliveryException extends RuntimeException {
        SlackDeliveryException(String safeMessage) { super(safeMessage); }
    }

    static void checkResponse(Response response) throws java.io.IOException {
        if (!response.isSuccessful()) {
            throw new SlackDeliveryException("Slack returned HTTP " + response.code());
        }
        if (response.body() == null || !"ok".equals(response.body().string().trim())) {
            throw new SlackDeliveryException("Slack returned HTTP " + response.code() + " without an ok acknowledgement");
        }
    }

    public static String label(Object value) {
        String text = value == null ? "unknown" : String.valueOf(value);
        text = text.replaceAll("[\\r\\n\\t]", " ");
        return text.substring(0, Math.min(text.length(), 200));
    }

    private static long cooldownMillis() {
        try {
            return Math.min(86400L, Math.max(1L, Long.parseLong(System.getenv()
                    .getOrDefault("AKTO_SLACK_ALERT_COOLDOWN_SECONDS", "300")))) * 1000L;
        } catch (NumberFormatException e) {
            return 300_000L;
        }
    }

    private static void deliver(String message) {
        try {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("text", message);
            payload.put("mrkdwn", false);
            Request request = new Request.Builder().url(WEBHOOK.trim()).post(RequestBody.create(
                    new ObjectMapper().writeValueAsString(payload), MediaType.get("application/json"))).build();
            try (Response response = HTTP.newCall(request).execute()) {
                checkResponse(response);
                LOG.info("Operational Slack notification delivered");
            }
        } catch (SlackDeliveryException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Slack delivery failed", e);
        }
    }
}
