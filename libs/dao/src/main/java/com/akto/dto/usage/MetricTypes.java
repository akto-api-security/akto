package com.akto.dto.usage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Add ACCOUNTS_COUNT
 *
 * Units differ per metric:
 *  - ACTIVE_ENDPOINTS: endpoints in API / DAST collections.
 *  - AI_ASSET_COUNT: number of agentic (MCP / GenAI / ai-agent-caller) collections.
 *  - ENDPOINT_ASSET_COUNT: max devices running a single AI agent (agentic assets summary).
 *  - MCP_ASSET_COUNT: retired. Kept only so stored usage/entitlement documents still decode.
 */

public enum MetricTypes {
    ACTIVE_ENDPOINTS ("feature-ap-is"),
    CUSTOM_TESTS ("feature-custom-templates"),
    TEST_RUNS ("feature-test-runs"),
    ACTIVE_ACCOUNTS ("feature-sso"),
    @Deprecated
    MCP_ASSET_COUNT("feature-mcp-asset-count"),
    AI_ASSET_COUNT("feature-ai-asset-count"),
    ENDPOINT_ASSET_COUNT("feature-endpoint-asset-count");

    public final String label;

    private MetricTypes(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /**
     * Metrics the usage cron measures and billing aggregates. Excludes only the retired
     * MCP_ASSET_COUNT.
     */
    public static List<MetricTypes> measured() {
        List<MetricTypes> ret = new ArrayList<>();
        for (MetricTypes metricType : values()) {
            if (metricType != MCP_ASSET_COUNT) {
                ret.add(metricType);
            }
        }
        return Collections.unmodifiableList(ret);
    }
}
