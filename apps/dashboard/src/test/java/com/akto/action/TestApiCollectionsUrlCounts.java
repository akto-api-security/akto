package com.akto.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiCollectionStatsMetaDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionStatsMeta;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.service.collections.CollectionStatsRefresher;
import com.akto.usage.UsageMetricCalculator;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.lang.reflect.Field;
import java.util.*;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.Test;

// The collections list counts endpoints live; where the stats refresh already holds the count it reads it instead.
public class TestApiCollectionsUrlCounts extends MongoBasedTest {

    private ApiCollectionsAction action;
    private Bson liveFilter;

    private static List<Document> hostHeaderRows(int collectionId, int from, int to, List<Integer> collectionIds) {
        List<Document> rows = new ArrayList<>();
        for (int i = from; i < to; i++) {
            rows.add(new Document(SingleTypeInfo._API_COLLECTION_ID, collectionId).append("url", "/c" + collectionId + "/" + i).append("method", "GET")
                .append(SingleTypeInfo._RESPONSE_CODE, -1).append(SingleTypeInfo._IS_HEADER, true).append(SingleTypeInfo._PARAM, "host")
                .append(SingleTypeInfo.SUB_TYPE, "GENERIC").append(SingleTypeInfo._COLLECTION_IDS, collectionIds));
        }
        return rows;
    }

    private static void insertRawRows(List<Document> rows) {
        try (MongoClient raw = MongoClients.create("mongodb://localhost:27019")) {
            raw.getDatabase(SingleTypeInfoDao.instance.getMCollection().getNamespace().getDatabaseName())
                .getCollection(SingleTypeInfoDao.instance.getMCollection().getNamespace().getCollectionName()).insertMany(rows);
        }
    }

    // 1 hostname with traffic, 2 hostname without, 3 deactivated, 4 group, 5 plain collection with a urls list
    private void seedCollections() throws Exception {
        ApiCollectionsDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiCollectionStatsDao.instance.getMCollection().drop();
        ApiCollectionStatsMetaDao.instance.getMCollection().drop();
        Context.userId.set(null);

        ApiCollection deactivated = new ApiCollection(3, "off", 1, null, "off.com", 0, false, true);
        deactivated.setDeactivated(true);
        ApiCollection group = new ApiCollection(4, "group", 1, null, null, 0, false, true);
        group.setType(ApiCollection.Type.API_GROUP);
        ApiCollectionsDao.instance.insertMany(Arrays.asList(
            new ApiCollection(1, "traffic", 1, null, "traffic.com", 0, false, true),
            new ApiCollection(2, "none", 1, null, "none.com", 0, false, true),
            deactivated, group,
            new ApiCollection(5, "urls", 1, new HashSet<>(Arrays.asList("a", "b", "c")), null, 0, false, true)));

        insertRawRows(hostHeaderRows(1, 0, 30, Arrays.asList(1, 4)));
        insertRawRows(hostHeaderRows(1, 30, 50, Arrays.asList(1)));
        insertRawRows(hostHeaderRows(3, 0, 6, Arrays.asList(3, 4)));   // deactivated, counted nowhere
        insertRawRows(hostHeaderRows(5, 0, 7, Arrays.asList(5)));

        // the deactivated ids are cached per account for a while, and the action reads them when it is created
        Field deactivatedFetchedAt = UsageMetricCalculator.class.getDeclaredField("lastDeactivatedFetchedMap");
        deactivatedFetchedAt.setAccessible(true);
        ((Map<?, ?>) deactivatedFetchedAt.get(null)).clear();
        action = new ApiCollectionsAction();
        liveFilter = Filters.nin(SingleTypeInfo._API_COLLECTION_ID, UsageMetricCalculator.getDeactivated());
    }

    private static void waitForCountsRefreshedAfter(int timestamp) throws Exception {
        for (int i = 0; i < 300; i++) {
            ApiCollectionStatsMeta meta = ApiCollectionStatsMetaDao.instance.findOne(Filters.eq(ApiCollectionStatsMeta.ID, "ENDPOINTS_COUNT"));
            if (meta != null && meta.getRefreshedAt() >= timestamp) return;
            Thread.sleep(100);
        }
        throw new AssertionError("the endpoint counts were not refreshed");
    }

    private static void setCountsRefreshedAt(int timestamp) {
        ApiCollectionStatsMetaDao.instance.getMCollection().updateOne(Filters.eq(ApiCollectionStatsMeta.ID, "ENDPOINTS_COUNT"),
            Updates.set(ApiCollectionStatsMeta.REFRESHED_AT, timestamp));
    }

    private static List<ApiCollection> listCollections() {
        return ApiCollectionsDao.instance.findAll(Filters.empty(), ApiCollectionsDao.LIST_PROJECTION);
    }

    private static Map<Integer, Integer> urlsCountById(List<ApiCollection> collections) {
        Map<Integer, Integer> urlsCounts = new TreeMap<>();
        collections.forEach(c -> urlsCounts.put(c.getId(), c.getUrlsCount()));
        return urlsCounts;
    }

    private Map<Integer, Integer> urlsCountsLive() {
        return urlsCountById(action.fillApiCollectionsUrlCount(listCollections(), liveFilter));
    }

    private Map<Integer, Integer> urlsCountsWithStats() {
        List<ApiCollection> collections = listCollections();
        return urlsCountById(action.fillApiCollectionsUrlCount(collections, liveFilter, action.endpointCountsFromStats(collections)));
    }

    private void buildStats() throws Exception {
        int before = Context.now();
        CollectionStatsRefresher.ensureFresh(true);
        waitForCountsRefreshedAfter(before);
    }

    @Test
    public void statsGiveTheCountsOfTheLiveList() throws Exception {
        seedCollections();
        buildStats();
        List<ApiCollection> collections = listCollections();

        Map<Integer, Integer> countsFromStats = action.endpointCountsFromStats(collections);

        assertEquals(urlsCountsLive(), urlsCountsWithStats());
        assertEquals(50, urlsCountsWithStats().get(1).intValue());
        // the big ones come from the stats; deactivated, uncounted and fallback-ambiguous ones are counted live
        assertTrue(countsFromStats.containsKey(1) && countsFromStats.containsKey(4));
        assertFalse(countsFromStats.containsKey(2) || countsFromStats.containsKey(3) || countsFromStats.containsKey(5));
    }

    @Test
    public void trafficAfterTheRefreshShowsOnceTheNextRefreshRan() throws Exception {
        seedCollections();
        buildStats();
        insertRawRows(hostHeaderRows(1, 50, 60, Arrays.asList(1)));

        assertEquals(50, urlsCountsWithStats().get(1).intValue());

        // the counts are older than their ttl: asking for them starts a refresh without waiting for it
        setCountsRefreshedAt(Context.now() - 200);
        int before = Context.now();
        urlsCountsWithStats();
        waitForCountsRefreshedAfter(before);

        assertEquals(60, urlsCountsWithStats().get(1).intValue());
        assertEquals(urlsCountsLive(), urlsCountsWithStats());
    }

    @Test
    public void statsThatWereNeverBuiltAreNotReadOrStarted() throws Exception {
        seedCollections();

        assertTrue(action.endpointCountsFromStats(listCollections()).isEmpty());
        assertEquals(urlsCountsLive(), urlsCountsWithStats());
        assertEquals(0, ApiCollectionStatsMetaDao.instance.findAll(Filters.empty()).size());
    }

    @Test
    public void statsFromAStoppedRefreshAreNotUsed() throws Exception {
        seedCollections();
        buildStats();
        setCountsRefreshedAt(Context.now() - 700);

        assertTrue(action.endpointCountsFromStats(listCollections()).isEmpty());
        assertEquals(urlsCountsLive(), urlsCountsWithStats());
    }
}
