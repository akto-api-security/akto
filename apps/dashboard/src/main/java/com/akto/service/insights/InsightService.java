package com.akto.service.insights;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.ArrayList;
import java.util.List;

public class InsightService {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InsightService.class, LogDb.DASHBOARD);

    public List<InsightResult> fetchList(InsightContext ctx, AbstractThreatDetectionAction threatClient) {
        InsightDataBundle bundle = InsightDataLoader.load(ctx, threatClient);
        List<InsightResult> results = new ArrayList<>();
        for (InsightId id : InsightId.values()) {
            results.add(computeSafely(id, bundle, ctx, InsightProvider.Scope.LIST, threatClient));
        }
        return results;
    }

    public InsightResult fetchDetail(InsightId id, InsightContext ctx, AbstractThreatDetectionAction threatClient) {
        InsightDataBundle bundle = InsightDataLoader.load(ctx, threatClient);
        InsightResult result = computeSafely(id, bundle, ctx, InsightProvider.Scope.DETAIL, threatClient);
        // Narrative generation (LLM markdown + InsightNarrativeCache) is a later step; until it
        // lands every detail response is metrics/evidence/ctas only, which is a complete and
        // honest response on its own.
        result.setMarkdown(null);
        result.setNarrativeStatus("UNAVAILABLE");
        return result;
    }

    private InsightResult computeSafely(InsightId id, InsightDataBundle bundle, InsightContext ctx, InsightProvider.Scope scope,
                                         AbstractThreatDetectionAction threatClient) {
        InsightProvider provider = InsightProviderRegistry.get(id);
        if (provider == null) {
            return InsightResult.noData(id, "Provider not implemented yet");
        }
        try {
            return provider.compute(bundle, ctx, scope, threatClient);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Insight provider failed for " + id.name());
            return InsightResult.noData(id, "Provider error");
        }
    }
}
