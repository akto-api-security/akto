package com.akto.dao;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.traffic.SensitiveDataCount;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Runs against a real Mongo: -DtestMongo=mongodb://host:port (default localhost:27029). Named *IT so
 * the default unit test run, which has no Mongo, skips it.
 */
public class SensitiveDataCountDaoIT {

    private static final int ACCOUNT_ID = 1000001;
    private static final int H = 1791536400;
    private static final SensitiveDataCountDao dao = SensitiveDataCountDao.instance;

    @BeforeClass
    public static void connect() {
        DaoInit.init(new ConnectionString(System.getProperty("testMongo", "mongodb://localhost:27029/admini")));
    }

    @Before
    public void reset() {
        Context.accountId.set(ACCOUNT_ID);
        dao.getMCollection().drop();
        requests().drop();
        dao.createIndicesIfAbsent();
    }

    private static MongoCollection<Document> requests() {
        return MCollection.clients[0].getDatabase(String.valueOf(ACCOUNT_ID))
                .getCollection(SensitiveDataCountDao.APPLIED_REQUESTS_COLLECTION);
    }

    private static SensitiveDataCount inc(int collection, String type, int hour, long count) {
        return new SensitiveDataCount(collection, type, hour, count);
    }

    private static long countOf(int collection, String type, int hour) {
        SensitiveDataCount c = dao.findOne(Filters.and(Filters.eq("apiCollectionId", collection),
                Filters.eq("subType", type), Filters.eq("hour", hour)));
        return c == null ? -1 : c.getCount();
    }

    @Test
    public void createsThenIncrementsAndSetsExpiryFromTheBucketHour() {
        dao.bulkInc(Arrays.asList(inc(1, "T", H, 3), inc(1, "T", H, 2), inc(2, "T", H, 1)));
        dao.bulkInc(Collections.singletonList(inc(1, "T", H, 10)));
        assertEquals(15, countOf(1, "T", H));
        assertEquals(1, countOf(2, "T", H));
        assertEquals(2, dao.count(Filters.empty()));

        SensitiveDataCount c = dao.findOne(Filters.eq("apiCollectionId", 1));
        long expectedExpiry = (H + TimeUnit.DAYS.toSeconds(SensitiveDataCountDao.RETENTION_DAYS)) * 1000L;
        assertEquals(expectedExpiry, c.getExpireAt().getTime());
    }

    @Test
    public void ignoresInvalidRows() {
        dao.bulkInc(Arrays.asList(null, inc(1, null, H, 5), inc(1, "T", H, 0), inc(1, "T", H, -4)));
        dao.bulkInc(null);
        dao.bulkInc(Collections.emptyList());
        assertEquals(0, dao.count(Filters.empty()));
    }

    @Test
    public void concurrentFirstWritesToOneBucketNeitherDuplicateNorLose() throws Exception {
        int threads = 16, each = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                Context.accountId.set(ACCOUNT_ID);
                start.await();
                for (int i = 0; i < each; i++) dao.bulkInc(Collections.singletonList(inc(7, "RACE", H, 1)));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(1, dao.count(Filters.eq("subType", "RACE")));
        assertEquals(threads * each, countOf(7, "RACE", H));
    }

    @Test
    public void racingFirstWritesStayExactWithTheOldIndexAlsoPresent() throws Exception {
        // A database that ran the earlier build keeps its (apiCollectionId, subType, hour) unique index.
        dao.getMCollection().createIndex(com.mongodb.client.model.Indexes.ascending("apiCollectionId", "subType", "hour"),
                new com.mongodb.client.model.IndexOptions().name("apiCollectionId_subType_hour").unique(true));
        concurrentFirstWritesToOneBucketNeitherDuplicateNorLose();
    }

    @Test
    public void sameIdempotencyKeyAppliesOnce() {
        List<SensitiveDataCount> batch = Collections.singletonList(inc(1, "T", H, 5));
        dao.bulkInc("key-1", batch);
        dao.bulkInc("key-1", batch);
        dao.bulkInc("key-2", batch);
        dao.bulkInc(null, batch);
        dao.bulkInc("", batch);
        assertEquals(20, countOf(1, "T", H));
        assertEquals(2, requests().countDocuments());
    }

    @Test
    public void sameKeyRacingFromTwoThreadsAppliesOnce() throws Exception {
        for (int round = 0; round < 20; round++) {
            String key = "race-" + round;
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> fs = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                fs.add(pool.submit(() -> {
                    Context.accountId.set(ACCOUNT_ID);
                    start.await();
                    dao.bulkInc(key, Collections.singletonList(inc(9, "K", H, 1)));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : fs) f.get(30, TimeUnit.SECONDS);
            pool.shutdown();
        }
        assertEquals(20, countOf(9, "K", H));
    }

    @Test
    public void failedApplyReleasesTheKeySoARetryCounts() {
        // A bucket whose count is not a number makes $inc fail.
        dao.getMCollection().withDocumentClass(Document.class).insertOne(new Document("apiCollectionId", 3)
                .append("subType", "BROKEN").append("hour", H).append("count", "not-a-number"));
        List<SensitiveDataCount> batch = Collections.singletonList(inc(3, "BROKEN", H, 4));
        try {
            dao.bulkInc("retry-key", batch);
            fail("expected the write to fail");
        } catch (RuntimeException expected) {
        }
        assertEquals(0, requests().countDocuments(Filters.eq("_id", "retry-key")));

        dao.getMCollection().withDocumentClass(Document.class)
                .updateOne(Filters.eq("subType", "BROKEN"), new Document("$set", new Document("count", 0L)));
        dao.bulkInc("retry-key", batch);
        assertEquals(4, countOf(3, "BROKEN", H));
    }

    @Test
    public void windowQueryIncludesFromHourExcludesEarlierAndIsStrictlyAbove() {
        dao.bulkInc(Arrays.asList(
                inc(1, "T", H - 3600, 100),   // before the window
                inc(1, "T", H, 10), inc(1, "T", H + 3600, 10), // 20 in window
                inc(2, "T", H, 21),            // above 20
                inc(3, "T", H, 20),            // exactly 20: not "more than"
                inc(4, "OTHER", H, 500)));     // other type
        Map<Integer, Long> above = dao.countsByCollectionAbove("T", H, 20);
        assertEquals(Collections.singletonMap(2, 21L), above);

        Map<Integer, Long> above19 = dao.countsByCollectionAbove("T", H, 19);
        assertEquals(3, above19.size());
        assertEquals(Long.valueOf(20), above19.get(1));
        assertTrue(dao.countsByCollectionAbove("MISSING", H, 0).isEmpty());
    }

    @Test
    public void windowQueryUsesTheIndex() {
        dao.bulkInc(Arrays.asList(inc(1, "T", H, 1), inc(2, "T", H, 1)));
        Document explain = MCollection.clients[0].getDatabase(String.valueOf(ACCOUNT_ID)).runCommand(new Document("explain",
                new Document("aggregate", SensitiveDataCountDao.COLLECTION_NAME)
                        .append("pipeline", Collections.singletonList(new Document("$match",
                                new Document("subType", "T").append("hour", new Document("$gte", H)))))
                        .append("cursor", new Document())));
        String plan = explain.toJson();
        assertTrue("expected an index scan: " + plan, plan.contains("subType_hour_apiCollectionId"));
        assertFalse("did not expect a collection scan: " + plan, plan.contains("COLLSCAN"));
    }

    @Test
    public void indexesAreDefinedAsExpected() {
        List<Document> counts = dao.getMCollection().listIndexes().into(new ArrayList<>());
        Document unique = counts.stream().filter(i -> "subType_hour_apiCollectionId".equals(i.getString("name"))).findFirst().orElse(null);
        assertTrue(unique != null && Boolean.TRUE.equals(unique.getBoolean("unique")));
        Document ttl = counts.stream().filter(i -> "expireAt_ttl".equals(i.getString("name"))).findFirst().orElse(null);
        assertTrue(ttl != null && ((Number) ttl.get("expireAfterSeconds")).longValue() == 0L);

        Document reqTtl = requests().listIndexes().into(new ArrayList<>()).stream()
                .filter(i -> "createdAt_ttl".equals(i.getString("name"))).findFirst().orElse(null);
        assertTrue(reqTtl != null && ((Number) reqTtl.get("expireAfterSeconds")).longValue() == 86400L);

        // idempotent: a second call neither fails nor adds indexes
        dao.createIndicesIfAbsent();
        assertEquals(counts.size(), dao.getMCollection().listIndexes().into(new ArrayList<>()).size());
    }
}
