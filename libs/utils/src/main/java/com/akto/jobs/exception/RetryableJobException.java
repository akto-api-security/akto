package com.akto.jobs.exception;

public class RetryableJobException extends Exception {
    private static final long serialVersionUID = 1L;

    public RetryableJobException(String message) {
        super(message);
    }

    public RetryableJobException(String message, Throwable cause) {
        super(message, cause);
    }
}
