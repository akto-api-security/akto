package com.akto.jobs.executors;

import com.akto.api_clients.JiraApiClient;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.jira_integration.BiDirectionalSyncSettings;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.dto.jira_integration.ProjectMapping;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.TicketSyncJobParams;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.jobs.JobExecutor;
import com.akto.jobs.utils.JobConstants;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.TimeZone;
import okhttp3.Credentials;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.bson.conversions.Bson;

public class TicketSyncJobExecutor extends JobExecutor<TicketSyncJobParams> {

    private static final LoggerMaker logger = new LoggerMaker(TicketSyncJobExecutor.class);
    public static final TicketSyncJobExecutor INSTANCE = new TicketSyncJobExecutor();

    public TicketSyncJobExecutor() {
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

        int diff = Context.now() - lastSyncedAt;
        int updatedAfterMinutes = (int) Math.ceil(diff / 60.0);


        Map<String, BasicDBObject> eligibleJiraTickets = fetchJiraTicketsWithPagination(jira, projectKey,
            updatedAfterMinutes);

        updateJobHeartbeat(job);

        Bson filter = Filters.and(Filters.eq(TestingRunIssues.TICKET_PROJECT_KEY, projectKey),
            Filters.eq(TestingRunIssues.TICKET_SOURCE, params.getTicketSource()),
            Filters.gt(TestingRunIssues.LAST_UPDATED, lastSyncedAt));

        List<TestingRunIssues> eligibleAktoIssues = TestingRunIssuesDao.instance.findAll(filter);

        if (eligibleJiraTickets.isEmpty() && eligibleAktoIssues.isEmpty()) {
            logger.info("No eligible issues found for syncing");
            params.setLastSyncedAt(Context.now());
            updateJobParams(job, params);
            return;
        }

        if (eligibleJiraTickets.isEmpty()) {
            logger.info("No Akto issues to be updated. Updating {} Jira tickets.",
                eligibleAktoIssues.size());
            updateJiraTickets(jira, eligibleAktoIssues, aktoToJiraStatusMappings, job);
            params.setLastSyncedAt(Context.now());
            updateJobParams(job, params);
            return;
        }

        if (eligibleAktoIssues.isEmpty()) {
            logger.info("No Jira Tickets to be updated. Updating {} Akto issues.",
                eligibleJiraTickets.size());
            Bson query = Filters.and(
                Filters.eq(TestingRunIssues.TICKET_PROJECT_KEY, projectKey),
                Filters.eq(TestingRunIssues.TICKET_SOURCE, params.getTicketSource()),
                Filters.in(TestingRunIssues.TICKET_ID, eligibleJiraTickets.keySet())
            );
            eligibleAktoIssues = TestingRunIssuesDao.instance.findAll(query);
            updateAktoIssues(eligibleAktoIssues, eligibleJiraTickets, jiraToAktoStatusMappings, job);
            params.setLastSyncedAt(Context.now());
            updateJobParams(job, params);
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
            int jiraUpdatedAt = jiraIssue.getInt("jiraUpdatedAt");
            if (jiraUpdatedAt > issue.getLastUpdated()) {
                aktoIssuesToBeUpdated.add(issue);
                jiraIssuesForAkto.put(issue.getTicketId(), jiraIssue);
            } else {
                jiraIssuesToBeUpdated.add(issue);
            }
        }

        // Handle Akto issues that need to be updated in Jira
        updateJiraTickets(jira, jiraIssuesToBeUpdated, aktoToJiraStatusMappings, job);

        // Handle Jira issues that need to be updated in Akto
        updateAktoIssues(aktoIssuesToBeUpdated, jiraIssuesForAkto, jiraToAktoStatusMappings, job);

        // Handle remaining Jira tickets that don't have corresponding Akto issues
        if (!eligibleJiraTickets.isEmpty()) {
            logger.info("Found {} Jira tickets without corresponding Akto issues", eligibleJiraTickets.size());
            // Fetch Akto issues by ticket IDs
            List<String> ticketIds = new ArrayList<>(eligibleJiraTickets.keySet());
            Bson query = Filters.and(
                Filters.eq(TestingRunIssues.TICKET_PROJECT_KEY, projectKey),
                Filters.eq(TestingRunIssues.TICKET_SOURCE, params.getTicketSource()),
                Filters.in(TestingRunIssues.TICKET_ID, ticketIds)
            );
            List<TestingRunIssues> remainingAktoIssues = TestingRunIssuesDao.instance.findAll(query);

            if (!remainingAktoIssues.isEmpty()) {
                logger.info("Found {} Akto issues for remaining Jira tickets", remainingAktoIssues.size());
                updateAktoIssues(remainingAktoIssues, eligibleJiraTickets, jiraToAktoStatusMappings, job);
            }
        }

        params.setLastSyncedAt(Context.now());
        updateJobParams(job, params);
    }

    private void updateJiraTickets(JiraIntegration jira, List<TestingRunIssues> issues,
        Map<String, List<String>> aktoToJiraStatusMappings, Job job) {
        if (issues.isEmpty()) {
            return;
        }

        try {

            // Group issues by their Akto status
            Map<String, List<TestingRunIssues>> issuesByStatus = new HashMap<>();
            for (TestingRunIssues issue : issues) {
                String aktoStatus = issue.getTestRunIssueStatus().name();
                issuesByStatus.computeIfAbsent(aktoStatus, k -> new ArrayList<>()).add(issue);
            }

            // Process each status group
            for (Map.Entry<String, List<TestingRunIssues>> entry : issuesByStatus.entrySet()) {
                String aktoStatus = entry.getKey();
                List<TestingRunIssues> statusIssues = entry.getValue();

                // Get the corresponding Jira statuses for this Akto status
                List<String> jiraStatuses = aktoToJiraStatusMappings.get(aktoStatus);
                if (jiraStatuses == null || jiraStatuses.isEmpty()) {
                    logger.warn("No Jira status mapping found for Akto status: {}", aktoStatus);
                    continue;
                }

                logger.debug("Found {} statues mapped with akto status {}. Using the first one", jiraStatuses.size(),
                    aktoStatus);

                // Use the first mapped status as the target
                String targetJiraStatus = jiraStatuses.get(0);

                // Extract issue keys for this status group
                List<String> issueKeys = statusIssues.stream()
                    .map(TestingRunIssues::getTicketId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

                if (issueKeys.isEmpty()) {
                    logger.info("No valid ticket IDs found for Akto status: {}", aktoStatus);
                    continue;
                }

                // Get transitions for these issues to reach the target status
                try {
                    Map<Integer, List<String>> transitionsMap = JiraApiClient.getTransitions(jira, issueKeys, targetJiraStatus);

                    updateJobHeartbeat(job);
                    if (transitionsMap.isEmpty()) {
                        logger.info("No transitions found for issues with Akto status: {} to Jira status: {}",
                                    aktoStatus, targetJiraStatus);
                        continue;
                    }

                    // Perform bulk transition with retries
                    boolean success = false;
                    int maxRetries = 2;
                    int retryCount = 0;

                    while (!success && retryCount < maxRetries) {
                        try {
                            success = JiraApiClient.bulkTransitionIssues(jira, transitionsMap);

                            updateJobHeartbeat(job);
                            if (success) {
                                break;
                            }
                            retryCount++;
                            if (retryCount < maxRetries) {
                                logger.info("Retrying bulk transition (attempt {}/{})", retryCount + 1, maxRetries);
                                Thread.sleep(2000L * retryCount); // Exponential backoff
                            }
                        } catch (Exception e) {
                            retryCount++;
                            if (retryCount < maxRetries) {
                                logger.error("Error during bulk transition, retrying (attempt {}/{}): {}",
                                           retryCount + 1, maxRetries, e.getMessage(), e);
                                Thread.sleep(2000L * retryCount); // Exponential backoff
                            }
                        }
                    }

                    if (success) {
                        logger.info("Successfully transitioned {} Jira tickets to status: {}. ticketIds: {}",
                            issueKeys.size(), targetJiraStatus, issueKeys);
                        // Update last updated timestamp in Akto
                        List<WriteModel<TestingRunIssues>> writeModels = new ArrayList<>();
                        for (TestingRunIssues issue : statusIssues) {
                            if (issue.getTicketId() != null) {
                                Bson query = Filters.eq(Constants.ID, issue.getId());
                                Bson update = Updates.set(TestingRunIssues.TICKET_LAST_UPDATED_AT, Context.now());
                                writeModels.add(new UpdateOneModel<>(query, update));
                            }
                        }

                        if (!writeModels.isEmpty()) {
                            TestingRunIssuesDao.instance.getMCollection().bulkWrite(writeModels);
                            logger.info("Updated last updated timestamp for {} Akto issues", writeModels.size());
                        }
                    } else {
                        logger.error("Failed to transition Jira tickets to status: {}. ticketIds: {}", targetJiraStatus,
                            issueKeys);
                    }
                } catch (Exception e) {
                    logger.error("Error getting transitions or performing bulk transition for Akto status: {} to Jira status: {}",
                                aktoStatus, targetJiraStatus, e);
                }
            }
        } catch (Exception e) {
            logger.error("Error updating Jira tickets: {}", e.getMessage(), e);
        }
    }

    private void updateAktoIssues(List<TestingRunIssues> aktoIssues, Map<String, BasicDBObject> jiraIssues,
        Map<String, String> jiraToAktoStatusMapping, Job job) {

        if (aktoIssues.isEmpty()) {
            return;
        }

        try {
            List<WriteModel<TestingRunIssues>> writeModelList = new ArrayList<>();
            for (TestingRunIssues issue : aktoIssues) {
                try {
                    BasicDBObject jiraIssue = jiraIssues.get(issue.getTicketId());
                    if (jiraIssue == null) {
                        logger.warn("No Jira issue found for ticket ID: {}", issue.getTicketId());
                        continue;
                    }

                    String jiraStatus = jiraIssue.getString("ticketStatus");
                    if (jiraStatus == null) {
                        logger.warn("No status found in Jira issue for ticket ID: {}", issue.getTicketId());
                        continue;
                    }

                    String aktoStatus = jiraToAktoStatusMapping.get(jiraStatus);
                    if (aktoStatus == null) {
                        logger.warn("No Akto status mapping found for Jira status: {} (ticket ID: {})",
                                  jiraStatus, issue.getTicketId());
                        continue;
                    }

                    TestRunIssueStatus status = TestRunIssueStatus.valueOf(aktoStatus);

                    if (status == issue.getTestRunIssueStatus()) {
                        logger.info("Skipping update for issue: {}, ticketId: {} as status is already: {}", issue.getId(),
                            issue.getTicketId(), status);
                        continue;
                    }

                    logger.info("Updating issue: {}, ticketId: {} with status: {}. old status: {}", issue.getId(),
                        issue.getTicketId(), status, issue.getTestRunIssueStatus());

                    int now = Context.now();
                    Bson query = Filters.eq(Constants.ID, issue.getId());
                    Bson update = Updates.combine(
                        Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, status),
                        Updates.set(TestingRunIssues.LAST_UPDATED, now)
                    );
                    writeModelList.add(new UpdateOneModel<>(query, update));
                } catch (Exception e) {
                    logger.error("Error processing Akto issue {}: {}", issue.getId(), e.getMessage());
                }
            }

            if (!writeModelList.isEmpty()) {
                TestingRunIssuesDao.instance.getMCollection().bulkWrite(writeModelList);
                logger.info("Updated {} Akto issues out of {}", writeModelList.size(), aktoIssues.size());
            } else {
                logger.info("No Akto issues to update");
            }
        } catch (Exception e) {
            logger.error("Error updating Akto issues: {}", e.getMessage(), e);
        }

        updateJobHeartbeat(job);
    }

    private JiraIntegration loadJiraIntegration() throws Exception {
        JiraIntegration integration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            throw new Exception("Jira is not integrated");
        }
        return integration;
    }

    private Map<String, List<String>> getIssueStatusMappings(JiraIntegration jiraIntegration, String projectKey) throws Exception {
        Map<String, ProjectMapping> mappings = jiraIntegration.getProjectMappings();
        if (mappings == null) {
            throw new Exception("No project mappings found in Jira integration");
        }

        ProjectMapping projMapping = mappings.get(projectKey);
        if (projMapping == null) {
            throw new Exception("No project mapping found for project key: " + projectKey);
        }

        BiDirectionalSyncSettings settings = projMapping.getBiDirectionalSyncSettings();
        if (settings == null) {
            throw new Exception("No bidirectional sync settings found for project key: " + projectKey);
        }

        Map<String, List<String>> statusMappings = settings.getAktoStatusMappings();
        if (statusMappings == null || statusMappings.isEmpty()) {
            throw new Exception("No status mappings found for project key: " + projectKey);
        }

        // Validate that all TestRunIssueStatus values have mappings
        for (TestRunIssueStatus status : TestRunIssueStatus.values()) {
            if (!statusMappings.containsKey(status.name())) {
                logger.warn("No Jira status mapping found for Akto status: {}", status.name());
            }
        }

        return statusMappings;
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

    private Map<String, BasicDBObject> fetchJiraTicketsWithPagination(JiraIntegration jira, String projectKey,
        int updatedAfter) throws Exception {
        Map<String, BasicDBObject> allResults = new HashMap<>();
        try {
            Map<String, BasicDBObject> pageResults = JiraApiClient.fetchUpdatedTickets(jira, projectKey,
                updatedAfter);
            allResults.putAll(pageResults);
        } catch (Exception e) {
            logger.error("Error fetching Jira tickets.", e);
            throw e;
        }

        return allResults;
    }
}
