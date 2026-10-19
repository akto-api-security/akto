package com.akto.jobs.executors.ibmwatsonx;

import java.io.IOException;

/**
 * A failed call to IBM watsonx Orchestrate (or its sign-in service).
 *
 * {@link #isAuthFailure()}: the API key was rejected (invalid, expired or revoked); retrying
 * cannot help until the key is updated in the connector. {@link #isRetryable()}: a transient
 * failure (rate limit, IBM server error, network) worth retrying on the next run.
 */
public class WatsonxApiException extends IOException {
    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final boolean authFailure;

    public WatsonxApiException(int statusCode, String message, boolean authFailure) {
        super(message);
        this.statusCode = statusCode;
        this.authFailure = authFailure;
    }

    public WatsonxApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.authFailure = false;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isAuthFailure() {
        return authFailure;
    }

    public boolean isRetryable() {
        return !authFailure && (statusCode == 0 || statusCode == 408 || statusCode == 429 || statusCode >= 500);
    }
}
