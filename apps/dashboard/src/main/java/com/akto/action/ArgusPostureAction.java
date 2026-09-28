package com.akto.action;

import com.akto.dao.AgenticPostureScoreHistoryDao;
import com.akto.dao.context.Context;
import com.akto.dto.AgenticPostureScoreHistory;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightService;
import com.akto.service.posture.ArgusPostureService;
import com.akto.service.posture.PostureDrillNarrativeService;
import com.akto.service.posture.PostureDrillResult;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class ArgusPostureAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ArgusPostureAction.class, LogDb.DASHBOARD);

    private static final int TREND_WINDOW_SECONDS = 30 * 86400;
    private static final int TREND_MAX_POINTS = 500;
    private static final int DELTA_LOOKBACK_SECONDS = 7 * 86400;

    private final ArgusPostureService argusPostureService = new ArgusPostureService();
    private final InsightService insightService = new InsightService();

    @Getter @Setter private int startTimestamp;
    @Getter @Setter private int endTimestamp;
    @Getter @Setter private String environment;
    @Getter @Setter private String drillId;
    @Getter @Setter private String path;
    @Getter @Setter private int skip;
    @Getter @Setter private int limit;

    @Getter private BasicDBObject response = new BasicDBObject();
    @Getter private PostureDrillResult postureDrill;

    public String fetchArgusPostureSummary() {
        try {
            if (endTimestamp == 0) endTimestamp = Context.now();

            final int accountId = Context.accountId.get();
            final Integer userId = Context.userId.get();
            final CONTEXT_SOURCE contextSource = Context.contextSource.get();

            InsightContext ctx = new InsightContext(accountId, userId, contextSource, startTimestamp, endTimestamp);
            InsightDataBundle bundle = insightService.getOrLoadBundle(ctx);

            this.response = argusPostureService.buildSummary(bundle, environment);
            response.put("postureScore", fetchPostureScore());
            response.put("highestRiskAgents", argusPostureService.buildHighestRiskAgents(bundle));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Argus posture summary: " + e.getMessage());
            addActionError("Failed to build Argus posture summary");
            return ERROR.toUpperCase();
        }
    }

    public String fetchArgusPostureDrill() {
        try {
            if (endTimestamp == 0) endTimestamp = Context.now();

            final int accountId = Context.accountId.get();
            final Integer userId = Context.userId.get();
            final CONTEXT_SOURCE contextSource = Context.contextSource.get();

            InsightContext ctx = new InsightContext(accountId, userId, contextSource, startTimestamp, endTimestamp);
            InsightDataBundle bundle = insightService.getOrLoadBundle(ctx);

            switch (drillId == null ? "" : drillId) {
                case ArgusPostureService.DRILL_PROTECTION_COVERAGE:
                    this.postureDrill = argusPostureService.fetchProtectionCoverageDrill(bundle, environment, skip, limit);
                    break;
                default:
                    addActionError("Unknown drill: " + drillId);
                    return ERROR.toUpperCase();
            }

            PostureDrillNarrativeService.attachNarrative(this.postureDrill, ctx, drillId, path,
                    "env=" + ArgusPostureService.environmentKey(environment));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Argus posture drill: " + e.getMessage());
            addActionError("Failed to build Argus posture drill");
            return ERROR.toUpperCase();
        }
    }

    // Latest cron-written history row is the current score; the 30-day window feeds the trend.
    private BasicDBObject fetchPostureScore() {
        int now = Context.now();
        List<AgenticPostureScoreHistory> trend = AgenticPostureScoreHistoryDao.instance.findAll(
                Filters.gte(AgenticPostureScoreHistory.COMPUTED_AT, now - TREND_WINDOW_SECONDS),
                0, TREND_MAX_POINTS, Sorts.ascending(AgenticPostureScoreHistory.COMPUTED_AT));
        List<AgenticPostureScoreHistory> weekAgo = AgenticPostureScoreHistoryDao.instance.findAll(
                Filters.lte(AgenticPostureScoreHistory.COMPUTED_AT, now - DELTA_LOOKBACK_SECONDS),
                0, 1, Sorts.descending(AgenticPostureScoreHistory.COMPUTED_AT));
        AgenticPostureScoreHistory latest = trend.isEmpty() ? null : trend.get(trend.size() - 1);
        return argusPostureService.buildPostureScore(latest, trend, weekAgo.isEmpty() ? null : weekAgo.get(0));
    }

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }
}
