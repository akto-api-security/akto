package com.akto.threat.detection.strategy;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApi;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.type.URLTemplate;
import com.akto.threat.detection.utils.ThreatDetector;

import java.util.List;

/**
 * Filter YAML-based threat detection strategy.
 * Uses the existing ThreatDetector logic with Filter YAML templates.
 * This is the default and most comprehensive detection mode.
 */
public class FilterYamlDetectionStrategy implements ThreatDetectionStrategy {

    private final ThreatDetector threatDetector;

    public FilterYamlDetectionStrategy(ThreatDetector threatDetector) {
        this.threatDetector = threatDetector;
    }

    @Override
    public boolean applyFilter(
            FilterConfig threatFilter,
            HttpResponseParams httpResponseParams,
            RawApi rawApi,
            ApiInfoKey apiInfoKey,
            URLTemplate matchedTemplate) {

        // Delegate to existing ThreatDetector implementation
        return threatDetector.applyFilter(
            threatFilter,
            httpResponseParams,
            rawApi,
            apiInfoKey,
            matchedTemplate
        );
    }

    @Override
    public List<com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError> getThreatPositions(
            String filterId,
            HttpResponseParams httpResponseParams) {

        // Delegate to existing ThreatDetector implementation
        return threatDetector.getThreatPositions(filterId, httpResponseParams);
    }

    @Override
    public String getStrategyName() {
        return "FilterYAML";
    }
}
