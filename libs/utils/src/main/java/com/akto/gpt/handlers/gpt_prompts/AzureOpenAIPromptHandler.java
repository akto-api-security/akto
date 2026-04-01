package com.akto.gpt.handlers.gpt_prompts;

import java.util.concurrent.TimeUnit;

import javax.validation.ValidationException;

import com.akto.dao.agents.AgentModelDao;
import com.akto.dto.agents.Model;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;

import okhttp3.OkHttpClient;

public abstract class AzureOpenAIPromptHandler {

    static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    static final LoggerMaker logger = new LoggerMaker(AzureOpenAIPromptHandler.class, LogDb.DASHBOARD);

    /**
     * Resolves the LLM model to use. Checks the agent_models DB collection.
     * Returns null if no model is configured in DB, which signals the caller
     * to fall back to Azure OpenAI env vars.
     */
    private static Model resolveModel() {
        try {
            Model model = AgentModelDao.instance.findFirstModel();
            if (model != null) {
                logger.infoAndAddToDb("resolveModel: found model in DB: name=" + model.getName() + ", type=" + model.getType());
            } else {
                logger.infoAndAddToDb("resolveModel: no model found in DB, will fall back to Azure OpenAI env vars");
            }
            return model;
        } catch (Exception e) {
            logger.error("resolveModel: failed to fetch model from DB: " + e.getMessage());
            return null;
        }
    }

    public BasicDBObject handle(BasicDBObject queryData) {
        String handlerName = this.getClass().getSimpleName();
        logger.infoAndAddToDb(handlerName + ".handle: starting");
        try {
            validate(queryData);
            logger.infoAndAddToDb(handlerName + ".handle: validation passed");

            String prompt = getPrompt(queryData);
            logger.infoAndAddToDb(handlerName + ".handle: prompt built, length=" + (prompt != null ? prompt.length() : 0));

            String rawResponse = call(prompt);
            logger.infoAndAddToDb(handlerName + ".handle: LLM call completed, response length=" + (rawResponse != null ? rawResponse.length() : 0));

            BasicDBObject resp = processResponse(rawResponse);
            logger.infoAndAddToDb(handlerName + ".handle: response processed successfully, keys=" + (resp != null ? resp.keySet() : "null"));
            return resp;
        } catch (ValidationException exception) {
            logger.error(handlerName + ".handle: validation failed: " + exception.getMessage());
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Invalid input parameters.");
            return resp;
        } catch (Exception e) {
            logger.error(handlerName + ".handle: error: " + e);
            BasicDBObject resp = new BasicDBObject();
            resp.put("error", "Internal server error: " + e.getMessage());
            return resp;
        }
    }

    protected abstract void validate(BasicDBObject queryData) throws ValidationException;

    protected abstract String getPrompt(BasicDBObject queryData);

    protected String call(String prompt) throws Exception {
        Model model = resolveModel();
        logger.infoAndAddToDb("call: resolved model=" + (model != null ? model.getName() + "/" + model.getType() : "null (env var fallback)"));
        String content = LLMProviderClient.callLLM(model, prompt, client);
        logger.infoAndAddToDb("call: raw content length=" + (content != null ? content.length() : 0));
        String cleaned = content != null ? cleanJSON(content) : null;
        logger.infoAndAddToDb("call: cleaned response length=" + (cleaned != null ? cleaned.length() : 0));
        return cleaned;
    }

    static String cleanJSON(String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            return "NOT_FOUND";
        }

        // Truncate at the last closing brace to remove any trailing notes
        int lastBrace = rawResponse.lastIndexOf('}');
        if (lastBrace != -1) {
            rawResponse = rawResponse.substring(0, lastBrace + 1);
        }

        // Start at the first opening brace to remove any forward notes
        int firstBrace = rawResponse.indexOf('{');
        if (firstBrace != -1) {
            rawResponse = rawResponse.substring(firstBrace);
        }
        return rawResponse.trim();
    }

    static String processOutput(String rawResponse) {
        try {
            return cleanJSON(rawResponse);
        } catch (Exception e) {
            logger.error("Failed to clean LLM response: " + rawResponse, e);
            return "NOT_FOUND";
        }
    }

    protected abstract BasicDBObject processResponse(String rawResponse);

}
