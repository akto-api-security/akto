package com.akto.service.insights;

import lombok.Getter;

/**
 * The Atlas Discovery insights — the original 10 agentic-AI-governance cards, plus 8
 * guardrail/violation cards merged in from feature/dashbaord/guardrail-insights. Category
 * nests here rather than in its own file — it only ever describes an InsightId.
 */
@Getter
public enum InsightId {
    MALICIOUS_COMPONENT_IN_USE("Malicious component in active use", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY, true),
    UNGOVERNED_AI_RATIO("Ungoverned AI ratio", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    GUARDRAIL_COVERAGE_GAP("Guardrail coverage gap", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    SENSITIVE_DATA_DESTINATIONS("Where sensitive data is actually going", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY, true),
    EXPOSURE_CONCENTRATION("Exposure concentration", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY),
    PERSONAL_USE_OF_ENTERPRISE_AI("Enterprise AI used for personal purposes", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY, true),
    OFF_DOMAIN_TOKEN_BURN("Off-domain token burn", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY, true),
    DANGEROUS_CAPABILITY_EXPOSURE("Dangerous capability exposure", InsightId.Category.ACTIONABLE, InsightId.Group.ATLAS_DISCOVERY, true),
    MCP_SPRAWL("Unapproved & local MCP sprawl", InsightId.Category.READ_ONLY, InsightId.Group.ATLAS_DISCOVERY),
    WHAT_CHANGED_THIS_WEEK("What changed this week", InsightId.Category.READ_ONLY, InsightId.Group.ATLAS_DISCOVERY, true),

    // — guardrail/violation insights (feature/dashbaord/guardrail-insights) —
    ALERT_FATIGUE("Alert fatigue", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS),
    TEST_POLICIES_ON_PROD("Test policies on production", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS),
    EVALUATION_CONCENTRATION("Evaluation concentration", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS, true),
    CREDENTIAL_EXPOSURE("Credential exposure", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS, true),
    PROMPT_INJECTION_REPEATS("Prompt injection repeats", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS, true),
    LIKELY_FALSE_POSITIVES("Likely false positives", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS, true),
    ALERT_MODE_REAL_HITS("Alert-mode policies catching real hits", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS),
    POLICY_HYGIENE("Policy hygiene", InsightId.Category.ACTIONABLE, InsightId.Group.GUARDRAIL_VIOLATIONS);

    private final String title;
    private final Category category;
    private final Group group;
    // Static, hand-set "not ready" toggle — true only for an insight whose provider isn't wired up
    // yet, same value for every account regardless of whether that account happens to have data.
    // NOT for "this account has no data right now" — that's a real, checked result (see
    // AbstractInsightProvider.skeleton, InsightResult.disabled), not an unimplemented insight.
    private final boolean disabled;

    InsightId(String title, Category category, Group group) {
        this(title, category, group, false);
    }

    InsightId(String title, Category category, Group group, boolean disabled) {
        this.title = title;
        this.category = category;
        this.group = group;
        this.disabled = disabled;
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
