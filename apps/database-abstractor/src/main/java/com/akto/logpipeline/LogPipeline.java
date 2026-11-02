package com.akto.logpipeline;

import com.akto.dto.Log;
import com.akto.dto.LogsEndpointShield;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.metrics.MetricData;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

import java.util.List;

/**
 * Producer-side facade used by the API handlers.
 *
 * Usage in a handler:
 * <pre>
 *   if (LogPipeline.isEnabled()) {
 *       LogPipeline.publishLog(...);          // never blocks, never throws
 *   } else {
 *       ... legacy direct Mongo write ...
 *   }
 * </pre>
 *
 * Messages use the same envelope as the existing pipeline: {triggerMethod, accountId, payload}.
 */
public final class LogPipeline {

    private LogPipeline() {}

    // triggerMethod values (also the consumer's dispatch keys)
    public static final String RUNTIME_LOG = "insertRuntimeLog";
    public static final String TESTING_LOG = "insertTestingLog";
    public static final String PROTECTION_LOG = "insertProtectionLog";
    public static final String ENDPOINT_SHIELD_LOG = "insertEndpointShieldLog";
    public static final String TRAFFIC_METRICS = "bulkWriteTrafficMetrics";
    public static final String METRICS_DATA = "ingestMetricsData";
    public static final String ANALYSER_LOG = "insertAnalyserLog";
    public static final String DATA_INGESTION_LOG = "insertDataIngestionLog";
    public static final String AGENTIC_TESTING_LOG = "insertAgenticTestingLog";
    public static final String PUPPETEER_LOG = "insertPuppeteerLog";
    public static final String SMART_TESTING_LOG = "insertSmartTestingLog";
    public static final String DAST_LOG = "insertDastLog";
    public static final String MINI_TESTING_ORCHESTRATOR_LOG = "insertMiniTestingOrchestratorLog";

    private static final Gson gson = new Gson();
    // MetricData has enums + nested ModuleInfo: use Jackson (same family the Struts JSON layer used to read it), skip nulls.
    private static final ObjectMapper metricMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public static boolean isEnabled() {
        return LogPipelineConfig.ENABLED;
    }

    /** Runtime / testing / protection log. {@code persist} is only meaningful for runtime logs. */
    public static void publishLog(String triggerMethod, int accountId, Log log, boolean persist) {
        try {
            BasicDBObject payload = new BasicDBObject();
            payload.put("log", log.getLog());
            payload.put("key", log.getKey());
            payload.put("timestamp", log.getTimestamp());
            if (log.getActivityId() != null) {
                payload.put("activityId", log.getActivityId());
            }
            if (log.getActivityType() != null) {
                payload.put("activityType", log.getActivityType().name());
            }
            BasicDBObject envelope = envelope(triggerMethod, accountId, payload.toJson());
            if (persist) {
                envelope.put("persist", true);
            }
            LogPipelineProducer.getInstance().offer(LogPipelineConfig.TOPIC_LOGS, envelope.toJson());
        } catch (Throwable t) {
            // never propagate into the API handler
        }
    }

    public static void publishEndpointShieldLog(int accountId, LogsEndpointShield log) {
        try {
            BasicDBObject payload = new BasicDBObject();
            payload.put("agentId", log.getAgentId());
            payload.put("deviceId", log.getDeviceId());
            payload.put("key", log.getKey());
            payload.put("level", log.getLevel());
            payload.put("log", log.getLog());
            payload.put("timestamp", log.getTimestamp());
            BasicDBObject envelope = envelope(ENDPOINT_SHIELD_LOG, accountId, payload.toJson());
            LogPipelineProducer.getInstance().offer(LogPipelineConfig.TOPIC_LOGS, envelope.toJson());
        } catch (Throwable t) {
            // never propagate into the API handler
        }
    }

    public static void publishTrafficMetrics(int accountId, List<BulkUpdates> writes) {
        try {
            if (writes == null || writes.isEmpty()) {
                return;
            }
            BasicDBObject envelope = envelope(TRAFFIC_METRICS, accountId, gson.toJson(writes));
            LogPipelineProducer.getInstance().offer(LogPipelineConfig.TOPIC_METRICS, envelope.toJson());
        } catch (Throwable t) {
            // never propagate into the API handler
        }
    }

    /** Storage of ingestMetricsData. Forwarding to New Relic/OTel stays in the request path (DbLayer.forwardMetricsData). */
    public static void publishMetricsData(int accountId, List<MetricData> metrics) {
        try {
            if (metrics == null || metrics.isEmpty()) {
                return;
            }
            BasicDBObject envelope = envelope(METRICS_DATA, accountId, metricMapper.writeValueAsString(metrics));
            LogPipelineProducer.getInstance().offer(LogPipelineConfig.TOPIC_METRICS, envelope.toJson());
        } catch (Throwable t) {
            // never propagate into the API handler
        }
    }

    private static BasicDBObject envelope(String triggerMethod, int accountId, String payloadJson) {
        BasicDBObject obj = new BasicDBObject();
        obj.put("triggerMethod", triggerMethod);
        obj.put("accountId", accountId);
        obj.put("payload", payloadJson);
        return obj;
    }
}
