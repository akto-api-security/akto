package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Argus (ARGUS_POSTURE) posture-finding narratives — one title/whyItMatters/remediation per
 * per-agent Finding, instead of ENDPOINT's single concern/impact/remediation. Builds on the same
 * AbstractGroundedNarrativeHandler retry loop and numeric-literal guard InsightNarrativeHandler
 * uses, with an added rule grounding each rewrite in that agent's own real red-team conversation
 * verdicts (see AgenticNarrativeStrategy#buildNarrativeInput for the input shape).
 *
 * Input adds a top-level "findings" array (each: id, agentName, environment, resource, severity,
 * draftTitle, draftWhy, draftRemediation, conversationEvidence[]) alongside the usual metrics/
 * evidence/caveats/dataGaps. Output: {"narrative": "<markdown>", "findings": [{"id", "title",
 * "whyItMatters", "remediation"}, ...]}. A finding the model omits, or whose id it invents, is
 * simply not applied — InsightService/AgenticNarrativeStrategy keeps that finding's Java draft.
 */
public class AgenticInsightNarrativeHandler extends AbstractGroundedNarrativeHandler {

    private static final int MAX_NARRATIVE_WORDS = 400;
    private static final int MAX_FIELD_WORDS = 60;
    private static final int MAX_TITLE_WORDS = 12;

    @Override
    BasicDBObject validateAndBuild(String rawResponse, Set<String> allowedLiterals) {
        BasicDBObject resp = new BasicDBObject();
        if (rawResponse == null || rawResponse.isEmpty() || "NOT_FOUND".equalsIgnoreCase(rawResponse)) {
            resp.put("error", "empty response");
            return resp;
        }
        try {
            JSONObject json = new JSONObject(rawResponse);
            String narrative = json.optString("narrative", "");
            if (narrative.isEmpty()) { resp.put("error", "no narrative"); return resp; }
            if (MARKDOWN_LINK.matcher(narrative).find()) { resp.put("error", "contains a markdown link"); return resp; }
            if (narrative.split("\\s+").length > MAX_NARRATIVE_WORDS) { resp.put("error", "narrative too long"); return resp; }

            Set<String> unknownLiterals = new HashSet<>();
            collectUnknownLiterals(narrative, allowedLiterals, unknownLiterals);

            JSONArray findingsIn = json.optJSONArray("findings");
            java.util.List<BasicDBObject> findingsOut = new java.util.ArrayList<>();
            if (findingsIn != null) {
                for (int i = 0; i < findingsIn.length(); i++) {
                    JSONObject f = findingsIn.optJSONObject(i);
                    if (f == null) continue;
                    String id = f.optString("id", "");
                    if (id.isEmpty()) continue; // an id the model invented/omitted — silently dropped, draft kept by the caller

                    String title = f.optString("title", "");
                    String whyItMatters = f.optString("whyItMatters", "");
                    String remediation = f.optString("remediation", "");

                    for (String field : new String[] { title, whyItMatters, remediation }) {
                        if (field.isEmpty()) continue;
                        if (MARKDOWN_LINK.matcher(field).find()) { resp.put("error", "finding " + id + " contains a markdown link"); return resp; }
                        collectUnknownLiterals(field, allowedLiterals, unknownLiterals);
                    }
                    if (!title.isEmpty() && title.split("\\s+").length > MAX_TITLE_WORDS) {
                        resp.put("error", "finding " + id + " title too long"); return resp;
                    }
                    if (!whyItMatters.isEmpty() && whyItMatters.split("\\s+").length > MAX_FIELD_WORDS) {
                        resp.put("error", "finding " + id + " whyItMatters too long"); return resp;
                    }
                    if (!remediation.isEmpty() && remediation.split("\\s+").length > MAX_FIELD_WORDS) {
                        resp.put("error", "finding " + id + " remediation too long"); return resp;
                    }

                    BasicDBObject out = new BasicDBObject("id", id);
                    if (!title.isEmpty()) out.put("title", title);
                    if (!whyItMatters.isEmpty()) out.put("whyItMatters", whyItMatters);
                    if (!remediation.isEmpty()) out.put("remediation", remediation);
                    findingsOut.add(out);
                }
            }

            if (!unknownLiterals.isEmpty()) {
                resp.put("error", "numbers not present in FACTS: " + String.join(", ", unknownLiterals));
                return resp;
            }

            resp.put("markdown", narrative);
            resp.put("findings", findingsOut);
            return resp;
        } catch (Exception e) {
            resp.put("error", "unparseable response");
            return resp;
        }
    }

    String buildPrompt(JSONObject input, String rejectedNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are rendering precomputed AI-agent security posture findings into prose for a reader ")
          .append("deciding which agent to fix first. You are a RENDERER, not an analyst — every number below ")
          .append("has already been computed in Java; your job is to make each finding specific and ")
          .append("ACTION-DRIVEN, grounded in the real evidence/conversation rows for that agent, not just the ")
          .append("aggregate counts. Return JSON.\n\n")
          .append("HARD RULES:\n")
          .append("1. Every number in your output MUST be copied verbatim from a \"formatted\" value in FACTS, ")
          .append("a cell in EVIDENCE, or a finding's own draft/conversationEvidence fields. Never compute, sum, ")
          .append("round, or estimate a number.\n")
          .append("2. Never name an agent, host, policy, or topic that does not appear verbatim in FACTS, ")
          .append("EVIDENCE, or that finding's own fields.\n")
          .append("3. Never write a link, URL, or call to action — those are rendered separately.\n")
          .append("4. Include every sentence in CAVEATS and every \"impact\" in DATA_GAPS, verbatim, somewhere ")
          .append("in narrative.\n")
          .append("5. SEVERITY on each finding is the real, Java-computed severity — let title/whyItMatters read ")
          .append("with that urgency (CRITICAL/HIGH: urgent; MEDIUM/LOW: worth doing, not alarming). Never invent ")
          .append("a severity a finding doesn't have.\n")
          .append("6. Epoch-seconds fields (lastSeen, timestamp, detectedAt) are NOT counts — never print one as ")
          .append("a bare number; describe timing only in relative words (\"recently\", \"this window\") using ")
          .append("CURRENT_TIME below only to judge roughly how far in the past it is.\n")
          .append("7. Each finding's \"conversationEvidence\" (when present) is the REAL, validated red-team ")
          .append("verdict of what an attack against that agent actually achieved (validationMessage) and the ")
          .append("run's own suggested fix (remediationMessage). This is the strongest possible grounding for ")
          .append("whyItMatters/remediation — prefer it over the finding's own generic draft, paraphrase it in ")
          .append("your own words, never quote it verbatim at length, and never invent detail beyond what it ")
          .append("actually says.\n")
          .append("8. Every finding's title MUST contain that finding's own agentName verbatim, at most ")
          .append("12 words, in the shape \"<agent> can/has <capability or gap>\" — never a generic title with ")
          .append("no agent named.\n\n")
          .append("CURRENT_TIME: ").append(nowForPrompt()).append("\n\n")
          .append("FACTS: ").append(input.optJSONArray("metrics")).append("\n\n")
          .append("EVIDENCE: ").append(input.optJSONArray("evidence")).append("\n\n")
          .append("CAVEATS: ").append(input.optJSONArray("caveats")).append("\n\n")
          .append("DATA_GAPS: ").append(input.optJSONArray("dataGaps")).append("\n\n")
          .append("FINDINGS (each has a draft title/whyItMatters/remediation — correct but generic; rewrite ")
          .append("using that finding's own conversationEvidence where present, keeping the same underlying ")
          .append("claim; return the draft unchanged if there's nothing grounded to add):\n")
          .append(input.optJSONArray("findings")).append("\n\n")
          .append("Write:\n")
          .append("- narrative: 2-4 short sentences of flowing prose covering the FINDINGS as a whole (which ")
          .append("agents, what kind of gaps), naming specific facts from FACTS/EVIDENCE. Plain sentences only ")
          .append("— no markdown list syntax, no section labels, no heading. Under 200 words, no emojis.\n")
          .append("- findings: one object per input finding id, each with:\n")
          .append("  - title: under 12 words, containing that finding's agentName verbatim.\n")
          .append("  - whyItMatters: one sentence, under 40 words, on the concrete consequence for that agent.\n")
          .append("  - remediation: one to two sentences, under 40 words, an imperative instruction naming ")
          .append("that agent.\n\n")
          .append("Return exactly: {\"narrative\": \"<markdown>\", \"findings\": [{\"id\": \"<id>\", ")
          .append("\"title\": \"<text>\", \"whyItMatters\": \"<text>\", \"remediation\": \"<text>\"}, ...]}. ")
          .append("This is a json response.\n");
        if (rejectedNote != null) {
            sb.append("\nYour previous attempt was rejected: ").append(rejectedNote)
              .append(". Return only numbers copied verbatim from FACTS/EVIDENCE/FINDINGS, in every field.\n");
        }
        return sb.toString();
    }

    /** Every numeric literal in FACTS/EVIDENCE/CAVEATS/DATA_GAPS and every finding's own draft/
     *  conversationEvidence fields — i.e. everything buildPrompt actually embeds. Same
     *  whole-block-scanning rationale as InsightNarrativeHandler#allowedLiterals. */
    Set<String> allowedLiterals(JSONObject input) {
        Set<String> out = new HashSet<>();
        for (String field : new String[] { "metrics", "evidence", "caveats", "dataGaps" }) {
            Object value = input.opt(field);
            if (value != null) addLiteralsFrom(value.toString(), out);
        }
        JSONArray findings = input.optJSONArray("findings");
        if (findings != null) {
            for (int i = 0; i < findings.length(); i++) {
                JSONObject f = findings.optJSONObject(i);
                if (f == null) continue;
                addLiteralsFrom(f.optString("draftTitle", ""), out);
                addLiteralsFrom(f.optString("draftWhy", ""), out);
                addLiteralsFrom(f.optString("draftRemediation", ""), out);
                JSONArray convEvidence = f.optJSONArray("conversationEvidence");
                if (convEvidence != null) addLiteralsFrom(convEvidence.toString(), out);
            }
        }
        return out;
    }
}
