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
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
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
    private static final double EPSILON = 0.01;
    private static final double DELTA = 0.01;
    private static final long TRIM_INTERVAL_MS = 5 * 60 * 1000;
    private static final long MAX_STREAM_LENGTH = 500000;
    private static final int FIELDS_PER_MESSAGE = 6;

    private static final FilterConfig IP_API_RATE_LIMIT_FILTER = Utils.getipApiRateLimitFilter();

    private final StatefulRedisConnection<String, String> connection;
    private final String asyncThreatProcessSha;
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
            try {
                List<StreamMessage<String, String>> messages = connection.sync().xreadgroup(
                    io.lettuce.core.Consumer.from(GROUP_NAME, consumerId),
                    XReadArgs.Builder.count(BATCH_SIZE).block(Duration.ofSeconds(1)),
                    XReadArgs.StreamOffset.lastConsumed(RedisKeyInfo.THREAT_INPUT_STREAM)
                );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                processBatch(messages);

                String[] messageIds = messages.stream()
                    .map(StreamMessage::getId)
                    .toArray(String[]::new);
                connection.sync().xack(RedisKeyInfo.THREAT_INPUT_STREAM, GROUP_NAME, messageIds);

                trimStreamIfNeeded();

            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error in threat stream consumer loop");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        logger.infoAndAddToDb("DistributionStreamConsumer stopped: " + consumerId);
    }

    private void processBatch(List<StreamMessage<String, String>> messages) {
        int headerSize = 6 + bucketRangeArgs.length;
        int totalArgvSize = headerSize + messages.size() * FIELDS_PER_MESSAGE;
        String[] argv = new String[totalArgvSize];

        argv[0] = String.valueOf(messages.size());
        argv[1] = String.valueOf(EPSILON);
        argv[2] = String.valueOf(DELTA);
        argv[3] = String.valueOf(CMS_TTL_SECONDS);
        argv[4] = String.valueOf(DIST_TTL_SECONDS);
        argv[5] = String.valueOf(bucketRangeArgs.length);
        System.arraycopy(bucketRangeArgs, 0, argv, 6, bucketRangeArgs.length);

        int offset = headerSize;
        for (StreamMessage<String, String> msg : messages) {
            Map<String, String> body = msg.getBody();
            argv[offset]     = body.getOrDefault("ipApiCmsKey", "");
            argv[offset + 1] = body.getOrDefault("apiKey", "");
            argv[offset + 2] = body.getOrDefault("minute", "0");
            argv[offset + 3] = body.getOrDefault("rateLimitWindow", "30");
            argv[offset + 4] = body.getOrDefault("threshold", "-1");
            argv[offset + 5] = body.getOrDefault("mitigationPeriod", "300");
            offset += FIELDS_PER_MESSAGE;
        }

        try {
            // EVALSHA returns list of "messageIndex|count" for breaches
            List<String> breachResults = connection.sync().evalsha(
                asyncThreatProcessSha, ScriptOutputType.MULTI, new String[]{}, argv
            );

            if (breachResults != null && !breachResults.isEmpty()) {
                for (String breach : breachResults) {
                    handleBreach(breach, messages);
                }
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error executing async_threat_process Lua script");
        }
    }

    private void handleBreach(String breachData, List<StreamMessage<String, String>> messages) {
        try {
            // Format: "messageIndex|count"
            int pipe = breachData.indexOf('|');
            if (pipe <= 0) {
                logger.errorAndAddToDb("Invalid breach data format: " + breachData);
                return;
            }
            int msgIndex = Integer.parseInt(breachData.substring(0, pipe));
            if (msgIndex < 0 || msgIndex >= messages.size()) {
                logger.errorAndAddToDb("Breach message index out of bounds: " + msgIndex);
                return;
            }

            Map<String, String> body = messages.get(msgIndex).getBody();
            String actor = body.getOrDefault("actor", "");
            String host = body.getOrDefault("host", "");
            String accountId = body.getOrDefault("accountId", "");
            String ipApiCmsKey = body.getOrDefault("ipApiCmsKey", "");
            int timestamp = Integer.parseInt(body.getOrDefault("timestamp", "0"));
            String countryCode = body.getOrDefault("countryCode", "");
            String destCountryCode = body.getOrDefault("destCountryCode", "");

            // Parse apiCollectionId, url, method from ipApiCmsKey
            // Format: ipApiCmsData|{collectionId}|{ip}|{path}|{method}
            String[] parts = ipApiCmsKey.split("\\|", 5);
            if (parts.length < 5) {
                logger.errorAndAddToDb("Invalid ipApiCmsKey format: " + ipApiCmsKey);
                return;
            }
            int apiCollectionId = Integer.parseInt(parts[1]);
            String url = parts[3];
            String method = parts[4];

            String status = com.akto.util.ThreatDetectionConstants.ACTIVE;

            Metadata metadata = Metadata.newBuilder()
                .setCountryCode(countryCode)
                .setDestCountryCode(destCountryCode)
                .build();

            SampleMaliciousRequest maliciousReq = SampleMaliciousRequest.newBuilder()
                .setUrl(url)
                .setMethod(method)
                .setPayload("")
                .setIp(actor)
                .setApiCollectionId(apiCollectionId)
                .setTimestamp(timestamp)
                .setFilterId(IP_API_RATE_LIMIT_FILTER.getId())
                .setSuccessfulExploit(false)
                .setStatus(status)
                .setMetadata(metadata)
                .build();

            MaliciousEventMessage maliciousEvent =
                MaliciousEventMessage.newBuilder()
                    .setFilterId(IP_API_RATE_LIMIT_FILTER.getId())
                    .setActor(actor)
                    .setDetectedAt(timestamp)
                    .setEventType(EventType.EVENT_TYPE_AGGREGATED)
                    .setLatestApiCollectionId(apiCollectionId)
                    .setLatestApiIp(actor)
                    .setLatestApiPayload("")
                    .setLatestApiMethod(method)
                    .setLatestApiEndpoint(url)
                    .setCategory(IP_API_RATE_LIMIT_FILTER.getInfo().getCategory().getName())
                    .setSubCategory(IP_API_RATE_LIMIT_FILTER.getInfo().getSubCategory())
                    .setSeverity(IP_API_RATE_LIMIT_FILTER.getInfo().getSeverity())
                    .setMetadata(maliciousReq.getMetadata())
                    .setType("Anomaly")
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
            logger.debugAndAddToDb("Breach event pushed for actor: " + actor);

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error handling breach: " + breachData);
        }
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
