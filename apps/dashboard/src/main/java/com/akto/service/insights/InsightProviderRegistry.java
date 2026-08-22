package com.akto.service.insights;

import com.akto.service.insights.providers.DangerousCapabilityExposureProvider;
import com.akto.service.insights.providers.ExposureConcentrationProvider;
import com.akto.service.insights.providers.GuardrailCoverageGapProvider;
import com.akto.service.insights.providers.MaliciousComponentInUseProvider;
import com.akto.service.insights.providers.McpSprawlProvider;
import com.akto.service.insights.providers.OffDomainTokenBurnProvider;
import com.akto.service.insights.providers.PersonalUseOfEnterpriseAiProvider;
import com.akto.service.insights.providers.SensitiveDataDestinationsProvider;
import com.akto.service.insights.providers.UngovernedAiRatioProvider;
import com.akto.service.insights.providers.WhatChangedThisWeekProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * InsightId -> provider. Insertion order below is the cheapest/most-certain-first order
 * from the build plan (10, 9, 2, 4, 3, 1, 5, 8, 6, 7) and is what fetchInsightsList
 * iterates, so the list reads in a stable, deliberate order.
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
