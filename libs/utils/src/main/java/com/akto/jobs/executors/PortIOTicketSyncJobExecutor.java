package com.akto.jobs.executors;

import com.akto.dao.PortIOIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.jobs.Job;
import com.akto.dto.jobs.TicketSyncJobParams;
import com.akto.dto.port_io_integration.PortIOIntegration;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.jobs.JobExecutor;
import com.akto.linear.LinearStatusMapping;
import com.akto.linear.PortApiClient;
import com.akto.linear.PortApiClient.PortLinearIssue;
import com.akto.linear.PortApiClient.PortWorkflowState;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.akto.util.enums.GlobalEnums.TicketSource;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class PortIOTicketSyncJobExecutor extends JobExecutor<TicketSyncJobParams> {

    public static final String SYNC_KEY = "PORT_IO";
    public static final String CREATED_VIA = "PORT_IO";
    public static final PortIOTicketSyncJobExecutor INSTANCE = new PortIOTicketSyncJobExecutor();

    private static final int MAX_RUNS_PER_SYNC = 50;
    private static final LoggerMaker logger = new LoggerMaker(PortIOTicketSyncJobExecutor.class);

    private PortIOTicketSyncJobExecutor() {
        super(TicketSyncJobParams.class);
    }

    @Override
    protected void runJob(Job job) throws Exception {
        TicketSyncJobParams params = paramClass.cast(job.getJobParams());

        PortIOIntegration integration = PortIOIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null || !integration.isBiDirectionalSyncEnabled()) {
            logger.info("Port.io bi-directional sync is not enabled. Skipping.");
            return;
        }

        int syncStartedAt = Context.now();
        int lastSyncedAt = params.getLastSyncedAt();
        String updateWorkflowId = StringUtils.defaultIfBlank(integration.getUpdateWorkflowId(),
            PortIOIntegration.DEFAULT_UPDATE_WORKFLOW_ID);

        List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(
            Filters.and(
                Filters.eq(TestingRunIssues.TICKET_SOURCE, TicketSource.LINEAR.name()),
                Filters.eq(TestingRunIssues.TICKET_CREATED_VIA, CREATED_VIA),
                Filters.exists(TestingRunIssues.TICKET_ID)
            ),
            Projections.include(Constants.ID, TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestingRunIssues.TICKET_ID,
                TestingRunIssues.LAST_UPDATED));

        if (issues.isEmpty()) {
            params.setLastSyncedAt(syncStartedAt);
            updateJobParams(job, params);
            return;
        }

        String token = PortApiClient.fetchAccessToken(integration.getClientId(), integration.getClientSecret());
        Map<String, PortLinearIssue> portIssues = PortApiClient.fetchLinearIssues(token,
            issues.stream().map(TestingRunIssues::getTicketId).collect(Collectors.toList()));
        Map<String, PortWorkflowState> states = PortApiClient.fetchWorkflowStates(token);
        updateJobHeartbeat(job);

        int toAkto = 0;
        int toPort = 0;
        boolean runLimitReached = false;

        for (TestingRunIssues issue : issues) {
            try {
                PortLinearIssue portIssue = portIssues.get(issue.getTicketId());
                PortWorkflowState portState = portIssue == null ? null : states.get(portIssue.getStateId());
                TestRunIssueStatus portStatus = portState == null ? null
                    : LinearStatusMapping.toAktoStatus(portState.getType());
                if (portStatus == null || portStatus == issue.getTestRunIssueStatus()) {
                    continue;
                }

                boolean aktoChanged = issue.getLastUpdated() > lastSyncedAt;
                if (!aktoChanged || portIssue.getUpdatedAt() > issue.getLastUpdated()) {
                    applyPortStatusToAkto(issue, portStatus, portIssue.getUpdatedAt());
                    toAkto++;
                    continue;
                }

                if (toPort >= MAX_RUNS_PER_SYNC) {
                    runLimitReached = true;
                    continue;
                }
                if (pushAktoStatusToPort(token, updateWorkflowId, issue, portIssue, states)) {
                    toPort++;
                }
            } catch (Exception e) {
                logger.error("Error syncing ticket {}: {}", issue.getTicketId(), e.getMessage());
            }
            updateJobHeartbeat(job);
        }

        logger.info("Port.io sync finished. Updated {} Akto issues and {} Linear tickets through Port.io",
            toAkto, toPort);

        if (!runLimitReached) {
            params.setLastSyncedAt(syncStartedAt);
            updateJobParams(job, params);
        }
    }

    private void applyPortStatusToAkto(TestingRunIssues issue, TestRunIssueStatus status, int portUpdatedAt) {
        logger.info("Updating issue {} (ticket {}) from {} to {}", issue.getId(), issue.getTicketId(),
            issue.getTestRunIssueStatus(), status);
        TestingRunIssuesDao.instance.updateOneNoUpsert(
            Filters.eq(Constants.ID, issue.getId()),
            Updates.combine(
                Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, status),
                Updates.set(TestingRunIssues.LAST_UPDATED, portUpdatedAt)
            )
        );
    }

    private boolean pushAktoStatusToPort(String token, String workflowId, TestingRunIssues issue,
        PortLinearIssue portIssue, Map<String, PortWorkflowState> states) throws Exception {
        List<String> targetTypes = LinearStatusMapping.targetStateTypes(issue.getTestRunIssueStatus());
        if (targetTypes == null) {
            return false;
        }

        Optional<PortWorkflowState> target = Optional.empty();
        for (String type : targetTypes) {
            target = states.values().stream()
                .filter(state -> type.equals(state.getType()) && StringUtils.equals(state.getTeamId(), portIssue.getTeamId()))
                .min((a, b) -> Double.compare(a.getPosition(), b.getPosition()));
            if (target.isPresent()) {
                break;
            }
        }
        if (!target.isPresent()) {
            logger.warn("No Linear state of type {} for ticket {}", targetTypes, issue.getTicketId());
            return false;
        }

        logger.info("Updating Linear ticket {} through Port.io to match Akto status {}", issue.getTicketId(),
            issue.getTestRunIssueStatus());
        PortApiClient.updateIssueState(token, workflowId, issue.getTicketId(), target.get().getId());
        return true;
    }
}
