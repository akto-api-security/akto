package com.akto.threat.backend.utils;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Only /config/ events are sorted and grouped; the rest pass through in the same row shape.
public class ThreatUtilsConfigScanDedupeTest {

  private final List<Document> preceding = Collections.singletonList(new Document("$match", new Document("contextSource", "ENDPOINT")));

  @Test
  void nonEndpointContextsAreNotDeduped() {
    assertTrue(ThreatUtils.configScanDedupeStages(preceding, "AGENTIC").isEmpty());
  }

  @Test
  void configEventsAreDedupedInAUnionThatReplaysTheCallersStages() {
    List<Document> stages = ThreatUtils.configScanDedupeStages(preceding, "ENDPOINT");

    assertEquals("$match", stages.get(0).keySet().iterator().next());
    Document passThrough = stages.get(1).get("$project", Document.class);
    assertEquals(new Document("uniqueId", "$_id"), passThrough.get("_id"));
    for (String field : new String[] {"host", "category", "subCategory", "status", "severity", "detectedAt", "successfulExploit"}) {
      assertTrue(passThrough.containsKey(field), field);
    }

    Document union = stages.get(2).get("$unionWith", Document.class);
    assertEquals("malicious_events", union.getString("coll"));
    List<Document> branch = union.getList("pipeline", Document.class);
    assertEquals(preceding.get(0), branch.get(0));
    assertTrue(branch.get(branch.size() - 1).containsKey("$group"));
    assertEquals(4, branch.get(branch.size() - 1).get("$group", Document.class).get("_id", Document.class).size());
  }
}
