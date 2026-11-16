package com.akto.hybrid_runtime;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.detection.DetectionCorrectorRegistry;
import com.akto.detection.SensitiveDataEvents;
import com.akto.dto.ApiCollection;
import com.akto.dto.CustomDataType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.IgnoreData;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.traffic.SensitiveDataCount;
import com.akto.dto.type.RequestTemplate;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.TrafficRecorder;
import com.akto.hybrid_parsers.HttpCallParser;
import com.google.gson.Gson;
import com.mongodb.ConnectionString;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What this runtime puts ON THE WIRE for the database abstractor.
 *
 * A real Kafka-format message goes through {@code HttpCallParser.parseKafkaMessage} and
 * {@code APICatalogSync.processResponse}, and the increments the counter would send are captured
 * rather than written - storing them is the abstractor's job, not this one's.
 *
 * The assertion that matters is that the caller's id equals the id its OWN collection is keyed by,
 * {@code ApiCollection.generateServiceTagCollectionId}. If those ever diverge, the two ends of a
 * call resolve to two different services and the alert reads as nonsense.
 *
 * APICatalogSync loads deactivated collections from Mongo, hence -DtestMongo (default
 * localhost:27029) and the *IT name.
 */
public class SensitiveDataPartiesIT {

    private static final int ACCOUNT_ID = 1000000;
    private static final int RECEIVER_COLLECTION = 1;
    private static final int HOUR = 1791536400;
    private static final Gson gson = new Gson();

    private APICatalogSync sync;
    private final List<SensitiveDataCount> sent = new CopyOnWriteArrayList<>();

    @BeforeClass
    public static void connect() {
        DaoInit.init(new ConnectionString(System.getProperty("testMongo", "mongodb://localhost:27029/admini")));
    }

    private static CustomDataType bookingRef() {
        Conditions values = new Conditions(
                Collections.<Predicate>singletonList(new RegexPredicate("^BK-[0-9]{6}$")), Conditions.Operator.OR);
        return new CustomDataType("BOOKING_REF", true, Collections.emptyList(), 0, true, null, values,
                Conditions.Operator.OR, new IgnoreData(new HashMap<>(), new HashSet<>()), false, false);
    }

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.setActualAccountId(ACCOUNT_ID);

        List<CustomDataType> types = Collections.singletonList(bookingRef());
        SingleTypeInfo.fetchCustomDataTypes(ACCOUNT_ID, types, new ArrayList<>());
        SensitiveDataEvents.setTrackedTypes(SensitiveDataEvents.customSensitiveTypeNames(types));
        sync = new APICatalogSync("access-token", 1, true, false);

        // drop anything a previous test left pending, then capture instead of writing
        SensitiveDataCounter.instance.setWriter((key, batch) -> true);
        SensitiveDataCounter.instance.flush();
        sent.clear();
        SensitiveDataCounter.instance.setWriter((key, batch) -> {
            sent.addAll(batch);
            return true;
        });
    }

    @After
    public void tearDown() {
        DetectionCorrectorRegistry.reset();
        SensitiveDataEvents.setTrackedTypes(null);
        SensitiveDataCounter.instance.setWriter((key, batch) -> true);
        SensitiveDataCounter.instance.flush();
    }

    /** One message carrying a tracked value, with this direction and these pod labels. */
    private void send(String direction, Map<String, String> podLabels) throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", "application/json");
        headers.put("host", "customerapi.sdlab.svc.cluster.local");

        Map<String, Object> msg = new HashMap<>();
        msg.put("path", "/api/booking");
        msg.put("method", "POST");
        msg.put("type", "HTTP/1.1");
        msg.put("requestHeaders", gson.toJson(headers));
        msg.put("responseHeaders", gson.toJson(Collections.singletonMap("content-type", "application/json")));
        msg.put("requestPayload", "{\"ref\":\"BK-123456\"}");
        msg.put("responsePayload", "{\"ok\":true}");
        msg.put("statusCode", "200");
        msg.put("status", "OK");
        msg.put("ip", "10.0.0.5");
        msg.put("time", String.valueOf(HOUR + 60));
        msg.put("akto_account_id", String.valueOf(ACCOUNT_ID));
        msg.put("akto_vxlan_id", String.valueOf(RECEIVER_COLLECTION));
        msg.put("is_pending", "false");
        msg.put("source", "MIRRORING");
        msg.put("direction", direction);
        if (podLabels != null) msg.put("tag", gson.toJson(podLabels));

        HttpResponseParams params = HttpCallParser.parseKafkaMessage(gson.toJson(msg));
        RequestTemplate template = new RequestTemplate(new HashMap<>(), new HashMap<>(), new HashMap<>(),
                new TrafficRecorder(new HashMap<>()));
        sync.processResponse(template, params, new ArrayList<>());
    }

    private static Map<String, String> callerLabels(String service) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(HttpCallParser.SERVICE_TAG_KEY, service);
        m.put("pod-template-hash", "7d8dbb7df6");
        return m;
    }

    /** The increments the counter would send, as "sender->receiver" -> count. */
    private Map<String, Long> flushed() {
        SensitiveDataCounter.instance.flush();
        Map<String, Long> out = new TreeMap<>();
        for (SensitiveDataCount c : sent) {
            String k = c.getSenderCollectionId() + "->" + c.getApiCollectionId();
            out.merge(k, c.getCount(), Long::sum);
        }
        return out;
    }

    /**
     * The id sent for the caller must be the one its own collection is keyed by, or the two ends
     * of a call resolve to two different services.
     */
    @Test
    public void sendsTheCallersOwnCollectionId() throws Exception {
        send(HttpCallParser.DIRECTION_OUTBOUND, callerLabels("pma-smile"));

        int expected = ApiCollection.generateServiceTagCollectionId("pma-smile");
        assertEquals(Collections.singletonMap(expected + "->" + RECEIVER_COLLECTION, 1L), flushed());
    }

    /** Inbound pod labels describe the callee, so there is no caller to attribute. */
    @Test
    public void inboundSendsNoSenderButStillCountsForTheCallee() throws Exception {
        send("1", callerLabels("customerapi"));

        assertEquals(Collections.singletonMap(
                SensitiveDataCount.UNKNOWN_COLLECTION_ID + "->" + RECEIVER_COLLECTION, 1L), flushed());
    }

    /** Outbound traffic with no pod labels at all - the caller is simply unknown. */
    @Test
    public void outboundWithoutLabelsSendsNoSender() throws Exception {
        send(HttpCallParser.DIRECTION_OUTBOUND, null);

        assertEquals(Collections.singletonMap(
                SensitiveDataCount.UNKNOWN_COLLECTION_ID + "->" + RECEIVER_COLLECTION, 1L), flushed());
    }

    /** Several callers to one callee stay separate increments rather than collapsing into one. */
    @Test
    public void severalCallersAreSentSeparately() throws Exception {
        send(HttpCallParser.DIRECTION_OUTBOUND, callerLabels("pma-smile"));
        send(HttpCallParser.DIRECTION_OUTBOUND, callerLabels("cegapi"));
        send(HttpCallParser.DIRECTION_OUTBOUND, callerLabels("pma-smile"));

        Map<String, Long> out = flushed();
        assertEquals(2, out.size());
        assertEquals(Long.valueOf(2),
                out.get(ApiCollection.generateServiceTagCollectionId("pma-smile") + "->" + RECEIVER_COLLECTION));
        assertEquals(Long.valueOf(1),
                out.get(ApiCollection.generateServiceTagCollectionId("cegapi") + "->" + RECEIVER_COLLECTION));
    }

    /** Every increment carries the hour and type it was counted under, alongside both ids. */
    @Test
    public void incrementsCarryCollectionHourAndType() throws Exception {
        send(HttpCallParser.DIRECTION_OUTBOUND, callerLabels("pma-smile"));
        SensitiveDataCounter.instance.flush();

        assertTrue("expected at least one increment", !sent.isEmpty());
        SensitiveDataCount c = sent.get(0);
        assertEquals(RECEIVER_COLLECTION, c.getApiCollectionId());
        assertEquals(ApiCollection.generateServiceTagCollectionId("pma-smile"), c.getSenderCollectionId());
        assertEquals("BOOKING_REF", c.getSubType());
        assertEquals(HOUR, c.getHour());
    }
}
