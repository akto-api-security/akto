package com.akto.service.ask;

import com.akto.dao.insights.InsightNarrativeCacheDao;
import com.akto.dto.insights.InsightNarrativeCache;
import com.akto.gpt.handlers.gpt_prompts.AbstractGroundedNarrativeHandler;
import com.akto.gpt.handlers.gpt_prompts.AskTileCurationHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.AskOverlayResponse;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightTile;
import com.akto.service.insights.InsightUtil;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * "Fast data first, AI after" for the overlay's tiles: the frontend renders the computed tiles
 * immediately, then asks this for which 1-4 to lead with and the question each fires. Cached per
 * account and context source, keyed by the exact tile content, so a model call only happens when
 * the numbers the user sees actually change.
 */
public class AskTileCurationService {

    private static final LoggerMaker logger = new LoggerMaker(AskTileCurationService.class, LogDb.DASHBOARD);

    static final int MAX_PICKS = 4;
    static final int MIN_CANDIDATES = 2;
    static final int CURATION_VERSION = 1;
    private static final long CACHE_TTL_DAYS = 1;
    private static final String CACHE_ID = "ASK_TILE_CURATION";
    private static final String KEY_PICKS = "picks";

    private final AbstractGroundedNarrativeHandler handler;

    public AskTileCurationService() {
        this(new AskTileCurationHandler());
    }

    AskTileCurationService(AbstractGroundedNarrativeHandler handler) {
        this.handler = handler;
    }

    /** Must match how ask/transform.js ids tiles, or the frontend can't map a pick to a tile. */
    static String recommendationTileId(String recommendationId) {
        return "rec_" + recommendationId;
    }

    static String insightTileId(String insightId) {
        return "insight_" + insightId;
    }

    public AskTileCuration curate(InsightContext ctx, AskOverlayResponse overlay) {
        Map<String, BasicDBObject> candidates = candidateTiles(overlay);
        if (candidates.size() < MIN_CANDIDATES) {
            return new AskTileCuration(AskTileCuration.STATUS_SKIPPED, new ArrayList<>());
        }

        String input = new BasicDBObject("dashboard", dashboardLabel(ctx.getContextSource()))
                .append("maxPicks", MAX_PICKS)
                .append("tiles", new ArrayList<>(candidates.values()))
                .toJson();
        String fingerprint = InsightUtil.md5(ctx.getAccountId() + "|" + ctx.getContextSource() + "|"
                + CURATION_VERSION + "|" + input);

        List<AskTileCuration.Pick> cached = readCache(fingerprint);
        if (cached != null) return new AskTileCuration(AskTileCuration.STATUS_OK, cached);

        BasicDBObject out = handler.handle(new BasicDBObject(AbstractGroundedNarrativeHandler.NARRATIVE_INPUT, input));
        if (out.containsField("error")) {
            logger.error("Ask tile curation failed: " + out.getString("error"));
            return new AskTileCuration(AskTileCuration.STATUS_UNAVAILABLE, new ArrayList<>());
        }

        List<AskTileCuration.Pick> picks = keepKnownPicks(out.get(KEY_PICKS), candidates.keySet());
        if (picks.isEmpty()) {
            return new AskTileCuration(AskTileCuration.STATUS_UNAVAILABLE, new ArrayList<>());
        }
        writeCache(fingerprint, picks);
        return new AskTileCuration(AskTileCuration.STATUS_OK, picks);
    }

    /** Tiles worth ranking, keyed by tile id. A zero-count recommendation still renders as a tile,
     *  but there is nothing to act on, so the model never sees it. */
    static Map<String, BasicDBObject> candidateTiles(AskOverlayResponse overlay) {
        Map<String, BasicDBObject> tiles = new LinkedHashMap<>();
        if (overlay == null) return tiles;
        for (Recommendation r : overlay.getRecommendations()) {
            if (r == null || r.getId() == null || r.getCount() <= 0) continue;
            tiles.put(recommendationTileId(r.getId()), tile(recommendationTileId(r.getId()), r.getLabel(),
                    InsightUtil.grouped(r.getCount()), r.getSeverity(), r.getPromptTemplate()));
        }
        for (InsightTile t : overlay.getInsightTiles()) {
            if (t == null || t.getInsightId() == null) continue;
            String id = insightTileId(t.getInsightId());
            tiles.put(id, tile(id, t.getTitle(), insightValue(t), t.getSeverity(),
                    "Tell me more about \"" + t.getTitle() + "\" — " + (t.getHeadline() == null ? "" : t.getHeadline())));
        }
        return tiles;
    }

    private static BasicDBObject tile(String id, String label, String value, String severity, String defaultPrompt) {
        return new BasicDBObject("id", id)
                .append("label", label == null ? "" : label)
                .append("value", value == null ? "" : value)
                .append("severity", severity == null ? "NONE" : severity)
                .append("defaultPrompt", defaultPrompt == null ? "" : defaultPrompt);
    }

    private static String insightValue(InsightTile t) {
        if (t.getMetrics() != null && !t.getMetrics().isEmpty()) {
            InsightResult.Metric metric = t.getMetrics().get(0);
            if (metric != null && metric.getFormatted() != null) return metric.getFormatted();
        }
        return t.getHeadline();
    }

    /** The handler only checks the response's shape; which ids are real is decided here, against
     *  the tile set this request actually computed. Unknown and repeated ids are dropped. */
    @SuppressWarnings("unchecked")
    static List<AskTileCuration.Pick> keepKnownPicks(Object rawPicks, Set<String> knownTileIds) {
        List<AskTileCuration.Pick> picks = new ArrayList<>();
        if (!(rawPicks instanceof List)) return picks;
        Set<String> seen = new HashSet<>();
        for (Object o : (List<Object>) rawPicks) {
            if (!(o instanceof Map) || picks.size() >= MAX_PICKS) continue;
            Map<String, Object> pick = (Map<String, Object>) o;
            String id = String.valueOf(pick.get("id"));
            Object prompt = pick.get("prompt");
            if (!knownTileIds.contains(id) || !seen.add(id) || prompt == null) continue;
            picks.add(new AskTileCuration.Pick(id, prompt.toString()));
        }
        return picks;
    }

    static String dashboardLabel(CONTEXT_SOURCE contextSource) {
        if (contextSource == null) return "API Security";
        switch (contextSource) {
            case MCP: return "MCP Security";
            case GEN_AI: return "Gen AI";
            case AGENTIC: return "Agentic Security";
            case ENDPOINT: return "Endpoint Security";
            case DAST: return "DAST";
            case API:
            default: return "API Security";
        }
    }

    private List<AskTileCuration.Pick> readCache(String fingerprint) {
        try {
            InsightNarrativeCache cached = InsightNarrativeCacheDao.instance.get(fingerprint);
            if (cached == null || cached.getNarrativeMarkdown() == null) return null;
            JSONArray stored = new JSONArray(cached.getNarrativeMarkdown());
            List<AskTileCuration.Pick> picks = new ArrayList<>();
            for (int i = 0; i < stored.length(); i++) {
                JSONObject p = stored.getJSONObject(i);
                picks.add(new AskTileCuration.Pick(p.getString("tileId"), p.getString("prompt")));
            }
            return picks.isEmpty() ? null : picks;
        } catch (Exception e) {
            // An unreadable entry is treated as a miss and regenerated.
            return null;
        }
    }

    private void writeCache(String fingerprint, List<AskTileCuration.Pick> picks) {
        try {
            JSONArray stored = new JSONArray();
            for (AskTileCuration.Pick p : picks) {
                stored.put(new JSONObject().put("tileId", p.getTileId()).put("prompt", p.getPrompt()));
            }
            long now = System.currentTimeMillis() / 1000;
            Date expiresAt = new Date((now + TimeUnit.DAYS.toSeconds(CACHE_TTL_DAYS)) * 1000L);
            InsightNarrativeCacheDao.instance.put(new InsightNarrativeCache(fingerprint, CACHE_ID, CURATION_VERSION,
                    stored.toString(), null, null, null, now, expiresAt));
        } catch (Exception e) {
            logger.error("Ask tile curation cache write failed: " + e.getMessage());
        }
    }
}
