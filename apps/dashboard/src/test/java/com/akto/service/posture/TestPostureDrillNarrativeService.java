package com.akto.service.posture;

import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightResult;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Logic-level coverage for {@link PostureDrillNarrativeService}'s own pure pieces —
 * {@code buildNarrativeInput}/{@code isEmpty}/{@code fingerprint} — same "test the pure builder
 * directly, not through the class's own Mongo/LLM plumbing" convention {@code TestPostureService}
 * already uses for {@code PostureService}. {@code attachNarrative}/{@code generateAndCache}
 * themselves (real cache reads/writes, a real LLM call) are deliberately NOT exercised here — see
 * this class's own javadoc for why that split exists.
 */
public class TestPostureDrillNarrativeService {

    private static PostureDrillResult emptyDrillResult() {
        return new PostureDrillResult();
    }

    private static PostureDrillResult drillResultWithRows() {
        PostureDrillResult r = new PostureDrillResult();
        r.setTitle("DLP incidents");
        List<InsightResult.Metric> summary = new ArrayList<>();
        summary.add(new InsightResult.Metric("incidents", "Incidents", 5, "count", "5"));
        r.setSummary(summary);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("username", "alice");
        row.put("incidents", 5);
        rows.add(row);
        r.setRows(rows);
        r.setTotal(1);
        return r;
    }

    /** A profile-layout drill has no top-level `rows` at all — its own facts/sections carry the
     *  grounded detail instead (see PostureDrillResult#layout's javadoc). */
    private static PostureDrillResult profileDrillResult() {
        PostureDrillResult r = new PostureDrillResult();
        r.setLayout("profile");
        r.setTitle("alice");

        List<PostureDrillResult.Fact> facts = new ArrayList<>();
        facts.add(new PostureDrillResult.Fact("Most used tool", "OpenAI", null));
        facts.add(new PostureDrillResult.Fact("Flagged actions", "3", null));
        r.setFacts(facts);

        PostureDrillResult.Section section = new PostureDrillResult.Section();
        section.setId("activity");
        section.setTitle("AI activity timeline");
        section.setKind("timeline");
        List<Map<String, Object>> sectionRows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("timestamp", 1_700_000_000);
        row.put("title", "Default-Customer PII");
        sectionRows.add(row);
        section.setRows(sectionRows);
        section.setTotal(1);
        r.setSections(Arrays.asList(section));
        return r;
    }

    // ── isEmpty ───────────────────────────────────────────────────────────────────

    @Test
    public void testIsEmpty_noMetricsRowsOrGaps() {
        BasicDBObject input = PostureDrillNarrativeService.buildNarrativeInput(emptyDrillResult(), "dlpIncidents", "");
        assertTrue(PostureDrillNarrativeService.isEmpty(input));
    }

    @Test
    public void testIsEmpty_falseWhenRowsPresent() {
        BasicDBObject input = PostureDrillNarrativeService.buildNarrativeInput(drillResultWithRows(), "dlpIncidents", "");
        assertFalse(PostureDrillNarrativeService.isEmpty(input));
    }

    @Test
    public void testIsEmpty_falseForProfileLayoutWithOnlyFactsAndSections() {
        // The whole reason facts/sections were folded into buildNarrativeInput: a profile level
        // has no top-level `rows`, so without this fold isEmpty would (wrongly) skip the LLM call
        // for every one of the risk-score breakdown's own 3rd-level pages.
        BasicDBObject input = PostureDrillNarrativeService.buildNarrativeInput(profileDrillResult(), "dlpIncidents", "dlpIncidents/deviceA");
        assertFalse(PostureDrillNarrativeService.isEmpty(input));
    }

    // ── buildNarrativeInput — facts/sections folding ─────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildNarrativeInput_foldsFactsIntoMetrics() {
        BasicDBObject input = PostureDrillNarrativeService.buildNarrativeInput(profileDrillResult(), "dlpIncidents", "dlpIncidents/deviceA");
        List<BasicDBObject> metrics = (List<BasicDBObject>) input.get("metrics");
        assertTrue(metrics.stream().anyMatch(m -> "Most used tool".equals(m.getString("label")) && "OpenAI".equals(m.getString("formatted"))));
        assertTrue(metrics.stream().anyMatch(m -> "Flagged actions".equals(m.getString("label")) && "3".equals(m.getString("formatted"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildNarrativeInput_foldsSectionRowsIntoEvidence() {
        BasicDBObject input = PostureDrillNarrativeService.buildNarrativeInput(profileDrillResult(), "dlpIncidents", "dlpIncidents/deviceA");
        List<BasicDBObject> evidence = (List<BasicDBObject>) input.get("evidence");
        assertEquals(1, evidence.size());
        BasicDBObject block = evidence.get(0);
        assertEquals("activity", block.getString("id"));
        assertEquals("AI activity timeline", block.getString("title"));
        assertEquals(1L, block.get("totalRowCount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBuildNarrativeInput_topLevelRowsStillFlowThrough() {
        // Non-profile levels (the generic table shape) keep working exactly as before this build —
        // folding facts/sections in is additive, not a replacement.
        BasicDBObject input = PostureDrillNarrativeService.buildNarrativeInput(drillResultWithRows(), "dlpIncidents", "");
        List<BasicDBObject> evidence = (List<BasicDBObject>) input.get("evidence");
        assertEquals(1, evidence.size());
        assertEquals("rows", evidence.get(0).getString("id"));
        assertEquals("DLP incidents", evidence.get(0).getString("title"));
    }

    @Test
    public void testBuildNarrativeInput_sectionWithNoRowsIsSkipped() {
        PostureDrillResult r = new PostureDrillResult();
        r.setLayout("profile");
        PostureDrillResult.Section empty = new PostureDrillResult.Section();
        empty.setId("devices");
        empty.setTitle("Devices");
        empty.setKind("table");
        empty.setRows(new ArrayList<>());
        r.setSections(Arrays.asList(empty));

        BasicDBObject input = PostureDrillNarrativeService.buildNarrativeInput(r, "vendorRisk", "vendorRisk/DeepSeek");
        assertTrue(PostureDrillNarrativeService.isEmpty(input)); // no facts, no rows anywhere -> nothing grounded
    }

    @Test
    public void testBuildNarrativeInput_severityPassedThroughOrBlank() {
        PostureDrillResult withSeverity = drillResultWithRows();
        withSeverity.setSeverity("CRITICAL");
        assertEquals("CRITICAL", PostureDrillNarrativeService.buildNarrativeInput(withSeverity, "dlpIncidents", "").getString("severity"));

        assertEquals("", PostureDrillNarrativeService.buildNarrativeInput(drillResultWithRows(), "dlpIncidents", "").getString("severity"));
    }

    // ── fingerprint ───────────────────────────────────────────────────────────────

    private static InsightContext ctx() {
        return new InsightContext(12345, 1, CONTEXT_SOURCE.AGENTIC, 1_000_000, 1_008_000);
    }

    @Test
    public void testFingerprint_sameInputsProduceSameFingerprint() {
        String a = PostureDrillNarrativeService.fingerprint(ctx(), "dlpIncidents", "dlpIncidents/deviceA");
        String b = PostureDrillNarrativeService.fingerprint(ctx(), "dlpIncidents", "dlpIncidents/deviceA");
        assertEquals(a, b);
    }

    @Test
    public void testFingerprint_differentPathProducesDifferentFingerprint() {
        // Real risk this build introduced: every entity under one sub-score must get its own
        // narrative, not the sub-score's own L2 narrative repeated for every device.
        String rootLevel = PostureDrillNarrativeService.fingerprint(ctx(), "riskScoreBreakdown", "dlpIncidents");
        String entityLevelA = PostureDrillNarrativeService.fingerprint(ctx(), "riskScoreBreakdown", "dlpIncidents/deviceA");
        String entityLevelB = PostureDrillNarrativeService.fingerprint(ctx(), "riskScoreBreakdown", "dlpIncidents/deviceB");
        assertNotEquals(rootLevel, entityLevelA);
        assertNotEquals(entityLevelA, entityLevelB);
    }

    @Test
    public void testFingerprint_differentDateRangeProducesDifferentFingerprint() {
        InsightContext rangeA = new InsightContext(12345, 1, CONTEXT_SOURCE.AGENTIC, 1_000_000, 1_008_000);
        InsightContext rangeB = new InsightContext(12345, 1, CONTEXT_SOURCE.AGENTIC, 2_000_000, 2_008_000);
        assertNotEquals(
                PostureDrillNarrativeService.fingerprint(rangeA, "riskScoreBreakdown", "dlpIncidents/deviceA"),
                PostureDrillNarrativeService.fingerprint(rangeB, "riskScoreBreakdown", "dlpIncidents/deviceA"));
    }
}
