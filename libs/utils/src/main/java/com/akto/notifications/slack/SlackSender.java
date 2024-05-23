package com.akto.notifications.slack;

import com.akto.dao.context.Context;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.log.LoggerMaker;
import com.akto.testing.HostDNSLookup;
import com.mongodb.BasicDBObject;
import com.slack.api.Slack;
import com.slack.api.webhook.WebhookResponse;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SlackSender {
    private static final LoggerMaker loggerMaker = new LoggerMaker(SlackAlerts.class, LoggerMaker.LogDb.DASHBOARD);
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    public static void sendAlert(int accountId, SlackAlerts alert) {
        if(alert == null || alert.toJson().isEmpty()) {
            loggerMaker.infoAndAddToDb("ERROR: " + "SlackAlert object is NULL or Empty.");
            return;
        }

        executor.submit(() -> {
            Context.accountId.set(accountId);

            try {
                String payload = alert.toJson();
                SlackAlertType alertType = alert.getALERT_TYPE();

                // Get slack webhook url
                List<SlackWebhook> listWebhooks = SlackWebhooksDao.instance.findAll(new BasicDBObject());
                if(listWebhooks == null || listWebhooks.isEmpty()) {
                    loggerMaker.infoAndAddToDb("Slack Alert Type: " + alertType + " Info: " + "No slack webhook found.");
                    return;
                }
                String webhookUrl = listWebhooks.get(0).getWebhook();

                URI uri = URI.create(webhookUrl);
                if(!HostDNSLookup.isRequestValid(uri.getHost())) {
                    throw new IllegalArgumentException("SSRF attack attempt");
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
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb("Slack Alert Type: " + alert.getALERT_TYPE() + " Error: " + e.getMessage());
            }
        });
    }

}