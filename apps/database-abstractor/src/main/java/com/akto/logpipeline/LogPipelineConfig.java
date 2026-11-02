package com.akto.logpipeline;

/**
 * Environment-driven configuration for the log/metrics Kafka pipeline.
 *
 * The single switch is {@code LOG_KAFKA_ENABLED}. When it is not exactly "true" (the default) the
 * log/metrics API handlers behave exactly as before and nothing in this package is initialised:
 * no Kafka producer, no threads, no connections.
 *
 * Everything else here is only read when the pipeline is enabled (producer side) or when the
 * consumer process is started.
 */
public final class LogPipelineConfig {

    private LogPipelineConfig() {}

    /** The one flag. Default false => legacy direct-to-Mongo behaviour. */
    public static final boolean ENABLED = "true".equalsIgnoreCase(env("LOG_KAFKA_ENABLED", "false"));

    public static final String BROKER_URL = env("LOG_KAFKA_BROKER_URL", null);
    public static final String TOPIC_LOGS = env("LOG_KAFKA_TOPIC_LOGS", "akto.logs");
    public static final String TOPIC_METRICS = env("LOG_KAFKA_TOPIC_METRICS", "akto.metrics");

    // ---- producer tuning (non-blocking guarantees live here) ----
    /** Bounded in-memory queue between request threads and the Kafka send threads. Full => drop. */
    public static final int QUEUE_SIZE = intEnv("LOG_KAFKA_QUEUE_SIZE", 50_000);
    public static final int SEND_THREADS = intEnv("LOG_KAFKA_SEND_THREADS", 2);
    public static final int LINGER_MS = intEnv("LOG_KAFKA_LINGER_MS", 50);
    public static final int BATCH_SIZE = intEnv("LOG_KAFKA_BATCH_SIZE", 64 * 1024);
    /**
     * Upper bound the producer may block a SEND thread on metadata / full buffer. Request threads
     * never wait on this: they only enqueue. Kept above typical metadata-fetch time so records
     * are not lost while the producer warms up.
     */
    public static final int MAX_BLOCK_MS = intEnv("LOG_KAFKA_MAX_BLOCK_MS", 1000);
    public static final long BUFFER_MEMORY_BYTES = (long) intEnv("LOG_KAFKA_BUFFER_MEMORY_MB", 64) * 1024 * 1024;
    /** Consecutive send failures before the circuit breaker opens. */
    public static final int BREAKER_THRESHOLD = intEnv("LOG_KAFKA_BREAKER_THRESHOLD", 20);
    /** How long the breaker stays open (records are dropped without touching Kafka). */
    public static final int BREAKER_OPEN_MS = intEnv("LOG_KAFKA_BREAKER_OPEN_MS", 30_000);

    // ---- consumer ----
    public static final String GROUP_ID_LOGS = env("LOG_KAFKA_GROUP_ID_LOGS", "log-writer-logs");
    public static final String GROUP_ID_METRICS = env("LOG_KAFKA_GROUP_ID_METRICS", "log-writer-metrics");
    /** Which topics this consumer process serves: "logs", "metrics" or "logs,metrics". */
    public static final String CONSUMER_ROLES = env("LOG_CONSUMER_ROLES", "logs,metrics");
    public static final int CONSUMER_THREADS = intEnv("LOG_CONSUMER_THREADS", 2);
    public static final int CONSUMER_THREADS_METRICS = intEnv("LOG_CONSUMER_THREADS_METRICS", CONSUMER_THREADS);
    public static final int MAX_POLL_RECORDS = intEnv("LOG_CONSUMER_MAX_POLL_RECORDS", 1000);
    /** Stop flag. Env value is the initial state; the pause file below can flip it at runtime. */
    public static final String PAUSED_ENV = "LOG_CONSUMER_PAUSED";
    public static final String PAUSE_FILE = env("LOG_CONSUMER_PAUSE_FILE", "/tmp/log-consumer.pause");

    /** True while the consumer should stop writing (Kafka keeps buffering). Checked every poll loop. */
    public static boolean isConsumerPaused() {
        if ("true".equalsIgnoreCase(env(PAUSED_ENV, "false"))) {
            return true;
        }
        try {
            return new java.io.File(PAUSE_FILE).exists();
        } catch (Exception e) {
            return false;
        }
    }

    static String env(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    static int intEnv(String key, int def) {
        try {
            String v = System.getenv(key);
            return (v == null || v.trim().isEmpty()) ? def : Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
