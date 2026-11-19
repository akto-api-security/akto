package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.RiskScoreFactor;
import com.mongodb.BasicDBObject;

public class TestRiskScoreExplainer {

    @Test
    public void explainReturnsSavedFactorsUnchanged() {
        ApiInfo info = new ApiInfo();
        info.setRiskScore(5f);
        info.setRiskScoreFactors(Arrays.asList(
                new RiskScoreFactor(1f, "Publicly accessible", null),
                new RiskScoreFactor(1f, "Threat activity, critical severity", null),
                new RiskScoreFactor(3f, "This MCP tool is marked malicious", null)
        ));

        RiskScoreExplainer.Explanation explanation = RiskScoreExplainer.explain(info);
        assertEquals(3, explanation.factors.size());
        assertEquals(1.0, ((Number) explanation.factors.get(0).get("points")).doubleValue(), 0.01);
        assertEquals("Publicly accessible", explanation.factors.get(0).getString("label"));
        assertEquals("This MCP tool is marked malicious", explanation.factors.get(2).getString("label"));
    }

    @Test
    public void explainReturnsEmptyWhenFactorsMissing() {
        ApiInfo info = new ApiInfo();
        info.setRiskScore(4f);
        assertTrue(RiskScoreExplainer.explain(info).factors.isEmpty());
        assertTrue(RiskScoreExplainer.explain(null).factors.isEmpty());
    }

    @Test
    public void explainReturnsEmptyWhenFactorsEmpty() {
        ApiInfo info = new ApiInfo();
        info.setRiskScore(4f);
        info.setRiskScoreFactors(Collections.emptyList());
        assertTrue(RiskScoreExplainer.explain(info).factors.isEmpty());
    }

    @Test
    public void putExplanationOnlySetsNonEmptyFactors() {
        BasicDBObject row = new BasicDBObject();
        RiskScoreExplainer.putExplanation(row, RiskScoreExplainer.explain(null));
        assertTrue(!row.containsKey("riskScoreFactors"));

        ApiInfo info = new ApiInfo();
        info.setRiskScoreFactors(Collections.singletonList(
                new RiskScoreFactor(1f, "Publicly accessible", "EMAIL")));
        RiskScoreExplainer.putExplanation(row, RiskScoreExplainer.explain(info));
        assertTrue(row.containsKey("riskScoreFactors"));
        @SuppressWarnings("unchecked")
        List<BasicDBObject> factors = (List<BasicDBObject>) row.get("riskScoreFactors");
        assertEquals(1, factors.size());
        assertEquals("EMAIL", factors.get(0).getString("detail"));
    }

    @Test
    public void parseFactorsFromStoredDocumentWithDiscriminator() {
        // Cron-written Mongo docs include _t; aggregation returns Map-shaped factor rows.
        Map<String, Object> stored = new HashMap<>();
        stored.put("_t", "com.akto.dto.ApiInfo$RiskScoreFactor");
        stored.put("points", 1);
        stored.put("label", "Publicly accessible");
        stored.put("detail", null);

        List<RiskScoreFactor> parsed = RiskScoreExplainer.parseFactors(Collections.singletonList(stored));
        assertEquals(1, parsed.size());
        assertEquals(1f, parsed.get(0).getPoints(), 0.01f);
        assertEquals("Publicly accessible", parsed.get(0).getLabel());
        assertEquals(null, parsed.get(0).getDetail());

        ApiInfo info = new ApiInfo();
        info.setRiskScore(1f);
        info.setRiskScoreFactors(parsed);
        RiskScoreExplainer.Explanation explanation = RiskScoreExplainer.explain(info);
        assertEquals(1, explanation.factors.size());
        assertEquals("Publicly accessible", explanation.factors.get(0).getString("label"));
        assertTrue(!explanation.factors.get(0).containsKey("detail"));
    }

    @Test
    public void scoresMatchWhenDisplayedEqualsWinning() {
        ApiInfo winning = new ApiInfo();
        winning.setRiskScore(4.2f);
        winning.setRiskScoreFactors(Collections.singletonList(
                new RiskScoreFactor(4.2f, "Open security findings", null)));
        assertTrue(RiskScoreExplainer.scoresMatch(winning, 4.2));
        assertTrue(RiskScoreExplainer.scoresMatch(winning, 4.21));
        assertFalse(RiskScoreExplainer.scoresMatch(winning, 3.0));
        assertFalse(RiskScoreExplainer.scoresMatch(null, 4.2));
    }

    @Test
    public void scoresMatchDropsWhenFactorsScoreDisagrees() {
        ApiInfo winning = new ApiInfo();
        winning.setRiskScore(4.2f);
        winning.setRiskScoreFactorsScore(3.0f);
        winning.setRiskScoreFactorsScorePresent(true);
        winning.setRiskScoreFactors(Collections.singletonList(
                new RiskScoreFactor(3.0f, "Publicly accessible", null)));
        assertFalse(RiskScoreExplainer.scoresMatch(winning, 4.2));
    }

    @Test
    public void scoresMatchIgnoresMissingFactorsScore() {
        ApiInfo winning = new ApiInfo();
        winning.setRiskScore(4.2f);
        winning.setRiskScoreFactorsScorePresent(false);
        winning.setRiskScoreFactors(Collections.singletonList(
                new RiskScoreFactor(4.2f, "Publicly accessible", null)));
        assertTrue(RiskScoreExplainer.scoresMatch(winning, 4.2));
    }

    @Test
    public void putExplanationIfMatchesOnlyWhenScoresAgree() {
        ApiInfo winning = new ApiInfo();
        winning.setRiskScore(4.2f);
        winning.setRiskScoreFactors(Collections.singletonList(
                new RiskScoreFactor(1f, "Publicly accessible", null)));

        BasicDBObject matched = new BasicDBObject();
        RiskScoreExplainer.putExplanationIfMatches(matched, winning, 4.2);
        assertTrue(matched.containsKey("riskScoreFactors"));

        BasicDBObject mismatched = new BasicDBObject();
        RiskScoreExplainer.putExplanationIfMatches(mismatched, winning, 3.0);
        assertFalse(mismatched.containsKey("riskScoreFactors"));
    }

    @Test
    public void isSetCollectionIdTreatsZeroAsUnsetAndAllowsNegativeIds() {
        assertFalse(RiskScoreExplainer.isSetCollectionId(0));
        assertFalse(RiskScoreExplainer.isSetCollectionId((Number) null));
        assertTrue(RiskScoreExplainer.isSetCollectionId(-134853966));
        assertTrue(RiskScoreExplainer.isSetCollectionId(1298715383));
        assertTrue(RiskScoreExplainer.isSetCollectionId(Integer.valueOf(-1)));
    }

    @Test
    public void factorsIfMatchesReturnsEmptyOnMismatch() {
        ApiInfo winning = new ApiInfo();
        winning.setRiskScore(5f);
        winning.setRiskScoreFactors(Collections.singletonList(
                new RiskScoreFactor(5f, "Capped at maximum risk score of 5", null)));
        assertTrue(RiskScoreExplainer.factorsIfMatches(winning, 3.0).isEmpty());
        assertEquals(1, RiskScoreExplainer.factorsIfMatches(winning, 5.0).size());
    }
}
