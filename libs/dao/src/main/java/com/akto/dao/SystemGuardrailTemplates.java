package com.akto.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.GuardrailPolicies;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

/**
 * Hardcoded system guardrail policy templates. Used by the one-time startup job
 * to seed system guardrails (ENDPOINT and AGENTIC) per account. Do not read from JSON.
 */
public final class SystemGuardrailTemplates {

    public static final String NAME_OFFENSIVE_WORDS = "Akto-OffensiveWordsFilter";
    public static final String NAME_HARMFUL_CONTENT = "Akto-HarmfulContentFilter";
    public static final String NAME_SENSITIVE_DATA = "Akto-SensitiveDataGuard";
    public static final String NAME_PROMPT_INJECTION = "Akto-PromptInjectionGuard";

    private static final String BLOCKED_MSG = "Sorry, the request is blocked due to security reasons";

    private SystemGuardrailTemplates() {}

    /**
     * Returns a new GuardrailPolicies instance for the given template name and context source.
     * Caller must set createdTimestamp, updatedTimestamp before insert.
     */
    public static GuardrailPolicies getTemplate(String name, CONTEXT_SOURCE contextSource) {
        GuardrailPolicies p = buildBase(name, contextSource);
        switch (name) {
            case NAME_OFFENSIVE_WORDS:
                applyOffensiveWordsFilter(p);
                break;
            case NAME_HARMFUL_CONTENT:
                applyHarmfulContentFilter(p);
                break;
            case NAME_SENSITIVE_DATA:
                applySensitiveDataGuard(p);
                break;
            case NAME_PROMPT_INJECTION:
                applyPromptInjectionGuard(p);
                break;
            default:
                throw new IllegalArgumentException("Unknown system guardrail template: " + name);
        }
        return p;
    }

    public static List<String> getAllSystemTemplateNames() {
        List<String> names = new ArrayList<>();
        names.add(NAME_OFFENSIVE_WORDS);
        names.add(NAME_HARMFUL_CONTENT);
        names.add(NAME_SENSITIVE_DATA);
        names.add(NAME_PROMPT_INJECTION);
        return names;
    }

    private static GuardrailPolicies buildBase(String name, CONTEXT_SOURCE contextSource) {
        GuardrailPolicies p = new GuardrailPolicies();
        p.setName(name);
        p.setDescription("");
        p.setBlockedMessage(BLOCKED_MSG);
        p.setActive(true);
        p.setApplyOnRequest(true);
        p.setApplyOnResponse(true);
        p.setUrl("");
        p.setConfidenceScore(0.0);
        p.setContextSource(contextSource);
        p.setSystemGuardrail(true);
        p.setCreatedBy("system");
        p.setUpdatedBy("system");
        p.setDeniedTopics(new ArrayList<>());
        p.setRegexPatterns(new ArrayList<>());
        p.setRegexPatternsV2(new ArrayList<>());
        p.setSelectedMcpServers(new ArrayList<>());
        p.setSelectedAgentServers(new ArrayList<>());
        p.setSelectedMcpServersV2(new ArrayList<>());
        p.setSelectedAgentServersV2(new ArrayList<>());
        p.setGibberishDetection(new GuardrailPolicies.GibberishDetection(false, 0.7));
        p.setAnonymizeDetection(new GuardrailPolicies.AnonymizeDetection(false, 0.7));
        p.setBanCodeDetection(new GuardrailPolicies.BanCodeDetection(false, 0.7));
        p.setSecretsDetection(new GuardrailPolicies.SecretsDetection(false, 0.7));
        p.setSentimentDetection(new GuardrailPolicies.SentimentDetection(false, 0.7));
        p.setTokenLimitDetection(new GuardrailPolicies.TokenLimitDetection(false, 0.7));
        return p;
    }

    private static void applyOffensiveWordsFilter(GuardrailPolicies p) {
        p.setSeverity("MEDIUM");
        p.setPiiTypes(new ArrayList<>());
        Map<String, Object> contentFiltering = new HashMap<>();
        contentFiltering.put("harmfulCategories", null);
        contentFiltering.put("code", null);
        contentFiltering.put("promptAttacks", null);
        p.setContentFiltering(contentFiltering);
        Map<String, Object> wordFilters = new HashMap<>();
        wordFilters.put("profanity", true);
        wordFilters.put("custom", new ArrayList<>());
        p.setWordFilters(wordFilters);
    }

    private static void applyHarmfulContentFilter(GuardrailPolicies p) {
        p.setSeverity("HIGH");
        p.setPiiTypes(new ArrayList<>());
        Map<String, Object> harmfulCategories = new HashMap<>();
        harmfulCategories.put("misconduct", "HIGH");
        harmfulCategories.put("useForResponses", true);
        harmfulCategories.put("hate", "HIGH");
        harmfulCategories.put("insults", "HIGH");
        harmfulCategories.put("sexual", "HIGH");
        harmfulCategories.put("violence", "HIGH");
        Map<String, Object> contentFiltering = new HashMap<>();
        contentFiltering.put("harmfulCategories", harmfulCategories);
        contentFiltering.put("code", null);
        contentFiltering.put("promptAttacks", null);
        p.setContentFiltering(contentFiltering);
    }

    private static void applySensitiveDataGuard(GuardrailPolicies p) {
        p.setSeverity("HIGH");
        p.setDescription("The guardrail blocks and redact any sensitive information flowing through the request or response");
        List<GuardrailPolicies.PiiType> piiTypes = new ArrayList<>();
        piiTypes.add(new GuardrailPolicies.PiiType("ssn", "mask"));
        piiTypes.add(new GuardrailPolicies.PiiType("jwt", "mask"));
        piiTypes.add(new GuardrailPolicies.PiiType("email", "mask"));
        piiTypes.add(new GuardrailPolicies.PiiType("credit_card", "block"));
        piiTypes.add(new GuardrailPolicies.PiiType("phone_number", "mask"));
        piiTypes.add(new GuardrailPolicies.PiiType("cvv", "block"));
        piiTypes.add(new GuardrailPolicies.PiiType("password", "block"));
        p.setPiiTypes(piiTypes);
        Map<String, Object> contentFiltering = new HashMap<>();
        contentFiltering.put("harmfulCategories", null);
        contentFiltering.put("code", null);
        contentFiltering.put("promptAttacks", null);
        p.setContentFiltering(contentFiltering);
        p.setAnonymizeDetection(new GuardrailPolicies.AnonymizeDetection(false, 0.5));
        p.setSecretsDetection(new GuardrailPolicies.SecretsDetection(false, 0.5));
    }

    private static void applyPromptInjectionGuard(GuardrailPolicies p) {
        p.setSeverity("HIGH");
        p.setDescription("This guardrail block prompt injection attacks payload");
        p.setPiiTypes(new ArrayList<>());
        Map<String, Object> promptAttacks = new HashMap<>();
        promptAttacks.put("level", "HIGH");
        Map<String, Object> contentFiltering = new HashMap<>();
        contentFiltering.put("harmfulCategories", null);
        contentFiltering.put("code", null);
        contentFiltering.put("promptAttacks", promptAttacks);
        p.setContentFiltering(contentFiltering);
    }
}
