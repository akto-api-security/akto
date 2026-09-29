package com.akto.service.posture;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestGuardrailControlComplianceMap {

    @Test
    public void matchRuleViolated_promptInjectionPrefixes_mapToOwaspLlm01() {
        for (String rv : new String[]{"PromptInjection", "prompt_injection", "IntentAnalysis", "intent",
                "prompt_injection_custom_rule_1"}) {
            GuardrailControlComplianceMap.ClauseMatch match = GuardrailControlComplianceMap.matchRuleViolated(rv);
            assertEquals("mismatch for " + rv, "OWASP LLM", match.framework);
            assertEquals("mismatch for " + rv, "LLM01", match.subClauseId);
        }
    }

    @Test
    public void matchRuleViolated_harmfulCategoryPrefixes_mapToNistAiRmfManage() {
        for (String rv : new String[]{"harmful", "BanTopics", "Toxicity", "BanSubstrings", "denied_topic",
                "BanCompetitors", "harmful_hate_speech"}) {
            GuardrailControlComplianceMap.ClauseMatch match = GuardrailControlComplianceMap.matchRuleViolated(rv);
            assertEquals("mismatch for " + rv, "NIST AI Risk Management Framework", match.framework);
            assertEquals("mismatch for " + rv, "MANAGE", match.subClauseId);
        }
    }

    @Test
    public void matchRuleViolated_isCaseInsensitive() {
        GuardrailControlComplianceMap.ClauseMatch match =
                GuardrailControlComplianceMap.matchRuleViolated("PROMPTINJECTION");
        assertEquals("OWASP LLM", match.framework);
    }

    @Test
    public void matchRuleViolated_unknownOrEmptyOrNull_returnsNull() {
        assertNull(GuardrailControlComplianceMap.matchRuleViolated(null));
        assertNull(GuardrailControlComplianceMap.matchRuleViolated(""));
        assertNull(GuardrailControlComplianceMap.matchRuleViolated("-"));
        assertNull(GuardrailControlComplianceMap.matchRuleViolated("PII_EMAIL"));
        assertNull(GuardrailControlComplianceMap.matchRuleViolated("Secrets"));
    }

    @Test
    public void frameworks_returnsBothTrackedFrameworks() {
        Set<String> frameworks = GuardrailControlComplianceMap.frameworks();
        assertEquals(2, frameworks.size());
        assertTrue(frameworks.contains("OWASP LLM"));
        assertTrue(frameworks.contains("NIST AI Risk Management Framework"));
    }
}
