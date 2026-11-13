package com.akto.threat.backend.utils;

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
  void evalTypeReadsNeedTheFlagEndpointContextAndNoSkillOnlyMode() {
    assertTrue(ThreatUtils.readsByEvalType("ENDPOINT", null, true));
    assertFalse(ThreatUtils.readsByEvalType("ENDPOINT", null, false));
    assertFalse(ThreatUtils.readsByEvalType("AGENTIC", null, true));
    assertFalse(ThreatUtils.readsByEvalType("ENDPOINT", "only", true));
    assertTrue(ThreatUtils.readsByEvalType("ENDPOINT", "exclude", true));
  }

  @Test
  void skillsAreAlwaysSubtractedAndConfigOnlyWhenExcluded() {
    assertEquals(Collections.singletonList("SKILL"), ThreatUtils.evalTypesExcludedFromCounts(null));
    assertEquals(Collections.singletonList("SKILL"), ThreatUtils.evalTypesExcludedFromCounts("only"));
    assertEquals(Arrays.asList("SKILL", "CONFIG"), ThreatUtils.evalTypesExcludedFromCounts("exclude"));
  }
}
