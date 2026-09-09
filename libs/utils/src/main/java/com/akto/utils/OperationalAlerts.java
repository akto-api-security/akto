package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Bounded, best-effort notifications: never perform network I/O on a request/Kafka thread. */
public final class OperationalAlerts {
    private static final LoggerMaker LOG = new LoggerMaker(OperationalAlerts.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final String WEBHOOK = System.getenv("AKTO_SLACK_ALERT_WEBHOOK");
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
        if (WEBHOOK == null || WEBHOOK.trim().isEmpty()) return;
        INSTANCE.submit(key, "[data-ingestion] host=" + label(System.getenv("HOSTNAME")) + "\n" + message);
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
                    LOG.warn("Operational Slack delivery failed: " + e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            // Keep cooldown even when saturated; avoid hot-loop logging during an outage.
            LOG.warn("Operational Slack queue full; notification skipped");
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
            Request request = new Request.Builder().url(WEBHOOK).post(RequestBody.create(
                    new ObjectMapper().writeValueAsString(payload), MediaType.get("application/json"))).build();
            try (Response response = HTTP.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null || !"ok".equals(response.body().string().trim())) {
                    throw new IllegalStateException("Slack rejected alert");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Slack delivery failed", e);
        }
    }
}
