package com.akto.s3_export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.Map;

import org.junit.Test;

public class GuardrailEventsExportSourceTest {

    @Test
    public void hourBoundariesConvertToWholeSeconds() {
        assertEquals(1704189600L, GuardrailEventsExportSource.toEpochSeconds(1704189600000L));
        try {
            GuardrailEventsExportSource.toEpochSeconds(1704189600500L);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void metadataFieldsReadRuleActionReasonAndRiskScore() {
        Map<String, Object> fields = GuardrailEventsExportSource.metadataFields(
            "{\"rule_violated\":\"rule-1\",\"behaviour\":\"block\",\"reason\":\"because\",\"risk_score\":7.5}");
        assertEquals("rule-1", fields.get("rule"));
        assertEquals("block", fields.get("action"));
        assertEquals("because", fields.get("reason"));
        assertEquals(7.5, (Double) fields.get("riskScore"), 0.0);
    }

    @Test
    public void metadataFieldsAcceptCamelCaseAliases() {
        Map<String, Object> fields = GuardrailEventsExportSource.metadataFields(
            "{\"ruleViolated\":\"rule-2\",\"nreason\":\"why\",\"riskScore\":\"3\"}");
        assertEquals("rule-2", fields.get("rule"));
        assertEquals("why", fields.get("reason"));
        assertEquals(3.0, (Double) fields.get("riskScore"), 0.0);
    }

    @Test
    public void metadataFieldsToleratesMissingOrInvalidJson() {
        for (String metadata : new String[] {null, "", "not json"}) {
            Map<String, Object> fields = GuardrailEventsExportSource.metadataFields(metadata);
            assertEquals("", fields.get("rule"));
            assertNull(fields.get("riskScore"));
        }
    }
}
