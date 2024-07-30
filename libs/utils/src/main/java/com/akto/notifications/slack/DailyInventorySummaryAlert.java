package com.akto.notifications.slack;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.List;

import static com.akto.notifications.slack.SlackAlertType.DAILY_API_INVENTORY_SUMMARY_ALERT;

public class DailyInventorySummaryAlert extends SlackAlerts {

    private String title;
    private final String color;
    private List<FieldsModel> inventorySummaryFields;
    private List<ActionButtonModel> actionButtonModelList;

    public DailyInventorySummaryAlert(int newEndpoints, int newSensitiveEndpoints, int newSensitiveParameters, int newParameters, String dashboardUrl) {
        super(DAILY_API_INVENTORY_SUMMARY_ALERT);
        this.color = "#D72C0D";
        init(newEndpoints, newSensitiveEndpoints, newSensitiveParameters, newParameters, dashboardUrl);
    }

    private void init(int newEndpoints, int newSensitiveEndpoints, int newSensitiveParameters, int newParameters, String dashboardUrl) {
        this.title = "\uD83D\uDCCA" + "Daily inventory summary";

        inventorySummaryFields = new ArrayList<>();
        inventorySummaryFields.add(new FieldsModel("New endpoints", Integer.toString(newEndpoints)));
        inventorySummaryFields.add(new FieldsModel("New sensitive endpoints", Integer.toString(newSensitiveEndpoints)));
        inventorySummaryFields.add(new FieldsModel("New sensitive parameters", Integer.toString(newSensitiveParameters)));
        inventorySummaryFields.add(new FieldsModel("New parameters", Integer.toString(newParameters)));
//        inventorySummaryFields.add(new FieldsModel("Test coverage", Integer.toString(testCoverage).concat("%")));

        actionButtonModelList = new ArrayList<>();
        String viewOnAktoURL = dashboardUrl+"/dashboard/observe/inventory";
        actionButtonModelList.add(new ActionButtonModel("View on Akto", viewOnAktoURL));

    }

    @Override
    public String toJson() {
        BasicDBList blocksList = new BasicDBList();
        blocksList.add(createHeader(title));
        blocksList.add(createFieldSection(inventorySummaryFields));
        blocksList.add(createActionButtons(actionButtonModelList));

        BasicDBObject blockObj = new BasicDBObject("blocks", blocksList).append("color", color);

        return toAttachment(blockObj).toJson();
    }
}
