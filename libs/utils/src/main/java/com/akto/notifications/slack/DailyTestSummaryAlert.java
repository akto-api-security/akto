package com.akto.notifications.slack;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.notifications.slack.SlackAlertType.DAILY_API_TEST_SUMMARY_ALERT;

public class DailyTestSummaryAlert extends SlackAlerts {
    private final String color;
    private final String title;
    private final String dashboardUrl;

    public DailyTestSummaryAlert(int testRuns, int apisTested, int vulnerableApis, int newIssues, int high, int medium, int low, String topCategories, long totalScanTimeInSeconds, int testCoverage, List<NewIssuesModel> newIssuesList, String dashboardUrl, String viewOnAktoURL, String exportReportUrl) {
        super(DAILY_API_TEST_SUMMARY_ALERT);
        String titleEmoji = newIssuesList != null && !newIssuesList.isEmpty() ? "\uD83D\uDCCA" : "✅";
        this.title = titleEmoji + " Daily test run summary";
        this.color = (newIssuesList != null && !newIssuesList.isEmpty()) ? "#D72C0D" : "#007F5F";
        this.dashboardUrl = dashboardUrl;

        init(testRuns, apisTested, vulnerableApis, newIssues, high, medium, low, topCategories, totalScanTimeInSeconds, testCoverage, newIssuesList, viewOnAktoURL, exportReportUrl);
    }


    private HorizontalFieldModel horizontalFieldModel;
    private List<FieldsModel> fieldsModelList;
    private List<NewIssuesModel> newIssuesModelList;
    private List<ActionButtonModel> actionButtonModelList;
    private void init(int testRuns, int apisTested, int vulnerableApis, int newIssues, int high, int medium, int low, String topCategories, long totalScanTimeInSeconds, int testCoverage, List<NewIssuesModel> newIssuesList, String viewOnAktoURL, String exportReportUrl) {
        Map<String, Integer> horizontalField = new HashMap<>();
        horizontalField.put("Test runs", testRuns);
        horizontalField.put("APIs tested", apisTested);
        horizontalField.put("Vulnerable APIs", vulnerableApis);
        horizontalField.put("New issues", newIssues);
        horizontalFieldModel = new HorizontalFieldModel(horizontalField);

        fieldsModelList = new ArrayList<>();
        if(newIssuesList != null && !newIssuesList.isEmpty()) {
            fieldsModelList.add(new FieldsModel("High", Integer.toString(high)));
            fieldsModelList.add(new FieldsModel("Medium", Integer.toString(medium)));
            fieldsModelList.add(new FieldsModel("Low", Integer.toString(low)));
            if(!topCategories.isEmpty()) fieldsModelList.add(new FieldsModel("Top Categories", topCategories));
        }
        String scanTime;
        if (totalScanTimeInSeconds < 60) {
            scanTime = totalScanTimeInSeconds + " seconds";
        } else {
            long scanTimeInMinutes = totalScanTimeInSeconds / 60;
            scanTime = scanTimeInMinutes + " minute" + (totalScanTimeInSeconds < 120 ? "" : "s");
        }
        fieldsModelList.add(new FieldsModel("Total scan time", scanTime));
        fieldsModelList.add(new FieldsModel("Test coverage", testCoverage+"%"));

        newIssuesModelList = newIssuesList;

        for(NewIssuesModel newIssuesModel : newIssuesModelList) {
            String oldUrl = newIssuesModel.getIssueUrl();
            newIssuesModel.setIssueUrl(dashboardUrl+"/dashboard/testing/issues/result/"+oldUrl);
        }

        actionButtonModelList = new ArrayList<>();
        actionButtonModelList.add(new ActionButtonModel("View on Akto", dashboardUrl + viewOnAktoURL));
        actionButtonModelList.add(new ActionButtonModel("Export report", dashboardUrl + exportReportUrl));
    }

    @Override
    public String toJson() {
        BasicDBList blocksList = new BasicDBList();
        blocksList.add(createHeader(title));
        if(!horizontalFieldModel.isEmpty()) blocksList.add(createTextSection(horizontalFieldModel.toString()));
        blocksList.add(createDivider());
        blocksList.add(createFieldSection(fieldsModelList));
        blocksList.add(createDivider());
        if(newIssuesModelList != null && !newIssuesModelList.isEmpty()) {
            for(int i = 0; i < Math.min(5, newIssuesModelList.size()); i++) {
                blocksList.addAll(createNewIssuesSection(i+1, newIssuesModelList.get(i)));
            }

            if(newIssuesModelList.size() > 5) {
                int issuesLeftToShow = newIssuesModelList.size() - 5;
                String moreIssuesUrl = dashboardUrl + "/dashboard";
                blocksList.add(createTextSection("<" + moreIssuesUrl + "|" + issuesLeftToShow + " more issues...>"));
            }
        } else {
            blocksList.add(createTextContext("No issues found, have a nice day."));
        }

        blocksList.add(createActionButtons(actionButtonModelList));

        BasicDBObject blockObj = new BasicDBObject("blocks", blocksList).append("color", color);

        return toAttachment(blockObj).toJson();
    }
}