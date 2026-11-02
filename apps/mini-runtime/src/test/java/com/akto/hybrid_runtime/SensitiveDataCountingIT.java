package com.akto.hybrid_runtime;

import com.akto.dao.context.Context;
import com.akto.detection.CandidatePublisher;
import com.akto.detection.DetectionCandidate;
import com.akto.detection.DetectionCorrector;
import com.akto.detection.DetectionCorrectorRegistry;
import com.akto.detection.ParamLocation;
import com.akto.detection.ParamVerdictCache;
import com.akto.detection.SensitiveDataEvents;
import com.akto.dto.CustomDataType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.IgnoreData;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.traffic.SensitiveDataCount;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.SingleTypeInfo.SubType;
import com.akto.dto.type.TrafficRecorder;
import com.akto.hybrid_parsers.HttpCallParser;
import com.google.gson.Gson;
import com.akto.DaoInit;
import com.mongodb.ConnectionString;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Pushes real Kafka-format messages through HttpCallParser.parseKafkaMessage and
 * APICatalogSync.processResponse - the runtime's own path - and checks what the counter holds.
 * APICatalogSync reads from Mongo while loading (-DtestMongo, default localhost:27029), so this is
 * named *IT and skipped by the default unit test run.
 */
public class SensitiveDataCountingIT {

    private static final int ACCOUNT_ID = 1000000;
    private static final Gson gson = new Gson();
    private static final int HOUR = 1791536400;

    private APICatalogSync sync;

    private static CustomDataType type(String name, String regex, boolean active, boolean sensitiveAlways,
                                       List<SingleTypeInfo.Position> positions) {
        Conditions values = new Conditions(Collections.<Predicate>singletonList(new RegexPredicate(regex)), Conditions.Operator.OR);
        return new CustomDataType(name, sensitiveAlways, positions, 0, active, null, values, Conditions.Operator.OR,
                new IgnoreData(new HashMap<>(), new HashSet<>()), false, false);
    }

    /** APICatalogSync reads deactivated collections from the DB while loading; point it at the test Mongo. */
    @BeforeClass
    public static void connect() {
        DaoInit.init(new ConnectionString(System.getProperty("testMongo", "mongodb://localhost:27029/admini")));
    }

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.setActualAccountId(ACCOUNT_ID);
        List<CustomDataType> types = Arrays.asList(
                type("BOOKING_REF", "^BK-[0-9]{6}$", true, true, Collections.emptyList()),
                type("PASSPORT_REF", "^PX-[0-9]{4}$", true, false, Collections.singletonList(SingleTypeInfo.Position.RESPONSE_PAYLOAD)),
                type("INACTIVE_REF", "^IN-[0-9]{4}$", false, true, Collections.emptyList()),
                type("PLAIN_REF", "^NS-[0-9]{4}$", true, false, Collections.emptyList()));
        SingleTypeInfo.fetchCustomDataTypes(ACCOUNT_ID, types, new ArrayList<>());
        SensitiveDataEvents.setTrackedTypes(SensitiveDataEvents.customSensitiveTypeNames(types));
        sync = new APICatalogSync("access-token", 1, true, false);
        drainCounter();
    }

    @After
    public void tearDown() {
        DetectionCorrectorRegistry.reset();
        SensitiveDataEvents.setTrackedTypes(null);
        drainCounter();
    }

    private static void drainCounter() {
        SensitiveDataCounter.instance.setWriter((key, batch) -> true);
        SensitiveDataCounter.instance.flush();
    }

    /** Counts currently pending, as "collection|type|hour" -> count. */
    private static Map<String, Long> pending() {
        Map<String, Long> out = new HashMap<>();
        for (Map.Entry<SensitiveDataCounter.Bucket, Long> e : SensitiveDataCounter.instance.snapshot().entrySet()) {
            out.put(e.getKey().apiCollectionId + "|" + e.getKey().subType + "|" + e.getKey().hour, e.getValue());
        }
        return out;
    }

    private static Map<String, Long> expect(Object... kv) {
        Map<String, Long> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        return m;
    }

    private void process(int collection, String path, Map<String, String> reqHeaders, String reqBody,
                         String respBody, int time, boolean pending) throws Exception {
        Map<String, String> headers = new HashMap<>();
        headers.put("host", "counting-test.local");
        headers.put("content-type", "application/json");
        if (reqHeaders != null) headers.putAll(reqHeaders);
        Map<String, Object> msg = new HashMap<>();
        msg.put("path", path);
        msg.put("method", "POST");
        msg.put("type", "HTTP/1.1");
        msg.put("requestHeaders", gson.toJson(headers));
        msg.put("responseHeaders", gson.toJson(Collections.singletonMap("content-type", "application/json")));
        msg.put("requestPayload", reqBody == null ? "" : reqBody);
        msg.put("responsePayload", respBody == null ? "" : respBody);
        msg.put("statusCode", "200");
        msg.put("status", "OK");
        msg.put("ip", "10.0.0.5");
        msg.put("time", String.valueOf(time));
        msg.put("akto_account_id", String.valueOf(ACCOUNT_ID));
        msg.put("akto_vxlan_id", String.valueOf(collection));
        msg.put("is_pending", pending ? "true" : "false");
        msg.put("source", "MIRRORING");

        HttpResponseParams params = HttpCallParser.parseKafkaMessage(gson.toJson(msg));
        RequestTemplate template = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder(new HashMap<>()));
        sync.processResponse(template, params, new ArrayList<>());
    }

    private void process(int collection, String reqBody, String respBody) throws Exception {
        process(collection, "/api/booking", null, reqBody, respBody, HOUR + 60, false);
    }

    @Test
    public void requestBodyOnly() throws Exception {
        process(1, "{\"booking\":{\"ref\":\"BK-123456\"}}", "{\"ok\":true}");
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void responseBodyOnly() throws Exception {
        process(1, "{}", "{\"ref\":\"BK-123456\"}");
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void manyValuesInRequestAndResponseCountOnce() throws Exception {
        process(1, "{\"refs\":[\"BK-000001\",\"BK-000002\",\"BK-000003\",\"BK-000004\",\"BK-000005\"]}",
                "{\"a\":\"BK-000006\",\"b\":{\"c\":\"BK-000007\"}}");
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void headerValueCounts() throws Exception {
        process(1, "/api/booking", Collections.singletonMap("x-booking-ref", "BK-123456"), "{}", "{}", HOUR + 60, false);
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void queryParamCounts() throws Exception {
        process(1, "/api/booking?ref=BK-123456&x=1", null, "{}", "{}", HOUR + 60, false);
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void deeplyNestedValueCounts() throws Exception {
        process(1, "{\"a\":{\"b\":{\"c\":{\"d\":[{\"e\":\"BK-123456\"}]}}}}", "{}");
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void twoTrackedTypesInOneMessageCountOnceEach() throws Exception {
        process(1, "{\"ref\":\"BK-123456\"}", "{\"passport\":\"PX-1234\"}");
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1, "1|PASSPORT_REF|" + HOUR, 1), pending());
    }

    @Test
    public void typeSensitiveOnlyInResponseIsCountedInRequestsToo() throws Exception {
        // Tracking is per type, not per position: a type sensitive in any position counts wherever seen.
        process(1, "{\"passport\":\"PX-1234\"}", "{}");
        assertEquals(expect("1|PASSPORT_REF|" + HOUR, 1), pending());
    }

    @Test
    public void builtInInactiveAndNonSensitiveTypesAreNotCounted() throws Exception {
        process(1, "{\"email\":\"jane@example.com\",\"a\":\"IN-1234\",\"b\":\"NS-1234\"}", "{\"card\":\"4111111111111111\"}");
        assertTrue(pending().isEmpty());
    }

    @Test
    public void keyNameAloneDoesNotCount() throws Exception {
        process(1, "{\"booking_ref\":\"not-a-ref\"}", "{}");
        assertTrue(pending().isEmpty());
    }

    @Test
    public void pendingMessagesAreCounted() throws Exception {
        process(1, "/api/booking", null, "{\"ref\":\"BK-123456\"}", "{}", HOUR + 60, true);
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void bucketsByMessageHourAndCollection() throws Exception {
        process(1, "/api/booking", null, "{\"ref\":\"BK-123456\"}", "{}", HOUR - 1, false);
        process(1, "/api/booking", null, "{\"ref\":\"BK-123456\"}", "{}", HOUR, false);
        process(1, "/api/booking", null, "{\"ref\":\"BK-123456\"}", "{}", HOUR + 3599, false);
        process(2, "/api/booking", null, "{\"ref\":\"BK-123456\"}", "{}", HOUR + 10, false);
        assertEquals(expect(
                "1|BOOKING_REF|" + (HOUR - 3600), 1,
                "1|BOOKING_REF|" + HOUR, 2,
                "2|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void everyProcessedMessageCounts() throws Exception {
        for (int i = 0; i < 7; i++) process(1, "{\"ref\":\"BK-12345" + i + "\"}", "{}");
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 7), pending());
    }

    @Test
    public void stopsCountingWhenTypeIsNoLongerTracked() throws Exception {
        process(1, "{\"ref\":\"BK-123456\"}", "{}");
        SensitiveDataEvents.setTrackedTypes(Collections.<String>emptySet());
        process(1, "{\"ref\":\"BK-123456\"}", "{}");
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void malformedResponseBodyStillCountsRequest() throws Exception {
        process(1, "{\"ref\":\"BK-123456\"}", "{not json");
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void formEncodedRequestBodyCounts() throws Exception {
        process(1, "/api/booking", Collections.singletonMap("content-type", "application/x-www-form-urlencoded"),
                "ref=BK-123456&nights=2", "{}", HOUR + 60, false);
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void noLeakBetweenMessages() throws Exception {
        process(1, "{\"ref\":\"BK-123456\"}", "{}");
        process(2, "{\"nothing\":\"here\"}", "{}");
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
        assertTrue(SensitiveDataEvents.drain().isEmpty());
    }

    @Test
    public void urlPathValuesSeenWhileTemplatingAreNotCountedAgainstTheNextMessage() throws Exception {
        // URL templating (computeDelta / tryWithKnownURLTemplates) runs detection on path segments on
        // the ingestion thread, between messages. That must not leak into the next message's count.
        String[] tokens = APICatalogSync.tokenize("/api/bookings/BK-123456");
        SingleTypeInfo.SuperType[] types = new SingleTypeInfo.SuperType[tokens.length];
        types[tokens.length - 1] = SingleTypeInfo.SuperType.STRING;
        RequestTemplate templated = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(), new TrafficRecorder(new HashMap<>()));
        templated.fillUrlParams(tokens, new com.akto.dto.type.URLTemplate(tokens, types, com.akto.dto.type.URLMethods.Method.GET), 2);

        process(1, "{\"nothing\":\"here\"}", "{}");
        assertTrue("path value leaked into collection 1: " + pending(), pending().isEmpty());
    }

    @Test
    public void refinedTypeIsCountedUnderRefinedName() throws Exception {
        // Local detection says EMAIL; the classifier's cached answer for this parameter says BOOKING_REF.
        ParamVerdictCache cache = new ParamVerdictCache(100, 3600);
        DetectionCorrector corrector = new DetectionCorrector() {
            public boolean isTrigger(SubType localSubType) { return localSubType == SingleTypeInfo.EMAIL; }
            public Map<Integer, String> correct(List<DetectionCandidate> candidates) { return Collections.emptyMap(); }
        };
        CandidatePublisher noop = candidates -> { };
        DetectionCorrectorRegistry.install(corrector, cache, noop);
        cache.put(new ParamLocation(1, "/api/booking", "POST", "guest#email"), "BOOKING_REF");

        process(1, "{\"guest\":{\"email\":\"jane@example.com\"}}", "{}");
        assertEquals(expect("1|BOOKING_REF|" + HOUR, 1), pending());
    }

    @Test
    public void hourOfHandlesNegativeAndBoundary() {
        assertEquals(HOUR, SensitiveDataCount.hourOf(HOUR));
        assertEquals(HOUR - 3600, SensitiveDataCount.hourOf(HOUR - 1));
    }
}
