package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.validation.ValidationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Suggests a starter guardrail policy for one agentic surface that has real harmful-topic
 * activity but no covering policy — GuardrailCoverageGapProvider's "create guardrail" CTA
 * prefill. Input: {hostName, harmfulTopics:["topic: lastReason", ...]}. Output field names
 * mirror GuardrailPolicies' own create fields (name/description/severity/behaviour/
 * deniedTopics) so the dashboard can drop the result straight into the create-policy form.
 */
public class GuardrailSuggestionClassifier extends AzureOpenAIPromptHandler {

    public static final String HOST_NAME = "hostName";
    public static final String HARMFUL_TOPICS = "harmfulTopics";

    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String SEVERITY = "severity";
    public static final String BEHAVIOUR = "behaviour";
    public static final String DENIED_TOPICS = "deniedTopics";

    private static final int MAX_HARMFUL_TOPICS_CHARS = 2000;

    @Override
    protected JSONObject getResponseFormat() {
        try { return new JSONObject("{\"type\":\"json_object\"}"); }
        catch (Exception e) { return null; }
    }

    @Override
    protected int getMaxTokens() { return 400; }

    @Override
    protected double getTemperature() { return 0.0; }

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        String hostName = queryData.getString(HOST_NAME);
        if (hostName == null || hostName.trim().isEmpty()) {
            throw new ValidationException(HOST_NAME + " is required");
        }
        Object harmfulTopics = queryData.get(HARMFUL_TOPICS);
        if (!(harmfulTopics instanceof List) || ((List<?>) harmfulTopics).isEmpty()) {
            throw new ValidationException(HARMFUL_TOPICS + " must be a non-empty list");
        }
    }

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
            logger.error("GuardrailSuggestionClassifier: " + e.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error: " + e.getMessage());
            return resp;
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String hostName = queryData.getString(HOST_NAME, "");
        @SuppressWarnings("unchecked")
        List<String> harmfulTopics = (List<String>) queryData.get(HARMFUL_TOPICS);
        String topicsBlock = truncate(String.join("\n", harmfulTopics), MAX_HARMFUL_TOPICS_CHARS);

        return "An agentic surface has been observed with real harmful activity and currently has no "
                + "guardrail policy covering it. Suggest a starter guardrail policy to block this activity.\n\n"
                + "SURFACE: " + hostName + "\n"
                + "OBSERVED HARMFUL ACTIVITY (topic: reason it was flagged):\n" + topicsBlock + "\n\n"
                + "Return JSON with:\n"
                + "{\n"
                + "  \"name\": \"<short policy name, <=60 chars>\",\n"
                + "  \"description\": \"<one sentence: what this policy blocks and why>\",\n"
                + "  \"severity\": \"<LOW|MEDIUM|HIGH|CRITICAL>\",\n"
                + "  \"behaviour\": \"<block|warn|alert>\",\n"
                + "  \"deniedTopics\": [ {\"topic\": \"<short topic name>\", \"description\": \"<what to catch>\", "
                + "\"samplePhrases\": [\"<example phrase>\", \"<example phrase>\"] } ]\n"
                + "}\n"
                + "Cover every distinct harmful topic listed above with its own deniedTopics entry. Pick "
                + "\"block\" as behaviour unless the activity looks borderline, then use \"warn\".\n";
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject result = new BasicDBObject();
        if (rawResponse == null || rawResponse.isEmpty() || "NOT_FOUND".equalsIgnoreCase(rawResponse)) return result;
        try {
            JSONObject json = new JSONObject(rawResponse);
            result.put(NAME, json.optString(NAME, "Suggested guardrail"));
            result.put(DESCRIPTION, json.optString(DESCRIPTION, ""));
            result.put(SEVERITY, json.optString(SEVERITY, "MEDIUM").trim().toUpperCase());
            String behaviour = json.optString(BEHAVIOUR, "block").trim().toLowerCase();
            result.put(BEHAVIOUR, behaviour.isEmpty() ? "block" : behaviour);

            List<BasicDBObject> deniedTopics = new ArrayList<>();
            JSONArray arr = json.optJSONArray(DENIED_TOPICS);
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject t = arr.optJSONObject(i);
                    if (t == null) continue;
                    BasicDBObject topic = new BasicDBObject();
                    topic.put("topic", t.optString("topic", ""));
                    topic.put("description", t.optString("description", ""));
                    List<String> phrases = new ArrayList<>();
                    JSONArray phraseArr = t.optJSONArray("samplePhrases");
                    if (phraseArr != null) {
                        for (int j = 0; j < phraseArr.length(); j++) phrases.add(phraseArr.optString(j, ""));
                    }
                    topic.put("samplePhrases", phrases);
                    deniedTopics.add(topic);
                }
            }
            result.put(DENIED_TOPICS, deniedTopics);
        } catch (Exception e) {
            logger.error("GuardrailSuggestionClassifier: failed to parse response: " + e.getMessage());
        }
        return result;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
