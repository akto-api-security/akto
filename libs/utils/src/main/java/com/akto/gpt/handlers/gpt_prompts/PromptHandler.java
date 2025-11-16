package com.akto.gpt.handlers.gpt_prompts;

import com.akto.data_actor.DataActorFactory;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.validation.ValidationException;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class PromptHandler {

    private static final LoggerMaker logger = new LoggerMaker(PromptHandler.class, LogDb.TESTING);
    private static final String OLLAMA_MODEL = "llama3:8b";
    static final Double temperature = 0.1;
    static final int max_tokens = 10000;
    static final Object llmLock = new Object();
    private static final int CHUNK_SIZE = 10000;
    private static final String CONTEXT_DELIMITER = "****";

    /**
     * Process the input query data and return a String response.
     */
    public BasicDBObject handle(BasicDBObject queryData) {
        try {
            validate(queryData);
            //String prompt = getPrompt(queryData);
            //String rawResponse = call(prompt, OLLAMA_MODEL, temperature, max_tokens);
            //BasicDBObject resp = processResponse(rawResponse);
            //return resp;
            List<BasicDBObject> responses = callLLMWithContextBreakdown(queryData);
            return aggregateResponses(responses);
        } catch (ValidationException exception) {
            logger.error("Validation error: " + exception.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Invalid input parameters.");
            return resp;
        } catch (Exception e) {
            logger.error("Error while handling request: " + e);
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error" + e.getMessage());
            return resp;
        }
    }

    private List<BasicDBObject> callLLMWithContextBreakdown(BasicDBObject queryData) throws Exception {
        String key = queryData.getBoolean(TestExecutorModifier._IS_EXTERNAL_CONTEXT_IN_OPERATION, false)
            ? TestExecutorModifier._OPERATION
            : TestExecutorModifier._REQUEST;

        String originalValue = queryData.getString(key);

        if (StringUtils.isNotEmpty(originalValue)) {
            String valueToSplit = key.equals(TestExecutorModifier._OPERATION)
                ? getContext(originalValue)
                : originalValue;

            if (StringUtils.isNotBlank(valueToSplit)) {
                List<String> chunks = splitIntoChunks(valueToSplit);
                if (chunks.size() > 1) {
                    List<BasicDBObject> responses = new ArrayList<>();
                    for (String chunk : chunks) {
                        BasicDBObject queryDataCopy = (BasicDBObject) queryData.copy();
                        queryDataCopy.put(key, key.equals(TestExecutorModifier._OPERATION)
                            ? replaceContext(originalValue, chunk)
                            : chunk);
                        responses.add(callLLM(queryDataCopy));
                    }
                    return responses;
                }
            }
        }

        return Collections.singletonList(callLLM(queryData));
    }

    private BasicDBObject callLLM(BasicDBObject queryData) throws Exception {
        String prompt = getPrompt(queryData);
        String rawResponse = call(prompt, OLLAMA_MODEL, temperature, max_tokens);
        return processResponse(rawResponse);
    }

    private static List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int i = 0; i < length; i += CHUNK_SIZE) {
            chunks.add(text.substring(i, Math.min(length, i + CHUNK_SIZE)));
        }
        return chunks;
    }

    private String getContext(String text) {
        int start = text.indexOf(CONTEXT_DELIMITER);
        int end = text.lastIndexOf(CONTEXT_DELIMITER);

        if (start < 0 || end < 0 || start >= end) {
            return null;
        }

        return text.substring(start + CONTEXT_DELIMITER.length(), end).trim();
    }

    private String replaceContext(String text, String context) {

        int start = text.indexOf(CONTEXT_DELIMITER);
        int end = text.lastIndexOf(CONTEXT_DELIMITER);

        if (start < 0 || end < 0 || start >= end) {
            return text;
        }

        return text.substring(0, start) + context + text.substring(end + CONTEXT_DELIMITER.length());
    }

    private BasicDBObject aggregateResponses(List<BasicDBObject> responses) {
        if (responses == null || responses.isEmpty()) {
            return new BasicDBObject();
        }

        if (responses.size() == 1) {
            return responses.get(0);
        }
        BasicDBObject aggregated = new BasicDBObject();

        for (BasicDBObject response : responses) {
            for (String key : response.keySet()) {
                Object newValue = response.get(key);
                Object existingValue = aggregated.get(key);

                if (existingValue == null) {
                    aggregated.put(key, newValue);
                } else if (existingValue instanceof JSONArray && newValue instanceof JSONArray) {
                    JSONArray existingArray = (JSONArray) existingValue;
                    JSONArray newArray = (JSONArray) newValue;
                    for (int i = 0; i < newArray.length(); i++) {
                        try {
                            existingArray.put(newArray.get(i));
                        } catch (JSONException e) {
                            logger.error("Error merging JSONArrays for key: " + key + ", value: " + newValue, e);
                        }
                    }
                } else if (existingValue instanceof Boolean && newValue instanceof Boolean) {
                    boolean existingBool = Boolean.parseBoolean(String.valueOf(existingValue));
                    aggregated.put(key, existingBool || Boolean.parseBoolean(String.valueOf(newValue)));
                } else {
                    aggregated.put(key, newValue);
                }
            }
        }

        return aggregated;
    }

    /**
     * Validate input parameters.
     */
    protected abstract void validate(BasicDBObject queryData) throws ValidationException;

    /**
     * Return the prompt string to be sent to the AI.
     */
    protected abstract String getPrompt(BasicDBObject queryData);

    /**
     * Call the AI model with the provided prompt and parameters
     */
    protected String call(String prompt, String model, Double temperature, int maxTokens) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("prompt", prompt);
        JSONObject options = new JSONObject();
        options.put("temperature", temperature);
        options.put("num_predict", maxTokens);
        options.put("top_p", 0.9); // Added top_p
        options.put("top_k", 50); // Added top_k
        options.put("repeat_penalty", 1.1); // Penalize repetitions
        options.put("presence_penalty", 0.6); // Discourage new topic jumps
        options.put("frequency_penalty", 0.0); // Don't punish frequency
        payload.put("options", options);
        payload.put("stream", false);

        synchronized (llmLock) {
            return DataActorFactory.fetchInstance().getLLMPromptResponse(payload);
        }
    }

    /**
     * Process the raw response (e.g., clean answer).
     */
    protected abstract BasicDBObject processResponse(String rawResponse);

    static String cleanJSON(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "NOT_FOUND";
        }

        // Truncate at the last closing brace to remove any trailing notes
        int lastBrace = rawResponse.lastIndexOf('}');
        if (lastBrace != -1) {
            rawResponse = rawResponse.substring(0, lastBrace + 1);
        }

        // Start at the first opening brace to remove any forward notes
        int firstBrace = rawResponse.indexOf('{');
        if (firstBrace != -1) {
            rawResponse = rawResponse.substring(firstBrace);
        }
        return rawResponse.trim();
    }

    static String processOutput(String rawResponse) {
        try {

            rawResponse = cleanJSON(rawResponse);

            JSONObject jsonResponse = new JSONObject(rawResponse);
            String cleanResponse = jsonResponse.getString("response");
    
            // Remove <think> tags
            cleanResponse = cleanResponse.replaceAll("(?s)<think>.*?</think>", "").trim();
    
            // If wrapped in escaped quotes, unescape it
            if (cleanResponse.startsWith("\"") && cleanResponse.endsWith("\"")) {
                cleanResponse = cleanResponse.substring(1, cleanResponse.length() - 1)
                                             .replace("\\\"", "");
            }
    
            return cleanResponse.trim();
        } catch (Exception e) {
            logger.error("Failed to clean LLM response: " + rawResponse, e);
            return "NOT_FOUND";
        }
    }
}
