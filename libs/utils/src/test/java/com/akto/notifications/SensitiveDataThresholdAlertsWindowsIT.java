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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Window lengths and the daily re-arm, against a real Mongo and a real HTTP receiver.
 * Same switches as SensitiveDataThresholdAlertsIT: -DtestMongo, -DreceiverBase, -DreceiverLog.
 *
 * SensitiveDataThresholdAlertsIT covers the 7 day case; this covers the rest of the range, the
 * edges of a window, and the fact that re-arming is now a day rather than the window's length.
 */
public class SensitiveDataThresholdAlertsWindowsIT {

    private static final int ACCOUNT = 1000000;
    private static final int HOUR = 3600;
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
        Context.accountId.set(ACCOUNT);
        SensitiveDataCountDao.instance.getMCollection().drop();
        SensitiveDataCountDao.instance.createIndicesIfAbsent();
        CustomWebhooksDao.instance.getMCollection().drop();
        CustomWebhooksResultDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        Files.write(Paths.get(receiverLog), new byte[0]);
    }

    private static int now() { return Context.now(); }

    /** A bucket {@code hoursAgo} hours back. */
    private static void counts(int collection, String type, int hoursAgo, long count) {
        int hour = SensitiveDataCount.hourOf(now() - hoursAgo * HOUR);
        SensitiveDataCountDao.instance.bulkInc(
                Collections.singletonList(new SensitiveDataCount(collection, type, hour, count)));
    }

    private static void collection(int id, String name) {
        ApiCollectionsDao.instance.insertOne(new ApiCollection(id, name, now(), new HashSet<>(), name, 0, false, true));
    }

    private static int webhook(int id, SensitiveDataAlertRule... rules) {
        int t = now();
        CustomWebhook w = new CustomWebhook(id, "it-" + id, receiver + "/hook" + id,
                "{'content-type': 'application/json'}", "", "{}", Method.POST, 900, "it@akto.io", t, t, 0,
                CustomWebhook.ActiveStatus.ACTIVE,
                Collections.singletonList(CustomWebhook.WebhookOptions.SENSITIVE_DATA_THRESHOLD_ALERTS), null, null);
        w.setWebhookType(CustomWebhook.WebhookType.DEFAULT);
        w.setSensitiveDataAlertRules(Arrays.asList(rules));
        CustomWebhooksDao.instance.insertOne(w);
        return id;
    }

    private static SensitiveDataAlertRule rule(String type, int threshold, int days) {
        return new SensitiveDataAlertRule(type, threshold, days);
    }

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
            pairs.add(a.get("dataType") + "|" + ((Number) a.get("apiCollectionId")).intValue()
                    + "|" + ((Number) a.get("count")).longValue());
        }
        return pairs;
    }

    private static void markAlerted(int webhookId, String key, int at) {
        CustomWebhooksDao.instance.updateOne(Filters.eq("_id", webhookId),
                Updates.set("sensitiveDataAlertedAt." + key, at));
    }

    // ---- window lengths -----------------------------------------------------

    /** Every window length counts its own span and nothing older. */
    @Test
    public void eachWindowLengthCountsOnlyItsOwnSpan() throws Exception {
        int[] days = {1, 2, 3, 10, 14, 30, 60, 90};
        for (int i = 0; i < days.length; i++) {
            reset();
            int d = days[i];
            collection(1, "c1");
            counts(1, "T", d * 24 - 2, 80);   // inside
            counts(1, "T", d * 24 + 2, 80);   // older than the window
            webhook(300 + i, rule("T", 100, d));
            SensitiveDataThresholdAlerts.run();
            assertEquals("window " + d + "d must not reach the threshold on the inside bucket alone",
                    0, received().size());

            counts(1, "T", d * 24 - 3, 40);   // inside, now over
            SensitiveDataThresholdAlerts.run();
            assertEquals("window " + d + "d must alert once both inside buckets count",
                    1, received().size());
            assertTrue(alertedPairs(received().get(0)).contains("T|1|120"));
        }
    }

    /** A two day rule ignores a spike from three days ago. */
    @Test
    public void twoDayWindowIgnoresAThreeDayOldSpike() throws Exception {
        collection(1, "c1");
        counts(1, "T", 3 * 24, 5000);
        webhook(310, rule("T", 10, 2));
        SensitiveDataThresholdAlerts.run();
        assertEquals(0, received().size());
    }

    /** Ten days of small hourly buckets add up across the window. */
    @Test
    public void tenDayWindowSumsManyHourlyBuckets() throws Exception {
        collection(1, "c1");
        for (int h = 1; h < 10 * 24; h += 6) counts(1, "T", h, 3);   // 40 buckets x 3 = 120
        webhook(311, rule("T", 100, 10));
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());
        assertTrue(alertedPairs(received().get(0)).contains("T|1|120"));
    }

    /** The same spread does not reach a threshold above the total. */
    @Test
    public void tenDayWindowStaysQuietBelowThreshold() throws Exception {
        collection(1, "c1");
        for (int h = 1; h < 10 * 24; h += 6) counts(1, "T", h, 3);
        webhook(312, rule("T", 120, 10));      // total is exactly 120, and the test is strict >
        SensitiveDataThresholdAlerts.run();
        assertEquals(0, received().size());
    }

    // ---- window edges -------------------------------------------------------

    /** The bucket at the far edge of the window is inside it. */
    @Test
    public void bucketExactlyAtTheWindowEdgeCounts() throws Exception {
        collection(1, "c1");
        counts(1, "T", 14 * 24, 50);
        webhook(320, rule("T", 10, 14));
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());
    }

    /** One hour past the edge is outside it. */
    @Test
    public void bucketOneHourPastTheEdgeDoesNot() throws Exception {
        collection(1, "c1");
        counts(1, "T", 14 * 24 + 1, 50);
        webhook(321, rule("T", 10, 14));
        SensitiveDataThresholdAlerts.run();
        assertEquals(0, received().size());
    }

    // ---- the cap ------------------------------------------------------------

    /** A rule at the cap is valid and queries the whole span. */
    @Test
    public void windowAtTheCapIsAcceptedAndCountsTheWholeSpan() throws Exception {
        collection(1, "c1");
        counts(1, "T", 89 * 24, 50);
        webhook(330, rule("T", 10, SensitiveDataAlertRule.MAX_WINDOW_DAYS));
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());
    }

    /** One day past the cap is rejected outright. */
    @Test
    public void windowPastTheCapIsRejected() throws Exception {
        collection(1, "c1");
        counts(1, "T", 1, 5000);
        webhook(331, rule("T", 10, SensitiveDataAlertRule.MAX_WINDOW_DAYS + 1));
        SensitiveDataThresholdAlerts.run();
        assertEquals(0, received().size());
    }

    // ---- thresholds ---------------------------------------------------------

    /** The threshold is strict: equal to it is not above it. */
    @Test
    public void thresholdIsStrictlyGreaterThan() throws Exception {
        collection(1, "c1");
        counts(1, "T", 1, 100);
        webhook(340, rule("T", 100, 10));
        SensitiveDataThresholdAlerts.run();
        assertEquals(0, received().size());

        counts(1, "T", 2, 1);
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());
    }

    // ---- the daily re-arm, which no longer follows the window ----------------

    /** A 60 day rule re-arms after a day, not after 60. */
    @Test
    public void longWindowStillReArmsAfterOneDay() throws Exception {
        collection(1, "c1");
        counts(1, "T", 1, 500);
        int id = webhook(350, rule("T", 10, 60));
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());

        SensitiveDataThresholdAlerts.run();                 // same day: quiet
        assertEquals(1, received().size());

        markAlerted(id, "T|1", now() - 25 * HOUR);          // a day later, still breaching
        SensitiveDataThresholdAlerts.run();
        assertEquals(2, received().size());
    }

    /** Just under a day stays quiet; just over it alerts. */
    @Test
    public void reArmBoundaryIsTwentyFourHours() throws Exception {
        collection(1, "c1");
        counts(1, "T", 1, 500);
        int id = webhook(351, rule("T", 10, 30));

        markAlerted(id, "T|1", now() - 23 * HOUR);
        SensitiveDataThresholdAlerts.run();
        assertEquals("23h since the last alert: still quiet", 0, received().size());

        markAlerted(id, "T|1", now() - 25 * HOUR);
        SensitiveDataThresholdAlerts.run();
        assertEquals("25h since the last alert: alerts again", 1, received().size());
    }

    /** Re-arming is per pair: one collection going quiet does not silence another. */
    @Test
    public void reArmIsPerCollection() throws Exception {
        collection(1, "c1"); collection(2, "c2");
        counts(1, "T", 1, 500);
        counts(2, "T", 1, 500);
        int id = webhook(352, rule("T", 10, 30));
        markAlerted(id, "T|1", now() - HOUR);        // c1 alerted an hour ago
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());
        assertEquals(Collections.singleton("T|2|500"), alertedPairs(received().get(0)));
    }


    /**
     * The day passing is necessary but not sufficient. Each run recomputes the trailing window
     * from the current moment, so a pair only alerts again if it is STILL over the threshold
     * across the last {@code windowDays} as measured now - not merely because a day has gone by.
     */
    @Test
    public void reArmAloneDoesNotAlertWhenTheWindowNoLongerBreaches() throws Exception {
        collection(1, "c1");
        counts(1, "T", 5 * 24 + 2, 500);             // the spike has aged out of a 5 day window
        int id = webhook(353, rule("T", 100, 5));
        markAlerted(id, "T|1", now() - 25 * HOUR);   // and a day has passed since the last alert
        SensitiveDataThresholdAlerts.run();
        assertEquals("a day passing does not alert on its own", 0, received().size());

        counts(1, "T", 2, 500);                      // fresh traffic inside the window
        SensitiveDataThresholdAlerts.run();
        assertEquals("it alerts once the trailing window breaches again", 1, received().size());
        assertTrue(alertedPairs(received().get(0)).contains("T|1|500"));
    }

    /** Falling back under the threshold inside the window silences it, day or no day. */
    @Test
    public void droppingBelowTheThresholdSilencesItEvenAfterADay() throws Exception {
        collection(1, "c1");
        counts(1, "T", 2, 60);                       // inside the window but under 100
        int id = webhook(354, rule("T", 100, 5));
        markAlerted(id, "T|1", now() - 25 * HOUR);
        SensitiveDataThresholdAlerts.run();
        assertEquals(0, received().size());
    }

    // ---- several rules at once ----------------------------------------------

    /** One type with two windows: only the window that reaches its threshold alerts. */
    @Test
    public void sameTypeWithTwoWindowsReportsOnlyTheOneThatBreaches() throws Exception {
        collection(1, "c1");
        counts(1, "T", 20 * 24, 500);                 // 20 days back: inside 30d, outside 2d
        webhook(360, rule("T", 100, 2), rule("T", 100, 30));
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());
        assertEquals(Collections.singleton("T|1|500"), alertedPairs(received().get(0)));
    }

    /** Rules of different lengths over different types travel in one payload. */
    @Test
    public void rulesOfDifferentLengthsBatchIntoOneRequest() throws Exception {
        collection(1, "c1"); collection(2, "c2");
        counts(1, "A", 36, 300);                      // 1.5 days back
        counts(2, "B", 20 * 24, 300);                 // 20 days back
        webhook(361, rule("A", 10, 2), rule("B", 10, 30));
        SensitiveDataThresholdAlerts.run();
        assertEquals("both rules travel in a single request", 1, received().size());
        Set<String> pairs = alertedPairs(received().get(0));
        assertTrue(pairs.contains("A|1|300"));
        assertTrue(pairs.contains("B|2|300"));
    }

    /** Collections are judged independently against the same rule. */
    @Test
    public void onlyCollectionsOverTheThresholdAppear() throws Exception {
        collection(1, "c1"); collection(2, "c2"); collection(3, "c3");
        counts(1, "T", 10, 500);
        counts(2, "T", 10, 50);                       // under
        counts(3, "T", 10, 101);
        webhook(370, rule("T", 100, 14));
        SensitiveDataThresholdAlerts.run();
        assertEquals(1, received().size());
        Set<String> pairs = alertedPairs(received().get(0));
        assertTrue(pairs.contains("T|1|500"));
        assertTrue(pairs.contains("T|3|101"));
        assertEquals(2, pairs.size());
    }
}
