package com.akto.gpt.handlers.gpt_prompts;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.ValidationException;

import com.mongodb.BasicDBObject;

public class CollectionDescriptionPromptHandler extends AzureOpenAIPromptHandler {

    public static final String COLLECTION_NAME = "collectionName";
    public static final String HOST_NAME = "hostName";
    public static final String ACCESS_TYPE = "accessType";
    public static final String COLLECTION_TYPE = "collectionType";
    public static final String TAGS = "tags";
    public static final String ENDPOINTS = "endpoints";
    public static final String SAMPLE_SNIPPETS = "sampleSnippets";
    public static final String MAX_CHARS = "maxChars";

    // The one sanctioned "I can't confidently decide" answer - same text the UI already shows as its
    // empty-state button label, so it reads the same to the user either way. Storing this (rather than
    // leaving the field empty) marks the collection as "tried, gave up" so it isn't retried forever.
    public static final String CANNOT_DECIDE_PLACEHOLDER = "Add description";

    // Phrasings the model might use that clearly mean the same "can't decide" signal - normalized to
    // the exact CANNOT_DECIDE_PLACEHOLDER text rather than stored verbatim.
    private static final Set<String> GIVE_UP_PHRASES = new HashSet<>(Arrays.asList(
        "add description",
        "add a description",
        "add a brief description"
    ));

    // Any other non-answer / filler text - not the sanctioned give-up phrase, so treated as a failed
    // attempt (left empty, retried next run) rather than stored.
    private static final Set<String> REJECTED_JUNK_DESCRIPTIONS = new HashSet<>(Arrays.asList(
        "n/a",
        "na",
        "none",
        "no description",
        "no description available",
        "todo",
        "tbd",
        "unknown"
    ));

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        if (!queryData.containsKey(ENDPOINTS)) {
            throw new ValidationException("Missing mandatory param: " + ENDPOINTS);
        }

        Object endpointsObj = queryData.get(ENDPOINTS);
        if (!(endpointsObj instanceof List) || ((List<?>) endpointsObj).isEmpty()) {
            throw new ValidationException(ENDPOINTS + " must be a non-empty list.");
        }

        if (isBlank(queryData.getString(COLLECTION_NAME)) && isBlank(queryData.getString(HOST_NAME))) {
            throw new ValidationException("At least one of " + COLLECTION_NAME + " or " + HOST_NAME + " is required.");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String collectionName = queryData.getString(COLLECTION_NAME);
        String hostName = queryData.getString(HOST_NAME);
        String accessType = queryData.getString(ACCESS_TYPE);
        String collectionType = queryData.getString(COLLECTION_TYPE);
        List<String> tags = (List<String>) queryData.getOrDefault(TAGS, null);
        List<String> endpoints = (List<String>) queryData.get(ENDPOINTS);
        List<String> sampleSnippets = (List<String>) queryData.getOrDefault(SAMPLE_SNIPPETS, null);
        int maxChars = queryData.getInt(MAX_CHARS, 300);

        StringBuilder infoBlock = new StringBuilder();
        if (!isBlank(collectionName)) {
            infoBlock.append("Name: ").append(collectionName).append("\n");
        }
        if (!isBlank(hostName)) {
            infoBlock.append("Host: ").append(hostName).append("\n");
        }
        if (!isBlank(collectionType)) {
            infoBlock.append("Collection type: ").append(collectionType).append("\n");
        }
        if (!isBlank(accessType)) {
            infoBlock.append("Access type: ").append(accessType).append("\n");
        }
        if (tags != null && !tags.isEmpty()) {
            infoBlock.append("Tags: ").append(String.join(", ", tags)).append("\n");
        }

        StringBuilder endpointsBlock = new StringBuilder();
        for (String endpoint : endpoints) {
            endpointsBlock.append("- ").append(endpoint).append("\n");
        }

        StringBuilder samplesBlock = new StringBuilder();
        if (sampleSnippets != null && !sampleSnippets.isEmpty()) {
            for (String snippet : sampleSnippets) {
                if (snippet == null || snippet.trim().isEmpty()) {
                    continue;
                }
                samplesBlock.append("---\n").append(snippet).append("\n");
            }
        }

        return
            "You are an API security analyst. Based on the API collection's identifying info, its "
                + "endpoints, and (if provided) sample request/response traffic, write a concise, factual "
                + "description of what this API collection is used for.\n\n"
                + "COLLECTION INFO:\n" + infoBlock
                + "\nENDPOINTS:\n" + endpointsBlock
                + (samplesBlock.length() > 0 ? "\nSAMPLE REQUEST/RESPONSE TRAFFIC:\n" + samplesBlock : "")
                + "\nINSTRUCTIONS:\n"
                + "- Infer the purpose of this API collection from all the info above.\n"
                + "- Do not invent details that aren't supported by the endpoints or samples.\n"
                + "- Plain text only, no markdown formatting.\n"
                + "- Maximum " + maxChars + " characters.\n"
                + "- If, and only if, you cannot confidently determine what this collection is used for from "
                + "the given info, respond with exactly \"" + CANNOT_DECIDE_PLACEHOLDER + "\" as the "
                + "description instead of guessing.\n\n"
                + "OUTPUT FORMAT:\n"
                + "Return a single valid JSON object with the following structure:\n"
                + "{\"description\": \"<the description text, max " + maxChars + " characters, or \\\""
                + CANNOT_DECIDE_PLACEHOLDER + "\\\" if you can't decide>\"}";
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        String processed = cleanJSON(rawResponse).trim();

        if (processed == null || processed.isEmpty() || processed.equalsIgnoreCase("NOT_FOUND")) {
            resp.put("error", "Unable to generate description - invalid response");
            return resp;
        }

        try {
            org.json.JSONObject json = new org.json.JSONObject(processed);
            String description = json.optString("description", "").trim();

            if (description.isEmpty()) {
                resp.put("error", "Empty description in LLM response");
                return resp;
            }

            String normalized = description.toLowerCase();

            if (GIVE_UP_PHRASES.contains(normalized)) {
                // Sanctioned "can't decide" signal - store the exact canonical text, not verbatim.
                resp.put("description", CANNOT_DECIDE_PLACEHOLDER);
                return resp;
            }

            if (REJECTED_JUNK_DESCRIPTIONS.contains(normalized)) {
                // Non-answer, but not the sanctioned give-up phrase - treat as a failed attempt so it
                // gets retried instead of stored.
                resp.put("error", "LLM returned a non-answer: " + description);
                return resp;
            }

            resp.put("description", description);
        } catch (Exception e) {
            logger.error("Error parsing collection description response: " + processed, e);
            resp.put("error", "Error parsing response: " + e.getMessage());
        }

        return resp;
    }
}
