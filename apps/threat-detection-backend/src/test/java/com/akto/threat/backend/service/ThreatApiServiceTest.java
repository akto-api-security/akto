package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatSeverityWiseCountRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatSeverityWiseCountResponse;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ThreatApiServiceTest {

  @Mock
  private MaliciousEventDao maliciousEventDao;

  @Mock
  private AggregateIterable<Document> aggregateIterable;

  @Mock
  private MongoCursor<Document> cursor;

  private ThreatApiService threatApiService;

  private static final String ACCOUNT_ID = "1000";

  @Before
  public void setUp() {
    threatApiService = new ThreatApiService(maliciousEventDao);
    when(maliciousEventDao.aggregateRaw(org.mockito.Mockito.eq(ACCOUNT_ID), anyList()))
        .thenReturn(aggregateIterable);
    when(aggregateIterable.cursor()).thenReturn(cursor);
  }

  private ThreatSeverityWiseCountRequest baseRequest(List<String> filterIds) {
    return ThreatSeverityWiseCountRequest.newBuilder()
        .setStartTs(1000)
        .setEndTs(2000)
        .addAllLatestAttack(filterIds)
        .build();
  }

  @Test
  public void testGetSeverityWiseCount_ReturnsCountsForEachSeverity() {
    // CRITICAL: 5, HIGH: 3, MEDIUM: 1, LOW: 2
    List<Document> docs = Arrays.asList(
        new Document("_id", "CRITICAL").append("count", 5),
        new Document("_id", "HIGH").append("count", 3),
        new Document("_id", "MEDIUM").append("count", 1),
        new Document("_id", "LOW").append("count", 2)
    );

    Iterator<Document> it = docs.iterator();
    when(cursor.hasNext()).thenAnswer(invocation -> it.hasNext());
    when(cursor.next()).thenAnswer(invocation -> it.next());

    ThreatSeverityWiseCountRequest req = baseRequest(Collections.singletonList("f1"));

    ThreatSeverityWiseCountResponse resp =
        threatApiService.getSeverityWiseCount(ACCOUNT_ID, req);

    assertEquals(4, resp.getCategoryWiseCountsCount());
    assertEquals("CRITICAL", resp.getCategoryWiseCounts(0).getSeverity());
    assertEquals(5, resp.getCategoryWiseCounts(0).getCount());
    assertEquals("HIGH", resp.getCategoryWiseCounts(1).getSeverity());
    assertEquals(3, resp.getCategoryWiseCounts(1).getCount());
    assertEquals("MEDIUM", resp.getCategoryWiseCounts(2).getSeverity());
    assertEquals(1, resp.getCategoryWiseCounts(2).getCount());
    assertEquals("LOW", resp.getCategoryWiseCounts(3).getSeverity());
    assertEquals(2, resp.getCategoryWiseCounts(3).getCount());
  }

  @Test
  public void testGetSeverityWiseCount_EmptyLatestAttackList_ReturnsEmptyResponse() {
    ThreatSeverityWiseCountRequest req = baseRequest(Collections.emptyList());

    ThreatSeverityWiseCountResponse resp =
        threatApiService.getSeverityWiseCount(ACCOUNT_ID, req);

    assertEquals(0, resp.getCategoryWiseCountsCount());
  }

  @Test
  public void testGetSeverityWiseCount_NoMatchingDocuments_ReturnsEmptyCounts() {
    when(cursor.hasNext()).thenReturn(false);

    ThreatSeverityWiseCountRequest req = baseRequest(Collections.singletonList("f1"));

    ThreatSeverityWiseCountResponse resp =
        threatApiService.getSeverityWiseCount(ACCOUNT_ID, req);

    assertEquals(0, resp.getCategoryWiseCountsCount());
  }

  @Test
  public void testGetSeverityWiseCount_MissingSomeSeverities_SkipsMissingOnes() {
    // Only CRITICAL and LOW in DB
    List<Document> docs = Arrays.asList(
        new Document("_id", "CRITICAL").append("count", 10),
        new Document("_id", "LOW").append("count", 1)
    );

    Iterator<Document> it = docs.iterator();
    when(cursor.hasNext()).thenAnswer(invocation -> it.hasNext());
    when(cursor.next()).thenAnswer(invocation -> it.next());

    ThreatSeverityWiseCountRequest req = baseRequest(Collections.singletonList("f1"));

    ThreatSeverityWiseCountResponse resp =
        threatApiService.getSeverityWiseCount(ACCOUNT_ID, req);

    // Should include only CRITICAL and LOW, in fixed order
    assertEquals(2, resp.getCategoryWiseCountsCount());
    assertEquals("CRITICAL", resp.getCategoryWiseCounts(0).getSeverity());
    assertEquals(10, resp.getCategoryWiseCounts(0).getCount());
    assertEquals("LOW", resp.getCategoryWiseCounts(1).getSeverity());
    assertEquals(1, resp.getCategoryWiseCounts(1).getCount());
  }

  @Test
  public void testGetSeverityWiseCount_ZeroCountsAreFilteredOut() {
    // One zero, one positive
    List<Document> docs = Arrays.asList(
        new Document("_id", "CRITICAL").append("count", 0),
        new Document("_id", "HIGH").append("count", 7)
    );

    Iterator<Document> it = docs.iterator();
    when(cursor.hasNext()).thenAnswer(invocation -> it.hasNext());
    when(cursor.next()).thenAnswer(invocation -> it.next());

    ThreatSeverityWiseCountRequest req = baseRequest(Collections.singletonList("f1"));

    ThreatSeverityWiseCountResponse resp =
        threatApiService.getSeverityWiseCount(ACCOUNT_ID, req);

    assertEquals(1, resp.getCategoryWiseCountsCount());
    assertEquals("HIGH", resp.getCategoryWiseCounts(0).getSeverity());
    assertEquals(7, resp.getCategoryWiseCounts(0).getCount());
  }
}


