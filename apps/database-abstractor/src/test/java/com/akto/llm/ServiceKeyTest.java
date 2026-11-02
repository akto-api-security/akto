package com.akto.llm;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ServiceKeyTest {

    @Test
    public void fromKey_matchesEachDeclaredKeyString() {
        for (ServiceKey serviceKey : ServiceKey.values()) {
            assertSame(serviceKey, ServiceKey.fromKey(serviceKey.getKey()));
        }
    }

    @Test
    public void fromKey_returnsNullForUnknownOrNullInput() {
        assertNull(ServiceKey.fromKey("not-a-real-service-key"));
        assertNull(ServiceKey.fromKey(null));
        assertNull(ServiceKey.fromKey(""));
    }

    @Test
    public void internalOnlyKeys_areNotExternallySelectable() {
        assertFalse(ServiceKey.ENDPOINT_SHIELD_SKILL_VALIDATION.isExternallySelectable());
        assertFalse(ServiceKey.ENDPOINT_SHIELD_SETTINGS_SCAN.isExternallySelectable());
    }

    @Test
    public void externallyFacingKeys_areExternallySelectable() {
        assertTrue(ServiceKey.MINI_TESTING_TEST_EDITOR.isExternallySelectable());
        assertTrue(ServiceKey.AI_AGENT_SHIELD_SESSION_SUMMARY.isExternallySelectable());
        assertTrue(ServiceKey.AI_AGENT_SHIELD_SCHEMA_DISCOVERY.isExternallySelectable());
        assertTrue(ServiceKey.ENDPOINT_SHIELD_RULE_EVALUATION.isExternallySelectable());
        assertTrue(ServiceKey.ENDPOINT_SHIELD_REMEDIATION.isExternallySelectable());
        assertTrue(ServiceKey.ENDPOINT_SHIELD_MCP_METADATA_VALIDATOR.isExternallySelectable());
    }

    @Test
    public void default_isNotExternallySelectableAndUsesTheLegacyKey() {
        assertEquals("llm_response_v2", ServiceKey.DEFAULT.getKey());
        assertFalse(ServiceKey.DEFAULT.isExternallySelectable());
    }

    @Test
    public void maxTokens_matchPerFeatureValuesFromTheDocInventory() {
        assertEquals(5000, ServiceKey.ENDPOINT_SHIELD_SKILL_VALIDATION.getMaxTokens());
        assertEquals(16000, ServiceKey.ENDPOINT_SHIELD_SETTINGS_SCAN.getMaxTokens());
        assertEquals(10000, ServiceKey.MINI_TESTING_TEST_EDITOR.getMaxTokens());
        assertEquals(150, ServiceKey.AI_AGENT_SHIELD_SESSION_SUMMARY.getMaxTokens());
        assertEquals(10000, ServiceKey.AI_AGENT_SHIELD_SCHEMA_DISCOVERY.getMaxTokens());
        assertEquals(10000, ServiceKey.ENDPOINT_SHIELD_RULE_EVALUATION.getMaxTokens());
        assertEquals(10000, ServiceKey.ENDPOINT_SHIELD_REMEDIATION.getMaxTokens());
        assertEquals(10000, ServiceKey.ENDPOINT_SHIELD_MCP_METADATA_VALIDATOR.getMaxTokens());
        assertEquals(10000, ServiceKey.DEFAULT.getMaxTokens());
    }

    @Test
    public void temperature_matchPerFeatureValuesFromTheDocInventory() {
        assertEquals(0, ServiceKey.ENDPOINT_SHIELD_SKILL_VALIDATION.getTemperature(), 0);
        assertEquals(0, ServiceKey.ENDPOINT_SHIELD_SETTINGS_SCAN.getTemperature(), 0);
        assertEquals(0.1, ServiceKey.MINI_TESTING_TEST_EDITOR.getTemperature(), 0);
        assertEquals(0.1, ServiceKey.AI_AGENT_SHIELD_SESSION_SUMMARY.getTemperature(), 0);
        assertEquals(0, ServiceKey.AI_AGENT_SHIELD_SCHEMA_DISCOVERY.getTemperature(), 0);
        assertEquals(0, ServiceKey.ENDPOINT_SHIELD_RULE_EVALUATION.getTemperature(), 0);
        assertEquals(0, ServiceKey.ENDPOINT_SHIELD_REMEDIATION.getTemperature(), 0);
        assertEquals(0, ServiceKey.ENDPOINT_SHIELD_MCP_METADATA_VALIDATOR.getTemperature(), 0);
        assertEquals(0, ServiceKey.DEFAULT.getTemperature(), 0);
    }

    @Test
    public void everyKeyString_isUnique() {
        Set<String> seen = new HashSet<>();
        for (ServiceKey serviceKey : ServiceKey.values()) {
            assertTrue("duplicate key string: " + serviceKey.getKey(), seen.add(serviceKey.getKey()));
        }
    }
}
