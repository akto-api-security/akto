package com.akto.linear;

import com.akto.dao.LinearIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.linear_integration.LinearIntegration;
import com.akto.dto.linear_integration.LinearTeamMapping;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.linear.LinearApiClient.LinearIssue;
import com.akto.linear.LinearApiClient.LinearTeam;
import com.akto.log.LoggerMaker;
import com.akto.ticketing.ATicketIntegrationService;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.utils.CurlUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

@NoArgsConstructor
public class LinearIntegrationService extends ATicketIntegrationService<LinearIntegration> {

    private static final LoggerMaker logger = new LoggerMaker(LinearIntegrationService.class,
        LoggerMaker.LogDb.DASHBOARD);

    private static final String PRIORITY_NONE = "0";
    private static final String PRIORITY_URGENT = "1";
    private static final String PRIORITY_HIGH = "2";
    private static final String PRIORITY_MEDIUM = "3";
    private static final String PRIORITY_LOW = "4";

    private static final List<String> PRIORITY_VALUES = Arrays.asList(PRIORITY_NONE, PRIORITY_URGENT,
        PRIORITY_HIGH, PRIORITY_MEDIUM, PRIORITY_LOW);
    private static final List<String> MAPPED_SEVERITIES = Arrays.asList(Severity.CRITICAL.name(),
        Severity.HIGH.name(), Severity.MEDIUM.name(), Severity.LOW.name());
    private static final Map<String, String> DEFAULT_SEVERITY_TO_PRIORITY = new HashMap<>();

    static {
        DEFAULT_SEVERITY_TO_PRIORITY.put(Severity.CRITICAL.name(), PRIORITY_URGENT);
        DEFAULT_SEVERITY_TO_PRIORITY.put(Severity.HIGH.name(), PRIORITY_HIGH);
        DEFAULT_SEVERITY_TO_PRIORITY.put(Severity.MEDIUM.name(), PRIORITY_MEDIUM);
        DEFAULT_SEVERITY_TO_PRIORITY.put(Severity.LOW.name(), PRIORITY_LOW);
    }

    private String apiToken;

    public LinearIntegrationService(String apiToken) {
        this.apiToken = apiToken;
    }

    public LinearIntegration saveApiKey() throws Exception {
        if (StringUtils.isBlank(apiToken)) {
            throw new Exception("Please enter a valid Linear API key.");
        }

        String token = apiToken.trim();
        Map<String, String> workspaceTeams = toTeamsMap(LinearApiClient.fetchTeams(token));

        Map<String, LinearTeamMapping> retainedTeams = new LinkedHashMap<>();
        LinearIntegration existing = LinearIntegrationDao.instance.findOne(new BasicDBObject());
        if (existing != null && existing.getTeamMappings() != null) {
            existing.getTeamMappings().forEach((teamId, mapping) -> {
                String teamName = workspaceTeams.get(teamId);
                if (teamName != null) {
                    mapping.setTeamName(teamName);
                    retainedTeams.put(teamId, mapping);
                }
            });
        }

        return upsertIntegration(token, retainedTeams);
    }

    public LinearIntegration saveTeams(Map<String, LinearTeamMapping> requestedTeams) throws Exception {
        if (requestedTeams == null || requestedTeams.isEmpty()) {
            throw new Exception("Please add at least one team.");
        }

        LinearIntegration existing = LinearIntegrationDao.instance.findOne(new BasicDBObject());
        if (existing == null || StringUtils.isBlank(existing.getApiToken())) {
            throw new Exception("Please save your Linear API key first.");
        }

        Map<String, String> workspaceTeams = toTeamsMap(LinearApiClient.fetchTeams(existing.getApiToken()));

        Map<String, LinearTeamMapping> teamMappings = new LinkedHashMap<>();
        for (Map.Entry<String, LinearTeamMapping> entry : requestedTeams.entrySet()) {
            String teamName = workspaceTeams.get(entry.getKey());
            if (teamName == null) {
                throw new Exception("Team not found in your Linear workspace: " + entry.getKey());
            }
            teamMappings.put(entry.getKey(),
                new LinearTeamMapping(teamName, sanitizeSeverityMapping(entry.getValue())));
        }

        return upsertIntegration(existing.getApiToken(), teamMappings);
    }

    private LinearIntegration upsertIntegration(String token, Map<String, LinearTeamMapping> teamMappings) {
        int now = Context.now();
        Bson updates = Updates.combine(
            Updates.set(LinearIntegration.API_TOKEN, token),
            Updates.set(LinearIntegration.TEAM_MAPPINGS, teamMappings),
            Updates.setOnInsert(LinearIntegration.CREATED_TS, now),
            Updates.set(LinearIntegration.UPDATED_TS, now)
        );

        LinearIntegration updated = LinearIntegrationDao.instance.getMCollection().findOneAndUpdate(
            new BasicDBObject(),
            updates,
            new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        );

        logger.infoAndAddToDb("Linear integration saved with " + teamMappings.size() + " teams",
            LoggerMaker.LogDb.DASHBOARD);

        if (updated != null) {
            updated.setApiToken(null);
        }
        return updated;
    }

    private static Map<String, String> sanitizeSeverityMapping(LinearTeamMapping requested) throws Exception {
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (String severity : MAPPED_SEVERITIES) {
            String value = requested != null && requested.getSeverityToPriority() != null
                ? requested.getSeverityToPriority().get(severity)
                : null;
            if (value == null) {
                sanitized.put(severity, DEFAULT_SEVERITY_TO_PRIORITY.get(severity));
                continue;
            }
            if (!PRIORITY_VALUES.contains(value)) {
                throw new Exception("Invalid Linear priority for " + severity + ": " + value);
            }
            sanitized.put(severity, value);
        }
        return sanitized;
    }

    public LinearIntegration fetchLinearIntegration() {
        LinearIntegration integration = LinearIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration != null) {
            integration.setApiToken(null);
        }
        return integration;
    }

    public Map<String, String> fetchWorkspaceTeams() throws Exception {
        return toTeamsMap(LinearApiClient.fetchTeams(resolveApiToken()));
    }

    public Map<String, String> createTeam(String teamName, String teamKey) throws Exception {
        if (StringUtils.isBlank(teamName)) {
            throw new Exception("Please enter a team name.");
        }

        LinearTeam team = LinearApiClient.createTeam(resolveApiToken(), teamName.trim(), StringUtils.trimToNull(teamKey));
        logger.infoAndAddToDb("Created Linear team " + team.getKey(), LoggerMaker.LogDb.DASHBOARD);

        Map<String, String> created = new LinkedHashMap<>();
        created.put(team.getId(), team.getDisplayName());
        return created;
    }

    public void removeIntegration() throws Exception {
        try {
            LinearIntegrationDao.instance.getMCollection().deleteOne(new BasicDBObject());
            logger.infoAndAddToDb("Linear integration removed successfully", LoggerMaker.LogDb.DASHBOARD);
        } catch (Exception e) {
            logger.errorAndAddToDb("Error removing Linear integration: " + e.getMessage(),
                LoggerMaker.LogDb.DASHBOARD);
            throw new Exception("Failed to remove Linear integration. Please try again.");
        }
    }

    private String resolveApiToken() throws Exception {
        if (StringUtils.isNotBlank(apiToken)) {
            return apiToken.trim();
        }

        LinearIntegration existing = LinearIntegrationDao.instance.findOne(new BasicDBObject());
        if (existing == null || StringUtils.isBlank(existing.getApiToken())) {
            throw new Exception("Please enter a valid Linear API key.");
        }
        return existing.getApiToken();
    }

    private static Map<String, String> toTeamsMap(List<LinearTeam> teams) {
        Map<String, String> teamsMap = new LinkedHashMap<>();
        for (LinearTeam team : teams) {
            teamsMap.put(team.getId(), team.getDisplayName());
        }
        return teamsMap;
    }

    private static int resolvePriority(LinearIntegration integration, String teamId, Severity severity) {
        LinearTeamMapping mapping = integration.getTeamMappings() == null
            ? null
            : integration.getTeamMappings().get(teamId);
        String value = mapping != null && mapping.getSeverityToPriority() != null
            ? mapping.getSeverityToPriority().get(severity.name())
            : null;
        if (value == null) {
            value = DEFAULT_SEVERITY_TO_PRIORITY.getOrDefault(severity.name(), PRIORITY_NONE);
        }
        return Integer.parseInt(value);
    }

    @Override
    protected String getIntegrationName() {
        return GlobalEnums.TicketSource.LINEAR.name();
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
    protected LinearIntegration fetchIntegration() {
        return LinearIntegrationDao.instance.findOne(new BasicDBObject());
    }

    @Override
    protected String validateAndGetProjectIdentifier(LinearIntegration integration, String teamId) throws Exception {
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
    protected String getAuthenticationToken(LinearIntegration integration) {
        return integration.getApiToken();
    }

    @Override
    protected String getExistingTicketUrl(TestingRunIssues issue) {
        return issue.getLinearIssueUrl();
    }

    @Override
    protected TicketInfo createTicketForIssue(LinearIntegration integration, String authToken,
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

            String description = buildDescription(testInfo, testingRunResult, severity, method,
                endpointDetails, aktoIssueUrl);

            LinearIssue issue = LinearApiClient.createIssue(authToken, teamId, title, description,
                resolvePriority(integration, teamId, severity));

            linkAktoIssue(authToken, issue, aktoIssueUrl, testInfo, severity);

            return new TicketInfo(issue.getIdentifier(), issue.getUrl());
        } catch (Exception e) {
            logger.errorAndAddToDb("Exception creating Linear issue: " + e.getMessage(),
                LoggerMaker.LogDb.DASHBOARD);
            return null;
        }
    }

    private void linkAktoIssue(String authToken, LinearIssue issue, String aktoIssueUrl, Info testInfo,
        Severity severity) {
        if (StringUtils.isBlank(aktoIssueUrl)) {
            return;
        }

        try {
            LinearApiClient.createAttachment(authToken, issue.getId(), "Open in Akto", testInfo.getName(),
                aktoIssueUrl, new BasicDBObject("severity", severity.name()));
        } catch (Exception e) {
            logger.errorAndAddToDb("Linear issue " + issue.getIdentifier()
                + " created, but linking it to Akto failed: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }
    }

    private String buildDescription(Info testInfo, TestingRunResult testingRunResult, Severity severity,
        String method, Pair<String, String> endpointDetails, String aktoIssueUrl) {
        StringBuilder body = new StringBuilder();

        if (StringUtils.isNotEmpty(endpointDetails.getFirst())) {
            body.append("**Host:** ").append(endpointDetails.getFirst()).append("\n\n");
        }
        body.append("**Endpoint:** ").append(endpointDetails.getSecond()).append("\n\n");
        body.append("**HTTP Method:** ").append(method).append("\n\n");
        body.append("**Severity:** ").append(severity.name()).append("\n\n");
        if (aktoIssueUrl != null) {
            body.append("[Issue link - Akto Dashboard](").append(aktoIssueUrl).append(")\n\n");
        }

        appendTextSection(body, "Description", testInfo.getDescription());
        appendTextSection(body, "Impact", testInfo.getImpact());
        appendTextSection(body, "Remediation", testInfo.getRemediation());
        appendListSection(body, "Tags", testInfo.getTags());
        appendListSection(body, "CWE", testInfo.getCwe());
        appendListSection(body, "CVE", testInfo.getCve());
        appendListSection(body, "References", testInfo.getReferences());

        String requestResponseData = buildRequestResponseData(testingRunResult);
        if (StringUtils.isNotBlank(requestResponseData)) {
            body.append("---\n\n## Request/Response Details\n\n").append(requestResponseData);
        }

        return body.toString();
    }

    private String buildRequestResponseData(TestingRunResult testingRunResult) {
        try {
            if (testingRunResult.getTestResults() == null || testingRunResult.getTestResults().isEmpty()) {
                return "";
            }

            GenericTestResult gtr = testingRunResult.getTestResults().get(testingRunResult.getTestResults().size() - 1);
            if (!(gtr instanceof TestResult)) {
                return "";
            }

            TestResult testResult = (TestResult) gtr;
            String originalMessage = testResult.getOriginalMessage();
            String message = testResult.getMessage();

            if (StringUtils.isBlank(originalMessage) && StringUtils.isBlank(message)) {
                return "";
            }

            String origCurl = "";
            String origResponse = "";
            String testCurl = "";
            String testResponse = "";

            if (StringUtils.isNotBlank(originalMessage)) {
                origCurl = CurlUtils.getCurl(originalMessage);
                try {
                    HttpResponseParams origObj = com.akto.runtime.utils.Utils.parseKafkaMessage(originalMessage);
                    origResponse = origObj.getPayload();
                } catch (Exception e) {
                    logger.errorAndAddToDb(e, "Error in building http response params for Linear");
                }
            }

            if (StringUtils.isNotBlank(message)) {
                testCurl = CurlUtils.getCurl(message);
                BasicDBObject testRespObj = BasicDBObject.parse(message);
                BasicDBObject testPayloadObj = BasicDBObject.parse(testRespObj.getString("response"));
                testResponse = testPayloadObj.getString("body");
            }

            StringBuilder data = new StringBuilder();
            appendCodeBlock(data, "Test Curl", testCurl);
            appendCodeBlock(data, "Test API Response", testResponse);
            appendCodeBlock(data, "Original Curl", origCurl);
            appendCodeBlock(data, "Original API Response", origResponse);
            return data.toString();

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error building request/response data: " + e.getMessage(),
                LoggerMaker.LogDb.DASHBOARD);
            return "";
        }
    }

    private static void appendCodeBlock(StringBuilder data, String heading, String content) {
        if (StringUtils.isBlank(content)) {
            return;
        }
        data.append("### ").append(heading).append("\n\n```\n").append(content).append("\n```\n\n");
    }

    private static void appendTextSection(StringBuilder body, String heading, String text) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        body.append("## ").append(heading).append("\n\n").append(text).append("\n\n");
    }

    private static void appendListSection(StringBuilder body, String heading, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        body.append("## ").append(heading).append("\n\n");
        for (String item : items) {
            body.append("- ").append(item).append("\n");
        }
        body.append("\n");
    }
}
