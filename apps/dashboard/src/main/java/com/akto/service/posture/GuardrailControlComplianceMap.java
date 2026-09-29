package com.akto.service.posture;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Static, deterministic mapping from a built-in guardrail control's {@code rule_violated} value
 * (already stored on every violation's metadata) to one {@link com.akto.util.compliance
 * .ComplianceSubClauseCatalog} sub-clause — for control types that never go through the LLM-driven
 * compliance-mapping flow ({@code GuardrailPolicies.LLMRule#getCompliance()}), because they aren't
 * LLMRule-based at all (built-in PII/prompt-injection/harmful-category detection, not a
 * user-authored rule). See ArgusPostureService#frameworkReadiness for the caller.
 *
 * <p>Prefix lists are the same ones already established for the "Content &amp; Policy Guardrails"
 * grouping in the (frontend-only, disconnected) {@code guardrails/components/owaspConfig.js} — reused
 * here for consistency with what the violations table already classifies as the same control, just
 * repointed at {@code ComplianceSubClauseCatalog}'s ids instead of the OWASP-ASI ones that file uses.
 *
 * <p>Only two control types are wired today (prompt injection, harmful category) — deliberately not
 * exhaustive. Extend {@link #ENTRIES} with a new group when another built-in control needs a mapping.
 */
public final class GuardrailControlComplianceMap {

    private GuardrailControlComplianceMap() {}

    /** One clause a group of rule_violated prefixes all map to. */
    public static final class ClauseMatch {
        public final String framework;
        public final String subClauseId;

        ClauseMatch(String framework, String subClauseId) {
            this.framework = framework;
            this.subClauseId = subClauseId;
        }
    }

    private static final class Group {
        final List<String> prefixes;
        final ClauseMatch match;

        Group(List<String> prefixes, String framework, String subClauseId) {
            this.prefixes = prefixes;
            this.match = new ClauseMatch(framework, subClauseId);
        }
    }

    private static final List<Group> ENTRIES = Arrays.asList(
            new Group(
                    Arrays.asList("PromptInjection", "prompt_injection", "IntentAnalysis", "intent"),
                    "OWASP LLM", "LLM01"),
            new Group(
                    Arrays.asList("harmful", "BanTopics", "Toxicity", "BanSubstrings", "denied_topic",
                            "BanCompetitors"),
                    "NIST AI Risk Management Framework", "MANAGE")
    );

    /** Distinct, ordered set of every framework this map can ever attribute a hit to — the fixed
     *  set of frameworks Argus's Framework Readiness panel tracks. */
    public static Set<String> frameworks() {
        Set<String> out = new LinkedHashSet<>();
        for (Group group : ENTRIES) {
            out.add(group.match.framework);
        }
        return out;
    }

    /**
     * @return the clause this rule_violated value maps to, or null if it doesn't match any known
     * built-in control group. Case-insensitive prefix-or-contains match, same convention
     * {@code owaspConfig.js#getOwaspThreatsForRule} already uses for this same field.
     */
    public static ClauseMatch matchRuleViolated(String ruleViolated) {
        if (ruleViolated == null) return null;
        String rv = ruleViolated.trim().toLowerCase();
        if (rv.isEmpty() || rv.equals("-")) return null;

        for (Group group : ENTRIES) {
            for (String prefix : group.prefixes) {
                String pfx = prefix.toLowerCase();
                if (rv.startsWith(pfx) || rv.contains(pfx)) {
                    return group.match;
                }
            }
        }
        return null;
    }
}
