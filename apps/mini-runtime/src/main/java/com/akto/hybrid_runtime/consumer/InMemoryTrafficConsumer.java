package com.akto.hybrid_runtime.consumer;

import com.akto.behaviour_modelling.SessionAnalyzer;
import com.akto.dto.APIConfig;
import com.akto.dto.HttpResponseParams;
import com.akto.hybrid_parsers.HttpCallParser;
import com.akto.hybrid_runtime.Main;
import com.akto.ingest.TrafficIngestQueue;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drains {@link TrafficIngestQueue} in batches and runs the full processing pipeline
 * ({@link Main#handleResponseParams}). Both Kafka-sourced and HTTP-ingest-sourced
 * messages converge here.
 *
 * Parsing logic lives in {@link Main#bulkParseTrafficToResponseParams} so it stays
 * in one place alongside the rest of Main's processing methods.
 *
 * Designed to run on the main thread (blocking). Call {@link #stop()} from a
 * shutdown hook to exit cleanly.
 */
public class InMemoryTrafficConsumer extends TrafficConsumer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InMemoryTrafficConsumer.class, LogDb.RUNTIME);
    private static final int BATCH_SIZE = 100;
    private static final long POLL_TIMEOUT_MS = 10_000;

    private final TrafficIngestQueue queue;
    private final boolean syncImmediately;
    private final boolean fetchAllSTI;
    private final Map<Integer, Main.AccountInfo> accountInfoMap;
    private final boolean isDashboardInstance;
    private final String centralKafkaTopicName;
    private final APIConfig apiConfig;
    private final Map<String, HttpCallParser> httpCallParserMap;
    private final Map<String, SessionAnalyzer> sessionAnalyzerMap;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public InMemoryTrafficConsumer(
            TrafficIngestQueue queue,
            boolean syncImmediately,
            boolean fetchAllSTI,
            Map<Integer, Main.AccountInfo> accountInfoMap,
            boolean isDashboardInstance,
            String centralKafkaTopicName,
            APIConfig apiConfig,
            Map<String, HttpCallParser> httpCallParserMap,
            Map<String, SessionAnalyzer> sessionAnalyzerMap) {
        this.queue = queue;
        this.syncImmediately = syncImmediately;
        this.fetchAllSTI = fetchAllSTI;
        this.accountInfoMap = accountInfoMap;
        this.isDashboardInstance = isDashboardInstance;
        this.centralKafkaTopicName = centralKafkaTopicName;
        this.apiConfig = apiConfig;
        this.httpCallParserMap = httpCallParserMap;
        this.sessionAnalyzerMap = sessionAnalyzerMap;
    }

    /** Blocking. Returns only when {@link #stop()} is called. */
    @Override
    public void start() {
        running.set(true);
        long lastSyncOffset = 0;
        loggerMaker.infoAndAddToDb("InMemoryTrafficConsumer started");
        while (running.get()) {
            try {
                List<String> batch = queue.drainBatch(BATCH_SIZE, POLL_TIMEOUT_MS);
                if (batch.isEmpty()) continue;

                Map<String, List<HttpResponseParams>> responseParamsToAccountMap = new HashMap<>();
                long start = System.currentTimeMillis();
                lastSyncOffset = Main.bulkParseTrafficToResponseParams(lastSyncOffset, batch, responseParamsToAccountMap);

                Main.handleResponseParams(responseParamsToAccountMap, accountInfoMap, isDashboardInstance,
                        httpCallParserMap, sessionAnalyzerMap, apiConfig, fetchAllSTI,
                        syncImmediately, centralKafkaTopicName);

                AllMetrics.instance.setRuntimeProcessLatency(System.currentTimeMillis() - start);
                AllMetrics.instance.setRuntimeApiReceivedCount((float) batch.size());

                if (lastSyncOffset % 1000 == 0) {
                    loggerMaker.infoAndAddToDb("InMemoryTrafficConsumer processed " + lastSyncOffset
                            + " messages, queue size=" + queue.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "InMemoryTrafficConsumer error: " + e.getMessage());
            }
        }
        loggerMaker.infoAndAddToDb("InMemoryTrafficConsumer stopped");
    }

    @Override
    public void stop() {
        running.set(false);
    }
}
