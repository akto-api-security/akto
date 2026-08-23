package com.akto.service.insights;

/**
 * One entry per insight card. Shared across insight sets that plug into this same registry
 * (InsightService / InsightProviderRegistry) — guardrails' 10 today, discovery's own set is
 * expected to add its entries alongside these rather than fork the enum.
 */
public enum InsightId {

    ALERT_FATIGUE(
            "Alert fatigue — same user, same policy, over and over",
            InsightCategory.ACTIONABLE),
    TEST_POLICIES_ON_PROD(
            "Test policies running against production users",
            InsightCategory.ACTIONABLE),
    SKILL_EVALUATION_CONCENTRATION(
            "One user, one class, flooding the queue",
            InsightCategory.ACTIONABLE),
    CREDENTIAL_EXPOSURE(
            "Credentials and secrets — separated from PII",
            InsightCategory.ACTIONABLE),
    PROMPT_INJECTION_REPEATS(
            "Prompt injection attempts, grouped by source",
            InsightCategory.ACTIONABLE),
    LIKELY_FALSE_POSITIVES(
            "Likely false positives — flag as min risk, don't hide",
            InsightCategory.ACTIONABLE),
    ALERT_MODE_REAL_HITS(
            "Policies in alert mode that are catching real things",
            InsightCategory.ACTIONABLE),
    POLICY_HYGIENE(
            "Policy hygiene — dead policies and uncovered assets",
            InsightCategory.ACTIONABLE),
    SIGNAL_QUALITY(
            "Signal quality",
            InsightCategory.READ_ONLY),
    POLICY_ACTIVITY_THIS_WEEK(
            "What changed this week",
            InsightCategory.READ_ONLY);

    private final String defaultTitle;
    private final InsightCategory category;

    InsightId(String defaultTitle, InsightCategory category) {
        this.defaultTitle = defaultTitle;
        this.category = category;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public InsightCategory getCategory() {
        return category;
    }
}
