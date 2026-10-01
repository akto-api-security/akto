package com.akto.utils.crons;

import com.akto.DaoInit;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.service.insights.HostCollectionResolver;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.akto.utils.crons.AgenticPostureScoreCron.guardrailSeverityScore;
import static com.akto.utils.crons.AgenticPostureScoreCron.redTeamSeverityScore;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestAgenticPostureScoreBandsIntegration {

    private static final int ACCOUNT_ID = 1_000_000;
    private static final double DELTA = 0.05;

    private static final int A_CRIT_1    = -1193439486;
    private static final int B_CRIT_5    = -1190657920;
    private static final int C_CRIT3_LOW12 = -1184648711;
    private static final int D_HIGH_5    = -1141658464;
    private static final int E_HIGH_1    = -1135676985;
    private static final int F_MED_5     = -1132511514;
    private static final int G_MED_1     = -1129838210;
    private static final int H_LOW_5     = -1124512988;
    private static final int I_LOW_1     = 58455172;
    private static final int J_NONE      = -1184835946;
    private static final int K_ALL_FIXED = -1184079483;
    private static final int L_IGNORED_CRIT_OPEN_LOW = -1599761133;

    private static final List<Integer> ALL = Arrays.asList(A_CRIT_1, B_CRIT_5, C_CRIT3_LOW12, D_HIGH_5,
            E_HIGH_1, F_MED_5, G_MED_1, H_LOW_5, I_LOW_1, J_NONE, K_ALL_FIXED, L_IGNORED_CRIT_OPEN_LOW);

    private static Map<Integer, Map<String, Integer>> severities;

    @BeforeClass
    public static void loadFromMongo() {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017"));
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(null);
        Context.contextSource.set(null);

        BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID,
                "$" + TestingRunIssues.ID_API_COLLECTION_ID)
                .append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY);
        severities = TestingRunIssuesDao.instance.getSeveritiesMapForCollections(
                Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, ALL), false, groupedId);
    }

    private static double score(int collectionId) {
        return redTeamSeverityScore(severities.get(collectionId));
    }

    @Test
    public void redTeamScoresMatchTheBandsEndToEnd() {
        assertEquals(96.8, score(A_CRIT_1), DELTA);
        assertEquals(100.0, score(B_CRIT_5), DELTA);
        assertEquals(95.0, score(D_HIGH_5), DELTA);
        assertEquals(79.8, score(E_HIGH_1), DELTA);
        assertEquals(75.0, score(F_MED_5), DELTA);
        assertEquals(55.8, score(G_MED_1), DELTA);
        assertEquals(50.0, score(H_LOW_5), DELTA);
        assertEquals(30.0, score(I_LOW_1), DELTA);
    }

    @Test
    public void worstSeverityCountOnlyNotTotalCount() {
        assertEquals("3 CRITICAL + 12 LOW must score on n=3", 98.4, score(C_CRIT3_LOW12), DELTA);
    }

    @Test
    public void volumeNeverCrossesABandEndToEnd() {
        assertTrue(score(H_LOW_5) < score(G_MED_1));
        assertTrue(score(F_MED_5) < score(E_HIGH_1));
        assertTrue(score(D_HIGH_5) < score(A_CRIT_1));
    }

    @Test
    public void collectionWithNoIssuesScoresZero() {
        assertEquals(0.0, score(J_NONE), DELTA);
    }

    @Test
    public void fixedIssuesDoNotCount() {
        assertEquals("5 CRITICAL, all FIXED", 0.0, score(K_ALL_FIXED), DELTA);
    }

    @Test
    public void ignoredIssuesDoNotCount() {
        assertEquals("3 CRITICAL IGNORED + 1 LOW OPEN scores as LOW",
                30.0, score(L_IGNORED_CRIT_OPEN_LOW), DELTA);
    }

    @Test
    public void guardrailScoresThroughTheRealResolver() {
        List<ApiCollection> collections = new ArrayList<>();
        collections.add(collection(900_001, "payments.mcp.akto.io"));
        collections.add(collection(900_002, "search.mcp.akto.io"));
        collections.add(collection(900_003, "notify.mcp.akto.io"));
        collections.add(collection(900_004, "quiet.mcp.akto.io"));
        HostCollectionResolver resolver = new HostCollectionResolver(collections);

        List<DashboardMaliciousEvent> events = new ArrayList<>();
        events.addAll(events("payments.mcp.akto.io", "CRITICAL", 1));
        events.addAll(events("search.mcp.akto.io", "HIGH", 50));
        events.addAll(events("notify.mcp.akto.io", "medium", 1));

        Map<Integer, Map<String, Integer>> bySeverity = resolver.severityByCollection(events);

        assertEquals(100.0, guardrailSeverityScore(bySeverity.get(900_001)), DELTA);
        assertEquals(99.0, guardrailSeverityScore(bySeverity.get(900_002)), DELTA);
        assertEquals("lowercase severity is uppercased by the resolver",
                55.2, guardrailSeverityScore(bySeverity.get(900_003)), DELTA);
        assertEquals(0.0, guardrailSeverityScore(bySeverity.get(900_004)), DELTA);

        assertTrue("50 HIGH events stay below one CRITICAL",
                guardrailSeverityScore(bySeverity.get(900_002))
                        < guardrailSeverityScore(bySeverity.get(900_001)));
    }

    @Test
    public void guardrailEventWithNoSeverityScoresZero() {
        List<ApiCollection> collections = new ArrayList<>();
        collections.add(collection(900_005, "unknown.mcp.akto.io"));
        HostCollectionResolver resolver = new HostCollectionResolver(collections);

        List<DashboardMaliciousEvent> events = events("unknown.mcp.akto.io", null, 5);
        Map<Integer, Map<String, Integer>> bySeverity = resolver.severityByCollection(events);

        assertEquals("UNKNOWN must not fall into the LOW band",
                0.0, guardrailSeverityScore(bySeverity.get(900_005)), DELTA);
    }

    private static ApiCollection collection(int id, String hostName) {
        ApiCollection c = new ApiCollection();
        c.setId(id);
        c.setHostName(hostName);
        c.setName(hostName);
        return c;
    }

    private static List<DashboardMaliciousEvent> events(String host, String severity, int n) {
        List<DashboardMaliciousEvent> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            DashboardMaliciousEvent e = new DashboardMaliciousEvent();
            e.setHost(host);
            e.setActor("actor-" + i);
            e.setSeverity(severity);
            out.add(e);
        }
        return out;
    }
}
