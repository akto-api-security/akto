package com.akto.dto.jira_integration;

import com.akto.dto.test_run_findings.TestingIssuesId;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
    private Map<String, String> customFields;
    private Set<String> labels;
}
