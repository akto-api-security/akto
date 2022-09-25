package com.akto.dto.notifications;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonId;

public class CustomWebhookResult {
    
    @BsonId
    int id;

    int webhookId;
    String userEmail;
    int timestamp;
    String message;
    List<String> errors;

    public CustomWebhookResult() {
    }

    public CustomWebhookResult(int id, int webhookId, String userEmail, int timestamp, String message,
            List<String> errors) {
        this.id = id;
        this.webhookId = webhookId;
        this.userEmail = userEmail;
        this.timestamp = timestamp;
        this.message = message;
        this.errors = errors;
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
    public String getUserEmail() {
        return userEmail;
    }
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
    public int getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public List<String> getErrors() {
        return errors;
    }
    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
