package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONObject;

import javax.validation.ValidationException;

/**
 * Classifies one MCP tool call as dangerous or not, straight from a sample of its
 * request/response — no separate description-fetch step. Fallback path for insight 8's
 * dangerous-capability-exposure metric. Same json_object + overridden-handle() pattern as
 * AgentDomainClassifier/UserQueryTopicClassifier.
 */
public class ToolCapabilityClassifier extends AzureOpenAIPromptHandler {

    public static final String TOOL_NAME = "toolName";
    public static final String SAMPLE_DATA = "sampleData";
    public static final String DANGEROUS = "dangerous";
    public static final String CAPABILITY = "capability";

    public static final String RESOURCE_DELETE = "RESOURCE_DELETE";
    public static final String FILE_WRITE = "FILE_WRITE";
    public static final String CREDENTIAL_OR_PII_READ = "CREDENTIAL_OR_PII_READ";
    public static final String CRITICAL_RESOURCE_WRITE = "CRITICAL_RESOURCE_WRITE";
    public static final String SAFE = "SAFE";

    private static final int MAX_SAMPLE_CHARS = 10000;

    @Override
    protected JSONObject getResponseFormat() {
        try { return new JSONObject("{\"type\":\"json_object\"}"); }
        catch (Exception e) { return null; }
    }

    @Override
    protected int getMaxTokens() { return 60; }

    @Override
    protected double getTemperature() { return 0.0; }

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        String toolName = queryData.getString(TOOL_NAME);
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new ValidationException(TOOL_NAME + " is required");
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
            logger.error("ToolCapabilityClassifier: " + e.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error: " + e.getMessage());
            return resp;
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String toolName = queryData.getString(TOOL_NAME, "");
        String sampleData = truncate(queryData.getString(SAMPLE_DATA, ""), MAX_SAMPLE_CHARS);
        return "You are given a tool name and one sample call to it - a structured record of the "
                + "request (headers, body) and response (headers, body). Decide whether calling this "
                + "tool is dangerous, based only on what the sample shows it actually does.\n\n"
                + "TOOL_NAME: " + toolName + "\n"
                + "SAMPLE: " + sampleData + "\n\n"
                + "Label it dangerous if the sample shows the tool doing any of:\n"
                + "- RESOURCE_DELETE: deletes a resource\n"
                + "- FILE_WRITE: writes to a file\n"
                + "- CREDENTIAL_OR_PII_READ: reads PII, secrets, tokens or credentials\n"
                + "- CRITICAL_RESOURCE_WRITE: creates or edits a critical resource (customer data or "
                + "something crucial)\n"
                + "Otherwise it is SAFE.\n"
                + "If more than one applies, pick the most dangerous one (RESOURCE_DELETE > "
                + "CREDENTIAL_OR_PII_READ > CRITICAL_RESOURCE_WRITE > FILE_WRITE).\n"
                + "Return JSON: {\"dangerous\": <true|false>, \"capability\": \"<one of RESOURCE_DELETE, "
                + "FILE_WRITE, CREDENTIAL_OR_PII_READ, CRITICAL_RESOURCE_WRITE, SAFE>\"}\n";
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject result = new BasicDBObject();
        String capability = SAFE;
        boolean dangerous = false;
        if (rawResponse != null && !rawResponse.isEmpty() && !"NOT_FOUND".equalsIgnoreCase(rawResponse)) {
            try {
                JSONObject parsed = new JSONObject(rawResponse);
                String c = parsed.optString(CAPABILITY, SAFE).trim().toUpperCase();
                if (c.equals(RESOURCE_DELETE) || c.equals(FILE_WRITE) || c.equals(CREDENTIAL_OR_PII_READ)
                        || c.equals(CRITICAL_RESOURCE_WRITE)) {
                    capability = c;
                    dangerous = true;
                } else {
                    dangerous = parsed.optBoolean(DANGEROUS, false);
                }
            } catch (Exception e) {
                logger.error("ToolCapabilityClassifier: failed to parse response: " + e.getMessage());
            }
        }
        result.put(CAPABILITY, capability);
        result.put(DANGEROUS, dangerous);
        return result;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
