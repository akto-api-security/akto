package com.akto.notifications;

import com.akto.dao.SensitiveDataCountDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.notifications.SensitiveDataAlertRule;
import com.akto.notifications.SensitiveDataThresholdAlerts.Breach;
import com.akto.notifications.SensitiveDataThresholdAlerts.Peer;
import com.akto.notifications.SensitiveDataThresholdAlerts.Role;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * How a collection is named in an alert, and what that means for real traffic.
 *
 * The fixture is ten hours of counts taken from a real deployment, with service and host names
 * replaced. Half its destinations are collections routed by Host with no service tag; naming by
 * tag alone left those as bare hashes and, worse, dropped every alert about them. Keeping the
 * shape of real traffic here means that cannot regress quietly.
 */
public class SensitiveDataThresholdAlertsNamingTest {

    private static final int NOW = 1791536400 + 1800;

    /**
     * One row per hour, as the counts are stored, with a [tag]/[NOTAG] marker on each end.
     *
     * hour, data type, sender, "->", receiver, count.
     */
    private static final String[] OBSERVED = {
        "2026-11-17T07:00  TOKEN  gateway[tag]  ->  metrics-collector[tag]   73",
        "2026-11-17T07:00  TOKEN  gateway[tag]  ->  metrics-collector-debug.test.internal[NOTAG]   35",
        "2026-11-17T07:00  TOKEN  messaging-server[tag]  ->  profile-service[tag]   14",
        "2026-11-17T07:00  TOKEN  order-query[tag]  ->  profile-service[tag]   3",
        "2026-11-17T07:00  TOKEN  order-query[tag]  ->  local.payment-service.svc:5000[NOTAG]   2",
        "2026-11-17T07:00  EMAIL_CUSTOMER  order-api[tag]  ->  ledger.test.internal[NOTAG]   2",
        "2026-11-17T07:00  TOKEN  payment-service[tag]  ->  central.payment-service.svc:5000[NOTAG]   1",
        "2026-11-17T07:00  USERNAME  search-proxy[tag]  ->  comms-workspace[tag]   1",
        "2026-11-17T08:00  TOKEN  gateway[tag]  ->  metrics-collector[tag]   57",
        "2026-11-17T08:00  TOKEN  messaging-server[tag]  ->  profile-service[tag]   54",
        "2026-11-17T08:00  TOKEN  gateway[tag]  ->  metrics-collector-debug.test.internal[NOTAG]   25",
        "2026-11-17T08:00  EMAIL_CUSTOMER  order-api[tag]  ->  ledger.test.internal[NOTAG]   10",
        "2026-11-17T08:00  TOKEN  order-query[tag]  ->  profile-service[tag]   2",
        "2026-11-17T08:00  TOKEN  order-query[tag]  ->  local.payment-service.svc:5000[NOTAG]   1",
        "2026-11-17T09:00  TOKEN  gateway[tag]  ->  metrics-collector[tag]   38",
        "2026-11-17T09:00  EMAIL_CUSTOMER  order-api[tag]  ->  ledger.test.internal[NOTAG]   6",
        "2026-11-17T09:00  TOKEN  gateway[tag]  ->  metrics-collector-debug.test.internal[NOTAG]   4",
        "2026-11-17T09:00  TOKEN  order-query[tag]  ->  profile-service[tag]   2",
        "2026-11-17T09:00  TOKEN  order-query[tag]  ->  local.payment-service.svc:5000[NOTAG]   2",
        "2026-11-17T09:00  USERNAME  search-proxy[tag]  ->  comms-workspace[tag]   1",
        "2026-11-17T09:00  EMAIL_EMPLOYEE  search-proxy[tag]  ->  comms-workspace[tag]   1",
        "2026-11-17T09:00  TOKEN  order-api[tag]  ->  profile-service[tag]   1",
        "2026-11-17T09:00  USERNAME  order-api[tag]  ->  profile-service[tag]   1",
        "2026-11-17T10:00  TOKEN  gateway[tag]  ->  metrics-collector[tag]   54",
        "2026-11-17T10:00  TOKEN  gateway[tag]  ->  metrics-collector-debug.test.internal[NOTAG]   11",
        "2026-11-17T10:00  TOKEN  order-query[tag]  ->  profile-service[tag]   3",
        "2026-11-17T10:00  EMAIL_CUSTOMER  order-api[tag]  ->  ledger.test.internal[NOTAG]   1",
        "2026-11-17T10:00  USERNAME  search-proxy[tag]  ->  comms-workspace[tag]   1",
        "2026-11-17T10:00  EMAIL_EMPLOYEE  search-proxy[tag]  ->  comms-workspace[tag]   1",
        "2026-11-17T11:00  TOKEN  gateway[tag]  ->  metrics-collector[tag]   68",
        "2026-11-17T11:00  TOKEN  order-query[tag]  ->  local.payment-service.svc:5000[NOTAG]   6",
        "2026-11-17T11:00  TOKEN  gateway[tag]  ->  metrics-collector-debug.test.internal[NOTAG]   3",
        "2026-11-17T11:00  EMAIL_CUSTOMER  order-api[tag]  ->  ledger.test.internal[NOTAG]   2",
        "2026-11-17T11:00  TOKEN  payment-service[tag]  ->  central.payment-service.svc:5000[NOTAG]   2",
        "2026-11-17T11:00  TOKEN  order-query[tag]  ->  profile-service[tag]   1",
        "2026-11-17T11:00  TOKEN  order-api[tag]  ->  profile-service[tag]   1",
        "2026-11-17T11:00  USERNAME  order-api[tag]  ->  profile-service[tag]   1",
        "2026-11-17T12:00  TOKEN  messaging-server[tag]  ->  profile-service[tag]   38",
        "2026-11-17T12:00  EMAIL_EMPLOYEE  messaging-server[tag]  ->  profile-service[tag]   16",
        "2026-11-17T12:00  TOKEN  gateway[tag]  ->  metrics-collector[tag]   10",
        "2026-11-17T12:00  TOKEN  gateway[tag]  ->  metrics-collector-debug.test.internal[NOTAG]   10",
        "2026-11-17T12:00  TOKEN  order-query[tag]  ->  local.payment-service.svc:5000[NOTAG]   1",
        "2026-11-17T12:00  TOKEN  order-query[tag]  ->  profile-service[tag]   1",
        "2026-11-17T13:00  TOKEN  gateway[tag]  ->  metrics-collector[tag]   26",
        "2026-11-17T13:00  TOKEN  gateway[tag]  ->  metrics-collector-debug.test.internal[NOTAG]   14",
        "2026-11-17T14:00  TOKEN  gateway[tag]  ->  metrics-collector[tag]   60",
        "2026-11-17T14:00  TOKEN  gateway[tag]  ->  metrics-collector-debug.test.internal[NOTAG]   39",
        "2026-11-17T14:00  TOKEN  gateway[tag]  ->  echo-service[tag]   22",
        "2026-11-17T14:00  TOKEN  order-query[tag]  ->  profile-service[tag]   3",
        "2026-11-17T14:00  TOKEN  order-query[tag]  ->  local.payment-service.svc:5000[NOTAG]   2",
        "2026-11-17T15:00  TOKEN  gateway[tag]  ->  metrics-collector[tag]   56",
        "2026-11-17T15:00  TOKEN  gateway[tag]  ->  metrics-collector-debug.test.internal[NOTAG]   7",
        "2026-11-17T15:00  TOKEN  order-query[tag]  ->  profile-service[tag]   2",
        "2026-11-17T15:00  USERNAME  auth-login[tag]  ->  profile-service[tag]   1",
        "2026-11-17T15:00  TOKEN  auth-login[tag]  ->  profile-service[tag]   1",
        "2026-11-17T16:00  TOKEN  gateway[tag]  ->  metrics-collector[tag]   17",
        "2026-11-17T16:00  TOKEN  gateway[tag]  ->  metrics-collector-debug.test.internal[NOTAG]   5",
    };

    /**
     * The ids the untagged collections really have.
     *
     * A tagged collection is keyed by the hash of its service tag, so its id can be derived. A
     * Host-routed one is not, so these are arbitrary, as they are in a real deployment.
     */
    private static final Map<String, Integer> HOST_IDS = new HashMap<>();
    static {
        HOST_IDS.put("metrics-collector-debug.test.internal", -1593409980);
        HOST_IDS.put("central.payment-service.svc:5000", 974118680);
        HOST_IDS.put("local.payment-service.svc:5000", 1462865762);
        HOST_IDS.put("ledger.test.internal", 1922662599);
    }

    private static boolean tagged(String token) {
        return token.endsWith("[tag]");
    }

    private static String labelOf(String token) {
        return token.substring(0, token.lastIndexOf('['));
    }

    private static int idOf(String token) {
        String label = labelOf(token);
        return tagged(token) ? label.hashCode() : HOST_IDS.get(label);
    }

    /** The collection document each end of the observed traffic resolves to. */
    private static ApiCollection collectionFor(String token) {
        ApiCollection c = new ApiCollection();
        c.setId(idOf(token));
        if (tagged(token)) {
            c.setServiceTag(labelOf(token));
            c.setName(labelOf(token));
        } else {
            // What a Host-routed collection holds: a host and nothing else.
            c.setHostName(labelOf(token));
        }
        return c;
    }

    private static class FakeEdges implements SensitiveDataThresholdAlerts.EdgeSource {
        final Map<String, List<SensitiveDataCountDao.Edge>> edges = new LinkedHashMap<>();

        @Override
        public List<SensitiveDataCountDao.Edge> edgeCountsSince(String subType, int fromHour) {
            return edges.getOrDefault(subType, Collections.<SensitiveDataCountDao.Edge>emptyList());
        }
    }

    private static FakeEdges observedEdges() {
        FakeEdges edges = new FakeEdges();
        for (String row : OBSERVED) {
            String[] f = row.split("\\s{2,}");
            edges.edges.computeIfAbsent(f[1], k -> new ArrayList<>())
                    .add(new SensitiveDataCountDao.Edge(idOf(f[2]), idOf(f[4]), Long.parseLong(f[5])));
        }
        return edges;
    }

    /** Every collection named in the observed traffic, as the alert would load it. */
    private static Map<Integer, String> observedNames() {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (String row : OBSERVED) {
            String[] f = row.split("\\s{2,}");
            for (String token : new String[] { f[2], f[4] }) {
                String label = SensitiveDataThresholdAlerts.label(collectionFor(token));
                if (label != null) names.put(idOf(token), label);
            }
        }
        return names;
    }

    @Test
    public void serviceTagWinsOverHostAndName() {
        ApiCollection c = new ApiCollection();
        c.setServiceTag("profile-service");
        c.setHostName("profile-service.svc.internal");
        c.setName("some other name");
        assertEquals("profile-service", SensitiveDataThresholdAlerts.label(c));
    }

    @Test
    public void hostIsUsedWhenThereIsNoServiceTag() {
        ApiCollection c = new ApiCollection();
        c.setHostName("metrics-collector-debug.test.internal");
        assertEquals("metrics-collector-debug.test.internal", SensitiveDataThresholdAlerts.label(c));
    }

    @Test
    public void blankServiceTagIsNotAName() {
        ApiCollection c = new ApiCollection();
        c.setServiceTag("   ");
        c.setHostName("metrics-collector-debug.test.internal");
        assertEquals("metrics-collector-debug.test.internal", SensitiveDataThresholdAlerts.label(c));
    }

    @Test
    public void nameIsTheLastResort() {
        ApiCollection c = new ApiCollection();
        c.setName("juice shop");
        assertEquals("juice shop", SensitiveDataThresholdAlerts.label(c));
    }

    /** Nothing readable means no name, which is what keeps a bare hash out of an alert. */
    @Test
    public void collectionWithNothingReadableHasNoName() {
        assertNull(SensitiveDataThresholdAlerts.label(new ApiCollection()));
    }

    /**
     * The regression this change exists for: naming by service tag alone left half of the real
     * destinations unnamed, and the filter in evaluate() then dropped every alert about them.
     */
    @Test
    public void hostRoutedDestinationsAreNamedAndSoSurviveTheFilter() {
        Map<Integer, String> names = observedNames();
        for (Map.Entry<String, Integer> host : HOST_IDS.entrySet()) {
            assertEquals("host-routed collection must be named by its host",
                    host.getKey(), names.get(host.getValue()));
        }
    }

    /** Every end of every observed edge resolves to something a reader can act on. */
    @Test
    public void everyEndOfObservedTrafficIsNamed() {
        Map<Integer, String> names = observedNames();
        Set<Integer> ends = new LinkedHashSet<>();
        for (String row : OBSERVED) {
            String[] f = row.split("\\s{2,}");
            ends.add(idOf(f[2]));
            ends.add(idOf(f[4]));
        }
        assertEquals("the ten hours hold 15 distinct services", 15, ends.size());
        for (int id : ends) {
            assertNotNull("unnamed collection " + id, names.get(id));
            assertFalse("a name must not be the id itself", String.valueOf(id).equals(names.get(id)));
        }
    }

    /**
     * The alert sent for the data type the requirement was written around.
     *
     * One sender view and one receiver view of the same 16 events, both ends named.
     */
    @Test
    public void employeeEmailAlertNamesBothEnds() {
        List<Breach> breaches = SensitiveDataThresholdAlerts.findBreaches(
                Collections.singletonList(new SensitiveDataAlertRule("EMAIL_EMPLOYEE", 10, 7)),
                null, NOW, observedEdges());

        Map<String, Long> totals = new LinkedHashMap<>();
        for (Breach b : breaches) totals.put(b.role + ":" + b.apiCollectionId, b.count);

        Map<String, Long> expected = new LinkedHashMap<>();
        expected.put(Role.SENDER + ":" + "messaging-server".hashCode(), 16L);
        expected.put(Role.RECEIVER + ":" + "profile-service".hashCode(), 16L);
        assertEquals("search-proxy's 2 events are below the threshold and must not alert",
                expected, totals);

        // Gson escapes > as \u003e, so the arrow is only visible once the payload is parsed back -
        // which is what the webhook's receiver does.
        String text = new Gson().fromJson(SensitiveDataThresholdAlerts.buildPayload(breaches, observedNames()),
                JsonObject.class).get("text").getAsString();
        assertTrue(text, text.contains("EMAIL_EMPLOYEE: messaging-server sent 16 events"));
        assertTrue(text, text.contains("messaging-server -> profile-service: 16"));
        assertTrue(text, text.contains("EMAIL_EMPLOYEE: profile-service received 16 events"));
    }

    /**
     * With every edge over the threshold, no part of the message falls back to an id.
     *
     * This is the end the client sees: the whole ten hours rendered as one alert body.
     */
    @Test
    public void noBareIdSurvivesIntoThePayload() {
        List<SensitiveDataAlertRule> rules = new ArrayList<>();
        for (String subType : observedEdges().edges.keySet()) {
            rules.add(new SensitiveDataAlertRule(subType, 1, 7));
        }

        List<Breach> breaches = SensitiveDataThresholdAlerts.findBreaches(
                rules, null, NOW, observedEdges());
        assertFalse(breaches.isEmpty());

        Map<Integer, String> names = observedNames();
        String payload = SensitiveDataThresholdAlerts.buildPayload(breaches, names);

        for (Breach b : breaches) {
            assertFalse("breach subject rendered as an id: " + b.apiCollectionId,
                    payload.contains("\"apiCollectionName\":\"" + b.apiCollectionId + "\""));
            for (Peer p : b.peers) {
                assertFalse("peer rendered as an id: " + p.apiCollectionId,
                        payload.contains("\"apiCollectionName\":\"" + p.apiCollectionId + "\""));
            }
        }
        assertTrue(payload.contains("metrics-collector-debug.test.internal"));
        assertTrue(payload.contains("ledger.test.internal"));
    }

    /**
     * Two hosts of one service stay two destinations.
     *
     * A host is not a service, so the fallback does not pool a service's two hosts into one
     * destination. Each is measured against the threshold alone - worth knowing before a
     * threshold is chosen.
     */
    @Test
    public void hostsOfOneServiceAreNotPooled() {
        List<Breach> breaches = SensitiveDataThresholdAlerts.findBreaches(
                Collections.singletonList(new SensitiveDataAlertRule("TOKEN", 1, 7)),
                null, NOW, observedEdges());

        Map<Integer, Long> receivers = new LinkedHashMap<>();
        for (Breach b : breaches) {
            if (b.role == Role.RECEIVER) receivers.put(b.apiCollectionId, b.count);
        }
        assertEquals(Long.valueOf(14), receivers.get(HOST_IDS.get("local.payment-service.svc:5000")));
        assertEquals(Long.valueOf(3), receivers.get(HOST_IDS.get("central.payment-service.svc:5000")));
    }
}
