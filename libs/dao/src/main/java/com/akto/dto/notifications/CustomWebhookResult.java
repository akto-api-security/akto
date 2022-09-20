package com.akto.dto.notifications;

import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

public class CustomWebhookResult {
    
    @BsonId
    int id;

    int webhookId;
    // TODO: use original req,res

    int responseStatusCode;
    String responseBody;
    String requestUrl;
    Map<String,List<String>> requestHeaders;
    String requestBody;

    public CustomWebhookResult() {
    }

    public CustomWebhookResult(int id, int webhookId, int responseStatusCode, String responseBody, String requestUrl,
            Map<String, List<String>> requestHeaders, String requestBody) {
        this.id = id;
        this.webhookId = webhookId;
        this.responseStatusCode = responseStatusCode;
        this.responseBody = responseBody;
        this.requestUrl = requestUrl;
        this.requestHeaders = requestHeaders;
        this.requestBody = requestBody;
    }

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public int getWebhookId() {
        return webhookId;
    }
    public void setWebhookId(int webhookId) {
        this.webhookId = webhookId;
    }
    public int getResponseStatusCode() {
        return responseStatusCode;
    }
    public void setResponseStatusCode(int responseStatusCode) {
        this.responseStatusCode = responseStatusCode;
    }
    public String getResponseBody() {
        return responseBody;
    }
    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }
    public String getRequestUrl() {
        return requestUrl;
    }
    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }
    public Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }
    public void setRequestHeaders(Map<String, List<String>> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }
    public String getRequestBody() {
        return requestBody;
    }
    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }    
}
