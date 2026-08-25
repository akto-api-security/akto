package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONObject;

import javax.validation.ValidationException;
import java.util.List;

/**
 * Classifies which topic domains are on-domain for a given agent, judged against that
 * agent's own free-text description (ApiCollection.description) — never a hardcoded
 * business-domain list. Input: {description, domains:[...]}. Output: {"technology":"ON",
 * "sports":"OFF", ...}, one entry per input domain. Mirrors UserQueryTopicClassifier's
 * json_object + overridden handle() pattern so cache-miss calls don't dump agent
 * descriptions into the DASHBOARD log DB via the base class's verbose handle().
 */
public class AgentDomainClassifier extends AzureOpenAIPromptHandler {

    public static final String DESCRIPTION = "description";
    public static final String DOMAINS = "domains";

    private static final int MAX_DESCRIPTION_CHARS = 1500;

    @Override
    protected JSONObject getResponseFormat() {
        try { return new JSONObject("{\"type\":\"json_object\"}"); }
        catch (Exception e) { return null; }
    }

    @Override
    protected int getMaxTokens() { return 300; }

    @Override
    protected double getTemperature() { return 0.0; }

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        String description = queryData.getString(DESCRIPTION);
        if (description == null || description.trim().isEmpty()) {
            throw new ValidationException(DESCRIPTION + " is required");
        }
        Object domains = queryData.get(DOMAINS);
        if (!(domains instanceof List) || ((List<?>) domains).isEmpty()) {
            throw new ValidationException(DOMAINS + " must be a non-empty list");
        }
    }

    // handle() overridden (not just getPrompt/processResponse) to avoid the base
    // class's verbose logger.warn(queryData)/logger.warn(prompt) calls, matching
    // UserQueryTopicClassifier's precedent.
    @Override
    public BasicDBObject handle(BasicDBObject queryData) {
        try {
            validate(queryData);
            String rawResponse = call(getPrompt(queryData));
            return processResponse(rawResponse);
        } catch (ValidationException e) {
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Invalid input parameters.");
            return resp;
        } catch (Exception e) {
            logger.error("AgentDomainClassifier: " + e.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error: " + e.getMessage());
            return resp;
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String description = truncate(queryData.getString(DESCRIPTION, ""), MAX_DESCRIPTION_CHARS);
        @SuppressWarnings("unchecked")
        List<String> domains = (List<String>) queryData.get(DOMAINS);

        StringBuilder sb = new StringBuilder();
        sb.append("An AI agent is described below. Decide, for EACH listed topic domain, whether a ")
          .append("user asking about that domain is using this agent for its intended purpose (ON) ")
          .append("or for something unrelated (OFF).\n\n")
          .append("AGENT_DESCRIPTION: ").append(description).append("\n\n")
          .append("DOMAINS: ").append(String.join(", ", domains)).append("\n\n")
          .append("Rules:\n")
          .append("- Judge ONLY against AGENT_DESCRIPTION — do not use outside knowledge of what the domain usually means.\n")
          .append("- If the description does not clearly cover a domain, mark it OFF (default to OFF, not ON).\n")
          .append("- Return a JSON object with exactly one key per domain, value \"ON\" or \"OFF\":\n")
          .append("{\"").append(domains.get(0)).append("\":\"ON\"}\n");
        return sb.toString();
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject result = new BasicDBObject();
        if (rawResponse == null || rawResponse.isEmpty() || "NOT_FOUND".equalsIgnoreCase(rawResponse)) return result;
        try {
            JSONObject json = new JSONObject(rawResponse);
            for (java.util.Iterator<?> it = json.keys(); it.hasNext(); ) {
                String key = String.valueOf(it.next());
                String v = json.optString(key, "OFF").trim().toUpperCase();
                result.put(key, "ON".equals(v) ? "ON" : "OFF");
            }
        } catch (Exception e) {
            logger.error("AgentDomainClassifier: failed to parse response: " + e.getMessage());
        }
        return result;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
