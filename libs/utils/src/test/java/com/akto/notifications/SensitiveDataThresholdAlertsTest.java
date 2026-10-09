package com.akto.notifications;

import com.akto.dto.notifications.SensitiveDataAlertRule;
import com.akto.dto.traffic.SensitiveDataCount;
import com.google.gson.Gson;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SensitiveDataThresholdAlertsTest {

    private static final int NOW = 1791536400 + 1800;
    private static final int DAY = 86400;

    /** Answers from a fixed table and records what it was asked. */
    private static class FakeCounts implements SensitiveDataThresholdAlerts.CountSource {
        final Map<String, Map<Integer, Long>> totals = new HashMap<>();
        final List<String> asked = new ArrayList<>();

        FakeCounts put(String subType, int apiCollectionId, long total) {
            totals.computeIfAbsent(subType, k -> new HashMap<>()).put(apiCollectionId, total);
            return this;
        }

        @Override
        public Map<Integer, Long> countsByCollectionAbove(String subType, int fromHour, long moreThan) {
            asked.add(subType + "@" + fromHour + ">" + moreThan);
            Map<Integer, Long> above = new HashMap<>();
            for (Map.Entry<Integer, Long> e : totals.getOrDefault(subType, Collections.<Integer, Long>emptyMap()).entrySet()) {
                if (e.getValue() > moreThan) above.put(e.getKey(), e.getValue());
            }
            return above;
        }
    }

    @Test
    public void alertsEveryCollectionAboveEachRulesOwnThreshold() {
        FakeCounts counts = new FakeCounts()
                .put("EMAIL_X", 1, 42).put("EMAIL_X", 2, 30).put("EMAIL_X", 3, 31)
                .put("PASSPORT_X", 1, 6);
        List<SensitiveDataAlertRule> rules = Arrays.asList(
                new SensitiveDataAlertRule("EMAIL_X", 30, 7),
                new SensitiveDataAlertRule("PASSPORT_X", 5, 30));

        List<SensitiveDataThresholdAlerts.Breach> breaches =
                SensitiveDataThresholdAlerts.findBreaches(rules, null, NOW, counts);

        Map<String, Long> got = new HashMap<>();
        for (SensitiveDataThresholdAlerts.Breach b : breaches) got.put(b.alertKey(), b.count);
        Map<String, Long> expected = new HashMap<>();
        expected.put("EMAIL_X|1", 42L);
        expected.put("EMAIL_X|3", 31L);   // 30 is not "more than 30"
        expected.put("PASSPORT_X|1", 6L);
        assertEquals(expected, got);

        // each rule looks back over its own window, from the start of that hour
        assertEquals(Arrays.asList(
                "EMAIL_X@" + SensitiveDataCount.hourOf(NOW - 7 * DAY) + ">30",
                "PASSPORT_X@" + SensitiveDataCount.hourOf(NOW - 30 * DAY) + ">5"), counts.asked);
    }

    @Test
    public void alertsOncePerWindowPerTypeAndCollection() {
        FakeCounts counts = new FakeCounts().put("EMAIL_X", 1, 42).put("EMAIL_X", 2, 50);
        List<SensitiveDataAlertRule> rules = Collections.singletonList(new SensitiveDataAlertRule("EMAIL_X", 30, 7));

        Map<String, Integer> alertedAt = new HashMap<>();
        alertedAt.put("EMAIL_X|1", NOW - 3 * DAY);   // within the window: stays quiet
        alertedAt.put("EMAIL_X|2", NOW - 8 * DAY);   // window passed: alerts again

        List<SensitiveDataThresholdAlerts.Breach> breaches =
                SensitiveDataThresholdAlerts.findBreaches(rules, alertedAt, NOW, counts);

        assertEquals(1, breaches.size());
        assertEquals("EMAIL_X|2", breaches.get(0).alertKey());
    }

    @Test
    public void skipsInvalidRulesWithoutQuerying() {
        FakeCounts counts = new FakeCounts().put("EMAIL_X", 1, 1000);
        List<SensitiveDataAlertRule> rules = Arrays.asList(
                null,
                new SensitiveDataAlertRule("", 30, 7),
                new SensitiveDataAlertRule("EMAIL_X", 0, 7),
                new SensitiveDataAlertRule("EMAIL_X", 30, 0),
                new SensitiveDataAlertRule("EMAIL_X", 30, SensitiveDataAlertRule.MAX_WINDOW_DAYS + 1));

        assertTrue(SensitiveDataThresholdAlerts.findBreaches(rules, null, NOW, counts).isEmpty());
        assertTrue(counts.asked.isEmpty());
        assertTrue(SensitiveDataThresholdAlerts.findBreaches(null, null, NOW, counts).isEmpty());
    }

    @Test
    public void alertKeyIsSafeAsAFieldName() {
        assertEquals("A_B_C|-12", SensitiveDataThresholdAlerts.keyFor("A.B$C", -12));
    }

    @Test
    public void payloadCarriesSlackTextAndStructuredAlerts() {
        FakeCounts counts = new FakeCounts().put("EMAIL_X", 7, 42);
        List<SensitiveDataThresholdAlerts.Breach> breaches = SensitiveDataThresholdAlerts.findBreaches(
                Collections.singletonList(new SensitiveDataAlertRule("EMAIL_X", 30, 7)), null, NOW, counts);

        String payload = SensitiveDataThresholdAlerts.buildPayload(breaches, Collections.singletonMap(7, "booking-api"));
        Map<?, ?> json = new Gson().fromJson(payload, Map.class);

        assertEquals("SENSITIVE_DATA_THRESHOLD_ALERTS", json.get("alertType"));
        assertTrue(((String) json.get("text")).contains("EMAIL_X in booking-api: 42 events in the last 7 days (threshold 30)"));
        Map<?, ?> alert = (Map<?, ?>) ((List<?>) json.get("alerts")).get(0);
        assertEquals("booking-api", alert.get("apiCollectionName"));
        assertEquals(42.0, alert.get("count"));
    }
}
