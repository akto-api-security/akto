package com.akto.notifications.slack;

import com.akto.dao.context.Context;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.slack.api.Slack;
import com.slack.api.webhook.WebhookResponse;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SlackSender {
    private static final LoggerMaker loggerMaker = new LoggerMaker(SlackAlerts.class, LoggerMaker.LogDb.DASHBOARD);
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    private static String slackWebhookUrl;
    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    static {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    if(slackWebhookUrl != null){
                        return;
                    }
                    Context.accountId.set(1000000);
                    SlackWebhook slackWebhook = SlackWebhooksDao.instance.findOne(Filters.eq("_id", 10));
                    if(slackWebhook != null){
                        slackWebhookUrl = slackWebhook.getWebhook();
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error in getting slack webhook url: " + e.getMessage());
                }
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    public static void sendAlert(int accountId, SlackAlerts alert) {
        sendAlert(accountId, alert, 0);
    }
    
    public static void sendAlert(int accountId, SlackAlerts alert, int slackChannelId) {
        executor.submit(() -> {
            Context.accountId.set(accountId);

            // Get payload
            if(alert == null || alert.toJson().isEmpty()) return;
            String payload = alert.toJson();
            SlackAlertType alertType = alert.getALERT_TYPE();

            String webhookUrl = "";
            if(slackChannelId > 0) {
                SlackWebhook listWebhook = SlackWebhooksDao.instance.findOne(Constants.ID, slackChannelId);
                webhookUrl = listWebhook.getWebhook();
            } 
            if(webhookUrl == null || webhookUrl.isEmpty()) {
                List<SlackWebhook> listWebhooks = SlackWebhooksDao.instance.findAll(Filters.empty());
                webhookUrl = getDefaultSlackWebhook(listWebhooks);
            }

            // Try to send alert
            Slack slack = Slack.getInstance();
            int attempts = 0;
            final int[] retryDelays = {1000, 5000, 25000};
            loggerMaker.infoAndAddToDb("Slack Alert Type: " + alertType + " Webhook URL: " + webhookUrl + " Channel ID: " + slackChannelId);

            while(attempts < retryDelays.length + 1) {
                try {
                    WebhookResponse response = slack.send(webhookUrl, payload);
                    int statusCode = response.getCode();

                    if(statusCode == 200) {
                        loggerMaker.infoAndAddToDb("Slack Alert Type: " + alertType + " Info: " + "Alert sent successfully.");
                        break;
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
                        break;
                    }
                }

                attempts++;
            }
            sendDuplicateAlert(payload, accountId);
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

    private static void sendDuplicateAlert(String payload, int accountId){
       if(accountId == 1723492815){
            Context.accountId.set(1000000);
            SlackWebhook slackWebhook = SlackWebhooksDao.instance.findOne(Filters.empty());
            if (slackWebhook == null) {
                Context.accountId.set(accountId);
                return;
            }
            try {
                String webhookUrl = slackWebhook.getWebhook();
                Slack.getInstance().send(webhookUrl, payload);
            } catch (Exception e) {
                // TODO: handle exception
            }
            Context.accountId.set(accountId);
       }
    }

    public static void sendFailedAlertToAkto(String customMessage, int accountId){
        String customMessageToSend = "Failed test: " + accountId + " Custom Message: " + customMessage;
        try {
            if(slackWebhookUrl == null || slackWebhookUrl.isEmpty()){
                return;
            }
            Slack.getInstance().send(slackWebhookUrl, customMessageToSend);
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

}