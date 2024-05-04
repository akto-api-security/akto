package com.akto.notifications.slack;

public class NewIssuesModel {
    private final String issueTitle;
    private String issueUrl;
    private final int apisAffected;
    private final String time;

    public NewIssuesModel(String issueTitle, String issueUrl, int apisAffected, int unixTime) {
        this.time = "<!date^"+unixTime+"^{date_pretty} at {time_secs}|February 18th, 2014 at 6:39 AM GMT>";
        this.issueTitle = issueTitle;
        this.issueUrl = issueUrl;
        this.apisAffected = apisAffected;
    }

    public String getIssueTitle() {
        return issueTitle;
    }

    public String getIssueUrl() {
        return issueUrl;
    }

    public void setIssueUrl(String issueUrl) {
        this.issueUrl = issueUrl;
    }

    public int getApisAffected() {
        return apisAffected;
    }

    public String getTime() {
        return time;
    }
}
