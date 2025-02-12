package com.akto.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.akto.dao.context.Context;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.TrafficUpdates.AlertResult;
import com.akto.notifications.TrafficUpdates.AlertType;
import com.akto.notifications.teams.CardTextBlock;
import com.akto.notifications.webhook.WebhookSender;

public class TrafficUpdatesTeams {

    public static void createAndSendTeamsTrafficAlerts(AlertType alertType, CustomWebhook webhook, String metricsUrl,
            AlertResult alertResult) {

        if (alertResult.redAlertHosts.isEmpty() && alertResult.greenAlertHosts.isEmpty()) {
            return;
        }

        String payload = createAndGetBody(alertType, metricsUrl, alertResult);
        int now = Context.now();
        List<String> errors = new ArrayList<>();
        WebhookSender.sendCustomWebhook(webhook, payload, errors, now, LogDb.TESTING);
    }

    private static String createAndGetBody(AlertType alertType, String metricsUrl,
            AlertResult alertResult) {
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

        String headingText = "Akto traffic alerts: ";
        body.append(CardTextBlock.createTextBlock(headingText, true, "bolder"));

        if (!alertResult.redAlertHosts.isEmpty()) {
            String payload = generateRedAlertPayload(alertResult.redAlertHosts, alertType, metricsUrl);
            if (payload != null) {
                body.append(CardTextBlock.createTextBlock(payload, true, "default", "attention"));
            }
        }

        if (!alertResult.greenAlertHosts.isEmpty()) {
            String payload = generateGreenAlertPayload(alertResult.greenAlertHosts, alertType, metricsUrl);
            if (payload != null) {

                body.append(CardTextBlock.createTextBlock(payload, true, "default", "good"));
            }
        }

        body.append("]\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}\n" +
                "");
        return body.toString();
    }

    public static String generateGreenAlertPayload(Set<String> hosts, AlertType alertType, String metricsUrl) {
        String text;

        switch (alertType) {
            case OUTGOING_REQUESTS_MIRRORING:
                text = "Resumed receiving traffic for hosts " + TrafficUpdates.prettifyHosts(hosts, 3)
                        + " Check metrics on dashboard: " + metricsUrl;
                break;
            case FILTERED_REQUESTS_RUNTIME:
                text = "Resumed processing traffic for hosts " + TrafficUpdates.prettifyHosts(hosts, 3)
                        + " Check metrics on dashboard: " + metricsUrl;
                break;
            default:
                return null;
        }
        return text;
    }

    public static String generateRedAlertPayload(Set<String> hosts, AlertType alertType, String metricsUrl) {
        String text;

        switch (alertType) {
            case OUTGOING_REQUESTS_MIRRORING:
                text = "Stopped receiving traffic for hosts " + TrafficUpdates.prettifyHosts(hosts, 3)
                        + " Check metrics on dashboard: " + metricsUrl;
                break;
            case FILTERED_REQUESTS_RUNTIME:
                text = "Stopped processing traffic for hosts " + TrafficUpdates.prettifyHosts(hosts, 3)
                        + " Check metrics on dashboard: " + metricsUrl;
                break;
            default:
                return null;
        }
        return text;
    }

}
