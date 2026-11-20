package com.akto.logpipeline;

import com.akto.DaoInit;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Standalone entry point for the log pipeline consumer. NOT a web app: no servlet container, no
 * crons, no API. It only reads Kafka and writes to the Mongo given by AKTO_MONGO_CONN, which must
 * be the NEW logs database (not cyborg's main database).
 *
 * Scale out by raising LOG_CONSUMER_THREADS and/or starting more processes with the same group id
 * (up to the topic's partition count). Pause with LOG_CONSUMER_PAUSED=true or by creating the file
 * named by LOG_CONSUMER_PAUSE_FILE (default /tmp/log-consumer.pause); delete the file to resume.
 *
 * Roles: LOG_CONSUMER_ROLES=logs | metrics | logs,metrics (default both) lets you run dedicated
 * consumers per topic and scale them independently.
 */
public class LogPipelineConsumerMain {

    private static final Logger logger = LoggerFactory.getLogger(LogPipelineConsumerMain.class);

    public static void main(String[] args) throws Exception {
        String mongoUri = System.getenv("AKTO_MONGO_CONN");
        if (mongoUri == null || mongoUri.isEmpty()) {
            throw new IllegalStateException("AKTO_MONGO_CONN (the NEW logs MongoDB) is required");
        }
        if (LogPipelineConfig.BROKER_URL == null) {
            throw new IllegalStateException("LOG_KAFKA_BROKER_URL is required");
        }

        // Writes only; primary read preference so collection-existence checks see fresh data.
        DaoInit.init(new ConnectionString(mongoUri), ReadPreference.primary());
        logger.info("log consumer connected to logs mongo; roles={} threads={} metricsThreads={}",
                LogPipelineConfig.CONSUMER_ROLES, LogPipelineConfig.CONSUMER_THREADS, LogPipelineConfig.CONSUMER_THREADS_METRICS);

        List<LogPipelineConsumerWorker> workers = new ArrayList<>();
        List<Thread> threads = new ArrayList<>();
        String roles = LogPipelineConfig.CONSUMER_ROLES.toLowerCase();

        if (roles.contains("logs")) {
            for (int i = 0; i < LogPipelineConfig.CONSUMER_THREADS; i++) {
                start(new LogPipelineConsumerWorker(LogPipelineConsumerWorker.Kind.LOGS, i), workers, threads);
            }
        }
        if (roles.contains("metrics")) {
            for (int i = 0; i < LogPipelineConfig.CONSUMER_THREADS_METRICS; i++) {
                start(new LogPipelineConsumerWorker(LogPipelineConsumerWorker.Kind.METRICS, i), workers, threads);
            }
        }
        if (workers.isEmpty()) {
            throw new IllegalStateException("LOG_CONSUMER_ROLES must contain 'logs' and/or 'metrics', got: " + roles);
        }

        ScheduledExecutorService stats = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-consumer-stats");
            t.setDaemon(true);
            return t;
        });
        stats.scheduleWithFixedDelay(() -> logger.info("log consumer totals: written={} skipped={} writeErrors={}",
                LogPipelineConsumerWorker.writtenCount(), LogPipelineConsumerWorker.skippedCount(),
                LogPipelineConsumerWorker.errorCount()), 60, 60, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("shutting down log consumers");
            for (LogPipelineConsumerWorker w : workers) {
                w.shutdown();
            }
            for (Thread t : threads) {
                try {
                    t.join(10_000);
                } catch (InterruptedException ignored) {
                }
            }
        }));

        for (Thread t : threads) {
            t.join();
        }
    }

    private static void start(LogPipelineConsumerWorker w, List<LogPipelineConsumerWorker> workers, List<Thread> threads) {
        Thread t = new Thread(w);
        t.start();
        workers.add(w);
        threads.add(t);
    }
}
