package com.akto.dto.test_run_findings;

import com.akto.util.enums.GlobalEnums;
import org.bson.codecs.pojo.annotations.BsonProperty;

public class TestingRunIssues {


    public static final String TESTING_ISSUES_ID = "testing_issues_id";
    @BsonProperty(value = TESTING_ISSUES_ID)
    private final TestingIssuesId testingIssuesId;

    public static final String ISSUE_SEVERITY = "i_s";
    @BsonProperty(value = ISSUE_SEVERITY)
    private final GlobalEnums.Severity severity;

    public static final String CREATION_TIME = "c_t";
    @BsonProperty(value = CREATION_TIME)
    private final int creationTime;

    public static final String START_TIME = "s_t";
    @BsonProperty(value = START_TIME)
    private final int startTime;


    public static final String TEST_RUN_ISSUES = "t_r_i";
    @BsonProperty(value = TEST_RUN_ISSUES)
    private GlobalEnums.TestRunIssueStatus testRunIssueStatus;

    TestingRunIssues(TestingIssuesId testingIssuesId, GlobalEnums.Severity severity,
                     int creationTime, int startTime) {
        this.creationTime = creationTime;
        this.startTime = startTime;
        this.testingIssuesId = testingIssuesId;
        this.severity = severity;
    }

    public TestingIssuesId getTestingIssuesId() {
        return testingIssuesId;
    }

    public int getCreationTime() {
        return creationTime;
    }

    public GlobalEnums.TestRunIssueStatus getTestRunIssueStatus() {
        return testRunIssueStatus;
    }

    public int getStartTime() {
        return startTime;
    }

    public GlobalEnums.Severity getSeverity() {
        return severity;
    }
}
