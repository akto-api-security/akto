package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatSeverityWiseCountRequest;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Severity counts on the Atlas Active tab skip the config-scan dedupe once /config/ events are excluded.
public class ThreatApiServiceSeverityDedupeTest {

  private MaliciousEventDao dao;
  private ThreatApiService service;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    dao = mock(MaliciousEventDao.class);
    AggregateIterable<Document> iterable = mock(AggregateIterable.class);
    MongoCursor<Document> cursor = mock(MongoCursor.class);
    when(dao.aggregateRaw(eq("1"), anyList())).thenReturn(iterable);
    when(iterable.maxTime(anyLong(), any(TimeUnit.class))).thenReturn(iterable);
    when(iterable.cursor()).thenReturn(cursor);
    service = new ThreatApiService(dao);
  }

  @SuppressWarnings("unchecked")
  private String pipelineFor(String configEvalMode) {
    service.getSeverityWiseCount("1", ThreatSeverityWiseCountRequest.newBuilder().setStartTs(1).setEndTs(2).setStatus("ACTIVE").build(),
        "ENDPOINT", "exclude", configEvalMode);
    ArgumentCaptor<List<Document>> pipeline = ArgumentCaptor.forClass(List.class);
    verify(dao).aggregateRaw(eq("1"), pipeline.capture());
    return pipeline.getValue().toString();
  }

  @Test
  void activeTabSkipsTheDedupeSortAndGroup() {
    assertFalse(pipelineFor("exclude").contains("$sort"));
  }

  @Test
  void otherTabsKeepTheDedupe() {
    assertTrue(pipelineFor(null).contains("$sort"));
  }
}
