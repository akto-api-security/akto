package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.SequenceCount;
import com.akto.dto.SequenceModel;
import com.akto.utils.MongoBasedTest;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestSequenceModelDao extends MongoBasedTest {

    private int currentBucket;
    private int previousBucket;

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        SequenceCountDao.instance.getMCollection().drop();
        SequenceModelDao.instance.getMCollection().drop();
        SequenceCountDao.instance.createIndicesIfAbsent();
        SequenceModelDao.instance.createIndicesIfAbsent();
        currentBucket = (Context.now() / 3600) * 3600;
        previousBucket = currentBucket - 3600;
    }

    private static SequenceCount sc(List<String> context, String next, int bucket, long count) {
        return new SequenceCount(0, context, next, bucket, count);
    }

    private void addSplit(List<SequenceCount> out, List<String> context, long a, long b, long c) {
        out.add(sc(context, "a", currentBucket, a / 2));
        out.add(sc(context, "a", previousBucket, a - a / 2));
        out.add(sc(context, "b", currentBucket, b / 2));
        out.add(sc(context, "b", previousBucket, b - b / 2));
        out.add(sc(context, "c", currentBucket, c / 2));
        out.add(sc(context, "c", previousBucket, c - c / 2));
    }

    private SequenceModel model(String contextKey, String next) {
        return SequenceModelDao.instance.findOne(Filters.and(
                Filters.eq(SequenceModel.CONTEXT_KEY, contextKey), Filters.eq(SequenceModel.NEXT, next)));
    }

    @Test
    public void testWriteAndRecompute() {
        List<String> order0 = new ArrayList<>();
        List<String> ctxA = Collections.singletonList("a");
        List<String> ctxAA = Arrays.asList("a", "a");
        List<String> ctxBA = Arrays.asList("b", "a");

        List<SequenceCount> first = new ArrayList<>();
        addSplit(first, order0, 2000, 15000, 200);
        first.add(sc(order0, "a", currentBucket - 30 * 3600, 1000));
        addSplit(first, ctxAA, 173, 1367, 13);
        addSplit(first, ctxBA, 1500, 50, 3);
        SequenceModelDao.instance.writeSequenceCounts(first);

        assertFalse(model("a|a", "a").isCollapsed());

        List<SequenceCount> second = new ArrayList<>();
        addSplit(second, ctxA, 1555, 13718, 169);
        second.add(sc(ctxA, "c", currentBucket, 0));
        SequenceModelDao.instance.writeSequenceCounts(second);

        Document doc = SequenceCountDao.instance.getMCollection().find(Filters.and(
                Filters.eq(SequenceCount.CONTEXT_KEY, "a|a"),
                Filters.eq(SequenceCount.NEXT, "a"),
                Filters.eq(SequenceCount.BUCKET_START, currentBucket)), Document.class).first();
        assertNotNull(doc);
        assertEquals(86L, ((Number) doc.get(SequenceCount.COUNT)).longValue());
        assertEquals("a", doc.getString(SequenceCount.PARENT_CONTEXT_KEY));
        assertEquals(new Date((currentBucket + 48 * 3600L) * 1000L), doc.getDate(SequenceCount.EXPIRES_AT));

        SequenceModelDao.instance.writeSequenceCounts(Collections.singletonList(sc(ctxAA, "a", currentBucket, 5)));
        doc = SequenceCountDao.instance.getMCollection().find(Filters.and(
                Filters.eq(SequenceCount.CONTEXT_KEY, "a|a"),
                Filters.eq(SequenceCount.NEXT, "a"),
                Filters.eq(SequenceCount.BUCKET_START, currentBucket)), Document.class).first();
        assertEquals(91L, ((Number) doc.get(SequenceCount.COUNT)).longValue());

        SequenceModel root = model("", "a");
        assertEquals(2000L, root.getCount24h());
        assertEquals(17200L, root.getContextTotal24h());
        assertNull(root.getParentContextKey());
        assertFalse(root.isCollapsed());
        assertEquals(1.0, root.getPrecedence(), 1e-12);

        SequenceModel ac = model("a", "c");
        assertEquals(169L, ac.getCount24h());
        assertEquals(15442L, ac.getContextTotal24h());
        assertEquals(1, ac.getOrder());
        assertEquals("", ac.getParentContextKey());
        assertEquals(169.0 / 15442, ac.getProbability(), 1e-12);
        assertEquals(0.845, ac.getPrecedence(), 1e-12);
        assertTrue(ac.getCiLow() < ac.getProbability() && ac.getProbability() < ac.getCiHigh());
        assertFalse(ac.isCollapsed());

        SequenceModel aa = model("a|a", "a");
        assertEquals(178L, aa.getCount24h());
        assertEquals(1558L, aa.getContextTotal24h());
        assertEquals(2, aa.getOrder());
        assertEquals("a", aa.getParentContextKey());

        for (String next : Arrays.asList("a", "b", "c")) {
            assertTrue(model("a|a", next).isCollapsed());
            assertFalse(model("b|a", next).isCollapsed());
        }

        Set<String> keys = new HashSet<>();
        for (SequenceModel m : SequenceModelDao.instance.findAll(Filters.empty())) {
            keys.add(m.getContextKey() + "->" + m.getNext());
        }
        assertEquals(12, keys.size());

        List<Document> countIndexes = SequenceCountDao.instance.getMCollection().listIndexes().into(new ArrayList<Document>());
        assertEquals(5, countIndexes.size());
        List<Document> modelIndexes = SequenceModelDao.instance.getMCollection().listIndexes().into(new ArrayList<Document>());
        assertEquals(3, modelIndexes.size());
    }
}
