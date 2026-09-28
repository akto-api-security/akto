package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * ENDPOINT/GUARDRAIL_VIOLATIONS insight narratives — concern/impact/remediation grounded in real
 * evidence rows. See AbstractGroundedNarrativeHandler for the shared retry loop and numeric-
 * literal guard this handler builds on, and AgenticInsightNarrativeHandler for the sibling
 * per-finding shape (Argus/ARGUS_POSTURE) that also builds on it.
 */
public class InsightNarrativeHandler extends AbstractGroundedNarrativeHandler {

    private static final int MAX_WORDS = 260;
    private static final int MAX_SUMMARY_FIELD_WORDS = 60;

    /** Package-private (not private): InsightNarrativeHandlerTest exercises the literal-rejection
     *  guard directly against hand-built responses, rather than only through a real `call()`. */
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
            String concern = json.optString("concern", "");
            String impact = json.optString("impact", "");
            String remediation = json.optString("remediation", "");

            if (narrative.isEmpty()) { resp.put("error", "no narrative"); return resp; }
            if (MARKDOWN_LINK.matcher(narrative).find()) { resp.put("error", "contains a markdown link"); return resp; }
            if (narrative.split("\\s+").length > MAX_WORDS) { resp.put("error", "too long"); return resp; }

            Set<String> unknownLiterals = new HashSet<>();
            collectUnknownLiterals(narrative, allowedLiterals, unknownLiterals);

            java.util.Map<String, String> summaryFields = new java.util.LinkedHashMap<>();
            summaryFields.put("concern", concern);
            summaryFields.put("impact", impact);
            summaryFields.put("remediation", remediation);
            for (java.util.Map.Entry<String, String> e : summaryFields.entrySet()) {
                String value = e.getValue();
                if (value.isEmpty()) continue; // model may leave one blank when there's genuinely nothing grounded to add
                if (value.split("\\s+").length > MAX_SUMMARY_FIELD_WORDS) {
                    resp.put("error", e.getKey() + " is too long"); return resp;
                }
                if (MARKDOWN_LINK.matcher(value).find()) { resp.put("error", e.getKey() + " contains a markdown link"); return resp; }
                collectUnknownLiterals(value, allowedLiterals, unknownLiterals);
            }
            if (!unknownLiterals.isEmpty()) {
                resp.put("error", "numbers not present in FACTS: " + String.join(", ", unknownLiterals));
                return resp;
            }

            resp.put("markdown", narrative);
            resp.put("concern", concern);
            resp.put("impact", impact);
            resp.put("remediation", remediation);
            return resp;
        } catch (Exception e) {
            resp.put("error", "unparseable response");
            return resp;
        }
    }

    /** Package-private (not private): pure string-building, no network call — same
     *  "test the pure piece directly" convention this repo already uses elsewhere (see
     *  InsightNarrativeHandlerTest). */
    @Override
    String buildPrompt(JSONObject input, String rejectedNote) {
        String severity = input.optString("severity", "");
        StringBuilder sb = new StringBuilder();
        sb.append("You are rendering a precomputed security finding into prose for a reader who needs to ")
          .append("decide what to do next, not just read what happened. You are a RENDERER, not an analyst ")
          .append("— every number below has already been computed in Java; your job is to make it specific, ")
          .append("concrete, and ACTION-DRIVEN, grounded in the real rows in EVIDENCE (actual hosts/users/")
          .append("topics/examples), not just the aggregate counts in FACTS. Return JSON.\n\n")
          .append("HARD RULES:\n")
          .append("1. Every number in your output (in every field) MUST be copied verbatim from a ")
          .append("\"formatted\" value in FACTS or a cell value in EVIDENCE. Never compute, sum, round, or ")
          .append("estimate a number.\n")
          .append("2. Never name an asset, user, team, server, tool, or topic that does not appear verbatim ")
          .append("in FACTS or EVIDENCE.\n")
          .append("3. Never write a link, URL, or call to action — those are rendered separately.\n")
          .append("4. Include every sentence in CAVEATS and every \"impact\" in DATA_GAPS, verbatim, ")
          .append("somewhere in narrative.\n")
          .append("5. If something is not in FACTS or EVIDENCE, say it is unavailable — never estimate it.\n")
          .append("6. SEVERITY below (if non-empty) is the real, Java-computed worst severity behind this ")
          .append("finding — let concern/impact read with that urgency (CRITICAL/HIGH: urgent, immediate; ")
          .append("MEDIUM/LOW: worth doing, not alarming). Never invent a severity or urgency that SEVERITY, ")
          .append("CAVEATS, and DATA_GAPS don't support — when SEVERITY is empty, stay neutral.\n")
          .append("7. FACTS/EVIDENCE hold raw Unix epoch seconds under keys like \"detectedAt\", \"firstSeen\", ")
          .append("\"lastSeen\", \"lastScannedAt\", \"timestamp\", or \"lastHit\" (a plain integer, e.g. ")
          .append("1758375000) — these are NOT counts. Never print one of these as a bare number. Describe ")
          .append("timing only in relative, qualitative words (\"recently\", \"earlier this month\", \"on its ")
          .append("most recent occurrence\", \"within this window\") using CURRENT_TIME below only to judge ")
          .append("roughly how far in the past it is — never state or compute a specific date, a day count, ")
          .append("or an age in days/weeks (that would be computing a new number, which rule 1 forbids).\n")
          .append("8. When a row in EVIDENCE has an \"evidenceSample\" field, that is the real, verbatim ")
          .append("intercepted request/response text behind that row — the strongest possible grounding for ")
          .append("WHY that specific row matters. Prefer it over the row's other fields when explaining a ")
          .append("row's importance, and pair it with that row's own \"policy\" field (the guardrail policy ")
          .append("that fired) to say what was detected, not just that something was. Never invent detail ")
          .append("beyond what evidenceSample actually shows, and never quote it verbatim at length — ")
          .append("paraphrase what it reveals in your own words.\n\n")
          .append("CURRENT_TIME: ").append(nowForPrompt()).append("\n\n")
          .append("SEVERITY: ").append(severity).append("\n\n")
          .append("FACTS: ").append(input.optJSONArray("metrics")).append("\n\n")
          .append("EVIDENCE: ").append(input.optJSONArray("evidence")).append("\n\n")
          .append("CAVEATS: ").append(input.optJSONArray("caveats")).append("\n\n")
          .append("DATA_GAPS: ").append(input.optJSONArray("dataGaps")).append("\n\n")
          .append("A provider-computed DRAFT is included below for concern/impact/remediation — it's ")
          .append("correct but generic (it only knows aggregate numbers, not the real rows in EVIDENCE). ")
          .append("Rewrite each one to reference specific rows from EVIDENCE where they exist (name the ")
          .append("actual host/user/topic/example), keeping the same underlying claim. If EVIDENCE has ")
          .append("nothing to add over the draft, you may return the draft unchanged, or \"\" if there is ")
          .append("no draft and nothing grounded to say.\n")
          .append("DRAFT_CONCERN: ").append(input.optString("draftConcern", "")).append("\n")
          .append("DRAFT_IMPACT: ").append(input.optString("draftImpact", "")).append("\n")
          .append("DRAFT_REMEDIATION: ").append(input.optString("draftRemediation", "")).append("\n\n")
          .append("Write four fields:\n")
          .append("- narrative: the detail behind concern/impact/remediation below, for a reader who wants the ")
          .append("specifics — not a restatement of them. 2-4 short sentences of flowing prose, each one naming ")
          .append("a specific fact from FACTS/EVIDENCE (its number and what it's about). Plain sentences only — ")
          .append("no markdown list syntax (no \"- \" or \"* \" bullets, they don't render as a list here, only ")
          .append("as a stray dash), no literal section labels like \"What we found\", \"Why it matters\", ")
          .append("\"Summary\", no markdown heading (#, ##). Under 200 words total, no emojis.\n")
          .append("- concern: one sentence, under 40 words, on what was specifically found — name real ")
          .append("entities from EVIDENCE where possible. Open with the severity word (e.g. \"A CRITICAL...\") ")
          .append("only when SEVERITY is non-empty — never state a severity otherwise.\n")
          .append("- impact: one to two sentences, under 40 words, on the concrete consequence of leaving ")
          .append("this unaddressed — name what actually breaks or who's exposed (from EVIDENCE/FACTS), not ")
          .append("generic risk language like \"could pose a risk.\"\n")
          .append("- remediation: one to two sentences, under 40 words, phrased as a direct instruction, not ")
          .append("a vague suggestion — start with an imperative verb (Review/Disable/Rotate/Escalate/Notify/")
          .append("Update/Contact) and name the specific policy, host, or device from EVIDENCE it applies to ")
          .append("wherever EVIDENCE names one.\n\n")
          .append("Return exactly: {\"narrative\": \"<markdown>\", \"concern\": \"<text>\", ")
          .append("\"impact\": \"<text>\", \"remediation\": \"<text>\"}. This is a json response.\n");
        if (rejectedNote != null) {
            sb.append("\nYour previous attempt was rejected: ").append(rejectedNote)
              .append(". Return only numbers copied verbatim from FACTS/EVIDENCE, in every field.\n");
        }
        return sb.toString();
    }

    /** Every numeric literal anywhere in FACTS/EVIDENCE/CAVEATS/DATA_GAPS/the DRAFT fields — i.e.
     *  everything actually embedded in the prompt the model sees (see buildPrompt). Deliberately
     *  scans whole serialized blocks rather than cherry-picking fields like "formatted": a metric's
     *  "label" ("more than 5 in 24h") or an evidence table's own title can carry a real number too,
     *  and the model can't tell those apart from a "formatted" value — it just sees text with a
     *  number in it. Field-by-field extraction only catches up with each new case one bug report at
     *  a time; scanning everything the prompt actually contains closes the whole class at once. */
    @Override
    Set<String> allowedLiterals(JSONObject input) {
        Set<String> out = new HashSet<>();
        for (String field : new String[] { "metrics", "evidence", "caveats", "dataGaps" }) {
            Object value = input.opt(field);
            if (value != null) addLiteralsFrom(value.toString(), out);
        }
        addLiteralsFrom(input.optString("draftConcern", ""), out);
        addLiteralsFrom(input.optString("draftImpact", ""), out);
        addLiteralsFrom(input.optString("draftRemediation", ""), out);
        return out;
    }
}
