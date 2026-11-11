package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailActionCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailHostPolicyCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailPolicyCountsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.FetchGuardrailTriageTimeResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.HostScope;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.GuardrailOverviewRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.TimeRangeFilter;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GuardrailOverviewServiceTest {

  private MaliciousEventDao maliciousEventDao;
  private MaliciousEventService maliciousEventService;
  private MongoCursor<Document> cursor;

  private GuardrailOverviewService service;

  private static final String ACCOUNT_ID = "1000";
  private static final long DAY = 86400L;
  // Midnight UTC, a day-aligned timestamp.
  private static final long DAY_1 = 20000L * DAY;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    maliciousEventDao = mock(MaliciousEventDao.class);
    maliciousEventService = mock(MaliciousEventService.class);
    AggregateIterable<Document> aggregateIterable = mock(AggregateIterable.class);
    cursor = mock(MongoCursor.class);

    service = new GuardrailOverviewService(maliciousEventDao, maliciousEventService);
    when(maliciousEventService.buildListFilterQuery(eq(ACCOUNT_ID), any(), anyString()))
        .thenReturn(new Document("contextSource", "AGENTIC"));
    when(maliciousEventDao.aggregateRaw(eq(ACCOUNT_ID), anyList())).thenReturn(aggregateIterable);
    when(aggregateIterable.cursor()).thenReturn(cursor);
  }

  private void returnRows(Document... rows) {
    Iterator<Document> it = Arrays.asList(rows).iterator();
    when(cursor.hasNext()).thenAnswer(i -> it.hasNext());
    when(cursor.next()).thenAnswer(i -> it.next());
  }

  private static Document row(long day, String action, int count) {
    return new Document("_id", new Document("day", day).append("action", action)).append("count", count);
  }

  private static GuardrailOverviewRequest request(long start, long end) {
    return GuardrailOverviewRequest.newBuilder()
        .setFilter(ListMaliciousRequestsRequest.Filter.newBuilder()
            .setDetectedAtTimeRange(TimeRangeFilter.newBuilder().setStart(start).setEnd(end)))
        .build();
  }

  @Test
  void actionCountsAreSplitPerDayAndSummedIntoTotal() {
    returnRows(row(DAY_1, "blocked", 5), row(DAY_1, "flagged", 2), row(DAY_1 + DAY, "alerted", 3), row(DAY_1 + DAY, "flagged", 1));

    FetchGuardrailActionCountsResponse resp =
        service.fetchActionCounts(ACCOUNT_ID, request(DAY_1, DAY_1 + DAY + 100), "AGENTIC", null);

    assertEquals(2, resp.getDaysCount());
    assertEquals(DAY_1, resp.getDays(0).getTs());
    assertEquals(5, resp.getDays(0).getCounts().getBlocked());
    assertEquals(2, resp.getDays(0).getCounts().getFlagged());
    assertEquals(3, resp.getDays(1).getCounts().getAlerted());
    assertEquals(1, resp.getDays(1).getCounts().getFlagged());
    assertEquals(5, resp.getTotal().getBlocked());
    assertEquals(3, resp.getTotal().getAlerted());
    assertEquals(3, resp.getTotal().getFlagged());
  }

  @Test
  void daysWithNoEventsStillGetAnEntry() {
    returnRows(row(DAY_1, "blocked", 4), row(DAY_1 + 3 * DAY, "blocked", 6));

    FetchGuardrailActionCountsResponse resp =
        service.fetchActionCounts(ACCOUNT_ID, request(DAY_1, DAY_1 + 3 * DAY), "AGENTIC", null);

    assertEquals(4, resp.getDaysCount());
    assertEquals(0, resp.getDays(1).getCounts().getBlocked());
    assertEquals(0, resp.getDays(2).getCounts().getBlocked());
    assertEquals(6, resp.getDays(3).getCounts().getBlocked());
    assertEquals(10, resp.getTotal().getBlocked());
  }

  @Test
  void noEventsAndNoRangeGivesEmptyResponse() {
    returnRows();

    FetchGuardrailActionCountsResponse resp = service.fetchActionCounts(ACCOUNT_ID, request(0, 0), "AGENTIC", null);

    assertEquals(0, resp.getDaysCount());
    assertEquals(0, resp.getTotal().getBlocked());
  }

  @Test
  void aYearLongCapKeepsTheNewestDaysButTotalsEveryEvent() {
    long end = DAY_1 + 1000 * DAY;
    returnRows(row(DAY_1, "blocked", 7), row(end - end % DAY, "blocked", 1));

    FetchGuardrailActionCountsResponse resp =
        service.fetchActionCounts(ACCOUNT_ID, request(DAY_1, end), "AGENTIC", null);

    assertEquals(366, resp.getDaysCount());
    assertEquals(end - end % DAY, resp.getDays(365).getTs());
    assertEquals(8, resp.getTotal().getBlocked());
  }

  @Test
  @SuppressWarnings("unchecked")
  void pipelineMatchesTheListFilterAndTheHostScope() {
    returnRows();
    Document hostScope = new Document("host", new Document("$in", Arrays.asList("agent-a")));

    service.fetchActionCounts(ACCOUNT_ID, request(DAY_1, DAY_1 + DAY), "AGENTIC", hostScope);

    ArgumentCaptor<List<Document>> pipeline = ArgumentCaptor.forClass(List.class);
    verify(maliciousEventDao).aggregateRaw(eq(ACCOUNT_ID), pipeline.capture());
    Document match = (Document) pipeline.getValue().get(0).get("$match");
    assertEquals("AGENTIC", match.get("contextSource"));
    assertTrue(((List<Document>) match.get("$and")).contains(hostScope));
    assertTrue(pipeline.getValue().get(1).containsKey("$group"));
  }

  private static GuardrailOverviewRequest.Builder pageRequest(Integer skip, Integer limit) {
    GuardrailOverviewRequest.Builder b = request(DAY_1, DAY_1 + DAY).toBuilder();
    if (skip != null) b.setSkip(skip);
    if (limit != null) b.setLimit(limit);
    return b;
  }

  @SuppressWarnings("unchecked")
  private List<Document> capturedPipeline() {
    ArgumentCaptor<List<Document>> pipeline = ArgumentCaptor.forClass(List.class);
    verify(maliciousEventDao).aggregateRaw(eq(ACCOUNT_ID), pipeline.capture());
    return pipeline.getValue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void policyCountsReturnTheRequestedPageAndTheTotal() {
    returnRows(new Document("page", Arrays.asList(
            new Document("_id", "Block prompt attacks").append("count", 9), new Document("_id", null).append("count", 2)))
        .append("total", Collections.singletonList(new Document("n", 14))));

    FetchGuardrailPolicyCountsResponse resp =
        service.fetchPolicyCounts(ACCOUNT_ID, pageRequest(5, 10).build(), "AGENTIC", null);

    assertEquals(2, resp.getPoliciesCount());
    assertEquals("Block prompt attacks", resp.getPolicies(0).getPolicy());
    assertEquals(9, resp.getPolicies(0).getCount());
    assertEquals("", resp.getPolicies(1).getPolicy());
    assertEquals(14, resp.getTotal());

    Document facet = (Document) capturedPipeline().get(3).get("$facet");
    List<Document> page = (List<Document>) facet.get("page");
    assertEquals(5, page.get(0).get("$skip"));
    assertEquals(10, page.get(1).get("$limit"));
    assertEquals(new Document("count", -1).append("_id", 1), capturedPipeline().get(2).get("$sort"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void policyCountPageSizeDefaultsAndIsCapped() {
    returnRows();
    service.fetchPolicyCounts(ACCOUNT_ID, pageRequest(null, null).build(), "AGENTIC", null);
    List<Document> page = (List<Document>) ((Document) capturedPipeline().get(3).get("$facet")).get("page");
    assertEquals(0, page.get(0).get("$skip"));
    assertEquals(20, page.get(1).get("$limit"));

    setUp();  // fresh mocks, so the second call's pipeline is the one captured
    returnRows();
    service.fetchPolicyCounts(ACCOUNT_ID, pageRequest(0, 100000).build(), "AGENTIC", null);
    page = (List<Document>) ((Document) capturedPipeline().get(3).get("$facet")).get("page");
    assertEquals(200, page.get(1).get("$limit"));
  }

  @Test
  void policyCountsWithNoEventsAreEmpty() {
    returnRows();
    FetchGuardrailPolicyCountsResponse resp = service.fetchPolicyCounts(ACCOUNT_ID, pageRequest(0, 5).build(), "AGENTIC", null);
    assertEquals(0, resp.getPoliciesCount());
    assertEquals(0, resp.getTotal());
  }

  @Test
  void hostPolicyCountsAreMappedAsTheyCome() {
    returnRows(
        new Document("_id", new Document("host", "agent-a").append("policy", "PII")).append("count", 12),
        new Document("_id", new Document("host", null).append("policy", "Secrets")).append("count", 3));

    FetchGuardrailHostPolicyCountsResponse resp =
        service.fetchHostPolicyCounts(ACCOUNT_ID, pageRequest(null, null).build(), "AGENTIC", null);

    assertEquals(2, resp.getRowsCount());
    assertEquals("agent-a", resp.getRows(0).getHost());
    assertEquals("PII", resp.getRows(0).getPolicy());
    assertEquals(12, resp.getRows(0).getCount());
    assertEquals("", resp.getRows(1).getHost());
    assertEquals(10000, capturedPipeline().get(3).get("$limit"));
  }

  @Test
  void triageTimeIsTheMedianOfTheTriagedEvents() {
    returnRows(new Document("_id", null).append("elapsed", Arrays.asList(1000L, 100L, 300, 200L)));

    FetchGuardrailTriageTimeResponse resp =
        service.fetchTriageTime(ACCOUNT_ID, pageRequest(null, null).build(), "AGENTIC", null);

    assertEquals(250, resp.getMedianSeconds());  // (200 + 300) / 2
    assertEquals(4, resp.getTriaged());
  }

  @Test
  void triageTimeOfAnOddCountIsTheMiddleValue() {
    returnRows(new Document("_id", null).append("elapsed", Arrays.asList(900L, 10L, 60L)));
    assertEquals(60, service.fetchTriageTime(ACCOUNT_ID, pageRequest(null, null).build(), "AGENTIC", null).getMedianSeconds());
  }

  @Test
  void triageTimeWithNothingTriagedIsZero() {
    returnRows();
    FetchGuardrailTriageTimeResponse resp = service.fetchTriageTime(ACCOUNT_ID, pageRequest(null, null).build(), "AGENTIC", null);
    assertEquals(0, resp.getMedianSeconds());
    assertEquals(0, resp.getTriaged());
  }

  @Test
  @SuppressWarnings("unchecked")
  void triagePipelineOnlyLooksAtEventsWithAStatusOrOwnerChange() {
    returnRows();
    service.fetchTriageTime(ACCOUNT_ID, pageRequest(null, null).build(), "AGENTIC", null);
    List<Document> and = (List<Document>) ((Document) capturedPipeline().get(0).get("$match")).get("$and");
    assertTrue(and.get(1).toJson().contains("STATUS") && and.get(1).toJson().contains("OWNER"));
    assertEquals(new Document("elapsed", new Document("$gte", 0)), capturedPipeline().get(2).get("$match"));
  }

  private ListMaliciousRequestsRequest listRequestSentFor(GuardrailOverviewRequest request) {
    ListMaliciousRequestsResponse reply = ListMaliciousRequestsResponse.newBuilder().setTotal(42).build();
    when(maliciousEventService.listMaliciousRequests(eq(ACCOUNT_ID), any(), anyString(), anyString(), anyString())).thenReturn(reply);

    ListMaliciousRequestsResponse resp = service.fetchNeedsAttention(ACCOUNT_ID, request, "AGENTIC");

    assertEquals(42, resp.getTotal());
    ArgumentCaptor<ListMaliciousRequestsRequest> sent = ArgumentCaptor.forClass(ListMaliciousRequestsRequest.class);
    verify(maliciousEventService).listMaliciousRequests(eq(ACCOUNT_ID), sent.capture(), eq("AGENTIC"), eq("exclude"), eq("exclude"));
    return sent.getValue();
  }

  @Test
  void needsAttentionAsksTheListForTheWorstActiveCasesFirstWithPaging() {
    ListMaliciousRequestsRequest sent = listRequestSentFor(pageRequest(10, 5).build());

    assertEquals("ACTIVE", sent.getFilter().getStatusFilter());
    assertTrue(sent.getFilter().getSortBySeverity());
    assertEquals(10, sent.getSkip());
    assertEquals(5, sent.getLimit());
    assertEquals(DAY_1, sent.getFilter().getDetectedAtTimeRange().getStart());
  }

  @Test
  void needsAttentionHostScopeReplacesTheCallersHostFilter() {
    GuardrailOverviewRequest base = pageRequest(0, 5).build();
    GuardrailOverviewRequest scoped = base.toBuilder()
        .setFilter(base.getFilter().toBuilder().addHosts("someone-elses-host").setMatchClaudeConfig(true))
        .setHostScope(HostScope.newBuilder().addHosts("mine").addLooseHostKeys("dev svc"))
        .build();

    ListMaliciousRequestsRequest sent = listRequestSentFor(scoped);

    assertEquals(Collections.singletonList("mine"), sent.getFilter().getHostsList());
    assertEquals(Collections.singletonList("dev svc"), sent.getFilter().getLooseHostKeysList());
    assertFalse(sent.getFilter().hasMatchClaudeConfig());
  }

  @Test
  void needsAttentionWithAnEmptyHostScopeSeesNothing() {
    GuardrailOverviewRequest scoped = pageRequest(0, 5).setHostScope(HostScope.getDefaultInstance()).build();

    ListMaliciousRequestsResponse resp = service.fetchNeedsAttention(ACCOUNT_ID, scoped, "AGENTIC");

    assertSame(ListMaliciousRequestsResponse.getDefaultInstance(), resp);
    verify(maliciousEventService, never()).listMaliciousRequests(any(), any(), any(), any(), any());
  }
}
