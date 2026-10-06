package com.akto.service.ask;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dao.insights.InsightNarrativeCacheDao;
import com.akto.gpt.handlers.gpt_prompts.AskTileCurationHandler;
import com.akto.service.insights.AskOverlayResponse;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightTile;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestAskTileCurationService extends MongoBasedTest {

    private AskTileCurationHandler handler;
    private AskTileCurationService service;

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        InsightNarrativeCacheDao.instance.getMCollection().deleteMany(Filters.empty());
        handler = mock(AskTileCurationHandler.class);
        service = new AskTileCurationService(handler);
    }

    private static InsightContext ctx(CONTEXT_SOURCE contextSource) {
        return new InsightContext(ACCOUNT_ID, 1, contextSource, 0, Context.now());
    }

    private static Recommendation rec(String id, long count, String severity) {
        return new Recommendation(id, id + " label", count, "count", severity, "Which of these " + count + " " + id + "?", "/route", Collections.emptyMap());
    }

    private static InsightTile insight(String insightId, String formatted, String headline) {
        InsightTile tile = new InsightTile();
        tile.setInsightId(insightId);
        tile.setTitle(insightId + " title");
        tile.setSeverity("HIGH");
        tile.setHeadline(headline);
        if (formatted != null) {
            tile.setMetrics(new ArrayList<>(Collections.singletonList(
                    new InsightResult.Metric("k", "label", 1, "count", formatted))));
        }
        return tile;
    }

    private static AskOverlayResponse overlay(List<Recommendation> recs, List<InsightTile> tiles) {
        AskOverlayResponse response = new AskOverlayResponse();
        response.setRecommendations(recs);
        response.setInsightTiles(tiles);
        return response;
    }

    private static AskOverlayResponse twoTileOverlay() {
        return overlay(Arrays.asList(rec("open_criticals", 12, "CRITICAL"), rec("never_tested", 1234, null)),
                new ArrayList<>());
    }

    private static BasicDBObject picks(String... idPromptPairs) {
        List<BasicDBObject> picks = new ArrayList<>();
        for (int i = 0; i < idPromptPairs.length; i += 2) {
            picks.add(new BasicDBObject("id", idPromptPairs[i]).append("prompt", idPromptPairs[i + 1]));
        }
        return new BasicDBObject("picks", picks);
    }

    // ── candidateTiles ──────────────────────────────────────────────────────────────────

    @Test
    public void testCandidateTiles_idsMatchFrontendScheme_valuesFormatted_zeroCountsExcluded() {
        AskOverlayResponse response = overlay(
                Arrays.asList(rec("open_criticals", 1234, "CRITICAL"), rec("never_tested", 0, null)),
                Arrays.asList(insight("AGING_OPEN_CRITICALS", "42%", "headline"), insight("NO_METRIC", null, "only headline")));

        Map<String, BasicDBObject> candidates = AskTileCurationService.candidateTiles(response);

        assertEquals(new HashSet<>(Arrays.asList("rec_open_criticals", "insight_AGING_OPEN_CRITICALS", "insight_NO_METRIC")),
                candidates.keySet());
        assertEquals("1,234", candidates.get("rec_open_criticals").getString("value"));
        assertEquals("42%", candidates.get("insight_AGING_OPEN_CRITICALS").getString("value"));
        assertEquals("only headline", candidates.get("insight_NO_METRIC").getString("value"));
        assertEquals("NONE", AskTileCurationService.candidateTiles(twoTileOverlay()).get("rec_never_tested").getString("severity"));
    }

    // ── curate ──────────────────────────────────────────────────────────────────────────

    @Test
    public void testCurate_fewerThanTwoCandidates_skippedWithoutModelCall() {
        AskOverlayResponse response = overlay(
                Arrays.asList(rec("open_criticals", 3, "CRITICAL"), rec("never_tested", 0, null)), new ArrayList<>());

        AskTileCuration result = service.curate(ctx(CONTEXT_SOURCE.API), response);

        assertEquals(AskTileCuration.STATUS_SKIPPED, result.getStatus());
        assertTrue(result.getPicks().isEmpty());
        verify(handler, never()).handle(any());
    }

    @Test
    public void testCurate_unknownAndDuplicateIdsDropped_orderKept() {
        when(handler.handle(any())).thenReturn(picks(
                "rec_never_tested", "Which never-tested APIs first?",
                "rec_made_up", "Invented tile",
                "rec_never_tested", "Duplicate",
                "rec_open_criticals", "Which of the 12 criticals are real?"));

        AskTileCuration result = service.curate(ctx(CONTEXT_SOURCE.API), twoTileOverlay());

        assertEquals(AskTileCuration.STATUS_OK, result.getStatus());
        assertEquals(2, result.getPicks().size());
        assertEquals("rec_never_tested", result.getPicks().get(0).getTileId());
        assertEquals("Which never-tested APIs first?", result.getPicks().get(0).getPrompt());
        assertEquals("rec_open_criticals", result.getPicks().get(1).getTileId());
    }

    @Test
    public void testCurate_moreThanMaxPicks_cappedAtMax() {
        List<Recommendation> recs = new ArrayList<>();
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            recs.add(rec("r" + i, 5, "HIGH"));
            pairs.add("rec_r" + i);
            pairs.add("prompt " + i);
        }
        when(handler.handle(any())).thenReturn(picks(pairs.toArray(new String[0])));

        AskTileCuration result = service.curate(ctx(CONTEXT_SOURCE.API), overlay(recs, new ArrayList<>()));

        assertEquals(AskTileCurationService.MAX_PICKS, result.getPicks().size());
    }

    @Test
    public void testCurate_modelError_unavailableAndNotCached() {
        when(handler.handle(any())).thenReturn(new BasicDBObject("error", "numbers not present in TILES: 99"));

        AskTileCuration first = service.curate(ctx(CONTEXT_SOURCE.API), twoTileOverlay());
        service.curate(ctx(CONTEXT_SOURCE.API), twoTileOverlay());

        assertEquals(AskTileCuration.STATUS_UNAVAILABLE, first.getStatus());
        assertTrue(first.getPicks().isEmpty());
        verify(handler, times(2)).handle(any());
    }

    @Test
    public void testCurate_onlyUnknownIds_unavailable() {
        when(handler.handle(any())).thenReturn(picks("rec_made_up", "Invented"));

        AskTileCuration result = service.curate(ctx(CONTEXT_SOURCE.API), twoTileOverlay());

        assertEquals(AskTileCuration.STATUS_UNAVAILABLE, result.getStatus());
    }

    @Test
    public void testCurate_sameTilesAndContextSource_servedFromCache() {
        when(handler.handle(any())).thenReturn(picks("rec_open_criticals", "Which of the 12 criticals are real?",
                "rec_never_tested", "Which never-tested APIs first?"));

        AskTileCuration first = service.curate(ctx(CONTEXT_SOURCE.API), twoTileOverlay());
        AskTileCuration second = service.curate(ctx(CONTEXT_SOURCE.API), twoTileOverlay());

        verify(handler, times(1)).handle(any());
        assertEquals(AskTileCuration.STATUS_OK, second.getStatus());
        assertEquals(first.getPicks().size(), second.getPicks().size());
        assertEquals("rec_open_criticals", second.getPicks().get(0).getTileId());
        assertEquals("Which of the 12 criticals are real?", second.getPicks().get(0).getPrompt());
    }

    @Test
    public void testCurate_changedNumbersOrContextSource_cacheMiss() {
        when(handler.handle(any())).thenReturn(picks("rec_open_criticals", "Which criticals first?"));

        service.curate(ctx(CONTEXT_SOURCE.API), twoTileOverlay());
        service.curate(ctx(CONTEXT_SOURCE.DAST), twoTileOverlay());
        service.curate(ctx(CONTEXT_SOURCE.API), overlay(
                Arrays.asList(rec("open_criticals", 13, "CRITICAL"), rec("never_tested", 1234, null)), new ArrayList<>()));

        verify(handler, times(3)).handle(any());
    }

    @Test
    public void testDashboardLabel_nullIsApiSecurity() {
        assertEquals("API Security", AskTileCurationService.dashboardLabel(null));
        assertEquals("Agentic Security", AskTileCurationService.dashboardLabel(CONTEXT_SOURCE.AGENTIC));
        assertEquals("Endpoint Security", AskTileCurationService.dashboardLabel(CONTEXT_SOURCE.ENDPOINT));
    }
}
