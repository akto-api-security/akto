package com.akto.threat.detection.ip_api_counter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.akto.dto.monitoring.FilterConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.message.malicious_event.event_type.v1.EventType;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventKafkaEnvelope;
import com.akto.proto.generated.threat_detection.message.malicious_event.v1.MaliciousEventMessage;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.Metadata;
import com.akto.threat.detection.constants.KafkaTopic;
import com.akto.threat.detection.constants.RedisKeyInfo;
import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.threat.detection.utils.Utils;
import com.akto.util.enums.GlobalEnums;
import com.akto.utils.ThreatApiDistributionUtils;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

public class DistributionStreamConsumer implements Runnable {

    private static final LoggerMaker logger = new LoggerMaker(DistributionStreamConsumer.class, LogDb.THREAT_DETECTION);

    private static final String GROUP_NAME = "threat_group";
    private static final int BATCH_SIZE = 500;
    private static final int CMS_TTL_SECONDS = 8 * 60 * 60; // 8 hours
    private static final int DIST_TTL_SECONDS = 8 * 60 * 60; // 8 hours
    private static final double EPSILON = 0.001;
    private static final double DELTA = 0.001;
    private static final long TRIM_INTERVAL_MS = 5 * 60 * 1000;
    private static final long MAX_STREAM_LENGTH = 2000000;
    private static final int FIELDS_PER_MESSAGE = 8; // 6 original + apiLevelWindow + apiLevelThreshold

    private static final FilterConfig IP_API_RATE_LIMIT_FILTER = Utils.getipApiRateLimitFilter();
    private static final FilterConfig API_LEVEL_RATE_LIMIT_FILTER = Utils.getApiLevelRateLimitFilter();

    private final StatefulRedisConnection<String, String> connection;
    private String asyncThreatProcessSha;
    private final String consumerId;
    private final String[] bucketRangeArgs;
    private final KafkaProtoProducer internalKafka;
    private long lastTrimTime = 0;

    public DistributionStreamConsumer(RedisClient redisClient, String consumerId,
                                      KafkaProtoProducer internalKafka) {
        this.connection = redisClient.connect();
        this.consumerId = consumerId;
        this.internalKafka = internalKafka;

        RedisCommands<String, String> sync = connection.sync();

        this.asyncThreatProcessSha = sync.scriptLoad(loadLuaScript("lua/async_threat_process.lua"));

        List<String> ranges = new ArrayList<>();
        for (ThreatApiDistributionUtils.Range r : ThreatApiDistributionUtils.getBucketRanges()) {
            ranges.add(r.label + "," + r.min + "," + r.max);
        }
        this.bucketRangeArgs = ranges.toArray(new String[0]);

        try {
            sync.xgroupCreate(
                XReadArgs.StreamOffset.from(RedisKeyInfo.THREAT_INPUT_STREAM, "0-0"),
                GROUP_NAME,
                XGroupCreateArgs.Builder.mkstream()
            );
            logger.infoAndAddToDb("Created consumer group: " + GROUP_NAME + " on " + RedisKeyInfo.THREAT_INPUT_STREAM);
        } catch (Exception e) {
            if (!e.getMessage().contains("BUSYGROUP")) {
                logger.errorAndAddToDb(e, "Error creating consumer group");
            }
        }

        logger.infoAndAddToDb("DistributionStreamConsumer initialized: " + consumerId);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            List<StreamMessage<String, String>> messages = null;

            // 1. Read from stream
            try {
                messages = connection.sync().xreadgroup(
                    io.lettuce.core.Consumer.from(GROUP_NAME, consumerId),
                    XReadArgs.Builder.count(BATCH_SIZE).block(Duration.ofSeconds(1)),
                    XReadArgs.StreamOffset.lastConsumed(RedisKeyInfo.THREAT_INPUT_STREAM)
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error reading from threat stream (xreadgroup)");
                handleConnectionError();
                continue;
            }

            // 2. Process batch
            try {
                processBatch(messages);
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error processing threat batch");
                // Continue without acknowledging - we'll retry this batch
                handleConnectionError();
                continue;
            }

            // 3. Acknowledge messages
            try {
                String[] messageIds = messages.stream()
                    .map(StreamMessage::getId)
                    .toArray(String[]::new);
                connection.sync().xack(RedisKeyInfo.THREAT_INPUT_STREAM, GROUP_NAME, messageIds);
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error acknowledging messages (xack)");
                handleConnectionError();
                // Don't continue - messages weren't acked, will be reprocessed
                continue;
            }

            // 4. Trim stream
            try {
                trimStreamIfNeeded();
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error trimming stream");
                // Non-critical, continue
            }
        }
        logger.infoAndAddToDb("DistributionStreamConsumer stopped: " + consumerId);
    }

    private void handleConnectionError() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Attempts to reload the Lua script if it was flushed from Redis.
     * Handles NOSCRIPT errors by re-loading the script.
     */
    private void reloadLuaScriptIfNeeded(Exception e) {
        if (e != null && e.getMessage() != null && e.getMessage().contains("NOSCRIPT")) {
            try {
                logger.infoAndAddToDb("Reloading Lua script due to NOSCRIPT error");
                this.asyncThreatProcessSha = connection.sync()
                    .scriptLoad(loadLuaScript("lua/async_threat_process.lua"));
                logger.infoAndAddToDb("Lua script reloaded successfully");
            } catch (Exception reloadEx) {
                logger.errorAndAddToDb(reloadEx, "Failed to reload Lua script");
            }
        }
    }

    /**
     * Build a flat ARGV array for the Lua script and execute via EVALSHA.
     *
     * Layout: [ HEADER (6 fixed fields) | BUCKET_RANGES (variable) | MESSAGE_0 | MESSAGE_1 | ... ]
     *
     * HEADER:
     *   [0] messageCount    — number of messages in this batch
     *   [1] epsilon          — CMS error rate (e.g. 0.01)
     *   [2] delta            — CMS confidence (e.g. 0.01)
     *   [3] cmsTtl           — TTL for CMS keys in seconds
     *   [4] distTtl          — TTL for distribution hash keys in seconds
     *   [5] bucketCount      — number of bucket range entries that follow
     *
     * BUCKET_RANGES (at indices 6 .. 6+bucketCount-1):
     *   Each entry is "label,min,max" e.g. "b1,1,10", "b2,11,50", ...
     *
     * MESSAGES (at indices headerSize .. end, each FIELDS_PER_MESSAGE wide):
     *   [+0] ipApiCmsKey      — CMS item key (e.g. "ipApiCmsData|acctId|ip|url|method")
     *   [+1] apiKey           — distribution key (e.g. "acctId|url|method")
     *   [+2] minute           — current epoch minute
     *   [+3] rateLimitWindow  — sliding window size in minutes
     *   [+4] threshold        — rate limit threshold (-1 = no check)
     *   [+5] mitigationPeriod — cooldown period in seconds after breach
     */
    private void processBatch(List<StreamMessage<String, String>> messages) {
        int headerSize = 6 + bucketRangeArgs.length;
        int totalArgvSize = headerSize + messages.size() * FIELDS_PER_MESSAGE;
        String[] argv = new String[totalArgvSize];

        // Fixed header fields
        argv[0] = String.valueOf(messages.size());
        argv[1] = String.valueOf(EPSILON);
        argv[2] = String.valueOf(DELTA);
        argv[3] = String.valueOf(CMS_TTL_SECONDS);
        argv[4] = String.valueOf(DIST_TTL_SECONDS);
        argv[5] = String.valueOf(bucketRangeArgs.length);

        // Bucket ranges (variable length, e.g. ["b1,1,10", "b2,11,50", ...])
        System.arraycopy(bucketRangeArgs, 0, argv, 6, bucketRangeArgs.length);

        // Per-message fields, packed sequentially after the header
        int offset = headerSize;
        for (StreamMessage<String, String> msg : messages) {
            Map<String, String> body = msg.getBody();
            argv[offset]     = body.getOrDefault("ipApiCmsKey", "");
            argv[offset + 1] = body.getOrDefault("apiKey", "");
            argv[offset + 2] = body.getOrDefault("minute", "0");
            argv[offset + 3] = body.getOrDefault("rateLimitWindow", "30");
            argv[offset + 4] = body.getOrDefault("threshold", "-1");
            argv[offset + 5] = body.getOrDefault("mitigationPeriod", "300");
            argv[offset + 6] = body.getOrDefault("apiLevelWindow", "0");
            argv[offset + 7] = body.getOrDefault("apiLevelThreshold", "0");
            offset += FIELDS_PER_MESSAGE;
        }

        try {
            // EVALSHA returns [{ipRateBreaches}, {apiCountBreaches}], each a list of "messageIndex|count"
            List<List<String>> results = connection.sync().evalsha(
                asyncThreatProcessSha, ScriptOutputType.MULTI, new String[]{}, argv
            );

            if (results != null && results.size() >= 1) {
                List<String> ipBreaches = results.get(0);
                if (ipBreaches != null) {
                    for (String breach : ipBreaches) {
                        handleBreach(breach, messages);
                    }
                }
            }
            if (results != null && results.size() >= 2) {
                List<String> apiCountBreaches = results.get(1);
                if (apiCountBreaches != null) {
                    for (String breach : apiCountBreaches) {
                        handleApiCountBreach(breach, messages);
                    }
                }
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error executing async_threat_process Lua script");
            // Try to reload the script if it was flushed from Redis
            reloadLuaScriptIfNeeded(e);
        }
    }

    private void handleBreach(String breachData, List<StreamMessage<String, String>> messages) {
        try {
            Map<String, String> body = parseBreachMessage(breachData, messages);
            if (body == null) return;

            String ipApiCmsKey = body.getOrDefault("ipApiCmsKey", "");
            // Format: ipApiCmsData|{collectionId}|{ip}|{path}|{method}
            String[] parts = ipApiCmsKey.split("\\|", 5);
            if (parts.length < 5) {
                logger.errorAndAddToDb("Invalid ipApiCmsKey format: " + ipApiCmsKey);
                return;
            }
            int apiCollectionId = Integer.parseInt(parts[1]);
            String url = parts[3];
            String method = parts[4];

            pushBreachEvent(body, apiCollectionId, url, method, IP_API_RATE_LIMIT_FILTER, "Anomaly");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error handling breach: " + breachData);
        }
    }

    private void handleApiCountBreach(String breachData, List<StreamMessage<String, String>> messages) {
        try {
            Map<String, String> body = parseBreachMessage(breachData, messages);
            if (body == null) return;

            // apiKey format: {collectionId}|{url}|{method}
            String apiKey = body.getOrDefault("apiKey", "");
            String[] parts = apiKey.split("\\|", 3);
            if (parts.length < 3) {
                logger.errorAndAddToDb("Invalid apiKey format in api count breach: " + apiKey);
                return;
            }
            int apiCollectionId = Integer.parseInt(parts[0]);
            String url = parts[1];
            String method = parts[2];

            pushBreachEvent(body, apiCollectionId, url, method, API_LEVEL_RATE_LIMIT_FILTER, "Rule-Based");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error handling api count breach: " + breachData);
        }
    }

    // Returns the message body for the given breach string, or null if invalid.
    private Map<String, String> parseBreachMessage(String breachData, List<StreamMessage<String, String>> messages) {
        int pipe = breachData.indexOf('|');
        if (pipe <= 0) {
            logger.errorAndAddToDb("Invalid breach data format: " + breachData);
            return null;
        }
        int msgIndex = Integer.parseInt(breachData.substring(0, pipe));
        if (msgIndex < 0 || msgIndex >= messages.size()) {
            logger.errorAndAddToDb("Breach message index out of bounds: " + msgIndex);
            return null;
        }
        return messages.get(msgIndex).getBody();
    }

    private void pushBreachEvent(Map<String, String> body, int apiCollectionId, String url, String method,
                                 FilterConfig filter, String detectionType) {
        String actor = body.getOrDefault("actor", "");
        String host = body.getOrDefault("host", "");
        String accountId = body.getOrDefault("accountId", "");
        int timestamp = Integer.parseInt(body.getOrDefault("timestamp", "0"));
        String countryCode = body.getOrDefault("countryCode", "");
        String destCountryCode = body.getOrDefault("destCountryCode", "");
        String status = com.akto.util.ThreatDetectionConstants.ACTIVE;

        Metadata metadata = Metadata.newBuilder()
            .setCountryCode(countryCode)
            .setDestCountryCode(destCountryCode)
            .build();

        MaliciousEventMessage maliciousEvent =
            MaliciousEventMessage.newBuilder()
                .setFilterId(filter.getId())
                .setActor(actor)
                .setDetectedAt(timestamp)
                .setEventType(EventType.EVENT_TYPE_AGGREGATED)
                .setLatestApiCollectionId(apiCollectionId)
                .setLatestApiIp(actor)
                .setLatestApiPayload("")
                .setLatestApiMethod(method)
                .setLatestApiEndpoint(url)
                .setCategory(filter.getInfo().getCategory().getName())
                .setSubCategory(filter.getInfo().getSubCategory())
                .setSeverity(filter.getInfo().getSeverity())
                .setMetadata(metadata)
                .setType(detectionType)
                .setSuccessfulExploit(false)
                .setStatus(status)
                .setHost(host)
                .setContextSource(GlobalEnums.CONTEXT_SOURCE.API.name())
                .setSessionId("")
                .build();

        MaliciousEventKafkaEnvelope envelope =
            MaliciousEventKafkaEnvelope.newBuilder()
                .setActor(actor)
                .setAccountId(accountId)
                .setMaliciousEvent(maliciousEvent)
                .build();

        internalKafka.send(KafkaTopic.ThreatDetection.ALERTS, envelope);
        logger.debugAndAddToDb("Breach event pushed for actor: " + actor + " filter: " + filter.getId());
    }

    private void trimStreamIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastTrimTime > TRIM_INTERVAL_MS) {
            try {
                connection.async().xtrim(RedisKeyInfo.THREAT_INPUT_STREAM, true, MAX_STREAM_LENGTH);
                lastTrimTime = now;
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error trimming stream");
            }
        }
    }

    private static String loadLuaScript(String resourcePath) {
        try (InputStream is = DistributionStreamConsumer.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Lua script not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Lua script: " + resourcePath, e);
        }
    }
}
