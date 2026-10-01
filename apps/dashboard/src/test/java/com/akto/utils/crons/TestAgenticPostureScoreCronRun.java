package com.akto.utils.crons;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.util.Constants;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestAgenticPostureScoreCronRun {

    private static final int ACCOUNT_ID = 1_000_000;
    private static final double DELTA = 0.05;

    private static final Map<Integer, Double> EXPECTED_RED_TEAM = new LinkedHashMap<>();
    private static final Map<Integer, Double> EXPECTED_GUARDRAIL = new LinkedHashMap<>();
    static {
        EXPECTED_RED_TEAM.put(-1193439486, 96.8);
        EXPECTED_RED_TEAM.put(-1190657920, 100.0);
        EXPECTED_RED_TEAM.put(-1184648711, 98.4);
        EXPECTED_RED_TEAM.put(-1141658464, 95.0);
        EXPECTED_RED_TEAM.put(-1135676985, 79.8);
        EXPECTED_RED_TEAM.put(-1132511514, 75.0);
        EXPECTED_RED_TEAM.put(-1129838210, 55.8);
        EXPECTED_RED_TEAM.put(-1124512988, 50.0);
        EXPECTED_RED_TEAM.put(58455172, 30.0);
        EXPECTED_RED_TEAM.put(-1184835946, 0.0);
        EXPECTED_RED_TEAM.put(-1184079483, 0.0);
        EXPECTED_RED_TEAM.put(-1599761133, 30.0);

        EXPECTED_GUARDRAIL.put(933861596, 100.0);
        EXPECTED_GUARDRAIL.put(-1242533772, 99.0);
        EXPECTED_GUARDRAIL.put(205784820, 90.0);
        EXPECTED_GUARDRAIL.put(-1447735949, 55.2);
        EXPECTED_GUARDRAIL.put(-1442801900, 29.4);
        EXPECTED_GUARDRAIL.put(-1437371172, 0.0);
    }

    @BeforeClass
    public static void runTheCron() {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017"));
        Context.accountId.set(ACCOUNT_ID);
        new AgenticPostureScoreCron().forceRunForAccount(ACCOUNT_ID);
        Context.accountId.set(ACCOUNT_ID);
        Context.contextSource.set(null);
        Context.userId.set(null);
    }

    @SuppressWarnings("unchecked")
    private static double subScore(int collectionId, String key) {
        ApiCollection c = ApiCollectionsDao.instance.findOneNoRbacFilter(
                Filters.eq(Constants.ID, collectionId), null);
        assertNotNull("collection " + collectionId + " missing", c);
        Map<String, Object> sub = (Map<String, Object>) c.getPostureSubScores();
        assertNotNull("no postureSubScores written for " + collectionId, sub);
        return ((Number) sub.get(key)).doubleValue();
    }

    @Test
    public void cronWritesRedTeamSubScoresFromTheBands() {
        for (Map.Entry<Integer, Double> e : EXPECTED_RED_TEAM.entrySet()) {
            assertEquals("redTeam for collection " + e.getKey(),
                    e.getValue(), subScore(e.getKey(), "redTeam"), DELTA);
        }
    }

    @Test
    public void cronWritesGuardrailSubScoresFromTheBands() {
        for (Map.Entry<Integer, Double> e : EXPECTED_GUARDRAIL.entrySet()) {
            assertEquals("guardrailMalicious for collection " + e.getKey(),
                    e.getValue(), subScore(e.getKey(), "guardrailMalicious"), DELTA);
        }
    }

    @Test
    public void compositeIsTheWeightedAverageOfWhatWasWritten() {
        int collectionId = -1190657920;
        ApiCollection c = ApiCollectionsDao.instance.findOneNoRbacFilter(
                Filters.eq(Constants.ID, collectionId), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> sub = (Map<String, Object>) c.getPostureSubScores();

        double expected = 0.3 * ((Number) sub.get("redTeam")).doubleValue()
                + 0.3 * ((Number) sub.get("guardrailMalicious")).doubleValue()
                + 0.1 * (((Number) sub.get("coverage")).doubleValue()
                        + ((Number) sub.get("sensitiveData")).doubleValue()
                        + ((Number) sub.get("accessAuth")).doubleValue()
                        + ((Number) sub.get("overprivilegedTools")).doubleValue());

        assertEquals(expected, c.getPostureScore(), DELTA);
        assertTrue("5 CRITICAL findings must push the composite well above zero",
                c.getPostureScore() > 30);
    }
}
