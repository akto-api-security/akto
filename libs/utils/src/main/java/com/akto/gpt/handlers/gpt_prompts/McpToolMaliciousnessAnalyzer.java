package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;

import javax.validation.ValidationException;

public class McpToolMaliciousnessAnalyzer extends AzureOpenAIPromptHandler {

    public static final String TOOL_NAME = "toolName";
    public static final String TOOL_DESCRIPTION = "toolDescription";

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        if (!queryData.containsKey(TOOL_NAME)) {
            throw new ValidationException("Missing mandatory param: " + TOOL_NAME);
        }
        if (!queryData.containsKey(TOOL_DESCRIPTION)) {
            throw new ValidationException("Missing mandatory param: " + TOOL_DESCRIPTION);
        }
        
        String toolName = queryData.getString(TOOL_NAME);
        String toolDescription = queryData.getString(TOOL_DESCRIPTION);
        
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new ValidationException(TOOL_NAME + " is empty.");
        }
        if (toolDescription == null) {
            throw new ValidationException(TOOL_DESCRIPTION + " is null.");
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String toolName = queryData.getString(TOOL_NAME);
        String toolDescription = queryData.getString(TOOL_DESCRIPTION);
        
        return
            "You are a security expert analyzing MCP (Model Context Protocol) tools for potential maliciousness. "
                + "Your task is to analyze a tool's name and description to determine if the tool is malicious.\n\n"
                + "IMPORTANT: Legitimate tools that handle sensitive data are NOT malicious. A tool that fetches payment card details, "
                + "accesses user information, or handles sensitive data as part of its intended function is SAFE if it does what it claims to do.\n\n"
                + "ANALYSIS CRITERIA:\n"
                + "1. Malicious Tool Name: Check if the tool name suggests malicious intent with destructive or unauthorized actions\n"
                + "   - MALICIOUS examples: 'delete_all_data', 'hack_user', 'steal_credentials', 'bypass_security', 'destroy_database', 'wipe_all_files'\n"
                + "   - SAFE examples: 'fetch_payment_card_details', 'get_user_info', 'retrieve_customer_data', 'access_payment_info' (these are legitimate operations)\n"
                + "2. Malicious Tool Description: Check if the description contains malicious intent with destructive or unauthorized actions\n"
                + "   - MALICIOUS examples: 'delete all user data', 'bypass authentication', 'steal sensitive information', 'destroy database', 'unauthorized access'\n"
                + "   - SAFE examples: 'fetches payment card details for processing', 'retrieves user information', 'accesses customer data' (these are legitimate operations)\n"
                + "3. Name-Description Mismatch: Check if the tool name and description do not match, indicating potential deception or malicious intention\n"
                + "   - MALICIOUS example: Name says 'get_user_info' but description says 'delete user account'\n"
                + "   - MALICIOUS example: Name says 'backup_data' but description says 'format all drives'\n"
                + "   - SAFE example: Name says 'fetch_payment_card_details' and description says 'retrieves payment card information' (they match)\n\n"
                + "KEY DISTINCTION:\n"
                + "- MALICIOUS: Tools that STEAL, DESTROY, DELETE, BYPASS, HACK, or perform UNAUTHORIZED actions\n"
                + "- SAFE: Tools that LEGITIMATELY ACCESS, FETCH, RETRIEVE, or HANDLE sensitive data as part of their intended function\n"
                + "- The presence of sensitive data keywords (payment, card, user, credentials) does NOT make a tool malicious if it's performing a legitimate operation\n\n"
                + "SECURITY INDICATORS (MALICIOUS):\n"
                + "- Verbs indicating destruction: delete_all, destroy, wipe, format, erase, remove_all\n"
                + "- Verbs indicating unauthorized access: hack, bypass, steal, exploit, crack, break_into\n"
                + "- Verbs indicating privilege escalation: escalate_privileges, gain_admin_access, become_root\n"
                + "- Names/descriptions that are deliberately misleading or contradictory\n"
                + "- Names/descriptions that hide the true purpose of the tool\n\n"
                + "OUTPUT FORMAT:\n"
                + "Return a single valid JSON object with the following structure:\n"
                + "{\"isMalicious\": <true/false>, \"reason\": \"<brief explanation of why it's malicious or safe>\", "
                + "\"hasMaliciousName\": <true/false>, \"hasMaliciousDescription\": <true/false>, "
                + "\"nameDescriptionMismatch\": <true/false>}\n\n"
                + "INPUTS:\n"
                + "TOOL NAME: " + toolName + "\n"
                + "TOOL DESCRIPTION: " + (toolDescription != null ? toolDescription : "(no description)") + "\n\n"
                + "Analyze the tool name and description. Remember: legitimate tools that handle sensitive data are SAFE. "
                + "Only flag as malicious if there is actual malicious intent (destruction, theft, unauthorized access, or deception). "
                + "Return the JSON response indicating if the tool is malicious.";
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        String processed = cleanJSON(rawResponse).trim();
        
        if (processed == null || processed.isEmpty() || processed.equalsIgnoreCase("NOT_FOUND")) {
            resp.put("isMalicious", false);
            resp.put("reason", "Unable to analyze - invalid response");
            return resp;
        }
        
        try {
            org.json.JSONObject json = new org.json.JSONObject(processed);
            
            boolean isMalicious = json.optBoolean("isMalicious", false);
            String reason = json.optString("reason", "");
            boolean hasMaliciousName = json.optBoolean("hasMaliciousName", false);
            boolean hasMaliciousDescription = json.optBoolean("hasMaliciousDescription", false);
            boolean nameDescriptionMismatch = json.optBoolean("nameDescriptionMismatch", false);
            
            resp.put("isMalicious", isMalicious);
            resp.put("reason", reason);
            resp.put("hasMaliciousName", hasMaliciousName);
            resp.put("hasMaliciousDescription", hasMaliciousDescription);
            resp.put("nameDescriptionMismatch", nameDescriptionMismatch);
            
        } catch (Exception e) {
            logger.error("Error parsing maliciousness analysis response: " + processed, e);
            resp.put("isMalicious", false);
            resp.put("reason", "Error parsing response: " + e.getMessage());
        }
        
        return resp;
    }
}

