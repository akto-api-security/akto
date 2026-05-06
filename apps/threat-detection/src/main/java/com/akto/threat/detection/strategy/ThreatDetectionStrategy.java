package com.akto.threat.detection.strategy;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApi;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.type.URLTemplate;

import java.util.List;

/**
 * Strategy interface for different threat detection approaches.
 * Implementations can use Filter YAML, Hyperscan, or hybrid approaches.
 */
public interface ThreatDetectionStrategy {

    /**
     * Apply a threat filter to detect malicious traffic.
     *
     * @param threatFilter The filter configuration to apply
     * @param httpResponseParams The HTTP request/response data
     * @param rawApi The raw API data
     * @param apiInfoKey The API info key
     * @param matchedTemplate The matched URL template (can be null)
     * @return true if threat is detected, false otherwise
     */
    boolean applyFilter(
        FilterConfig threatFilter,
        HttpResponseParams httpResponseParams,
        RawApi rawApi,
        ApiInfoKey apiInfoKey,
        URLTemplate matchedTemplate
    );

    /**
     * Get threat positions/details for reporting.
     *
     * @param filterId The filter ID that detected the threat
     * @param httpResponseParams The HTTP request/response data
     * @return List of schema conformance errors with threat details
     */
    List<com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError> getThreatPositions(
        String filterId,
        HttpResponseParams httpResponseParams
    );

    /**
     * Get the name of this detection strategy.
     *
     * @return Strategy name for logging/debugging
     */
    String getStrategyName();
}
