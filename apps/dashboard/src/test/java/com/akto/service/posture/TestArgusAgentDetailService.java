package com.akto.service.posture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestArgusAgentDetailService {

    private static final String POLICY = "pii-policy-for-finance";

    @Test
    public void piiTypeOf_readsTheGatewayPrefix() {
        assertEquals("ssn", ArgusAgentDetailService.piiTypeOf(POLICY, "PII-ssn"));
        assertEquals("credit_card_cap", ArgusAgentDetailService.piiTypeOf(POLICY, "PII-credit_card_cap"));
    }

    @Test
    public void piiTypeOf_readsTheOtherRecognisedPrefixForms() {
        assertEquals("email", ArgusAgentDetailService.piiTypeOf(POLICY, "PII_email"));
        assertEquals("email", ArgusAgentDetailService.piiTypeOf(POLICY, "pii-email"));
        assertEquals("email", ArgusAgentDetailService.piiTypeOf(POLICY, "piiemail"));
    }

    // subCategory is the policy's own name when no rule was recorded, which is the one case where
    // it equals category. A policy named "pii-..." would otherwise read as a PII type.
    @Test
    public void piiTypeOf_ignoresAPolicyNameEvenWhenItLooksLikeAPiiRule() {
        assertNull(ArgusAgentDetailService.piiTypeOf(POLICY, POLICY));
        assertNull(ArgusAgentDetailService.piiTypeOf("Default-Customer PII", "Default-Customer PII"));
    }

    @Test
    public void piiTypeOf_ignoresNonPiiRules() {
        assertNull(ArgusAgentDetailService.piiTypeOf(POLICY, "denied-topic"));
        assertNull(ArgusAgentDetailService.piiTypeOf(POLICY, "PROMPT_INJECTION"));
        assertNull(ArgusAgentDetailService.piiTypeOf(POLICY, null));
    }

    @Test
    public void piiTypeOf_ignoresABarePrefix() {
        assertNull(ArgusAgentDetailService.piiTypeOf(POLICY, "PII-"));
        assertNull(ArgusAgentDetailService.piiTypeOf(POLICY, "PII"));
    }
}
