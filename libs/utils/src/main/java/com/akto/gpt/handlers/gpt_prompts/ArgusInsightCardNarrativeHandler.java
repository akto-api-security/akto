package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * Argus (AGENTIC) posture insight cards — a short AI write-up per card (red-team breakdown,
 * guardrail breakdown/hotspot, observability), grounded in the small set of Java-computed facts
 * each card carries PLUS real supporting text (a vulnerability template's own description/impact,
 * a guardrail policy's actual configuration, a validated red-team conversation's real outcome —
 * see each card-builder method in ArgusPostureService for what it puts in "context"). Much
 * simpler than the old per-finding rewrite handler this replaced: three short strings in, three
 * short strings out, same retry/literal-guard machinery every AbstractGroundedNarrativeHandler
 * subclass shares.
 *
 * Input: {"facts": [{"key","label","formatted"}, ...], "context": [{"key","text"}, ...]}. "facts"
 * are numbers (grounds every digit in the output); "context" is real prose (grounds WHY a number
 * matters — never invented, never generic security boilerplate). Output:
 * {"summary": "<what the numbers show>", "impact": "<the real, specific reason this matters>",
 * "recommendation": "<one imperative next step>"}.
 */
public class ArgusInsightCardNarrativeHandler extends AbstractGroundedNarrativeHandler {

    private static final int MAX_FIELD_WORDS = 45;

    @Override
    BasicDBObject validateAndBuild(String rawResponse, Set<String> allowedLiterals) {
        BasicDBObject resp = new BasicDBObject();
        if (rawResponse == null || rawResponse.isEmpty() || "NOT_FOUND".equalsIgnoreCase(rawResponse)) {
            resp.put("error", "empty response");
            return resp;
        }
        try {
            JSONObject json = new JSONObject(rawResponse);
            String summary = json.optString("summary", "");
            String impact = json.optString("impact", "");
            String recommendation = json.optString("recommendation", "");
            if (summary.isEmpty() || impact.isEmpty() || recommendation.isEmpty()) {
                resp.put("error", "summary/impact/recommendation must all be non-empty");
                return resp;
            }

            Set<String> unknownLiterals = new HashSet<>();
            for (String field : new String[] { summary, impact, recommendation }) {
                if (MARKDOWN_LINK.matcher(field).find()) { resp.put("error", "contains a markdown link"); return resp; }
                if (field.split("\\s+").length > MAX_FIELD_WORDS) { resp.put("error", "a field is too long"); return resp; }
                collectUnknownLiterals(field, allowedLiterals, unknownLiterals);
            }
            if (!unknownLiterals.isEmpty()) {
                resp.put("error", "numbers not present in FACTS/CONTEXT: " + String.join(", ", unknownLiterals));
                return resp;
            }

            resp.put("summary", summary);
            resp.put("impact", impact);
            resp.put("recommendation", recommendation);
            return resp;
        } catch (Exception e) {
            resp.put("error", "unparseable response");
            return resp;
        }
    }

    @Override
    String buildPrompt(JSONObject input, String rejectedNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are rendering one precomputed AI-agent security posture card for a reader scanning a ")
          .append("dashboard. You are a RENDERER, not an analyst — every number below has already been computed ")
          .append("in Java, and CONTEXT below is real supporting text (a vulnerability template's own ")
          .append("description/impact, a guardrail policy's actual configuration, or a validated red-team ")
          .append("conversation's real outcome). Your job is to explain what the numbers mean using the REAL ")
          .append("reason from CONTEXT, never a generic restatement. Return JSON.\n\n")
          .append("HARD RULES:\n")
          .append("1. Every number in your output MUST be copied verbatim from a \"formatted\" value in FACTS, ")
          .append("or a number that appears verbatim in CONTEXT. Never compute, sum, round, or estimate a number.\n")
          .append("2. Never name an agent, policy, or topic that does not appear verbatim in FACTS or CONTEXT.\n")
          .append("3. \"impact\" MUST cite the SPECIFIC mechanism/reason from CONTEXT — e.g. what the ")
          .append("vulnerability actually lets an attacker do, what the guardrail policy is/isn't configured to ")
          .append("catch, or what a real conversation showed happened. If CONTEXT is empty for this card, state ")
          .append("only what the numbers themselves show — do not invent a reason.\n")
          .append("4. BANNED: generic security filler that could apply to any card — phrases like \"may lead to ")
          .append("unaddressed security threats\", \"lack of monitoring and response\", \"ensure proper ")
          .append("configuration\", \"could pose a risk\". Every sentence must be specific to THIS card's own ")
          .append("FACTS/CONTEXT, not a template that could be pasted onto a different card unchanged.\n")
          .append("5. Never write a link, URL, or markdown.\n")
          .append("6. Epoch-seconds fields are NOT counts — never print one as a bare number.\n\n")
          .append("CURRENT_TIME: ").append(nowForPrompt()).append("\n\n")
          .append("FACTS: ").append(input.optJSONArray("facts")).append("\n\n")
          .append("CONTEXT (the real reason, when present — ground \"impact\" in this, paraphrased, never quoted ")
          .append("at length):\n").append(input.optJSONArray("context")).append("\n\n")
          .append("Write three short fields, each under ").append(MAX_FIELD_WORDS).append(" words, plain sentences, ")
          .append("no markdown list syntax, no section labels, no emojis:\n")
          .append("- summary: what this card's numbers actually show, naming specific facts from FACTS.\n")
          .append("- impact: the SPECIFIC consequence, grounded in CONTEXT per rule 3 — not a restatement of ")
          .append("the summary, and never the banned filler from rule 4.\n")
          .append("- recommendation: one imperative next step naming the specific agent/policy/topic involved, ")
          .append("grounded in CONTEXT's own remediation text when present.\n\n")
          .append("Return exactly: {\"summary\": \"<text>\", \"impact\": \"<text>\", \"recommendation\": \"<text>\"}. ")
          .append("This is a json response.\n");
        if (rejectedNote != null) {
            sb.append("\nYour previous attempt was rejected: ").append(rejectedNote)
              .append(". Return only numbers copied verbatim from FACTS/CONTEXT.\n");
        }
        return sb.toString();
    }

    @Override
    Set<String> allowedLiterals(JSONObject input) {
        Set<String> out = new HashSet<>();
        Object facts = input.opt("facts");
        if (facts != null) addLiteralsFrom(facts.toString(), out);
        JSONArray context = input.optJSONArray("context");
        if (context != null) {
            for (int i = 0; i < context.length(); i++) {
                JSONObject c = context.optJSONObject(i);
                if (c != null) addLiteralsFrom(c.optString("text", ""), out);
            }
        }
        return out;
    }
}
