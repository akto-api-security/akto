package com.akto.service.insights;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestSensitiveDataPolicy {

    private static DashboardMaliciousEvent event(String policy, String ruleViolated) {
        DashboardMaliciousEvent e = new DashboardMaliciousEvent();
        e.setCategory(policy);
        e.setSubCategory(ruleViolated);
        return e;
    }

    @Test
    public void onlyDataRulesCountEvenWhenThePolicyAlsoDetectsOtherThings() {
        assertTrue(InsightUtil.isSensitiveDataEvent(event("pii-policy", "PII-email")));
        assertTrue(InsightUtil.isSensitiveDataEvent(event("pii-policy", "pii-password")));
        assertTrue(InsightUtil.isSensitiveDataEvent(event("Employee PII", "UserDefinedLLMRule")));
        assertTrue(InsightUtil.isSensitiveDataEvent(event("Redact", "UserDefinedLLMRedactionRule")));
        // Same PII-enabled policy, but the prompt-injection check fired.
        assertFalse(InsightUtil.isSensitiveDataEvent(event("pii-policy", "PromptInjection")));
        assertFalse(InsightUtil.isSensitiveDataEvent(event("pii-policy", "pii-policy")));
        assertFalse(InsightUtil.isSensitiveDataEvent(event("pii-policy", null)));
        assertFalse(InsightUtil.isSensitiveDataEvent(null));
    }

    @Test
    public void labelsShowTheFlaggedData() {
        assertEquals("email", InsightUtil.sensitiveDataLabel(event("pii-policy", "PII-email")));
        assertEquals("Employee PII (LLM rule)", InsightUtil.sensitiveDataLabel(event("Employee PII", "UserDefinedLLMRule")));
        assertNull(InsightUtil.sensitiveDataLabel(event("pii-policy", "PromptInjection")));

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("password", 3);
        counts.put("email", 12);
        assertEquals("email (12), password (3)", InsightUtil.sensitiveDataLine(counts));
    }
}
