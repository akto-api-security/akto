package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.guardrails.AtlasGuardrailsOverviewService;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightService;
import com.akto.util.TimePeriodUtils;
import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.Setter;

public class AtlasGuardrailsOverviewAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AtlasGuardrailsOverviewAction.class, LogDb.DASHBOARD);

    private final AtlasGuardrailsOverviewService overviewService = new AtlasGuardrailsOverviewService();
    private final InsightService insightService = new InsightService();

    @Getter @Setter private int startTimestamp;
    @Getter @Setter private int endTimestamp;
    @Getter @Setter private String timeZone;

    @Getter private BasicDBObject response = new BasicDBObject();

    public String fetchAtlasGuardrailsOverviewKpis() {
        try {
            InsightDataBundle bundle = insightService.getOrLoadBundle(buildCtx());
            this.response = overviewService.buildKpis(bundle);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Atlas guardrails overview KPIs: " + e.getMessage());
            addActionError("Failed to build Atlas guardrails overview KPIs");
            return ERROR.toUpperCase();
        }
    }

    public String fetchAtlasGuardrailsOverviewTriage() {
        try {
            InsightDataBundle bundle = insightService.getOrLoadBundle(buildCtx());
            this.response = overviewService.buildTriage(bundle);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Atlas guardrails overview triage: " + e.getMessage());
            addActionError("Failed to build Atlas guardrails overview triage");
            return ERROR.toUpperCase();
        }
    }

    public String fetchAtlasGuardrailsOverviewDecisions() {
        try {
            InsightDataBundle bundle = insightService.getOrLoadBundle(buildCtx());
            this.response = overviewService.buildDecisions(bundle, TimePeriodUtils.zoneOrDefault(timeZone));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Atlas guardrails overview decisions: " + e.getMessage());
            addActionError("Failed to build Atlas guardrails overview decisions");
            return ERROR.toUpperCase();
        }
    }

    private InsightContext buildCtx() {
        if (endTimestamp == 0) endTimestamp = Context.now();
        return new InsightContext(Context.accountId.get(), Context.userId.get(), Context.contextSource.get(),
                startTimestamp, endTimestamp);
    }

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }
}
