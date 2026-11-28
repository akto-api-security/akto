package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchTopNDataResponse;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import java.util.Collections;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ThreatActorServiceTopHostsTest {
    private MaliciousEventDao dao;
    private MongoCursor<Document> cursor;
    private ThreatActorService service;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        dao = mock(MaliciousEventDao.class);
        AggregateIterable<Document> aggregate = mock(AggregateIterable.class, RETURNS_SELF);
        cursor = mock(MongoCursor.class);
        when(dao.aggregateRaw(eq("1000"), anyList())).thenReturn(aggregate);
        when(aggregate.cursor()).thenReturn(cursor);
        service = new ThreatActorService(null, dao);
    }

    @Test
    public void hostsOnlySkipsApiAggregation() {
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(new Document("host", "device.agent").append("attacks", 7));

        FetchTopNDataResponse result = service.fetchTopNData("1000", 100, 200, Collections.emptyList(), 5,
            "ENDPOINT", "ACTIVE", "exclude", "exclude", null, true);

        assertEquals(0, result.getTopApisCount());
        assertEquals("device.agent", result.getTopHosts(0).getHost());
        assertEquals(7, result.getTopHosts(0).getAttacks());
        verify(dao, times(1)).aggregateRaw(eq("1000"), anyList());
    }

    @Test
    public void existingCallersStillReceiveApisAndHosts() {
        when(cursor.hasNext()).thenReturn(true, false, true, false);
        when(cursor.next()).thenReturn(
            new Document("endpoint", "/chat").append("method", "POST").append("severity", "HIGH").append("attacks", 7),
            new Document("host", "device.agent").append("attacks", 7));

        FetchTopNDataResponse result = service.fetchTopNData("1000", 100, 200, Collections.emptyList(), 5,
            "ENDPOINT", "ACTIVE", "exclude", "exclude", null);

        assertEquals("/chat", result.getTopApis(0).getEndpoint());
        assertEquals("device.agent", result.getTopHosts(0).getHost());
        verify(dao, times(2)).aggregateRaw(eq("1000"), anyList());
    }
}
