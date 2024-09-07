package com.akto.dto.user_journey;

public class UserJourneyEvents {

    public static final String USER_SIGNUP = "userSignup";
    private EventInfo userSignup;

    public static final String TEAM_INVITE_SENT = "teamInviteSent";
    private EventInfo teamInviteSent;

    public static final String TRAFFIC_CONNECTOR_ADDED = "trafficConnectorAdded";
    private EventInfo trafficConnectorAdded;

    public static final String INVENTORY_SUMMARY = "inventorySummary";
    private EventInfo inventorySummary;

    public static final String FIRST_TEST_RUN = "firstTestRun";
    private EventInfo firstTestRun;

    public static final String AUTH_TOKEN_SETUP = "authTokenSetup";
    private EventInfo authTokenSetup;

    public static final String ISSUES_PAGE = "issuesPage";
    private EventInfo issuesPage;

    public static final String JIRA_INTEGRATED = "jiraIntegrated";
    private EventInfo jiraIntegrated;

    public static final String REPORT_EXPORTED = "reportExported";
    private EventInfo reportExported;

    public static final String CICD_SCHEDULED_TESTS = "cicdScheduledTests";
    private EventInfo cicdScheduledTests;

    public static final String FIRST_CUSTOM_TEMPLATE_CREATED = "firstCustomTemplateCreated";
    private EventInfo firstCustomTemplateCreated;

    public static final String CUSTOM_TEMPLATE_UNUSED = "customTemplateUnused";
    private EventInfo customTemplateUnused;

    public static final String SLACK_INTEGRATION = "slackIntegration";
    private EventInfo slackIntegration;

    public static final String POC_COMPLETED = "pocCompleted";
    private EventInfo pocCompleted;

    public static final String ACCOUNT_BLOCKED = "accountBlocked";
    private EventInfo accountBlocked;
    
    public static class EventInfo {
        public static final String SHOULD_TRIGGER_EVENT = "shouldTriggerEvent";
        private boolean shouldTriggerEvent;

        public static final String IS_EVENT_SENT = "isEventSent";
        private boolean isEventSent;

        public boolean isShouldTriggerEvent() {
            return shouldTriggerEvent;
        }

        public void setShouldTriggerEvent(boolean shouldTriggerEvent) {
            this.shouldTriggerEvent = shouldTriggerEvent;
        }

        public boolean isEventSent() {
            return isEventSent;
        }

        public void setIsEventSent(boolean isEventSent) {
            this.isEventSent = isEventSent;
        }
    }

    public UserJourneyEvents() {}

    public EventInfo getUserSignup() {
        return userSignup;
    }

    public void setUserSignup(EventInfo userSignup) {
        this.userSignup = userSignup;
    }

    public EventInfo getTeamInviteSent() {
        return teamInviteSent;
    }

    public void setTeamInviteSent(EventInfo teamInviteSent) {
        this.teamInviteSent = teamInviteSent;
    }

    public EventInfo getTrafficConnectorAdded() {
        return trafficConnectorAdded;
    }

    public void setTrafficConnectorAdded(EventInfo trafficConnectorAdded) {
        this.trafficConnectorAdded = trafficConnectorAdded;
    }

    public EventInfo getInventorySummary() {
        return inventorySummary;
    }

    public void setInventorySummary(EventInfo inventorySummary) {
        this.inventorySummary = inventorySummary;
    }

    public EventInfo getFirstTestRun() {
        return firstTestRun;
    }

    public void setFirstTestRun(EventInfo firstTestRun) {
        this.firstTestRun = firstTestRun;
    }

    public EventInfo getAuthTokenSetup() {
        return authTokenSetup;
    }

    public void setAuthTokenSetup(EventInfo authTokenSetup) {
        this.authTokenSetup = authTokenSetup;
    }

    public EventInfo getIssuesPage() {
        return issuesPage;
    }

    public void setIssuesPage(EventInfo issuesPage) {
        this.issuesPage = issuesPage;
    }

    public EventInfo getJiraIntegrated() {
        return jiraIntegrated;
    }

    public void setJiraIntegrated(EventInfo jiraIntegrated) {
        this.jiraIntegrated = jiraIntegrated;
    }

    public EventInfo getReportExported() {
        return reportExported;
    }

    public void setReportExported(EventInfo reportExported) {
        this.reportExported = reportExported;
    }

    public EventInfo getCicdScheduledTests() {
        return cicdScheduledTests;
    }

    public void setCicdScheduledTests(EventInfo cicdScheduledTests) {
        this.cicdScheduledTests = cicdScheduledTests;
    }

    public EventInfo getFirstCustomTemplateCreated() {
        return firstCustomTemplateCreated;
    }

    public void setFirstCustomTemplateCreated(EventInfo firstCustomTemplateCreated) {
        this.firstCustomTemplateCreated = firstCustomTemplateCreated;
    }

    public EventInfo getCustomTemplateUnused() {
        return customTemplateUnused;
    }

    public void setCustomTemplateUnused(EventInfo customTemplateUnused) {
        this.customTemplateUnused = customTemplateUnused;
    }

    public EventInfo getSlackIntegration() {
        return slackIntegration;
    }

    public void setSlackIntegration(EventInfo slackIntegration) {
        this.slackIntegration = slackIntegration;
    }

    public EventInfo getPocCompleted() {
        return pocCompleted;
    }

    public void setPocCompleted(EventInfo pocCompleted) {
        this.pocCompleted = pocCompleted;
    }

    public EventInfo getAccountBlocked() {
        return accountBlocked;
    }

    public void setAccountBlocked(EventInfo accountBlocked) {
        this.accountBlocked = accountBlocked;
    }
}
