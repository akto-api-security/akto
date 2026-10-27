package com.akto.threat.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.IndexOptions;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class ThreatUtilsFilterFieldIndexTest {

    private static final String TYPE_INDEX = "idx_context_status_agenticType_detectedAt";
    private static final String BEHAVIOUR_INDEX = "idx_context_status_behaviour_detectedAt";

    @SuppressWarnings("unchecked")
    private static MongoCollection<MaliciousEventDto> collectionWithIndexes(MaliciousEventDao dao, String... existingNames) {
        MongoCollection<MaliciousEventDto> collection = mock(MongoCollection.class);
        ListIndexesIterable<Document> iterable = mock(ListIndexesIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(dao.getCollection("1000")).thenReturn(collection);
        when(collection.listIndexes()).thenReturn(iterable);
        when(iterable.iterator()).thenReturn(cursor);

        Boolean[] hasNext = new Boolean[existingNames.length + 1];
        Document[] next = new Document[existingNames.length];
        for (int i = 0; i < existingNames.length; i++) {
            hasNext[i] = true;
            next[i] = new Document("name", existingNames[i]);
        }
        hasNext[existingNames.length] = false;
        when(cursor.hasNext()).thenReturn(hasNext[0], java.util.Arrays.copyOfRange(hasNext, 1, hasNext.length));
        if (next.length > 0) {
            when(cursor.next()).thenReturn(next[0], java.util.Arrays.copyOfRange(next, 1, next.length));
        }
        return collection;
    }

    private static List<IndexOptions> createdOptions(MongoCollection<MaliciousEventDto> collection) {
        ArgumentCaptor<IndexOptions> options = ArgumentCaptor.forClass(IndexOptions.class);
        verify(collection, atLeastOnce()).createIndex(any(Bson.class), options.capture());
        return new ArrayList<>(options.getAllValues());
    }

    private static IndexOptions named(List<IndexOptions> options, String name) {
        for (IndexOptions o : options) {
            if (name.equals(o.getName())) {
                return o;
            }
        }
        return null;
    }

    @Test
    public void createsBothFilterFieldIndexesAsPartialIndexes() {
        MaliciousEventDao dao = mock(MaliciousEventDao.class);
        MongoCollection<MaliciousEventDto> collection = collectionWithIndexes(dao);

        ThreatUtils.createIndexIfAbsent("1000", dao);

        List<IndexOptions> options = createdOptions(collection);
        IndexOptions type = named(options, TYPE_INDEX);
        IndexOptions behaviour = named(options, BEHAVIOUR_INDEX);
        assertNotNull(type);
        assertNotNull(behaviour);
        assertEquals(new Document("agenticType", new Document("$exists", true)), type.getPartialFilterExpression());
        assertEquals(new Document("behaviour", new Document("$exists", true)), behaviour.getPartialFilterExpression());
    }

    @Test
    public void doesNotRecreateIndexesThatAlreadyExist() {
        MaliciousEventDao dao = mock(MaliciousEventDao.class);
        MongoCollection<MaliciousEventDto> collection = collectionWithIndexes(dao, TYPE_INDEX, BEHAVIOUR_INDEX);

        ThreatUtils.createIndexIfAbsent("1000", dao);

        List<IndexOptions> options = createdOptions(collection);
        assertFalse(options.stream().anyMatch(o -> TYPE_INDEX.equals(o.getName())));
        assertFalse(options.stream().anyMatch(o -> BEHAVIOUR_INDEX.equals(o.getName())));
    }

    @Test
    public void createsOnlyTheMissingFilterFieldIndex() {
        MaliciousEventDao dao = mock(MaliciousEventDao.class);
        MongoCollection<MaliciousEventDto> collection = collectionWithIndexes(dao, TYPE_INDEX);

        ThreatUtils.createIndexIfAbsent("1000", dao);

        List<IndexOptions> options = createdOptions(collection);
        assertFalse(options.stream().anyMatch(o -> TYPE_INDEX.equals(o.getName())));
        assertTrue(options.stream().anyMatch(o -> BEHAVIOUR_INDEX.equals(o.getName())));
    }

    @Test
    public void existingIndexesAreStillCreatedWhenMissing() {
        MaliciousEventDao dao = mock(MaliciousEventDao.class);
        MongoCollection<MaliciousEventDto> collection = collectionWithIndexes(dao);

        ThreatUtils.createIndexIfAbsent("1000", dao);

        List<IndexOptions> options = createdOptions(collection);
        assertNotNull(named(options, "detectedAt_1"));
        assertNotNull(named(options, "idx_context_detectedAt_latestApiEndpoint"));
    }
}
