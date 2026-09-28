package com.akto.service.insights.narrative;

import com.akto.dto.insights.InsightNarrativeCache;
import com.akto.gpt.handlers.gpt_prompts.AgenticInsightNarrativeHandler;
import com.akto.gpt.handlers.gpt_prompts.AbstractGroundedNarrativeHandler;
import com.akto.service.insights.InsightResult;
import com.mongodb.BasicDBObject;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * ARGUS_POSTURE narrative strategy — one title/whyItMatters/remediation rewrite per Finding,
 * grounded in that finding's own real evidence rows (e.g. AgentRedTeamFindingsProvider's
 * "redTeamConversations" table). See AgenticInsightNarrativeHandler for the prompt/validation
 * this drives.
 */
public class AgenticNarrativeStrategy implements InsightNarrativeStrategy {

    private final AgenticInsightNarrativeHandler handler = new AgenticInsightNarrativeHandler();

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

        List<BasicDBObject> findings = new ArrayList<>();
        for (InsightResult.Finding f : r.getFindings()) {
            List<Map<String, Object>> conversationEvidence = new ArrayList<>();
            for (InsightResult.Evidence e : r.getEvidence()) {
                if (e.getRows() == null) continue;
                for (Map<String, Object> row : e.getRows()) {
                    // Every finding-grounding evidence row (see AgentRedTeamFindingsProvider's own
                    // "redTeamConversations" table) carries the agent's display name in "agent" —
                    // matched back to this finding's own agentName, not the evidence table's id, so
                    // a future second grounding table needs no change here.
                    if (f.getAgentName() != null && f.getAgentName().equals(row.get("agent"))) {
                        conversationEvidence.add(row);
                    }
                }
            }
            findings.add(new BasicDBObject("id", f.getId())
                    .append("agentName", f.getAgentName())
                    .append("environment", f.getEnvironment())
                    .append("resource", f.getResource())
                    .append("severity", f.getSeverity())
                    .append("draftTitle", f.getTitle() != null ? f.getTitle() : "")
                    .append("draftWhy", f.getWhyItMatters() != null ? f.getWhyItMatters() : "")
                    .append("draftRemediation", f.getRemediation() != null ? f.getRemediation() : "")
                    .append("conversationEvidence", conversationEvidence));
        }

        return new BasicDBObject("insightId", r.getInsightId())
                .append("metrics", metrics)
                .append("evidence", evidence)
                .append("caveats", r.getCaveats())
                .append("dataGaps", gaps)
                .append("findings", findings);
    }

    @Override
    public String promptTag() { return "agentic"; }

    @Override
    public BasicDBObject generate(BasicDBObject narrativeInput) {
        BasicDBObject input = new BasicDBObject(AbstractGroundedNarrativeHandler.NARRATIVE_INPUT, narrativeInput.toJson());
        return handler.handle(input);
    }

    /** Each Finding's own draft title/whyItMatters/remediation is the guaranteed fallback — only
     *  overwritten when the model actually returned a non-empty rewrite for that exact id. A
     *  finding the model dropped, or an id it invented, changes nothing (silently ignored — the
     *  invented-id case can't reach here at all, since AgenticInsightNarrativeHandler doesn't
     *  validate id membership itself; the loop below only ever finds a match for a real id). */
    @Override
    @SuppressWarnings("unchecked")
    public void apply(InsightResult r, BasicDBObject output) {
        r.setMarkdown(output.getString("markdown"));
        Object rawFindings = output.get("findings");
        if (!(rawFindings instanceof List)) return;
        for (Object o : (List<?>) rawFindings) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> fo = (Map<String, Object>) o;
            Object id = fo.get("id");
            if (id == null) continue;
            for (InsightResult.Finding f : r.getFindings()) {
                if (!id.toString().equals(f.getId())) continue;
                String title = str(fo.get("title"));
                String why = str(fo.get("whyItMatters"));
                String remediation = str(fo.get("remediation"));
                if (title != null && !title.isEmpty()) f.setTitle(title);
                if (why != null && !why.isEmpty()) f.setWhyItMatters(why);
                if (remediation != null && !remediation.isEmpty()) f.setRemediation(remediation);
                break;
            }
        }
    }

    private String str(Object o) { return o == null ? null : o.toString(); }

    @Override
    public InsightNarrativeCache toCache(String fingerprint, InsightResult r, int providerVersion,
                                          BasicDBObject output, long generatedAt, Date expiresAt) {
        InsightNarrativeCache cache = new InsightNarrativeCache(fingerprint, r.getInsightId(), providerVersion,
                output.getString("markdown"), null, null, null, generatedAt, expiresAt);
        cache.setNarrativeFindings(new BasicDBObject("findings", output.get("findings")).toJson());
        return cache;
    }

    @Override
    public BasicDBObject fromCache(InsightNarrativeCache cached) {
        BasicDBObject out = new BasicDBObject("markdown", cached.getNarrativeMarkdown());
        String findingsJson = cached.getNarrativeFindings();
        List<BasicDBObject> findings = new ArrayList<>();
        if (findingsJson != null && !findingsJson.isEmpty()) {
            try {
                Document doc = Document.parse(findingsJson);
                List<Document> rawFindings = doc.getList("findings", Document.class);
                if (rawFindings != null) {
                    for (Document d : rawFindings) findings.add(new BasicDBObject(d));
                }
            } catch (Exception ignored) {
                // A cached doc that fails to parse just replays with no findings rewrites —
                // every Finding keeps its Java draft, same as a cache miss that failed generation.
            }
        }
        out.put("findings", findings);
        return out;
    }
}
