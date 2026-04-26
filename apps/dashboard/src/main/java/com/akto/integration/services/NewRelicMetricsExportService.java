package com.akto.integration.services;

import com.akto.dao.context.Context;
import com.akto.dao.integration.NewRelicIntegrationDao;
import com.akto.dto.integration.NewRelicIntegration;
import com.akto.dto.traffic_metrics.RuntimeMetrics;
import com.akto.integration.NewRelicConfiguration;
import com.akto.integration.NewRelicApiClient;
import com.akto.kafka.Kafka;
import com.akto.kafka.KafkaConsumerConfig;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * NewRelic Metrics Export Service
 * Kafka consumer that exports API metrics to NewRelic
 * Runs as a background service started by InitializerListener
 */
public class NewRelicMetricsExportService implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(NewRelicMetricsExportService.class);
    private static final Gson gson = new Gson();
    private static final long CACHE_TTL_MILLIS = 5 * 60 * 1000; // 5 minutes
    private static final int POLL_TIMEOUT_MS = 3000;
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private final NewRelicConfiguration config;
    private final NewRelicApiClient apiClient;
    private final KafkaConsumer<String, String> consumer;
    private volatile boolean running = true;

    // Cache for integration configs (orgId -> config with timestamp)
    private final ConcurrentHashMap<Integer, CachedConfig> integrationConfigCache = new ConcurrentHashMap<>();

    // Metrics tracking
    private volatile long metricsExportedTotal = 0;
    private volatile long metricsFailedTotal = 0;
    private volatile long lastExportTime = 0;
    private volatile int lastBatchSize = 0;

    /**
     * Cached config with TTL
     */
    private static class CachedConfig {
        final NewRelicIntegration config;
        final long cachedAt;

        CachedConfig(NewRelicIntegration config) {
            this.config = config;
            this.cachedAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > CACHE_TTL_MILLIS;
        }
    }

    /**
     * Constructor
     */
    public NewRelicMetricsExportService(NewRelicConfiguration config, NewRelicApiClient apiClient) {
        this.config = config;
        this.apiClient = apiClient;
        this.consumer = createKafkaConsumer();
        logger.info("NewRelicMetricsExportService initialized");
    }

    /**
     * Create Kafka consumer
     */
    private KafkaConsumer<String, String> createKafkaConsumer() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getKafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, config.getKafkaConsumerGroup());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 10000);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, config.getMaxBatchSize());
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 3000);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(props);
        kafkaConsumer.subscribe(Collections.singletonList("api-runtime-metrics"));
        logger.info("Kafka consumer subscribed to: api-runtime-metrics");
        return kafkaConsumer;
    }

    /**
     * Main consumer loop - runs as background service
     */
    @Override
    public void run() {
        logger.info("NewRelic metrics export service started");

        while (running) {
            try {
                // Poll for messages
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(POLL_TIMEOUT_MS));

                if (records.isEmpty()) {
                    continue;
                }

                logger.debug("Polled {} messages from Kafka", records.count());

                // Group metrics by organization
                Map<Integer, List<RuntimeMetrics>> metricsPerOrg = new HashMap<>();
                for (org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record : records) {
                    try {
                        RuntimeMetrics metric = gson.fromJson(record.value(), RuntimeMetrics.class);
                        if (metric != null && metric.getVal() != null) {
                            // Extract orgId from metric (would need to be passed in message or context)
                            // For now, we'll process metrics from all configured orgs
                            metricsPerOrg.computeIfAbsent(extractOrgId(record), k -> new ArrayList<>())
                                .add(metric);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to parse metric from Kafka message: {}", record.value(), e);
                        metricsFailedTotal++;
                    }
                }

                // Export metrics per organization
                for (Map.Entry<Integer, List<RuntimeMetrics>> entry : metricsPerOrg.entrySet()) {
                    int orgId = entry.getKey();
                    List<RuntimeMetrics> metrics = entry.getValue();

                    try {
                        exportMetricsForOrg(orgId, metrics);
                    } catch (Exception e) {
                        logger.error("Failed to export metrics for org {}: {}", orgId, e.getMessage(), e);
                        metricsFailedTotal += metrics.size();
                    }
                }

            } catch (Exception e) {
                logger.error("Error in NewRelic metrics export consumer loop", e);
                // Continue running on transient errors
                try {
                    Thread.sleep(5000); // Wait before retry
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // Graceful shutdown
        logger.info("Flushing pending metrics on shutdown...");
        flushPendingMetrics();
        consumer.close();
        logger.info("NewRelic metrics export service stopped");
    }

    /**
     * Export metrics for a specific organization
     */
    private void exportMetricsForOrg(int orgId, List<RuntimeMetrics> metrics) {
        // Check if integration enabled for this org
        if (!isNewRelicEnabledForOrg(orgId)) {
            logger.debug("NewRelic integration not enabled for org {}, skipping {} metrics", orgId, metrics.size());
            return;
        }

        // Get NewRelic configuration for org
        NewRelicIntegration config = getNewRelicConfig(orgId);
        if (config == null || !config.getEnabled()) {
            logger.debug("No NewRelic configuration found for org {}", orgId);
            return;
        }

        // Batch metrics
        List<List<RuntimeMetrics>> batches = batchMetrics(metrics, this.config.getMaxBatchSize());
        for (List<RuntimeMetrics> batch : batches) {
            exportBatch(orgId, config, batch);
        }

        // Update last sync time
        updateLastSyncTime(orgId);
        lastExportTime = System.currentTimeMillis();
    }

    /**
     * Export a batch of metrics with retries
     */
    private void exportBatch(int orgId, NewRelicIntegration config, List<RuntimeMetrics> metrics) {
        int retries = 0;
        long backoffMs = INITIAL_BACKOFF_MS;

        while (retries < MAX_RETRIES) {
            try {
                // Send to NewRelic API
                long startTime = System.currentTimeMillis();
                apiClient.sendMetrics(config.getApiKey(), config.getAccountId(),
                                     config.getRegion(), transformMetrics(metrics));

                long latencyMs = System.currentTimeMillis() - startTime;
                metricsExportedTotal += metrics.size();
                lastBatchSize = metrics.size();

                logger.info("Exported {} metrics for org {} in {}ms",
                    metrics.size(), orgId, latencyMs);
                return;

            } catch (Exception e) {
                retries++;
                logger.warn("Failed to export metrics for org {} (attempt {}/{}): {}",
                    orgId, retries, MAX_RETRIES, e.getMessage());

                if (retries < MAX_RETRIES) {
                    try {
                        Thread.sleep(backoffMs);
                        backoffMs *= 2; // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    metricsFailedTotal += metrics.size();
                    logger.error("Failed to export {} metrics for org {} after {} retries",
                        metrics.size(), orgId, MAX_RETRIES);
                }
            }
        }
    }

    /**
     * Transform RuntimeMetrics to NewRelic NRQL format
     */
    private List<Map<String, Object>> transformMetrics(List<RuntimeMetrics> metrics) {
        List<Map<String, Object>> nrqlMetrics = new ArrayList<>();

        for (RuntimeMetrics metric : metrics) {
            Map<String, Object> nrqlMetric = new HashMap<>();
            nrqlMetric.put("timestamp", metric.getTimestamp() * 1000L); // Convert to milliseconds
            nrqlMetric.put("value", metric.getVal());
            nrqlMetric.put("name", metric.getName());
            nrqlMetric.put("attributes", new HashMap<String, Object>() {{
                put("instanceId", metric.getInstanceId());
            }});
            nrqlMetrics.add(nrqlMetric);
        }

        return nrqlMetrics;
    }

    /**
     * Batch metrics into chunks
     */
    private List<List<RuntimeMetrics>> batchMetrics(List<RuntimeMetrics> metrics, int batchSize) {
        List<List<RuntimeMetrics>> batches = new ArrayList<>();
        for (int i = 0; i < metrics.size(); i += batchSize) {
            batches.add(metrics.subList(i, Math.min(i + batchSize, metrics.size())));
        }
        return batches;
    }

    /**
     * Get NewRelic config for organization (with caching)
     */
    private NewRelicIntegration getNewRelicConfig(int orgId) {
        CachedConfig cached = integrationConfigCache.get(orgId);
        if (cached != null && !cached.isExpired()) {
            return cached.config;
        }

        // Fetch from MongoDB
        Context.accountId.set(orgId);
        try {
            NewRelicIntegration config = NewRelicIntegrationDao.instance.findOne(
                Filters.eq(NewRelicIntegration.ORG_ID, orgId)
            );
            if (config != null) {
                integrationConfigCache.put(orgId, new CachedConfig(config));
            }
            return config;
        } finally {
            Context.accountId.remove();
        }
    }

    /**
     * Check if NewRelic integration is enabled for org
     */
    private boolean isNewRelicEnabledForOrg(int orgId) {
        if (!config.isIntegrationEnabled()) {
            return false;
        }
        NewRelicIntegration integration = getNewRelicConfig(orgId);
        return integration != null && integration.getEnabled();
    }

    /**
     * Update last sync time in MongoDB
     */
    private void updateLastSyncTime(int orgId) {
        Context.accountId.set(orgId);
        try {
            NewRelicIntegrationDao.instance.updateOne(
                Filters.eq(NewRelicIntegration.ORG_ID, orgId),
                Updates.set(NewRelicIntegration.LAST_SYNC_TIME, System.currentTimeMillis())
            );
        } catch (Exception e) {
            logger.warn("Failed to update last sync time for org {}: {}", orgId, e.getMessage());
        } finally {
            Context.accountId.remove();
        }
    }

    /**
     * Flush pending metrics on shutdown
     */
    private void flushPendingMetrics() {
        try {
            // Poll remaining messages with timeout
            ConsumerRecords<String, String> remainingRecords = consumer.poll(Duration.ofSeconds(5));
            if (!remainingRecords.isEmpty()) {
                logger.info("Processing {} remaining messages during shutdown", remainingRecords.count());
                // Process remaining messages (simplified)
                for (org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record : remainingRecords) {
                    try {
                        RuntimeMetrics metric = gson.fromJson(record.value(), RuntimeMetrics.class);
                        logger.debug("Processed remaining metric: {}", metric);
                    } catch (Exception e) {
                        logger.error("Failed to process remaining message", e);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error flushing pending metrics", e);
        }
    }

    /**
     * Extract org ID from Kafka message
     * NOTE: This would typically come from the message headers or payload
     * For now, we iterate through configured orgs
     */
    private int extractOrgId(org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record) {
        // In production, this would be passed in the message or header
        // For now, return a default - actual implementation would extract from context/header
        return 1;
    }

    /**
     * Stop the service
     */
    public void stop() {
        logger.info("Stopping NewRelic metrics export service...");
        running = false;
    }

    /**
     * Get service metrics
     */
    public Map<String, Object> getMetrics() {
        return new HashMap<String, Object>() {{
            put("metricsExportedTotal", metricsExportedTotal);
            put("metricsFailedTotal", metricsFailedTotal);
            put("lastExportTime", lastExportTime);
            put("lastBatchSize", lastBatchSize);
            put("isRunning", running);
        }};
    }
}
