package com.akto.dto.notifications;

public class SlackWebhook {

    String webhook;
    int smallerDuration;
    int largerDuration;

    public SlackWebhook() {
    }

    public SlackWebhook(String webhook, int smallerDuration, int largerDuration) {
        this.webhook = webhook;
        this.smallerDuration = smallerDuration;
        this.largerDuration = largerDuration;
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

    @Override
    public String toString() {
        return "{" +
            " webhook='" + getWebhook() + "'" +
            ", smallerDuration='" + getSmallerDuration() + "'" +
            ", largerDuration='" + getLargerDuration() + "'" +
            "}";
    }

    
}
