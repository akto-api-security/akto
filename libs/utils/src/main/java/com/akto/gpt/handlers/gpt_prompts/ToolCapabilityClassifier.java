package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.json.JSONObject;

import javax.validation.ValidationException;

/**
 * Classifies one MCP tool (open-vocabulary — not the fixed built-in slug table) into a
 * capability class. Fallback path for insight 8's dangerous-capability-exposure metric,
 * used only when a tool name misses the static table in InsightUtil. Same
 * json_object + overridden-handle() pattern as AgentDomainClassifier/UserQueryTopicClassifier.
 */
public class ToolCapabilityClassifier extends AzureOpenAIPromptHandler {

    public static final String TOOL_NAME = "toolName";
    public static final String TOOL_DESCRIPTION = "toolDescription";
    public static final String CAPABILITY = "capability";

    public static final String SHELL_EXEC = "SHELL_EXEC";
    public static final String FILE_WRITE = "FILE_WRITE";
    public static final String FILE_READ = "FILE_READ";
    public static final String NETWORK_EGRESS = "NETWORK_EGRESS";
    public static final String CREDENTIAL_READ = "CREDENTIAL_READ";
    public static final String UNCLASSIFIED = "UNCLASSIFIED";

    private static final int MAX_DESCRIPTION_CHARS = 500;

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
        String toolDescription = truncate(queryData.getString(TOOL_DESCRIPTION, ""), MAX_DESCRIPTION_CHARS);
        return "Classify this tool into exactly one capability class based on what it actually DOES, "
                + "not its name alone.\n\n"
                + "TOOL_NAME: " + toolName + "\n"
                + "TOOL_DESCRIPTION: " + toolDescription + "\n\n"
                + "CAPABILITY CLASSES:\n"
                + "- SHELL_EXEC: runs arbitrary shell commands / code execution\n"
                + "- FILE_WRITE: writes, edits or deletes files\n"
                + "- FILE_READ: reads files or lists directories, nothing else\n"
                + "- NETWORK_EGRESS: fetches URLs, calls external APIs, sends data out\n"
                + "- CREDENTIAL_READ: reads secrets, tokens, API keys or credentials\n"
                + "- UNCLASSIFIED: none of the above clearly apply\n\n"
                + "If more than one applies, pick the most dangerous one (SHELL_EXEC > CREDENTIAL_READ > "
                + "NETWORK_EGRESS > FILE_WRITE > FILE_READ).\n"
                + "Return JSON: {\"capability\": \"<one of the classes above>\"}\n";
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject result = new BasicDBObject();
        String capability = UNCLASSIFIED;
        if (rawResponse != null && !rawResponse.isEmpty() && !"NOT_FOUND".equalsIgnoreCase(rawResponse)) {
            try {
                String c = new JSONObject(rawResponse).optString(CAPABILITY, UNCLASSIFIED).trim().toUpperCase();
                if (c.equals(SHELL_EXEC) || c.equals(FILE_WRITE) || c.equals(FILE_READ)
                        || c.equals(NETWORK_EGRESS) || c.equals(CREDENTIAL_READ)) {
                    capability = c;
                }
            } catch (Exception e) {
                logger.error("ToolCapabilityClassifier: failed to parse response: " + e.getMessage());
            }
        }
        result.put(CAPABILITY, capability);
        return result;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
