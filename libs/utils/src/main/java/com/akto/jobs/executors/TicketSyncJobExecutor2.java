package com.akto.jobs.executors;

import com.akto.api_clients.JiraApiClient;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.jira_integration.BiDirectionalSyncSettings;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.dto.jira_integration.ProjectMapping;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.TicketSyncJobParams;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.conversions.Bson;

public class TicketSyncJobExecutor2 extends JobExecutor<TicketSyncJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(TicketSyncJobExecutor.class);
    public static final TicketSyncJobExecutor INSTANCE = new TicketSyncJobExecutor();

    public TicketSyncJobExecutor2() {
        super(TicketSyncJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        TicketSyncJobParams params = paramClass.cast(job.getJobParams());
        String projectKey = params.getProjectKey();
        int lastSyncedAt = params.getLastSyncedAt();
        JiraIntegration jira = loadJiraIntegration();
        Map<String, List<String>> aktoToJiraStatusMappings = getIssueStatusMappings(jira, projectKey);
        Map<String, String> jiraToAktoStatusMappings = invertMapWithListValues(aktoToJiraStatusMappings);

        Map<String, BasicDBObject> eligibleJiraTickets = JiraApiClient.fetchUpdatedTickets(jira, projectKey,
            lastSyncedAt);

        Bson filter = Filters.and(Filters.eq(TestingRunIssues.TICKET_PROJECT_KEY, projectKey),
            Filters.eq(TestingRunIssues.TICKET_SOURCE, params.getTicketSource()),
            Filters.in(TestingRunIssues.LAST_UPDATED, lastSyncedAt));

        List<TestingRunIssues> eligibleAktoIssues = TestingRunIssuesDao.instance.findAll(filter);

        if (eligibleJiraTickets.isEmpty() && eligibleAktoIssues.isEmpty()) {
            logger.info("No eligible issues found for syncing");
            return;
        }

        if (eligibleJiraTickets.isEmpty()) {
            logger.info("No eligible Jira issues found for syncing");
            updateJiraIssues(eligibleAktoIssues, aktoToJiraStatusMappings);
            return;
        }

        if (eligibleAktoIssues.isEmpty()) {
            logger.info("No eligible Akto issues found for syncing");
            Bson query = Filters.and(
                Filters.eq(TestingRunIssues.TICKET_PROJECT_KEY, projectKey),
                Filters.eq(TestingRunIssues.TICKET_SOURCE, params.getTicketSource()),
                Filters.in(TestingRunIssues.TICKET_ID, eligibleJiraTickets.keySet())
            );
            eligibleAktoIssues = TestingRunIssuesDao.instance.findAll(query);
            updateAktoIssues(eligibleAktoIssues, eligibleJiraTickets, jiraToAktoStatusMappings);
            return;
        }

        // handle for intersection

        List<TestingRunIssues> aktoIssuesToBeUpdated = new ArrayList<>();
        List<TestingRunIssues> jiraIssuesToBeUpdated = new ArrayList<>();
        Map<String, BasicDBObject> jiraIssuesForAkto = new HashMap<>();

        for (TestingRunIssues issue : eligibleAktoIssues) {
            BasicDBObject jiraIssue = eligibleJiraTickets.remove(issue.getTicketId());
            if (jiraIssue == null) {
                jiraIssuesToBeUpdated.add(issue);
                continue;
            }
            int jiraUpdatedAt = jiraIssue.getInt("updated");
            if (jiraUpdatedAt > issue.getTicketLastUpdatedAt()) {
                jiraIssuesToBeUpdated.add(issue);
            } else {
                jiraIssuesForAkto.put(issue.getTicketId(), jiraIssue);
                aktoIssuesToBeUpdated.add(issue);
            }
        }

        updateJiraIssues(jiraIssuesToBeUpdated, aktoToJiraStatusMappings);
        updateAktoIssues(aktoIssuesToBeUpdated, jiraIssuesForAkto, jiraToAktoStatusMappings);
    }

    private void updateJiraIssues(List<TestingRunIssues> issues, Map<String, List<String>> aktoToJiraStatusMappings) {
        if (issues.isEmpty()) {
            return;
        }
    }

    private void updateAktoIssues(List<TestingRunIssues> aktoIssues, Map<String, BasicDBObject> jiraIssues,
        Map<String, String> jiraToAktoStatusMapping) {

        if (aktoIssues.isEmpty()) {
            return;
        }

        List<WriteModel<TestingRunIssues>> writeModelList = new ArrayList<>();
        for (TestingRunIssues issue : aktoIssues) {
            BasicDBObject jiraIssue = jiraIssues.get(issue.getTicketId());
            TestRunIssueStatus status = TestRunIssueStatus.valueOf(
                jiraToAktoStatusMapping.get(jiraIssue.getString("ticketStatus")));

            logger.debug("Updating issue: {} with status: {}. old status: {}", issue.getId(), status,
                issue.getTestRunIssueStatus());

            Bson query = Filters.eq(Constants.ID, issue.getId());
            Bson update = Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, status);
            writeModelList.add(new UpdateOneModel<>(query, update));
        }

        TestingRunIssuesDao.instance.getMCollection().bulkWrite(writeModelList);
        logger.info("Updated {} Akto issues out of {}", writeModelList.size(), aktoIssues.size());
    }

    private JiraIntegration loadJiraIntegration() throws Exception {
        JiraIntegration integration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            throw new Exception("Jira is not integrated");
        }
        return integration;
    }

    private Date subtractDays(Date date, int daysToSubtract) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, -daysToSubtract);
        return calendar.getTime();
    }

    private Map<String, List<String>> getIssueStatusMappings(JiraIntegration jiraIntegration, String projectKey) {
        Map<String, ProjectMapping> mappings = jiraIntegration.getProjectMappings();
        ProjectMapping projMapping = mappings.get(projectKey);
        BiDirectionalSyncSettings settings = projMapping.getBiDirectionalSyncSettings();
        return settings.getAktoStatusMappings();
    }

    private Map<String, String> invertMapWithListValues(Map<String, List<String>> inputMap) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : inputMap.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();

            if (values != null) {
                for (String value : values) {
                    result.put(value, key);
                }
            }
        }
        return result;
    }
}
