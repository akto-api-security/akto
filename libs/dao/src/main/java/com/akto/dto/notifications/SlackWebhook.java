package com.akto.dto.notifications;

import org.bson.codecs.pojo.annotations.BsonId;

public class SlackWebhook {

    @BsonId
    int id;

    String webhook;
    int smallerDuration;
    int largerDuration;
    int timestamp;
    String userEmail;

    public SlackWebhook() {
    }

    public SlackWebhook(String webhook, int smallerDuration, int largerDuration, int timestamp, String userEmail, int id) {
        this.webhook = webhook;
        this.smallerDuration = smallerDuration;
        this.largerDuration = largerDuration;
        this.timestamp = timestamp;
        this.userEmail = userEmail;
        this.id = id;
    }

    public String getWebhook() {
        return this.webhook;
    }

    public void setWebhook(String webhook) {
        this.webhook = webhook;
    }

    public int getSmallerDuration() {
        return this.smallerDuration;
    }

    public void setSmallerDuration(int smallerDuration) {
        this.smallerDuration = smallerDuration;
    }

    public int getLargerDuration() {
        return this.largerDuration;
    }

    public void setLargerDuration(int largerDuration) {
        this.largerDuration = largerDuration;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getUserEmail() {
        return this.userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "{" +
            " webhook='" + getWebhook() + "'" +
            ", smallerDuration='" + getSmallerDuration() + "'" +
            ", largerDuration='" + getLargerDuration() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            ", userEmail='" + getUserEmail() + "'" +
            ", id='" + getId() + "'" +
            "}";
    }

}
