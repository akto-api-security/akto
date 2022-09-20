package com.akto.dto.notifications;

import org.bson.codecs.pojo.annotations.BsonId;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;

public class CustomWebhookResult {
    
    @BsonId
    int id;

    int webhookId;
    OriginalHttpRequest originalHttpRequest;
    OriginalHttpResponse originalHttpResponse;

    public CustomWebhookResult() {
    }

    public CustomWebhookResult(int id, int webhookId, OriginalHttpRequest originalHttpRequest,
            OriginalHttpResponse originalHttpResponse) {
        this.id = id;
        this.webhookId = webhookId;
        this.originalHttpRequest = originalHttpRequest;
        this.originalHttpResponse = originalHttpResponse;
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
    public OriginalHttpRequest getOriginalHttpRequest() {
        return originalHttpRequest;
    }
    public void setOriginalHttpRequest(OriginalHttpRequest originalHttpRequest) {
        this.originalHttpRequest = originalHttpRequest;
    }
    public OriginalHttpResponse getOriginalHttpResponse() {
        return originalHttpResponse;
    }
    public void setOriginalHttpResponse(OriginalHttpResponse originalHttpResponse) {
        this.originalHttpResponse = originalHttpResponse;
    }    
}
