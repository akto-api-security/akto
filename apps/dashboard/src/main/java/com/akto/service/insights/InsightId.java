package com.akto.service.insights;

import lombok.Getter;

/**
 * The Atlas Discovery insights — the original 10 agentic-AI-governance cards, plus 8
 * guardrail/violation cards merged in from feature/dashbaord/guardrail-insights. Category
 * nests here rather than in its own file — it only ever describes an InsightId.
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
    WHAT_CHANGED_THIS_WEEK("What changed this week", InsightId.Category.READ_ONLY),

    // — guardrail/violation insights (feature/dashbaord/guardrail-insights) —
    ALERT_FATIGUE("Alert fatigue — same user, same policy, over and over", InsightId.Category.ACTIONABLE),
    TEST_POLICIES_ON_PROD("Test policies running against production users", InsightId.Category.ACTIONABLE),
    SKILL_EVALUATION_CONCENTRATION("One user, one class, flooding the queue", InsightId.Category.ACTIONABLE),
    CREDENTIAL_EXPOSURE("Credentials and secrets — separated from PII", InsightId.Category.ACTIONABLE),
    PROMPT_INJECTION_REPEATS("Prompt injection attempts, grouped by source", InsightId.Category.ACTIONABLE),
    LIKELY_FALSE_POSITIVES("Likely false positives — flag as min risk, don't hide", InsightId.Category.ACTIONABLE),
    ALERT_MODE_REAL_HITS("Policies in alert mode that are catching real things", InsightId.Category.ACTIONABLE),
    POLICY_HYGIENE("Policy hygiene — dead policies and uncovered assets", InsightId.Category.ACTIONABLE);

    private final String title;
    private final Category category;

    InsightId(String title, Category category) {
        this.title = title;
        this.category = category;
    }

    /** How an insight is presented: ACTIONABLE cards carry CTAs, READ_ONLY cards are context only. */
    public enum Category {
        ACTIONABLE,
        READ_ONLY
    }
}
