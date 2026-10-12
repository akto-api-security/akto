package com.akto.service.collections;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiCollectionStatsMetaDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionStats;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.ApiInfo.ApiType;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.ApiStats;
import com.akto.dto.ApiCollectionStatsMeta;
import com.akto.service.collections.CollectionStatsRefresher.Metric;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.types.CappedSet;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestPosturePartials extends MongoBasedTest {

    // totalTestedCount is only ever $inc'd in mongo, it is not a field of ApiInfo
    private final java.util.Set<String> testedOnce = new HashSet<>();
    // the urls that get a sensitive single_type_info row: sensitivity is not stored on api_info
    private final java.util.Set<String> sensitiveUrls = new HashSet<>();

    @Before
    public void reset() {
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollectionStatsDao.instance.getMCollection().drop();
        ApiCollectionStatsMetaDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();
        // the one the sensitive lists are served in order by (ApiInfoDao.createIndicesIfAbsent makes it in an account)
        ApiInfoDao.instance.getMCollection().createIndex(Indexes.ascending(ApiInfo.ID_API_COLLECTION_ID, ApiInfo.ID_URL));
    }

    private ApiCollection collection(int id, String host) {
        return new ApiCollection(id, "c" + id, 100 + id, new HashSet<>(), host, 0, false, true);
    }

    private ApiInfo api(int collectionId, String url) {
        ApiInfo info = new ApiInfo(new ApiInfoKey(collectionId, url, Method.GET));
        info.setCollectionIds(Collections.singletonList(collectionId));
        info.setDiscoveredTimestamp(Context.now() - 1000);
        return info;
    }

    private void withAuth(ApiInfo info, String... authTypes) {
        info.setAllAuthTypesFound(new HashSet<>(Collections.singletonList(new HashSet<>(Arrays.asList(authTypes)))));
    }

    /** A spread of documents, one auth type per document so that "the first auth type" is unambiguous. */
    private List<ApiInfo> seed() {
        List<ApiInfo> docs = new ArrayList<>();
        int i = 0;
        String[] auths = {AuthType.UNAUTHENTICATED, AuthType.JWT, AuthType.BASIC, "my-custom-auth", AuthType.BEARER};
        ApiAccessType[] access = {ApiAccessType.PUBLIC, ApiAccessType.PRIVATE, ApiAccessType.THIRD_PARTY, ApiAccessType.PARTNER};
        ApiType[] types = {ApiType.REST, ApiType.GRAPHQL, ApiType.GRPC};
        float[] risks = {0f, 1.4f, 2.5f, 3.2f, 4f, 4.5f, 5f};
        float[] severities = {0f, 1f, 5f, 10f, 99f, 100f, 1000f};
        for (int c = 1; c <= 3; c++) {
            for (int n = 0; n < 14; n++, i++) {
                ApiInfo info = api(c, "/u" + i);
                withAuth(info, auths[i % auths.length]);
                info.setApiAccessTypes(new HashSet<>(Collections.singletonList(access[i % access.length])));
                info.setApiType(types[i % types.length]);
                info.setRiskScore(risks[i % risks.length]);
                info.setSeverityScore(severities[i % severities.length]);
                if (i % 3 == 0) info.setLastTested(Context.now() - 100);
                if (i % 6 == 0) testedOnce.add(info.getId().getUrl());
                if (i % 4 == 0) sensitiveUrls.add(info.getId().getUrl());
                info.setDiscoveredTimestamp(i % 2 == 0 ? Context.now() - 100_000 : Context.now() - 1_000);
                docs.add(info);
            }
        }
        // documents with nothing computed yet
        ApiInfo bare = api(1, "/bare");
        bare.setAllAuthTypesFound(new HashSet<>());
        bare.setApiAccessTypes(new HashSet<>());
        bare.setApiType(null);
        docs.add(bare);
        return docs;
    }

    private void prepare(List<ApiInfo> docs) {
        ApiCollectionsDao.instance.insertMany(Arrays.asList(collection(1, "a.com"), collection(2, "b.com"), collection(3, "c.com")));
        new CollectionAttrsSync().syncAll();
        ApiInfoDao.instance.insertMany(docs);
        for (ApiInfo doc : docs) {
            if (testedOnce.contains(doc.getId().getUrl())) {
                ApiInfoDao.instance.updateOne(ApiInfoDao.getFilter(doc.getId()), Updates.set(ApiInfo.TOTAL_TESTED_COUNT, 1));
            }
        }
        // a request row for some of the sensitive apis, a response row for the others
        int n = 0;
        List<SingleTypeInfo> rows = new ArrayList<>();
        for (ApiInfo doc : docs) {
            if (!sensitiveUrls.contains(doc.getId().getUrl())) continue;
            rows.add(sensitiveRow(doc.getId(), n++ % 2 == 0 ? -1 : 200));
        }
        // and rows that do not make an api sensitive: another type, and a sensitive type of an api that has no api_info document
        rows.add(sensitiveRow(new ApiInfoKey(1, "/u1", Method.GET), 200, SingleTypeInfo.GENERIC));
        rows.add(sensitiveRow(new ApiInfoKey(1, "/no-api-info", Method.GET), 200, SingleTypeInfo.EMAIL));
        SingleTypeInfoDao.instance.insertMany(rows);
        // what the refresh needs from the lastSeen / endpointsCount metrics
        ApiCollectionStatsDao.instance.getMCollection().updateMany(Filters.empty(), Updates.combine(
                Updates.set(ApiCollectionStats.ENDPOINTS_COUNT, 20), Updates.set(ApiCollectionStats.LAST_SEEN, Context.now())));
    }

    private SingleTypeInfo sensitiveRow(ApiInfoKey key, int responseCode) {
        return sensitiveRow(key, responseCode, SingleTypeInfo.EMAIL);
    }

    private SingleTypeInfo sensitiveRow(ApiInfoKey key, int responseCode, SingleTypeInfo.SubType subType) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(key.getUrl(), key.getMethod().name(), responseCode, false, "email",
                subType, key.getApiCollectionId(), false);
        return new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, Context.now(), 0,
                new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0, 10);
    }

    /** What the home dashboard counts per collection comes from the stats rows: the sensitive refresh, then the recount. */
    private void refreshAll() {
        new BaseCollectionStatsSource().refreshSensitive();
        new PosturePartialsRefresh().refresh();
    }

    private int sum(String field) {
        int total = 0;
        for (ApiCollectionStats stats : ApiCollectionStatsDao.instance.getMCollection().find().into(new ArrayList<>())) {
            total += PostureMetric.SENSITIVE.equals(field) ? stats.getSensitiveApisCount() : stats.getPosture().getOrDefault(field, 0L);
        }
        return total;
    }

    @Test
    public void countsMatchWhatApiInfoAddStatsCounts() {
        List<ApiInfo> docs = seed();
        prepare(docs);

        refreshAll();

        ApiStats expected = new ApiStats(0);
        int[] totalTested = {0};
        for (ApiInfo doc : docs) {
            doc.addStats(expected);
            expected.addSeverityCount(doc.findSeverity());
            if (doc.getLastTested() > Context.now() - 30 * 24 * 60 * 60) totalTested[0]++;
        }

        assertEquals(docs.size(), sum(PostureMetric.TOTAL));
        for (int bucket = 0; bucket <= PostureMetric.MAX_RISK_BUCKET; bucket++) {
            assertEquals("risk bucket " + bucket, expected.getRiskScoreMap().getOrDefault(bucket, 0).intValue(),
                    sum(PostureMetric.riskField(bucket)));
        }
        for (ApiAccessType type : ApiAccessType.values()) {
            assertEquals(type.name(), expected.getAccessTypeMap().getOrDefault(type, 0).intValue(),
                    sum(PostureMetric.ACCESS_PREFIX + type.name()));
        }
        for (ApiType type : ApiType.values()) {
            assertEquals(type.name(), expected.getApiTypeMap().getOrDefault(type, 0).intValue(),
                    sum(PostureMetric.API_TYPE_PREFIX + type.name()));
        }
        for (Map.Entry<String, Integer> auth : expected.getAuthTypeMap().entrySet()) {
            String field = AuthType.CUSTOM.equals(auth.getKey()) ? PostureMetric.CUSTOM_AUTH : PostureMetric.AUTH_PREFIX + auth.getKey();
            assertEquals(auth.getKey(), auth.getValue().intValue(), sum(field));
        }
        for (String severity : new String[]{"CRITICAL", "HIGH", "MEDIUM", "LOW"}) {
            assertEquals(severity, expected.getCriticalMap().getOrDefault(severity, 0).intValue(),
                    sum(PostureMetric.SEVERITY_PREFIX + severity));
        }
        assertEquals(totalTested[0], sum(PostureMetric.TESTED_IN_LOOKBACK));
    }

    @Test
    public void actionItemCountsMatchTheirFilters() {
        List<ApiInfo> docs = seed();
        prepare(docs);

        refreshAll();

        int notTested = 0, onlyOnce = 0, highRisk = 0, highRiskThirdParty = 0, unauthenticated = 0, sensitiveUnauth = 0, sensitiveApis = 0;
        int noAuth = 0, noAccess = 0, noType = 0;
        for (ApiInfo doc : docs) {
            if (doc.getLastTested() == 0) notTested++;
            else if (testedOnce.contains(doc.getId().getUrl())) onlyOnce++;
            if (doc.getRiskScore() > 3) highRisk++;
            if (doc.getRiskScore() >= 4 && doc.getApiAccessTypes().contains(ApiAccessType.THIRD_PARTY)) highRiskThirdParty++;
            boolean unauth = doc.getAllAuthTypesFound().stream().anyMatch(s -> s.contains(AuthType.UNAUTHENTICATED));
            if (unauth) unauthenticated++;
            boolean sensitive = sensitiveUrls.contains(doc.getId().getUrl());
            if (sensitive) sensitiveApis++;
            if (sensitive && doc.getAllAuthTypesFound().contains(Collections.singleton(AuthType.UNAUTHENTICATED))) sensitiveUnauth++;
            if (doc.getAllAuthTypesFound().isEmpty()) noAuth++;
            if (doc.getApiAccessTypes().isEmpty()) noAccess++;
            if (doc.getApiType() == null) noType++;
        }

        assertEquals(notTested, sum(PostureMetric.NOT_TESTED));
        assertEquals(onlyOnce, sum(PostureMetric.ONLY_ONCE_TESTED));
        assertEquals(highRisk, sum(PostureMetric.HIGH_RISK));
        assertEquals(highRiskThirdParty, sum(PostureMetric.HIGH_RISK_THIRD_PARTY));
        assertEquals(unauthenticated, sum(PostureMetric.UNAUTHENTICATED));
        assertEquals(sensitiveUnauth, sum(PostureMetric.SENSITIVE_UNAUTHENTICATED));
        // single_type_info knows one more sensitive api than api_info does: the count is single_type_info's, as it always was
        assertEquals(sensitiveApis + 1, sum(PostureMetric.SENSITIVE));
        assertEquals(noAuth, sum(PostureMetric.AUTH_NOT_CALCULATED));
        assertEquals(noAccess, sum(PostureMetric.ACCESS_NOT_CALCULATED));
        assertEquals(noType, sum(PostureMetric.API_TYPE_MISSING));
    }

    @Test
    public void aCollectionWithoutApiInfoIsCountedAsZeroAndNotPickedAgain() {
        ApiCollectionsDao.instance.insertOne(collection(1, "empty.com"));
        new CollectionAttrsSync().syncAll();

        PosturePartialsRefresh refresh = new PosturePartialsRefresh();
        assertEquals(1, refresh.pickBatch(Context.now()).size());
        refresh.refresh();

        ApiCollectionStats stats = ApiCollectionStatsDao.instance.getMCollection().find(Filters.eq(ApiCollectionStats.ID, 1)).first();
        assertEquals(0, stats.getPosture().get(PostureMetric.TOTAL).longValue());
        assertTrue(stats.getPosture().get(ApiCollectionStats.POSTURE_AT_KEY) > 0);
        assertTrue(refresh.pickBatch(Context.now()).isEmpty());
    }

    @Test
    public void onlyCollectionsWithNewTrafficOrNeverCountedAreRecounted() {
        ApiCollectionsDao.instance.insertMany(Arrays.asList(collection(1, "a.com"), collection(2, "b.com")));
        new CollectionAttrsSync().syncAll();
        ApiInfoDao.instance.insertOne(api(1, "/a"));
        ApiInfoDao.instance.insertOne(api(2, "/b"));
        int old = Context.now() - 1000;
        ApiCollectionStatsDao.instance.getMCollection().updateMany(Filters.empty(), Updates.combine(
                Updates.set(ApiCollectionStats.LAST_SEEN, old), Updates.set(ApiCollectionStats.ENDPOINTS_COUNT, 1)));

        PosturePartialsRefresh refresh = new PosturePartialsRefresh();
        refresh.refresh();
        assertTrue("nothing changed since: nothing to recount", refresh.pickBatch(Context.now()).isEmpty());

        ApiCollectionStatsDao.instance.getMCollection().updateOne(Filters.eq(ApiCollectionStats.ID, 2),
                Updates.set(ApiCollectionStats.LAST_SEEN, Context.now() + 5));
        List<ApiCollectionStats> batch = refresh.pickBatch(Context.now());
        assertEquals(1, batch.size());
        assertEquals(2, batch.get(0).getId());
    }

    @Test
    public void aBatchStopsAtTheDocumentBudget() {
        List<ApiCollection> collections = new ArrayList<>();
        for (int id = 1; id <= 5; id++) collections.add(collection(id, id + ".com"));
        ApiCollectionsDao.instance.insertMany(collections);
        new CollectionAttrsSync().syncAll();
        ApiCollectionStatsDao.instance.getMCollection().updateMany(Filters.empty(),
                Updates.set(ApiCollectionStats.ENDPOINTS_COUNT, PosturePartialsRefresh.DOC_BUDGET_PER_BATCH / 2 - 1));

        // two collections fit the budget, the third would not
        assertEquals(2, new PosturePartialsRefresh().pickBatch(Context.now()).size());

        ApiCollectionStatsDao.instance.getMCollection().updateMany(Filters.empty(),
                Updates.set(ApiCollectionStats.ENDPOINTS_COUNT, PosturePartialsRefresh.DOC_BUDGET_PER_BATCH * 3));
        assertEquals("one collection larger than the budget is still taken", 1, new PosturePartialsRefresh().pickBatch(Context.now()).size());
        assertFalse(new PosturePartialsRefresh().pickBatch(Context.now()).isEmpty());
    }

    /** So a snapshot request does not start background refreshes that would overwrite what the test set up. */
    private void markAllMetricsFresh() {
        for (Metric metric : Metric.values()) {
            ApiCollectionStatsMeta meta = new ApiCollectionStatsMeta();
            meta.setId(metric.name());
            meta.setRefreshedAt(Context.now());
            ApiCollectionStatsMetaDao.instance.getMCollection().replaceOne(Filters.eq("_id", metric.name()), meta,
                    new com.mongodb.client.model.ReplaceOptions().upsert(true));
        }
    }

    private static <K> Map<K, Integer> withoutNullKey(Map<K, Integer> map) {
        Map<K, Integer> copy = new java.util.HashMap<>(map);
        copy.remove(null);
        return copy;
    }

    private void assertSameStats(String what, ApiStats expected, ApiStats actual, boolean includingTested) {
        assertEquals(what + " total", expected.getTotalAPIs(), actual.getTotalAPIs());
        assertEquals(what + " risk", expected.getTotalRiskScore(), actual.getTotalRiskScore(), 0.01f);
        assertEquals(what + " risk buckets", expected.getRiskScoreMap(), actual.getRiskScoreMap());
        // the old scan counted an api with no access / api type under a null key; those are the "not calculated" counts now
        assertEquals(what + " access", withoutNullKey(expected.getAccessTypeMap()), actual.getAccessTypeMap());
        assertEquals(what + " api types", withoutNullKey(expected.getApiTypeMap()), actual.getApiTypeMap());
        assertEquals(what + " auth", expected.getAuthTypeMap(), actual.getAuthTypeMap());
        // the old scan only worked these two out for the end of the range
        if (includingTested) {
            assertEquals(what + " tested", expected.getApisTestedInLookBackPeriod(), actual.getApisTestedInLookBackPeriod());
            assertEquals(what + " in scope", expected.getTotalInScopeForTestingApis(), actual.getTotalInScopeForTestingApis());
        }
    }

    @Test
    public void snapshotMatchesWhatTheOldFullScanComputed() {
        List<ApiInfo> docs = seed();
        prepare(docs);
        refreshAll();
        markAllMetricsFresh();
        int start = Context.now() - 50_000;
        int end = Context.now();

        PostureSnapshotResponse snapshot = new PostureSnapshotService().fetch(start, end, false);

        // what the old full scan computed: every document through ApiInfo.addStats, split by when it was discovered
        ApiStats oldEnd = new ApiStats(end);
        ApiStats oldStart = new ApiStats(start);
        for (ApiInfo doc : docs) {
            doc.addStats(oldEnd);
            oldEnd.setTotalAPIs(oldEnd.getTotalAPIs() + 1);
            oldEnd.setTotalRiskScore(oldEnd.getTotalRiskScore() + doc.getRiskScore());
            if (doc.getDiscoveredTimestamp() <= start) {
                doc.addStats(oldStart);
                oldStart.setTotalAPIs(oldStart.getTotalAPIs() + 1);
                oldStart.setTotalRiskScore(oldStart.getTotalRiskScore() + doc.getRiskScore());
            }
            if (doc.getLastTested() > Context.now() - 30 * 24 * 60 * 60) oldEnd.setApisTestedInLookBackPeriod(oldEnd.getApisTestedInLookBackPeriod() + 1);
            oldEnd.setTotalInScopeForTestingApis(oldEnd.getTotalInScopeForTestingApis() + 1);
        }
        assertSameStats("end", oldEnd, snapshot.getApiStatsEnd(), true);
        assertSameStats("start", oldStart, snapshot.getApiStatsStart(), false);
        assertFalse(snapshot.isStartUnavailable());
        assertFalse(snapshot.isStatsPending());
        // the new domains tile comes with the snapshot: the host collections, newest first
        assertEquals(3, snapshot.getNewDomains().size());
        assertEquals(20, snapshot.getNewDomains().get(0).getTotalApis());
        // 20 endpoints were set on each of the 3 collections, api_info has fewer
        assertEquals(60 - docs.size(), snapshot.getMissing().getNetMissing());
    }

    @Test
    public void snapshotIsPendingUntilEveryCollectionIsCounted() {
        prepare(seed());
        markAllMetricsFresh();

        assertTrue(new PostureSnapshotService().fetch(Context.now() - 50_000, Context.now(), false).isStatsPending());

        refreshAll();
        assertFalse(new PostureSnapshotService().fetch(Context.now() - 50_000, Context.now(), false).isStatsPending());
    }

    @Test
    public void aRangeWithTooManyNewApisReportsTheStartAsUnavailable() {
        prepare(seed());
        refreshAll();
        markAllMetricsFresh();

        PostureData data = new PartialsPostureSource() {
            @Override
            int windowDocCap() {
                return 3;
            }
        }.load(Context.now() - 50_000, Context.now());

        assertTrue(data.isStartUnavailable());
        assertEquals(0, data.getStart().getTotalAPIs());
        assertTrue("the end does not depend on the window", data.getEnd().getTotalAPIs() > 0);
    }

    private PostureActionItemsResponse allOf(PostureActionItemsService.Type type, int pageSize) {
        List<PostureActionItemsResponse.Row> rows = new ArrayList<>();
        long total = 0;
        for (int skip = 0; ; skip += pageSize) {
            PostureActionItemsResponse page = new PostureActionItemsService().fetch(type, skip, pageSize);
            total = page.getTotal();
            rows.addAll(page.getRows());
            if (page.getRows().size() < pageSize) break;
        }
        return new PostureActionItemsResponse(rows, new ArrayList<>(), total);
    }

    @Test
    public void everyApiLevelItemListsExactlyTheApisItsCountCounts() {
        prepare(seed());
        refreshAll();

        for (PostureActionItemsService.Type type : PostureActionItemsService.Type.values()) {
            // the third party item is about recently seen apis only, the seeded ones were never seen
            if (type.getMetric() == null || type == PostureActionItemsService.Type.THIRD_PARTY) continue;
            PostureActionItemsResponse all = allOf(type, 7);
            // the sensitive api that has no api_info document is counted (it is single_type_info's count) but cannot be listed
            long notListable = type == PostureActionItemsService.Type.SENSITIVE ? 1 : 0;
            assertEquals(type + " rows", all.getTotal() - notListable, all.getRows().size());
            assertEquals(type + " no row twice", all.getRows().size(),
                    all.getRows().stream().map(r -> r.getApiCollectionId() + r.getUrl()).distinct().count());
        }
        List<PostureActionItemsResponse.Row> sensitive = allOf(PostureActionItemsService.Type.SENSITIVE, 3).getRows();
        assertEquals("sensitive pages come in key order", sensitive.stream()
                .sorted(java.util.Comparator.comparingInt(PostureActionItemsResponse.Row::getApiCollectionId)
                        .thenComparing(PostureActionItemsResponse.Row::getUrl)).collect(java.util.stream.Collectors.toList()), sensitive);
        assertTrue(allOf(PostureActionItemsService.Type.SENSITIVE_UNAUTHENTICATED, 2).getRows().stream()
                .allMatch(r -> sensitiveUrls.contains(r.getUrl()) && r.getAllAuthTypesFound().contains(Collections.singleton(AuthType.UNAUTHENTICATED))));
        assertTrue(allOf(PostureActionItemsService.Type.HIGH_RISK, 5).getRows().stream().allMatch(r -> r.getRiskScore() > 3));
        assertEquals(sum(PostureMetric.HIGH_RISK), allOf(PostureActionItemsService.Type.HIGH_RISK, 5).getTotal());
    }

    @Test
    public void aCollectionIsRecountedWhenItsSensitiveApisChange() {
        prepare(seed());
        refreshAll();
        PosturePartialsRefresh refresh = new PosturePartialsRefresh();
        assertTrue(refresh.pickBatch(Context.now()).isEmpty());

        SingleTypeInfoDao.instance.insertOne(sensitiveRow(new ApiInfoKey(2, "/u15", Method.GET), 200));
        new BaseCollectionStatsSource().refreshSensitive();

        List<ApiCollectionStats> due = refresh.pickBatch(Context.now());
        assertEquals(1, due.size());
        assertEquals(2, due.get(0).getId());
    }

    @Test
    public void aPageIsNeverLargerThanTheMaximum() {
        prepare(seed());
        refreshAll();

        PostureActionItemsResponse page = new PostureActionItemsService().fetch(PostureActionItemsService.Type.HIGH_RISK, 0, 100_000);

        assertTrue(page.getRows().size() <= PostureActionItemsService.MAX_PAGE_SIZE);
    }
}
