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


public class AnalyzeRequestResponseHeaders extends PromptHandler {

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();

    private static final LoggerMaker logger = new LoggerMaker(AnalyzeRequestResponseHeaders.class, LogDb.DASHBOARD);
    private static final String OLLAMA_SERVER_ENDPOINT = "http://jarvis.internal.akto.io/api/generate";
    private static final String OLLAMA_MODEL = "llama3.2";
    private static final double temperature = 0.0;
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
        String prompt = 
        "You are an expert security engineer and developer.\n" +
        "Below is a list of HTTP headers and their values captured from an API request or response:\n" +
        "----------------------------------------\n" +
        queryData.get("headers_with_values") + "\n" +
        "----------------------------------------\n" +
        "Your task is to identify ONLY those headers which reveal information about the protocol or gateway technology used (e.g., apigee, cloudfront, aws, azure, etc.).\n" +
        "\n" +
        "Match headers using known technology patterns ONLY:\n" +
        "- AWS: key contains \"amzn\"\n" +
        "- Cloudflare/CloudFront: key contains \"cf\"\n" +
        "- Azure: key contains \"azure\"\n" +
        "- Apigee: key contains \"apigee\"\n" +
        "- Akamai: key contains \"akamai\"\n" +
        "- Fastly: key contains \"fastly\" or \"x-timer\"\n" +
        "- Google Cloud: key contains \"gcp\" or \"x-goog\"\n" +
        "- Vercel: key contains \"vercel\"\n" +
        "- Netlify: key contains \"netlify\"\n" +
        "\n" +
        "Strict rules:\n" +
        "- DO NOT guess technologies based on unknown or generic headers\n" +
        "- DO NOT map headers like `x-recruiting`, `sec-ch-ua-platform`, `x-forwarded-for`, `x-forwarded-proto`, `accept`, or `user-agent` to any technology\n" +
        "- DO NOT infer based on common HTTP behavior (e.g., HTTPS ≠ Cloudflare)\n" +
        "- IGNORE headers with very long values (likely tokens, auth, cookies, etc.)\n" +
        "- DO NOT add any header in response not matched by technology patterns listed above\n" +
        "- DO NOT return response with \"none\" response. Ignore those headers\n" +
        "- DO NOT append previous results\n" +
        "- DO NOT include content which can be false positive\n" +
        "\n" +
        "Expected Output Format:\n" +
        "- Return ONLY a JSON object with matched headers as keys and the technology as values\n" +
        "- If no valid headers are found, return only this exact word: NOT_FOUND\n" +
        "- DO NOT include explanations, tags, metadata, or anything other than the final JSON or NOT_FOUND\n" +
        "- Be 100% precise — hallucinated or speculative outputs will be treated as incorrect\n" +
        "\n" +
        "Examples:\n" +
        "Input header: x-amzn-requestid: abc → Output: {\"x-amzn-requestid\": \"aws\"}\n" +
        "Input header: cf-ray: 3252 → Output: {\"cf-ray\": \"cloudfront\"}\n" +
        "\n" +
        "Respond now using only the JSON format or NOT_FOUND. Do not return model info or guesses.";
    
        return prompt;
    }

    @Override
    protected String call(String prompt, String model, Double temperature, int maxTokens) {

        MediaType mediaType = MediaType.parse("application/json");
        JSONObject payload = new JSONObject();
            payload.put("model", model);
            payload.put("prompt", prompt);
            payload.put("temperature", temperature);
            payload.put("max_tokens", maxTokens);
            payload.put("stream", false);

        RequestBody body = RequestBody.create(payload.toString(), mediaType);
        Request request = new Request.Builder()
                .url(OLLAMA_SERVER_ENDPOINT)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .build();
        Response response = null;
        String resp_body = "";
        try {
            response =  client.newCall(request).execute();
            ResponseBody responseBody = response.body();
            if(responseBody != null) {
                resp_body = responseBody.string();
            }
        } catch (IOException e) {
            logger.error("Error while executing request " + request.url() + ": " + e);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return resp_body;
    }

    @Override
    protected BasicDBObject processResponse(String rawResponse) {
        BasicDBObject resp = new BasicDBObject();
        List<String> responses = new ArrayList<>();
        resp.put("responses", responses);

        rawResponse = processOutput(rawResponse); 

        String[] splits = rawResponse.split(",");
        for (String split : splits) {
            split = split.trim();
            if (!split.contains("NOT_FOUND")) {
                responses.add(split);
            }
        }
        return resp;
    }

    private String processOutput(String rawResponse) {
        // Implement your logic to process the output here
        // For example, you can clean up the response or format it as needed
        JSONObject jsonResponse = new JSONObject(rawResponse);
        String cleanResponse = jsonResponse.getString("response");

        // Optional: remove <think> tags if they exist
        cleanResponse = cleanResponse.replaceAll("(?s)<think>.*?</think>", "");
        cleanResponse = cleanResponse.replaceAll("^\\{", "").replaceAll("}$", "");


        return cleanResponse;
    }
}

