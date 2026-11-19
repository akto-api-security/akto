package com.akto.gpt.handlers.gpt_prompts;

class LlmCallException extends Exception {

    // perRequest: this input failed (truncated, refused) but the provider is fine, so don't skip it.
    final boolean perRequest;

    private LlmCallException(String message, boolean perRequest) {
        super(message);
        this.perRequest = perRequest;
    }

    static LlmCallException provider(String message) {
        return new LlmCallException(message, false);
    }

    static LlmCallException request(String message) {
        return new LlmCallException(message, true);
    }
}
