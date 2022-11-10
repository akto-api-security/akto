package com.akto.dto.test_run_findings;

import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.util.enums.GlobalEnums;

public class TestingRunIssues {
    //    public static final String TESTING_ISSUES_ID = "testing_issues_id";
    //    @BsonProperty(value = TESTING_ISSUES_ID)

    //    @BsonProperty(value = ISSUE_SEVERITY)

    //    @BsonProperty(value = CREATION_TIME)

    //    @BsonProperty(value = START_TIME)


    //    @BsonProperty(value = TEST_RUN_ISSUES)
    public static final String TEST_RUN_ISSUES_STATUS = "testRunIssueStatus";
    private final GlobalEnums.TestRunIssueStatus testRunIssueStatus;
    private final TestingIssuesId id;

    public static final String LAST_SEEN = "lastSeen";
    private final int lastSeen;

    public static final String CREATION_TIME = "creationTime";
    private final int creationTime;


    public static final String SEVERITY = "severity";
    private final GlobalEnums.Severity severity;

    public TestingRunIssues(TestingIssuesId id, GlobalEnums.Severity severity, GlobalEnums.TestRunIssueStatus status,
                     int creationTime, int lastSeen) {
        this.creationTime = creationTime;
        this.lastSeen = lastSeen;
        this.id = id;
        this.severity = severity;
        this.testRunIssueStatus = status;
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

    public int getLastSeen() {
        return lastSeen;
    }

    public GlobalEnums.Severity getSeverity() {
        return severity;
    }
}
