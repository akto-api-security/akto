package com.akto.gpt.handlers.gpt_prompts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.validation.ValidationException;

import com.mongodb.BasicDBObject;

/**
 * Describes a batch of endpoints in a single call - either bare names (skills, MCP tools, where the name
 * alone is enough to infer purpose) or fuller {@code id + context} pairs (plain agent/LLM endpoints,
 * which have no identifying name and need their own method/url/sample). One call per batch instead of
 * one per endpoint, since a single collection can have hundreds of skills or MCP tools.
 */
public class EndpointDescriptionBatchPromptHandler extends AzureOpenAIPromptHandler {

    public static final String ITEM_KIND = "itemKind";
    public static final String COLLECTION_CONTEXT = "collectionContext";
    public static final String ITEMS = "items";
    public static final String ITEM_ID = "id";
    public static final String ITEM_CONTEXT = "context";
    public static final String MAX_CHARS = "maxChars";

    @SuppressWarnings("unchecked")
    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        Object itemsObj = queryData.get(ITEMS);
        if (!(itemsObj instanceof List) || ((List<?>) itemsObj).isEmpty()) {
            throw new ValidationException(ITEMS + " must be a non-empty list.");
        }
        for (Object item : (List<Object>) itemsObj) {
            if (!(item instanceof Map) || isBlank((String) ((Map<String, Object>) item).get(ITEM_ID))) {
                throw new ValidationException("Every item in " + ITEMS + " needs a non-blank " + ITEM_ID);
            }
        }
        if (isBlank(queryData.getString(ITEM_KIND))) {
            throw new ValidationException("Missing mandatory param: " + ITEM_KIND);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String itemKind = queryData.getString(ITEM_KIND);
        String collectionContext = queryData.getString(COLLECTION_CONTEXT);
        List<Map<String, Object>> items = (List<Map<String, Object>>) queryData.get(ITEMS);
        int maxChars = queryData.getInt(MAX_CHARS, 150);

        StringBuilder itemsBlock = new StringBuilder();
        for (Map<String, Object> item : items) {
            String id = (String) item.get(ITEM_ID);
            String context = (String) item.get(ITEM_CONTEXT);
            itemsBlock.append("- \"").append(id).append("\"");
            if (!isBlank(context)) {
                itemsBlock.append(": ").append(context);
            }
            itemsBlock.append("\n");
        }

        return
            "You are an API security analyst. Below is a batch of " + itemKind + "s from the same "
                + "collection" + (isBlank(collectionContext) ? "" : " (" + collectionContext + ")")
                + ". Write a one-line, factual description of what each one does.\n\n"
                + itemKind.toUpperCase() + "S:\n" + itemsBlock
                + "\nINSTRUCTIONS:\n"
                + "- For each " + itemKind + ", infer its purpose from whatever identifies it below - its "
                + "id itself when that id is a name (e.g. a tool id \"browser_click\" clicks an element "
                + "in a browser page), or its context when the id is just a reference number and the "
                + "real details (method, url, sample) are there instead.\n"
                + "- Every item gets its own independent description - do not describe the collection as a "
                + "whole, and do not let one item's description bleed into another's.\n"
                + "- Do not invent specifics you can't reasonably infer from the id/context.\n"
                + "- Plain text only, no markdown. Do not use the word \"API\".\n"
                + "- Avoid hedging filler (\"indicating\", \"appears to\", \"likely\") and generic filler "
                + "verbs (\"facilitates\", \"enables\", \"leverages\", \"utilizes\") - state the action.\n"
                + "- Maximum " + maxChars + " characters per description.\n"
                + "- If you genuinely cannot infer a " + itemKind + "'s purpose from its id/context, "
                + "omit it from the output entirely rather than guessing.\n"
                + "- Reply with the exact id shown in quotes before each item, verbatim - never a "
                + "different label or a number of your own choosing.\n\n"
                + "OUTPUT FORMAT:\n"
                + "Return a single valid JSON object: {\"descriptions\": {\"<id>\": \"<description>\", ...}} "
                + "with one entry per " + itemKind + " you could confidently describe.";
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        String processed = cleanJSON(rawResponse).trim();

        if (processed.isEmpty() || processed.equalsIgnoreCase("NOT_FOUND")) {
            resp.put("error", "Unable to generate descriptions - invalid response");
            return resp;
        }

        try {
            org.json.JSONObject json = new org.json.JSONObject(processed);
            org.json.JSONObject descriptionsJson = json.optJSONObject("descriptions");
            if (descriptionsJson == null) {
                resp.put("error", "Missing 'descriptions' object in LLM response");
                return resp;
            }

            Map<String, String> descriptions = new LinkedHashMap<>();
            java.util.Iterator<String> keys = descriptionsJson.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                String description = descriptionsJson.optString(id, "").trim();
                if (!description.isEmpty()) {
                    descriptions.put(id, description);
                }
            }

            if (descriptions.isEmpty()) {
                resp.put("error", "LLM returned no usable descriptions");
                return resp;
            }

            resp.put("descriptions", descriptions);
        } catch (Exception e) {
            logger.error("Error parsing endpoint description batch response: " + processed, e);
            resp.put("error", "Error parsing response: " + e.getMessage());
        }

        return resp;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
