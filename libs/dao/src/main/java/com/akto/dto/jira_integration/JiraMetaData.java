package com.akto.dto.jira_integration;

import com.akto.dto.test_run_findings.TestingIssuesId;

public class JiraMetaData {
    
    private String issueTitle;
    private String hostStr;
    private String endPointStr;
    private String issueUrl;
    private String issueDescription;
    private TestingIssuesId testingIssueId;

    public JiraMetaData () {}

    public JiraMetaData (String issueTitle,String hostStr,String endPointStr,String issueUrl,String issueDescription,TestingIssuesId testingIssueId){
        this.issueTitle = issueTitle;
        this.hostStr = hostStr;
        this.endPointStr = endPointStr;
        this.issueUrl = issueUrl;
        this.issueDescription = issueDescription; 
        this.testingIssueId = testingIssueId;
    }

    public String getIssueTitle() {
        return issueTitle;
    }

    public void setIssueTitle(String issueTitle) {
        this.issueTitle = issueTitle;
    }

    public String getHostStr() {
        return hostStr;
    }

    public void setHostStr(String hostStr) {
        this.hostStr = hostStr;
    }

    public String getEndPointStr() {
        return endPointStr;
    }

    public void setEndPointStr(String endPointStr) {
        this.endPointStr = endPointStr;
    }

    public String getIssueUrl() {
        return issueUrl;
    }

    public void setIssueUrl(String issueUrl) {
        this.issueUrl = issueUrl;
    }

    public String getIssueDescription() {
        return issueDescription;
    }

    public void setIssueDescription(String issueDescription) {
        this.issueDescription = issueDescription;
    }

    public TestingIssuesId getTestingIssueId() {
        return testingIssueId;
    }

    public void setTestingIssueId(TestingIssuesId testingIssueId) {
        this.testingIssueId = testingIssueId;
    }
}
