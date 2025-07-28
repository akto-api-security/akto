package com.akto.dto.jira_integration;

import com.akto.dto.test_editor.Info;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

import org.bson.types.ObjectId;

@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
public class JiraMetaData {
    
    private String issueTitle;
    private String hostStr;
    private String endPointStr;
    private String issueUrl;
    private String issueDescription;
    private TestingIssuesId testingIssueId;
    private ObjectId testSummaryId;
    private Map<String, Object> additionalIssueFields;
    private Info testInfo;
    private TestingRunIssues issue;


    public JiraMetaData(String issueTitle, String hostStr, String endPointStr, String issueUrl, String issueDescription,
        TestingIssuesId testingIssueId, ObjectId testSummaryId, Map<String, Object> additionalIssueFields) {
        this.issueTitle = issueTitle;
        this.hostStr = hostStr;
        this.endPointStr = endPointStr;
        this.issueUrl = issueUrl;
        this.issueDescription = issueDescription;
        this.testingIssueId = testingIssueId;
        this.testSummaryId = testSummaryId;
        this.additionalIssueFields = additionalIssueFields;
    }
}
