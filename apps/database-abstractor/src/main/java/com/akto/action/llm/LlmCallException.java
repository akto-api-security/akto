package com.akto.action.llm;

public class LlmCallException extends Exception {

    // perRequest: this input failed (truncated, refused) but the provider is fine, so don't skip it.
    public final boolean perRequest;

    private LlmCallException(String message, boolean perRequest) {
        super(message);
        this.perRequest = perRequest;
    }

    public static LlmCallException provider(String message) {
        return new LlmCallException(message, false);
    }

    public static LlmCallException request(String message) {
        return new LlmCallException(message, true);
    }
}
