package com.akto.threat.detection.strategy;

import com.akto.threat.detection.utils.ThreatDetector;

/**
 * Factory for creating ThreatDetectionStrategy instances.
 * The filter YAML loop always uses FilterYamlDetectionStrategy.
 * Hyperscan detection is handled separately via HyperscanEventHandler.
 */
public class ThreatDetectionStrategyFactory {

    public static ThreatDetectionStrategy createStrategy(ThreatDetector threatDetector) {
        return new FilterYamlDetectionStrategy(threatDetector);
    }
}
