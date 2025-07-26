package com.akto.notifications.slack;

import com.akto.dao.context.Context;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.log.LoggerMaker;
import com.slack.api.Slack;
import com.slack.api.webhook.WebhookResponse;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SlackSender {
    private static final LoggerMaker loggerMaker = new LoggerMaker(SlackAlerts.class, LoggerMaker.LogDb.DASHBOARD);
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    public static void sendAlert(int accountId, SlackAlerts alert) {
        sendAlert(accountId, alert, 0, null);
    }

    public static void sendAlert(int accountId, SlackAlerts alert, int slackWebhookId, List<SlackWebhook> listWebhooks) {
        executor.submit(() -> {
            Context.accountId.set(accountId);

            // Get payload
            if(alert == null || alert.toJson().isEmpty()) return;
            String payload = alert.toJson();
            SlackAlertType alertType = alert.getALERT_TYPE();
            if(listWebhooks == null || listWebhooks.isEmpty()) {
                loggerMaker.infoAndAddToDb("Slack Alert Type: " + alertType + " Info: " + "No slack webhook found.");
                return;
            }
            String webhookUrl = "";
            if(slackWebhookId > 0){
                for(Object webhook : listWebhooks) {
                    if(((SlackWebhook)webhook).getId() == slackWebhookId) {
                        webhookUrl = ((SlackWebhook)webhook).getWebhook();
                        break;
                    }
                }
            }

            if(webhookUrl.isEmpty()) {
                webhookUrl = getDefaultSlackWebhook(listWebhooks);
            }   

            // Try to send alert
            Slack slack = Slack.getInstance();
            int attempts = 0;
            final int[] retryDelays = {1000, 5000, 25000};

            while(attempts < retryDelays.length + 1) {
                try {
                    WebhookResponse response = slack.send(webhookUrl, payload);
                    int statusCode = response.getCode();

                    if(statusCode == 200) {
                        loggerMaker.infoAndAddToDb("Slack Alert Type: " + alertType + " Info: " + "Alert sent successfully.");
                        return;
                    } else {
                        loggerMaker.errorAndAddToDb("Slack Alert Type: " + alertType + " Error: " + response.getMessage());
                    }
                } catch(Exception e) {
                    loggerMaker.errorAndAddToDb("Slack Alert Type: " + alertType + " Error: " + e.getMessage());
                }

                if(attempts < retryDelays.length) {
                    try {
                        Thread.sleep(retryDelays[attempts]);
                    } catch(InterruptedException ie) {
                        loggerMaker.errorAndAddToDb("Slack Alert Type: " + alertType + " Error: " + "Thread was interrupted, failed to complete operation");
                        return;
                    }
                }

                attempts++;
            }

            loggerMaker.errorAndAddToDb("Slack Alert Type: " + alertType + " Error: " + "Failed to send Alert after multiple retries.");
        });
    }

    public static String getDefaultSlackWebhook(List<SlackWebhook> listWebhooks) {
        String webhookUrl ="";
        if (listWebhooks == null || listWebhooks.isEmpty()) {
            loggerMaker.infoAndAddToDb(" No slack webhook found.");
        } else {
            webhookUrl = listWebhooks.get(0).getWebhook();
        }
        return webhookUrl;
    }
}