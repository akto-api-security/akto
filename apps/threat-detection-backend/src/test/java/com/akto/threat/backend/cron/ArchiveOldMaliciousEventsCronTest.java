package com.akto.threat.backend.cron;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ArchiveOldMaliciousEventsCronTest {

    @Mock
    private MongoClient mongoClient;

    @Mock
    private MongoDatabase mongoDatabase;

    @Mock
    private MongoCollection<Document> cfgCollection;

    @Mock
    private MongoCollection<Document> sourceCollection;

    @Mock
    private MongoCollection<Document> destCollection;

    private ArchiveOldMaliciousEventsCron cron;

    @Before
    public void setup() {
        cron = new ArchiveOldMaliciousEventsCron(mongoClient);
    }

    @Test
    public void testFetchRetentionDays_DefaultWhenMissing() throws Exception {
        when(mongoDatabase.getCollection(eq("threat_configuration"), eq(Document.class))).thenReturn(cfgCollection);
        when(cfgCollection.find()).thenReturn(mock(com.mongodb.client.FindIterable.class));
        when(cfgCollection.find().first()).thenReturn(null);

        Method m = ArchiveOldMaliciousEventsCron.class.getDeclaredMethod("fetchRetentionDays", MongoDatabase.class);
        m.setAccessible(true);
        long days = (long) m.invoke(cron, mongoDatabase);
        assertEquals(60L, days);
    }

    @Test
    public void testFetchRetentionDays_Valid90() throws Exception {
        when(mongoDatabase.getCollection(eq("threat_configuration"), eq(Document.class))).thenReturn(cfgCollection);
        when(cfgCollection.find()).thenReturn(mock(com.mongodb.client.FindIterable.class));
        Document cfg = new Document("archivalDays", 90);
        when(cfgCollection.find().first()).thenReturn(cfg);

        Method m = ArchiveOldMaliciousEventsCron.class.getDeclaredMethod("fetchRetentionDays", MongoDatabase.class);
        m.setAccessible(true);
        long days = (long) m.invoke(cron, mongoDatabase);
        assertEquals(90L, days);
    }

    @Test
    public void testFetchRetentionDays_InvalidFallsBack() throws Exception {
        when(mongoDatabase.getCollection(eq("threat_configuration"), eq(Document.class))).thenReturn(cfgCollection);
        when(cfgCollection.find()).thenReturn(mock(com.mongodb.client.FindIterable.class));
        Document cfg = new Document("archivalDays", 45);
        when(cfgCollection.find().first()).thenReturn(cfg);

        Method m = ArchiveOldMaliciousEventsCron.class.getDeclaredMethod("fetchRetentionDays", MongoDatabase.class);
        m.setAccessible(true);
        long days = (long) m.invoke(cron, mongoDatabase);
        assertEquals(60L, days);
    }

    @Test
    public void testTrimCollectionIfExceedsCap_DeletesOldest() throws Exception {
        when(sourceCollection.countDocuments()).thenReturn(410_000L);

        // Build a small batch of oldest docs
        List<Document> oldest = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            oldest.add(new Document("_id", "id-" + i).append("detectedAt", i));
        }

        // Mock find().sort().limit().cursor()
        com.mongodb.client.FindIterable<Document> findIt = mock(com.mongodb.client.FindIterable.class);
        when(sourceCollection.find()).thenReturn(findIt);
        when(findIt.sort(any())).thenReturn(findIt);
        when(findIt.limit(anyInt())).thenReturn(findIt);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(findIt.cursor()).thenReturn(cursor);
        // Cursor iteration
        when(cursor.hasNext()).thenAnswer(inv -> {
            int idx = (int) cursorState[0];
            return idx < oldest.size();
        });
        when(cursor.next()).thenAnswer(inv -> {
            int idx = (int) cursorState[0];
            Document d = oldest.get(idx);
            cursorState[0] = idx + 1;
            return d;
        });

        // deleteMany returns count
        when(sourceCollection.deleteMany(any())).thenReturn(new com.mongodb.client.result.DeleteResult() {
            @Override public boolean wasAcknowledged() { return true; }
            @Override public long getDeletedCount() { return oldest.size(); }
        });

        Method m = ArchiveOldMaliciousEventsCron.class.getDeclaredMethod(
                "trimCollectionIfExceedsCap", String.class, MongoCollection.class, MongoCollection.class);
        m.setAccessible(true);
        // reset cursor state before invoke
        cursorState[0] = 0;
        m.invoke(cron, "1000", sourceCollection, destCollection);

        verify(sourceCollection, atLeastOnce()).deleteMany(any());
    }

    // mutable state for cursor index in answers
    private final Object[] cursorState = new Object[]{0};
}


