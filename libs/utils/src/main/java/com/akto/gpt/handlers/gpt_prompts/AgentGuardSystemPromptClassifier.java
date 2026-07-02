package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.validation.ValidationException;
import java.util.ArrayList;
import java.util.List;

public class AgentGuardSystemPromptClassifier extends AzureOpenAIPromptHandler {

    public static final String SYSTEM_PROMPT = "systemPrompt";
    public static final String SOURCE_KEY = "system_prompt";
    private static final int MAX_PROMPT_CHARS = 8000;

    @Override
    protected JSONObject getResponseFormat() {
        try { return new JSONObject("{\"type\":\"json_object\"}"); }
        catch (Exception e) { return null; }
    }

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        if (!queryData.containsKey(SYSTEM_PROMPT)) {
            throw new ValidationException("Missing mandatory param: " + SYSTEM_PROMPT);
        }
        String systemPrompt = queryData.getString(SYSTEM_PROMPT);
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
            throw new ValidationException(SYSTEM_PROMPT + " is empty.");
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String systemPrompt = AgentGuardIntentClassifier.truncate(
            queryData.getString(SYSTEM_PROMPT, ""), MAX_PROMPT_CHARS);

        StringBuilder sb = new StringBuilder();
        sb.append("You are labeling training data for a per-agent instruction-intent classifier ")
          .append("that guards an AI agent. Below is the agent's full SYSTEM_PROMPT — the instructions ")
          .append("it was configured with, which may describe several distinct tasks or capabilities ")
          .append("the agent is allowed to perform.\n\n")
          .append("Identify every DISTINCT task/capability the agent is instructed it may carry out. ")
          .append("For each one, do TWO things:\n\n")
          .append("1. Task intent: assign ONE fine-grained lowercase_snake_case label describing the ")
          .append("task's intent (e.g. \"delete_customer_record\", \"summarize_document\"). If a task is ")
          .append("real but doesn't fit a clean label, use \"").append(AgentGuardIntentClassifier.OTHER_CLASS).append("\".\n\n")
          .append("2. Risk category: one of ").append(AgentGuardIntentClassifier.RISK_CATEGORIES).append(" — pick the ")
          .append("highest-risk action that task actually performs.\n\n")
          .append("Rules:\n")
          .append("- taskIntent and riskCategory MUST be lowercase.\n")
          .append("- Only extract tasks the agent is actually instructed to be capable of — ignore tone, ")
          .append("persona, and formatting instructions.\n")
          .append("- Do not invent tasks that aren't grounded in the system prompt text.\n\n")
          .append("Return a JSON object with an 'intents' array, one object per distinct task:\n")
          .append("{\"intents\":[{\"taskIntent\":\"delete_customer_record\",\"riskCategory\":\"delete\",")
          .append("\"instructionText\":\"the line(s) from the prompt describing this task\"}]}\n\n")
          .append("SYSTEM_PROMPT:\n").append(systemPrompt);
        return sb.toString();
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        resp.put("intents", parseIntents(rawResponse));
        return resp;
    }

    /**
     * Classifies a full system prompt and returns its distinct task intents directly.
     * Returns an empty list on any validation/LLM/parsing failure.
     */
    @SuppressWarnings("unchecked")
    public List<BasicDBObject> classify(String systemPrompt) {
        BasicDBObject queryData = new BasicDBObject(SYSTEM_PROMPT, systemPrompt);
        BasicDBObject resp = handle(queryData);
        if (resp == null || resp.containsKey("error")) return new ArrayList<>();
        Object intents = resp.get("intents");
        return intents instanceof List ? (List<BasicDBObject>) intents : new ArrayList<>();
    }

    private List<BasicDBObject> parseIntents(String rawResponse) {
        List<BasicDBObject> out = new ArrayList<>();
        if (rawResponse == null || rawResponse.isEmpty() || "NOT_FOUND".equalsIgnoreCase(rawResponse)) return out;
        try {
            JSONObject json = new JSONObject(rawResponse);
            JSONArray arr = json.getJSONArray("intents");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item != null) out.add(parseItem(item));
            }
        } catch (Exception e) {
            logger.error("AgentGuardSystemPromptClassifier: failed to parse intents array: " + e.getMessage());
        }
        return out;
    }

    private static BasicDBObject parseItem(JSONObject json) {
        BasicDBObject resp = new BasicDBObject();
        resp.put("taskIntent", AgentGuardIntentClassifier.normalizeLabel(json.optString("taskIntent", "")));
        resp.put("riskCategory", AgentGuardIntentClassifier.normalizeLabel(json.optString("riskCategory", "")));
        resp.put("instructionText", json.optString("instructionText", ""));
        return resp;
    }
}
