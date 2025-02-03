package com.akto.notifications.slack;

import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.notifications.data.TestingAlertData;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.akto.notifications.slack.SlackAlertType.API_TEST_STATUS_ALERT;

public class APITestStatusAlert extends SlackAlerts {
    private String color;
    private String title;

    /**
     * Constructs a new {@code APITestStatusAlert} instance.
     * This constructor initializes a new daily test status alert with detailed api testing metrics.
     *
     * @param title The name of the API collection.
     * @param critical The total number of critical severity issues.
     * @param high The total number of high severity issues.
     * @param medium The total number of medium severity issues.
     * @param low The total number of low severity issues.
     * @param vulnerableApis The total number of vulnerable APIs identified.
     * @param newIssues The total number of new issues found.
     * @param totalApis The total number of APIs in the collection.
     * @param collection The name of the API collection being tested.
     * @param scanTimeInSeconds The duration of the test scan in seconds.
     * @param testType The type of the test conducted, either "ONE_TIME" or "SCHEDULED_DAILY".
     * @param nextTestRun The scheduled time for the next test run in Unix time format.
     * @param newIssuesList A list of new issues, detailed in the {@link NewIssuesModel}.
     * @param viewOnAktoURL The URL to view all the issues on the Akto dashboard.
     * @param exportReportUrl The URL to export the issue report.
     */
    public APITestStatusAlert(TestingAlertData data) {
        super(API_TEST_STATUS_ALERT);
        init(data.getTitle(), data.getCritical(), data.getHigh(), data.getMedium(), data.getLow(),
             data.getVulnerableApis(), data.getNewIssues(), data.getTotalApis(), data.getCollection(),
             data.getScanTimeInSeconds(), data.getTestType(), data.getNextTestRun(),
             data.getNewIssuesList(), data.getViewOnAktoURL(), data.getExportReportUrl());
    }

    private HorizontalFieldModel horizontalFieldModel;
    private List<FieldsModel> fieldsModelList;
    private List<NewIssuesModel> newIssuesModelList;
    private List<ActionButtonModel> actionButtonModelList;
    private String dashboardUrl;
    private void init(String title, int critical, int high, int medium, int low, int vulnerableApis, int newIssues, int totalApis, String collection, long scanTimeInSeconds, String testType, long nextTestRun, List<NewIssuesModel> newIssuesList, String viewOnAktoURL, String exportReportUrl) {
        List<SlackWebhook> listWebhooks = SlackWebhooksDao.instance.findAll(new BasicDBObject());
        if(listWebhooks != null && !listWebhooks.isEmpty()) {
            SlackWebhook slackWebhook = listWebhooks.get(0);
            dashboardUrl = slackWebhook.getDashboardUrl();
        } else {
            dashboardUrl = "app.akto.io";
        }

        this.color = "#D72C0D";
        this.title = "✅ Test run on " + title;

        Map<String, Integer> horizontalField = new HashMap<>();
        horizontalField.put("High", high);
        horizontalField.put("Medium", medium);
        horizontalField.put("Low", low);
        horizontalFieldModel = new HorizontalFieldModel(horizontalField);

        fieldsModelList = new ArrayList<>();
        String vulAPIs = (vulnerableApis == 0) ? "No vulnerability found" : Integer.toString(vulnerableApis);
        fieldsModelList.add(new FieldsModel("Vulnerable APIs", vulAPIs));
        if(newIssuesList != null && !newIssuesList.isEmpty()) {
            fieldsModelList.add(new FieldsModel("New issues", Integer.toString(newIssues)));
        }
        fieldsModelList.add(new FieldsModel("Total APIs", Integer.toString(totalApis)));
        if(collection != null && !collection.isEmpty()) fieldsModelList.add(new FieldsModel("Collection", collection));

        String scanTime;
        if (scanTimeInSeconds < 60) {
            scanTime = scanTimeInSeconds + " seconds";
        } else {
            long scanTimeInMinutes = scanTimeInSeconds / 60;
            scanTime = scanTimeInMinutes + " minute" + (scanTimeInSeconds < 120 ? "" : "s");
        }

        fieldsModelList.add(new FieldsModel("Scan time", scanTime));
        fieldsModelList.add(new FieldsModel("Test type", testType));
        if(nextTestRun != 0) fieldsModelList.add(new FieldsModel("Next test run", "<!date^"+nextTestRun+"^{date_pretty} at {time_secs}|February 18th, 2014 at 6:39 AM GMT>"));

        newIssuesModelList = newIssuesList;

        for(NewIssuesModel newIssuesModel : newIssuesModelList) {
            String oldUrl = newIssuesModel.getIssueUrl();
            newIssuesModel.setIssueUrl(dashboardUrl+"/dashboard/testing/issues/result/"+oldUrl);
        }

        actionButtonModelList = new ArrayList<>();
        actionButtonModelList.add(new ActionButtonModel("View on Akto", dashboardUrl+"/dashboard/testing/"+viewOnAktoURL+"#vulnerable"));
        actionButtonModelList.add(new ActionButtonModel("Export report", dashboardUrl+"/dashboard/testing/summary/"+exportReportUrl));
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
                String moreIssuesUrl = dashboardUrl+"/dashboard/issues";
                blocksList.add(createTextSection("<" + moreIssuesUrl + "|" + issuesLeftToShow + " more issues...>"));
            }
        } else {
            blocksList.add(createTextContext("No new issues found, have a nice day."));
        }

        blocksList.add(createActionButtons(actionButtonModelList));

        BasicDBObject blockObj = new BasicDBObject("blocks", blocksList).append("color", color);

        return toAttachment(blockObj).toJson();
    }
}
