package com.akto.action.gpt.handlers.gpt_prompts;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.validation.ValidationException;
import org.json.JSONObject;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.akto.util.Constants;



public class AnalyzeRequestResponseHeaders extends PromptHandler {

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();

    private static final LoggerMaker logger = new LoggerMaker(AnalyzeRequestResponseHeaders.class, LogDb.DASHBOARD);
    private static final String OLLAMA_SERVER_ENDPOINT = "http://jarvis.internal.akto.io/api/generate";
    //private static final String OLLAMA_SERVER_ENDPOINT = "http://35.226.83.20/api/generate";
    private static final String OLLAMA_MODEL =  "llama3:8b";
    private static final Double temperature = 0.1;
    private static final int max_tokens = 4000;

    @Override
    public BasicDBObject handle(BasicDBObject queryData) {
        try {
            validate(queryData);
            String prompt = getPrompt(queryData);
            String rawResponse = call(prompt, OLLAMA_MODEL, temperature, max_tokens);
            BasicDBObject resp =  processResponse(rawResponse);
            return resp;
        } catch(ValidationException exception) {
            logger.error("Validation error: " + exception.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error",  "Invalid input parameters.");
            return resp;
        } catch (Exception e) {
            logger.error("Error while handling request: " + e);
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error" + e.getMessage());
            return resp;
        }
    }

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
    protected String call(String prompt, String model, Double temperature, int maxTokens) {

        MediaType mediaType = MediaType.parse("application/json");
        JSONObject payload = new JSONObject();
            payload.put("model", model);
            payload.put("prompt", prompt);
            payload.put("temperature", temperature);
            payload.put("max_tokens", maxTokens);
            payload.put("top_p", 0.9);                 // Added top_p
            payload.put("top_k", 50);                  // Added top_k
            payload.put("repeat_penalty", 1.1);        // Penalize repetitions
            payload.put("presence_penalty", 0.6);      // Discourage new topic jumps
            payload.put("frequency_penalty", 0.0);     // Don't punish frequency
            payload.put("stream", false);

        RequestBody body = RequestBody.create(payload.toString(), mediaType);
        Request request = new Request.Builder()
                .url(OLLAMA_SERVER_ENDPOINT)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (
            Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Unexpected response code: " + response.code());
                return null;
            }
            ResponseBody responseBody = response.body();
            return responseBody != null ? responseBody.string() : null;
        } catch (IOException e) {
            logger.error("Error while executing request: " + e.getMessage());
            return null;
        }
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
    
            for (String key : json.keySet()) {
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
    
    private String processOutput(String rawResponse) {
        try {
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

    

