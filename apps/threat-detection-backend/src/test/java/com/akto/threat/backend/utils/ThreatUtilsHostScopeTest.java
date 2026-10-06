package com.akto.threat.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.HostScope;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatSeverityWiseCountRequest;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.threat.backend.service.ThreatApiService;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;

public class ThreatUtilsHostScopeTest {

    @Test
    public void testHostAttributionMatch() {
        assertNull(ThreatUtils.hostAttributionMatch(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false));

        Document hostsOnly = ThreatUtils.hostAttributionMatch(Collections.singletonList("agent.example.com"), Collections.emptyList(), Collections.emptyList(), false);
        assertEquals(new Document("host", new Document("$in", Collections.singletonList("agent.example.com"))), hostsOnly);

        // several conditions are ORed
        Document all = ThreatUtils.hostAttributionMatch(Collections.singletonList("agent.example.com"), Collections.singletonList("agent com"),
                Collections.singletonList("laptop1"), false);
        assertEquals(3, ((List<?>) all.get("$or")).size());
    }

    @Test
    public void testHostScopeWithNothingMatchesNothing() {
        Document match = ThreatUtils.hostScopeMatch(HostScope.newBuilder().build());
        assertEquals(new Document("host", new Document("$in", Collections.emptyList())), match);
    }

    @Test
    public void testAndHostScopeKeepsExistingConditions() {
        Document existing = new Document("x", 1);
        Document match = new Document("detectedAt", 5).append("$and", new ArrayList<>(Collections.singletonList(existing)));
        Document scope = new Document("host", "a");
        ThreatUtils.andHostScope(match, scope);
        assertEquals(Arrays.asList(existing, scope), match.get("$and"));
        assertEquals(5, match.get("detectedAt"));

        Document untouched = new Document("detectedAt", 5);
        ThreatUtils.andHostScope(untouched, null);
        assertEquals(new Document("detectedAt", 5), untouched);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSeverityCountsUseTheHostScope() {
        MaliciousEventDao maliciousEventDao = mock(MaliciousEventDao.class);
        AggregateIterable<Document> aggregateIterable = mock(AggregateIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);
        when(maliciousEventDao.aggregateRaw(eq("1000"), anyList())).thenReturn(aggregateIterable);
        when(aggregateIterable.cursor()).thenReturn(cursor);
        ThreatApiService service = new ThreatApiService(maliciousEventDao);

        ThreatSeverityWiseCountRequest req = ThreatSeverityWiseCountRequest.newBuilder().setStartTs(1).setEndTs(2)
                .setHostScope(HostScope.newBuilder().addHosts("agent.example.com")).build();
        service.getSeverityWiseCount("1000", req, "AGENTIC");

        ArgumentCaptor<List<Document>> pipeline = ArgumentCaptor.forClass(List.class);
        verify(maliciousEventDao).aggregateRaw(eq("1000"), pipeline.capture());
        Document first = pipeline.getValue().get(0);
        assertEquals(new Document("$match", new Document("host", new Document("$in", Collections.singletonList("agent.example.com")))), first);

        // without a host scope the pipeline is unchanged (whole account)
        ThreatSeverityWiseCountRequest unscoped = ThreatSeverityWiseCountRequest.newBuilder().setStartTs(1).setEndTs(2).build();
        service.getSeverityWiseCount("1000", unscoped, "AGENTIC");
        verify(maliciousEventDao, org.mockito.Mockito.times(2)).aggregateRaw(eq("1000"), pipeline.capture());
        assertTrue(pipeline.getValue().stream().noneMatch(stage -> stage.toJson().contains("agent.example.com")));
    }
}
