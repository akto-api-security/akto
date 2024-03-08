package com.akto.dto.usage;

/*
 * Add ACCOUNTS_COUNT
 *
 */

public enum MetricTypes {
    ACTIVE_ENDPOINTS ("feature-ap-is"),
    CUSTOM_TESTS ("feature-custom-templates"),
    TEST_RUNS ("feature-test-runs"),
    ACTIVE_ACCOUNTS ("feature-sso");

    public final String label;

    private MetricTypes(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
