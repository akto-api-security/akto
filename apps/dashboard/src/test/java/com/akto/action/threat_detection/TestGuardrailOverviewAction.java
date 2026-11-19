package com.akto.action.threat_detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.Test;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailActionCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailActionCountsResponse.ActionCounts;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailActionCountsResponse.DayCounts;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailHostPolicyCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailPolicyCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailTriageTimeResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.service.guardrails.GuardrailOverviewHeatmap;
import com.google.protobuf.Message;

/* The overview action sends the page range and filters to the threat backend, and shows nothing to a limited user it cannot scope. */
public class TestGuardrailOverviewAction {

    static class FakeAction extends GuardrailOverviewAction {
        Message reply;
        boolean limited;
        Map<String, Object> hostScope;
        String calledPath;
        Map<String, Object> sentBody;

        @Override
        protected boolean isLimitedToOwnAgents() {
            return limited;
        }

        @Override
        protected Map<String, Object> backendHostScope() {
            return hostScope;
        }

        // support-* hosts are one agent; any other host is an agent of its own
        @Override
        protected GuardrailOverviewHeatmap.AgentResolver agentResolver() {
            return host -> host.startsWith("support-")
                ? new GuardrailOverviewHeatmap.Agent("collection:1", "Support copilot", "Production")
                : new GuardrailOverviewHeatmap.Agent("host:" + host, host, "");
        }

        @Override
        protected <T extends Message> T postDashboardApi(String path, Map<String, Object> body, Class<T> responseClass) {
            calledPath = path;
            sentBody = body;
            return responseClass.cast(reply);
        }
    }

    private static FetchGuardrailActionCountsResponse twoDays() {
        return FetchGuardrailActionCountsResponse.newBuilder()
            .addDays(DayCounts.newBuilder().setTs(1000).setCounts(ActionCounts.newBuilder().setBlocked(3).setFlagged(1)))
            .addDays(DayCounts.newBuilder().setTs(2000).setCounts(ActionCounts.newBuilder().setAlerted(2).setFlagged(4)))
            .setTotal(ActionCounts.newBuilder().setBlocked(3).setAlerted(2).setFlagged(5))
            .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSendsRangeAndFiltersAndMapsTheCounts() {
        FakeAction action = new FakeAction();
        action.reply = twoDays();
        action.setStartTimestamp(100);
        action.setEndTimestamp(900);
        Map<String, Object> filters = new HashMap<>();
        filters.put("latestAttack", Collections.singletonList("Block prompt attacks"));
        filters.put("detected_at_time_range", Collections.singletonMap("start", 1)); // the page range wins
        action.setFilters(filters);

        assertEquals("SUCCESS", action.fetchGuardrailActionCounts());

        assertEquals("get_guardrail_action_counts", action.calledPath);
        Map<String, Object> filter = (Map<String, Object>) action.sentBody.get("filter");
        assertEquals(Collections.singletonList("Block prompt attacks"), filter.get("latestAttack"));
        Map<String, Object> range = (Map<String, Object>) filter.get("detected_at_time_range");
        assertEquals(100, range.get("start"));
        assertEquals(900, range.get("end"));
        assertFalse(action.sentBody.containsKey("hostScope"));

        assertEquals(2, action.getDays().size());
        assertEquals(1000, action.getDays().get(0).get("ts"));
        assertEquals(3, action.getDays().get(0).get("blocked"));
        assertEquals(2, action.getDays().get(1).get("alerted"));
        assertEquals(4, action.getDays().get(1).get("flagged"));
        assertFalse(action.getDays().get(1).containsKey("approval"));
        assertEquals(3, action.getTotal().get("blocked"));
        assertEquals(5, action.getTotal().get("flagged"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testLimitedUserIsScopedToTheirAgents() {
        FakeAction action = new FakeAction();
        action.reply = twoDays();
        action.limited = true;
        action.hostScope = Collections.singletonMap("hosts", Collections.singletonList("agent-a"));

        assertEquals("SUCCESS", action.fetchGuardrailActionCounts());

        assertEquals(action.hostScope, action.sentBody.get("hostScope"));
    }

    @Test
    public void testLimitedUserWithoutAScopeSeesNothingAndNoBackendCallIsMade() {
        FakeAction action = new FakeAction();
        action.reply = twoDays();
        action.limited = true;
        action.hostScope = null;

        assertEquals("SUCCESS", action.fetchGuardrailActionCounts());

        assertNull(action.calledPath);
        assertTrue(action.getDays().isEmpty());
        assertEquals(0, action.getTotal().get("blocked"));
    }

    @Test
    public void testBackendWithoutTheEndpointIsAnError() {
        FakeAction action = new FakeAction();
        action.reply = null;

        assertEquals("ERROR", action.fetchGuardrailActionCounts());

        assertFalse(action.getActionErrors().isEmpty());
        assertTrue(action.getDays().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTopPoliciesArePagedByTheBackend() {
        FakeAction action = new FakeAction();
        action.reply = FetchGuardrailPolicyCountsResponse.newBuilder()
            .addPolicies(FetchGuardrailPolicyCountsResponse.PolicyCount.newBuilder().setPolicy("PII").setCount(9))
            .addPolicies(FetchGuardrailPolicyCountsResponse.PolicyCount.newBuilder().setPolicy("Secrets").setCount(4))
            .setTotal(11)
            .build();
        action.setStartTimestamp(100);
        action.setEndTimestamp(900);
        action.setSkip(5);
        action.setLimit(2);

        assertEquals("SUCCESS", action.fetchGuardrailTopPolicies());

        assertEquals("get_guardrail_policy_counts", action.calledPath);
        assertEquals(5, action.sentBody.get("skip"));
        assertEquals(2, action.sentBody.get("limit"));
        assertEquals(2, action.getRows().size());
        assertEquals("PII", action.getRows().get(0).get("name"));
        assertEquals(9, action.getRows().get(0).get("count"));
        assertEquals(11L, action.getTotalRows());
    }

    @Test
    public void testHeatmapGroupsHostsIntoAgentsAndPagesThemItself() {
        FakeAction action = new FakeAction();
        action.reply = FetchGuardrailHostPolicyCountsResponse.newBuilder()
            .addRows(FetchGuardrailHostPolicyCountsResponse.HostPolicyCount.newBuilder().setHost("support-1").setPolicy("PII").setCount(10))
            .addRows(FetchGuardrailHostPolicyCountsResponse.HostPolicyCount.newBuilder().setHost("support-2").setPolicy("PII").setCount(5))
            .addRows(FetchGuardrailHostPolicyCountsResponse.HostPolicyCount.newBuilder().setHost("refund-bot").setPolicy("Secrets").setCount(30))
            .build();
        action.setSkip(1);
        action.setLimit(1);

        assertEquals("SUCCESS", action.fetchGuardrailHeatmap());

        // the backend returns every host pair, the page is cut here after the grouping
        assertEquals("get_guardrail_host_policy_counts", action.calledPath);
        assertFalse(action.sentBody.containsKey("skip"));
        assertFalse(action.sentBody.containsKey("limit"));
        assertEquals(Arrays.asList("Secrets", "PII"), action.getColumns());
        assertEquals(2L, action.getTotalRows());
        assertEquals(1, action.getRows().size());
        assertEquals("Support copilot", action.getRows().get(0).get("agent"));
        assertEquals(Arrays.asList(0L, 15L), action.getRows().get(0).get("cells"));
        assertEquals(Arrays.asList("support-1", "support-2"), action.getRows().get(0).get("hosts"));
        assertTrue(action.getOtherPolicies().isEmpty());
    }

    @Test
    public void testTriageTimeIsPassedThrough() {
        FakeAction action = new FakeAction();
        action.reply = FetchGuardrailTriageTimeResponse.newBuilder().setMedianSeconds(11520).setTriaged(8).build();

        assertEquals("SUCCESS", action.fetchGuardrailTriageTime());

        assertEquals("get_guardrail_triage_time", action.calledPath);
        assertEquals(11520, action.getTriage().get("medianSeconds"));
        assertEquals(8, action.getTriage().get("triaged"));
    }

    @Test
    public void testNeedsAttentionRowsCarryEverythingTheCardShows() {
        FakeAction action = new FakeAction();
        action.reply = ListMaliciousRequestsResponse.newBuilder()
            .addMaliciousEvents(ListMaliciousRequestsResponse.MaliciousEvent.newBuilder()
                .setId("e1").setSeverity("critical").setFilterId("Block prompt attacks").setHost("support-1")
                .setApiCollectionId(7).setDetectedAt(1700000000).setGroupCount(46)
                .addAllGroupEventIds(Arrays.asList("e1", "e2")))
            .addMaliciousEvents(ListMaliciousRequestsResponse.MaliciousEvent.newBuilder()
                .setId("e3").setFilterId("No financial advice").setHost("refund-bot").setDetectedAt(1700000100).setCaseOwner("a@b.com"))
            .setTotal(23)
            .build();
        action.setSkip(10);
        action.setLimit(5);

        assertEquals("SUCCESS", action.fetchGuardrailNeedsAttention());

        assertEquals("get_guardrail_needs_attention", action.calledPath);
        assertEquals(10, action.sentBody.get("skip"));
        assertEquals(23L, action.getTotalRows());
        Map<String, Object> grouped = action.getRows().get(0);
        assertEquals(Arrays.asList("e1", "e2"), grouped.get("eventIds"));
        assertEquals("CRITICAL", grouped.get("severity"));
        assertEquals(46, grouped.get("count"));
        assertEquals("Block prompt attacks", grouped.get("policy"));
        assertEquals(7, grouped.get("collectionId"));
        assertEquals(1700000000L, grouped.get("ts"));
        Map<String, Object> single = action.getRows().get(1);
        assertEquals(Collections.singletonList("e3"), single.get("eventIds"));
        assertEquals("HIGH", single.get("severity"));  // none recorded: the list's own default
        assertEquals(1, single.get("count"));
        assertEquals("a@b.com", single.get("owner"));
    }

    private static List<Supplier<String>> everyRowPanel(FakeAction action) {
        return Arrays.asList(
            action::fetchGuardrailTopPolicies, action::fetchGuardrailHeatmap,
            action::fetchGuardrailTriageTime, action::fetchGuardrailNeedsAttention);
    }

    @Test
    public void testLimitedUserWithoutAScopeSeesNothingInEveryPanel() {
        for (int i = 0; i < 4; i++) {
            FakeAction action = new FakeAction();
            action.reply = FetchGuardrailTriageTimeResponse.getDefaultInstance();
            action.limited = true;
            action.hostScope = null;

            assertEquals("SUCCESS", everyRowPanel(action).get(i).get());
            assertNull(action.calledPath);
        }
    }

    @Test
    public void testEveryPanelReportsABackendThatCannotAnswer() {
        for (int i = 0; i < 4; i++) {
            FakeAction action = new FakeAction();
            action.reply = null;
            assertEquals("ERROR", everyRowPanel(action).get(i).get());
            assertFalse(action.getActionErrors().isEmpty());
            assertTrue(action.getRows().isEmpty());
        }
    }
}
