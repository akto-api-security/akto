package com.akto.logpipeline;

import com.akto.listener.InfraMetricsListener;
import com.akto.metrics.CyborgMetricsConfig;
import io.micrometer.core.instrument.FunctionCounter;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fire-and-forget Kafka producer for the log pipeline.
 *
 * Guarantees for the calling (API request) thread:
 *  - {@link #offer} never blocks and never throws: it only does a non-blocking enqueue.
 *  - Sends happen on dedicated daemon threads with a bounded queue. A full queue DROPS the record.
 *  - The Kafka client itself is created lazily and re-tried in the background, so a broker that is
 *    down at startup (or later) never leaves the pipeline permanently dead.
 *  - A circuit breaker stops even enqueueing work while Kafka is failing repeatedly.
 *  - Records are never written to Mongo as a fallback: dropping is intentional (logs/metrics are
 *    non-critical, and falling back would dump the full load on Mongo during a Kafka outage).
 *
 * This class intentionally uses plain slf4j and not LoggerMaker: LoggerMaker writes to the DB, which
 * would feed this very pipeline.
 */
final class LogPipelineProducer {

    private static final Logger logger = LoggerFactory.getLogger(LogPipelineProducer.class);

    private static volatile LogPipelineProducer instance;

    static LogPipelineProducer getInstance() {
        LogPipelineProducer local = instance;
        if (local == null) {
            synchronized (LogPipelineProducer.class) {
                local = instance;
                if (local == null) {
                    local = new LogPipelineProducer();
                    instance = local;
                }
            }
        }
        return local;
    }

    private final ThreadPoolExecutor sendExecutor;
    private volatile KafkaProducer<String, String> producer;
    private volatile long nextCreateAttemptAt = 0L;

    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long breakerOpenUntil = 0L;

    private long lastReportedDropped = 0L;
    private long lastReportedFailed = 0L;

    private LogPipelineProducer() {
        ThreadFactory tf = new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "log-pipeline-send-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        // AbortPolicy => execute() throws RejectedExecutionException when the queue is full; offer() turns that into a drop.
        this.sendExecutor = new ThreadPoolExecutor(
                LogPipelineConfig.SEND_THREADS, LogPipelineConfig.SEND_THREADS,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(LogPipelineConfig.QUEUE_SIZE),
                tf, new ThreadPoolExecutor.AbortPolicy());

        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-pipeline-reporter");
            t.setDaemon(true);
            return t;
        });
        reporter.scheduleWithFixedDelay(this::report, 60, 60, TimeUnit.SECONDS);

        registerMetrics();
    }

    /** Non-blocking. Returns false when the record was dropped. */
    boolean offer(String topic, String message) {
        try {
            if (System.currentTimeMillis() < breakerOpenUntil) {
                dropped.incrementAndGet();
                return false;
            }
            sendExecutor.execute(() -> doSend(topic, message));
            enqueued.incrementAndGet();
            return true;
        } catch (RejectedExecutionException e) {
            dropped.incrementAndGet();
            return false;
        } catch (Throwable t) {
            dropped.incrementAndGet();
            return false;
        }
    }

    private void doSend(String topic, String message) {
        try {
            KafkaProducer<String, String> p = ensureProducer();
            if (p == null) {
                onFailure();
                return;
            }
            p.send(new ProducerRecord<>(topic, message), (metadata, exception) -> {
                if (exception != null) {
                    onFailure();
                } else {
                    sent.incrementAndGet();
                    consecutiveFailures.set(0);
                }
            });
        } catch (Throwable t) {
            // includes TimeoutException from max.block.ms when metadata/buffer is unavailable
            onFailure();
        }
    }

    private void onFailure() {
        failed.incrementAndGet();
        if (consecutiveFailures.incrementAndGet() >= LogPipelineConfig.BREAKER_THRESHOLD) {
            breakerOpenUntil = System.currentTimeMillis() + LogPipelineConfig.BREAKER_OPEN_MS;
            consecutiveFailures.set(0);
            logger.warn("log pipeline circuit breaker opened for {} ms", LogPipelineConfig.BREAKER_OPEN_MS);
        }
    }

    private KafkaProducer<String, String> ensureProducer() {
        KafkaProducer<String, String> p = producer;
        if (p != null) {
            return p;
        }
        synchronized (this) {
            if (producer != null) {
                return producer;
            }
            long now = System.currentTimeMillis();
            if (now < nextCreateAttemptAt) {
                return null;
            }
            nextCreateAttemptAt = now + 30_000L;
            if (LogPipelineConfig.BROKER_URL == null) {
                logger.error("LOG_PIPELINE_ACCOUNTS is set but LOG_KAFKA_BROKER_URL is not set; log pipeline records are being dropped");
                return null;
            }
            try {
                Properties props = new Properties();
                props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, LogPipelineConfig.BROKER_URL);
                props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                props.put(ProducerConfig.CLIENT_ID_CONFIG, "log-pipeline-producer");
                props.put(ProducerConfig.ACKS_CONFIG, "1");
                props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
                props.put(ProducerConfig.LINGER_MS_CONFIG, LogPipelineConfig.LINGER_MS);
                props.put(ProducerConfig.BATCH_SIZE_CONFIG, LogPipelineConfig.BATCH_SIZE);
                props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, LogPipelineConfig.BUFFER_MEMORY_BYTES);
                props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, LogPipelineConfig.MAX_BLOCK_MS);
                props.put(ProducerConfig.RETRIES_CONFIG, 1);
                props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
                props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000);
                KafkaProducer<String, String> created = new KafkaProducer<>(props);
                // Warm up topic metadata on this (send) thread so the first real records are not
                // lost to a metadata timeout. Failure here is fine: sends will retry metadata.
                try {
                    created.partitionsFor(LogPipelineConfig.TOPIC_LOGS);
                    created.partitionsFor(LogPipelineConfig.TOPIC_METRICS);
                } catch (Throwable warm) {
                    logger.warn("log pipeline metadata warm-up failed: {}", warm.toString());
                }
                producer = created;
                logger.info("log pipeline producer created for {}", LogPipelineConfig.BROKER_URL);
                return producer;
            } catch (Throwable t) {
                logger.error("log pipeline producer creation failed; will retry in 30s: {}", t.toString());
                return null;
            }
        }
    }

    private void report() {
        try {
            long d = dropped.get();
            long f = failed.get();
            if (d != lastReportedDropped || f != lastReportedFailed) {
                logger.warn("log pipeline: enqueued={} sent={} dropped={} (+{}) failed={} (+{}) queued={}",
                        enqueued.get(), sent.get(), d, d - lastReportedDropped, f, f - lastReportedFailed,
                        sendExecutor.getQueue().size());
                lastReportedDropped = d;
                lastReportedFailed = f;
            }
        } catch (Throwable ignored) {
        }
    }

    private void registerMetrics() {
        try {
            if (!CyborgMetricsConfig.isEnabled()) {
                return;
            }
            FunctionCounter.builder("log_pipeline_enqueued_total", enqueued, AtomicLong::get).register(InfraMetricsListener.registry);
            FunctionCounter.builder("log_pipeline_sent_total", sent, AtomicLong::get).register(InfraMetricsListener.registry);
            FunctionCounter.builder("log_pipeline_dropped_total", dropped, AtomicLong::get).register(InfraMetricsListener.registry);
            FunctionCounter.builder("log_pipeline_failed_total", failed, AtomicLong::get).register(InfraMetricsListener.registry);
        } catch (Throwable t) {
            logger.warn("could not register log pipeline metrics: {}", t.toString());
        }
    }
}
