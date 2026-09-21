package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.AskOverlayResponse;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightService;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The "Ask Akto" overlay's one round trip: a handful of always-on recommendation tiles (see
 * RecommendationCatalog), the CRITICAL/HIGH subset of the full insights engine (see
 * InsightService.buildAskOverlay), and a small on-the-fly "what changed" feed. Gated identically
 * to the three existing insights actions (featureLabel=ASK_GPT here, matching what api/ask_ai and
 * api/chatAndStore themselves use) — per-group RBAC filtering is layered on top inside
 * InsightService.groupVisible, since the struts interceptor can only allow-or-deny the whole
 * request.
 */
public class AskOverlayAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AskOverlayAction.class, LogDb.DASHBOARD);
    private final InsightService insightService = new InsightService();

    private static final int DEFAULT_TILE_LIMIT = 3;
    private static final int MAX_TILE_LIMIT = 12;
    private static final int DEFAULT_FEED_LIMIT = 10;
    private static final int MAX_FEED_LIMIT = 50;

    @Setter private int startTimestamp;
    @Setter private int endTimestamp;
    @Setter private List<String> groups;
    @Setter private String domain;
    @Setter private int tileLimit;
    @Setter private int feedLimit;

    @Getter private AskOverlayResponse askOverlay;

    public String fetchAskOverlay() {
        try {
            CONTEXT_SOURCE parsedDomain = parseDomain(domain);
            Set<InsightId.Group> requestedGroups = parseGroups(groups, parsedDomain);
            int tiles = clamp(tileLimit, DEFAULT_TILE_LIMIT, 1, MAX_TILE_LIMIT);
            int feed = clamp(feedLimit, DEFAULT_FEED_LIMIT, 1, MAX_FEED_LIMIT);
            askOverlay = insightService.buildAskOverlay(buildContext(), parsedDomain, requestedGroups, tiles, feed);
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

    /** Missing/unrecognized domain defaults to API — presentation-only, not a security gate (the
     *  actual group RBAC is groupVisible(), unaffected by domain), so a bad value degrading to
     *  the API tile set rather than 422ing keeps this endpoint lenient for a caller that hasn't
     *  been updated yet. Reuses GlobalEnums.CONTEXT_SOURCE (API/MCP/GEN_AI/AGENTIC/DAST/ENDPOINT)
     *  rather than a parallel dashboard-only enum — RecommendationCatalog.compute() already folds
     *  MCP/GEN_AI into the same tile set as AGENTIC. */
    private CONTEXT_SOURCE parseDomain(String requested) {
        if (requested == null || requested.isEmpty()) return CONTEXT_SOURCE.API;
        try {
            return CONTEXT_SOURCE.valueOf(requested.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CONTEXT_SOURCE.API;
        }
    }

    /** Empty/absent groups defaults to the requesting domain's own groups rather than all four —
     *  showing agentic AI-governance insight cards on the plain API dashboard (or vice versa)
     *  would be exactly as confusing as recommendations from the wrong domain. Guardrail
     *  insights are included for BOTH Agentic and Endpoint, not just Agentic — an explicit
     *  groups list from the caller always overrides this default, and an unrecognized group
     *  name in that list is still a real 422, not a silently dropped entry. */
    private Set<InsightId.Group> parseGroups(List<String> requested, CONTEXT_SOURCE domain) {
        if (requested == null || requested.isEmpty()) {
            return defaultGroupsForDomain(domain);
        }
        Set<InsightId.Group> parsed = new HashSet<>();
        for (String g : requested) parsed.add(InsightId.Group.valueOf(g));
        return parsed;
    }

    private Set<InsightId.Group> defaultGroupsForDomain(CONTEXT_SOURCE domain) {
        switch (domain) {
            case AGENTIC:
            case MCP:
            case GEN_AI:
                return new HashSet<>(java.util.Arrays.asList(InsightId.Group.ATLAS_DISCOVERY, InsightId.Group.GUARDRAIL_VIOLATIONS));
            case ENDPOINT:
                return new HashSet<>(java.util.Collections.singletonList(InsightId.Group.GUARDRAIL_VIOLATIONS));
            case API:
            case DAST:
            default:
                return new HashSet<>(java.util.Arrays.asList(InsightId.Group.API_POSTURE, InsightId.Group.TESTING_POSTURE));
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
