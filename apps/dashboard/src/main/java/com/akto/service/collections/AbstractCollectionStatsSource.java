package com.akto.service.collections;

import com.akto.action.ApiCollectionsAction;
import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiCollectionStatsMeta;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.service.TimedService;
import com.akto.usage.UsageMetricCalculator;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The parts of a CollectionStatsSource that do not depend on where the counts come from. */
abstract class AbstractCollectionStatsSource extends TimedService implements CollectionStatsSource {

    private final ApiCollectionStatsDao statsDao = ApiCollectionStatsDao.instance;

    /** Raw per collection counts, before ApiCollectionsAction.resolveUrlsCount applies its fallbacks. */
    protected abstract Map<Integer, Integer> buildCountMap(List<ApiCollection> collections);

    /** collection id -> sensitive sub types. */
    protected abstract Map<Integer, ? extends Iterable<String>> buildSensitiveMap();

    protected abstract int countSensitiveEndpoints();

    @Override
    public void refreshEndpointsCount() {
        int startedAt = Context.now();
        List<ApiCollection> collections = timed("load collections to count", this::loadCollectionsForCount, List::size);
        Map<Integer, Integer> countMap = timed("count endpoints per collection (" + getClass().getSimpleName() + ")",
                () -> buildCountMap(collections), Map::size);

        timed("write endpoint counts", () -> {
            try (ApiCollectionStatsMetricWriter writer = new ApiCollectionStatsMetricWriter(ApiCollectionStats.ENDPOINTS_COUNT, startedAt)) {
                for (ApiCollection collection : collections) {
                    writer.set(collection.getId(),
                            ApiCollectionsAction.resolveUrlsCount(collection, countMap.get(collection.getId())));
                }
            }
        });
        timed("count endpoints of deactivated hostname collections", () -> applyDeactivatedHostnameCounts(startedAt));
    }

    /**
     * Counts exclude deactivated collections, so a deactivated hostname collection would show its
     * fallback. ApiCollectionsAction#fetchCountForHostnameDeactivatedCollections counted those
     * separately; keep that exact method so the deactivated tab shows the same numbers as before.
     */
    private void applyDeactivatedHostnameCounts(int startedAt) {
        Set<Integer> deactivated = UsageMetricCalculator.getDeactivated();
        if (deactivated == null || deactivated.isEmpty()) return;

        Set<Integer> hostnameIds = new HashSet<>();
        for (ApiCollection c : ApiCollectionsDao.instance.findAllNoRbacFilter(
                Filters.and(Filters.exists(ApiCollection.HOST_NAME), Filters.in(ApiCollection.ID, deactivated)),
                Projections.include(ApiCollection.ID))) {
            hostnameIds.add(c.getId());
        }
        if (hostnameIds.isEmpty()) return;

        Map<Integer, Integer> counts = ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMap(
                Filters.in(SingleTypeInfo._COLLECTION_IDS, hostnameIds));
        try (ApiCollectionStatsMetricWriter writer = new ApiCollectionStatsMetricWriter(ApiCollectionStats.ENDPOINTS_COUNT, startedAt)) {
            counts.forEach((id, count) -> {
                if (hostnameIds.contains(id)) writer.set(id, count);
            });
        }
    }

    @Override
    public void refreshSensitive() {
        int startedAt = Context.now();
        Map<Integer, ? extends Iterable<String>> sensitiveMap = timed("sensitive sub types per collection (" + getClass().getSimpleName() + ")",
                this::buildSensitiveMap, Map::size);
        timed("write sensitive sub types", () -> {
            try (ApiCollectionStatsMetricWriter writer = new ApiCollectionStatsMetricWriter(ApiCollectionStats.SENSITIVE_SUB_TYPES, startedAt)) {
                sensitiveMap.forEach((id, subTypes) -> {
                    List<String> list = new ArrayList<>();
                    subTypes.forEach(list::add);
                    writer.set(id, list);
                });
                // the rows not written above have no sensitive data (any more)
                writer.resetUnwritten(new ArrayList<String>());
            }
        });
        int sensitiveEndpoints = timed("count sensitive endpoints (" + getClass().getSimpleName() + ")", this::countSensitiveEndpoints);
        CollectionStatsRefresher.saveSummary(Updates.set(ApiCollectionStatsMeta.TOTAL_SENSITIVE_ENDPOINTS, sensitiveEndpoints));
    }

    private List<ApiCollection> loadCollectionsForCount() {
        List<ApiCollection> collections = new ArrayList<>();
        // every collection, not the ones a user may see: this runs in the background for the whole account
        for (ApiCollectionStats stats : statsDao.findAllNoRbacFilter(Filters.empty(),
                Projections.include(ApiCollectionStats.ID, ApiCollectionStats.TYPE,
                        ApiCollectionStats.HOST_NAME, ApiCollectionStats.URLS_FALLBACK_COUNT))) {
            ApiCollection c = new ApiCollection();
            c.setId(stats.getId());
            c.setHostName(stats.getHostName());
            if (stats.getType() != null) c.setType(ApiCollection.Type.valueOf(stats.getType()));
            // urls itself stays null on purpose: resolveUrlsCount then falls back to this
            c.setUrlsCount(stats.getUrlsFallbackCount());
            collections.add(c);
        }
        return collections;
    }

    static Bson deactivatedFilter() {
        return Filters.nin(SingleTypeInfo._API_COLLECTION_ID, UsageMetricCalculator.getDeactivated());
    }
}
