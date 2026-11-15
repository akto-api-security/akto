package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiSequences;
import com.akto.dto.SequenceCount;
import com.akto.utils.MongoBasedTest;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestApiSequencesDao extends MongoBasedTest {

    private int bucket;

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        ApiSequencesDao.instance.getMCollection().drop();
        ApiSequencesDao.INDEXED_ACCOUNTS.clear();
        bucket = (Context.now() / 3600) * 3600;
    }

    private static SequenceCount sc(List<String> context, String next, int bucket, long count) {
        return new SequenceCount(1, context, next, bucket, count);
    }

    private static List<String> ctx(String... apis) {
        return new ArrayList<>(Arrays.asList(apis));
    }

    private ApiSequences row(String seqKey) {
        return ApiSequencesDao.instance.findOne(Filters.eq(ApiSequences.SEQ_KEY, seqKey));
    }

    @Test
    public void testCountsAndRatios() {
        ApiSequencesDao.instance.incrementSequences(Arrays.asList(
                sc(ctx(), "a", bucket, 200), sc(ctx(), "b", bucket, 100), sc(ctx(), "c", bucket, 40),
                sc(ctx("a"), "b", bucket, 90), sc(ctx("a", "b"), "c", bucket, 30)));

        ApiSequences ab = row("a|b");
        assertEquals(1, ab.getOrder());
        assertEquals("a", ab.getContextKey());
        assertEquals("b", ab.getNext());
        assertEquals(Arrays.asList("a", "b"), ab.getPaths());
        assertEquals(90, ab.getTransitionCount());
        assertEquals(200, ab.getPrevStateCount());
        assertEquals(100, ab.getLastStateCount());
        assertEquals(0.45, ab.getProbability(), 1e-6);
        assertEquals(0.9, ab.getPrecedenceScore(), 1e-6);
        assertTrue(ab.getPrecedenceLow() > 0.8 && ab.getPrecedenceLow() < 0.9);
        assertEquals(90 * ab.getPrecedenceLow(), ab.getScore(), 1e-6);

        ApiSequences abc = row("a|b|c");
        assertEquals(2, abc.getOrder());
        assertEquals("a|b", abc.getContextKey());
        assertEquals(90, abc.getPrevStateCount());
        assertEquals(30.0 / 90, abc.getProbability(), 1e-6);
        assertEquals(30.0 / 40, abc.getPrecedenceScore(), 1e-6);

        ApiSequencesDao.instance.incrementSequences(Arrays.asList(sc(ctx("a"), "b", bucket, 10), sc(ctx(), "b", bucket, 10)));
        ab = row("a|b");
        assertEquals(100, ab.getTransitionCount());
        assertEquals(110, ab.getLastStateCount());
        assertEquals(100.0 / 110, ab.getPrecedenceScore(), 1e-6);
        assertEquals(5, ApiSequencesDao.instance.getMCollection().countDocuments());
    }

    @Test
    public void testDecay() {
        int dayLater = bucket + ApiSequencesDao.HALF_LIFE_SECONDS;
        ApiSequencesDao.instance.incrementSequences(Arrays.asList(
                sc(ctx(), "a", bucket, 80), sc(ctx(), "b", bucket, 80), sc(ctx("a"), "b", bucket, 80)));
        ApiSequencesDao.instance.incrementSequences(Arrays.asList(
                sc(ctx(), "a", dayLater, 10), sc(ctx(), "b", dayLater, 10), sc(ctx("a"), "b", dayLater, 10)));

        ApiSequences ab = row("a|b");
        assertEquals(50.0, ab.getWeight(), 1e-6);
        assertEquals(50, ab.getTransitionCount());
        assertEquals(dayLater, ab.getLastTs());
        assertEquals(1.0, ab.getPrecedenceScore(), 1e-6);

        ApiSequencesDao.instance.incrementSequences(Collections.singletonList(sc(ctx("a"), "b", bucket, 5)));
        assertEquals(55.0, row("a|b").getWeight(), 1e-6);
        assertEquals(dayLater, row("a|b").getLastTs());
    }

    @Test
    public void testIndexes() {
        ApiSequencesDao.instance.incrementSequences(Collections.singletonList(sc(ctx(), "a", bucket, 1)));
        boolean seqKeyUnique = false, score = false, ttl = false, paths = false;
        for (Document index : ApiSequencesDao.instance.getMCollection().listIndexes()) {
            Document key = index.get("key", Document.class);
            if (key.containsKey(ApiSequences.SEQ_KEY)) seqKeyUnique = Boolean.TRUE.equals(index.getBoolean("unique"));
            if (key.containsKey(ApiSequences.SCORE)) score = true;
            if (key.containsKey(ApiSequences.LAST_SEEN)) ttl = ((Number) index.get("expireAfterSeconds")).longValue() == 7 * 24 * 3600L;
            if (key.containsKey(ApiSequences.PATHS)) paths = true;
        }
        assertTrue(seqKeyUnique);
        assertTrue(score);
        assertTrue(ttl);
        assertFalse(paths);
    }
}
