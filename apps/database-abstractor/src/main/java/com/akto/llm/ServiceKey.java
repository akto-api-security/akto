package com.akto.llm;

import lombok.Getter;
import lombok.Setter;

/**
 * Canonical LLM service keys — single source of truth for every caller of
 * {@code LLMService.callLLM(...)}: the string key used for {@code LlmServiceChains}/
 * {@code llm_service_config} lookups, the max_tokens budget for that feature, and whether an
 * external caller may select it via the (optional) serviceKey field on
 * {@code POST /api/getLLMResponseV2}. See apps/database-abstractor/docs/llm-service-keys.md for
 * the full feature inventory behind each key.
 */

@Getter
public enum ServiceKey {

    // Set only by direct in-repo Java calls (SkillValidationAction/SkillValidationV2Action,
    // SettingsScanAction) — not externally selectable, so an external caller can't name itself
    // into these dedicated chains/quotas.
    ENDPOINT_SHIELD_SKILL_VALIDATION("endpoint-shield-skill-validation", 5000, 0, false),
    ENDPOINT_SHIELD_SETTINGS_SCAN("endpoint-shield-settings-scan", 16000, 0, false),

    // Externally selectable via the serviceKey field on POST /api/getLLMResponseV2 and V3.
    MINI_TESTING_TEST_EDITOR("mini-testing-test-editor", 10000, 0.1, true),
    AI_AGENT_SHIELD_SESSION_SUMMARY("ai-agent-shield-session-summary", 150, 0.1, true),
    AI_AGENT_SHIELD_SCHEMA_DISCOVERY("ai-agent-shield-schema-discovery", 10000, 0, true),
    ENDPOINT_SHIELD_RULE_EVALUATION("endpoint-shield-rule-evaluation", 10000, 0, true),
    ENDPOINT_SHIELD_REMEDIATION("endpoint-shield-remediation", 10000, 0, true),
    ENDPOINT_SHIELD_MCP_METADATA_VALIDATOR("endpoint-shield-mcp-metadata-validator", 10000, 0, true),

    // Fallback used when the external serviceKey field is absent, blank, or not externallySelectable
    // on V2 — unchanged from the single key every external caller used before getLLMResponseV2 gained
    // a serviceKey field. V3 has no legacy callers to be compatible with, so it requires a real,
    // externally-selectable key instead of ever falling back to this one.
    DEFAULT("llm_response_v2", 10000, 0, false);

    private final String key;
    private final int maxTokens;
    private final double temperature;
    private final boolean externallySelectable;

    ServiceKey(String key, int maxTokens, double temperature, boolean externallySelectable) {
        this.key = key;
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.externallySelectable = externallySelectable;
    }

    /** Null when no ServiceKey has this string key. */
    public static ServiceKey fromKey(String key) {
        if (key == null) return null;
        for (ServiceKey serviceKey : values()) {
            if (serviceKey.key.equals(key)) return serviceKey;
        }
        return null;
    }
}
