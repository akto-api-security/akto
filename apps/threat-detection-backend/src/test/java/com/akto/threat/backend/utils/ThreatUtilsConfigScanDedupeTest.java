package com.akto.threat.backend.utils;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Only /config/ events are sorted and grouped; the rest pass through in the same row shape.
public class ThreatUtilsConfigScanDedupeTest {

  private final List<Document> preceding = Collections.singletonList(new Document("$match", new Document("contextSource", "ENDPOINT")));

  @Test
  void nonEndpointContextsAreNotDeduped() {
    assertTrue(ThreatUtils.configScanDedupeStages(preceding, "AGENTIC").isEmpty());
  }

}
