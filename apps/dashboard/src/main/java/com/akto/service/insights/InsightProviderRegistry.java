package com.akto.service.insights;

import com.akto.service.insights.providers.AlertFatigueProvider;
import com.akto.service.insights.providers.AlertModeRealHitsProvider;
import com.akto.service.insights.providers.CredentialExposureProvider;
import com.akto.service.insights.providers.LikelyFalsePositivesProvider;
import com.akto.service.insights.providers.PolicyHygieneProvider;
import com.akto.service.insights.providers.PromptInjectionRepeatsProvider;
import com.akto.service.insights.providers.SkillEvaluationConcentrationProvider;
import com.akto.service.insights.providers.TestPoliciesOnProdProvider;

import java.util.EnumMap;
import java.util.Map;

/**
 * InsightId -> InsightProvider. An InsightId with no registered provider is a normal, expected
 * state while that insight is still being built — InsightService treats it the same as a
 * provider that threw (NO_DATA), not as an error.
 */
public class InsightProviderRegistry {

    private static final Map<InsightId, InsightProvider> PROVIDERS = new EnumMap<>(InsightId.class);

    static {
        // Providers register here one at a time as they're implemented.
        register(new TestPoliciesOnProdProvider());
        register(new SkillEvaluationConcentrationProvider());
        register(new CredentialExposureProvider());
        register(new AlertFatigueProvider());
        register(new PromptInjectionRepeatsProvider());
        register(new LikelyFalsePositivesProvider());
        register(new AlertModeRealHitsProvider());
        register(new PolicyHygieneProvider());
    }

    private InsightProviderRegistry() {}

    private static void register(InsightProvider provider) {
        PROVIDERS.put(provider.getInsightId(), provider);
    }

    public static InsightProvider get(InsightId id) {
        return PROVIDERS.get(id);
    }
}
