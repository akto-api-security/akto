package com.akto.gpt.handlers.gpt_prompts;
import java.util.ArrayList;
import java.util.List;
import javax.validation.ValidationException;
import org.json.JSONObject;
import com.mongodb.BasicDBObject;

public class AnalyzeRequestResponseHeaders extends PromptHandler {

    @Override
    protected void validate(BasicDBObject queryData) throws ValidationException {
        //CommonValidations.validate(queryData);

        if (!queryData.containsKey("headers_with_values")) {
            throw new ValidationException("Missing mandatory param: headers_with_values");
        }

        Object data = queryData.get("headers_with_values");
        if (!(data instanceof String)) {
            throw new ValidationException("headers_with_values must be a string.");
        }

        String headersWithValues = (String) data;

        if (headersWithValues.isEmpty()) {
            throw new ValidationException("headers_with_values is empty.");
        }

        if (headersWithValues.length() > 2000000) {
            throw new ValidationException("headers_with_values is too long.");
        }
    }

    @Override
    protected String getPrompt(BasicDBObject queryData) {
        String headers = queryData.getString("headers_with_values");
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("You are a precise pattern-matching assistant.\n\n")
            .append("Below is a list of HTTP headers captured from an API request or response:\n")
            .append("----------------------------------------\n")
            .append(headers)
            .append("\n----------------------------------------\n\n")
            .append("Your task:\n")
            .append("- Identify headers that reveal the use of gateway or infrastructure technologies using strict pattern matching.\n")
            .append("- Only match based on header **names**, not values.\n")
            .append("- Known patterns:\n")
            .append("    - 'amzn' → AWS\n")
            .append("    - 'cf' → Cloudflare or CloudFront\n")
            .append("    - 'azure' → Azure\n")
            .append("    - 'akamai' → Akamai\n")
            .append("    - 'x-goog', 'gcp' → Google Cloud\n")
            .append("    - 'apigee' → Apigee\n")
            .append("    - 'fastly' → Fastly\n")
            .append("    - 'vercel' → Vercel\n")
            .append("    - 'netlify' → Netlify\n\n")
            .append("Strict rules:\n")
            .append("- DO NOT guess or infer based on generic headers like `x-forwarded-for`, `user-agent`, etc.\n")
            .append("- DO NOT match based on values.\n")
            .append("- DO NOT include tokens, auth keys, or headers with very long values.\n")
            .append("- Return only headers that match a known pattern exactly.\n")
            .append("- If no match is found, return only this word: NOT_FOUND\n\n")
            .append("Expected Output:\n")
            .append("- A JSON object with matched headers and technology names.\n")
            .append("- Example: { \"x-amzn-requestid\": \"aws\", \"cf-ray\": \"cloudflare\" }\n")
            .append("- Return ONLY the JSON or NOT_FOUND — nothing else.");
        return promptBuilder.toString();
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        List<String> responses = new ArrayList<>();
        resp.put("responses", responses);
    
        String processed = processOutput(rawResponse).trim();
        if (processed.equalsIgnoreCase("NOT_FOUND")) {
            responses = new ArrayList<>();
            return resp;
        }
    
        try {
            JSONObject json = new JSONObject(processed);
    
            boolean hasValid = false;
    
            for (java.util.Iterator<?> it = json.keys(); it.hasNext(); ) {
                String key = (String) it.next();
                String value = json.optString(key, "").trim();
    
                if (key.equalsIgnoreCase("not_found") || value.equalsIgnoreCase("not_found") || value.equalsIgnoreCase("none") || value.isEmpty()) {
                    continue;
                }
    
                responses.add(key + ": " + value);
                hasValid = true;
            }
    
            if (!hasValid) {
                responses = new ArrayList<>();
            }
    
        } catch (Exception e) {
            logger.error("Malformed JSON from model: " + processed, e);
            responses = new ArrayList<>();
        }
        return resp;
    }

}
