package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.RiskScoreFactor;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;

public class TestRiskScoreExplainer {

    private static ApiInfo baseInfo() {
        ApiInfo info = new ApiInfo();
        info.setDiscoveredTimestamp(Context.now() - Constants.ONE_MONTH_TIMESTAMP - 60);
        info.setApiAccessTypes(new HashSet<>());
        info.setIsSensitive(false);
        info.setThreatScore(0f);
        info.setSeverityScore(0f);
        info.setRiskScore(0f);
        return info;
    }

    private static float sumPoints(List<BasicDBObject> factors) {
        float sum = 0f;
        for (BasicDBObject f : factors) {
            sum += ((Number) f.get("points")).floatValue();
        }
        return sum;
    }

    private static boolean hasLabel(List<BasicDBObject> factors, String label) {
        for (BasicDBObject f : factors) {
            if (label.equals(f.getString("label"))) return true;
        }
        return false;
    }

    @Test
    public void threatPublicSensitiveHighSeverityIsFiveWithFourLines() {
        // Threat suppresses the "new" point, so the real full stack is 5, not 6 — and never invents
        // an "Other factors" residual.
        ApiInfo info = baseInfo();
        info.setApiAccessTypes(Collections.singleton(ApiAccessType.PUBLIC));
        info.setIsSensitive(true);
        info.setThreatScore(1f);
        info.setSeverityScore(100f);
        info.setRiskScore(5f);

        List<RiskScoreFactor> persisted = RiskScoreExplainer.buildFactors(info, true, 100f, null, null);
        assertEquals(4, persisted.size());
        RiskScoreExplainer.Explanation explanation = RiskScoreExplainer.explain(info, 5f);
        assertEquals(4, explanation.factors.size());
        assertNull(explanation.note);
        assertFalse(hasLabel(explanation.factors, "Other factors"));
        assertFalse(hasLabel(explanation.factors, "Discovered in the last 30 days"));
        assertTrue(hasLabel(explanation.factors, "Publicly accessible"));
        assertTrue(hasLabel(explanation.factors, "Sensitive data in responses"));
        assertTrue(hasLabel(explanation.factors, "Guardrail or threat activity"));
        assertTrue(hasLabel(explanation.factors, "High or critical open test findings"));
        assertEquals(5f, sumPoints(explanation.factors), 0.01f);
    }

    @Test
    public void newPublicSensitiveHighSeverityIsFiveWithoutThreat() {
        ApiInfo info = baseInfo();
        info.setDiscoveredTimestamp(Context.now() - 60);
        info.setApiAccessTypes(Collections.singleton(ApiAccessType.PUBLIC));
        info.setIsSensitive(true);
        info.setSeverityScore(100f);
        info.setRiskScore(5f);

        List<RiskScoreFactor> factors = RiskScoreExplainer.buildFactors(info, true, 100f, null, null);
        assertEquals(4, factors.size());
        assertEquals(5f, factors.stream().map(RiskScoreFactor::getPoints).reduce(0f, Float::sum), 0.01f);
    }

    @Test
    public void lowFindingsOnlyIsHalfPoint() {
        ApiInfo info = baseInfo();
        info.setSeverityScore(1f);
        info.setRiskScore(0.5f);

        RiskScoreExplainer.Explanation explanation = RiskScoreExplainer.explain(info, 0.5f);
        assertEquals(1, explanation.factors.size());
        assertEquals("Low open test findings", explanation.factors.get(0).getString("label"));
        assertEquals(0.5f, ((Number) explanation.factors.get(0).get("points")).floatValue(), 0.01f);
        assertNull(explanation.note);
    }

    @Test
    public void threatSuppressesNewPoint() {
        ApiInfo info = baseInfo();
        info.setDiscoveredTimestamp(Context.now() - 60);
        info.setThreatScore(2.5f);
        info.setRiskScore(1f);

        List<RiskScoreFactor> factors = RiskScoreExplainer.buildFactors(info, false, 0f, null, null);
        assertEquals(1, factors.size());
        assertEquals("Guardrail or threat activity", factors.get(0).getLabel());
    }

    @Test
    public void nullAccessTypesDoesNotThrow() {
        ApiInfo info = baseInfo();
        info.setApiAccessTypes(null);
        info.setIsSensitive(true);
        info.setRiskScore(1f);

        RiskScoreExplainer.Explanation explanation = RiskScoreExplainer.explain(info, 1f);
        assertEquals(1, explanation.factors.size());
        assertTrue(hasLabel(explanation.factors, "Sensitive data in responses"));
    }

    @Test
    public void mediumSeverityLabel() {
        ApiInfo info = baseInfo();
        List<RiskScoreFactor> factors = RiskScoreExplainer.buildFactors(info, false, 10f, null, null);
        assertEquals(1, factors.size());
        assertEquals("Medium open test findings", factors.get(0).getLabel());
        assertEquals(1f, factors.get(0).getPoints(), 0.01f);
    }

    @Test
    public void savedListIsReturnedUnchanged() {
        ApiInfo info = baseInfo();
        info.setRiskScore(5f);
        info.setLastCalculatedTime(1_700_000_000);
        info.setRiskScoreFactors(Arrays.asList(
                new RiskScoreFactor(1f, "Publicly accessible", null),
                new RiskScoreFactor(2f, "High or critical open test findings", "BOLA, SQLi"),
                new RiskScoreFactor(1f, "Sensitive data in responses", "EMAIL"),
                new RiskScoreFactor(1f, "Guardrail or threat activity", null)
        ));
        // Live fields would only support 0 — saved list must win.
        RiskScoreExplainer.Explanation explanation = RiskScoreExplainer.explain(info, 5f);
        assertEquals(4, explanation.factors.size());
        assertNull(explanation.note);
        assertEquals(5f, sumPoints(explanation.factors), 0.01f);
        assertEquals("BOLA, SQLi", explanation.factors.get(1).getString("detail"));
    }

    @Test
    public void shortfallAddsNoteWithoutInventingPoints() {
        ApiInfo info = baseInfo();
        info.setApiAccessTypes(Collections.singleton(ApiAccessType.PUBLIC));
        info.setThreatScore(1f);
        info.setRiskScore(5f);
        info.setLastCalculatedTime(1_700_000_000);
        // No saved list — live factors = 2, badge = 5.
        RiskScoreExplainer.Explanation explanation = RiskScoreExplainer.explain(info, 5f);
        assertEquals(2, explanation.factors.size());
        assertEquals(2f, sumPoints(explanation.factors), 0.01f);
        assertFalse(hasLabel(explanation.factors, "Other factors"));
        assertNotNull(explanation.note);
        assertTrue(explanation.note.contains("before explanations were saved"));
    }

    @Test
    public void severityDetailNamesOpenFindings() {
        ApiInfo info = baseInfo();
        List<RiskScoreFactor> factors = RiskScoreExplainer.buildFactors(
                info, false, 100f, Arrays.asList("BOLA", "SQLi", "XSS"), null);
        assertEquals(1, factors.size());
        assertEquals("BOLA, SQLi, XSS", factors.get(0).getDetail());
    }
}
