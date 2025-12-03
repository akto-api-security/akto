package com.akto.action;

import com.akto.dao.ServiceNowIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.servicenow_integration.ServiceNowIntegration;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBList;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import okhttp3.*;
import org.bson.conversions.Bson;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.akto.utils.Utils.createRequestFile;
import static com.akto.utils.Utils.getTestResultFromTestingRunResult;

public class ServiceNowIntegrationAction extends UserAction {

    private String instanceUrl;
    private String clientId;
    private String clientSecret;
    private List<String> tableNames;
    private ServiceNowIntegration serviceNowIntegration;
    private List<ServiceNowTable> tables;

    private static final LoggerMaker logger = new LoggerMaker(ServiceNowIntegrationAction.class, LogDb.DASHBOARD);

    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public static class ServiceNowTable {
        private String name;
        private String label;

        public ServiceNowTable() {}

        public ServiceNowTable(String name, String label) {
            this.name = name;
            this.label = label;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    public String fetchServiceNowIntegration() {
        serviceNowIntegration = ServiceNowIntegrationDao.instance.findOne(
            new BasicDBObject(),
            Projections.exclude(ServiceNowIntegration.CLIENT_SECRET)
        );
        return Action.SUCCESS.toUpperCase();
    }

    public String fetchServiceNowTables() {
        if (instanceUrl == null || instanceUrl.isEmpty()) {
            addActionError("Please enter a valid instance URL.");
            return Action.ERROR.toUpperCase();
        }

        if (clientId == null || clientId.isEmpty()) {
            addActionError("Please enter a valid client ID.");
            return Action.ERROR.toUpperCase();
        }

        if (clientSecret == null || clientSecret.isEmpty()) {
            addActionError("Please enter a valid client secret.");
            return Action.ERROR.toUpperCase();
        }

        // Remove trailing slash from instance URL
        if (instanceUrl.endsWith("/")) {
            instanceUrl = instanceUrl.substring(0, instanceUrl.length() - 1);
        }

        try {
            logger.info("Fetching ServiceNow tables for instance: {}", instanceUrl);

            // Get OAuth token first
            String accessToken = getOAuthToken(instanceUrl, clientId, clientSecret);
            if (accessToken == null) {
                addActionError("Failed to authenticate with ServiceNow. Please verify your client ID and client secret are correct, and that the OAuth endpoint is enabled in your ServiceNow instance.");
                return Action.ERROR.toUpperCase();
            }

            // Fetch tables from ServiceNow using Table API
            // Filter for tables that extend the Task table (super_class=task or super_class.super_class=task, etc.)
            // This includes: Incident, Problem, Change Request, and other task-based tables
            String url = instanceUrl + "/api/now/table/sys_db_object?sysparm_query=super_class.name=task&sysparm_fields=name,label,super_class&sysparm_limit=200";

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Accept", "application/json")
                    .build();

            Response response = null;
            try {
                response = client.newCall(request).execute();
                String responsePayload = response.body() != null ? response.body().string() : "";

                logger.info("Response from ServiceNow tables API: {} | {}", response.code(), responsePayload);

                if (response.isSuccessful()) {
                    BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
                    BasicDBList resultList = (BasicDBList) respPayloadObj.get("result");

                    this.tables = new ArrayList<>();
                    if (resultList != null) {
                        logger.info("Found {} tables that extend Task table", resultList.size());
                        for (Object tableObj : resultList) {
                            BasicDBObject table = (BasicDBObject) tableObj;
                            String name = table.getString("name");
                            String label = table.getString("label");
                            if (name != null && !name.isEmpty()) {
                                this.tables.add(new ServiceNowTable(name, label != null ? label : name));
                                logger.info("Added table: {} ({})", label, name);
                            }
                        }
                    }

                    logger.info("Total tables returned to frontend: {}", this.tables.size());
                    clientSecret = "********";
                    return Action.SUCCESS.toUpperCase();
                } else {
                    logger.error("Error fetching ServiceNow tables: {} | {}", response.code(), responsePayload);
                    addActionError("Failed to fetch tables from ServiceNow. Status: " + response.code());
                    return Action.ERROR.toUpperCase();
                }
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        } catch (Exception e) {
            logger.error("Exception while fetching ServiceNow tables: {}", e.getMessage());
            e.printStackTrace();
            addActionError("An error occurred while fetching ServiceNow tables.");
            return Action.ERROR.toUpperCase();
        }
    }

    private String getOAuthToken(String instanceUrl, String clientId, String clientSecret) {
        Response response = null;
        try {
            // ServiceNow OAuth endpoint
            String tokenUrl = instanceUrl + "/oauth_token.do";

            // Create form body using OkHttp's FormBody
            RequestBody formBody = new FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("client_id", clientId)
                    .add("client_secret", clientSecret)
                    .build();

            Request request = new Request.Builder()
                    .url(tokenUrl)
                    .post(formBody)
                    .addHeader("Accept", "application/json")
                    .build();

            logger.info("OAuth token request URL: {}", tokenUrl);
            logger.info("Requesting OAuth token...");

            response = client.newCall(request).execute();
            String responsePayload = response.body() != null ? response.body().string() : "";

            logger.info("OAuth token response - Status: {} | Body: {}", response.code(), responsePayload);

            if (response.isSuccessful()) {
                if (responsePayload == null || responsePayload.isEmpty()) {
                    logger.error("Empty response body from OAuth token endpoint");
                    return null;
                }

                try {
                    BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
                    String accessToken = respPayloadObj.getString("access_token");
                    if (accessToken != null && !accessToken.isEmpty()) {
                        logger.info("Successfully obtained OAuth token");
                        return accessToken;
                    } else {
                        logger.error("No access_token in response: {}", responsePayload);
                        return null;
                    }
                } catch (Exception parseEx) {
                    logger.error("Failed to parse OAuth response: {}", parseEx.getMessage());
                    return null;
                }
            } else {
                logger.error("Failed to get OAuth token - Status: {} | Response: {}", response.code(), responsePayload);
                return null;
            }
        } catch (Exception e) {
            logger.error("Exception while getting OAuth token: {}", e.getMessage(), e);
            return null;
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    public String addServiceNowIntegration() {
        if (instanceUrl == null || instanceUrl.isEmpty()) {
            addActionError("Please enter a valid instance URL.");
            return Action.ERROR.toUpperCase();
        }

        if (clientId == null || clientId.isEmpty()) {
            addActionError("Please enter a valid client ID.");
            return Action.ERROR.toUpperCase();
        }

        if (clientSecret == null || clientSecret.isEmpty()) {
            addActionError("Please enter a valid client secret.");
            return Action.ERROR.toUpperCase();
        }

        if (tableNames == null || tableNames.isEmpty()) {
            addActionError("Please select at least one table.");
            return Action.ERROR.toUpperCase();
        }

        // Remove trailing slash from instance URL
        if (instanceUrl.endsWith("/")) {
            instanceUrl = instanceUrl.substring(0, instanceUrl.length() - 1);
        }

        // Verify credentials by getting OAuth token
        String accessToken = getOAuthToken(instanceUrl, clientId, clientSecret);
        if (accessToken == null) {
            addActionError("Failed to authenticate with ServiceNow. Please verify your credentials.");
            return Action.ERROR.toUpperCase();
        }

        Bson combineUpdates = Updates.combine(
            Updates.set(ServiceNowIntegration.INSTANCE_URL, instanceUrl),
            Updates.set(ServiceNowIntegration.CLIENT_ID, clientId),
            Updates.set(ServiceNowIntegration.CLIENT_SECRET, clientSecret),
            Updates.set(ServiceNowIntegration.TABLE_NAMES, tableNames),
            Updates.setOnInsert(ServiceNowIntegration.CREATED_TS, Context.now()),
            Updates.set(ServiceNowIntegration.UPDATED_TS, Context.now())
        );

        ServiceNowIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            combineUpdates,
            new UpdateOptions().upsert(true)
        );

        return Action.SUCCESS.toUpperCase();
    }

    public String removeServiceNowIntegration() {
        ServiceNowIntegrationDao.instance.deleteAll(new BasicDBObject());
        return Action.SUCCESS.toUpperCase();
    }

    // Ticket Creation Methods
    private TestingIssuesId testingIssuesId;
    private String serviceNowTicketUrl;
    private String serviceNowTicketNumber;
    private String tableName;

    public String createServiceNowTicket() {
        ServiceNowIntegration integration = ServiceNowIntegrationDao.instance.findOne(Filters.empty());
        if (integration == null) {
            logger.error("ServiceNow Integration not found for account: {}", Context.accountId.get());
            addActionError("ServiceNow Integration is not configured.");
            return Action.ERROR.toUpperCase();
        }

        if (testingIssuesId == null) {
            addActionError("Testing issue ID is required.");
            return Action.ERROR.toUpperCase();
        }

        // Get OAuth token
        String accessToken = getOAuthToken(integration.getInstanceUrl(), integration.getClientId(), integration.getClientSecret());
        if (accessToken == null) {
            addActionError("Failed to authenticate with ServiceNow.");
            return Action.ERROR.toUpperCase();
        }

        // Fetch test details
        TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(
            Filters.and(
                Filters.in(TestingRunResult.TEST_SUB_TYPE, testingIssuesId.getTestSubCategory()),
                Filters.in(TestingRunResult.API_INFO_KEY, testingIssuesId.getApiInfoKey())
            )
        );

        if (testingRunResult == null) {
            logger.error("TestingRunResult not found for issue ID: {}", testingIssuesId);
            addActionError("Test result not found.");
            return Action.ERROR.toUpperCase();
        }

        Info testInfo = YamlTemplateDao.instance.findOne(
            Filters.in(Constants.ID, testingIssuesId.getTestSubCategory()),
            Projections.include(YamlTemplate.INFO + ".description", YamlTemplate.INFO + ".name")
        ).getInfo();

        String testName = testInfo.getName();
        String testDescription = testInfo.getDescription();

        // Get severity from TestingRunIssues
        TestingRunIssues testingRunIssues = TestingRunIssuesDao.instance.findOne(
            Filters.eq(Constants.ID, testingIssuesId)
        );
        GlobalEnums.Severity severity = testingRunIssues != null ? testingRunIssues.getSeverity() : GlobalEnums.Severity.HIGH;

        // Use provided table name or fall back to first configured table
        if (tableName == null || tableName.isEmpty()) {
            List<String> configuredTables = integration.getTableNames();
            if (configuredTables == null || configuredTables.isEmpty()) {
                addActionError("No ServiceNow tables configured.");
                return Action.ERROR.toUpperCase();
            }
            tableName = configuredTables.get(0);
        }

        boolean success = createTicketInTable(integration, accessToken, tableName, testName, testDescription, testingRunResult, severity);

        if (success) {
            // Update TestingRunIssues with ticket info
            TestingRunIssuesDao.instance.updateOneNoUpsert(
                Filters.eq(Constants.ID, testingIssuesId),
                Updates.combine(
                    Updates.set(TestingRunIssues.SERVICENOW_ISSUE_URL, serviceNowTicketUrl),
                    Updates.set(TestingRunIssues.TICKET_SOURCE, GlobalEnums.TicketSource.SERVICENOW.name()),
                    Updates.set(TestingRunIssues.TICKET_ID, serviceNowTicketNumber),
                    Updates.set(TestingRunIssues.TICKET_LAST_UPDATED_AT, Context.now())
                )
            );
            return Action.SUCCESS.toUpperCase();
        } else {
            addActionError("Failed to create ServiceNow ticket.");
            return Action.ERROR.toUpperCase();
        }
    }

    private boolean createTicketInTable(ServiceNowIntegration integration, String accessToken, String tableName,
                                        String testName, String testDescription, TestingRunResult testingRunResult,
                                        GlobalEnums.Severity severity) {
        try {
            String url = integration.getInstanceUrl() + "/api/now/table/" + tableName;

            // Build ticket payload
            BasicDBObject ticketData = new BasicDBObject();
            ticketData.put("short_description", "Security Issue: " + testName);

            // Build detailed description
            StringBuilder description = new StringBuilder();
            description.append("Test: ").append(testName).append("\n\n");
            description.append("Description: ").append(testDescription).append("\n\n");

            if (testingRunResult != null) {
                description.append("API Endpoint: ").append(testingRunResult.getApiInfoKey().getUrl()).append("\n");
                description.append("Method: ").append(testingRunResult.getApiInfoKey().getMethod()).append("\n");
            }

            ticketData.put("description", description.toString());

            // Map Akto severity to ServiceNow priority/urgency/impact
            // ServiceNow uses 1-5 scale where 1 is highest priority
            String urgency, impact, priority;
            switch (severity) {
                case CRITICAL:
                    urgency = "1";   // Critical
                    impact = "1";    // Critical
                    priority = "1";  // Critical
                    break;
                case HIGH:
                    urgency = "1";   // High
                    impact = "1";    // High
                    priority = "2";  // High
                    break;
                case MEDIUM:
                    urgency = "2";   // Moderate
                    impact = "2";    // Moderate
                    priority = "3";  // Moderate
                    break;
                case LOW:
                    urgency = "3";   // Low
                    impact = "3";    // Low
                    priority = "4";  // Low
                    break;
                default:
                    urgency = "3";   // Default to Moderate
                    impact = "3";
                    priority = "3";
                    break;
            }

            ticketData.put("urgency", urgency);
            ticketData.put("impact", impact);
            ticketData.put("priority", priority);

            Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(ticketData.toJson(), MediaType.parse("application/json")))
                .addHeader("Authorization", "Bearer " + accessToken)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build();

            Response response = null;
            try {
                response = client.newCall(request).execute();
                String responsePayload = response.body() != null ? response.body().string() : "";

                logger.info("ServiceNow ticket creation response - Status: {} | Body: {}", response.code(), responsePayload);

                if (response.isSuccessful()) {
                    BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
                    BasicDBObject result = (BasicDBObject) respPayloadObj.get("result");

                    if (result != null) {
                        serviceNowTicketNumber = result.getString("number");
                        String sysId = result.getString("sys_id");
                        serviceNowTicketUrl = integration.getInstanceUrl() + "/nav_to.do?uri=" + tableName + ".do?sys_id=" + sysId;

                        logger.info("Created ServiceNow ticket: {} in table: {}", serviceNowTicketNumber, tableName);

                        // Attach request/response file to the ticket
                        attachFileToServiceNowTicket(integration, accessToken, tableName, sysId, testingRunResult);

                        return true;
                    }
                }

                logger.error("Failed to create ServiceNow ticket - Status: {} | Response: {}", response.code(), responsePayload);
                return false;
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        } catch (Exception e) {
            logger.error("Exception while creating ServiceNow ticket: {}", e.getMessage(), e);
            return false;
        }
    }

    private void attachFileToServiceNowTicket(ServiceNowIntegration integration, String accessToken,
                                              String tableName, String sysId, TestingRunResult testingRunResult) {
        try {
            // Get test result with original and test messages
            TestResult testResult = getTestResultFromTestingRunResult(testingRunResult);
            if (testResult == null) {
                logger.info("No test result found for attachment");
                return;
            }

            // Create the request file with original and test messages
            File tmpOutputFile = createRequestFile(testResult.getOriginalMessage(), testResult.getMessage());
            if (tmpOutputFile == null) {
                logger.info("Could not create attachment file");
                return;
            }

            try {
                // ServiceNow Attachment API endpoint with table_name and table_sys_id as query parameters
                String attachmentUrl = integration.getInstanceUrl() +
                    "/api/now/attachment/file?table_name=" + tableName +
                    "&table_sys_id=" + sysId +
                    "&file_name=" + tmpOutputFile.getName();

                logger.info("ServiceNow attachment URL: {}", attachmentUrl);

                // Build request body with just the file content
                RequestBody requestBody = RequestBody.create(tmpOutputFile, MediaType.parse("text/plain"));

                Request request = new Request.Builder()
                    .url(attachmentUrl)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "text/plain")
                    .build();

                Response response = null;
                try {
                    response = client.newCall(request).execute();
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        logger.info("Successfully attached file to ServiceNow ticket: {} | Response: {}",
                            serviceNowTicketNumber, responseBody);
                    } else {
                        logger.warn("Failed to attach file to ServiceNow ticket - Status: {} | Response: {}",
                            response.code(), responseBody);
                    }
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            } finally {
                // Clean up temp file
                tmpOutputFile.delete();
            }
        } catch (Exception e) {
            logger.error("Exception while attaching file to ServiceNow ticket: {}", e.getMessage(), e);
            // Don't fail the ticket creation if attachment fails
        }
    }

    private List<TestingIssuesId> testingIssuesIdList;
    private String errorMessage;

    public String bulkCreateServiceNowTickets() {
        if (testingIssuesIdList == null || testingIssuesIdList.isEmpty()) {
            addActionError("Cannot create empty ServiceNow tickets.");
            return Action.ERROR.toUpperCase();
        }

        ServiceNowIntegration integration = ServiceNowIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            logger.error("ServiceNow Integration not found");
            addActionError("ServiceNow Integration is not configured.");
            return Action.ERROR.toUpperCase();
        }

        // Get OAuth token once for all tickets
        String accessToken = getOAuthToken(integration.getInstanceUrl(), integration.getClientId(), integration.getClientSecret());
        if (accessToken == null) {
            addActionError("Failed to authenticate with ServiceNow.");
            return Action.ERROR.toUpperCase();
        }

        // Check for existing issues
        int existingIssues = 0;
        List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(
            Filters.and(
                Filters.in(Constants.ID, testingIssuesIdList),
                Filters.exists(TestingRunIssues.SERVICENOW_ISSUE_URL, true)
            )
        );

        if (!testingRunIssuesList.isEmpty()) {
            existingIssues = testingRunIssuesList.size();
        }

        int successCount = 0;
        int failCount = 0;

        // Use provided table name or fall back to first configured table
        if (tableName == null || tableName.isEmpty()) {
            List<String> configuredTables = integration.getTableNames();
            if (configuredTables == null || configuredTables.isEmpty()) {
                addActionError("No ServiceNow tables configured.");
                return Action.ERROR.toUpperCase();
            }
            tableName = configuredTables.get(0);
        }

        for (TestingIssuesId issueId : testingIssuesIdList) {
            try {
                // Check if ticket already exists
                TestingRunIssues existingIssue = TestingRunIssuesDao.instance.findOne(
                    Filters.eq(Constants.ID, issueId)
                );

                if (existingIssue != null && existingIssue.getServicenowIssueUrl() != null) {
                    logger.info("Ticket already exists for issue: {}", issueId);
                    continue;
                }

                // Fetch test details
                TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(
                    Filters.and(
                        Filters.in(TestingRunResult.TEST_SUB_TYPE, issueId.getTestSubCategory()),
                        Filters.in(TestingRunResult.API_INFO_KEY, issueId.getApiInfoKey())
                    )
                );

                if (testingRunResult == null) {
                    logger.error("TestingRunResult not found for issue: {}", issueId);
                    failCount++;
                    continue;
                }

                Info testInfo = YamlTemplateDao.instance.findOne(
                    Filters.in(Constants.ID, issueId.getTestSubCategory()),
                    Projections.include(YamlTemplate.INFO + ".description", YamlTemplate.INFO + ".name")
                ).getInfo();

                String testName = testInfo.getName();
                String testDescription = testInfo.getDescription();

                // Get severity from existing issue
                GlobalEnums.Severity severity = existingIssue != null ? existingIssue.getSeverity() : GlobalEnums.Severity.HIGH;

                // Create ticket
                if (createTicketInTable(integration, accessToken, tableName, testName, testDescription, testingRunResult, severity)) {
                    // Update issue with ticket info
                    TestingRunIssuesDao.instance.updateOneNoUpsert(
                        Filters.eq(Constants.ID, issueId),
                        Updates.combine(
                            Updates.set(TestingRunIssues.SERVICENOW_ISSUE_URL, serviceNowTicketUrl),
                            Updates.set(TestingRunIssues.TICKET_SOURCE, GlobalEnums.TicketSource.SERVICENOW.name()),
                            Updates.set(TestingRunIssues.TICKET_ID, serviceNowTicketNumber),
                            Updates.set(TestingRunIssues.TICKET_LAST_UPDATED_AT, Context.now())
                        )
                    );
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                logger.error("Error creating ticket for issue {}: {}", issueId, e.getMessage(), e);
                failCount++;
            }
        }

        errorMessage = String.format("Created %d tickets, %d failed, %d already existed", successCount, failCount, existingIssues);
        logger.info(errorMessage);

        return Action.SUCCESS.toUpperCase();
    }

    // Getters and Setters
    public String getInstanceUrl() {
        return instanceUrl;
    }

    public void setInstanceUrl(String instanceUrl) {
        this.instanceUrl = instanceUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public List<String> getTableNames() {
        return tableNames;
    }

    public void setTableNames(List<String> tableNames) {
        this.tableNames = tableNames;
    }

    public ServiceNowIntegration getServiceNowIntegration() {
        return serviceNowIntegration;
    }

    public void setServiceNowIntegration(ServiceNowIntegration serviceNowIntegration) {
        this.serviceNowIntegration = serviceNowIntegration;
    }

    public List<ServiceNowTable> getTables() {
        return tables;
    }

    public void setTables(List<ServiceNowTable> tables) {
        this.tables = tables;
    }

    public TestingIssuesId getTestingIssuesId() {
        return testingIssuesId;
    }

    public void setTestingIssuesId(TestingIssuesId testingIssuesId) {
        this.testingIssuesId = testingIssuesId;
    }

    public String getServiceNowTicketUrl() {
        return serviceNowTicketUrl;
    }

    public void setServiceNowTicketUrl(String serviceNowTicketUrl) {
        this.serviceNowTicketUrl = serviceNowTicketUrl;
    }

    public String getServiceNowTicketNumber() {
        return serviceNowTicketNumber;
    }

    public void setServiceNowTicketNumber(String serviceNowTicketNumber) {
        this.serviceNowTicketNumber = serviceNowTicketNumber;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public List<TestingIssuesId> getTestingIssuesIdList() {
        return testingIssuesIdList;
    }

    public void setTestingIssuesIdList(List<TestingIssuesId> testingIssuesIdList) {
        this.testingIssuesIdList = testingIssuesIdList;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
