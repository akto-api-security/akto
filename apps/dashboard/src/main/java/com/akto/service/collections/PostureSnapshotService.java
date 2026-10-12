package com.akto.service.collections;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.service.TimedService;
import com.akto.service.collections.PostureSnapshotResponse.ActionItemCounts;
import com.akto.service.collections.PostureSnapshotResponse.Missing;
import com.mongodb.client.model.Filters;

import java.util.List;

import static com.akto.util.Constants.AKTO_DISCOVERED_APIS_COLLECTION;

/**
 * The api numbers of the home dashboard in one call: the stats of the range, the action item counts
 * and the missing data figures. Where they come from is a {@link PostureSource}; asking also keeps the
 * per collection counts current (refresh on read, like the collections table).
 */
public class PostureSnapshotService extends TimedService {

    // the new domains tile shows this many
    static final int NEW_DOMAINS = 10;

    public PostureSnapshotResponse fetch(int startTimestamp, int endTimestamp, boolean force) {
        CollectionStatsRefresher.Freshness freshness = CollectionStatsRefresher.ensureFresh(force);

        PostureSource source = PostureSource.forAccount();
        PostureData data = timed("load posture from " + source.getClass().getSimpleName(), () -> source.load(startTimestamp, endTimestamp));
        PostureCounts counts = data.getCounts();
        long shadowApis = timed("count shadow apis", this::countShadowApis);

        return new PostureSnapshotResponse(data.getStart(), data.getEnd(), data.isStartUnavailable(),
                new ActionItemCounts(counts.get(PostureMetric.SENSITIVE_UNAUTHENTICATED), counts.get(PostureMetric.HIGH_RISK_THIRD_PARTY),
                        shadowApis, counts.get(PostureMetric.UNAUTHENTICATED), counts.get(PostureMetric.NOT_TESTED),
                        counts.get(PostureMetric.ONLY_ONCE_TESTED), counts.get(PostureMetric.HIGH_RISK), counts.get(PostureMetric.SENSITIVE)),
                new Missing(data.getNetMissing(), counts.get(PostureMetric.AUTH_NOT_CALCULATED),
                        counts.get(PostureMetric.ACCESS_NOT_CALCULATED), counts.get(PostureMetric.API_TYPE_MISSING)),
                freshness.getRefreshedAt(), data.isPending(),
                timed("newest host collections", () -> new CollectionsPageService().fetchNewestHostnames(NEW_DOMAINS), List::size));
    }

    private long countShadowApis() {
        ApiCollection shadow = ApiCollectionsDao.instance.findByName(AKTO_DISCOVERED_APIS_COLLECTION);
        return shadow == null ? 0 : ApiInfoDao.instance.count(Filters.eq(ApiInfo.ID_API_COLLECTION_ID, shadow.getId()));
    }
}
