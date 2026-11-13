package com.akto.linear;

import com.akto.dao.PortIOIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.linear_integration.LinearTeamMapping;
import com.akto.dto.port_io_integration.PortIOIntegration;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRunResult;
import com.akto.linear.LinearApiClient.LinearIssue;
import com.akto.log.LoggerMaker;
import com.akto.ticketing.ATicketIntegrationService;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

public class PortIOIntegrationService extends ATicketIntegrationService<PortIOIntegration> {

    private static final LoggerMaker logger = new LoggerMaker(PortIOIntegrationService.class,
        LoggerMaker.LogDb.DASHBOARD);

    public PortIOIntegration saveCredentials(String clientId, String clientSecret, String workflowId)
        throws Exception {
        if (StringUtils.isAnyBlank(clientId, clientSecret, workflowId)) {
            throw new Exception("Port client ID, client secret and workflow identifier are required.");
        }

        PortApiClient.fetchAccessToken(clientId, clientSecret);

        int now = Context.now();
        Bson updates = Updates.combine(
            Updates.set(PortIOIntegration.CLIENT_ID, clientId.trim()),
            Updates.set(PortIOIntegration.CLIENT_SECRET, clientSecret.trim()),
            Updates.set(PortIOIntegration.WORKFLOW_ID, workflowId.trim()),
            Updates.setOnInsert(PortIOIntegration.TEAM_MAPPINGS, new LinkedHashMap<String, LinearTeamMapping>()),
            Updates.setOnInsert(PortIOIntegration.CREATED_TS, now),
            Updates.set(PortIOIntegration.UPDATED_TS, now)
        );

        return redacted(upsert(updates));
    }

    public PortIOIntegration saveTeams(Map<String, LinearTeamMapping> requestedTeams) throws Exception {
        if (requestedTeams == null || requestedTeams.isEmpty()) {
            throw new Exception("Please add at least one team.");
        }

        if (fetchIntegration() == null) {
            throw new Exception("Please save your Port credentials first.");
        }

        Map<String, LinearTeamMapping> teamMappings = new LinkedHashMap<>();
        for (Map.Entry<String, LinearTeamMapping> entry : requestedTeams.entrySet()) {
            LinearTeamMapping requested = entry.getValue();
            if (StringUtils.isBlank(entry.getKey()) || requested == null
                || StringUtils.isBlank(requested.getTeamName())) {
                throw new Exception("Each team needs a Linear team ID and a name.");
            }
            teamMappings.put(entry.getKey().trim(), new LinearTeamMapping(requested.getTeamName().trim(),
                LinearIntegrationService.sanitizeSeverityMapping(requested)));
        }

        Bson updates = Updates.combine(
            Updates.set(PortIOIntegration.TEAM_MAPPINGS, teamMappings),
            Updates.set(PortIOIntegration.UPDATED_TS, Context.now())
        );
        return redacted(upsert(updates));
    }

    public PortIOIntegration fetchPortIOIntegration() {
        return redacted(fetchIntegration());
    }

    public void removeIntegration() throws Exception {
        try {
            PortIOIntegrationDao.instance.getMCollection().deleteOne(new BasicDBObject());
        } catch (Exception e) {
            logger.errorAndAddToDb("Error removing Port integration: " + e.getMessage(),
                LoggerMaker.LogDb.DASHBOARD);
            throw new Exception("Failed to remove Port integration. Please try again.");
        }
    }

    private static PortIOIntegration upsert(Bson updates) {
        return PortIOIntegrationDao.instance.getMCollection().findOneAndUpdate(
            new BasicDBObject(),
            updates,
            new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        );
    }

    private static PortIOIntegration redacted(PortIOIntegration integration) {
        if (integration != null) {
            integration.setClientSecret(null);
        }
        return integration;
    }

    @Override
    protected String getIntegrationName() {
        return "PORT_IO";
    }

    @Override
    protected String getTicketUrlFieldName() {
        return TestingRunIssues.LINEAR_ISSUE_URL;
    }

    @Override
    protected GlobalEnums.TicketSource getTicketSource() {
        return GlobalEnums.TicketSource.LINEAR;
    }

    @Override
    protected PortIOIntegration fetchIntegration() {
        return PortIOIntegrationDao.instance.findOne(new BasicDBObject());
    }

    @Override
    protected String validateAndGetProjectIdentifier(PortIOIntegration integration, String teamId) throws Exception {
        if (StringUtils.isBlank(teamId)) {
            throw new Exception("Team is required.");
        }

        Map<String, LinearTeamMapping> teamMappings = integration.getTeamMappings();
        if (teamMappings == null || teamMappings.isEmpty()) {
            throw new Exception("No Linear teams configured.");
        }

        if (!teamMappings.containsKey(teamId)) {
            throw new Exception("Invalid Linear team: " + teamId);
        }

        return teamId;
    }

    @Override
    protected String getAuthenticationToken(PortIOIntegration integration) {
        try {
            return PortApiClient.fetchAccessToken(integration.getClientId(), integration.getClientSecret());
        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching Port access token: " + e.getMessage(),
                LoggerMaker.LogDb.DASHBOARD);
            return null;
        }
    }

    @Override
    protected String getExistingTicketUrl(TestingRunIssues issue) {
        return issue.getLinearIssueUrl();
    }

    @Override
    protected TicketInfo createTicketForIssue(PortIOIntegration integration, String authToken,
        TestingIssuesId issueId, Info testInfo, TestingRunResult testingRunResult, Severity severity,
        String teamId, String workItemType, String aktoDashboardHost) {

        try {
            String method = issueId.getApiInfoKey().getMethod().name();
            Pair<String, String> endpointDetails = getEndpointDetails(issueId.getApiInfoKey().getUrl());
            String title = String.format("Akto Report - %s (%s - %s)", testInfo.getName(), method,
                endpointDetails.getSecond());

            String aktoIssueUrl = StringUtils.isNotBlank(aktoDashboardHost)
                ? aktoDashboardHost + "/dashboard/issues?result=" + testingRunResult.getId().toHexString()
                : null;

            String description = LinearIntegrationService.buildDescription(testInfo, testingRunResult, severity,
                method, endpointDetails, aktoIssueUrl);

            LinearIssue issue = PortApiClient.createIssue(authToken, integration.getWorkflowId(), teamId, title,
                description, LinearIntegrationService.resolvePriority(integration.getTeamMappings(), teamId,
                    severity));

            return new TicketInfo(issue.getIdentifier(), issue.getUrl());
        } catch (Exception e) {
            logger.errorAndAddToDb("Exception creating Linear issue through Port: " + e.getMessage(),
                LoggerMaker.LogDb.DASHBOARD);
            return null;
        }
    }
}
