package com.akto.threat.backend.utils;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThreatUtilsEvalTypeTest {

  @Test
  void configEndpointsAreConfigForAnyAgentPrefix() {
    assertEquals("CONFIG", ThreatUtils.evalType("ENDPOINT", "/claude/config/skipDangerousModePermissionPrompt"));
    assertEquals("CONFIG", ThreatUtils.evalType("endpoint", "/codex/config/mcp_servers.computer-use.command"));
  }

  @Test
  void skillEndpointsAreSkillOnlyWhenAnchoredAtStart() {
    assertEquals("SKILL", ThreatUtils.evalType("ENDPOINT", "/skills/pdf"));
    assertNull(ThreatUtils.evalType("ENDPOINT", "/api/skills/pdf"));
  }

  @Test
  void skillPrefixWinsOverConfigSegment() {
    assertEquals("SKILL", ThreatUtils.evalType("ENDPOINT", "/skills/x/config/y"));
  }

  @Test
  void nonEndpointContextOrMissingEndpointIsUntyped() {
    assertNull(ThreatUtils.evalType("AGENTIC", "/claude/config/a"));
    assertNull(ThreatUtils.evalType(null, "/claude/config/a"));
    assertNull(ThreatUtils.evalType("ENDPOINT", null));
    assertNull(ThreatUtils.evalType("ENDPOINT", "/v1/messages"));
  }

  @Test
  void dedupeKeyIsStablePerSettingAndDiffersAcrossHostActorCategoryEndpoint() {
    String key = ThreatUtils.configDedupeKey("h", "a", "c", "/claude/config/x");
    assertEquals(key, ThreatUtils.configDedupeKey("h", "a", "c", "/claude/config/x"));
    assertNotEquals(key, ThreatUtils.configDedupeKey("h2", "a", "c", "/claude/config/x"));
    assertNotEquals(key, ThreatUtils.configDedupeKey("h", "a2", "c", "/claude/config/x"));
    assertNotEquals(key, ThreatUtils.configDedupeKey("h", "a", "c2", "/claude/config/x"));
    assertNotEquals(key, ThreatUtils.configDedupeKey("h", "a", "c", "/claude/config/y"));
  }

  @Test
  void dedupeKeyTreatsNullPartsAsEmpty() {
    assertEquals("|||", ThreatUtils.configDedupeKey(null, null, null, null));
  }

  @Test
  void evalTypeConditionsMatchByFieldAndNeverByRegex() {
    assertEquals(Collections.singletonList(new Document("evalType", "SKILL")), ThreatUtils.evalTypeConditions("only", null));
    assertEquals(Collections.singletonList(new Document("evalType", "CONFIG")), ThreatUtils.evalTypeConditions(null, "only"));
    assertEquals(
        Collections.singletonList(new Document("evalType", new Document("$nin", Arrays.asList("SKILL", "CONFIG")))),
        ThreatUtils.evalTypeConditions("exclude", "exclude"));
    assertEquals(
        Collections.singletonList(new Document("evalType", new Document("$nin", Collections.singletonList("SKILL")))),
        ThreatUtils.evalTypeConditions("exclude", null));
    assertTrue(ThreatUtils.evalTypeConditions(null, null).isEmpty());
  }

  @Test
  void coverIndexIsHintedOnlyWhenEveryFilteredFieldIsInIt() {
    Document plain = new Document("contextSource", "ENDPOINT").append("status", "ACTIVE")
        .append("detectedAt", new Document("$gte", 1).append("$lte", 2))
        .append("evalType", new Document("$ne", "SKILL"));
    assertTrue(ThreatUtils.filtersOnlyCoverFields(plain));
    assertTrue(ThreatUtils.filtersOnlyCoverFields(new Document("$and", Arrays.asList(plain, new Document("evalType", "CONFIG")))));
    assertTrue(ThreatUtils.filtersOnlyCoverFields(new Document(plain).append("filterId", new Document("$in", Arrays.asList("a")))));
    assertFalse(ThreatUtils.filtersOnlyCoverFields(new Document(plain).append("country", "IN")));
    assertFalse(ThreatUtils.filtersOnlyCoverFields(new Document("$and", Arrays.asList(plain, new Document("$or", Arrays.asList(new Document("host", "h")))))));
  }
}
