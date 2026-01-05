package com.akto.fast_discovery.dto;

/**
 * ApiExistenceResult - DTO for batch existence check results.
 * Extends ApiId to include the "exists" flag.
 */
public class ApiExistenceResult extends ApiId {
    private boolean exists;

    public ApiExistenceResult() {
        super();
    }

    public ApiExistenceResult(int apiCollectionId, String url, String method, boolean exists) {
        super(apiCollectionId, url, method);
        this.exists = exists;
    }

    public boolean isExists() {
        return exists;
    }

    public void setExists(boolean exists) {
        this.exists = exists;
    }

    @Override
    public String toString() {
        return "ApiExistenceResult{" +
                "apiCollectionId=" + getApiCollectionId() +
                ", url='" + getUrl() + '\'' +
                ", method='" + getMethod() + '\'' +
                ", exists=" + exists +
                '}';
    }
}
