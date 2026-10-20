package com.akto.hybrid_runtime;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.detection.SensitiveDataEvents;
import com.akto.dto.CustomDataType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.IgnoreData;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.hybrid_parsers.HttpCallParser;
import com.google.gson.Gson;
import com.mongodb.ConnectionString;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Counting through APICatalogSync.computeDelta - the method the runtime calls per batch and per
 * collection - rather than processResponse alone. This covers the known-API and new-API paths and URL
 * templating, which runs detection on path segments between messages. Needs Mongo (-DtestMongo,
 * default localhost:27029) because APICatalogSync reads from it while loading.
 */
public class SensitiveDataComputeDeltaIT {

    private static final int ACCOUNT_ID = 1000000;
    private static final Gson gson = new Gson();
    private static final int HOUR = 1791536400;
    private APICatalogSync sync;

    @BeforeClass
    public static void connect() {
        DaoInit.init(new ConnectionString(System.getProperty("testMongo", "mongodb://localhost:27029/admini")));
    }

    private static CustomDataType type(String name, String regex) {
        Conditions values = new Conditions(Collections.<Predicate>singletonList(new RegexPredicate(regex)), Conditions.Operator.OR);
        return new CustomDataType(name, true, Collections.emptyList(), 0, true, null, values, Conditions.Operator.OR,
                new IgnoreData(new HashMap<>(), new HashSet<>()), false, false);
    }

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.setActualAccountId(ACCOUNT_ID);
        List<CustomDataType> types = Arrays.asList(type("BOOKING_REF", "^BK-[0-9]{6}$"), type("PASSPORT_REF", "^PX-[0-9]{4}$"));
        SingleTypeInfo.fetchCustomDataTypes(ACCOUNT_ID, types, new ArrayList<>());
        SensitiveDataEvents.setTrackedTypes(SensitiveDataEvents.customSensitiveTypeNames(types));
        sync = new APICatalogSync("access-token", 1, true, false);
        drain();
    }

    @After
    public void tearDown() {
        SensitiveDataEvents.setTrackedTypes(null);
        drain();
    }

    private static void drain() {
        SensitiveDataCounter.instance.setWriter((key, batch) -> true);
        SensitiveDataCounter.instance.flush();
    }

    private static Map<String, Long> pending() {
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<SensitiveDataCounter.Bucket, Long> e : SensitiveDataCounter.instance.snapshot().entrySet()) {
            out.put(e.getKey().apiCollectionId + "|" + e.getKey().subType + "|" + e.getKey().hour, e.getValue());
        }
        return out;
    }

    private static HttpResponseParams message(int collection, String method, String path, Map<String, String> extraHeaders,
                                              String reqBody, String respBody, int time, boolean pending) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("host", "delta-test.local");
        headers.put("content-type", "application/json");
        if (extraHeaders != null) headers.putAll(extraHeaders);
        Map<String, Object> m = new HashMap<>();
        m.put("path", path); m.put("method", method); m.put("type", "HTTP/1.1");
        m.put("requestHeaders", gson.toJson(headers));
        m.put("responseHeaders", gson.toJson(Collections.singletonMap("content-type", "application/json")));
        m.put("requestPayload", reqBody == null ? "" : reqBody);
        m.put("responsePayload", respBody == null ? "{}" : respBody);
        m.put("statusCode", "200"); m.put("status", "OK"); m.put("ip", "10.0.0.5");
        m.put("time", String.valueOf(time));
        m.put("akto_account_id", String.valueOf(ACCOUNT_ID));
        m.put("akto_vxlan_id", String.valueOf(collection));
        m.put("is_pending", pending ? "true" : "false");
        m.put("source", "MIRRORING");
        return HttpCallParser.parseKafkaMessage(gson.toJson(m));
    }

    private void batch(int collection, List<HttpResponseParams> messages) {
        URLAggregator aggregator = new URLAggregator();
        for (HttpResponseParams p : messages) aggregator.addURL(p);
        sync.computeDelta(aggregator, true, collection);
    }

    /** One batch for collection 1: path values that must not count, plus body values that must. */
    private List<HttpResponseParams> collectionOneBatch() throws Exception {
        List<HttpResponseParams> out = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            // sensitive value only in the URL path; enough distinct values to make the URL a template
            out.add(message(1, "GET", "/api/bookings/BK-" + String.format("%06d", 100000 + i) + "/confirm", null, "", "{\"ok\":true}", HOUR + 60, false));
        }
        for (int i = 0; i < 10; i++) {
            out.add(message(1, "POST", "/api/bookings/create", null, "{\"ref\":\"BK-" + String.format("%06d", 200000 + i) + "\"}", "{}", HOUR + 60, false));
        }
        for (int i = 0; i < 5; i++) {
            // numeric ids in the path (templated too) with the value in the body; pending ones count
            out.add(message(1, "POST", "/api/users/" + (1000 + i) + "/bookings", null, "{\"ref\":\"BK-" + String.format("%06d", 300000 + i) + "\"}", "{}", HOUR + 120, i % 2 == 0));
        }
        return out;
    }

    private List<HttpResponseParams> collectionTwoBatch() throws Exception {
        List<HttpResponseParams> out = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            out.add(message(2, "GET", "/v2/profile", Collections.singletonMap("x-passport", "PX-" + (1000 + i)), "", "{}", HOUR + 3600 + 10, false));
        }
        for (int i = 0; i < 20; i++) {
            // passport only in the path of collection 2
            out.add(message(2, "GET", "/v2/docs/PX-" + (2000 + i), null, "", "{}", HOUR + 3600 + 10, false));
        }
        return out;
    }

    @Test
    public void interleavedCollectionsAcrossTwoBatchesCountOnlyTheirOwnBodyHeaderValues() throws Exception {
        batch(1, collectionOneBatch());
        batch(2, collectionTwoBatch());
        batch(1, collectionOneBatch());   // second batch: the APIs are known now
        batch(2, collectionTwoBatch());

        Map<String, Long> expected = new HashMap<>();
        expected.put("1|BOOKING_REF|" + HOUR, 2L * (10 + 5));          // body values, pending included
        expected.put("2|PASSPORT_REF|" + (HOUR + 3600), 2L * 15);      // header values
        assertEquals(expected, pending());

        // the risky path really ran: path segments were turned into templates
        assertFalse("expected URL templates in collection 1", sync.getDelta(1).getTemplateURLToMethods().isEmpty());
        assertFalse("expected URL templates in collection 2", sync.getDelta(2).getTemplateURLToMethods().isEmpty());
    }

    @Test
    public void aBatchWithOnlyPathValuesCountsNothing() throws Exception {
        List<HttpResponseParams> msgs = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            msgs.add(message(3, "DELETE", "/api/bookings/BK-" + String.format("%06d", 400000 + i), null, "", "{}", HOUR, false));
        }
        batch(3, msgs);
        batch(4, Collections.singletonList(message(4, "GET", "/health", null, "", "{\"ok\":true}", HOUR, false)));
        assertEquals(Collections.emptyMap(), pending());
    }
}
