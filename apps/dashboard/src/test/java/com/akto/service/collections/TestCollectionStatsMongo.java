package com.akto.service.collections;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiCollectionStatsMetaDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.billing.UningestedApiOverageDao;
import com.akto.dao.context.Context;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.types.CappedSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.akto.service.collections.CollectionStatsRefresher.Metric;
import com.akto.dto.ApiCollectionStatsMeta;
import com.akto.dto.traffic.CollectionTags;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestCollectionStatsMongo extends MongoBasedTest {

    @Before
    public void reset() {
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollectionStatsDao.instance.getMCollection().drop();
        ApiCollectionStatsMetaDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();
        UningestedApiOverageDao.instance.getMCollection().drop();
    }

    private ApiCollectionStats stat(int id) {
        return ApiCollectionStatsDao.instance.getMCollection().find(Filters.eq(ApiCollectionStats.ID, id)).first();
    }

    private ApiCollection collection(int id, String name, String hostName, boolean deactivated) {
        ApiCollection c = new ApiCollection(id, name, 100 + id, new HashSet<>(Arrays.asList("a", "b")), hostName, 0, false, true);
        c.setDeactivated(deactivated);
        return c;
    }

    @Test
    public void attrsSyncDerivesTabDisplayNameAndFallbackCount() {
        ApiCollectionsDao.instance.insertMany(Arrays.asList(
                collection(1, "api", "a.com", false),
                collection(2, "custom", null, false),
                collection(3, "gone", "b.com", true)));
        ApiCollection group = collection(4, "grp", null, false);
        group.setType(ApiCollection.Type.API_GROUP);
        ApiCollectionsDao.instance.insertOne(group);

        new CollectionAttrsSync().syncAll();

        assertEquals("HOSTNAME", stat(1).getTab());
        assertEquals("a.com - api", stat(1).getDisplayName());
        assertEquals("CUSTOM", stat(2).getTab());
        assertEquals("custom", stat(2).getDisplayName());
        assertEquals("DEACTIVATED", stat(3).getTab());
        assertEquals("GROUP", stat(4).getTab());
        assertEquals(2, stat(1).getUrlsFallbackCount());
    }

    @Test
    public void syncIdsRemovesRowsOfDeletedCollections() {
        ApiCollectionsDao.instance.insertMany(Arrays.asList(collection(1, "a", null, false), collection(2, "b", null, false)));
        new CollectionAttrsSync().syncAll();
        ApiCollectionsDao.instance.getMCollection().deleteOne(Filters.eq("_id", 2));

        new CollectionAttrsSync().syncIds(Arrays.asList(1, 2));

        assertTrue(stat(1) != null);
        assertNull(stat(2));
    }

    @Test
    public void claimIsExclusiveUntilReleased() {
        int now = 1_000_000;
        assertTrue(CollectionStatsRefresher.tryClaim(Metric.RISK_SCORE, now));
        assertFalse(CollectionStatsRefresher.tryClaim(Metric.RISK_SCORE, now));
        assertFalse(CollectionStatsRefresher.tryClaim(Metric.RISK_SCORE, now + 10));
        // a holder that never finished is taken over after the lock timeout
        assertTrue(CollectionStatsRefresher.tryClaim(Metric.RISK_SCORE, now + CollectionStatsRefresher.LOCK_TIMEOUT_SECONDS + 1));
        // other metrics are independent
        assertTrue(CollectionStatsRefresher.tryClaim(Metric.LAST_SEEN, now));
    }

    @Test
    public void riskScoreAndLastSeenAreTheMaximumPerCollection() {
        ApiCollectionsDao.instance.insertMany(Arrays.asList(collection(1, "a", "a.com", false), collection(2, "b", "b.com", false)));
        new CollectionAttrsSync().syncAll();

        insertApiInfo(1, "/x", 2f, 50);
        insertApiInfo(1, "/y", 4f, 40);
        insertApiInfo(1, "/z", 3f, 90);

        new ApiInfoMetricsRefresh().refreshRiskScore();
        new ApiInfoMetricsRefresh().refreshLastSeen();

        assertEquals(4.0, stat(1).getRiskScore(), 0.0001);
        assertEquals(90, stat(1).getLastSeen());
        // a collection without any api_info is reset rather than left unset
        assertEquals(0, (int) stat(2).getRiskScore());
        assertEquals(0, stat(2).getLastSeen());
    }

    private void insertHostHeader(int collectionId, String url) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, "GET", -1, true, "host", SingleTypeInfo.GENERIC, collectionId, false);
        SingleTypeInfo sti = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, Context.now(), 0,
                new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0, 10);
        sti.setCollectionIds(Arrays.asList(collectionId));
        SingleTypeInfoDao.instance.insertOne(sti);
    }

    @Test
    public void endpointCountsFollowTheTrueCountMethodAndItsFallbacks() {
        // host collection: counted from single_type_info. custom collection: its own urls (2) win over any count
        ApiCollectionsDao.instance.insertMany(Arrays.asList(collection(1, "api", "a.com", false), collection(2, "custom", null, false)));
        new CollectionAttrsSync().syncAll();
        insertHostHeader(1, "/a");
        insertHostHeader(1, "/b");
        insertHostHeader(1, "/c");

        CollectionStatsSource.forAccount().refreshEndpointsCount();

        assertEquals(3, stat(1).getEndpointsCount());
        assertEquals(2, stat(2).getEndpointsCount());
    }

    @Test
    public void pageIsSortedFilteredAndPagedInTheDatabase() {
        for (int id = 1; id <= 5; id++) {
            ApiCollectionsDao.instance.insertOne(collection(id, "c" + id, "h" + id + ".com", id == 5));
        }
        new CollectionAttrsSync().syncAll();
        // endpoint counts 10,20,30,40,50 for ids 1..5
        for (int id = 1; id <= 5; id++) {
            ApiCollectionStatsDao.instance.getMCollection().updateOne(Filters.eq(ApiCollectionStats.ID, id),
                    Updates.combine(Updates.set(ApiCollectionStats.ENDPOINTS_COUNT, id * 10),
                            Updates.set(ApiCollectionStats.RISK_SCORE, (double) (6 - id))));
        }

        markAllMetricsFresh();
        CollectionsPageRequest firstPage = new CollectionsPageRequest(0, 2, "urlsCount", -1, "HOSTNAME", null, null, null, false);
        CollectionsPageResponse.Page page = new CollectionsPageService().fetchPage(firstPage);
        // id 5 is deactivated so it is on another tab
        assertEquals(4, page.getTotal());
        assertEquals(Arrays.asList(4, 3), ids(page));
        assertEquals(40, page.getApiCollections().get(0).getUrlsCount());

        CollectionsPageResponse.Page second = new CollectionsPageService().fetchPage(
                new CollectionsPageRequest(2, 2, "urlsCount", -1, "HOSTNAME", null, null, null, false));
        assertEquals(Arrays.asList(2, 1), ids(second));

        CollectionsPageResponse.Page byRisk = new CollectionsPageService().fetchPage(
                new CollectionsPageRequest(0, 10, "riskScore", -1, "ALL", null, null, null, false));
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), ids(byRisk));

        CollectionsPageResponse.Page searched = new CollectionsPageService().fetchPage(
                new CollectionsPageRequest(0, 10, "urlsCount", -1, "ALL", "H3.", null, null, false));
        assertEquals(Arrays.asList(3), ids(searched));
    }

    @Test
    public void pageIsScopedToTheContextSourceLikeOtherCollectionDaos() {
        ApiCollection mcp = collection(1, "mcp", "mcp.com", false);
        mcp.setTagsList(Arrays.asList(new CollectionTags(0, Constants.AKTO_MCP_SERVER_TAG, "true", CollectionTags.TagSource.USER)));
        ApiCollectionsDao.instance.insertMany(Arrays.asList(mcp, collection(2, "plain", "plain.com", false)));
        new CollectionAttrsSync().syncAll();
        markAllMetricsFresh();

        try {
            Context.userId.set(1);
            Context.contextSource.set(CONTEXT_SOURCE.MCP);
            assertEquals(Arrays.asList(1), ids(new CollectionsPageService().fetchPage(allCollectionsRequest())));

            Context.contextSource.set(CONTEXT_SOURCE.API);
            assertEquals(Arrays.asList(2), ids(new CollectionsPageService().fetchPage(allCollectionsRequest())));
        } finally {
            Context.userId.remove();
            Context.contextSource.remove();
        }
        // no user and no context: the background view of every collection
        assertEquals(2, new CollectionsPageService().fetchPage(allCollectionsRequest()).getTotal());
    }

    private CollectionsPageRequest allCollectionsRequest() {
        return new CollectionsPageRequest(0, 10, "urlsCount", -1, "ALL", null, null, null, false);
    }

    private void insertTestedApiInfo(int collectionId, String url) {
        ApiInfo info = new ApiInfo(new ApiInfoKey(collectionId, url, Method.GET));
        info.setLastTested(Context.now());
        info.setCollectionIds(Arrays.asList(collectionId));
        ApiInfoDao.instance.insertOne(info);
    }

    @Test
    public void detailsAreTheCoverageOfTheGivenCollections() {
        insertTestedApiInfo(1, "/a");
        insertTestedApiInfo(1, "/b");
        insertTestedApiInfo(2, "/c");

        CollectionsPageResponse.Details details = new CollectionsPageService().fetchDetails(Arrays.asList(1));

        assertEquals(2, (int) details.getCoverageMap().get(1));
        assertFalse(details.getCoverageMap().containsKey(2));
        assertFalse(details.isCoverageUnavailable());
    }

    @Test
    public void aSlowIssuesQueryIsReportedUnavailableAndTheRowsStillReturn() {
        ApiCollectionsDao.instance.insertOne(collection(1, "a", "a.com", false));
        new CollectionAttrsSync().syncAll();
        markAllMetricsFresh();
        CollectionsPageService service = new CollectionsPageService(1) {
            @Override
            protected Map<Integer, Map<String, Integer>> loadSeverityInfo(List<Integer> ids) {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new java.util.HashMap<>();
            }
        };

        long start = System.currentTimeMillis();
        CollectionsPageResponse.Page page = service.fetchPage(allCollectionsRequest());

        assertTrue("waited " + (System.currentTimeMillis() - start) + "ms", System.currentTimeMillis() - start < 3_000);
        assertTrue(page.isIssuesUnavailable());
        assertEquals(Arrays.asList(1), ids(page));
    }

    @Test
    public void summaryCountsTestedApisOnlyOfCollectionsInTestingScope() {
        ApiCollection outOfScope = collection(2, "b", "b.com", false);
        outOfScope.setIsOutOfTestingScope(true);
        ApiCollectionsDao.instance.insertMany(Arrays.asList(collection(1, "a", "a.com", false), outOfScope));
        new CollectionAttrsSync().syncAll();
        insertTestedApiInfo(1, "/a");
        insertTestedApiInfo(2, "/b");

        new SummaryRefresh().refresh();

        assertEquals(1, CollectionStatsRefresher.loadSummary().getTotalTestedEndpoints());
    }

    @Test
    public void aFailingQueryIsReportedUnavailable() {
        CollectionsPageService service = new CollectionsPageService(2) {
            @Override
            protected Map<Integer, Integer> loadCoverage(List<Integer> ids) {
                throw new IllegalStateException("boom");
            }
        };

        CollectionsPageResponse.Details details = service.fetchDetails(Arrays.asList(1));

        assertTrue(details.isCoverageUnavailable());
    }

    @Test
    public void tabCountsFollowTheSearchAndFilters() {
        ApiCollectionsDao.instance.insertMany(Arrays.asList(
                collection(1, "alpha", "alpha.com", false),
                collection(2, "alpine", null, false),
                collection(3, "beta", "beta.com", false),
                collection(4, "alphagone", "gone.com", true)));
        new CollectionAttrsSync().syncAll();
        markAllMetricsFresh();

        CollectionsPageResponse.TabCounts all = new CollectionsPageService().fetchTabCounts(allCollectionsRequest());
        assertEquals(4, all.getAll());

        CollectionsPageResponse.TabCounts searched = new CollectionsPageService().fetchTabCounts(
                new CollectionsPageRequest(0, 10, null, -1, "HOSTNAME", "alp", null, null, false));
        assertEquals(1, searched.getHostname());
        assertEquals(1, searched.getCustom());
        assertEquals(1, searched.getDeactivated());
        assertEquals(0, searched.getGroups());
        assertEquals(3, searched.getAll());
    }

    @Test
    public void untrackedTabListsCollectionsWithUningestedApis() {
        ApiCollectionsDao.instance.insertMany(Arrays.asList(collection(1, "a", "a.com", false), collection(2, "b", "b.com", false)));
        new CollectionAttrsSync().syncAll();
        UningestedApiOverageDao.instance.insertOne(new UningestedApiOverage(2, "STATIC", "GET /x"));
        UningestedApiOverageDao.instance.insertOne(new UningestedApiOverage(2, "STATIC", "GET /y"));
        UningestedApiOverageDao.instance.insertOne(new UningestedApiOverage(1, "STATIC", "OPTIONS /z"));
        markAllMetricsFresh();

        CollectionsPageResponse.Page page = new CollectionsPageService().fetchPage(
                new CollectionsPageRequest(0, 10, null, -1, "UNTRACKED", null, null, null, false));

        assertEquals(1, page.getTotal());
        assertEquals(2, page.getUntrackedRows().get(0).getId());
        assertEquals(2, page.getUntrackedRows().get(0).getUrlsCount());
        assertEquals(2, page.getUntrackedRows().get(0).getUningestedApiList().size());
    }

    /** So a page request does not start background refreshes that would overwrite what the test set up. */
    private void markAllMetricsFresh() {
        for (Metric metric : Metric.values()) {
            ApiCollectionStatsMeta meta = new ApiCollectionStatsMeta();
            meta.setId(metric.name());
            meta.setRefreshedAt(Context.now());
            ApiCollectionStatsMetaDao.instance.getMCollection().insertOne(meta);
        }
    }

    private List<Integer> ids(CollectionsPageResponse.Page page) {
        List<Integer> ids = new ArrayList<>();
        for (ApiCollection c : page.getApiCollections()) ids.add(c.getId());
        return ids;
    }

    private void insertApiInfo(int collectionId, String url, float riskScore, int lastSeen) {
        ApiInfo info = new ApiInfo(new ApiInfoKey(collectionId, url, Method.GET));
        info.setRiskScore(riskScore);
        info.setLastSeen(lastSeen);
        info.setCollectionIds(Arrays.asList(collectionId));
        ApiInfoDao.instance.insertOne(info);
    }
}
