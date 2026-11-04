package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
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
    public void fullStackSumsToSixWithFiveLines() {
        ApiInfo info = baseInfo();
        // New (+1) is suppressed when threat is set — so for a true "full" additive stack we need
        // no threat and all other flags, OR threat without new. Plan case: new + public + sensitive
        // + threat + high severity. Threat suppresses new, so live formula = 1+1+1+2 = 5.
        // Exercise the five-line path: public + sensitive + threat + high + residual-to-6 via stored.
        info.setApiAccessTypes(Collections.singleton(ApiAccessType.PUBLIC));
        info.setIsSensitive(true);
        info.setThreatScore(1f);
        info.setSeverityScore(100f);
        info.setRiskScore(6f);

        List<BasicDBObject> factors = RiskScoreExplainer.explain(info, 6f);
        assertEquals(5, factors.size());
        assertTrue(hasLabel(factors, "Publicly accessible"));
        assertTrue(hasLabel(factors, "Sensitive data in responses"));
        assertTrue(hasLabel(factors, "Guardrail or threat activity"));
        assertTrue(hasLabel(factors, "High or critical open test findings"));
        assertTrue(hasLabel(factors, "Other factors"));
        assertEquals(6f, sumPoints(factors), 0.01f);
        assertFalse(hasLabel(factors, "Discovered in the last 30 days"));
    }

    @Test
    public void newPublicSensitiveHighSeverityIsFiveWithoutThreat() {
        ApiInfo info = baseInfo();
        info.setDiscoveredTimestamp(Context.now() - 60);
        info.setApiAccessTypes(Collections.singleton(ApiAccessType.PUBLIC));
        info.setIsSensitive(true);
        info.setSeverityScore(100f);
        info.setRiskScore(5f);

        List<BasicDBObject> factors = RiskScoreExplainer.buildFactors(info);
        assertEquals(4, factors.size());
        assertTrue(hasLabel(factors, "Discovered in the last 30 days"));
        assertTrue(hasLabel(factors, "Publicly accessible"));
        assertTrue(hasLabel(factors, "Sensitive data in responses"));
        assertTrue(hasLabel(factors, "High or critical open test findings"));
        assertEquals(5f, sumPoints(factors), 0.01f);
    }

    @Test
    public void lowFindingsOnlyIsHalfPoint() {
        ApiInfo info = baseInfo();
        info.setSeverityScore(1f);
        info.setRiskScore(0.5f);

        List<BasicDBObject> factors = RiskScoreExplainer.explain(info, 0.5f);
        assertEquals(1, factors.size());
        assertEquals("Low open test findings", factors.get(0).getString("label"));
        assertEquals(0.5f, ((Number) factors.get(0).get("points")).floatValue(), 0.01f);
    }

    @Test
    public void threatSuppressesNewPoint() {
        ApiInfo info = baseInfo();
        info.setDiscoveredTimestamp(Context.now() - 60);
        info.setThreatScore(2.5f);
        info.setRiskScore(1f);

        List<BasicDBObject> factors = RiskScoreExplainer.buildFactors(info);
        assertEquals(1, factors.size());
        assertTrue(hasLabel(factors, "Guardrail or threat activity"));
        assertFalse(hasLabel(factors, "Discovered in the last 30 days"));
        assertEquals(1f, sumPoints(factors), 0.01f);
    }

    @Test
    public void nullAccessTypesDoesNotThrow() {
        ApiInfo info = baseInfo();
        info.setApiAccessTypes(null);
        info.setIsSensitive(true);
        info.setRiskScore(1f);

        List<BasicDBObject> factors = RiskScoreExplainer.explain(info, 1f);
        assertEquals(1, factors.size());
        assertTrue(hasLabel(factors, "Sensitive data in responses"));
    }

    @Test
    public void mediumSeverityLabel() {
        ApiInfo info = baseInfo();
        info.setSeverityScore(10f);
        List<BasicDBObject> factors = RiskScoreExplainer.buildFactors(info);
        assertEquals(1, factors.size());
        assertEquals("Medium open test findings", factors.get(0).getString("label"));
        assertEquals(1f, ((Number) factors.get(0).get("points")).floatValue(), 0.01f);
    }
}
