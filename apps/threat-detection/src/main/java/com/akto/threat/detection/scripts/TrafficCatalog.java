package com.akto.threat.detection.scripts;

import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.akto.proto.http_response_param.v1.StringList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Single source of truth for the E2E traffic simulation.
 *
 * Contains:
 *   - ApiProfile: describes one API endpoint and its traffic shape
 *   - IP pool builder: Zipf-split across 4 tiers
 *   - MessageBuilder: builds HttpResponseParam proto given an IP, tier, and epoch minute
 *
 * No Kafka, no Redis. Pure data and construction logic.
 */
public class TrafficCatalog {

    public static final int COLLECTION_ID = 1000;

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    // -------------------------------------------------------------------------
    // Tier definitions — maps to distribution buckets b1-b14
    // -------------------------------------------------------------------------

    public enum Tier {
        // ~60% of IPs: casual users, 1-8 calls/min → b1-b2 over a 5-min window
        CASUAL(1, 8),
        // ~25% of IPs: regular users, 10-40 calls/min → b3-b4 over a 5-min window
        REGULAR(10, 40),
        // ~12% of IPs: power users, 80-500 calls/min → b5-b9 over a 5-min window
        POWER(80, 500),
        // ~3% of IPs: anomalous/bot-like, 2000-8000 calls/min → b11-b14 over a 5-min window
        ANOMALOUS(2000, 8000);

        public final int minCallsPerMin;
        public final int maxCallsPerMin;

        Tier(int min, int max) {
            this.minCallsPerMin = min;
            this.maxCallsPerMin = max;
        }
    }

    // -------------------------------------------------------------------------
    // IpEntry: an IP address with its assigned tier
    // -------------------------------------------------------------------------

    public static class IpEntry {
        public final String ip;
        public final Tier tier;

        public IpEntry(String ip, Tier tier) {
            this.ip = ip;
            this.tier = tier;
        }
    }

    // -------------------------------------------------------------------------
    // ApiProfile: one API's full traffic profile
    // -------------------------------------------------------------------------

    public static class ApiProfile {
        public final String path;
        public final String method;
        public final String usageLevel;  // HIGH, MEDIUM, LOW
        public final List<IpEntry> ipPool;

        public ApiProfile(String path, String method, String usageLevel, List<IpEntry> ipPool) {
            this.path = path;
            this.method = method;
            this.usageLevel = usageLevel;
            this.ipPool = ipPool;
        }
    }

    // -------------------------------------------------------------------------
    // IP pool builder
    // Zipf split: 60% CASUAL, 25% REGULAR, 12% POWER, 3% ANOMALOUS
    // -------------------------------------------------------------------------

    public static List<IpEntry> buildIpPool(int poolSize, String subnet) {
        List<IpEntry> pool = new ArrayList<>(poolSize);
        int casual    = (int)(poolSize * 0.60);
        int regular   = (int)(poolSize * 0.25);
        int power     = (int)(poolSize * 0.12);
        int anomalous = poolSize - casual - regular - power;  // remaining ~3%

        int idx = 0;
        for (int i = 0; i < casual;    i++) pool.add(new IpEntry(subnet + (++idx), Tier.CASUAL));
        for (int i = 0; i < regular;   i++) pool.add(new IpEntry(subnet + (++idx), Tier.REGULAR));
        for (int i = 0; i < power;     i++) pool.add(new IpEntry(subnet + (++idx), Tier.POWER));
        for (int i = 0; i < anomalous; i++) pool.add(new IpEntry(subnet + (++idx), Tier.ANOMALOUS));
        return pool;
    }

    // -------------------------------------------------------------------------
    // The 11 APIs for collection 1000
    // HIGH: 200 IPs | MEDIUM: 100 IPs | LOW: 40 IPs
    // -------------------------------------------------------------------------

    public static List<ApiProfile> buildCatalog() {
        List<ApiProfile> catalog = new ArrayList<>();

        // HIGH usage — large IP pools, busy endpoints
        catalog.add(new ApiProfile("/api/v1/feed",              "GET",  "HIGH",   buildIpPool(200, "10.1.0.")));
        catalog.add(new ApiProfile("/api/v1/search",            "GET",  "HIGH",   buildIpPool(200, "10.2.0.")));
        catalog.add(new ApiProfile("/api/v1/products/123",      "GET",  "HIGH",   buildIpPool(200, "10.3.0.")));

        // MEDIUM usage — moderate IP pools
        catalog.add(new ApiProfile("/api/v1/cart",              "POST", "MEDIUM", buildIpPool(100, "10.4.0.")));
        catalog.add(new ApiProfile("/api/v1/orders",            "POST", "MEDIUM", buildIpPool(100, "10.5.0.")));
        catalog.add(new ApiProfile("/api/v1/orders/456",        "GET",  "MEDIUM", buildIpPool(100, "10.6.0.")));
        catalog.add(new ApiProfile("/api/v1/payments/checkout", "POST", "MEDIUM", buildIpPool(100, "10.7.0.")));

        // LOW usage — small IP pools, infrequent endpoints
        catalog.add(new ApiProfile("/api/v1/auth/login",        "POST", "LOW",    buildIpPool(40,  "10.8.0.")));
        catalog.add(new ApiProfile("/api/v1/users/me",          "GET",  "LOW",    buildIpPool(40,  "10.9.0.")));
        catalog.add(new ApiProfile("/api/v1/admin/config",      "GET",  "LOW",    buildIpPool(40,  "10.10.0.")));
        catalog.add(new ApiProfile("/api/v1/reports/export",    "POST", "LOW",    buildIpPool(40,  "10.11.0.")));

        return catalog;
    }

    // -------------------------------------------------------------------------
    // MessageBuilder: builds one HttpResponseParam for a given IP + profile + minute
    // -------------------------------------------------------------------------

    public static HttpResponseParam buildMessage(ApiProfile profile, IpEntry ipEntry,
                                                  long epochMinute, Random random) {
        int timeSeconds = (int)(epochMinute * 60) + random.nextInt(60);

        Map<String, StringList> reqHeaders = new HashMap<>();
        reqHeaders.put("content-type", sl("application/json"));
        reqHeaders.put("authorization", sl("Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMDAwMDAwIn0.sim"));
        reqHeaders.put("host", sl("apiratelimit.example.com"));
        reqHeaders.put("x-forwarded-for", sl(ipEntry.ip));
        reqHeaders.put("user-agent", sl(USER_AGENT));
        reqHeaders.put("accept", sl("application/json"));

        Map<String, StringList> respHeaders = new HashMap<>();
        respHeaders.put("content-type", sl("application/json; charset=utf-8"));
        respHeaders.put("x-request-id", sl("sim-" + timeSeconds + "-" + random.nextInt(99999)));

        String requestBody = profile.method.equals("GET") ? ""
                : "{\"timestamp\": " + timeSeconds + ", \"source\": \"sim\"}";
        String responseBody = "{\"status\": \"ok\", \"ts\": " + timeSeconds + "}";

        int statusCode = random.nextInt(20) == 0 ? 500 : 200;

        return HttpResponseParam.newBuilder()
                .setMethod(profile.method)
                .setPath(profile.path)
                .setType("HTTP/1.1")
                .putAllRequestHeaders(reqHeaders)
                .putAllResponseHeaders(respHeaders)
                .setRequestPayload(requestBody)
                .setResponsePayload(responseBody)
                .setApiCollectionId(COLLECTION_ID)
                .setStatusCode(statusCode)
                .setStatus(statusCode == 200 ? "OK" : "Internal Server Error")
                .setTime(timeSeconds)
                .setAktoAccountId("1000000")
                .setIp(ipEntry.ip)
                .setDestIp("10.0.0.1")
                .setDirection("INBOUND")
                .setIsPending(false)
                .setSource("MIRRORING")
                .setAktoVxlanId("1313121")
                .build();
    }

    public static HttpResponseParam buildMessage(String path, String method,
                                                  String ip, long epochMinute, Random random) {
        int timeSeconds = (int)(epochMinute * 60) + random.nextInt(60);

        Map<String, StringList> reqHeaders = new HashMap<>();
        reqHeaders.put("content-type", sl("application/json"));
        reqHeaders.put("authorization", sl("Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMDAwMDAwIn0.sim"));
        reqHeaders.put("host", sl("apiratelimit.example.com"));
        reqHeaders.put("x-forwarded-for", sl(ip));
        reqHeaders.put("user-agent", sl(USER_AGENT));
        reqHeaders.put("accept", sl("application/json"));

        Map<String, StringList> respHeaders = new HashMap<>();
        respHeaders.put("content-type", sl("application/json; charset=utf-8"));
        respHeaders.put("x-request-id", sl("sim-" + timeSeconds + "-" + random.nextInt(99999)));

        String requestBody = method.equals("GET") ? ""
                : "{\"timestamp\": " + timeSeconds + ", \"source\": \"sim\"}";
        String responseBody = "{\"status\": \"ok\", \"ts\": " + timeSeconds + "}";
        int statusCode = random.nextInt(20) == 0 ? 500 : 200;

        return HttpResponseParam.newBuilder()
                .setMethod(method)
                .setPath(path)
                .setType("HTTP/1.1")
                .putAllRequestHeaders(reqHeaders)
                .putAllResponseHeaders(respHeaders)
                .setRequestPayload(requestBody)
                .setResponsePayload(responseBody)
                .setApiCollectionId(COLLECTION_ID)
                .setStatusCode(statusCode)
                .setStatus(statusCode == 200 ? "OK" : "Internal Server Error")
                .setTime(timeSeconds)
                .setAktoAccountId("1000000")
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
