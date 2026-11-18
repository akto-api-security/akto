package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.TimeRangeFilter;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

// The list filter as Mongo sees it. The builder is shared by the violations list and the Guardrail overview.
public class MaliciousEventServiceFilterTest {

  // The builder uses no service state, so the real method runs on a bare instance (no Kafka needed).
  private final MaliciousEventService service = mock(MaliciousEventService.class, CALLS_REAL_METHODS);

  private String query(ListMaliciousRequestsRequest.Filter.Builder filter) {
    Document query = service.buildListFilterQuery("1000", filter.build(), "AGENTIC");
    return query.toJson();
  }

  @Test
  void filterFieldsBecomeTheirMongoConditions() {
    String json = query(ListMaliciousRequestsRequest.Filter.newBuilder()
        .addLatestAttack("Block prompt attacks")
        .addSeverity("CRITICAL")
        .setDetectedAtTimeRange(TimeRangeFilter.newBuilder().setStart(100).setEnd(900)));

    assertTrue(json.contains("\"filterId\": {\"$in\": [\"Block prompt attacks\"]}"), json);
    assertTrue(json.contains("\"severity\": {\"$in\": [\"CRITICAL\"]}"), json);
    assertTrue(json.contains("\"$gte\": 100") && json.contains("\"$lte\": 900"), json);
    assertTrue(json.contains("\"contextSource\": \"AGENTIC\""), json);
  }

  @Test
  void statusFilterIsAppliedOnlyWhenAsked() {
    assertFalse(query(ListMaliciousRequestsRequest.Filter.newBuilder()).contains("\"status\""));
    assertTrue(query(ListMaliciousRequestsRequest.Filter.newBuilder().setStatusFilter("UNDER_REVIEW"))
        .contains("\"status\": \"UNDER_REVIEW\""));
  }

  @Test
  void activeStatusFilterIsTheActiveTab() {
    assertTrue(query(ListMaliciousRequestsRequest.Filter.newBuilder().setStatusFilter("ACTIVE"))
        .contains("\"status\": \"ACTIVE\""));
  }
}
