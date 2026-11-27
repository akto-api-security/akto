package com.akto.jobs.executors;

import com.akto.dao.LinearIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.TicketSyncJobParams;
import com.akto.dto.linear_integration.LinearIntegration;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.jobs.JobExecutor;
import com.akto.linear.LinearApiClient;
import com.akto.linear.LinearApiClient.LinearIssueState;
import com.akto.linear.LinearApiClient.LinearWorkflowState;
import com.akto.linear.LinearStatusMapping;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.akto.util.enums.GlobalEnums.TicketSource;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

public class LinearTicketSyncJobExecutor extends JobExecutor<TicketSyncJobParams> {

    public static final String SYNC_KEY = "LINEAR";
    public static final LinearTicketSyncJobExecutor INSTANCE = new LinearTicketSyncJobExecutor();

    private static final LoggerMaker logger = new LoggerMaker(LinearTicketSyncJobExecutor.class);

    private LinearTicketSyncJobExecutor() {
        super(TicketSyncJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        TicketSyncJobParams params = paramClass.cast(job.getJobParams());

        LinearIntegration integration = LinearIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null || StringUtils.isBlank(integration.getApiToken())
            || !integration.isBiDirectionalSyncEnabled()) {
            logger.info("Linear bi-directional sync is not enabled. Skipping.");
            return;
        }

        String token = integration.getApiToken();
        int syncStartedAt = Context.now();
        int lastSyncedAt = params.getLastSyncedAt();
        List<String> teamIds = integration.getTeamMappings() == null
            ? new ArrayList<>() : new ArrayList<>(integration.getTeamMappings().keySet());

        Map<String, LinearIssueState> linearIssues = new HashMap<>();
        for (LinearIssueState issue : LinearApiClient.fetchIssuesUpdatedAfter(token, lastSyncedAt, teamIds)) {
            linearIssues.put(issue.getIdentifier(), issue);
        }
        updateJobHeartbeat(job);

        Map<String, TestingRunIssues> candidates = new HashMap<>();
        collectIssues(candidates, Filters.gt(TestingRunIssues.LAST_UPDATED, lastSyncedAt));
        if (!linearIssues.isEmpty()) {
            collectIssues(candidates, Filters.in(TestingRunIssues.TICKET_ID, linearIssues.keySet()));
        }

        Map<String, List<LinearWorkflowState>> statesByTeam = new HashMap<>();
        int toAkto = 0;
        int toLinear = 0;

        for (TestingRunIssues issue : candidates.values()) {
            try {
                LinearIssueState linear = linearIssues.get(issue.getTicketId());
                boolean aktoChangedAfterLinear = issue.getLastUpdated() > lastSyncedAt
                    && (linear == null || issue.getLastUpdated() >= linear.getUpdatedAt());

                if (linear != null && !aktoChangedAfterLinear) {
                    toAkto += applyLinearStatusToAkto(issue, linear);
                } else {
                    toLinear += applyAktoStatusToLinear(token, issue, linear, statesByTeam);
                }
            } catch (Exception e) {
                logger.error("Error syncing ticket {}: {}", issue.getTicketId(), e.getMessage());
            }
            updateJobHeartbeat(job);
        }

        logger.info("Linear sync finished. Updated {} Akto issues and {} Linear tickets", toAkto, toLinear);

        params.setLastSyncedAt(syncStartedAt);
        updateJobParams(job, params);
    }

    private void collectIssues(Map<String, TestingRunIssues> candidates, Bson condition) {
        List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(Filters.and(
            Filters.eq(TestingRunIssues.TICKET_SOURCE, TicketSource.LINEAR.name()),
            Filters.exists(TestingRunIssues.TICKET_ID),
            condition
        ));
        for (TestingRunIssues issue : issues) {
            if (StringUtils.isNotBlank(issue.getTicketId())) {
                candidates.put(issue.getTicketId(), issue);
            }
        }
    }

    private int applyLinearStatusToAkto(TestingRunIssues issue, LinearIssueState linear) {
        TestRunIssueStatus status = LinearStatusMapping.toAktoStatus(linear.getStateType());
        if (status == null || status == issue.getTestRunIssueStatus()) {
            return 0;
        }

        logger.info("Updating issue {} (ticket {}) from {} to {}", issue.getId(), issue.getTicketId(),
            issue.getTestRunIssueStatus(), status);
        TestingRunIssuesDao.instance.updateOneNoUpsert(
            Filters.eq(Constants.ID, issue.getId()),
            Updates.combine(
                Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, status),
                Updates.set(TestingRunIssues.LAST_UPDATED, linear.getUpdatedAt())
            )
        );
        return 1;
    }

    private int applyAktoStatusToLinear(String token, TestingRunIssues issue, LinearIssueState linear,
        Map<String, List<LinearWorkflowState>> statesByTeam) throws Exception {
        TestRunIssueStatus aktoStatus = issue.getTestRunIssueStatus();
        if (LinearStatusMapping.targetStateTypes(aktoStatus) == null) {
            return 0;
        }

        LinearIssueState current = linear != null ? linear : LinearApiClient.fetchIssue(token, issue.getTicketId());
        if (aktoStatus == LinearStatusMapping.toAktoStatus(current.getStateType())) {
            return 0;
        }

        List<LinearWorkflowState> states = statesByTeam.get(current.getTeamId());
        if (states == null) {
            states = LinearApiClient.fetchTeamStates(token, current.getTeamId());
            statesByTeam.put(current.getTeamId(), states);
        }

        Optional<LinearWorkflowState> target = LinearStatusMapping.targetState(states, aktoStatus);
        if (!target.isPresent()) {
            logger.warn("No Linear state for Akto status {} on ticket {}", aktoStatus, issue.getTicketId());
            return 0;
        }

        logger.info("Updating Linear ticket {} to match Akto status {}", issue.getTicketId(), aktoStatus);
        LinearApiClient.updateIssueState(token, current.getId(), target.get().getId());
        return 1;
    }
}
