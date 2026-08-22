package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightService;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Atlas Discovery insights. fetchInsightsList is the fast, no-LLM path the "Insights"
 * button opens; fetchInsightDetail/refreshInsightNarrative are the one-insight,
 * one-LLM-call-on-cache-miss path. See InsightService for the orchestration.
 */
public class InsightsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InsightsAction.class, LogDb.DASHBOARD);
    private final InsightService insightService = new InsightService();

    @Setter private int startTimestamp;
    @Setter private int endTimestamp;
    @Setter private String insightId;

    @Getter private List<InsightResult> insights;
    @Getter private InsightResult insight;

    public String fetchInsightsList() {
        try {
            insights = insightService.listInsights(buildContext());
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching insights list: " + e.getMessage());
            addActionError("Error fetching insights list: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public String fetchInsightDetail() {
        return computeDetail(false);
    }

    public String refreshInsightNarrative() {
        return computeDetail(true);
    }

    private String computeDetail(boolean forceRefresh) {
        try {
            InsightId id = InsightId.valueOf(insightId);
            insight = insightService.getInsightDetail(buildContext(), id, forceRefresh);
            return SUCCESS.toUpperCase();
        } catch (IllegalArgumentException e) {
            addActionError("Unknown insightId: " + insightId);
            return ERROR.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching insight detail: " + e.getMessage());
            addActionError("Error fetching insight detail: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    private InsightContext buildContext() {
        return new InsightContext(Context.accountId.get(), Context.userId.get(), Context.contextSource.get(), startTimestamp, endTimestamp);
    }
}
