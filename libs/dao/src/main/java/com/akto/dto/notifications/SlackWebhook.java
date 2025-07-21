package com.akto.dto.notifications;

import org.bson.codecs.pojo.annotations.BsonId;

public class SlackWebhook {

    @BsonId
    int id;

    String webhook;
    String slackWebhookName;
    int smallerDuration;
    int largerDuration;
    int timestamp;
    String userEmail;
    String dashboardUrl;
    int lastSentTimestamp;
    int frequencyInSeconds;

    public SlackWebhook() {
    }

    public SlackWebhook(int id, String webhook, int smallerDuration, int largerDuration, int timestamp, String userEmail, String dashboardUrl, int lastSentTimestamp, int frequencyInSeconds, String slackWebhookName) {
        this.id = id;
        this.webhook = webhook;
        this.smallerDuration = smallerDuration;
        this.largerDuration = largerDuration;
        this.timestamp = timestamp;
        this.userEmail = userEmail;
        this.dashboardUrl = dashboardUrl;
        this.lastSentTimestamp = lastSentTimestamp;
        this.frequencyInSeconds = frequencyInSeconds;
        this.slackWebhookName = slackWebhookName;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
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

    public String getDashboardUrl() {
        return this.dashboardUrl;
    }

    public void setDashboardUrl(String dashboardUrl) {
        this.dashboardUrl = dashboardUrl;
    }

    public int getLastSentTimestamp() {
        return this.lastSentTimestamp;
    }

    public void setLastSentTimestamp(int lastSentTimestamp) {
        this.lastSentTimestamp = lastSentTimestamp;
    }

    public int getFrequencyInSeconds() {
        return this.frequencyInSeconds;
    }

    public void setFrequencyInSeconds(int frequencyInSeconds) {
        this.frequencyInSeconds = frequencyInSeconds;
    }

    public String getSlackWebhookName() {
        return slackWebhookName;
    }

    public void setSlackWebhookName(String slackWebhookName) {
        this.slackWebhookName = slackWebhookName;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", webhook='" + getWebhook() + "'" +
            ", smallerDuration='" + getSmallerDuration() + "'" +
            ", largerDuration='" + getLargerDuration() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            ", userEmail='" + getUserEmail() + "'" +
            ", dashboardUrl='" + getDashboardUrl() + "'" +
            ", LastSentTimestamp='" + getLastSentTimestamp() + "'" +
            ", FrequencyInSeconds='" + getFrequencyInSeconds() + "'" +
            "}";
    }
}
