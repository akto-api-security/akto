package com.akto.threat.backend.service;

import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ExportMaliciousEventsRequest;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.TimeRangeFilter;
import com.mongodb.MongoClientSettings;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

// The export filter as Mongo sees it: end-exclusive window, keyset cursor, no status filter.
public class MaliciousEventServiceExportFilterTest {

  // The builder uses no service state, so the real method runs on a bare instance (no Kafka needed).
  private final MaliciousEventService service = mock(MaliciousEventService.class, CALLS_REAL_METHODS);

  private String filter(String accountId, ExportMaliciousEventsRequest.Builder request) {
    BsonDocument doc = service.buildExportFilter(accountId, request.build(), "AGENTIC")
        .toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
    return doc.toJson();
  }

  private ExportMaliciousEventsRequest.Builder window() {
    return ExportMaliciousEventsRequest.newBuilder()
        .setDetectedAtTimeRange(TimeRangeFilter.newBuilder().setStart(100).setEnd(200));
  }

  @Test
  void windowIsStartInclusiveEndExclusive() {
    String json = filter("1000", window());

    assertTrue(json.contains("\"$gte\": 100"), json);
    assertTrue(json.contains("\"$lt\": 200"), json);
    assertFalse(json.contains("\"$lte\""), json);
    assertFalse(json.contains("\"status\""), json);
  }

  @Test
  void cursorIsKeysetOnDetectedAtThenId() {
    String json = filter("1000", window().setCursor("150|abc-id"));

    assertTrue(json.contains("\"$gt\": 150"), json);
    assertTrue(json.contains("\"_id\": {\"$gt\": \"abc-id\"}"), json);
  }

  @Test
  void cursorKeepsLegacyAccountContextOr() {
    String json = filter("1700087964", window().setCursor("150|abc-id"));

    assertTrue(json.contains("\"contextSource\": \"AGENTIC\""), json);
    assertTrue(json.contains("\"$or\""), json);
  }

  @Test
  void malformedCursorIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> filter("1000", window().setCursor("no-separator")));
    assertThrows(IllegalArgumentException.class, () -> filter("1000", window().setCursor("x|abc-id")));
  }
}
