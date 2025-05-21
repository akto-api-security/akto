package com.akto.action.gpt.handlers.gpt_prompts;

import javax.validation.ValidationException;
import com.mongodb.BasicDBObject;

public abstract class PromptHandler {

    /**
     * Process the input query data and return a String response.
     */
    public abstract BasicDBObject handle(BasicDBObject queryData);

    /**
     * Validate input parameters.
     */
    protected abstract void validate(BasicDBObject queryData) throws ValidationException;

    /**
     * Return the prompt string to be sent to the AI.
     */
    protected abstract String getPrompt(BasicDBObject queryData);

    /**
     * Call the AI model with the provided prompt and parameters
     */
    protected abstract String call(String prompt, String model, Double temperature, int maxTokens);

    /**
     * Process the raw response (e.g., clean answer).
     */
    protected abstract BasicDBObject processResponse(String rawResponse);
}
