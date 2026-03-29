package com.akto.threat.detection.utils;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApi;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.type.URLTemplate;
import com.akto.threat.detection.strategy.FilterYamlDetectionStrategy;

import java.util.List;

/**
 * Wrapper around ThreatDetector that delegates filter-based detection
 * to FilterYamlDetectionStrategy. Hyperscan detection is handled
 * separately by HyperscanEventHandler.
 */
public class ThreatDetectorWithStrategy {

    private final ThreatDetector threatDetector;
    private final FilterYamlDetectionStrategy strategy;

    public ThreatDetectorWithStrategy() throws Exception {
        this.threatDetector = new ThreatDetector();
        this.strategy = new FilterYamlDetectionStrategy(threatDetector);
    }

    public boolean applyFilter(FilterConfig threatFilter, HttpResponseParams httpResponseParams,
                               RawApi rawApi, ApiInfoKey apiInfoKey, URLTemplate matchedTemplate) {
        return strategy.applyFilter(threatFilter, httpResponseParams, rawApi, apiInfoKey, matchedTemplate);
    }

    public List<com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError> getThreatPositions(
            String filterId, HttpResponseParams httpResponseParams) {
        return strategy.getThreatPositions(filterId, httpResponseParams);
    }

    public URLTemplate findMatchingUrlTemplate(HttpResponseParams httpResponseParams) {
        return threatDetector.findMatchingUrlTemplate(httpResponseParams);
    }

    public boolean shouldIgnoreApi(FilterConfig apiFilter, RawApi rawApi, ApiInfoKey apiInfoKey) {
        return threatDetector.shouldIgnoreApi(apiFilter, rawApi, apiInfoKey);
    }

    public boolean isSuccessfulExploit(List<FilterConfig> successfulExploitFilters,
                                       RawApi rawApi, ApiInfoKey apiInfoKey) {
        return threatDetector.isSuccessfulExploit(successfulExploitFilters, rawApi, apiInfoKey);
    }

    public boolean isIgnoredEvent(List<FilterConfig> ignoredEventFilters,
                                  RawApi rawApi, ApiInfoKey apiInfoKey) {
        return threatDetector.isIgnoredEvent(ignoredEventFilters, rawApi, apiInfoKey);
    }

    public String getStrategyName() {
        return "FilterYAML";
    }
}
