package com.akto.gpt.handlers.gpt_prompts;

import javax.validation.ValidationException;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mongodb.BasicDBObject;

public class TestExecutorModifier extends AzureOpenAIPromptHandler {

    // TODO: use abstract class to decide which prompt handler to use

    static final int MAX_QUERY_LENGTH = 100000;
    public static final String _REQUEST = "request";
    public static final String _OPERATION = "operation";
    static final String _NOT_FOUND = "not_found";
    public final static String _AKTO_GPT_AI = "AKTO_GPT_AI";

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        // validate request if present
        if (queryData.containsKey(_REQUEST)) {
            Object data = queryData.get(_REQUEST);
            if (!(data instanceof String)) {
                throw new ValidationException(_REQUEST + " must be a string.");
            }
            String request = (String) data;
            if (request.isEmpty()) {
                throw new ValidationException(_REQUEST + " is empty.");
            }
            if (request.length() > MAX_QUERY_LENGTH) {
                throw new ValidationException(_REQUEST + " is too long.");
            }
        }

        // Validation for operation
        if (!queryData.containsKey(_OPERATION)) {
            throw new ValidationException("Missing mandatory param: " + _OPERATION);
        }
        Object opData = queryData.get(_OPERATION);
        if (!(opData instanceof String)) {
            throw new ValidationException(_OPERATION + " must be a string.");
        }
        String operation = (String) opData;
        if (operation.isEmpty()) {
            throw new ValidationException(_OPERATION + " is empty.");
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String operation = queryData.getString(_OPERATION);
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are a API request expert.\n\n");

        if (queryData.containsKey(_REQUEST)) {
            String request = queryData.getString(_REQUEST);
            promptBuilder.append("You are given an API request :\n")
                .append("----------------------------------------\n")
                .append(request)
                .append("\n----------------------------------------\n\n");
        } else {
            promptBuilder.append(
                "You are given with a contextual information on which you have to perform the operation described below.\n\n");
        }

        promptBuilder.append("Your task:\n")
            .append("- Give out the delta key and value (optional) for the operation described.\n")
            .append("- The operation is: ")
            .append(operation)
            .append("\n----------------------------------------\n\n")
            .append("Strict rules:\n")
            .append("- Return only the delta operation\n")
            .append("- Assign a suitable value, according to the key, when needed\n")
            .append("- If you're unable to do the operation, return only this word: " + _NOT_FOUND + "\n\n")
            .append("Expected Output:\n")
            .append("- A JSON object with operation and key and value (optional) pair array.\n")
            .append("- Example: { \"delete_body_param\": \"param1\" }\n")
            .append("- Example: { \"modify_header\": {\"header1\": \"value1\"} }\n")
            .append("- Example: { \"modify_url\": \"https://example.com/product?id=5 OR 1=1\" }\n")
            .append("- Example: { \"modify_body_param\": {\"key\": \"value1\"} }\n")
            .append("- Example: { \"add_header\": {\"key\": \"value1\"} }\n")
            .append("- Example: { \"add_body_param\": {\"key\": \"value1\"} }\n")
            .append("- Give preference to the operation given in the prompt, but not strictly as operation can be of modify nature but key would not be there to modify in the api request shared to you, be intelligent about the operations to be performed.\n")
            .append("- Check the key location for choosing the operation to be performed, if key belongs to headers, then choose the operation from the list of operations given for headers, if key belongs to body, then choose the operation from the list of operations given for body, if key belongs to url, then choose the operation from the list of operations given for url, if key belongs to query params, then choose the operation from the list of operations given for query params.\n")
            .append("- Return ONLY the JSON or " + _NOT_FOUND + " — nothing else.");
        return promptBuilder.toString();
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        String processed = processOutput(rawResponse).trim();
        String processedResponse = cleanJSON(processed);
        if (processedResponse.equalsIgnoreCase(_NOT_FOUND)) {
            return resp;
        }
        try {
            JSONObject json = new JSONObject(processedResponse);
            for (java.util.Iterator<?> it = json.keys(); it.hasNext(); ) {
                String key = String.valueOf(it.next());
                key = key.trim().toLowerCase();
                Object valueObj = json.get(key);
                if (key.equalsIgnoreCase(_NOT_FOUND) || valueObj == null) {
                    continue;
                }
                if (valueObj instanceof String) {
                    String value = ((String) valueObj).trim();
                    if (value.isEmpty() || value.equalsIgnoreCase(_NOT_FOUND) || value.equalsIgnoreCase("none")) {
                        continue;
                    }
                    resp.put(key, value);
                } else if (valueObj instanceof JSONObject) {
                    JSONObject obj = (JSONObject) valueObj;
                    if (obj.length() == 0) {
                        continue;
                    }
                    resp.put(key, obj);
                } else if (valueObj instanceof JSONArray) {
                    JSONArray arr = (JSONArray) valueObj;
                    if (arr.length() == 0) {
                        continue;
                    }
                    resp.put(key, arr);
                } else if (valueObj instanceof Boolean) {
                    resp.put(key, Boolean.valueOf(String.valueOf(valueObj)));
                }
            }
        } catch (Exception e) {
            logger.error("Malformed JSON from model: " + processed, e);
        }
        return resp;
    }
}