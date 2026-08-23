package com.akto.service.insights;

/**
 * One entry per insight card. Shared across insight sets that plug into this same registry
 * (InsightService / InsightProviderRegistry) — guardrails' 10 today, discovery's own set is
 * expected to add its entries alongside these rather than fork the enum.
 */
public enum InsightId {

    ALERT_FATIGUE(
            "Alert fatigue — same user, same policy, over and over",
            Category.ACTIONABLE),
    TEST_POLICIES_ON_PROD(
            "Test policies running against production users",
            Category.ACTIONABLE),
    SKILL_EVALUATION_CONCENTRATION(
            "One user, one class, flooding the queue",
            Category.ACTIONABLE),
    CREDENTIAL_EXPOSURE(
            "Credentials and secrets — separated from PII",
            Category.ACTIONABLE),
    PROMPT_INJECTION_REPEATS(
            "Prompt injection attempts, grouped by source",
            Category.ACTIONABLE),
    LIKELY_FALSE_POSITIVES(
            "Likely false positives — flag as min risk, don't hide",
            Category.ACTIONABLE),
    ALERT_MODE_REAL_HITS(
            "Policies in alert mode that are catching real things",
            Category.ACTIONABLE),
    POLICY_HYGIENE(
            "Policy hygiene — dead policies and uncovered assets",
            Category.ACTIONABLE),
    SIGNAL_QUALITY(
            "Signal quality",
            Category.READ_ONLY),
    POLICY_ACTIVITY_THIS_WEEK(
            "What changed this week",
            Category.READ_ONLY);

    private final String defaultTitle;
    private final Category category;

    InsightId(String defaultTitle, Category category) {
        this.defaultTitle = defaultTitle;
        this.category = category;
    }

    public String getDefaultTitle() {
        return defaultTitle;
    }

    public Category getCategory() {
        return category;
    }

    /** How an insight is presented: ACTIONABLE cards carry CTAs, READ_ONLY cards are context only. */
    public enum Category {
        ACTIONABLE,
        READ_ONLY
    }
}
