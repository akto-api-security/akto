package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Argus (AGENTIC) "how agents were compromised" insight card — renders the real, validated
 * red-team verdict for the account's most critical open issues as a short attack FLOW (ordered
 * steps: what the attacker attempted, what the agent did, what actually happened), not a raw
 * request/response dump. Grounded in AgentConversationResult#validationMessage/remediationMessage
 * (the real judged outcome of an actual red-team conversation), paraphrased into steps rather than
 * quoted at length — the same "ground in real evidence, never invent, never quote raw payload at
 * length" contract the rest of this package's handlers use.
 *
 * Input: {"issues": [{"agentName","vulnType","severity","validationMessage","remediationMessage"}, ...]}
 * (1-2 issues, the account's most critical open red-team findings that had a real validated
 * conversation behind them). Output: {"flows": [{"agentName","steps":["<step1>","<step2>",...],
 * "impact","recommendation"}, ...]} — one flow per input issue, in the same order.
 */
public class ArgusAttackFlowNarrativeHandler extends AbstractGroundedNarrativeHandler {

    private static final int MIN_STEPS = 3;
    private static final int MAX_STEPS = 5;
    private static final int MAX_STEP_WORDS = 30;
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
            JSONArray flowsIn = json.optJSONArray("flows");
            if (flowsIn == null || flowsIn.length() == 0) { resp.put("error", "no flows"); return resp; }

            Set<String> unknownLiterals = new HashSet<>();
            List<BasicDBObject> flowsOut = new ArrayList<>();
            for (int i = 0; i < flowsIn.length(); i++) {
                JSONObject f = flowsIn.optJSONObject(i);
                if (f == null) continue;
                String agentName = f.optString("agentName", "");
                if (agentName.isEmpty()) continue; // an agent name the model invented/omitted — drop this flow

                JSONArray stepsIn = f.optJSONArray("steps");
                if (stepsIn == null || stepsIn.length() < MIN_STEPS || stepsIn.length() > MAX_STEPS) {
                    resp.put("error", "flow for " + agentName + " must have " + MIN_STEPS + "-" + MAX_STEPS + " steps");
                    return resp;
                }
                List<String> steps = new ArrayList<>();
                for (int j = 0; j < stepsIn.length(); j++) {
                    String step = stepsIn.optString(j, "");
                    if (step.isEmpty()) continue;
                    if (MARKDOWN_LINK.matcher(step).find()) { resp.put("error", "a step contains a markdown link"); return resp; }
                    if (step.split("\\s+").length > MAX_STEP_WORDS) { resp.put("error", "a step is too long"); return resp; }
                    collectUnknownLiterals(step, allowedLiterals, unknownLiterals);
                    steps.add(step);
                }
                if (steps.size() < MIN_STEPS) { resp.put("error", "flow for " + agentName + " has too few usable steps"); return resp; }

                String impact = f.optString("impact", "");
                String recommendation = f.optString("recommendation", "");
                if (impact.isEmpty() || recommendation.isEmpty()) {
                    resp.put("error", "flow for " + agentName + " missing impact/recommendation");
                    return resp;
                }
                for (String field : new String[] { impact, recommendation }) {
                    if (MARKDOWN_LINK.matcher(field).find()) { resp.put("error", "contains a markdown link"); return resp; }
                    if (field.split("\\s+").length > MAX_FIELD_WORDS) { resp.put("error", "impact/recommendation too long"); return resp; }
                    collectUnknownLiterals(field, allowedLiterals, unknownLiterals);
                }

                flowsOut.add(new BasicDBObject("agentName", agentName)
                        .append("steps", steps)
                        .append("impact", impact)
                        .append("recommendation", recommendation));
            }

            if (!unknownLiterals.isEmpty()) {
                resp.put("error", "numbers not present in FACTS: " + String.join(", ", unknownLiterals));
                return resp;
            }
            if (flowsOut.isEmpty()) { resp.put("error", "no valid flows after validation"); return resp; }

            resp.put("flows", flowsOut);
            return resp;
        } catch (Exception e) {
            resp.put("error", "unparseable response");
            return resp;
        }
    }

    @Override
    String buildPrompt(JSONObject input, String rejectedNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are reconstructing, as a short step-by-step FLOW, how a real red-team attempt against an ")
          .append("AI agent succeeded — for a reader deciding which agent to fix first. You are a RENDERER, not ")
          .append("an analyst: each issue below already carries the real, human-validated verdict of what ")
          .append("happened (validationMessage) and the run's own suggested fix (remediationMessage). Your job is ")
          .append("to turn that verdict into a short ordered flow, not to invent a new one. Return JSON.\n\n")
          .append("HARD RULES:\n")
          .append("1. Every step must be grounded in that issue's own validationMessage — paraphrase it into a ")
          .append("flow, never invent detail beyond what it actually says, and never quote it verbatim at length.\n")
          .append("2. Never include raw request/response payloads, headers, or code — describe the ATTEMPT and ")
          .append("OUTCOME in plain language (e.g. \"the agent revealed internal configuration when asked an ")
          .append("indirect question\", not a literal transcript).\n")
          .append("3. Each flow needs ").append(MIN_STEPS).append("-").append(MAX_STEPS)
          .append(" steps in order: what the attacker attempted, how the agent responded, and what the ")
          .append("validated outcome was. Each step under ").append(MAX_STEP_WORDS).append(" words.\n")
          .append("4. Every number in your output MUST be copied verbatim from a \"formatted\" value in FACTS or ")
          .append("that issue's own fields. Never compute, sum, round, or estimate a number.\n")
          .append("5. Never name an agent that does not appear verbatim in that issue's own agentName.\n")
          .append("6. recommendation must be grounded in that issue's own remediationMessage, paraphrased.\n")
          .append("7. Never write a link, URL, or markdown.\n\n")
          .append("CURRENT_TIME: ").append(nowForPrompt()).append("\n\n")
          .append("ISSUES (each has the real validated verdict — reconstruct the flow from it):\n")
          .append(input.optJSONArray("issues")).append("\n\n")
          .append("Write one flow per issue, same order, each with:\n")
          .append("- agentName: copied verbatim from that issue's own agentName.\n")
          .append("- steps: ").append(MIN_STEPS).append("-").append(MAX_STEPS)
          .append(" short ordered strings (see rule 3).\n")
          .append("- impact: one sentence, under ").append(MAX_FIELD_WORDS).append(" words, on the concrete ")
          .append("consequence for that agent.\n")
          .append("- recommendation: one sentence, under ").append(MAX_FIELD_WORDS)
          .append(" words, an imperative instruction grounded in remediationMessage.\n\n")
          .append("Return exactly: {\"flows\": [{\"agentName\": \"<name>\", \"steps\": [\"<step>\", ...], ")
          .append("\"impact\": \"<text>\", \"recommendation\": \"<text>\"}, ...]}. This is a json response.\n");
        if (rejectedNote != null) {
            sb.append("\nYour previous attempt was rejected: ").append(rejectedNote)
              .append(". Return only numbers copied verbatim from FACTS/ISSUES, in every field.\n");
        }
        return sb.toString();
    }

    @Override
    Set<String> allowedLiterals(JSONObject input) {
        Set<String> out = new HashSet<>();
        JSONArray issues = input.optJSONArray("issues");
        if (issues != null) {
            for (int i = 0; i < issues.length(); i++) {
                JSONObject issue = issues.optJSONObject(i);
                if (issue == null) continue;
                addLiteralsFrom(issue.optString("agentName", ""), out);
                addLiteralsFrom(issue.optString("vulnType", ""), out);
                addLiteralsFrom(issue.optString("validationMessage", ""), out);
                addLiteralsFrom(issue.optString("remediationMessage", ""), out);
            }
        }
        return out;
    }
}
