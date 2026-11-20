package com.akto.notifications;

import com.akto.dao.SensitiveDataCountDao;
import com.akto.dto.notifications.SensitiveDataAlertRule;
import com.akto.dto.traffic.SensitiveDataCount;
import com.akto.notifications.SensitiveDataThresholdAlerts.Breach;
import com.akto.notifications.SensitiveDataThresholdAlerts.Role;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SensitiveDataThresholdAlertsTest {

    private static final int NOW = 1791536400 + 1800;
    private static final int DAY = 86400;

    private static final int PROFILE_SERVICE = 1;
    private static final int PROFILE_API = 2;
    private static final int ORDER_API = 3;
    private static final int SEARCH_API = 4;

    /** Answers from a fixed set of caller-to-callee totals and records what it was asked. */
    private static class FakeEdges implements SensitiveDataThresholdAlerts.EdgeSource {
        final Map<String, List<SensitiveDataCountDao.Edge>> edges = new HashMap<>();
        final List<String> asked = new ArrayList<>();

        FakeEdges put(String subType, int sender, int receiver, long count) {
            edges.computeIfAbsent(subType, k -> new ArrayList<>())
                    .add(new SensitiveDataCountDao.Edge(sender, receiver, count));
            return this;
        }

        @Override
        public List<SensitiveDataCountDao.Edge> edgeCountsSince(String subType, int fromHour) {
            asked.add(subType + "@" + fromHour);
            return edges.getOrDefault(subType, Collections.<SensitiveDataCountDao.Edge>emptyList());
        }
    }

    private static Map<String, Long> totalsByKey(List<Breach> breaches) {
        Map<String, Long> got = new LinkedHashMap<>();
        for (Breach b : breaches) got.put(b.alertKey(), b.count);
        return got;
    }

    /**
     * The worked example from the requirement: profile-service fans out to three services, and the one
     * alert for the sender carries the same traffic the three receiver alerts do.
     */
    @Test
    public void reproducesTheFanOutExample() {
        FakeEdges edges = new FakeEdges()
                .put("EMAIL_EMPLOYEE", PROFILE_SERVICE, PROFILE_API, 500)
                .put("EMAIL_EMPLOYEE", PROFILE_SERVICE, ORDER_API, 200)
                .put("EMAIL_EMPLOYEE", PROFILE_SERVICE, SEARCH_API, 1000);

        List<Breach> breaches = SensitiveDataThresholdAlerts.findBreaches(
                Collections.singletonList(new SensitiveDataAlertRule("EMAIL_EMPLOYEE", 100, 7)),
                null, NOW, edges);

        Map<String, Long> expected = new LinkedHashMap<>();
        expected.put("EMAIL_EMPLOYEE|SENDER|" + PROFILE_SERVICE, 1700L);     // summed across destinations
        expected.put("EMAIL_EMPLOYEE|RECEIVER|" + PROFILE_API, 500L);
        expected.put("EMAIL_EMPLOYEE|RECEIVER|" + ORDER_API, 200L);
        expected.put("EMAIL_EMPLOYEE|RECEIVER|" + SEARCH_API, 1000L);
        assertEquals(expected, totalsByKey(breaches));
    }

    /** Several callers into one callee: the receiver sums them, each sender keeps its own total. */
    @Test
    public void fanInSumsOnTheReceiverAndSeparatesSenders() {
        FakeEdges edges = new FakeEdges()
                .put("EMAIL_X", PROFILE_SERVICE, PROFILE_API, 60)
                .put("EMAIL_X", ORDER_API, PROFILE_API, 70);

        List<Breach> breaches = SensitiveDataThresholdAlerts.findBreaches(
                Collections.singletonList(new SensitiveDataAlertRule("EMAIL_X", 50, 7)), null, NOW, edges);

        Map<String, Long> expected = new LinkedHashMap<>();
        expected.put("EMAIL_X|SENDER|" + PROFILE_SERVICE, 60L);
        expected.put("EMAIL_X|SENDER|" + ORDER_API, 70L);
        expected.put("EMAIL_X|RECEIVER|" + PROFILE_API, 130L);
        assertEquals(expected, totalsByKey(breaches));
    }

    /** Each rule applies its own threshold, and looks back over its own window. */
    @Test
    public void everyServiceAboveItsRulesOwnThreshold() {
        FakeEdges edges = new FakeEdges()
                .put("EMAIL_X", PROFILE_SERVICE, PROFILE_API, 42)
                .put("EMAIL_X", ORDER_API, SEARCH_API, 30)      // 30 is not "more than 30"
                .put("PASSPORT_X", PROFILE_SERVICE, PROFILE_API, 6);

        List<Breach> breaches = SensitiveDataThresholdAlerts.findBreaches(Arrays.asList(
                new SensitiveDataAlertRule("EMAIL_X", 30, 7),
                new SensitiveDataAlertRule("PASSPORT_X", 5, 30)), null, NOW, edges);

        Map<String, Long> expected = new LinkedHashMap<>();
        expected.put("EMAIL_X|SENDER|" + PROFILE_SERVICE, 42L);
        expected.put("EMAIL_X|RECEIVER|" + PROFILE_API, 42L);
        expected.put("PASSPORT_X|SENDER|" + PROFILE_SERVICE, 6L);
        expected.put("PASSPORT_X|RECEIVER|" + PROFILE_API, 6L);
        assertEquals(expected, totalsByKey(breaches));

        assertEquals(Arrays.asList(
                "EMAIL_X@" + SensitiveDataCount.hourOf(NOW - 7 * DAY),
                "PASSPORT_X@" + SensitiveDataCount.hourOf(NOW - 30 * DAY)), edges.asked);
    }

    /**
     * A service can breach as a sender and as a receiver at the same time. Those are two alerts,
     * and suppressing one must not suppress the other.
     */
    @Test
    public void sendingAndReceivingAreSuppressedSeparately() {
        FakeEdges edges = new FakeEdges()
                .put("EMAIL_X", PROFILE_API, SEARCH_API, 80)      // profile-api as a sender
                .put("EMAIL_X", PROFILE_SERVICE, PROFILE_API, 90);      // and as a receiver

        Map<String, Integer> alertedAt = new HashMap<>();
        alertedAt.put("EMAIL_X|SENDER|" + PROFILE_API, NOW - 3600);   // quiet: alerted within the day

        List<Breach> breaches = SensitiveDataThresholdAlerts.findBreaches(
                Collections.singletonList(new SensitiveDataAlertRule("EMAIL_X", 50, 7)), alertedAt, NOW, edges);

        Map<String, Long> got = totalsByKey(breaches);
        assertEquals(90L, (long) got.get("EMAIL_X|RECEIVER|" + PROFILE_API));
        assertTrue("sender side was alerted an hour ago and must stay quiet",
                !got.containsKey("EMAIL_X|SENDER|" + PROFILE_API));
    }

    /** A day after alerting, the same breach alerts again. */
    @Test
    public void alertsAgainOnceADayHasPassed() {
        FakeEdges edges = new FakeEdges().put("EMAIL_X", PROFILE_SERVICE, PROFILE_API, 90);

        Map<String, Integer> alertedAt = new HashMap<>();
        alertedAt.put("EMAIL_X|SENDER|" + PROFILE_SERVICE, NOW - 2 * DAY);

        List<Breach> breaches = SensitiveDataThresholdAlerts.findBreaches(
                Collections.singletonList(new SensitiveDataAlertRule("EMAIL_X", 50, 7)), alertedAt, NOW, edges);

        assertTrue(totalsByKey(breaches).containsKey("EMAIL_X|SENDER|" + PROFILE_SERVICE));
    }

    @Test
    public void skipsInvalidRulesWithoutQuerying() {
        FakeEdges edges = new FakeEdges().put("EMAIL_X", PROFILE_SERVICE, PROFILE_API, 1000);
        List<SensitiveDataAlertRule> rules = Arrays.asList(
                null,
                new SensitiveDataAlertRule("", 30, 7),
                new SensitiveDataAlertRule("EMAIL_X", 0, 7),
                new SensitiveDataAlertRule("EMAIL_X", 30, 0),
                new SensitiveDataAlertRule("EMAIL_X", 30, SensitiveDataAlertRule.MAX_WINDOW_DAYS + 1));

        assertTrue(SensitiveDataThresholdAlerts.findBreaches(rules, null, NOW, edges).isEmpty());
        assertTrue(edges.asked.isEmpty());
        assertTrue(SensitiveDataThresholdAlerts.findBreaches(null, null, NOW, edges).isEmpty());
    }

    /** The role is part of the key, and the key stays usable as a Mongo field name. */
    @Test
    public void alertKeyIsSafeAsAFieldName() {
        assertEquals("A_B_C|SENDER|-12", SensitiveDataThresholdAlerts.keyFor("A.B$C", Role.SENDER, -12));
        assertEquals("A_B_C|RECEIVER|-12", SensitiveDataThresholdAlerts.keyFor("A.B$C", Role.RECEIVER, -12));
    }

    /** Every alert names both ends, in the text and in the structured data. */
    @Test
    public void payloadNamesBothEnds() {
        FakeEdges edges = new FakeEdges()
                .put("EMAIL_X", PROFILE_SERVICE, PROFILE_API, 500)
                .put("EMAIL_X", PROFILE_SERVICE, ORDER_API, 200);

        List<Breach> breaches = SensitiveDataThresholdAlerts.findBreaches(
                Collections.singletonList(new SensitiveDataAlertRule("EMAIL_X", 100, 7)), null, NOW, edges);

        Map<Integer, String> names = new HashMap<>();
        names.put(PROFILE_SERVICE, "profile-service");
        names.put(PROFILE_API, "profile-api");
        names.put(ORDER_API, "order-api");

        String payload = SensitiveDataThresholdAlerts.buildPayload(breaches, names);
        Map<?, ?> json = new Gson().fromJson(payload, Map.class);
        String text = (String) json.get("text");

        assertEquals("SENSITIVE_DATA_THRESHOLD_ALERTS", json.get("alertType"));
        assertTrue(text, text.contains("profile-service sent 700 events in the last 7 days (threshold 100)"));
        assertTrue(text, text.contains("profile-service -> profile-api: 500"));
        assertTrue(text, text.contains("profile-service -> order-api: 200"));
        assertTrue(text, text.contains("profile-api received 500 events"));

        Map<?, ?> senderAlert = null;
        for (Object o : (List<?>) json.get("alerts")) {
            Map<?, ?> a = (Map<?, ?>) o;
            if ("SENDER".equals(a.get("role")) && "profile-service".equals(a.get("apiCollectionName"))) senderAlert = a;
        }
        assertTrue("expected a sender alert for profile-service", senderAlert != null);
        assertEquals(700.0, senderAlert.get("count"));

        // destinations, largest first, each named
        List<?> destinations = (List<?>) senderAlert.get("destinations");
        assertEquals(2, destinations.size());
        assertEquals("profile-api", ((Map<?, ?>) destinations.get(0)).get("apiCollectionName"));
        assertEquals(500.0, ((Map<?, ?>) destinations.get(0)).get("count"));
        assertEquals("order-api", ((Map<?, ?>) destinations.get(1)).get("apiCollectionName"));
    }
}
