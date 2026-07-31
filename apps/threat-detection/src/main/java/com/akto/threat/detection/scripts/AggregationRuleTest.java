package com.akto.threat.detection.scripts;

import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.akto.proto.http_response_param.v1.StringList;
import com.akto.threat.detection.kafka.KafkaProtoProducer;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Pushes exactly 60 messages per API within a single 5-minute tumbling window,
 * from a fixed IP per API — to trigger aggregation rules.
 *
 * APIs:
 *   - POST /api/v1/orders             → status 200, IP 10.99.0.1
 *   - POST /api/v1/payments/checkout  → status 400, IP 10.99.0.2
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="com.akto.threat.detection.scripts.AggregationRuleTest" \
 *     [-Dkafka.url=localhost:9092]
 */
public class AggregationRuleTest {

    private static final String KAFKA_URL  = RealisticTrafficBenchmark.readStringConfig("kafka.url", "KAFKA_URL", "localhost:9092");
    private static final String TOPIC      = RealisticTrafficBenchmark.THREAT_TOPIC;
    private static final String ACCOUNT_ID = "1662680463";
    private static final int    COLLECTION_ID = TrafficCatalog.COLLECTION_ID;

    private static final int MESSAGES_PER_API = 60;
    private static final int WINDOW_MINUTES   = 5;
    private static final int MSG_PER_MINUTE   = MESSAGES_PER_API / WINDOW_MINUTES; // 12

    private static final Random random = new Random(42);

    public static void main(String[] args) throws Exception {
        KafkaConfig kafkaConfig = KafkaConfig.newBuilder()
            .setGroupId("akto.threat_detection")
            .setBootstrapServers(KAFKA_URL)
            .setConsumerConfig(KafkaConsumerConfig.newBuilder()
                .setMaxPollRecords(500)
                .setPollDurationMilli(100)
                .build())
            .setProducerConfig(KafkaProducerConfig.newBuilder()
                .setBatchSize(16384)
                .setLingerMs(0)
                .build())
            .setKeySerializer(Serializer.STRING)
            .setValueSerializer(Serializer.BYTE_ARRAY)
            .build();

        KafkaProtoProducer producer = new KafkaProtoProducer(kafkaConfig);

        // Align to the start of the current 5-minute tumbling window
        long nowMin = System.currentTimeMillis() / 60000;
        long windowStart = (nowMin / WINDOW_MINUTES) * WINDOW_MINUTES;

        System.out.println("Window: [" + windowStart + " .. " + (windowStart + WINDOW_MINUTES - 1) + "]");
        System.out.println("Sending " + MESSAGES_PER_API + " messages per API x 2 APIs = " + (MESSAGES_PER_API * 2) + " total");

        int sent = 0;

        // --- API 1: POST /api/v1/orders, status 200 ---
        for (int min = 0; min < WINDOW_MINUTES; min++) {
            long epochMinute = windowStart + min;
            for (int i = 0; i < MSG_PER_MINUTE; i++) {
                HttpResponseParam msg = buildMessage(
                    "/api/v1/orders", "POST", "10.99.0.1", epochMinute, 200);
                producer.send(TOPIC, msg);
                sent++;
            }
        }
        System.out.println("Sent " + MESSAGES_PER_API + " messages for POST /api/v1/orders (200)");

        // --- API 2: POST /api/v1/payments/checkout, status 400 ---
        for (int min = 0; min < WINDOW_MINUTES; min++) {
            long epochMinute = windowStart + min;
            for (int i = 0; i < MSG_PER_MINUTE; i++) {
                HttpResponseParam msg = buildMessage(
                    "/api/v1/payments/checkout", "POST", "10.99.0.2", epochMinute, 400);
                producer.send(TOPIC, msg);
                sent++;
            }
        }
        System.out.println("Sent " + MESSAGES_PER_API + " messages for POST /api/v1/payments/checkout (400)");

        producer.flush();
        System.out.println("Done. Total sent: " + sent);
        System.out.println("Wait ~1 min for stream consumer + distribution cron to process.");
    }

    private static HttpResponseParam buildMessage(String path, String method,
                                                   String ip, long epochMinute,
                                                   int statusCode) {
        int timeSeconds = (int)(epochMinute * 60) + random.nextInt(60);

        Map<String, StringList> reqHeaders = new HashMap<>();
        reqHeaders.put("content-type",    sl("application/json"));
        reqHeaders.put("host",            sl("apiratelimit.example.com"));
        reqHeaders.put("x-forwarded-for", sl(ip));
        reqHeaders.put("authorization",   sl("Bearer test-token"));

        Map<String, StringList> respHeaders = new HashMap<>();
        respHeaders.put("content-type", sl("application/json"));

        String status = statusCode == 200 ? "OK"
                      : statusCode == 400 ? "Bad Request"
                      : String.valueOf(statusCode);

        return HttpResponseParam.newBuilder()
            .setMethod(method)
            .setPath(path)
            .setType("HTTP/1.1")
            .putAllRequestHeaders(reqHeaders)
            .putAllResponseHeaders(respHeaders)
            .setRequestPayload("{\"test\": true}")
            .setResponsePayload("{\"status\": \"" + status.toLowerCase() + "\"}")
            .setApiCollectionId(COLLECTION_ID)
            .setStatusCode(statusCode)
            .setStatus(status)
            .setTime(timeSeconds)
            .setAktoAccountId(ACCOUNT_ID)
            .setIp(ip)
            .setDestIp("10.0.0.1")
            .setDirection("INBOUND")
            .setIsPending(false)
            .setSource("MIRRORING")
            .setAktoVxlanId("1313121")
            .build();
    }

    private static StringList sl(String value) {
        return StringList.newBuilder().addValues(value).build();
    }
}
