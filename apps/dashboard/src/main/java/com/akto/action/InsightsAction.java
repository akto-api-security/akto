package com.akto.action;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightId;
import com.akto.service.insights.InsightResult;
import com.akto.service.insights.InsightService;
import com.mongodb.BasicDBObject;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class InsightsAction extends AbstractThreatDetectionAction {

    @Getter
    private BasicDBObject response = new BasicDBObject();

    @Setter
    private int startTimestamp;

    @Setter
    private int endTimestamp;

    @Setter
    private String insightId;

    private final InsightService insightService = new InsightService();

    public String fetchInsightsList() {
        response = new BasicDBObject();
        InsightContext ctx = InsightContext.fromThreadLocals(startTimestamp, endTimestamp);
        List<InsightResult> insights = insightService.fetchList(ctx, this);
        response.put("insights", insights);
        return SUCCESS.toUpperCase();
    }

    public String fetchInsightDetail() {
        response = new BasicDBObject();
        InsightId id;
        try {
            id = InsightId.valueOf(insightId);
        } catch (Exception e) {
            addActionError("Unknown insightId: " + insightId);
            return ERROR.toUpperCase();
        }
        InsightContext ctx = InsightContext.fromThreadLocals(startTimestamp, endTimestamp);
        InsightResult result = insightService.fetchDetail(id, ctx, this);
        response.put("insight", result);
        return SUCCESS.toUpperCase();
    }
}
