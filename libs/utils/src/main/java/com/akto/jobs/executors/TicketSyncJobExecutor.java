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

        Map<String, BasicDBObject> eligibleJiraTickets = fetchJiraTicketsWithPagination(jira, projectKey, new Date(lastSyncedAt));

        Bson filter = Filters.and(Filters.eq(TestingRunIssues.TICKET_PROJECT_KEY, projectKey),
            Filters.eq(TestingRunIssues.TICKET_SOURCE, params.getTicketSource()),
            Filters.gte(TestingRunIssues.LAST_UPDATED, lastSyncedAt));

        List<TestingRunIssues> eligibleAktoIssues = TestingRunIssuesDao.instance.findAll(filter);

        if (eligibleJiraTickets.isEmpty() && eligibleAktoIssues.isEmpty()) {
            logger.info("No eligible issues found for syncing");
            return;
        }

        if (eligibleJiraTickets.isEmpty()) {
            logger.info("No eligible Jira issues found for syncing");
            updateJiraIssues(jira, eligibleAktoIssues, aktoToJiraStatusMappings);
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
            int jiraUpdatedAt = jiraIssue.getInt("jiraUpdatedAt");
            if (jiraUpdatedAt > issue.getTicketLastUpdatedAt()) {
                jiraIssuesToBeUpdated.add(issue);
            } else {
                jiraIssuesForAkto.put(issue.getTicketId(), jiraIssue);
                aktoIssuesToBeUpdated.add(issue);
            }
        }

        // Handle Akto issues that need to be updated in Jira
        updateJiraIssues(jira, jiraIssuesToBeUpdated, aktoToJiraStatusMappings);

        // Handle Jira issues that need to be updated in Akto
        updateAktoIssues(aktoIssuesToBeUpdated, jiraIssuesForAkto, jiraToAktoStatusMappings);

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
                updateAktoIssues(remainingAktoIssues, eligibleJiraTickets, jiraToAktoStatusMappings);
            }
        }
    }

    private void updateJiraIssues(JiraIntegration jira, List<TestingRunIssues> issues, Map<String, List<String>> aktoToJiraStatusMappings) {
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

                    if (transitionsMap.isEmpty()) {
                        logger.info("No transitions found for issues with Akto status: {} to Jira status: {}",
                                    aktoStatus, targetJiraStatus);
                        continue;
                    }

                    // Perform bulk transition with retries
                    boolean success = false;
                    int maxRetries = 2;
                    int retryCount = 0;
                    Exception lastException = null;

                    while (!success && retryCount < maxRetries) {
                        try {
                            success = JiraApiClient.bulkTransitionIssues(jira, transitionsMap);
                            if (success) {
                                break;
                            }
                            retryCount++;
                            if (retryCount < maxRetries) {
                                logger.info("Retrying bulk transition (attempt {}/{})", retryCount + 1, maxRetries);
                                Thread.sleep(2000 * retryCount); // Exponential backoff
                            }
                        } catch (Exception e) {
                            lastException = e;
                            retryCount++;
                            if (retryCount < maxRetries) {
                                logger.warn("Error during bulk transition, retrying (attempt {}/{}): {}",
                                           retryCount + 1, maxRetries, e.getMessage());
                                Thread.sleep(2000 * retryCount); // Exponential backoff
                            }
                        }
                    }

                    logger.info("Successfully transitioned {} Jira issues to status: {}",
                        issueKeys.size(), targetJiraStatus);

                    if (success) {
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
                        logger.error("Failed to transition Jira issues to status: {}", targetJiraStatus);
                    }
                } catch (Exception e) {
                    logger.error("Error getting transitions or performing bulk transition for Akto status: {} to Jira status: {}",
                                aktoStatus, targetJiraStatus, e);
                }
            }
        } catch (Exception e) {
            logger.error("Error updating Jira issues: {}", e.getMessage(), e);
        }
    }

    private void updateAktoIssues(List<TestingRunIssues> aktoIssues, Map<String, BasicDBObject> jiraIssues,
        Map<String, String> jiraToAktoStatusMapping) {

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

                    logger.debug("Updating issue: {} with status: {}. old status: {}", issue.getId(), status,
                        issue.getTestRunIssueStatus());

                    Bson query = Filters.eq(Constants.ID, issue.getId());
                    Bson update = Updates.combine(
                        Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, status),
                        Updates.set(TestingRunIssues.TICKET_LAST_UPDATED_AT, Context.now())
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

    private Map<String, BasicDBObject> fetchJiraTicketsWithPagination(JiraIntegration jira, String projectKey, Date updatedAfter) throws Exception {
        Map<String, BasicDBObject> allResults = new HashMap<>();
        int startAt = 0;
        int maxResults = 100;
        boolean hasMore = true;

        while (hasMore) {
            try {
                // Use the existing fetchUpdatedTickets method but add pagination parameters
                Map<String, BasicDBObject> pageResults = fetchJiraTicketsPage(jira, projectKey, updatedAfter, startAt, maxResults);

                if (pageResults.isEmpty()) {
                    hasMore = false;
                } else {
                    allResults.putAll(pageResults);
                    startAt += maxResults;

                    // If we got fewer results than maxResults, we've reached the end
                    if (pageResults.size() < maxResults) {
                        hasMore = false;
                    }

                    logger.info("Fetched {} Jira tickets (total: {})", pageResults.size(), allResults.size());
                }
            } catch (Exception e) {
                logger.error("Error fetching Jira tickets page starting at {}: {}", startAt, e.getMessage(), e);
                throw e;
            }
        }

        return allResults;
    }

    private Map<String, BasicDBObject> fetchJiraTicketsPage(JiraIntegration jira, String projectKey, Date updatedAfter,
                                                          int startAt, int maxResults) throws Exception {
        String jql = String.format("project = \"%s\" AND updated >= \"%s\" AND labels = \"%s\" ORDER BY updated ASC",
                                 projectKey, formatJiraDate(updatedAfter), JobConstants.TICKET_LABEL_AKTO_SYNC);

        BasicDBObject body = new BasicDBObject("jql", jql)
            .append("fields", Arrays.asList("status", "updated"))
            .append("startAt", startAt)
            .append("maxResults", maxResults);

        String url = jira.getBaseUrl() + "/rest/api/3/search/jql";
        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(body.toJson().getBytes()))
            .addHeader("Authorization", Credentials.basic(jira.getUserEmail(), jira.getApiToken()))
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = CoreHTTPClient.client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.error("Failed to fetch Jira tickets: Url: {}, Response: {}", url, response);
                throw new IOException("Failed to fetch Jira tickets: " + response);
            }

            if (response.body() == null) {
                logger.error("Response body is null. Url: {}, Response: {}", url, response);
                throw new Exception("Response body is null.");
            }

            String responseBody = response.body().string();

            BasicDBObject responseObj = BasicDBObject.parse(responseBody);
            Map<String, BasicDBObject> results = new HashMap<>();

            List<?> issues = (List<?>) responseObj.get("issues");
            if (issues != null) {
                for (Object obj : issues) {
                    BasicDBObject issue = (BasicDBObject) obj;
                    BasicDBObject fields = (BasicDBObject) issue.get("fields");
                    BasicDBObject statusObj = (BasicDBObject) fields.get("status");
                    String key = issue.getString("key");
                    BasicDBObject ticket = new BasicDBObject("ticketKey", key)
                        .append("ticketStatus", statusObj.getString("name"))
                        .append("ticketStatusId", statusObj.getString("id"))
                        .append("jiraUpdatedAt", convertToEpochSeconds(fields.getString("updated")));

                    results.put(key, ticket);
                }
            }
            return results;
        }
    }

    private int convertToEpochSeconds(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        OffsetDateTime odt = OffsetDateTime.parse(date, formatter);
        long epochSeconds = odt.toEpochSecond();
        return (int) epochSeconds;
    }

    private String formatJiraDate(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }
}
