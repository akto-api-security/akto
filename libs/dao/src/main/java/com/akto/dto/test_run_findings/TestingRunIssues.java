package com.akto.dto.test_run_findings;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;

import com.akto.util.enums.GlobalEnums.TicketSource;
import java.util.Arrays;
import java.util.List;

import org.bson.types.ObjectId;

public class TestingRunIssues {
    public static final String TEST_RUN_ISSUES_STATUS = "testRunIssueStatus";

    private GlobalEnums.TestRunIssueStatus testRunIssueStatus;
    private TestingIssuesId id;
    public static final String LAST_SEEN = "lastSeen";
    private int lastSeen;
    public static final String CREATION_TIME = "creationTime";
    private int creationTime;
    public static final String KEY_SEVERITY = "severity";
    private GlobalEnums.Severity severity;
    public static final String LATEST_TESTING_RUN_SUMMARY_ID = "latestTestingRunSummaryId";
    private ObjectId latestTestingRunSummaryId;
    public static final String IGNORE_REASON = "ignoreReason";
    private String ignoreReason;
    public static final String JIRA_ISSUE_URL = "jiraIssueUrl";
    private String jiraIssueUrl;
    public static final String TICKET_SOURCE = "ticketSource";
    private TicketSource ticketSource;
    public static final String TICKET_PROJECT_KEY = "ticketProjectKey";
    private String ticketProjectKey;
    public static final String TICKET_ID = "ticketId";
    private String ticketId;
    public static final String TICKET_LAST_UPDATED_AT = "ticketLastUpdatedAt";
    private int ticketLastUpdatedAt;
    public static final String AZURE_BOARDS_WORK_ITEM_URL = "azureBoardsWorkItemUrl";
    private String azureBoardsWorkItemUrl;
    public static final String LAST_UPDATED = "lastUpdated";
    private int lastUpdated;
    public static final String LAST_UPDATED_BY = "lastUpdatedBy";
    private String lastUpdatedBy;
    public static final String UNREAD = "unread";
    private boolean unread;
    private List<Integer> collectionIds;
    public static final String DESCRIPTION = "description";
    private String description;
    public static final String SERVICENOW_ISSUE_URL = "servicenowIssueUrl";
    private String servicenowIssueUrl;

    public static final String ID_API_COLLECTION_ID = Constants.ID + "." + TestingIssuesId.API_KEY_INFO + "." + ApiInfoKey.API_COLLECTION_ID;
    public static final String ID_URL = Constants.ID + "." + TestingIssuesId.API_KEY_INFO + "." + ApiInfoKey.URL;
    public static final String ID_METHOD = Constants.ID + "." + TestingIssuesId.API_KEY_INFO + "." + ApiInfoKey.METHOD;

    public TestingRunIssues(TestingIssuesId id, GlobalEnums.Severity severity, GlobalEnums.TestRunIssueStatus status,
                            int creationTime, int lastSeen, ObjectId latestTestingRunSummaryId, String jiraIssueUrl, int lastUpdated) {
        this(id, severity, status, creationTime, lastSeen, latestTestingRunSummaryId, jiraIssueUrl, lastUpdated, false);
    }

    public TestingRunIssues(TestingIssuesId id, GlobalEnums.Severity severity, GlobalEnums.TestRunIssueStatus status,
                            int creationTime, int lastSeen, ObjectId latestTestingRunSummaryId, String jiraIssueUrl, int lastUpdated, boolean unread) {
        this.creationTime = creationTime;
        this.lastSeen = lastSeen;
        this.id = id;
        if(id !=null && id.getApiInfoKey()!=null){
            this.collectionIds = Arrays.asList(id.getApiInfoKey().getApiCollectionId());
        }
        this.severity = severity;
        this.testRunIssueStatus = status;
        this.latestTestingRunSummaryId = latestTestingRunSummaryId;
        this.jiraIssueUrl = jiraIssueUrl;
        this.lastUpdated = lastUpdated;
        this.unread = unread;
    }

    public TestingRunIssues() {
    }

    public void setTestRunIssueStatus(GlobalEnums.TestRunIssueStatus testRunIssueStatus) {
        this.testRunIssueStatus = testRunIssueStatus;
    }

    public void setLastSeen(int lastSeen) {
        this.lastSeen = lastSeen;
    }

    public void setCreationTime(int creationTime) {
        this.creationTime = creationTime;
    }

    public void setSeverity(GlobalEnums.Severity severity) {
        this.severity = severity;
    }

    public void setId(TestingIssuesId id) {
        this.id = id;
        if(id !=null && id.getApiInfoKey()!=null){
            this.collectionIds = Arrays.asList(id.getApiInfoKey().getApiCollectionId());
        }
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

    public ObjectId getLatestTestingRunSummaryId() {
        return latestTestingRunSummaryId;
    }

    public void setLatestTestingRunSummaryId(ObjectId latestTestingRunSummaryId) {
        this.latestTestingRunSummaryId = latestTestingRunSummaryId;
    }

    public String getIgnoreReason() {
        return ignoreReason;
    }

    public void setIgnoreReason(String ignoreReason) {
        this.ignoreReason = ignoreReason;
    }

    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
    }
    public String getJiraIssueUrl() {
        return jiraIssueUrl;
    }

    public void setJiraIssueUrl(String jiraIssueUrl) {
        this.jiraIssueUrl = jiraIssueUrl;
    }


    public int getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(int lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    @Override
    public String toString() {
        return "TestingRunIssues{" +
                "testRunIssueStatus=" + testRunIssueStatus.toString() +
                ", id=" + id.toString() +
                ", lastSeen=" + lastSeen +
                ", creationTime=" + creationTime +
                ", severity=" + severity.toString() +
                ", latestTestingRunSummaryId=" + latestTestingRunSummaryId.toString() +
                ", ignoreReason='" + ignoreReason + '\'' +
                ", jiraIssueUrl='" + jiraIssueUrl + '\'' +
                ", lastUpdated=" + lastUpdated +
                ", collectionIds=" + collectionIds +
                '}';
    }

    public boolean isUnread() {
        return unread;
    }

    public void setUnread(boolean unread) {
        this.unread = unread;
    }

    public String getAzureBoardsWorkItemUrl() {
        return (azureBoardsWorkItemUrl != null && azureBoardsWorkItemUrl.contains("/_apis/wit/workItems/"))
                ? azureBoardsWorkItemUrl.replace("/_apis/wit/workItems/", "/_workItems/edit/")
                : azureBoardsWorkItemUrl;
    }

    public void setAzureBoardsWorkItemUrl(String azureBoardsWorkItemUrl) {
        this.azureBoardsWorkItemUrl = azureBoardsWorkItemUrl;
    }

    public TicketSource getTicketSource() {
        return ticketSource;
    }

    public void setTicketSource(TicketSource ticketSource) {
        this.ticketSource = ticketSource;
    }

    public String getTicketProjectKey() {
        return ticketProjectKey;
    }

    public void setTicketProjectKey(String ticketProjectKey) {
        this.ticketProjectKey = ticketProjectKey;
    }

    public int getTicketLastUpdatedAt() {
        return ticketLastUpdatedAt;
    }

    public void setTicketLastUpdatedAt(int ticketLastUpdatedAt) {
        this.ticketLastUpdatedAt = ticketLastUpdatedAt;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getServicenowIssueUrl() {
        return servicenowIssueUrl;
    }

    public void setServicenowIssueUrl(String servicenowIssueUrl) {
        this.servicenowIssueUrl = servicenowIssueUrl;
    }
}
