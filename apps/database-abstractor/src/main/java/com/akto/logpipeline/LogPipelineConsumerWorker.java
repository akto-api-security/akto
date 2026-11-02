package com.akto.logpipeline;

import com.akto.dao.AgenticTestingLogsDao;
import com.akto.dao.AnalyserLogsDao;
import com.akto.dao.DastLogsDao;
import com.akto.dao.DataIngestionLogsDao;
import com.akto.dao.LogsDao;
import com.akto.dao.MiniTestingOrchestratorLogsDao;
import com.akto.dao.PupeteerLogsDao;
import com.akto.dao.SmartTestingLogsDao;
import com.akto.dao.LogsEndpointShieldDao;
import com.akto.dao.ProtectionLogsDao;
import com.akto.dao.RuntimeLogsDao;
import com.akto.dao.RuntimePersistentLogsDao;
import com.akto.dao.context.Context;
import com.akto.dao.metrics.MetricDataDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.Log;
import com.akto.dto.LogsEndpointShield;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.metrics.MetricData;
import com.akto.dto.traffic_metrics.TrafficMetrics;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.InsertManyOptions;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One Kafka consumer thread of the log pipeline. Run several (threads and/or processes) with the
 * same group id to scale out: Kafka splits the topic's partitions between them.
 *
 * Delivery semantics differ per kind on purpose:
 *  - LOGS    : write first, then commit (at-least-once). A crash replays a batch; duplicate log
 *              lines are harmless. If Mongo fails the offsets are rewound so nothing is lost.
 *  - METRICS : commit first, then write (at-most-once). Traffic metrics are $inc upserts, so a
 *              replay would double-count; losing a batch on a crash is the lesser evil.
 *
 * Deliberately avoids DbLayer / LoggerMaker: LoggerMaker writes log rows to the DB and would feed
 * itself. All Mongo access goes straight through the DAOs of the DB this process is pointed at
 * (AKTO_MONGO_CONN => the NEW logs database).
 */
public class LogPipelineConsumerWorker implements Runnable {

    public enum Kind { LOGS, METRICS }

    private static final Logger logger = LoggerFactory.getLogger(LogPipelineConsumerWorker.class);
    private static final Gson gson = new Gson();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ObjectMapper metricMapper = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // (accountId + target) combos whose collections have already been created/indexed in this process
    private static final Set<String> ensured = ConcurrentHashMap.newKeySet();

    private static final AtomicLong written = new AtomicLong();
    private static final AtomicLong skipped = new AtomicLong();
    private static final AtomicLong writeErrors = new AtomicLong();

    private enum Target {
        RUNTIME, RUNTIME_PERSISTENT, TESTING, PROTECTION, ENDPOINT_SHIELD,
        ANALYSER, DATA_INGESTION, AGENTIC_TESTING, PUPPETEER, SMART_TESTING, DAST, MINI_TESTING_ORCHESTRATOR
    }

    private final Kind kind;
    private final String topic;
    private final String groupId;
    private final int index;
    private volatile KafkaConsumer<String, String> consumer;
    private volatile boolean running = true;
    private boolean pausedApplied = false;

    public LogPipelineConsumerWorker(Kind kind, int index) {
        this.kind = kind;
        this.index = index;
        this.topic = kind == Kind.LOGS ? LogPipelineConfig.TOPIC_LOGS : LogPipelineConfig.TOPIC_METRICS;
        this.groupId = kind == Kind.LOGS ? LogPipelineConfig.GROUP_ID_LOGS : LogPipelineConfig.GROUP_ID_METRICS;
    }

    public void shutdown() {
        running = false;
        KafkaConsumer<String, String> c = consumer;
        if (c != null) {
            c.wakeup();
        }
    }

    @Override
    public void run() {
        Thread.currentThread().setName("log-consumer-" + kind.name().toLowerCase() + "-" + index);
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, LogPipelineConfig.BROKER_URL);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, LogPipelineConfig.MAX_POLL_RECORDS);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, CooperativeStickyAssignor.class.getName());
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300_000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 45_000);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "log-consumer-" + kind.name().toLowerCase() + "-" + index);

        try (KafkaConsumer<String, String> c = new KafkaConsumer<>(props)) {
            this.consumer = c;
            c.subscribe(java.util.Collections.singletonList(topic));
            logger.info("log consumer started: kind={} topic={} group={} index={}", kind, topic, groupId, index);

            while (running) {
                try {
                    applyPauseState(c);
                    ConsumerRecords<String, String> records = c.poll(Duration.ofMillis(500));
                    if (records.isEmpty()) {
                        continue;
                    }
                    handleBatch(c, records);
                } catch (WakeupException we) {
                    if (!running) {
                        break;
                    }
                } catch (Throwable t) {
                    logger.error("log consumer loop error ({}): {}", kind, t.toString(), t);
                    sleepQuietly(2000);
                }
            }
        } catch (Throwable t) {
            logger.error("log consumer ({}) failed to start/stop cleanly: {}", kind, t.toString(), t);
        }
        logger.info("log consumer stopped: kind={} index={}", kind, index);
    }

    /** Pause = stop fetching but keep polling so the group does not rebalance. Kafka keeps buffering. */
    private void applyPauseState(KafkaConsumer<String, String> c) {
        boolean paused = LogPipelineConfig.isConsumerPaused();
        Set<TopicPartition> assigned = c.assignment();
        if (paused) {
            // re-apply every loop: partitions assigned by a rebalance after the pause are not paused yet
            if (!assigned.isEmpty()) {
                c.pause(assigned);
            }
            if (!pausedApplied) {
                logger.warn("log consumer {} PAUSED", kind);
                pausedApplied = true;
            }
        } else if (pausedApplied) {
            c.resume(c.paused());
            logger.warn("log consumer {} RESUMED", kind);
            pausedApplied = false;
        }
    }

    private void handleBatch(KafkaConsumer<String, String> c, ConsumerRecords<String, String> records) {
        if (kind == Kind.METRICS) {
            // at-most-once: commit before write
            try {
                c.commitSync();
            } catch (Exception e) {
                logger.error("commit failed before metrics write: {}", e.toString());
            }
            try {
                writeMetrics(records);
            } catch (Throwable t) {
                writeErrors.incrementAndGet();
                logger.error("metrics batch write failed (batch lost, at-most-once): {}", t.toString(), t);
            }
            return;
        }

        // LOGS: at-least-once. write, then commit. On failure rewind so the batch is re-polled.
        try {
            writeLogs(records);
            c.commitSync();
        } catch (Throwable t) {
            writeErrors.incrementAndGet();
            logger.error("logs batch write failed; rewinding and retrying: {}", t.toString());
            try {
                for (TopicPartition tp : records.partitions()) {
                    List<ConsumerRecord<String, String>> recs = records.records(tp);
                    if (!recs.isEmpty()) {
                        c.seek(tp, recs.get(0).offset());
                    }
                }
            } catch (Throwable seekErr) {
                logger.error("seek after failure failed: {}", seekErr.toString());
            }
            sleepQuietly(2000);
        }
    }

    // ------------------------------------------------------------------ logs

    private void writeLogs(ConsumerRecords<String, String> records) {
        // accountId -> target -> rows
        Map<Integer, Map<Target, List<Object>>> batches = new HashMap<>();

        for (ConsumerRecord<String, String> r : records) {
            try {
                Map<String, Object> env = gson.fromJson(r.value(), Map.class);
                String trigger = (String) env.get("triggerMethod");
                int accountId = ((Number) env.get("accountId")).intValue();
                String payload = (String) env.get("payload");
                boolean persist = Boolean.TRUE.equals(env.get("persist"));
                Map<String, Object> p = gson.fromJson(payload, Map.class);

                Map<Target, List<Object>> forAccount = batches.computeIfAbsent(accountId, k -> new HashMap<>());
                switch (trigger) {
                    case LogPipeline.RUNTIME_LOG: {
                        Log l = toLog(p);
                        forAccount.computeIfAbsent(Target.RUNTIME, k -> new ArrayList<>()).add(l);
                        if (persist) {
                            forAccount.computeIfAbsent(Target.RUNTIME_PERSISTENT, k -> new ArrayList<>()).add(toLog(p));
                        }
                        break;
                    }
                    case LogPipeline.TESTING_LOG:
                        forAccount.computeIfAbsent(Target.TESTING, k -> new ArrayList<>()).add(toLog(p));
                        break;
                    case LogPipeline.PROTECTION_LOG:
                        forAccount.computeIfAbsent(Target.PROTECTION, k -> new ArrayList<>()).add(toLog(p));
                        break;
                    case LogPipeline.ENDPOINT_SHIELD_LOG:
                        forAccount.computeIfAbsent(Target.ENDPOINT_SHIELD, k -> new ArrayList<>()).add(toShieldLog(p));
                        break;
                    case LogPipeline.ANALYSER_LOG:
                        forAccount.computeIfAbsent(Target.ANALYSER, k -> new ArrayList<>()).add(toLog(p));
                        break;
                    case LogPipeline.DATA_INGESTION_LOG:
                        forAccount.computeIfAbsent(Target.DATA_INGESTION, k -> new ArrayList<>()).add(toLog(p));
                        break;
                    case LogPipeline.AGENTIC_TESTING_LOG:
                        forAccount.computeIfAbsent(Target.AGENTIC_TESTING, k -> new ArrayList<>()).add(toLog(p));
                        break;
                    case LogPipeline.PUPPETEER_LOG:
                        forAccount.computeIfAbsent(Target.PUPPETEER, k -> new ArrayList<>()).add(toLog(p));
                        break;
                    case LogPipeline.SMART_TESTING_LOG:
                        forAccount.computeIfAbsent(Target.SMART_TESTING, k -> new ArrayList<>()).add(toLog(p));
                        break;
                    case LogPipeline.DAST_LOG:
                        forAccount.computeIfAbsent(Target.DAST, k -> new ArrayList<>()).add(toLog(p));
                        break;
                    case LogPipeline.MINI_TESTING_ORCHESTRATOR_LOG:
                        forAccount.computeIfAbsent(Target.MINI_TESTING_ORCHESTRATOR, k -> new ArrayList<>()).add(toLog(p));
                        break;
                    default:
                        skipped.incrementAndGet();
                }
            } catch (Throwable t) {
                skipped.incrementAndGet();
                logger.warn("skipping unparseable log record: {}", t.toString());
            }
        }

        InsertManyOptions unordered = new InsertManyOptions().ordered(false);
        for (Map.Entry<Integer, Map<Target, List<Object>>> accountEntry : batches.entrySet()) {
            int accountId = accountEntry.getKey();
            Context.accountId.set(accountId);
            for (Map.Entry<Target, List<Object>> targetEntry : accountEntry.getValue().entrySet()) {
                Target target = targetEntry.getKey();
                List<Object> rows = targetEntry.getValue();
                if (rows.isEmpty()) {
                    continue;
                }
                ensureCollection(accountId, target);
                insert(target, rows, unordered);
                written.addAndGet(rows.size());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void insert(Target target, List<Object> rows, InsertManyOptions opts) {
        switch (target) {
            case RUNTIME:
                RuntimeLogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case RUNTIME_PERSISTENT:
                RuntimePersistentLogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case TESTING:
                LogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case PROTECTION:
                ProtectionLogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case ENDPOINT_SHIELD:
                LogsEndpointShieldDao.instance.insertMany((List<LogsEndpointShield>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case ANALYSER:
                AnalyserLogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case DATA_INGESTION:
                DataIngestionLogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case AGENTIC_TESTING:
                AgenticTestingLogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case PUPPETEER:
                PupeteerLogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case SMART_TESTING:
                SmartTestingLogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case DAST:
                DastLogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            case MINI_TESTING_ORCHESTRATOR:
                MiniTestingOrchestratorLogsDao.instance.insertMany((List<Log>) (List<?>) rows, WriteConcern.W1, opts);
                break;
            default:
                break;
        }
    }

    /** Creates the (capped) collection + indexes the same way the main DB does, once per account/target. */
    private static void ensureCollection(int accountId, Target target) {
        String key = accountId + ":" + target.name();
        if (ensured.contains(key)) {
            return;
        }
        try {
            switch (target) {
                case RUNTIME: RuntimeLogsDao.instance.createIndicesIfAbsent(); break;
                case RUNTIME_PERSISTENT: RuntimePersistentLogsDao.instance.createIndicesIfAbsent(); break;
                case TESTING: LogsDao.instance.createIndicesIfAbsent(); break;
                case PROTECTION: ProtectionLogsDao.instance.createIndicesIfAbsent(); break;
                case ENDPOINT_SHIELD: LogsEndpointShieldDao.instance.createIndicesIfAbsent(); break;
                case ANALYSER: AnalyserLogsDao.instance.createIndicesIfAbsent(); break;
                case DATA_INGESTION: DataIngestionLogsDao.instance.createIndicesIfAbsent(); break;
                case AGENTIC_TESTING: AgenticTestingLogsDao.instance.createIndicesIfAbsent(); break;
                case PUPPETEER: PupeteerLogsDao.instance.createIndicesIfAbsent(); break;
                case SMART_TESTING: SmartTestingLogsDao.instance.createIndicesIfAbsent(); break;
                case DAST: DastLogsDao.instance.createIndicesIfAbsent(); break;
                case MINI_TESTING_ORCHESTRATOR: MiniTestingOrchestratorLogsDao.instance.createIndicesIfAbsent(); break;
                default: break;
            }
            ensured.add(key);
        } catch (Throwable t) {
            // not fatal: the insert will auto-create an uncapped collection. Retry next batch.
            logger.warn("ensureCollection failed for {}: {}", key, t.toString());
        }
    }

    private static Log toLog(Map<String, Object> p) {
        Log l = new Log((String) p.get("log"), (String) p.get("key"), ((Number) p.get("timestamp")).intValue());
        Object activityId = p.get("activityId");
        if (activityId != null) {
            l.setActivityId((String) activityId);
        }
        Object activityType = p.get("activityType");
        if (activityType != null) {
            try {
                l.setActivityType(Log.ActivityType.valueOf((String) activityType));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return l;
    }

    private static LogsEndpointShield toShieldLog(Map<String, Object> p) {
        return new LogsEndpointShield(
                (String) p.get("agentId"), (String) p.get("deviceId"), (String) p.get("key"),
                (String) p.get("level"), (String) p.get("log"), ((Number) p.get("timestamp")).intValue());
    }

    // --------------------------------------------------------------- metrics

    /** One merged traffic-metrics upsert: same filter => $inc values summed (identical end result, far fewer writes). */
    private static final class Merged {
        final Map<String, Object> filters;
        final Map<String, Integer> incs = new LinkedHashMap<>();

        Merged(Map<String, Object> filters) {
            this.filters = filters;
        }
    }

    private void writeMetrics(ConsumerRecords<String, String> records) throws Exception {
        // accountId -> canonicalFilterKey -> merged update
        Map<Integer, Map<String, Merged>> perAccount = new HashMap<>();
        Map<Integer, List<MetricData>> metricDataByAccount = new HashMap<>();

        for (ConsumerRecord<String, String> r : records) {
            try {
                Map<String, Object> env = gson.fromJson(r.value(), Map.class);
                Object trigger = env.get("triggerMethod");
                if (LogPipeline.METRICS_DATA.equals(trigger)) {
                    int acc = ((Number) env.get("accountId")).intValue();
                    List<MetricData> md = metricMapper.readValue((String) env.get("payload"), new TypeReference<List<MetricData>>() {});
                    metricDataByAccount.computeIfAbsent(acc, k -> new ArrayList<>()).addAll(md);
                    continue;
                }
                if (!LogPipeline.TRAFFIC_METRICS.equals(trigger)) {
                    skipped.incrementAndGet();
                    continue;
                }
                int accountId = ((Number) env.get("accountId")).intValue();
                List<BulkUpdates> updates = mapper.readValue((String) env.get("payload"), new TypeReference<List<BulkUpdates>>() {});
                Map<String, Merged> merged = perAccount.computeIfAbsent(accountId, k -> new LinkedHashMap<>());
                for (BulkUpdates bu : updates) {
                    Map<String, Object> filters = normalizeFilters(bu.getFilters());
                    String key = new TreeMap<>(filters).toString();
                    Merged m = merged.computeIfAbsent(key, k -> new Merged(filters));
                    for (String payload : bu.getUpdates()) {
                        Map<String, Object> json = gson.fromJson(payload, Map.class);
                        String field = (String) json.get("field");
                        int val = ((Double) json.get("val")).intValue();
                        m.incs.merge(field, val, Integer::sum);
                    }
                }
            } catch (Throwable t) {
                skipped.incrementAndGet();
                logger.warn("skipping unparseable metrics record: {}", t.toString());
            }
        }

        for (Map.Entry<Integer, Map<String, Merged>> accountEntry : perAccount.entrySet()) {
            int accountId = accountEntry.getKey();
            Context.accountId.set(accountId);
            ensureMetricsCollection(accountId);

            List<WriteModel<TrafficMetrics>> writes = new ArrayList<>();
            for (Merged m : accountEntry.getValue().values()) {
                Bson filter = Filters.empty();
                for (Map.Entry<String, Object> f : m.filters.entrySet()) {
                    filter = Filters.and(filter, Filters.eq(f.getKey(), f.getValue()));
                }
                List<Bson> incs = new ArrayList<>();
                for (Map.Entry<String, Integer> inc : m.incs.entrySet()) {
                    incs.add(Updates.inc(inc.getKey(), inc.getValue()));
                }
                writes.add(new UpdateOneModel<>(filter, Updates.combine(incs), new UpdateOptions().upsert(true)));
            }
            // chunk so a huge poll does not become one giant command
            for (int i = 0; i < writes.size(); i += 1000) {
                List<WriteModel<TrafficMetrics>> chunk = writes.subList(i, Math.min(i + 1000, writes.size()));
                TrafficMetricsDao.instance.getMCollection().bulkWrite(chunk, new BulkWriteOptions().ordered(false));
            }
            written.addAndGet(writes.size());
        }

        // ingestMetricsData -> metrics_data (cleanup + insert, same as DbLayer.storeMetricsData but against the logs DB)
        for (Map.Entry<Integer, List<MetricData>> e : metricDataByAccount.entrySet()) {
            int accountId = e.getKey();
            Context.accountId.set(accountId);
            ensureMetricDataCollection(accountId);
            try {
                if (MetricDataDao.instance.shouldPerformCleanup()) {
                    MetricDataDao.instance.deleteOldMetrics();
                }
            } catch (Throwable t) {
                // e.g. capped collections do not allow deletes; the cap already bounds the size
                logger.debug("metrics_data cleanup skipped: {}", t.toString());
            }
            MetricDataDao.instance.insertMany(e.getValue(), WriteConcern.W1, new InsertManyOptions().ordered(false));
            written.addAndGet(e.getValue().size());
        }
    }

    private static void ensureMetricDataCollection(int accountId) {
        String key = accountId + ":METRIC_DATA";
        if (ensured.contains(key)) {
            return;
        }
        try {
            MetricDataDao.instance.createIndicesIfAbsent();
            ensured.add(key);
        } catch (Throwable t) {
            logger.warn("ensureCollection failed for {}: {}", key, t.toString());
        }
    }

    /** Same type coercion the API handler applies (these three key parts must be ints to match stored _id). */
    private static Map<String, Object> normalizeFilters(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String k = e.getKey();
            if (k.equalsIgnoreCase("_id.bucketStartEpoch") || k.equalsIgnoreCase("_id.bucketEndEpoch") || k.equalsIgnoreCase("_id.vxlanID")) {
                out.put(k, Integer.valueOf(e.getValue().toString()));
            } else {
                out.put(k, e.getValue());
            }
        }
        return out;
    }

    private static void ensureMetricsCollection(int accountId) {
        String key = accountId + ":TRAFFIC_METRICS";
        if (ensured.contains(key)) {
            return;
        }
        try {
            TrafficMetricsDao.instance.createIndicesIfAbsent();
            ensured.add(key);
        } catch (Throwable t) {
            logger.warn("ensureCollection failed for {}: {}", key, t.toString());
        }
    }

    // ------------------------------------------------------------------ misc

    static long writtenCount() { return written.get(); }
    static long skippedCount() { return skipped.get(); }
    static long errorCount() { return writeErrors.get(); }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
