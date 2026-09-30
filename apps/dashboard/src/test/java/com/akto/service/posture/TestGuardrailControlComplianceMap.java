package com.akto.service.posture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestGuardrailControlComplianceMap {

    @Test
    public void capabilityForRuleViolated_promptInjectionPrefixes_resolveToPromptAttacks() {
        for (String rv : new String[]{"PromptInjection", "prompt_injection", "prompt_injection_custom_rule_1"}) {
            assertEquals("mismatch for " + rv, "promptAttacks",
                    GuardrailControlComplianceMap.capabilityForRuleViolated(rv));
        }
    }

    @Test
    public void capabilityForRuleViolated_harmfulCategoryPrefixes_resolveToHarmfulCategories() {
        for (String rv : new String[]{"harmful", "Toxicity", "HarmfulCategories", "harmful_hate_speech"}) {
            assertEquals("mismatch for " + rv, "harmfulCategories",
                    GuardrailControlComplianceMap.capabilityForRuleViolated(rv));
        }
    }

    @Test
    public void capabilityForRuleViolated_otherKnownCapabilities() {
        assertEquals("piiTypes", GuardrailControlComplianceMap.capabilityForRuleViolated("PII_EMAIL"));
        assertEquals("secretsDetection", GuardrailControlComplianceMap.capabilityForRuleViolated("Secrets"));
        assertEquals("gibberishDetection", GuardrailControlComplianceMap.capabilityForRuleViolated("Gibberish"));
    }

    @Test
    public void capabilityForRuleViolated_isCaseInsensitive() {
        assertEquals("promptAttacks", GuardrailControlComplianceMap.capabilityForRuleViolated("PROMPTINJECTION"));
    }

    @Test
    public void capabilityForRuleViolated_unknownOrEmptyOrNull_returnsNull() {
        assertNull(GuardrailControlComplianceMap.capabilityForRuleViolated(null));
        assertNull(GuardrailControlComplianceMap.capabilityForRuleViolated(""));
        assertNull(GuardrailControlComplianceMap.capabilityForRuleViolated("-"));
        assertNull(GuardrailControlComplianceMap.capabilityForRuleViolated("BanCode"));
    }
}
