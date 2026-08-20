package com.akto.notifications.teams;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.data.TestingAlertData;
import com.akto.notifications.webhook.WebhookSender;
import com.mongodb.client.model.Filters;

public class TeamsSender {
    private static final ExecutorService executor = Executors.newFixedThreadPool(1);
    private static final LoggerMaker loggerMaker = new LoggerMaker(TeamsSender.class, LogDb.TESTING);

    public static void sendAlert(int accountId, TestingAlertData alertData) {
        executor.submit(() -> {
            try {
                Context.accountId.set(accountId);

                List<CustomWebhook> teamsWebhooks = CustomWebhooksDao.instance.findAll(
                        Filters.and(
                                Filters.eq(
                                        CustomWebhook.WEBHOOK_TYPE,
                                        CustomWebhook.WebhookType.MICROSOFT_TEAMS.name()),
                                Filters.in(CustomWebhook.SELECTED_WEBHOOK_OPTIONS,
                                        CustomWebhook.WebhookOptions.TESTING_RUN_RESULTS.name())));

                if (teamsWebhooks == null || teamsWebhooks.isEmpty()) {
                    return;
                }

                for (CustomWebhook webhook : teamsWebhooks) {
                    try {
                        String payload = TeamsAlert.createAndGetBody(alertData, webhook);
                        int now = Context.now();
                        List<String> errors = new ArrayList<>();
                        WebhookSender.sendCustomWebhook(webhook, payload, errors, now, LogDb.TESTING);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error sending MS Teams alert for webhookId: " + webhook.getId());
                    }
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in TeamsSender.sendAlert for accountId: " + accountId);
            }
        });
    }

}
