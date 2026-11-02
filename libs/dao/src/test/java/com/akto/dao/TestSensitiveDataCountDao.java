package com.akto.dao;

import com.akto.dto.traffic.SensitiveDataCount;
import com.akto.utils.MongoBasedTest;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestSensitiveDataCountDao extends MongoBasedTest {

    private static final int HOUR = 1792425600;

    @Before
    public void clear() {
        SensitiveDataCountDao.instance.getMCollection().drop();
        MCollection.clients[0].getDatabase(SensitiveDataCountDao.instance.getDBName())
                .getCollection(SensitiveDataCountDao.APPLIED_REQUESTS_COLLECTION).drop();
    }

    private static SensitiveDataCount stored(int apiCollectionId, String subType, int hour) {
        return SensitiveDataCountDao.instance.findOne(Filters.and(
                Filters.eq(SensitiveDataCount.API_COLLECTION_ID, apiCollectionId),
                Filters.eq(SensitiveDataCount.SUB_TYPE, subType),
                Filters.eq(SensitiveDataCount.HOUR, hour)));
    }

    @Test
    public void testIncrementsAddUpAcrossWrites() {
        SensitiveDataCountDao.instance.bulkInc(Arrays.asList(
                new SensitiveDataCount(1, "PASSPORT", HOUR, 3),
                new SensitiveDataCount(2, "PASSPORT", HOUR, 1)));
        SensitiveDataCountDao.instance.bulkInc(Collections.singletonList(
                new SensitiveDataCount(1, "PASSPORT", HOUR, 4)));

        SensitiveDataCount bucket = stored(1, "PASSPORT", HOUR);
        assertEquals(7, bucket.getCount());
        assertEquals((HOUR + TimeUnit.DAYS.toSeconds(SensitiveDataCountDao.RETENTION_DAYS)) * 1000L,
                bucket.getExpireAt().getTime());
        assertEquals(1, stored(2, "PASSPORT", HOUR).getCount());
        assertEquals(2, SensitiveDataCountDao.instance.count(new Document()));
    }

    @Test
    public void testSkipsInvalidIncrements() {
        SensitiveDataCountDao.instance.bulkInc((java.util.List<SensitiveDataCount>) null);
        SensitiveDataCountDao.instance.bulkInc(Arrays.asList(
                null,
                new SensitiveDataCount(1, null, HOUR, 2),
                new SensitiveDataCount(1, "PASSPORT", HOUR, 0)));
        assertEquals(0, SensitiveDataCountDao.instance.count(new Document()));
    }

    @Test
    public void testRetryWithSameKeyCountsOnce() {
        SensitiveDataCountDao.instance.bulkInc("key-1", Collections.singletonList(
                new SensitiveDataCount(1, "PASSPORT", HOUR, 5)));
        SensitiveDataCountDao.instance.bulkInc("key-1", Collections.singletonList(
                new SensitiveDataCount(1, "PASSPORT", HOUR, 5)));
        assertEquals(5, stored(1, "PASSPORT", HOUR).getCount());

        SensitiveDataCountDao.instance.bulkInc("key-2", Collections.singletonList(
                new SensitiveDataCount(1, "PASSPORT", HOUR, 5)));
        SensitiveDataCountDao.instance.bulkInc("", Collections.singletonList(
                new SensitiveDataCount(1, "PASSPORT", HOUR, 1)));
        assertEquals(11, stored(1, "PASSPORT", HOUR).getCount());
    }

    @Test
    public void testPartialFailureIsNotAppliedTwice() {
        // $inc fails on this bucket while the rest of the batch applies
        SensitiveDataCountDao.instance.getMCollection().withDocumentClass(Document.class).insertOne(
                new Document(SensitiveDataCount.API_COLLECTION_ID, 2).append(SensitiveDataCount.SUB_TYPE, "PASSPORT")
                        .append(SensitiveDataCount.HOUR, HOUR).append(SensitiveDataCount.COUNT, "not a number"));
        List<SensitiveDataCount> batch = Arrays.asList(
                new SensitiveDataCount(1, "PASSPORT", HOUR, 10),
                new SensitiveDataCount(2, "PASSPORT", HOUR, 10),
                new SensitiveDataCount(3, "PASSPORT", HOUR, 10));

        for (int attempt = 0; attempt < 3; attempt++) {
            SensitiveDataCountDao.instance.bulkInc("key-1", batch);
        }
        assertEquals(10, stored(1, "PASSPORT", HOUR).getCount());
        assertEquals(10, stored(3, "PASSPORT", HOUR).getCount());
    }

    @Test
    public void testConcurrentWritersToTheSameNewBuckets() throws Exception {
        SensitiveDataCountDao.instance.getMCollection().createIndex(
                Indexes.ascending(SensitiveDataCount.SUB_TYPE, SensitiveDataCount.HOUR, SensitiveDataCount.API_COLLECTION_ID),
                new IndexOptions().unique(true));
        int writers = 20, collections = 50;
        List<SensitiveDataCount> batch = new ArrayList<>();
        for (int c = 0; c < collections; c++) {
            batch.add(new SensitiveDataCount(c, "PASSPORT", HOUR, 1));
            batch.add(new SensitiveDataCount(c, "EMAIL", HOUR, 2));
        }

        ExecutorService pool = Executors.newFixedThreadPool(writers);
        List<Future<?>> done = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            String key = "writer-" + w;
            done.add(pool.submit(() -> {
                com.akto.dao.context.Context.accountId.set(ACCOUNT_ID);
                SensitiveDataCountDao.instance.bulkInc(key, batch);
            }));
        }
        for (Future<?> f : done) f.get();
        pool.shutdown();

        assertEquals(2 * collections, SensitiveDataCountDao.instance.count(new Document()));
        for (int c = 0; c < collections; c++) {
            assertEquals(writers, stored(c, "PASSPORT", HOUR).getCount());
            assertEquals(2L * writers, stored(c, "EMAIL", HOUR).getCount());
        }
    }

    @Test
    public void testCountsByCollectionAbove() {
        SensitiveDataCountDao.instance.bulkInc(Arrays.asList(
                new SensitiveDataCount(1, "PASSPORT", HOUR - 7200, 100),   // before the window
                new SensitiveDataCount(1, "PASSPORT", HOUR - 3600, 20),
                new SensitiveDataCount(1, "PASSPORT", HOUR, 15),
                new SensitiveDataCount(2, "PASSPORT", HOUR, 30),            // not above 30
                new SensitiveDataCount(3, "EMAIL", HOUR, 500)));            // another type

        Map<Integer, Long> totals = SensitiveDataCountDao.instance.countsByCollectionAbove("PASSPORT", HOUR - 3600, 30);
        assertEquals(1, totals.size());
        assertEquals(Long.valueOf(35), totals.get(1));
        assertTrue(SensitiveDataCountDao.instance.countsByCollectionAbove("PHONE", HOUR, 0).isEmpty());
    }

    @Test
    public void testHourOf() {
        assertEquals(HOUR, SensitiveDataCount.hourOf(HOUR));
        assertEquals(HOUR, SensitiveDataCount.hourOf(HOUR + 3599));
        assertEquals(HOUR + 3600, SensitiveDataCount.hourOf(HOUR + 3600));
        assertNull(new SensitiveDataCount().getSubType());
    }
}
