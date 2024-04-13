package com.akto.dto;

import java.util.Map;
import java.util.UUID;

import com.akto.dao.context.Context;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;

public class Attempt {

    @BsonDiscriminator
    public static abstract class AttemptResult {}

    @BsonDiscriminator
    public static class Success extends AttemptResult {
        private Map<String,List<String>> requestHeaders;
        private String requestBody;
    
        private Map<String, List<String>> responseHeaders;
        private String responseBody;
        private int errorCode;
        private int timeTakenInMillis;  

        public Success() {}

        public Success(Map<String,List<String>> requestHeaders, String requestBody, Map<String,List<String>> responseHeaders, String responseBody, int errorCode, int timeTakenInMillis) {
            this.requestHeaders = requestHeaders;
            this.requestBody = requestBody;
            this.responseHeaders = responseHeaders;
            this.responseBody = responseBody;
            this.errorCode = errorCode;
            this.timeTakenInMillis = timeTakenInMillis;
        }    

        public Map<String,List<String>> getRequestHeaders() {
            return this.requestHeaders;
        }
    
        public void setRequestHeaders(Map<String,List<String>> requestHeaders) {
            this.requestHeaders = requestHeaders;
        }
    
        public String getRequestBody() {
            return this.requestBody;
        }
    
        public void setRequestBody(String requestBody) {
            this.requestBody = requestBody;
        }
    
        public Map<String,List<String>> getResponseHeaders() {
            return this.responseHeaders;
        }
    
        public void setResponseHeaders(Map<String,List<String>> responseHeaders) {
            this.responseHeaders = responseHeaders;
        }
    
        public String getResponseBody() {
            return this.responseBody;
        }
    
        public void setResponseBody(String responseBody) {
            this.responseBody = responseBody;
        }
    
        public int getErrorCode() {
            return this.errorCode;
        }
    
        public void setErrorCode(int errorCode) {
            this.errorCode = errorCode;
        }
    
        public int getTimeTakenInMillis() {
            return this.timeTakenInMillis;
        }
    
        public void setTimeTakenInMillis(int timeTakenInMillis) {
            this.timeTakenInMillis = timeTakenInMillis;
        }  
        
        @Override
        public String toString() {
            return "{" +
            " requestBody='" + getRequestBody()+ "'" +
            " responseBody='" + getResponseBody() + "'" +
            " errorCode='" + getErrorCode() + "'" +
            " timeTakenInMillis='" + getTimeTakenInMillis() + "'" +
            "}";
        }
    }

    @BsonDiscriminator
    public static class Err extends AttemptResult {

        private String errorMessage;

        public Err() {}

        public Err(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getErrorMessage() {
            return this.errorMessage;
        }

        public void setErrorMessage(String errorMessage){ 
            this.errorMessage = errorMessage;
        }

        @Override
        public String toString() {
            return "{" +
            " errorMessage='" + getErrorMessage() + "'" +
            "}";
        }
    }

    public enum Status {
        CREATED, NOT_CREATED, FAILED, SENT;
    }

    private int timestamp;

    @BsonId
    private String id;
    private String uri;
    private String method;
    private AttemptResult attemptResult;
    private String status;
    private boolean isHappy;

    public Attempt() {
    }

    public Attempt(int timestamp, String id, String uri, String method, AttemptResult attemptResult, boolean isHappy) {
        this.timestamp = timestamp;
        this.id = id;
        this.uri = uri;
        this.method = method;
        this.attemptResult = attemptResult;
        this.isHappy = isHappy;
        if (attemptResult instanceof Err) {
            this.status = Status.NOT_CREATED.toString();
        } else {
            this.status = Status.CREATED.toString();
        }
    }

    public Attempt(String uri, String method, AttemptResult attemptResult, boolean isHappy) {
        this (Context.now(), UUID.randomUUID().toString(), uri, method, attemptResult, isHappy);
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUri() {
        return this.uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public AttemptResult getAttemptResult() {
        return this.attemptResult;
    }

    public void setAttemptResult(AttemptResult attemptResult) {
        this.attemptResult = attemptResult;
    }

    public String getStatus() {
        return this.status.toString();
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean getIsHappy() {
        return this.isHappy;
    }

    public void setIsHappy(boolean isHappy) {
        this.isHappy = isHappy;
    }

    @Override
    public String toString() {
        return "{" +
            " timestamp='" + getTimestamp() + "'" +
            ", uuid='" + getId() + "'" +
            ", uri='" + getUri() + "'" +
            ", method='" + getMethod() + "'" +
            ", status='" + getStatus() + "'" +
            ", isHappy='" + getIsHappy() + "'" +
            ", attemptResult='" + getAttemptResult() + "'" +
            "}";
    }
}

