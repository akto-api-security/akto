package com.akto.action;

import com.akto.audit_logs_util.Audit;
import com.akto.dao.AzureBoardsIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.FilterConfigYamlParser;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.azure_boards_integration.AzureBoardsIntegration;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.MultiExecTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import org.bson.conversions.Bson;
import com.akto.dto.azure_boards_integration.AzureBoardsIntegration.AzureBoardsOperations;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static com.akto.utils.Utils.createRequestFile;
import static com.akto.utils.Utils.getTestResultFromTestingRunResult;

import static com.akto.utils.AzureBoardsUtils.getAccountAzureBoardFields;

public class AzureBoardsIntegrationAction extends UserAction {

    private String azureBoardsBaseUrl;
    private String organization;
    private List<String> projectList;
    private String personalAuthToken;

    private AzureBoardsIntegration azureBoardsIntegration;

    public static final String version = "7.1";

    private static final LoggerMaker logger = new LoggerMaker(AzureBoardsIntegrationAction.class, LoggerMaker.LogDb.DASHBOARD);


    public String fetchAzureBoardsIntegration() {
        azureBoardsIntegration = AzureBoardsIntegrationDao.instance.findOne(new BasicDBObject(), Projections.exclude(AzureBoardsIntegration.PERSONAL_AUTH_TOKEN));

        return Action.SUCCESS.toUpperCase();
    }

    public String addAzureBoardsIntegration() {
        if(azureBoardsBaseUrl == null || azureBoardsBaseUrl.isEmpty()) {
            addActionError("Please enter a valid base url.");
            return Action.ERROR.toUpperCase();
        }

        if(organization == null || organization.isEmpty()) {
            addActionError("Please enter a valid organization.");
            return Action.ERROR.toUpperCase();
        }

        if(projectList == null || projectList.isEmpty()) {
            addActionError("Please enter valid project names.");
            return Action.ERROR.toUpperCase();
        }

        if(azureBoardsBaseUrl.endsWith("/")) {
            azureBoardsBaseUrl = azureBoardsBaseUrl.substring(0, azureBoardsBaseUrl.length() - 1);
        }

        Bson combineUpdates = Updates.combine(
                Updates.set(AzureBoardsIntegration.BASE_URL, azureBoardsBaseUrl),
                Updates.set(AzureBoardsIntegration.ORGANIZATION, organization),
                Updates.set(AzureBoardsIntegration.PROJECT_LIST, projectList),
                Updates.setOnInsert(AzureBoardsIntegration.CREATED_TS, Context.now()),
                Updates.set(AzureBoardsIntegration.UPDATED_TS, Context.now())
        );

        String basicAuth = ":" + personalAuthToken;
        String base64PersonalAuthToken = Base64.getEncoder().encodeToString(basicAuth.getBytes());
        if(personalAuthToken != null && !personalAuthToken.isEmpty()) {
            Bson personalAuthTokenUpdate = Updates.set(AzureBoardsIntegration.PERSONAL_AUTH_TOKEN, base64PersonalAuthToken);
            combineUpdates = Updates.combine(combineUpdates, personalAuthTokenUpdate);
        } else {
            AzureBoardsIntegration boardsIntegration = AzureBoardsIntegrationDao.instance.findOne(new BasicDBObject());
            if(boardsIntegration == null || boardsIntegration.getPersonalAuthToken() == null || boardsIntegration.getPersonalAuthToken().isEmpty()) {
                addActionError("Please enter a valid personal auth token.");
                return Action.ERROR.toUpperCase();
            }
            base64PersonalAuthToken = AzureBoardsIntegrationDao.instance.findOne(new BasicDBObject()).getPersonalAuthToken();
        }

        Map<String, List<String>> projectToWorkItemsMap = new HashMap<>();
        for(String project : projectList) {
            boolean isSuccessful = fetchAzureBoardsWorkItems(base64PersonalAuthToken, project, projectToWorkItemsMap);
            if(!isSuccessful) {
                addActionError("An error occurred while fetching work item types for the project: " + project);
                return Action.ERROR.toUpperCase();
            }
        }

        if(projectToWorkItemsMap.isEmpty()) {
            addActionError("Something went wrong. Please verify your configurations and try again.");
            return Action.ERROR.toUpperCase();
        }

        Bson updateProjectToWorkItemsMap = Updates.set(AzureBoardsIntegration.PROJECT_TO_WORK_ITEMS_MAP, projectToWorkItemsMap);
        combineUpdates = Updates.combine(combineUpdates, updateProjectToWorkItemsMap);

        AzureBoardsIntegrationDao.instance.updateOne(
                new BasicDBObject(),
                combineUpdates
        );

        return Action.SUCCESS.toUpperCase();
    }

    private boolean fetchAzureBoardsWorkItems(String personalAuthToken, String projectName, Map<String, List<String>> projectToWorkItemsMap) {
        String url = azureBoardsBaseUrl + "/" + organization + "/" + projectName + "/_apis/wit/workitemtypes?api-version=" + version;

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + personalAuthToken));
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            logger.errorAndAddToDb("Status and Response from the getAzureBoardsWorkItems API: " + response.getStatusCode() + " | " + response.getBody());
            String responsePayload = response.getBody();
            BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
            BasicDBList workItemTypeListObj = (BasicDBList) (respPayloadObj.get("value"));

            List<String> workItemTypeList = new ArrayList<>();
            for(Object workItem : workItemTypeListObj) {
                BasicDBObject item = (BasicDBObject) workItem;
                String workItemType = item.get("name").toString();
                workItemTypeList.add(workItemType);
            }

            projectToWorkItemsMap.put(projectName, workItemTypeList);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String workItemType;
    private String projectName;
    private TestingIssuesId testingIssuesId;
    private String aktoDashboardHostName;
    public List<BasicDBObject> customABWorkItemFieldsPayload;
    
    // Fields for general work item creation (for threat events)
    private String threatEventId;
    private String templateId;  // filterId from threat policy
    private String title;
    private String description;
    private String endpoint;  // endpoint path for title formatting
    private String azureBoardsWorkItemUrl;
    private String originalMessage;  // Request/response message for attachment (for threat activity, contains orig with both request and response)

    public String createWorkItem() {
        AzureBoardsIntegration azureBoardsIntegration = AzureBoardsIntegrationDao.instance.findOne(new BasicDBObject());
        if(azureBoardsIntegration == null) {
            logger.errorAndAddToDb("Azure Boards Integration not found for account: " + Context.accountId.get(), LoggerMaker.LogDb.DASHBOARD);
            addActionError("Azure Boards Integration is not integrated.");
            return Action.ERROR.toUpperCase();
        }

        TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(Filters.and(
                Filters.in(TestingRunResult.TEST_SUB_TYPE, testingIssuesId.getTestSubCategory()),
                Filters.in(TestingRunResult.API_INFO_KEY, testingIssuesId.getApiInfoKey())
        ));

        logger.infoAndAddToDb("Found testingRunResult for: " + testingIssuesId.getTestSubCategory(), LoggerMaker.LogDb.DASHBOARD);

        Info testInfo = YamlTemplateDao.instance.findOne(
                Filters.in(Constants.ID, testingIssuesId.getTestSubCategory()),
                Projections.include(YamlTemplate.INFO+".description", YamlTemplate.INFO+".name")
        ).getInfo();

        logger.infoAndAddToDb("Found YamlTemplate info for: " + testInfo.getName(), LoggerMaker.LogDb.DASHBOARD);

        String testName = testInfo.getName();
        String testDescription = testInfo.getDescription();

        TestResult testResult = getTestResultFromTestingRunResult(testingRunResult);

        logger.infoAndAddToDb("TestResult size for the given test: " + testingRunResult.getTestResults().size(), LoggerMaker.LogDb.DASHBOARD);
        String attachmentUrl;
        if(testResult != null) {
            attachmentUrl = getAttachmentUrl(testResult.getOriginalMessage(), testResult.getMessage(), azureBoardsIntegration);
        } else {
            logger.errorAndAddToDb("TestResult obj not found.", LoggerMaker.LogDb.DASHBOARD);
            attachmentUrl = null;
        }
        logger.infoAndAddToDb("Attachment URL: " + attachmentUrl, LoggerMaker.LogDb.DASHBOARD);

        BasicDBList reqPayload = new BasicDBList();
        azureBoardsPayloadCreator(testingRunResult, testName, testDescription, attachmentUrl, customABWorkItemFieldsPayload, reqPayload);
        logger.infoAndAddToDb("Azure board payload: " + reqPayload.toString(), LoggerMaker.LogDb.DASHBOARD);

        try {
            String workItemUrl = createAndSendWorkItemRequest(azureBoardsIntegration, projectName, workItemType, reqPayload);
            if(workItemUrl == null) {
                return Action.ERROR.toUpperCase();
            }

            TestingRunIssuesDao.instance.updateOneNoUpsert(
                    Filters.eq(Constants.ID, testingIssuesId),
                    Updates.set(TestingRunIssues.AZURE_BOARDS_WORK_ITEM_URL, workItemUrl)
            );
        } catch (Exception e) {
            logger.errorAndAddToDb("Error while creating work item for azure boards: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
        }


        return Action.SUCCESS.toUpperCase();
    }

    private void azureBoardsPayloadCreator(TestingRunResult testingRunResult, String testName, String testDescription, String attachmentUrl, List<BasicDBObject> customABWorkItemFieldsPayload, BasicDBList reqPayload) {
        String method = testingRunResult.getApiInfoKey().getMethod().name();
        String fullUrl = testingRunResult.getApiInfoKey().getUrl();
        String endpointPath = getEndpointPath(fullUrl);
        String title = "Akto Report - " + testName + " (" + method + " - " + endpointPath + ")";

        addTitleField(reqPayload, title);

        // Format description with host, endpoint, and dashboard link
        try {
            URL url = new URL(fullUrl);
            String hostname = url.getHost();
            String endpoint = url.getPath();
            testDescription = "Host - " + hostname + "<br><br>Endpoint - " + endpoint + "<br><br><a target='_blank' href='"+aktoDashboardHostName+"/dashboard/issues?result="+testingRunResult.getId().toHexString()+"'>Issue link - Akto dashboard</a>"  + "<br><br>Description - " + testDescription;
        } catch (Exception e) {
            testDescription = "Host - " + fullUrl + "<br><br>Endpoint - " + endpointPath + "<br><br><a target='_blank' href='"+aktoDashboardHostName+"/dashboard/issues?result="+testingRunResult.getId().toHexString()+"'>Issue link - Akto dashboard</a>"  + "<br><br>Description - " + testDescription;
            e.printStackTrace();
        }

        addDescriptionField(reqPayload, testDescription);
        addAttachment(reqPayload, attachmentUrl);
        addCustomFields(reqPayload, customABWorkItemFieldsPayload);
    }

    private String getAttachmentUrl(String originalMessage, String message, AzureBoardsIntegration azureBoardsIntegration) {
        return getAttachmentUrl(originalMessage, message, azureBoardsIntegration, false);
    }

    private String getAttachmentUrl(String originalMessage, String message, AzureBoardsIntegration azureBoardsIntegration, boolean isThreatActivity) {
        File requestComparisonFile = createRequestFile(originalMessage, message, isThreatActivity);
        if (requestComparisonFile == null) {
            return null;
        }

        return uploadAttachmentFile(requestComparisonFile, azureBoardsIntegration);
    }

    /**
     * Uploads an attachment file to Azure DevOps and returns the attachment URL
     */
    private String uploadAttachmentFile(File attachmentFile, AzureBoardsIntegration azureBoardsIntegration) {
        if (attachmentFile == null) {
            return null;
        }

        try {
            String uploadUrl = azureBoardsIntegration.getBaseUrl() + "/" + azureBoardsIntegration.getOrganization() + "/" + projectName + "/_apis/wit/attachments?fileName=" + URLEncoder.encode(attachmentFile.getName(), "UTF-8") + "&api-version=" + version;

            byte[] fileBytes = Files.readAllBytes(attachmentFile.toPath());

            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Authorization", Collections.singletonList("Basic " + azureBoardsIntegration.getPersonalAuthToken()));
            headers.put("content-type", Collections.singletonList("application/octet-stream"));

            OriginalHttpRequest request = new OriginalHttpRequest(uploadUrl, "", "POST", new String(fileBytes, StandardCharsets.UTF_8), headers, "");
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            logger.errorAndAddToDb("Status and Response from the uploadAttachmentToAzureDevops API: " + response.getStatusCode() + " | " + response.getBody());

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                BasicDBObject responseObj = BasicDBObject.parse(response.getBody());
                return responseObj.getString("url");
            } else {
                System.err.println("Attachment upload failed with status code: " + response.getStatusCode());
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            attachmentFile.delete();
        }

        return null;
    }

    private String getEndpointPath(String fullUrl) {
        String endpointPath;

        try {
            URI uri = new URI(fullUrl);
            String path = uri.getPath();

            if (path.length() > 30) {
                endpointPath = path.substring(0, 15) + "..." + path.substring(path.length() - 15);
            } else {
                endpointPath = path;
            }

        } catch (Exception e) {
            endpointPath = fullUrl;
        }


        return endpointPath;
    }

    private List<TestingIssuesId> testingIssuesIdList;
    String errorMessage;
    public String bulkCreateAzureWorkItems() {
        int existingIssues = 0;
        List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(Filters.and(
                Filters.in(Constants.ID, testingIssuesIdList),
                Filters.exists(TestingRunIssues.AZURE_BOARDS_WORK_ITEM_URL, true)
        ));

        Set<TestingIssuesId> testingRunIssueIds = new HashSet<>();
        for (TestingRunIssues testingRunIssues : testingRunIssuesList) {
            testingRunIssueIds.add(testingRunIssues.getId());
        }

        for(TestingIssuesId testingIssuesId : testingIssuesIdList) {
            if(testingRunIssueIds.contains(testingIssuesId)) {
                existingIssues++;
                continue;
            }
            setTestingIssuesId(testingIssuesId);
            createWorkItem();
        }

        if(existingIssues == testingIssuesIdList.size()) {
            errorMessage = "All selected issues already have existing work items. No new work items were created.";
        } else if(existingIssues > 0) {
            errorMessage = "Azure board work items created for all selected issues, except for " + existingIssues + " issues that already have work items.";
        }

        return Action.SUCCESS.toUpperCase();
    }

    Map<String, Map<String, BasicDBList>> createWorkItemFieldMetaData;
    public String fetchCreateWorkItemFieldMetaData() {
        AzureBoardsIntegration azureBoardsIntegration = AzureBoardsIntegrationDao.instance.findOne(new BasicDBObject());
        if(azureBoardsIntegration == null) {
            addActionError("Azure Boards is not integrated.");
            return Action.ERROR.toUpperCase();
        }

        createWorkItemFieldMetaData = getAccountAzureBoardFields();

        return Action.SUCCESS.toUpperCase();
    }

    @Audit(description = "User removed Azure Boards integration", resource = Resource.AZURE_BOARDS_INTEGRATION, operation = Operation.DELETE)
    public String removeAzureBoardsIntegration() {
        AzureBoardsIntegrationDao.instance.deleteAll(new BasicDBObject());
        return Action.SUCCESS.toUpperCase();
    }

    /**
     * Creates a general Azure Boards work item (for threat events or other general use cases)
     * Similar to createGeneralJiraTicket
     */
    public String createGeneralAzureBoardsWorkItem() {
        AzureBoardsIntegration azureBoardsIntegration = AzureBoardsIntegrationDao.instance.findOne(new BasicDBObject());
        if(azureBoardsIntegration == null) {
            logger.errorAndAddToDb("Azure Boards Integration not found for account: " + Context.accountId.get(), LoggerMaker.LogDb.DASHBOARD);
            addActionError("Azure Boards Integration is not integrated.");
            return Action.ERROR.toUpperCase();
        }

        try {
            BasicDBList reqPayload = new BasicDBList();
            
            String workItemTitle = this.title;
            String workItemDescription = this.description;
            
            // Enrich with threat policy info if templateId is provided
            if (this.templateId != null && !this.templateId.isEmpty()) {
                ThreatPolicyInfo enriched = enrichWithThreatPolicyInfo(this.templateId, workItemTitle, workItemDescription, this.endpoint);
                workItemTitle = enriched.title;
                workItemDescription = enriched.description;
            }
            
            addTitleField(reqPayload, workItemTitle);
            addDescriptionField(reqPayload, workItemDescription);
            
            // Add attachment if request/response data is provided
            // For threat activity, originalMessage contains the orig field with both request and response
            String attachmentUrl = null;
            if (this.originalMessage != null && !this.originalMessage.isEmpty()) {
                // For threat activity: originalMessage contains everything, explicitly mark as threat activity
                attachmentUrl = getAttachmentUrl(this.originalMessage, this.originalMessage, azureBoardsIntegration, true);
                if (attachmentUrl != null) {
                    logger.infoAndAddToDb("Successfully created attachment for threat activity work item", LoggerMaker.LogDb.DASHBOARD);
                } else {
                    logger.errorAndAddToDb("Failed to create attachment for threat activity work item", LoggerMaker.LogDb.DASHBOARD);
                }
            }
            
            addAttachment(reqPayload, attachmentUrl);
            addCustomFields(reqPayload, customABWorkItemFieldsPayload);
            
            logger.infoAndAddToDb("Azure Boards work item payload (before sending): " + reqPayload.toString(), LoggerMaker.LogDb.DASHBOARD);

            String workItemUrl = createAndSendWorkItemRequest(azureBoardsIntegration, projectName, workItemType, reqPayload);
            if(workItemUrl == null) {
                addActionError("Failed to create Azure Boards work item");
                return Action.ERROR.toUpperCase();
            }

            this.azureBoardsWorkItemUrl = workItemUrl;

            // Update malicious event with Azure Boards work item URL if threatEventId is provided
            if(threatEventId != null && !threatEventId.isEmpty()) {
                // TODO: Add support for updating malicious event with Azure Boards work item URL
                // For now, we'll need to add this functionality similar to updateMaliciousEventJiraUrl
                // This requires backend changes to support azureBoardsWorkItemUrl in the threat detection service
                logger.infoAndAddToDb("Threat event ID provided: " + threatEventId + ", but Azure Boards URL update not yet implemented", LoggerMaker.LogDb.DASHBOARD);
            }

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            logger.errorAndAddToDb("Error creating general Azure Boards work item: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            addActionError("Error creating Azure Boards work item: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Helper class to hold enriched threat policy information
     */
    private static class ThreatPolicyInfo {
        String title;
        String description;
        
        ThreatPolicyInfo(String title, String description) {
            this.title = title;
            this.description = description;
        }
    }

    /**
     * Enriches title and description with threat policy information
     */
    private ThreatPolicyInfo enrichWithThreatPolicyInfo(String templateId, String originalTitle, String originalDescription, String endpoint) {
        try {
            YamlTemplate threatPolicyTemplate = FilterYamlTemplateDao.instance.findOne(
                Filters.eq(Constants.ID, templateId)
            );
            
            if (threatPolicyTemplate == null || threatPolicyTemplate.getContent() == null) {
                return new ThreatPolicyInfo(originalTitle, originalDescription);
            }
            
            FilterConfig filterConfig = FilterConfigYamlParser.parseTemplate(
                threatPolicyTemplate.getContent(), false
            );
            
            if (filterConfig == null || filterConfig.getInfo() == null) {
                return new ThreatPolicyInfo(originalTitle, originalDescription);
            }
            
            Info policyInfo = filterConfig.getInfo();
            String enrichedTitle = originalTitle;
            String enrichedDescription = originalDescription;
            
            // Format title as "Policy Name - Endpoint"
            if (policyInfo.getName() != null && !policyInfo.getName().isEmpty()) {
                if (endpoint != null && !endpoint.isEmpty()) {
                    enrichedTitle = policyInfo.getName() + " - " + endpoint;
                } else {
                    enrichedTitle = policyInfo.getName();
                }
            }
            
            // Build description: append Description, Details, Impact from threat policy
            StringBuilder descriptionBuilder = new StringBuilder();
            if (originalDescription != null && !originalDescription.isEmpty()) {
                descriptionBuilder.append(originalDescription).append("\n\n");
            }
            
            if (policyInfo.getDescription() != null && !policyInfo.getDescription().isEmpty()) {
                descriptionBuilder.append("Description:\n").append(policyInfo.getDescription()).append("\n\n");
            }
            
            if (policyInfo.getDetails() != null && !policyInfo.getDetails().isEmpty()) {
                descriptionBuilder.append("Details:\n").append(policyInfo.getDetails()).append("\n\n");
            }
            
            if (policyInfo.getImpact() != null && !policyInfo.getImpact().isEmpty()) {
                descriptionBuilder.append("Impact:\n").append(policyInfo.getImpact());
            }
            
            if (descriptionBuilder.length() > 0) {
                enrichedDescription = descriptionBuilder.toString();
            }
            
            logger.infoAndAddToDb("Using threat policy info for work item: " + templateId, LoggerMaker.LogDb.DASHBOARD);
            return new ThreatPolicyInfo(enrichedTitle, enrichedDescription);
            
        } catch (Exception e) {
            logger.errorAndAddToDb("Error fetching threat policy template: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            return new ThreatPolicyInfo(originalTitle, originalDescription);
        }
    }

    /**
     * Adds title field to the request payload
     */
    private void addTitleField(BasicDBList reqPayload, String title) {
        BasicDBObject titleDBObject = new BasicDBObject();
        titleDBObject.put("op", AzureBoardsOperations.ADD.name().toLowerCase());
        titleDBObject.put("path", "/fields/System.Title");
        titleDBObject.put("value", title);
        reqPayload.add(titleDBObject);
    }

    /**
     * Adds description field to the request payload with HTML formatting
     */
    private void addDescriptionField(BasicDBList reqPayload, String description) {
        String formattedDescription = description != null ? description.replace("\n", "<br>") : "";
        BasicDBObject descriptionDBObject = new BasicDBObject();
        descriptionDBObject.put("op", AzureBoardsOperations.ADD.name().toLowerCase());
        descriptionDBObject.put("path", "/fields/System.Description");
        descriptionDBObject.put("value", formattedDescription);
        reqPayload.add(descriptionDBObject);
    }

    /**
     * Adds attachment to the request payload if attachment URL is provided
     */
    private void addAttachment(BasicDBList reqPayload, String attachmentUrl) {
        if (attachmentUrl != null && !attachmentUrl.isEmpty()) {
            BasicDBObject attachmentsDBObject = new BasicDBObject();
            attachmentsDBObject.put("op", AzureBoardsOperations.ADD.name().toLowerCase());
            attachmentsDBObject.put("path", "/relations/-");
            BasicDBObject valueDBObject = new BasicDBObject();
            valueDBObject.put("rel", "AttachedFile");
            valueDBObject.put("url", attachmentUrl);
            valueDBObject.put("attributes", new BasicDBObject().put("comment", "Request and Response sample data."));
            attachmentsDBObject.put("value", valueDBObject);
            reqPayload.add(attachmentsDBObject);
            logger.infoAndAddToDb("Added attachment to payload with URL: " + attachmentUrl, LoggerMaker.LogDb.DASHBOARD);
        } else {
            logger.infoAndAddToDb("No attachment URL provided, skipping attachment", LoggerMaker.LogDb.DASHBOARD);
        }
    }

    /**
     * Adds custom fields to the request payload
     */
    private void addCustomFields(BasicDBList reqPayload, List<BasicDBObject> customFields) {
        if (customFields == null) {
            return;
        }
        
        for (BasicDBObject field : customFields) {
            try {
                String fieldReferenceName = field.getString("referenceName");
                String fieldValue = field.getString("value");
                String fieldType = field.getString("type");

                BasicDBObject customFieldDBObject = new BasicDBObject();
                customFieldDBObject.put("op", AzureBoardsOperations.ADD.name().toLowerCase());
                customFieldDBObject.put("path", "/fields/" + fieldReferenceName);

                switch (fieldType) {
                    case "integer":
                        customFieldDBObject.put("value", Integer.parseInt(fieldValue));
                        break;
                    case "double":
                        customFieldDBObject.put("value", Double.parseDouble(fieldValue));
                        break;
                    case "boolean":
                        customFieldDBObject.put("value", Boolean.parseBoolean(fieldValue));
                        break;
                    default:
                        customFieldDBObject.put("value", fieldValue);
                }

                reqPayload.add(customFieldDBObject);
            } catch (Exception e) {
                logger.errorAndAddToDb("Error processing custom field: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            }
        }
    }

    /**
     * Creates and sends work item request, returns work item URL or null on error
     */
    private String createAndSendWorkItemRequest(AzureBoardsIntegration azureBoardsIntegration, String projectName, String workItemType, BasicDBList reqPayload) {
        String url = azureBoardsIntegration.getBaseUrl() + "/" + azureBoardsIntegration.getOrganization() + "/" + projectName + "/_apis/wit/workitems/$" + workItemType + "?api-version=" + version;
        logger.infoAndAddToDb("Azure board final url: " + url, LoggerMaker.LogDb.DASHBOARD);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + azureBoardsIntegration.getPersonalAuthToken()));
        headers.put("content-type", Collections.singletonList("application/json-patch+json"));
        
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            logger.infoAndAddToDb("Status and Response from Azure Boards work item API: " + response.getStatusCode() + " | " + response.getBody());
            
            String responsePayload = response.getBody();
            if (response.getStatusCode() > 201 || responsePayload == null) {
                return null;
            }

            try {
                BasicDBObject respPayloadObj = BasicDBObject.parse(responsePayload);
                try {
                    Object linksObj = respPayloadObj.get("_links");
                    BasicDBObject links = BasicDBObject.parse(linksObj.toString());
                    Object htmlObj = links.get("html");
                    BasicDBObject html = BasicDBObject.parse(htmlObj.toString());
                    Object href = html.get("href");
                    return href.toString();
                } catch (Exception e) {
                    Object urlObj = respPayloadObj.get("url");
                    return urlObj != null ? urlObj.toString() : null;
                }
            } catch (Exception e) {
                logger.errorAndAddToDb("Error parsing work item response: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                return null;
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error sending work item request: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            return null;
        }
    }

    public String getAzureBoardsBaseUrl() {
        return azureBoardsBaseUrl;
    }

    public void setAzureBoardsBaseUrl(String azureBoardsBaseUrl) {
        this.azureBoardsBaseUrl = azureBoardsBaseUrl;
    }

    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public List<String> getProjectList() {
        return projectList;
    }

    public void setProjectList(List<String> projectList) {
        this.projectList = projectList;
    }

    public void setPersonalAuthToken(String personalAuthToken) {
        this.personalAuthToken = personalAuthToken;
    }

    public AzureBoardsIntegration getAzureBoardsIntegration() {
        return azureBoardsIntegration;
    }

    public void setAzureBoardsIntegration(AzureBoardsIntegration azureBoardsIntegration) {
        this.azureBoardsIntegration = azureBoardsIntegration;
    }

    public String getWorkItemType() {
        return workItemType;
    }

    public void setWorkItemType(String workItemType) {
        this.workItemType = workItemType;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public TestingIssuesId getTestingIssuesId() {
        return testingIssuesId;
    }

    public void setTestingIssuesId(TestingIssuesId testingIssuesId) {
        this.testingIssuesId = testingIssuesId;
    }

    public String getAktoDashboardHostName() {
        return aktoDashboardHostName;
    }

    public void setAktoDashboardHostName(String aktoDashboardHostName) {
        this.aktoDashboardHostName = aktoDashboardHostName;
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

    public Map<String, Map<String, BasicDBList>> getCreateWorkItemFieldMetaData() {
        return createWorkItemFieldMetaData;
    }

    public void setCreateWorkItemFieldMetaData(Map<String, Map<String, BasicDBList>> createWorkItemFieldMetaData) {
        this.createWorkItemFieldMetaData = createWorkItemFieldMetaData;
    }

    public List<BasicDBObject> getCustomABWorkItemFieldsPayload() {
        return customABWorkItemFieldsPayload;
    }

    public void setCustomABWorkItemFieldsPayload(List<BasicDBObject> customABWorkItemFieldsPayload) {
        this.customABWorkItemFieldsPayload = customABWorkItemFieldsPayload;
    }

    public String getThreatEventId() {
        return threatEventId;
    }

    public void setThreatEventId(String threatEventId) {
        this.threatEventId = threatEventId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAzureBoardsWorkItemUrl() {
        return azureBoardsWorkItemUrl;
    }

    public void setAzureBoardsWorkItemUrl(String azureBoardsWorkItemUrl) {
        this.azureBoardsWorkItemUrl = azureBoardsWorkItemUrl;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }

}