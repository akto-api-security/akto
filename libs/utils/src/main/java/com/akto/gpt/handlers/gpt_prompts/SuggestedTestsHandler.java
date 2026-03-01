package com.akto.gpt.handlers.gpt_prompts;

import java.util.ArrayList;
import java.util.List;

import javax.validation.ValidationException;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.BasicDBObject;

public class SuggestedTestsHandler extends AzureOpenAIPromptHandler {

    public static final String SAMPLE_DATA = "sampleData";
    public static final String TESTS = "tests";
    public static final String SUGGESTED_TEST_IDS = "suggestedTestIds";

    public static final int MAX_SAMPLE_DATA_LENGTH = 50000;
    public static final int MAX_TESTS_COUNT = 3000;
    private static final int MAX_SUGGESTED_IDS = 40;

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        if (!queryData.containsKey(SAMPLE_DATA)) {
            throw new ValidationException("Missing mandatory param: " + SAMPLE_DATA);
        }
        Object sampleDataObj = queryData.get(SAMPLE_DATA);
        if (!(sampleDataObj instanceof String)) {
            throw new ValidationException(SAMPLE_DATA + " must be a string.");
        }
        String sampleData = (String) sampleDataObj;
        if (sampleData.length() > MAX_SAMPLE_DATA_LENGTH) {
            throw new ValidationException(SAMPLE_DATA + " exceeds max length of " + MAX_SAMPLE_DATA_LENGTH);
        }

        if (!queryData.containsKey(TESTS)) {
            throw new ValidationException("Missing mandatory param: " + TESTS);
        }
        Object testsObj = queryData.get(TESTS);
        if (!(testsObj instanceof List)) {
            throw new ValidationException(TESTS + " must be a list of objects with id and name.");
        }
        List<?> testsList = (List<?>) testsObj;
        if (testsList.size() > MAX_TESTS_COUNT) {
            throw new ValidationException(TESTS + " list exceeds max size of " + MAX_TESTS_COUNT);
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String sampleData = queryData.getString(SAMPLE_DATA);
        @SuppressWarnings("unchecked")
        List<BasicDBObject> tests = (List<BasicDBObject>) queryData.get(TESTS);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are an API security testing expert.\n\n");
        promptBuilder.append("You are given:\n");
        promptBuilder.append("1. Sample request/response data from an API endpoint.\n");
        promptBuilder.append("2. A list of available security tests (each with id, name, and category).\n\n");
        promptBuilder.append("Your task: Choose the tests that are most likely to yield positive findings (i.e. most likely to detect a real issue) when run against this API. Prioritise tests that match how this API works and what it accepts, not just the most severe vulnerability types. For example, an API that accepts HTTP form-like input may be more susceptible to XSRF than to BOLA/IDOR, so prefer XSRF when it fits the API.\n\n");
        promptBuilder.append("API sample data:\n");
        promptBuilder.append("----------------------------------------\n");
        promptBuilder.append(sampleData);
        promptBuilder.append("\n----------------------------------------\n\n");
        promptBuilder.append("Available tests (id, name, category):\n");
        for (BasicDBObject test : tests) {
            String id = test.getString("id");
            String name = test.getString("name");
            String category = test.getString("category");
            if (id != null && name != null) {
                promptBuilder.append("- ").append(id).append(": ").append(name);
                if (category != null && !category.isEmpty()) {
                    promptBuilder.append(" (category: ").append(category).append(")");
                }
                promptBuilder.append("\n");
            }
        }
        promptBuilder.append("\nStrict rules:\n");
        promptBuilder.append("- Return a JSON object with a single key \"").append(SUGGESTED_TEST_IDS).append("\" whose value is an array of test ids (strings), ordered by likelihood of a positive finding (most likely first).\n");
        promptBuilder.append("- Include at most ").append(MAX_SUGGESTED_IDS).append(" test ids.\n");
        promptBuilder.append("- Only include test ids that appear in the available tests list above.\n");
        promptBuilder.append("- If no tests seem relevant, return {\"").append(SUGGESTED_TEST_IDS).append("\": []}.\n");
        promptBuilder.append("- Return ONLY valid JSON, no other text or explanation.\n");
        promptBuilder.append("Example: {\"").append(SUGGESTED_TEST_IDS).append("\": [\"id1\", \"id2\", \"id3\"]}\n");

        return promptBuilder.toString();
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        List<String> suggestedTestIds = new ArrayList<>();
        String processed = processOutput(rawResponse).trim();
        if (processed == null || processed.isEmpty() || "NOT_FOUND".equals(processed)) {
            resp.put(SUGGESTED_TEST_IDS, suggestedTestIds);
            return resp;
        }
        String processedResponse = cleanJSON(processed);
        if ("NOT_FOUND".equals(processedResponse)) {
            resp.put(SUGGESTED_TEST_IDS, suggestedTestIds);
            return resp;
        }
        try {
            JSONObject json = new JSONObject(processedResponse);
            if (!json.has(SUGGESTED_TEST_IDS)) {
                resp.put(SUGGESTED_TEST_IDS, suggestedTestIds);
                return resp;
            }
            Object idsObj = json.get(SUGGESTED_TEST_IDS);
            if (!(idsObj instanceof JSONArray)) {
                resp.put(SUGGESTED_TEST_IDS, suggestedTestIds);
                return resp;
            }
            JSONArray arr = (JSONArray) idsObj;
            for (int i = 0; i < Math.min(arr.length(), MAX_SUGGESTED_IDS); i++) {
                Object item = arr.get(i);
                if (item instanceof String) {
                    String id = ((String) item).trim();
                    if (!id.isEmpty()) {
                        suggestedTestIds.add(id);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse suggested tests response (invalid or truncated JSON): " + processed, e);
            resp.put("error", "Invalid or truncated response from AI. Please try again.");
            resp.put(SUGGESTED_TEST_IDS, suggestedTestIds);
            return resp;
        }
        resp.put(SUGGESTED_TEST_IDS, suggestedTestIds);
        return resp;
    }
}
