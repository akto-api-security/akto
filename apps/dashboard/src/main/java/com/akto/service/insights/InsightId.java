package com.akto.service.insights;

import lombok.Getter;

/**
 * The 10 Atlas Discovery insights. Category nests here rather than in its own file —
 * it only ever describes an InsightId.
 */
@Getter
public enum InsightId {
    MALICIOUS_COMPONENT_IN_USE("Malicious component in active use", InsightId.Category.ACTIONABLE),
    UNGOVERNED_AI_RATIO("Ungoverned AI ratio", InsightId.Category.ACTIONABLE),
    GUARDRAIL_COVERAGE_GAP("Guardrail coverage gap", InsightId.Category.ACTIONABLE),
    SENSITIVE_DATA_DESTINATIONS("Where sensitive data is actually going", InsightId.Category.ACTIONABLE),
    EXPOSURE_CONCENTRATION("Exposure concentration", InsightId.Category.ACTIONABLE),
    PERSONAL_USE_OF_ENTERPRISE_AI("Enterprise AI used for personal purposes", InsightId.Category.ACTIONABLE),
    OFF_DOMAIN_TOKEN_BURN("Off-domain token burn", InsightId.Category.ACTIONABLE),
    DANGEROUS_CAPABILITY_EXPOSURE("Dangerous capability exposure", InsightId.Category.ACTIONABLE),
    MCP_SPRAWL("Unapproved & local MCP sprawl", InsightId.Category.READ_ONLY),
    WHAT_CHANGED_THIS_WEEK("What changed this week", InsightId.Category.READ_ONLY);

    private final String title;
    private final Category category;

    InsightId(String title, Category category) {
        this.title = title;
        this.category = category;
    }

    public enum Category {
        ACTIONABLE,
        READ_ONLY
    }
}
