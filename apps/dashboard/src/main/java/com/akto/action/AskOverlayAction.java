package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.ask.AskTileCuration;
import com.akto.service.ask.AskTileCurationService;
import com.akto.service.insights.AskOverlayResponse;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightService;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The "Ask Akto" overlay's round trips: a handful of always-on recommendation tiles (see
 * RecommendationCatalog), the CRITICAL/HIGH subset of the full insights engine (see
 * InsightService.buildAskOverlay), a small on-the-fly "what changed" feed, and — fetched
 * separately, after those render — the AI curation of those tiles. Which dashboard's tiles come
 * back is the request's own x-context-source (Context.contextSource), never a body field. Gated
 * identically to the insights actions (featureLabel=ASK_GPT, matching api/ask_ai and
 * api/chatAndStore); per-group RBAC is layered on top inside InsightService.groupVisible.
 */
public class AskOverlayAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AskOverlayAction.class, LogDb.DASHBOARD);
    private final InsightService insightService = new InsightService();
    private final AskTileCurationService curationService = new AskTileCurationService();

    private static final int DEFAULT_TILE_LIMIT = 3;
    private static final int MAX_TILE_LIMIT = 12;
    private static final int DEFAULT_FEED_LIMIT = 10;
    private static final int MAX_FEED_LIMIT = 50;
    // Curation only ranks tiles; the change feed isn't part of what it sees.
    private static final int CURATION_FEED_LIMIT = 1;

    @Setter private int startTimestamp;
    @Setter private int endTimestamp;
    @Setter private List<String> groups;
    @Setter private int tileLimit;
    @Setter private int feedLimit;

    @Getter private AskOverlayResponse askOverlay;
    @Getter private AskTileCuration askOverlayCuration;

    public String fetchAskOverlay() {
        try {
            askOverlay = buildOverlay(clamp(feedLimit, DEFAULT_FEED_LIMIT, 1, MAX_FEED_LIMIT));
            return SUCCESS.toUpperCase();
        } catch (IllegalArgumentException e) {
            addActionError("Unknown insight group: " + e.getMessage());
            return ERROR.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Ask Akto overlay: " + e.getMessage());
            addActionError("Error building Ask Akto overlay: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    /** Recomputes the same tiles fetchAskOverlay returned (the insight bundle is 60s-cached) rather
     *  than accepting tiles from the client, so the model is only ever grounded in server-computed
     *  numbers. Called by the frontend after the tiles have already rendered. */
    public String fetchAskOverlayCuration() {
        try {
            InsightContext ctx = buildContext();
            askOverlayCuration = curationService.curate(ctx, buildOverlay(ctx, CURATION_FEED_LIMIT));
            return SUCCESS.toUpperCase();
        } catch (IllegalArgumentException e) {
            addActionError("Unknown insight group: " + e.getMessage());
            return ERROR.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error curating Ask Akto overlay tiles: " + e.getMessage());
            addActionError("Error curating Ask Akto overlay tiles: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    private AskOverlayResponse buildOverlay(int feed) {
        return buildOverlay(buildContext(), feed);
    }

    private AskOverlayResponse buildOverlay(InsightContext ctx, int feed) {
        Set<InsightId.Group> requestedGroups = parseGroups(groups, ctx.getContextSource());
        int tiles = clamp(tileLimit, DEFAULT_TILE_LIMIT, 1, MAX_TILE_LIMIT);
        return insightService.buildAskOverlay(ctx, requestedGroups, tiles, feed);
    }

    /** Empty/absent groups defaults to the current dashboard's own groups rather than all four —
     *  agentic AI-governance insight cards on the plain API dashboard (or vice versa) would be
     *  exactly as confusing as recommendations from the wrong dashboard. An explicit groups list
     *  always overrides this, and an unrecognized group name in it is a real 422. */
    private Set<InsightId.Group> parseGroups(List<String> requested, CONTEXT_SOURCE contextSource) {
        if (requested == null || requested.isEmpty()) {
            return defaultGroupsFor(contextSource);
        }
        Set<InsightId.Group> parsed = new HashSet<>();
        for (String g : requested) parsed.add(InsightId.Group.valueOf(g));
        return parsed;
    }

    /** Null (no x-context-source header) is treated as API, same as RecommendationCatalog.compute. */
    private Set<InsightId.Group> defaultGroupsFor(CONTEXT_SOURCE contextSource) {
        if (contextSource == null) contextSource = CONTEXT_SOURCE.API;
        switch (contextSource) {
            case AGENTIC:
            case MCP:
            case GEN_AI:
                return new HashSet<>(Arrays.asList(InsightId.Group.ATLAS_DISCOVERY, InsightId.Group.GUARDRAIL_VIOLATIONS));
            case ENDPOINT:
                return new HashSet<>(Arrays.asList(InsightId.Group.ATLAS_DISCOVERY,InsightId.Group.GUARDRAIL_VIOLATIONS));
            case API:
            case DAST:
            default:
                return new HashSet<>(Arrays.asList(InsightId.Group.API_POSTURE, InsightId.Group.TESTING_POSTURE));
        }
    }

    private int clamp(int value, int defaultValue, int min, int max) {
        int v = value <= 0 ? defaultValue : value;
        return Math.max(min, Math.min(max, v));
    }

    private InsightContext buildContext() {
        return new InsightContext(Context.accountId.get(), Context.userId.get(), Context.contextSource.get(), startTimestamp, endTimestamp);
    }
}
