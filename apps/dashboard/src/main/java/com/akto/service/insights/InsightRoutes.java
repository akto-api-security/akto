package com.akto.service.insights;

/** CTA route constants — every one is a real path in App.js. Never build a route ad hoc. */
public final class InsightRoutes {
    private InsightRoutes() {}

    public static final String AGENTIC_ASSETS = "/dashboard/observe/agentic-assets";
    public static final String USERS_AND_DEVICES = "/dashboard/observe/users-and-devices";
    public static final String LLM_OBSERVABILITY = "/dashboard/observe/llm-observability";
    public static final String AUDIT = "/dashboard/observe/audit";
    public static final String ENDPOINT_SHIELD = "/dashboard/observe/endpoint-shield";
    public static final String INVENTORY = "/dashboard/observe/inventory";
    public static final String GUARDRAIL_POLICIES = "/dashboard/guardrails/policies";
    public static final String GUARDRAIL_VIOLATIONS = "/dashboard/guardrails/violations";
    public static final String GUARDRAIL_ACTIVITY = "/dashboard/guardrails/activity";
    public static final String GUARDRAIL_MISCONFIGURATIONS = "/dashboard/guardrails/misconfigurations";
    public static final String AGENTIC_ASSET_DEVICES = "/dashboard/observe/agentic-assets-legacy/devices";

    // — added for API_POSTURE / TESTING_POSTURE (Ask Akto overlay) —
    public static final String ISSUES = "/dashboard/issues";
    public static final String TESTING = "/dashboard/testing";
    public static final String SENSITIVE_DATA = "/dashboard/observe/sensitive";
    public static final String API_CHANGES = "/dashboard/observe/changes";

    // — added for the RecommendationCatalog AGENTIC domain —
    public static final String THREAT_ACTIVITY = "/dashboard/protection/threat-activity";
}
