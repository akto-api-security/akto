package com.akto.notifications.teams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.akto.dto.notifications.CustomWebhook;
import com.akto.notifications.data.TestingAlertData;

public class TeamsAlert {

    public static String createAndGetBody(TestingAlertData data, CustomWebhook webhook) {
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

        String headingText = "Akto test run results for " + data.getTitle();

        body.append(CardTextBlock.createTextBlock(headingText, true, "bolder"));

        String tableHeading = "Issue list: ";
        body.append(CardTextBlock.createTextBlock(tableHeading, true, "default"));

        List<List<String>> tableData = new ArrayList<>();

        tableData.add(Arrays.asList("Severity", "No. of issues"));
        tableData.add(Arrays.asList("Critical", String.valueOf(data.getCritical())));
        tableData.add(Arrays.asList("High", String.valueOf(data.getHigh())));
        tableData.add(Arrays.asList("Medium", String.valueOf(data.getMedium())));
        tableData.add(Arrays.asList("Low", String.valueOf(data.getLow())));

        body.append(CardDataTable.createTable(tableData));

        String dashboardLink = "View the complete results on Akto: " + webhook.getDashboardUrl() + "/dashboard/testing/"
                + data.getViewOnAktoURL() + "#vulnerable";

        body.append(CardTextBlock.createTextBlock(dashboardLink, true, "default"));


        body.append("]\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}\n" +
                "");
        return body.toString();
    }

}
