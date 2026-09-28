package com.akto.service.insights.narrative;

import com.akto.dto.insights.InsightNarrativeCache;
import com.akto.gpt.handlers.gpt_prompts.InsightNarrativeHandler;
import com.akto.service.insights.InsightResult;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ATLAS_DISCOVERY/GUARDRAIL_VIOLATIONS narrative strategy — concern/impact/remediation, exactly
 * the shape/logic InsightService owned inline before the ARGUS_POSTURE group needed a second one.
 * Moved here verbatim (see git history for the pre-move version) so both strategies live next to
 * each other instead of one inline and one in InsightService.
 */
public class EndpointNarrativeStrategy implements InsightNarrativeStrategy {

    private final InsightNarrativeHandler handler = new InsightNarrativeHandler();

    @Override
    public BasicDBObject buildNarrativeInput(InsightResult r) {
        List<BasicDBObject> metrics = new ArrayList<>();
        for (InsightResult.Metric m : r.getMetrics()) {
            metrics.add(new BasicDBObject("key", m.getKey()).append("label", m.getLabel()).append("formatted", m.getFormatted()));
        }
        List<BasicDBObject> evidence = new ArrayList<>();
        for (InsightResult.Evidence e : r.getEvidence()) {
            evidence.add(new BasicDBObject("id", e.getId()).append("title", e.getTitle())
                    .append("rows", e.getRows()).append("totalRowCount", e.getTotalRowCount()));
        }
        List<BasicDBObject> gaps = new ArrayList<>();
        for (InsightResult.Gap g : r.getDataGaps()) {
            gaps.add(new BasicDBObject("source", g.getSource()).append("reason", g.getReason()).append("impact", g.getImpact()));
        }
        return new BasicDBObject("insightId", r.getInsightId())
                .append("metrics", metrics)
                .append("evidence", evidence)
                .append("caveats", r.getCaveats())
                .append("dataGaps", gaps)
                .append("severity", r.getSeverity() != null ? r.getSeverity() : "")
                .append("draftConcern", r.getConcern() != null ? r.getConcern() : "")
                .append("draftImpact", r.getImpact() != null ? r.getImpact() : "")
                .append("draftRemediation", r.getRemediation() != null ? r.getRemediation() : "");
    }

    @Override
    public String promptTag() { return "endpoint"; }

    @Override
    public BasicDBObject generate(BasicDBObject narrativeInput) {
        BasicDBObject input = new BasicDBObject(InsightNarrativeHandler.NARRATIVE_INPUT, narrativeInput.toJson());
        return handler.handle(input);
    }

    /** The provider's own concern/impact/remediation are a guaranteed, deterministic fallback —
     *  only replace a field when the model actually returned something non-empty for it. */
    @Override
    public void apply(InsightResult r, BasicDBObject output) {
        r.setMarkdown(output.getString("markdown"));
        String concern = output.getString("concern");
        String impact = output.getString("impact");
        String remediation = output.getString("remediation");
        if (concern != null && !concern.isEmpty()) r.setConcern(concern);
        if (impact != null && !impact.isEmpty()) r.setImpact(impact);
        if (remediation != null && !remediation.isEmpty()) r.setRemediation(remediation);
    }

    @Override
    public InsightNarrativeCache toCache(String fingerprint, InsightResult r, int providerVersion,
                                          BasicDBObject output, long generatedAt, Date expiresAt) {
        return new InsightNarrativeCache(fingerprint, r.getInsightId(), providerVersion,
                output.getString("markdown"), output.getString("concern"), output.getString("impact"),
                output.getString("remediation"), generatedAt, expiresAt);
    }

    @Override
    public BasicDBObject fromCache(InsightNarrativeCache cached) {
        return new BasicDBObject("markdown", cached.getNarrativeMarkdown())
                .append("concern", cached.getNarrativeConcern())
                .append("impact", cached.getNarrativeImpact())
                .append("remediation", cached.getNarrativeRemediation());
    }
}
