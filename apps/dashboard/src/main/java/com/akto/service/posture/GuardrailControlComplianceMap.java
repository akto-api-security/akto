package com.akto.service.posture;

import java.util.Arrays;
import java.util.List;

/**
 * Resolves a built-in guardrail control's {@code rule_violated} value to its compliance
 * "capability" key — the same key {@code guardrail_compliance_infos} documents are stored under
 * (id {@code "guardrails/<capability>.conf"}), mirroring {@code getGuardrailCapabilityForRule} in
 * {@code pages/threat_detection/constants/guardrailRuleDefinitions.js}. The actual framework/clause
 * data lives in {@link com.akto.dao.threat_detection.GuardrailComplianceInfosDao}, read at call
 * time by ArgusPostureService#frameworkReadiness — not hardcoded here.
 *
 * <p>Per-policy capabilities ({@code deniedTopics}/{@code llmRule}) aren't ported here — those are
 * merged from each policy's own compliance field, a different path (see
 * ComplianceClauseScanService for the llmRule case).
 */
public final class GuardrailControlComplianceMap {

    private GuardrailControlComplianceMap() {}

    private static final class Group {
        final List<String> prefixes;
        final String capability;

        Group(List<String> prefixes, String capability) {
            this.prefixes = prefixes;
            this.capability = capability;
        }
    }

    private static final List<Group> ENTRIES = Arrays.asList(
            new Group(Arrays.asList("PromptInjection", "prompt_injection"), "promptAttacks"),
            new Group(Arrays.asList("Toxicity", "HarmfulCategories", "harmful"), "harmfulCategories"),
            new Group(Arrays.asList("PII-", "PII_", "pii"), "piiTypes"),
            new Group(Arrays.asList("Secrets", "SecretsDetection", "secret"), "secretsDetection"),
            new Group(Arrays.asList("Anonymize", "anonymize"), "anonymizeDetection"),
            new Group(Arrays.asList("Gibberish", "gibberish"), "gibberishDetection"),
            new Group(Arrays.asList("Sentiment", "sentiment"), "sentimentDetection"),
            new Group(Arrays.asList("BlockedHost", "blocked_host", "block_host", "BlockedHosts",
                    "block_host_policy", "EndpointNotWhitelisted"), "blockedHosts"),
            new Group(Arrays.asList("ContextPoisoning", "context_poisoning"), "contextPoisoning")
    );

    /**
     * @return the guardrail_compliance_infos capability key this rule_violated value resolves to,
     * or null if it doesn't match any known built-in control. Case-insensitive prefix-or-contains
     * match, same convention getGuardrailCapabilityForRule already uses for this same field.
     */
    public static String capabilityForRuleViolated(String ruleViolated) {
        if (ruleViolated == null) return null;
        String rv = ruleViolated.trim().toLowerCase();
        if (rv.isEmpty() || rv.equals("-")) return null;

        for (Group group : ENTRIES) {
            for (String prefix : group.prefixes) {
                String pfx = prefix.toLowerCase();
                if (rv.startsWith(pfx) || rv.contains(pfx)) {
                    return group.capability;
                }
            }
        }
        return null;
    }
}
