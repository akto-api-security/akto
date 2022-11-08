package com.akto.dto.test_run_findings;

import com.akto.util.enums.GlobalEnums;

public class TestingRunIssues {
    //    public static final String TESTING_ISSUES_ID = "testing_issues_id";
    //    @BsonProperty(value = TESTING_ISSUES_ID)

    //    public static final String ISSUE_SEVERITY = "i_s";
    //    @BsonProperty(value = ISSUE_SEVERITY)

    //    public static final String CREATION_TIME = "c_t";
    //    @BsonProperty(value = CREATION_TIME)

    //    public static final String START_TIME = "s_t";
    //    @BsonProperty(value = START_TIME)


    //    public static final String TEST_RUN_ISSUES = "t_r_i";
    //    @BsonProperty(value = TEST_RUN_ISSUES)
    private GlobalEnums.TestRunIssueStatus testRunIssueStatus;
    private final TestingIssuesId id;
    private final int startTime;
    private final int creationTime;
    private final GlobalEnums.Severity severity;

    public TestingRunIssues(TestingIssuesId id, GlobalEnums.Severity severity,
                     int creationTime, int startTime) {
        this.creationTime = creationTime;
        this.startTime = startTime;
        this.id = id;
        this.severity = severity;
    }

    public TestingIssuesId getId() {
        return id;
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
