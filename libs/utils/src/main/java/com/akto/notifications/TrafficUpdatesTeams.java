package com.akto.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.akto.dao.context.Context;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.TrafficUpdates.AlertResult;
import com.akto.notifications.TrafficUpdates.AlertType;
import com.akto.notifications.webhook.WebhookSender;

public class TrafficUpdatesTeams {

    public static void createAndSendTeamsTrafficAlerts(AlertType alertType, CustomWebhook webhook, String metricsUrl,
            AlertResult alertResult) {

        if (!alertResult.redAlertHosts.isEmpty()) {
            String payload = createAndGetBody(alertType, metricsUrl, alertResult.redAlertHosts);
            int now = Context.now();
            List<String> errors = new ArrayList<>();
            WebhookSender.sendCustomWebhook(webhook, payload, errors, now, LogDb.TESTING);
        }
        if (!alertResult.greenAlertHosts.isEmpty()) {
            String payload = createAndGetBody(alertType, metricsUrl, alertResult.greenAlertHosts);
            int now = Context.now();
            List<String> errors = new ArrayList<>();
            WebhookSender.sendCustomWebhook(webhook, payload, errors, now, LogDb.TESTING);

        }

    }

    private static String createAndGetBody(AlertType alertType, String metricsUrl,
            Set<String> hosts) {
        StringBuilder body = new StringBuilder();
        body.append("{\n" +
                "    \"type\": \"message\",\n" +
                "    \"attachments\": [\n" +
                "        {\n" +
                "            \"contentType\": \"application/vnd.microsoft.card.adaptive\",\n" +
                "            \"content\": {\n" +
                "                \"type\": \"AdaptiveCard\",\n" +
                "                \"$schema\": \"http://adaptivecards.io/schemas/adaptive-card.json\",\n" +
                "                \"version\": \"1.5\",\n" +
                "\"msteams\": { \"width\": \"full\" }," +
                "                \"body\": [");

        body.append("]\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}\n" +
                "");
        return body.toString();
    }

}
