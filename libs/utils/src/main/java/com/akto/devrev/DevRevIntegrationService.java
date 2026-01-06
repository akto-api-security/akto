package com.akto.devrev;

import com.akto.dao.DevRevIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.devrev_integration.DevRevIntegration;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.akto.ticketing.ATicketIntegrationService;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.TicketSource;
import com.akto.utils.CurlUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

@Getter
@Setter
@NoArgsConstructor
public class DevRevIntegrationService extends ATicketIntegrationService<DevRevIntegration> {

    private static final LoggerMaker logger = new LoggerMaker(DevRevIntegrationService.class, LoggerMaker.LogDb.DASHBOARD);

    private String orgUrl;
    private String personalAccessToken;

    public DevRevIntegrationService(String orgUrl, String personalAccessToken) {
        this.orgUrl = orgUrl;
        this.personalAccessToken = personalAccessToken;
    }

    public DevRevIntegration addIntegration(Map<String, String> partsIdToNameMap) throws Exception {
        if (orgUrl == null || orgUrl.isEmpty()) {
            throw new Exception("Please enter a valid organization URL.");
        }

        if (orgUrl.endsWith("/")) {
            orgUrl = orgUrl.substring(0, orgUrl.length() - 1);
        }

        String actualToken = getPersonalAccessToken(personalAccessToken);

        if (partsIdToNameMap == null || partsIdToNameMap.isEmpty()) {
            throw new Exception("Please select at least one part.");
        }

        int currTimeStamp = Context.now();
        Bson combineUpdates = Updates.combine(
                Updates.set(DevRevIntegration.ORG_URL, orgUrl),
                Updates.set(DevRevIntegration.PARTS_ID_TO_NAME_MAP, partsIdToNameMap),
                Updates.set(DevRevIntegration.PERSONAL_ACCESS_TOKEN, actualToken),
                Updates.setOnInsert(DevRevIntegration.CREATED_TS, currTimeStamp),
                Updates.set(DevRevIntegration.UPDATED_TS, currTimeStamp)
        );

        DevRevIntegration updatedIntegration = DevRevIntegrationDao.instance.getMCollection().findOneAndUpdate(
                new BasicDBObject(),
                combineUpdates,
                new FindOneAndUpdateOptions()
                        .upsert(true)
                        .returnDocument(ReturnDocument.AFTER)
        );

        logger.infoAndAddToDb("DevRev integration added successfully", LoggerMaker.LogDb.DASHBOARD);

        if (updatedIntegration != null) {
            updatedIntegration.setPersonalAccessToken(null);
        }

        return updatedIntegration;
    }

    private Map<String, String> fetchAllPartsFromDevRev(String token) {
        Map<String, String> partsIdToNameMap = new HashMap<>();
        String url = DevRevIntegration.API_BASE_URL + "/parts.list";

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + token));
        headers.put("Content-Type", Collections.singletonList("application/json"));

        String cursor = null;
        String lastCursor = null;
        int maxIterations = 20; // Safety limit
        int iteration = 0;

        try {
            do {
                // Build request body with cursor and limit
                BasicDBObject requestBody = new BasicDBObject("limit", 100);
                if (cursor != null) {
                    requestBody.put("cursor", cursor);
                }

                OriginalHttpRequest request = new OriginalHttpRequest(
                    url, "", Method.POST.name(),
                    requestBody.toJson(),
                    headers, ""
                );

                OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());

                if (response.getStatusCode() > 201) {
                    logger.errorAndAddToDb("Failed to fetch parts from DevRev: " + response.getBody(), LoggerMaker.LogDb.DASHBOARD);
                    break;
                }

                BasicDBObject respPayloadObj = BasicDBObject.parse(response.getBody());
                BasicDBList partsListObj = (BasicDBList) respPayloadObj.get("parts");

                if (partsListObj != null) {
                    for (Object partObj : partsListObj) {
                        BasicDBObject part = (BasicDBObject) partObj;
                        String partId = part.getString("id");
                        String partName = part.getString("name");
                        partsIdToNameMap.put(partId, partName);
                    }
                }

                // Get next cursor
                cursor = respPayloadObj.getString("next_cursor");

                // Infinite loop protection
                if (cursor != null && cursor.equals(lastCursor)) {
                    logger.errorAndAddToDb("Detected same cursor, stopping pagination", LoggerMaker.LogDb.DASHBOARD);
                    break;
                }

                lastCursor = cursor;
                iteration++;

                if (iteration >= maxIterations) {
                    logger.errorAndAddToDb("Max iterations reached, stopping pagination", LoggerMaker.LogDb.DASHBOARD);
                    break;
                }

            } while (cursor != null);

            logger.infoAndAddToDb("Fetched " + partsIdToNameMap.size() + " parts from DevRev in " + iteration + " iterations", LoggerMaker.LogDb.DASHBOARD);

        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching parts from DevRev: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }

        return partsIdToNameMap;
    }

    public DevRevIntegration fetchDevRevIntegration() {
        DevRevIntegration integration = DevRevIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration != null) {
            integration.setPersonalAccessToken(null);
        }
        return integration;
    }

    public Map<String, String> fetchDevrevProjects() throws Exception {
        String actualToken = getPersonalAccessToken(personalAccessToken);

        Map<String, String> partsIdToNameMap = fetchAllPartsFromDevRev(actualToken);

        if (partsIdToNameMap.isEmpty()) {
            throw new Exception("Failed to fetch projects from DevRev. Please verify your personal access token and try again.");
        }

        return partsIdToNameMap;
    }

    public void removeIntegration() throws Exception {
        try {
            DevRevIntegrationDao.instance.getMCollection().deleteOne(new BasicDBObject());
            logger.infoAndAddToDb("DevRev integration removed successfully", LoggerMaker.LogDb.DASHBOARD);
        } catch (Exception e) {
            logger.errorAndAddToDb("Error removing DevRev integration: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            throw new Exception("Failed to remove DevRev integration. Please try again.");
        }
    }

    private String getPersonalAccessToken(String token) throws Exception {
        if (token != null && !token.isEmpty()) {
            return token;
        }

        DevRevIntegration existingIntegration = DevRevIntegrationDao.instance.findOne(new BasicDBObject());
        if (existingIntegration == null || existingIntegration.getPersonalAccessToken() == null
            || existingIntegration.getPersonalAccessToken().isEmpty()) {
            throw new Exception("Please enter a valid personal access token.");
        }
        return existingIntegration.getPersonalAccessToken();
    }

    @Override
    protected String getIntegrationName() {
        return TicketSource.DEVREV.name();
    }

    @Override
    protected String getTicketUrlFieldName() {
        return TestingRunIssues.DEVREV_WORK_URL;
    }

    @Override
    protected GlobalEnums.TicketSource getTicketSource() {
        return GlobalEnums.TicketSource.DEVREV;
    }

    @Override
    protected DevRevIntegration fetchIntegration() {
        return DevRevIntegrationDao.instance.findOne(new BasicDBObject());
    }

    @Override
    protected String validateAndGetProjectIdentifier(DevRevIntegration integration, String partId) throws Exception {
        if (StringUtils.isBlank(partId)) {
            throw new Exception("Part ID is required.");
        }

        Map<String, String> partsMap = integration.getPartsMap();
        if (partsMap == null || partsMap.isEmpty()) {
            throw new Exception("No DevRev parts configured.");
        }

        if (!partsMap.containsKey(partId)) {
            throw new Exception("Invalid part ID: " + partId);
        }

        return partId;
    }

    @Override
    protected String getAuthenticationToken(DevRevIntegration integration) {
        return integration.getPersonalAccessToken();
    }

    @Override
    protected String getExistingTicketUrl(TestingRunIssues issue) {
        return issue.getDevrevWorkUrl();
    }

    @Override
    protected TicketInfo createTicketForIssue(DevRevIntegration integration, String authToken, TestingIssuesId issueId,
        Info testInfo, TestingRunResult testingRunResult, GlobalEnums.Severity severity, String partId, String workItemType, String aktoDashboardHost) {

        try {
            BasicDBObject ticketPayload = buildDevRevTicketPayload(
                testInfo,
                testingRunResult,
                issueId,
                severity,
                partId,
                workItemType,
                aktoDashboardHost
            );

            return createDevRevWorkItem(integration, authToken, ticketPayload);

        } catch (Exception e) {
            logger.errorAndAddToDb("Exception creating DevRev ticket: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            return null;
        }
    }

    private BasicDBObject buildDevRevTicketPayload(
            Info testInfo,
            TestingRunResult testingRunResult,
            TestingIssuesId issueId,
            GlobalEnums.Severity severity,
            String partId,
            String workItemType,
            String aktoDashboardHost) {

        BasicDBObject payload = new BasicDBObject();

        String type = StringUtils.isBlank(workItemType) ? "issue" : workItemType;
        payload.put("type", type);

        String fullUrl = issueId.getApiInfoKey().getUrl();
        String method = issueId.getApiInfoKey().getMethod().name();

        Pair<String, String> endpointDetails = getEndpointDetails(fullUrl);
        String hostname = endpointDetails.getFirst();
        String endpointPath = endpointDetails.getSecond();

        String title = String.format("Security Issue: %s (%s - %s)",
            testInfo.getName(), method, endpointPath);

        // Max length for title (as per deverv docs)
        if (title.length() > 256) {
            title = title.substring(0, 253) + "...";
        }

        payload.put("title", title);

        StringBuilder body = new StringBuilder();
        body.append("**Test Name:** ").append(testInfo.getName()).append("\n\n");
        if (StringUtils.isNotEmpty(hostname)) {
            body.append("**Host - ").append(hostname).append("**\n\n");
        }
        body.append("**Endpoint - ").append(endpointPath).append("**\n\n");
        if (StringUtils.isNotBlank(aktoDashboardHost)) {
            String issueUrl = aktoDashboardHost + "/dashboard/issues?result=" + testingRunResult.getId().toHexString();
            body.append("<a href=\"").append(issueUrl).append("\">Issue Link - Akto Dashboard</a>").append("\n\n");
        }
        body.append("**Description:** ").append(testInfo.getDescription()).append("\n\n");
        body.append("**Severity:** ").append(severity.name()).append("\n\n");
        body.append("**HTTP Method:** ").append(method).append("\n\n");

        if (testingRunResult != null) {
            body.append("**Test Result ID:** ").append(testingRunResult.getId().toHexString()).append("\n");
            body.append("\n");

            String requestResponseData = buildRequestResponseData(testingRunResult);
            if (StringUtils.isNotBlank(requestResponseData)) {
                body.append("---\n\n");
                body.append("## Request/Response Details\n\n");
                body.append(requestResponseData);
            }
        }

        String bodyString = body.toString();
        // Max length for body (as per deverv docs)
        if (bodyString.length() > 65536) {
            bodyString = bodyString.substring(0, 65500) + "...";
        }

        payload.put("body", bodyString);
        payload.put("applies_to_part", partId);
        return payload;
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
                    logger.errorAndAddToDb(e, "Error in building http response params for DevRev");
                }
            }

            if (StringUtils.isNotBlank(message)) {
                testCurl = CurlUtils.getCurl(message);
                BasicDBObject testRespObj = BasicDBObject.parse(message);
                BasicDBObject testPayloadObj = BasicDBObject.parse(testRespObj.getString("response"));
                testResponse = testPayloadObj.getString("body");
            }

            StringBuilder data = new StringBuilder();

            if (StringUtils.isNotBlank(testCurl)) {
                data.append("### Test Curl\n\n");
                data.append("```\n").append(testCurl).append("\n```\n\n");
            }

            if (StringUtils.isNotBlank(testResponse)) {
                data.append("### Test API Response\n\n");
                data.append("```\n").append(testResponse).append("\n```\n\n");
            }

            if (StringUtils.isNotBlank(origCurl)) {
                data.append("### Original Curl\n\n");
                data.append("```\n").append(origCurl).append("\n```\n\n");
            }

            if (StringUtils.isNotBlank(origResponse)) {
                data.append("### Original API Response\n\n");
                data.append("```\n").append(origResponse).append("\n```\n\n");
            }

            return data.toString();

        } catch (Exception e) {
            logger.errorAndAddToDb(e,"Error building request/response data: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            return "";
        }
    }

    private TicketInfo createDevRevWorkItem(DevRevIntegration integration, String token, BasicDBObject ticketPayload) {

        try {
            String url = DevRevIntegration.API_BASE_URL + "/works.create";

            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Authorization", Collections.singletonList("Bearer " + token));
            headers.put("Content-Type", Collections.singletonList("application/json"));

            OriginalHttpRequest request = new OriginalHttpRequest(
                url,
                "",
                Method.POST.name(),
                ticketPayload.toJson(),
                headers,
                ""
            );

            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());

            logger.infoAndAddToDb("DevRev work item creation response - Status: " + response.getStatusCode(), LoggerMaker.LogDb.DASHBOARD);

            if (response.getStatusCode() > 201) {
                logger.errorAndAddToDb("Failed to create DevRev work item - Status: " + response.getStatusCode() +
                    " | Response: " + response.getBody(), LoggerMaker.LogDb.DASHBOARD);
                return null;
            }

            String responsePayload = response.getBody();
            BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
            BasicDBObject work = (BasicDBObject) respPayloadObj.get("work");

            if (work != null) {
                String workId = work.getString("display_id");
                String workItemUrl = integration.getOrgUrl() + "/works/" + workId;
                logger.infoAndAddToDb("Created DevRev work item: " + workId, LoggerMaker.LogDb.DASHBOARD);
                return new TicketInfo(workId, workItemUrl);
            }

            logger.errorAndAddToDb("No work item returned in DevRev response", LoggerMaker.LogDb.DASHBOARD);
            return null;

        } catch (Exception e) {
            logger.errorAndAddToDb("Exception while creating DevRev work item: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            return null;
        }
    }
}