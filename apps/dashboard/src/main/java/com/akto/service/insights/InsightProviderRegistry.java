package com.akto.service.insights;

import com.akto.service.insights.providers.AlertFatigueProvider;
import com.akto.service.insights.providers.AlertModeRealHitsProvider;
import com.akto.service.insights.providers.CredentialExposureProvider;
import com.akto.service.insights.providers.DangerousCapabilityExposureProvider;
import com.akto.service.insights.providers.ExposureConcentrationProvider;
import com.akto.service.insights.providers.GuardrailCoverageGapProvider;
import com.akto.service.insights.providers.LikelyFalsePositivesProvider;
import com.akto.service.insights.providers.MaliciousComponentInUseProvider;
import com.akto.service.insights.providers.McpSprawlProvider;
import com.akto.service.insights.providers.OffDomainTokenBurnProvider;
import com.akto.service.insights.providers.PersonalUseOfEnterpriseAiProvider;
import com.akto.service.insights.providers.PolicyHygieneProvider;
import com.akto.service.insights.providers.PromptInjectionRepeatsProvider;
import com.akto.service.insights.providers.SensitiveDataDestinationsProvider;
import com.akto.service.insights.providers.SkillEvaluationConcentrationProvider;
import com.akto.service.insights.providers.TestPoliciesOnProdProvider;
import com.akto.service.insights.providers.UngovernedAiRatioProvider;
import com.akto.service.insights.providers.WhatChangedThisWeekProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * InsightId -> provider. Insertion order below is the cheapest/most-certain-first order
 * from the build plan (10, 9, 2, 4, 3, 1, 5, 8, 6, 7), then the 8 guardrail/violation
 * providers merged in from feature/dashbaord/guardrail-insights in their own
 * cheapest-first order — and is what fetchInsightsList iterates, so the list reads in a
 * stable, deliberate order.
 */
public final class InsightProviderRegistry {

    private static final Map<InsightId, InsightProvider> PROVIDERS = new LinkedHashMap<>();

    static {
        register(new WhatChangedThisWeekProvider());
        register(new McpSprawlProvider());
        register(new UngovernedAiRatioProvider());
        register(new SensitiveDataDestinationsProvider());
        register(new GuardrailCoverageGapProvider());
        register(new MaliciousComponentInUseProvider());
        register(new ExposureConcentrationProvider());
        register(new DangerousCapabilityExposureProvider());
        register(new PersonalUseOfEnterpriseAiProvider());
        register(new OffDomainTokenBurnProvider());

        register(new PolicyHygieneProvider());
        register(new AlertModeRealHitsProvider());
        register(new AlertFatigueProvider());
        register(new CredentialExposureProvider());
        register(new LikelyFalsePositivesProvider());
        register(new PromptInjectionRepeatsProvider());
        register(new TestPoliciesOnProdProvider());
        register(new SkillEvaluationConcentrationProvider());
    }

    private InsightProviderRegistry() {}

    private static void register(InsightProvider p) {
        PROVIDERS.put(p.id(), p);
    }

    public static InsightProvider get(InsightId id) {
        return PROVIDERS.get(id);
    }

    public static Map<InsightId, InsightProvider> all() {
        return PROVIDERS;
    }
}
