package com.akto.utils;

import com.akto.log.LoggerMaker;
import com.akto.notifications.slack.CustomTextAlert;
import com.slack.api.Slack;

public class SlackUtils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(SlackUtils.class, LoggerMaker.LogDb.DATA_INGESTION);

    /**
     * Sends a Slack alert directly to a webhook without any DB lookup.
     * Webhook URL is read from the AKTO_SLACK_ALERT_WEBHOOK env variable.
     * If the env variable is not set, the alert is skipped.
     */
    public static void sendAlert(String message) {
        String webhookUrl = System.getenv("AKTO_SLACK_ALERT_WEBHOOK");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            loggerMaker.warn("No Slack webhook configured (AKTO_SLACK_ALERT_WEBHOOK not set), skipping alert: " + message);
            return;
        }
        try {
            String payload = new CustomTextAlert(message).toJson();
            Slack.getInstance().send(webhookUrl, payload);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to send Slack alert: " + e.getMessage());
        }
    }
}
