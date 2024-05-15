package com.akto.dto.upload;

public class FileUploadError {

    public FileUploadError() {
    }

    private String error;

    private ErrorType errorType;

    public enum ErrorType{
        WARNING,
        ERROR
    }

    public FileUploadError(String error, ErrorType errorType) {
        this.error = error;
        this.errorType = errorType;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(ErrorType errorType) {
        this.errorType = errorType;
    }
}
