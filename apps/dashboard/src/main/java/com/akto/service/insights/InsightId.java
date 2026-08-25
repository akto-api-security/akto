package com.akto.service.insights;

import lombok.Getter;

/**
 * The Atlas Discovery insights — the original 10 agentic-AI-governance cards, plus 8
 * guardrail/violation cards merged in from feature/dashbaord/guardrail-insights. Category
 * nests here rather than in its own file — it only ever describes an InsightId.
 */
@Getter
public enum InsightId {
    MALICIOUS_COMPONENT_IN_USE("Malicious component in active use", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    UNGOVERNED_AI_RATIO("Ungoverned AI ratio", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    GUARDRAIL_COVERAGE_GAP("Guardrail coverage gap", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    SENSITIVE_DATA_DESTINATIONS("Where sensitive data is actually going", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    EXPOSURE_CONCENTRATION("Exposure concentration", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    PERSONAL_USE_OF_ENTERPRISE_AI("Enterprise AI used for personal purposes", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    OFF_DOMAIN_TOKEN_BURN("Off-domain token burn", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    DANGEROUS_CAPABILITY_EXPOSURE("Dangerous capability exposure", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    MCP_SPRAWL("Unapproved & local MCP sprawl", InsightId.Category.READ_ONLY, InsightId.Group.ATLAS_DISCOVERY),
    WHAT_CHANGED_THIS_WEEK("What changed this week", InsightId.Category.READ_ONLY, InsightId.Group.ATLAS_DISCOVERY),

    // — guardrail/violation insights (feature/dashbaord/guardrail-insights) —
    ALERT_FATIGUE("Alert fatigue — same user, same policy, over and over", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS),
    TEST_POLICIES_ON_PROD("Test policies running against production users", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS),
    SKILL_EVALUATION_CONCENTRATION("One user, one class, flooding the queue", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS),
    CREDENTIAL_EXPOSURE("Credentials and secrets — separated from PII", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS),
    PROMPT_INJECTION_REPEATS("Prompt injection attempts, grouped by source", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS),
    LIKELY_FALSE_POSITIVES("Likely false positives — flag as min risk, don't hide", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS),
    ALERT_MODE_REAL_HITS("Policies in alert mode that are catching real things", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS),
    POLICY_HYGIENE("Policy hygiene — dead policies and uncovered assets", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS);

    private final String title;
    private final Category category;
    private final Group group;

    InsightId(String title, Category category, Group group) {
        this.title = title;
        this.category = category;
        this.group = group;
    }

    /** How an insight is presented: ACTIONABLE cards carry CTAs, READ_ONLY cards are context only. */
    public enum Category {
        ACTIONABLE,
        READ_ONLY
    }

    /** Which surface an insight belongs to — Atlas Discovery vs the guardrail/violations set
     *  merged in from feature/dashbaord/guardrail-insights. Callers filter listInsights by this
     *  so the two never mix in the same list; see InsightService.listInsights. */
    public enum Group {
        ATLAS_DISCOVERY,
        GUARDRAIL_VIOLATIONS
    }
}
