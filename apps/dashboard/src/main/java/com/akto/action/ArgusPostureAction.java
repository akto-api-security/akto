package com.akto.action;

import com.akto.dao.context.Context;
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

import lombok.Getter;
import lombok.Setter;


public class ArgusPostureAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ArgusPostureAction.class, LogDb.DASHBOARD);

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

            if (!ArgusPostureService.DRILL_PROTECTION_COVERAGE.equals(drillId)) {
                addActionError("Unknown drill: " + drillId);
                return ERROR.toUpperCase();
            }

            InsightContext ctx = new InsightContext(accountId, userId, contextSource, startTimestamp, endTimestamp);
            InsightDataBundle bundle = insightService.getOrLoadBundle(ctx);

            this.postureDrill = argusPostureService.fetchProtectionCoverageDrill(bundle, environment, skip, limit);
            PostureDrillNarrativeService.attachNarrative(this.postureDrill, ctx, drillId, path,
                    "env=" + ArgusPostureService.environmentKey(environment));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Argus posture drill: " + e.getMessage());
            addActionError("Failed to build Argus posture drill");
            return ERROR.toUpperCase();
        }
    }

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }
}
