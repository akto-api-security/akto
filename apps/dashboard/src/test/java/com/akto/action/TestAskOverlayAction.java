package com.akto.action;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.service.insights.InsightId;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.threat_detection.ThreatDetectionBackendClient;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers AskOverlayAction#fetchAskOverlay and its private helpers (defaultGroupsForDomain,
 * parseDomain, parseGroups, clamp) via reflection where the public entry point doesn't
 * surface enough to assert on directly. See apps/dashboard/src/main/java/com/akto/service/ask/CLAUDE.md
 * for the feature's domain-scoping table this file exercises.
 */
public class TestAskOverlayAction extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        Context.contextSource.remove();
    }

    private AskOverlayAction newAction() {
        return new AskOverlayAction();
    }

    // ── fetchAskOverlay() — public entry point ─────────────────────────────────────────

    @Test
    public void testFetchAskOverlay_happyPath_defaultDomainAndGroups_returnsSuccessWithApiTiles() {
        AskOverlayAction action = newAction();
        action.setDomain("API");
        String result = action.fetchAskOverlay();
        assertEquals("SUCCESS", result);
        assertNotNull(action.getAskOverlay());
        assertNotNull(action.getAskOverlay().getRecommendations());
        // computeApi() always returns exactly 4 tiles: open_criticals, unauth_sensitive,
        // never_tested, sensitive_data_types.
        assertEquals(4, action.getAskOverlay().getRecommendations().size());
    }

    @Test
    public void testFetchAskOverlay_unknownGroupName_returnsErrorWithMessage() {
        AskOverlayAction action = newAction();
        action.setDomain("API");
        action.setGroups(Collections.singletonList("NOT_A_REAL_GROUP"));

        String result = action.fetchAskOverlay();

        assertEquals("ERROR", result);
        Collection<String> errors = action.getActionErrors();
        assertFalse(errors.isEmpty());
        boolean found = false;
        for (String err : errors) {
            if (err.contains("Unknown insight group:")) {
                found = true;
                break;
            }
        }
        assertTrue("Expected an action error containing 'Unknown insight group:', got: " + errors, found);
    }

    @Test
    public void testFetchAskOverlay_domainNull_degradesLenientlyToApi_notError() {
        AskOverlayAction action = newAction();
        action.setDomain(null);
        assertEquals("SUCCESS", action.fetchAskOverlay());
    }

    @Test
    public void testFetchAskOverlay_domainEmpty_degradesLenientlyToApi_notError() {
        AskOverlayAction action = newAction();
        action.setDomain("");
        assertEquals("SUCCESS", action.fetchAskOverlay());
    }

    @Test
    public void testFetchAskOverlay_domainGarbage_degradesLenientlyToApi_notError() {
        AskOverlayAction action = newAction();
        action.setDomain("not_a_real_domain");
        assertEquals("SUCCESS", action.fetchAskOverlay());
    }

    /**
     * All 6 CONTEXT_SOURCE values must succeed. AGENTIC/MCP/GEN_AI additionally exercise
     * RecommendationCatalog.computeAgentic(), whose threatActivity() tile calls the real
     * threat-detection-backend (https://tbs.akto.io by default — see ThreatDetectionBackendClient
     * .backendUrl()). That's a real external host with no guarantee of reachability or bounded
     * latency in a CI sandbox, so it's stubbed here to fail fast rather than risk a slow/hanging
     * test; threatActivity()'s own try/catch is what's expected to turn that failure into a
     * zero-count tile without the request as a whole failing.
     */
    @Test
    public void testFetchAskOverlay_allValidContextSources_returnSuccess() {
        try (MockedStatic<ThreatDetectionBackendClient> threatMock = mockStatic(ThreatDetectionBackendClient.class)) {
            threatMock.when(() -> ThreatDetectionBackendClient.listMaliciousRequests(
                            anyInt(), anyInt(), anyInt(), anyInt(), any(), any(), any()))
                    .thenThrow(new RuntimeException("stubbed — no live threat-detection-backend in tests"));

            for (CONTEXT_SOURCE cs : CONTEXT_SOURCE.values()) {
                AskOverlayAction action = newAction();
                action.setDomain(cs.name());
                assertEquals("Domain " + cs.name() + " should succeed", "SUCCESS", action.fetchAskOverlay());
                assertNotNull(action.getAskOverlay());
            }
        } catch (Exception e) {
            fail("Unexpected checked exception setting up threat backend mock: " + e.getMessage());
        }
    }

    @Test
    public void testFetchAskOverlay_tileAndFeedLimitsBeyondMax_clampedSilently_doesNotBlowUp() {
        AskOverlayAction action = newAction();
        action.setDomain("API");
        action.setTileLimit(999);
        action.setFeedLimit(999);

        String result = action.fetchAskOverlay();

        assertEquals("SUCCESS", result);
        assertNotNull(action.getAskOverlay());
        // MAX_TILE_LIMIT is 12 — a silently-clamped tileLimit must never let more than that
        // through, even though 999 was requested.
        assertTrue(action.getAskOverlay().getInsightTiles().size() <= 12);
    }

    /**
     * buildContext() does `new InsightContext(Context.accountId.get(), Context.userId.get(), ...)`
     * against an InsightContext whose accountId/userId fields are primitive ints. With
     * Context.userId unset (a real scenario for a background job or a request UserDetailsFilter
     * never touched), Context.userId.get() returns null and unboxing it throws NullPointerException
     * from inside buildContext() — before InsightService.buildAskOverlay is ever entered. This
     * verifies that failure is caught by fetchAskOverlay()'s outer catch(Exception), degrading to
     * ERROR with a non-null action error, rather than propagating out of the action.
     */
    @Test
    public void testFetchAskOverlay_missingUserId_degradesToErrorNotPropagatedException() {
        Context.userId.remove();
        try {
            AskOverlayAction action = newAction();
            action.setDomain("API");

            String result = action.fetchAskOverlay();

            assertEquals("ERROR", result);
            assertNull(action.getAskOverlay());
            assertFalse(action.getActionErrors().isEmpty());
        } finally {
            Context.userId.set(1);
        }
    }

    // ── Private helpers via reflection ──────────────────────────────────────────────────

    private Object invokePrivate(Object target, String name, Class<?>[] paramTypes, Object... args) throws Exception {
        Method m = AskOverlayAction.class.getDeclaredMethod(name, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    @Test
    public void testParseDomain_nullEmptyAndGarbage_defaultToApi() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {String.class};
        assertEquals(CONTEXT_SOURCE.API, invokePrivate(action, "parseDomain", sig, (Object) null));
        assertEquals(CONTEXT_SOURCE.API, invokePrivate(action, "parseDomain", sig, ""));
        assertEquals(CONTEXT_SOURCE.API, invokePrivate(action, "parseDomain", sig, "not_a_real_domain"));
    }

    @Test
    public void testParseDomain_validValues_caseInsensitive() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {String.class};
        assertEquals(CONTEXT_SOURCE.AGENTIC, invokePrivate(action, "parseDomain", sig, "agentic"));
        assertEquals(CONTEXT_SOURCE.ENDPOINT, invokePrivate(action, "parseDomain", sig, "ENDPOINT"));
        assertEquals(CONTEXT_SOURCE.DAST, invokePrivate(action, "parseDomain", sig, "Dast"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultGroupsForDomain_agenticMcpGenAi_returnAtlasAndGuardrail() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {CONTEXT_SOURCE.class};
        Set<InsightId.Group> expected = new HashSet<>(Arrays.asList(
                InsightId.Group.ATLAS_DISCOVERY, InsightId.Group.GUARDRAIL_VIOLATIONS));

        for (CONTEXT_SOURCE cs : Arrays.asList(CONTEXT_SOURCE.AGENTIC, CONTEXT_SOURCE.MCP, CONTEXT_SOURCE.GEN_AI)) {
            Set<InsightId.Group> actual = (Set<InsightId.Group>) invokePrivate(action, "defaultGroupsForDomain", sig, cs);
            assertEquals("Mismatch for domain " + cs, expected, actual);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultGroupsForDomain_endpoint_returnsGuardrailOnly() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {CONTEXT_SOURCE.class};
        Set<InsightId.Group> actual =
                (Set<InsightId.Group>) invokePrivate(action, "defaultGroupsForDomain", sig, CONTEXT_SOURCE.ENDPOINT);
        assertEquals(Collections.singleton(InsightId.Group.GUARDRAIL_VIOLATIONS), actual);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultGroupsForDomain_apiDastAndDefault_returnApiAndTestingPosture() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {CONTEXT_SOURCE.class};
        Set<InsightId.Group> expected = new HashSet<>(Arrays.asList(
                InsightId.Group.API_POSTURE, InsightId.Group.TESTING_POSTURE));

        for (CONTEXT_SOURCE cs : Arrays.asList(CONTEXT_SOURCE.API, CONTEXT_SOURCE.DAST)) {
            Set<InsightId.Group> actual = (Set<InsightId.Group>) invokePrivate(action, "defaultGroupsForDomain", sig, cs);
            assertEquals("Mismatch for domain " + cs, expected, actual);
        }
    }

    @Test
    public void testClamp_valueBelowOrEqualZero_usesDefault() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {int.class, int.class, int.class, int.class};
        assertEquals(3, invokePrivate(action, "clamp", sig, 0, 3, 1, 12));
        assertEquals(3, invokePrivate(action, "clamp", sig, -5, 3, 1, 12));
    }

    @Test
    public void testClamp_valueBelowMin_clampsToMin() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {int.class, int.class, int.class, int.class};
        // value=1 is > 0 so the default is NOT applied, but 1 is still below min=5.
        assertEquals(5, invokePrivate(action, "clamp", sig, 1, 10, 5, 12));
    }

    @Test
    public void testClamp_valueAboveMax_clampsToMax() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {int.class, int.class, int.class, int.class};
        assertEquals(12, invokePrivate(action, "clamp", sig, 999, 3, 1, 12));
        assertEquals(50, invokePrivate(action, "clamp", sig, 999, 10, 1, 50));
    }

    @Test
    public void testClamp_valueInRange_unchanged() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {int.class, int.class, int.class, int.class};
        assertEquals(7, invokePrivate(action, "clamp", sig, 7, 3, 1, 12));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseGroups_explicitGroups_overridesDomainDefault() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {List.class, CONTEXT_SOURCE.class};

        // API's own default is {API_POSTURE, TESTING_POSTURE} — an explicit groups list must
        // override that, not merge with it.
        Set<InsightId.Group> actual = (Set<InsightId.Group>) invokePrivate(action, "parseGroups", sig,
                Collections.singletonList("ATLAS_DISCOVERY"), CONTEXT_SOURCE.API);

        assertEquals(Collections.singleton(InsightId.Group.ATLAS_DISCOVERY), actual);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testParseGroups_nullOrEmpty_fallsBackToDomainDefault() throws Exception {
        AskOverlayAction action = newAction();
        Class<?>[] sig = {List.class, CONTEXT_SOURCE.class};
        Set<InsightId.Group> expected = new HashSet<>(Arrays.asList(
                InsightId.Group.API_POSTURE, InsightId.Group.TESTING_POSTURE));

        Set<InsightId.Group> actualNull =
                (Set<InsightId.Group>) invokePrivate(action, "parseGroups", sig, (Object) null, CONTEXT_SOURCE.API);
        Set<InsightId.Group> actualEmpty =
                (Set<InsightId.Group>) invokePrivate(action, "parseGroups", sig, Collections.emptyList(), CONTEXT_SOURCE.API);

        assertEquals(expected, actualNull);
        assertEquals(expected, actualEmpty);
    }
}
