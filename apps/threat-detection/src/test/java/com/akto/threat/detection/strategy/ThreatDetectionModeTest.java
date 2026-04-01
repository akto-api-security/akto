package com.akto.threat.detection.strategy;

import com.akto.threat.detection.config.ThreatDetectionConfig;
import com.akto.threat.detection.enums.ThreatDetectionMode;
import com.akto.threat.detection.utils.ThreatDetectorWithStrategy;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test for ThreatDetectionMode configuration and strategy selection.
 */
public class ThreatDetectionModeTest {

    @After
    public void cleanup() {
        // Reset configuration after each test
        ThreatDetectionConfig.reset();
    }

    @Test
    public void testModeFromString() {
        assertEquals(ThreatDetectionMode.FILTER_YAML_ONLY, ThreatDetectionMode.fromString("FILTER_YAML_ONLY"));
        assertEquals(ThreatDetectionMode.HYPERSCAN_ONLY, ThreatDetectionMode.fromString("HYPERSCAN_ONLY"));

        // Test case insensitivity
        assertEquals(ThreatDetectionMode.HYPERSCAN_ONLY, ThreatDetectionMode.fromString("hyperscan_only"));

        // Test default for invalid values
        assertEquals(ThreatDetectionMode.FILTER_YAML_ONLY, ThreatDetectionMode.fromString("INVALID"));
        assertEquals(ThreatDetectionMode.FILTER_YAML_ONLY, ThreatDetectionMode.fromString(null));
        assertEquals(ThreatDetectionMode.FILTER_YAML_ONLY, ThreatDetectionMode.fromString(""));
    }

    @Test
    public void testConfigHelpers() {
        // Default mode should be FILTER_YAML_ONLY
        ThreatDetectionMode mode = ThreatDetectionConfig.getDetectionMode();
        assertEquals(ThreatDetectionMode.FILTER_YAML_ONLY, mode);

        // Check helper methods
        assertTrue(ThreatDetectionConfig.isFilterYamlEnabled());
        assertFalse(ThreatDetectionConfig.isHyperscanEnabled());
    }

    @Test
    public void testStrategyNames() throws Exception {
        // Note: This test depends on environment variable not being set
        // In practice, you'd use a mocking framework or test-specific config

        ThreatDetectorWithStrategy detector = new ThreatDetectorWithStrategy();

        // Default should be FilterYAML
        String strategyName = detector.getStrategyName();
        assertNotNull(strategyName);
        assertTrue("Strategy name should be set", strategyName.length() > 0);

        System.out.println("Active strategy: " + strategyName);
    }
}
