package com.akto.notifications;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SensitiveDataCountDao;
import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.notifications.CustomWebhooksResultDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.SensitiveDataAlertRule;
import com.akto.dto.traffic.SensitiveDataCount;
import com.akto.dto.type.URLMethods.Method;
import com.google.gson.Gson;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The evaluator against a real Mongo and real HTTP delivery to a local receiver
 * (status_listener.py, which answers with the status code named in the path).
 * -DtestMongo, -DreceiverBase (http://host:port), -DreceiverLog (its JSONL file). Named *IT so the
 * default unit test run, which has neither, skips it.
 */
public class SensitiveDataThresholdAlertsIT {

    private static final int ACCOUNT = 1000000;
    private static final int OTHER_ACCOUNT = 1000002;
    private static final int DAY = 86400;
    private static final Gson gson = new Gson();
    private static String receiver;
    private static String receiverLog;

    @BeforeClass
    public static void connect() {
        DaoInit.init(new ConnectionString(System.getProperty("testMongo", "mongodb://localhost:27029/admini")));
        receiver = System.getProperty("receiverBase");
        receiverLog = System.getProperty("receiverLog");
    }

    @Before
    public void reset() throws Exception {
        for (int acc : new int[]{ACCOUNT, OTHER_ACCOUNT}) {
            Context.accountId.set(acc);
            SensitiveDataCountDao.instance.getMCollection().drop();
            SensitiveDataCountDao.instance.createIndicesIfAbsent();
            CustomWebhooksDao.instance.getMCollection().drop();
            CustomWebhooksResultDao.instance.getMCollection().drop();
            ApiCollectionsDao.instance.getMCollection().drop();
        }
        Context.accountId.set(ACCOUNT);
        Files.write(Paths.get(receiverLog), new byte[0]);
    }

    private static int now() {
        return Context.now();
    }

    private static void counts(int collection, String type, int hoursAgo, long count) {
        int hour = SensitiveDataCount.hourOf(now() - hoursAgo * 3600);
        SensitiveDataCountDao.instance.bulkInc(Collections.singletonList(new SensitiveDataCount(collection, type, hour, count)));
    }

    private static void collection(int id, String name) {
        ApiCollection c = new ApiCollection(id, name, now(), new HashSet<>(), name, 0, false, true);
        ApiCollectionsDao.instance.insertOne(c);
    }

    private static int webhook(int id, String statusPath, boolean active, SensitiveDataAlertRule... rules) {
        int t = now();
        CustomWebhook w = new CustomWebhook(id, "it-" + id, receiver + statusPath + "/hook" + id,
                "{'content-type': 'application/json'}", "", "{}", Method.POST, 900, "it@akto.io", t, t, 0,
                active ? CustomWebhook.ActiveStatus.ACTIVE : CustomWebhook.ActiveStatus.INACTIVE,
                Collections.singletonList(CustomWebhook.WebhookOptions.SENSITIVE_DATA_THRESHOLD_ALERTS), null, null);
        w.setWebhookType(CustomWebhook.WebhookType.DEFAULT);
        w.setSensitiveDataAlertRules(rules == null ? null : Arrays.asList(rules));
        CustomWebhooksDao.instance.insertOne(w);
        return id;
    }

    private static SensitiveDataAlertRule rule(String type, int threshold, int days) {
        return new SensitiveDataAlertRule(type, threshold, days);
    }

    /** Requests the receiver got, as maps with path/status/body (body parsed). */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> received() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get(receiverLog))) {
            if (line.trim().isEmpty()) continue;
            Map<String, Object> r = gson.fromJson(line, Map.class);
            r.put("json", gson.fromJson((String) r.get("body"), Map.class));
            out.add(r);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> alertedPairs(Map<String, Object> request) {
        Set<String> pairs = new HashSet<>();
        for (Map<String, Object> a : (List<Map<String, Object>>) ((Map<String, Object>) request.get("json")).get("alerts")) {
            pairs.add(a.get("dataType") + "|" + ((Number) a.get("apiCollectionId")).intValue() + "|" + ((Number) a.get("count")).longValue());
        }
        return pairs;
    }

    private static Map<String, Integer> alertedAt(int webhookId) {
        return CustomWebhooksDao.instance.findOne(Filters.eq("_id", webhookId)).getSensitiveDataAlertedAt();
    }

    @Test
    public void alertsStrictlyAboveThresholdAcrossCollectionsInOnePayload() throws Exception {
        collection(1, "c-one"); collection(2, "c-two");
        counts(1, "T", 1, 15); counts(1, "T", 30, 10);  // 25 within 7 days
        counts(2, "T", 2, 20);                         // exactly 20: not more than
        counts(3, "T", 3, 21);                         // collection with no api_collections doc
        webhook(11, "", true, rule("T", 20, 7));

        SensitiveDataThresholdAlerts.run();

        List<Map<String, Object>> got = received();
        assertEquals(1, got.size());
        assertEquals(new HashSet<>(Arrays.asList("T|1|25", "T|3|21")), alertedPairs(got.get(0)));
        String body = (String) got.get(0).get("body");
        assertTrue(body.contains("c-one"));
        assertTrue("unknown collection falls back to its id: " + body, body.contains("\"apiCollectionName\":\"3\""));
    }

    @Test
    public void eachRuleUsesItsOwnWindow() throws Exception {
        counts(1, "A", 24 * 5, 50);   // 5 days ago
        counts(1, "B", 24 * 5, 50);
        webhook(12, "", true, rule("A", 10, 3), rule("B", 10, 7));
        SensitiveDataThresholdAlerts.run();
        assertEquals(Collections.singleton("B|1|50"), alertedPairs(received().get(0)));
    }

    @Test
    public void bucketsOlderThanTheWindowAreExcluded() throws Exception {
        counts(1, "T", 24 * 8, 100);  // outside 7 days
        counts(1, "T", 1, 5);
        webhook(13, "", true, rule("T", 10, 7));
        SensitiveDataThresholdAlerts.run();
        assertTrue(received().isEmpty());
    }

    @Test
    public void alertsOncePerDayAndAgainTheNextDay() throws Exception {
        counts(1, "T", 1, 50);
        webhook(14, "", true, rule("T", 10, 7));
        SensitiveDataThresholdAlerts.run();
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());

        // a day has passed since the last alert, and it is still breaching
        CustomWebhooksDao.instance.updateOne(Filters.eq("_id", 14), Updates.set("sensitiveDataAlertedAt.T|1", now() - 2 * DAY));
        SensitiveDataThresholdAlerts.run();
        assertEquals(2, received().size());
        assertTrue(alertedAt(14).get("T|1") >= now() - 60);
    }

    @Test
    public void newCollectionCrossingLaterStillAlertsWhileAnotherIsDeduped() throws Exception {
        counts(1, "T", 1, 50);
        webhook(15, "", true, rule("T", 10, 7));
        SensitiveDataThresholdAlerts.run();
        counts(2, "T", 1, 11);
        SensitiveDataThresholdAlerts.run();
        List<Map<String, Object>> got = received();
        assertEquals(2, got.size());
        assertEquals(Collections.singleton("T|2|11"), alertedPairs(got.get(1)));
    }

    @Test
    public void inactiveWebhookEmptyAndInvalidRulesSendNothing() throws Exception {
        counts(1, "T", 1, 50);
        webhook(16, "", false, rule("T", 10, 7));
        webhook(17, "", true);                                // no rules
        webhook(18, "", true, rule("T", 0, 7), rule("", 10, 7), rule("T", 10, 0),
                rule("T", 10, SensitiveDataAlertRule.MAX_WINDOW_DAYS + 1));
        CustomWebhooksDao.instance.updateOne(Filters.eq("_id", 17), Updates.unset(CustomWebhook.SENSITIVE_DATA_ALERT_RULES));
        SensitiveDataThresholdAlerts.run();
        assertTrue(received().isEmpty());
    }

    @Test
    public void invalidRuleDoesNotBlockValidOnes() throws Exception {
        counts(1, "T", 1, 50);
        webhook(19, "", true, rule("T", 0, 7), rule("T", 10, 7));
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());
    }

    @Test
    public void unreachableReceiverIsNotMarkedSoTheNextRunRetries() throws Exception {
        counts(1, "T", 1, 50);
        CustomWebhook w = new CustomWebhook(20, "it-20", "http://192.0.2.1:9/unreachable", "{'content-type': 'application/json'}",
                "", "{}", Method.POST, 900, "it@akto.io", now(), now(), 0, CustomWebhook.ActiveStatus.ACTIVE,
                Collections.singletonList(CustomWebhook.WebhookOptions.SENSITIVE_DATA_THRESHOLD_ALERTS), null, null);
        w.setSensitiveDataAlertRules(Collections.singletonList(rule("T", 10, 7)));
        CustomWebhooksDao.instance.insertOne(w);
        SensitiveDataThresholdAlerts.run();
        assertNull("not delivered, so not marked", alertedAt(20));
    }

    @Test
    public void receiverErrorStatusIsNotMarkedSoTheNextRunRetries() throws Exception {
        counts(1, "T", 1, 50);
        webhook(21, "/status/500", true, rule("T", 10, 7));
        SensitiveDataThresholdAlerts.run();
        SensitiveDataThresholdAlerts.run();
        assertEquals("a 500 must not count as delivered", 2, received().size());
        assertNull(alertedAt(21));
    }

    @Test
    public void typeNamesWithDotsAndDollarsDedupe() throws Exception {
        counts(1, "A.B$C", 1, 50);
        webhook(22, "", true, rule("A.B$C", 10, 7));
        SensitiveDataThresholdAlerts.run();
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());
        assertTrue(alertedAt(22).containsKey("A_B_C|1"));
    }

    @Test
    public void accountsAreIsolated() throws Exception {
        Context.accountId.set(OTHER_ACCOUNT);
        counts(1, "T", 1, 50);                       // counts only in the other account
        Context.accountId.set(ACCOUNT);
        webhook(23, "", true, rule("T", 10, 7));     // webhook only in this account
        SensitiveDataThresholdAlerts.run();
        Context.accountId.set(OTHER_ACCOUNT);
        SensitiveDataThresholdAlerts.run();
        Context.accountId.set(ACCOUNT);
        assertTrue(received().isEmpty());
    }

    @Test
    public void deliveryIsRecordedForTheResultTab() throws Exception {
        counts(1, "T", 1, 50);
        webhook(24, "", true, rule("T", 10, 7));
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, CustomWebhooksResultDao.instance.count(Filters.eq("webhookId", 24)));
        assertTrue(CustomWebhooksDao.instance.findOne(Filters.eq("_id", 24)).getLastSentTimestamp() > 0);
    }

    @Test
    public void storedRuleHasNoValidField() {
        webhook(25, "", true, rule("T", 10, 7));
        Document raw = CustomWebhooksDao.instance.getMCollection().withDocumentClass(Document.class)
                .find(Filters.eq("_id", 25)).first();
        @SuppressWarnings("unchecked")
        Map<String, Object> stored = (Map<String, Object>) ((List<?>) raw.get("sensitiveDataAlertRules")).get(0);
        assertEquals(new HashSet<>(Arrays.asList("subType", "threshold", "windowDays")), stored.keySet());
    }

    @Test
    public void manyCollectionsFitOnePayload() throws Exception {
        Map<Integer, Long> expected = new HashMap<>();
        for (int c = 100; c < 300; c++) { counts(c, "T", 1, 11 + c); expected.put(c, 11L + c); }
        webhook(26, "", true, rule("T", 10, 7));
        SensitiveDataThresholdAlerts.run();
        List<Map<String, Object>> got = received();
        assertEquals(1, got.size());
        assertEquals(200, alertedPairs(got.get(0)).size());
        assertEquals(200, alertedAt(26).size());
    }
}
