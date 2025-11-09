package com.akto.action;

import static com.akto.utils.Utils.createRequestFile;
import static com.akto.utils.Utils.getTestResultFromTestingRunResult;

import com.akto.dao.ConfigsDao;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.BidirectionalSyncSettingsDao;
import com.akto.dao.testing.RemediationsDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.Config.AktoHostUrlConfig;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.AccountSettings;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.jira_integration.*;
import com.akto.dto.jobs.*;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.BidirectionalSyncSettings;
import com.akto.dto.testing.Remediation;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.jobs.JobScheduler;
import com.akto.jobs.utils.JobConstants;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.Utils;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums;
import com.akto.util.enums.GlobalEnums.TicketSource;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import com.opensymphony.xwork2.Action;

import java.net.URI;
import java.util.function.Function;

import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import okhttp3.*;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.action.threat_detection.SuspectSampleDataAction;
import com.akto.dao.AccountSettingsDao;

import static com.akto.utils.jira.Utils.buildAdditionalIssueFieldsForJira;
import static com.akto.utils.jira.Utils.buildApiToken;
import static com.akto.utils.jira.Utils.buildBasicRequest;
import static com.akto.utils.jira.Utils.buildPayloadForJiraTicket;
import static com.akto.utils.jira.Utils.getAccountJiraFields;
import static com.akto.utils.jira.Utils.isLabelsFieldError;
import static com.akto.utils.jira.Utils.getJiraTicketUrlPair;
import static com.akto.utils.jira.Utils.handleError;
import static com.akto.utils.jira.Utils.retryWithoutGzipRequest;


public class JiraIntegrationAction extends UserAction implements ServletRequestAware {

    private String baseUrl;
    private String projId;
    private String userEmail;
    private String apiToken;
    private String issueType;
    private JiraIntegration jiraIntegration;
    private JiraMetaData jiraMetaData;

    private String jiraTicketKey;

    private String origReq;
    private String testReq;
    private String issueId;

    private String dashboardUrl;

    @Setter
    private String actionItemType;

    @Getter
    @Setter
    private String threatEventId;

    @Getter @Setter
    private String jiraTicketUrl;

    private Map<String,List<BasicDBObject>> projectAndIssueMap;
    private Map<String, ProjectMapping> projectMappings;

    private static final String META_ENDPOINT = "/rest/api/3/issue/createmeta";
    private static final String CREATE_ISSUE_ENDPOINT = "/rest/api/3/issue";
    private static final String CREATE_ISSUE_ENDPOINT_BULK = "/rest/api/3/issue/bulk";
    private static final String ATTACH_FILE_ENDPOINT = "/attachments";
    private static final String ISSUE_STATUS_ENDPOINT = "/rest/api/3/project/%s/statuses";

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutor.class, LogDb.DASHBOARD);

    private static final int TICKET_SYNC_JOB_RECURRING_INTERVAL_SECONDS = 3600;
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String REMEDIATION_INFO_URL = "tests-library-master/remediation/%s.md";

    @Setter
    private String title;
    @Setter
    private String description;
    @Setter
    private String labels;

    public String testIntegration() {

        String url = baseUrl + META_ENDPOINT;
        setApiToken(buildApiToken(apiToken));
        try {
            Request.Builder builder = buildBasicRequest(url, userEmail, apiToken, true);
            Request okHttpRequest = builder.build();
            Call call = client.newCall(okHttpRequest);
            Response response = null;
            String responsePayload = null;
            try {
                response = call.execute();
                responsePayload = response.body().string();
                if (responsePayload == null) {
                    addActionError("Error while testing jira integration, received null response");
                    loggerMaker.errorAndAddToDb("error while testing jira integration, received null response", LoggerMaker.LogDb.DASHBOARD);
                    return Action.ERROR.toUpperCase();
                }
                if (!Utils.isJsonPayload(responsePayload)) {
                    okHttpRequest = retryWithoutGzipRequest(builder, url);
                    call = client.newCall(okHttpRequest);
                    response = call.execute();
                    responsePayload = response.body().string();
                    if (responsePayload == null) {
                        addActionError("Error while testing jira integration, received null response");
                        loggerMaker.errorAndAddToDb("error while testing jira integration, received null response", LoggerMaker.LogDb.DASHBOARD);
                        return Action.ERROR.toUpperCase();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                addActionError("Error while testing jira integration, error making call\"");
                loggerMaker.errorAndAddToDb("error while testing jira integration, error making call" + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            } finally {
                if (response != null) {
                    response.close();
                }
            }
            BasicDBObject payloadObj;
            setProjId(projId.replaceAll("\\s+", ""));
            Set<String> inputProjectIds = new HashSet(Arrays.asList(this.projId.split(",")));
            this.projectAndIssueMap = new HashMap<>();
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList projects = (BasicDBList) payloadObj.get("projects");
                for (Object projObj: projects) {
                    BasicDBObject obj = (BasicDBObject) projObj;
                    String key = obj.getString("key");
                    if (!inputProjectIds.contains(key)) {
                        continue;
                    }
                    loggerMaker.debugAndAddToDb("evaluating issuetype for project key " + key + ", project json obj " + obj, LoggerMaker.LogDb.DASHBOARD);
                    BasicDBList issueTypes = (BasicDBList) obj.get("issuetypes");
                    List<BasicDBObject> issueIdPairs = getIssueTypesWithIds(issueTypes);
                    this.projectAndIssueMap.put(key, issueIdPairs);
                }
                if (this.projectAndIssueMap.isEmpty()) {
                    addActionError("Error while testing jira integration, unable to resolve issue type id");
                    loggerMaker.errorAndAddToDb("Error while testing jira integration, unable to resolve issue type id", LoggerMaker.LogDb.DASHBOARD);
                    return Action.ERROR.toUpperCase();
                }
            } catch(Exception e) {
                return Action.ERROR.toUpperCase();
            }
        } catch (Exception e) {
            addActionError("Error while testing jira integration");
            loggerMaker.errorAndAddToDb("error while testing jira integration, " + e, LoggerMaker.LogDb.DASHBOARD);
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchJiraStatusMappings() {
        setApiToken(buildApiToken(apiToken));
        try {
            setProjId(projId.replaceAll("\\s+", ""));

            // Step 2: Call the API to get issue types for the project
            String statusUrl = baseUrl + String.format(ISSUE_STATUS_ENDPOINT, projId) + "?maxResults=100";
            Request.Builder builder = buildBasicRequest(statusUrl, userEmail, apiToken, false);
            Request okHttpRequest = builder.build();

            Response response = null;
            String responsePayload;

            try {
                response = client.newCall(okHttpRequest).execute();

                if (!response.isSuccessful()) {
                    loggerMaker.error(
                        "Error while fetching Jira Project statuses. Response code: {}, accountId: {}, projectId: {}",
                        response.code(), Context.accountId.get(), projId);
                    return Action.ERROR.toUpperCase();
                }

                if (response.body() == null) {
                    loggerMaker.error(
                        "Error while fetching Jira Project statuses. Response body is null. accountId: {}, projectId: {}",
                        Context.accountId.get(), projId);
                    return Action.ERROR.toUpperCase();
                }

                responsePayload = response.body().string();

            } catch (Exception e) {
                String msg = "Error while fetching Jira Project statuses";
                addActionError(msg);
                loggerMaker.error(msg, e);
                return Action.ERROR.toUpperCase();
            } finally {
                if (response != null) {
                    response.close();
                }
            }

            this.projectMappings = new HashMap<>();
            this.projectMappings.put(projId, new ProjectMapping(
                extractUniqueStatusCategories(responsePayload),
                null
            ));
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            addActionError("Error while fetching jira project status mappings");
            loggerMaker.error("Error while fetching jira project status mappings. p[rojId: {}", projId, e);
            return Action.ERROR.toUpperCase();
        }
    }

    private Set<JiraStatus> extractUniqueStatusCategories(String responsePayload) {
        List<JiraStatusApiResponse> statusesMap = JsonUtils.fromJson(responsePayload,
            new TypeReference<List<JiraStatusApiResponse>>() {
            });

        if (statusesMap == null || statusesMap.isEmpty()) {
            return Collections.emptySet();
        }

        return statusesMap.stream()
            .flatMap(statusResp -> statusResp.getStatuses().stream())
            .collect(Collectors.toSet());
    }

    private List<BasicDBObject> getIssueTypesWithIds(BasicDBList issueTypes) {

        List<BasicDBObject> idPairs = new ArrayList<>();
        for (Object issueObj: issueTypes) {
            BasicDBObject obj2 = (BasicDBObject) issueObj;
            String issueName = obj2.getString("name");
            String issueId = obj2.getString("id");
            BasicDBObject finalObj = new BasicDBObject();
            finalObj.put("issueId", issueId);
            finalObj.put("issueType", issueName);
            idPairs.add(finalObj);
        }
        return idPairs;
    }

    public String addIntegration() {

        addAktoHostUrl();

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);
        Bson tokenUpdate = Updates.set("apiToken", apiToken);
        Bson integrationUpdate = Updates.combine(
            Updates.set("baseUrl", baseUrl),
            Updates.set("projId", projId),
            Updates.set("userEmail", userEmail),
            Updates.set("issueType", issueType),
            Updates.setOnInsert("createdTs", Context.now()),
            Updates.set("updatedTs", Context.now()),
            Updates.set("projectIdsMap", projectAndIssueMap),
            Updates.set("projectMappings", projectMappings)
        );
        if(!apiToken.contains("******")){
            integrationUpdate = Updates.combine(integrationUpdate, tokenUpdate);
        }

        JiraIntegrationDao.instance.getMCollection().updateOne(
                new BasicDBObject(),
                integrationUpdate,
                updateOptions
        );

        return Action.SUCCESS.toUpperCase();
    }

    public String addIntegrationV2() {

        addAktoHostUrl();

        if (projectMappings == null || projectMappings.isEmpty()) {
            addActionError("Project mappings cannot be empty");
            return Action.ERROR.toUpperCase();
        }

        this.projectAndIssueMap = new HashMap<>();
        try {
            for (Map.Entry<String, ProjectMapping> entry : projectMappings.entrySet()) {
                List<BasicDBObject> issueTypes = getProjectMetadata(entry.getKey());
                this.projectAndIssueMap.put(entry.getKey(), issueTypes);
            }
        } catch (Exception ex) {
            loggerMaker.error("Error while fetching project metadata", ex);
            addActionError("Error while fetching project metadata");
            return Action.ERROR.toUpperCase();
        }

        JiraIntegration existingIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());

        if (existingIntegration == null) {
            String response = addIntegration();
            this.jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
            syncBidirectionalSettings(jiraIntegration);
            return response;
        }

        Map<String, ProjectMapping> existingProjectMappings = existingIntegration.getProjectMappings();

        if (existingProjectMappings == null) {
            existingProjectMappings = new HashMap<>();
        }

        for (Map.Entry<String, ProjectMapping> entry : projectMappings.entrySet()) {
            ProjectMapping mapping = entry.getValue();
            if (!mapping.getBiDirectionalSyncSettings().isEnabled()) {
                mapping.setStatuses(null);
            }
        }

        existingProjectMappings.putAll(projectMappings);

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(false);

        Bson integrationUpdate = Updates.combine(
            Updates.set("updatedTs", Context.now()),
            Updates.set("projectMappings", existingProjectMappings)
        );

        Map<String, List<BasicDBObject>> existingProjectIdMap = existingIntegration.getProjectIdsMap();

        if (existingProjectIdMap == null) {
            existingProjectIdMap = new HashMap<>();
        }

        existingProjectIdMap.putAll(this.projectAndIssueMap);

        integrationUpdate = Updates.combine(integrationUpdate, Updates.set("projectIdsMap", existingProjectIdMap));

        JiraIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            integrationUpdate,
            updateOptions
        );

        this.jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());

        syncBidirectionalSettings(jiraIntegration);

        return Action.SUCCESS.toUpperCase();
    }

    public String deleteProject() {
        if (this.projId == null || this.projId.isEmpty()) {
            addActionError("Project Id cannot be null");
            return Action.ERROR.toUpperCase();
        }

        JiraIntegration jira = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if (jira == null) {
            addActionError("Jira Integration not found. AccountId: " + Context.accountId.get());
            return Action.ERROR.toUpperCase();
        }

        Map<String, List<BasicDBObject>> existingProjectIdsMap = jira.getProjectIdsMap();
        Map<String, ProjectMapping> existingProjectMappings = jira.getProjectMappings();

        if (existingProjectIdsMap.isEmpty() || (existingProjectMappings != null && existingProjectMappings.isEmpty())) {
            addActionError("Atleast one project is required for Jira Integration");
            return Action.ERROR.toUpperCase();
        }

        List<BasicDBObject> removedProjectIdMap = existingProjectIdsMap.remove(this.projId);

        // null check for backward compatibility - users who already have jira integration before this change.
        ProjectMapping removedProjectMapping = existingProjectMappings == null ? null : existingProjectMappings.remove(this.projId);

        if (removedProjectIdMap == null && removedProjectMapping == null) {
            loggerMaker.debug("Project Key not found in Jira Integration: projId: {}", this.projId);
            return Action.SUCCESS.toUpperCase();
        }

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(false);

        Bson integrationUpdate = Updates.combine(
            Updates.set("updatedTs", Context.now()),
            Updates.set("projectIdsMap", existingProjectIdsMap)
        );

        if (existingProjectMappings != null) {
            integrationUpdate = Updates.combine(integrationUpdate, Updates.set("projectMappings", existingProjectMappings));
        }

        JiraIntegrationDao.instance.getMCollection().updateOne(
            new BasicDBObject(),
            integrationUpdate,
            updateOptions
        );

        BidirectionalSyncSettings disabledSettings = BidirectionalSyncSettingsDao.instance.getMCollection()
            .findOneAndUpdate(
                Filters.and(
                    Filters.eq(BidirectionalSyncSettings.SOURCE, TicketSource.JIRA.name()),
                    Filters.eq(BidirectionalSyncSettings.PROJECT_KEY, this.projId)
                ),
                Updates.combine(
                    Updates.set(BidirectionalSyncSettings.ACTIVE, false),
                    Updates.set(BidirectionalSyncSettings.LAST_SYNCED_AT, Context.now())
                ),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
            );
        if (disabledSettings != null) {
            JobScheduler.deleteJob(disabledSettings.getJobId());
        }
        return Action.SUCCESS.toUpperCase();
    }

    private List<BasicDBObject> getProjectMetadata(String projectId) throws Exception {

        setApiToken(buildApiToken(apiToken));
        if(apiToken == null){
            throw new IllegalStateException("No jira integration found. Please integrate Jira first.");
        }
        String url = baseUrl + META_ENDPOINT;
        Request.Builder builder = buildBasicRequest(url, userEmail, apiToken, true);
        Request okHttpRequest = builder.build();

        Response response = null;
        String responsePayload;

        try {
            response = client.newCall(okHttpRequest).execute();
            responsePayload = response.body().string();

            if (!Utils.isJsonPayload(responsePayload)) {
                okHttpRequest = retryWithoutGzipRequest(builder, url);
                response = client.newCall(okHttpRequest).execute();
                responsePayload = response.body().string();
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }

        BasicDBObject payloadObj = BasicDBObject.parse(responsePayload);
        BasicDBList projects = (BasicDBList) payloadObj.get("projects");

        for (Object projObj : projects) {
            BasicDBObject obj = (BasicDBObject) projObj;
            String key = obj.getString("key");

            if (projectId.equals(key)) {
                BasicDBList issueTypes = (BasicDBList) obj.get("issuetypes");
                return getIssueTypesWithIds(issueTypes);
            }
        }

        throw new IllegalArgumentException("Project with ID '" + projectId + "' not found");
    }

    public String fetchIntegration() {

        addAktoHostUrl();

        jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if(jiraIntegration != null){
            jiraIntegration.setApiToken("****************************");
        }
        return Action.SUCCESS.toUpperCase();
    }

    Map<String, Map<String, BasicDBList>> createIssueFieldMetaData;
    public String fetchCreateIssueFieldMetaData() {

        JiraIntegration jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if (jiraIntegration == null) {
            addActionError("Jira is not integrated.");
            return ERROR.toUpperCase();
        }

        createIssueFieldMetaData = getAccountJiraFields();

        return Action.SUCCESS.toUpperCase();
    }

    public String createIssue() {

        BasicDBObject reqPayload = new BasicDBObject();
        jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if(jiraIntegration == null) {
            addActionError("Jira is not integrated.");
            return ERROR.toUpperCase();
        }

        TestingRunIssues testingRunIssues = TestingRunIssuesDao.instance.findOne(
            Filters.eq(Constants.ID, jiraMetaData.getTestingIssueId()));
        YamlTemplate yamlTemplate = YamlTemplateDao.instance.findOne(
            Filters.eq(Constants.ID, jiraMetaData.getTestingIssueId().getTestSubCategory()),
            Projections.include(YamlTemplate.INFO));
        Remediation remediation = RemediationsDao.instance.findOne(
            Filters.eq(Constants.ID,
                getRemediationId(jiraMetaData.getTestingIssueId().getTestSubCategory())));
        BasicDBObject fields = jiraTicketPayloadCreator(jiraMetaData, testingRunIssues, yamlTemplate.getInfo(),
            remediation);

        reqPayload.put("fields", fields);

        String url = jiraIntegration.getBaseUrl() + CREATE_ISSUE_ENDPOINT;
        String authHeader = Base64.getEncoder().encodeToString((jiraIntegration.getUserEmail() + ":" + jiraIntegration.getApiToken()).getBytes());

        String jiraTicketUrl = "";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + authHeader));
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responsePayload = response.getBody();
            if (response.getStatusCode() > 201 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("error while creating jira issue, url not accessible, requestbody " + request.getBody() + " ,responsebody " + response.getBody() + " ,responsestatus " + response.getStatusCode(), LoggerMaker.LogDb.DASHBOARD);
                
                // Check if it's a labels field error and retry without labels
                if (isLabelsFieldError(responsePayload)) {
                    loggerMaker.info("Labels field error detected, retrying without labels field", LoggerMaker.LogDb.DASHBOARD);
                    fields.remove("labels");
                    reqPayload.put("fields", fields);
                    
                    // Create new request without labels
                    OriginalHttpRequest retryRequest = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");
                    response = ApiExecutor.sendRequest(retryRequest, true, null, false, new ArrayList<>());
                    responsePayload = response.getBody();
                    
                    if (response.getStatusCode() > 201 || responsePayload == null) {
                        loggerMaker.errorAndAddToDb("error while creating jira issue after retry without labels, requestbody " + retryRequest.getBody() + " ,responsebody " + response.getBody() + " ,responsestatus " + response.getStatusCode(), LoggerMaker.LogDb.DASHBOARD);
                        String error = handleError(responsePayload);
                        if (error != null) {
                            addActionError("Error while creating jira issue: " + error);
                        }
                        return Action.ERROR.toUpperCase();
                    }
                } else {
                    String error = handleError(responsePayload);
                    if (error != null) {
                        addActionError("Error while creating jira issue: " + error);
                    }
                    return Action.ERROR.toUpperCase();
                }
            }
            try {
                Pair<String, String> pair = getJiraTicketUrlPair(responsePayload, jiraIntegration.getBaseUrl());
                this.jiraTicketKey = pair.getSecond();
                jiraTicketUrl = pair.getFirst();
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb(e, "error making jira issue url " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }
        } catch(Exception e) {
            return Action.ERROR.toUpperCase();
        }

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(false);

        if(jiraTicketUrl.length() > 0){
            TestingRunIssuesDao.instance.getMCollection().updateOne(
                Filters.eq(Constants.ID, jiraMetaData.getTestingIssueId()),
                Updates.combine(
                    Updates.set("jiraIssueUrl", jiraTicketUrl),
                    Updates.set(TestingRunIssues.TICKET_SOURCE, GlobalEnums.TicketSource.JIRA.name()),
                    Updates.set(TestingRunIssues.TICKET_PROJECT_KEY, projId),
                    Updates.set(TestingRunIssues.TICKET_ID, this.jiraTicketKey),
                    Updates.set(TestingRunIssues.TICKET_LAST_UPDATED_AT, Context.now())
                ),
                updateOptions
            );
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String attachFileToIssue() {

        try {
            jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
            String url = jiraIntegration.getBaseUrl() + CREATE_ISSUE_ENDPOINT + "/" + issueId + ATTACH_FILE_ENDPOINT;
            File tmpOutputFile = createRequestFile(origReq, testReq);
            if(tmpOutputFile == null) {
                return Action.SUCCESS.toUpperCase();
            }

            MediaType mType = MediaType.parse("application/octet-stream");
            RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", tmpOutputFile.getName(),
                            RequestBody.create(tmpOutputFile, mType))
                    .build();
            Request.Builder builder = buildBasicRequest(url, jiraIntegration.getUserEmail(), jiraIntegration.getApiToken(), false);

            builder.removeHeader("Accept");
            builder.addHeader("X-Atlassian-Token", "no-check");
            Request request = builder.post(requestBody).build();
            Response response = null;

            try {
                response = client.newCall(request).execute();
            } catch (Exception ex) {
                loggerMaker.errorAndAddToDb(ex,
                        String.format("Failed to call jira from url %s. Error %s", url, ex.getMessage()),
                        LogDb.DASHBOARD);
            } finally {
                if (response != null) {
                    response.close();
                }
            }

        } catch (Exception ex) {
                ex.printStackTrace();
        }


        return Action.SUCCESS.toUpperCase();
    }

    private BasicDBObject buildContentDetails(String txt, String link) {
        BasicDBObject details = new BasicDBObject();
        details.put("type", "paragraph");
        BasicDBList contentInnerList = new BasicDBList();
        BasicDBObject innerDetails = new BasicDBObject();
        innerDetails.put("text", txt);
        innerDetails.put("type", "text");

        if (link != null) {
            BasicDBList marksList = new BasicDBList();
            BasicDBObject marks = new BasicDBObject();
            marks.put("type", "link");
            BasicDBObject attrs = new BasicDBObject();
            attrs.put("href", link);
            marks.put("attrs", attrs);
            marksList.add(marks);
            innerDetails.put("marks", marksList);
        }

        contentInnerList.add(innerDetails);
        details.put("content", contentInnerList);


        return details;
    }

    String aktoDashboardHost;
    List<TestingIssuesId> issuesIds;
    private String errorMessage;

    public String bulkCreateJiraTickets (){
        if(issuesIds == null || issuesIds.isEmpty()){
            addActionError("Cannot create an empty jira issue.");
            return ERROR.toUpperCase();
        }

        if((projId == null || projId.isEmpty()) || (issueType == null || issueType.isEmpty())){
            addActionError("Project ID or Issue Type cannot be empty.");
            return ERROR.toUpperCase();
        }

        jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if(jiraIntegration == null) {
            addActionError("Jira is not integrated.");
            return ERROR.toUpperCase();
        }

        List<JiraMetaData> jiraMetaDataList = new ArrayList<>();
        Bson projection = Projections.include(YamlTemplate.INFO);
        List<String> testingSubCategories = new ArrayList<>();
        for(TestingIssuesId testingIssuesId : issuesIds) {
            testingSubCategories.add(testingIssuesId.getTestSubCategory());
        }
        List<YamlTemplate> yamlTemplateList = YamlTemplateDao.instance.findAll(Filters.in("_id", testingSubCategories), projection);
        Map<String, Info> testSubTypeToInfoMap = new HashMap<>();
        for(YamlTemplate yamlTemplate : yamlTemplateList) {
            if(yamlTemplate == null || yamlTemplate.getInfo() == null) {
                loggerMaker.errorAndAddToDb("ERROR: YamlTemplate or YamlTemplate.info is null", LogDb.DASHBOARD);
                continue;
            }
            Info info = yamlTemplate.getInfo();
            testSubTypeToInfoMap.put(info.getSubCategory(), info);
        }

        List<TestingRunResult> testingRunResultList = new ArrayList<>();
        int existingIssues = 0;
        List<TestingRunIssues> testingRunIssuesList = TestingRunIssuesDao.instance.findAll(
            Filters.in("_id", issuesIds),
            Projections.include(Constants.ID, TestingRunIssues.JIRA_ISSUE_URL,
                TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestingRunIssues.LAST_UPDATED, TestingRunIssues.CREATION_TIME,
                TestingRunIssues.KEY_SEVERITY, TestingRunIssues.IGNORE_REASON)
        );

        Map<TestingIssuesId, TestingRunIssues> issuesEligibleForJiraMap = testingRunIssuesList.stream()
            .filter(issue -> StringUtils.isBlank(issue.getJiraIssueUrl()))
            .collect(Collectors.toMap(TestingRunIssues::getId, Function.identity()));

        testingRunIssuesList = testingRunIssuesList.stream().filter(
            issue -> StringUtils.isNotBlank(issue.getJiraIssueUrl())
        ).collect(Collectors.toList());

        Set<TestingIssuesId> testingRunIssueIds = new HashSet<>();
        for (TestingRunIssues testingRunIssues : testingRunIssuesList) {
            testingRunIssueIds.add(testingRunIssues.getId());
        }

        for(TestingIssuesId testingIssuesId : issuesIds) {
            if(testingRunIssueIds.contains(testingIssuesId)) {
                existingIssues++;
                continue;
            }

            Info info = testSubTypeToInfoMap.get(testingIssuesId.getTestSubCategory());

            TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(Filters.and(
                    Filters.in(TestingRunResult.TEST_SUB_TYPE, testingIssuesId.getTestSubCategory()),
                    Filters.in(TestingRunResult.API_INFO_KEY, testingIssuesId.getApiInfoKey())
            ));

            if(testingRunResult == null) {
                loggerMaker.errorAndAddToDb("Error: Testing Run Result not found", LogDb.DASHBOARD);
                continue;
            }

            testingRunResultList.add(testingRunResult);

            JiraMetaData jiraMetaData;
            try {
                String inputUrl = testingIssuesId.getApiInfoKey().getUrl();

                URI url = new URI(inputUrl);
                String hostname = url.getHost();
                String endpoint = url.getPath();

                Map<String, Object> additionalFields = null;
                String labels = null;
                if (this.jiraMetaData != null) {
                    if (this.jiraMetaData.getAdditionalIssueFields() != null) {
                        additionalFields = this.jiraMetaData.getAdditionalIssueFields();
                    }
                    if (this.jiraMetaData.getLabels() != null) {
                        labels = this.jiraMetaData.getLabels();
                    }
                }

                jiraMetaData = new JiraMetaData(
                        info.getName(),
                        "Host - "+hostname,
                        endpoint,
                        aktoDashboardHost+"/dashboard/issues?result="+testingRunResult.getId().toHexString(),
                        info.getDescription(),
                        testingIssuesId,
                        null,
                        additionalFields,
                        labels
                );

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while parsing the url: " + e.getMessage(), LogDb.DASHBOARD);
                continue;
            }

            jiraMetaDataList.add(jiraMetaData);
        }

        BasicDBObject reqPayload = new BasicDBObject();
        BasicDBList issueUpdates = new BasicDBList();

        if(existingIssues == issuesIds.size()) {
            errorMessage = "All selected issues already have existing Jira tickets. No new tickets were created.";
        } else if(existingIssues > 0) {
            errorMessage = "Jira tickets created for all selected issues, except for " + existingIssues + " issues that already have tickets.";
        }

        if(jiraMetaDataList.isEmpty()) {
            return Action.SUCCESS.toUpperCase();
        }

        Map<String, Remediation> remediationMap = getRemediationMap(testingSubCategories);

        for (JiraMetaData jiraMetaData : jiraMetaDataList) {
            TestingIssuesId issuesId = jiraMetaData.getTestingIssueId();
            BasicDBObject fields = jiraTicketPayloadCreator(jiraMetaData,
                issuesEligibleForJiraMap.get(issuesId),
                testSubTypeToInfoMap.get(issuesId.getTestSubCategory()),
                remediationMap.get(getRemediationId(issuesId.getTestSubCategory())));

            // Prepare the issue object
            BasicDBObject issueObject = new BasicDBObject();
            issueObject.put("fields", fields);
            issueUpdates.add(issueObject);
        }

        // Prepare the full request payload
        reqPayload.put("issueUpdates", issueUpdates);

        // URL for bulk create issues
        String url = jiraIntegration.getBaseUrl() + CREATE_ISSUE_ENDPOINT_BULK;
        String authHeader = Base64.getEncoder().encodeToString((jiraIntegration.getUserEmail() + ":" + jiraIntegration.getApiToken()).getBytes());

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + authHeader));

        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");

        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responsePayload = response.getBody();

            if (response.getStatusCode() > 201 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("Error while creating Jira issues in bulk, URL not accessible, request body "
                                + request.getBody() + " ,response body " + response.getBody() + " ,response status " + response.getStatusCode(),
                        LoggerMaker.LogDb.DASHBOARD);

                // Check if it's a labels field error and retry without labels
                if (isLabelsFieldError(responsePayload)) {
                    loggerMaker.infoAndAddToDb("Labels field error detected in bulk request, retrying without labels field", LoggerMaker.LogDb.DASHBOARD);
                    for (Object issueUpdateObj : issueUpdates) {
                        BasicDBObject issueUpdate = (BasicDBObject) issueUpdateObj;
                        BasicDBObject fields = (BasicDBObject) issueUpdate.get("fields");
                        if (fields != null) {
                            fields.remove("labels");
                        }
                    }
                    
                    // Create new request without labels
                    OriginalHttpRequest retryRequest = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");
                    response = ApiExecutor.sendRequest(retryRequest, true, null, false, new ArrayList<>());
                    responsePayload = response.getBody();
                    
                    if (response.getStatusCode() > 201 || responsePayload == null) {
                        loggerMaker.errorAndAddToDb("Error while creating Jira issues in bulk after retry without labels, request body "
                                        + retryRequest.getBody() + " ,response body " + response.getBody() + " ,response status " + response.getStatusCode(),
                                LoggerMaker.LogDb.DASHBOARD);
                        String error = handleError(responsePayload);
                        if (error != null) {
                            addActionError("Error while creating Jira issues in bulk: " + error);
                        }
                        return Action.ERROR.toUpperCase();
                    }
                } else {
                    String error = handleError(responsePayload);
                    if (error != null) {
                        addActionError("Error while creating Jira issues in bulk: " + error);
                    }
                    return Action.ERROR.toUpperCase();
                }
            }

            BasicDBObject payloadObj;
            try {
                payloadObj = BasicDBObject.parse(responsePayload);
                List<BasicDBObject> issues = (List<BasicDBObject>) payloadObj.get("issues");
                for (int i = 0; i < Math.min(issues.size(), jiraMetaDataList.size()); i++) {
                    BasicDBObject issue = issues.get(i);
                    String issueKey = issue.getString("key");
                    String jiraTicketUrl = jiraIntegration.getBaseUrl() + "/browse/" + issueKey;

                    JiraMetaData metaData = jiraMetaDataList.get(i);
                    TestingRunIssuesDao.instance.getMCollection().updateOne(
                            Filters.eq(Constants.ID, metaData.getTestingIssueId()),
                            Updates.combine(
                                Updates.set("jiraIssueUrl", jiraTicketUrl),
                                Updates.set(TestingRunIssues.TICKET_SOURCE, GlobalEnums.TicketSource.JIRA.name()),
                                Updates.set(TestingRunIssues.TICKET_PROJECT_KEY, projId),
                                Updates.set(TestingRunIssues.TICKET_ID, issueKey),
                                Updates.set(TestingRunIssues.TICKET_LAST_UPDATED_AT, Context.now())
                            ),
                            new UpdateOptions().upsert(false)
                    );
                }

                for (int i = 0; i < issues.size(); i++) {
                    BasicDBObject issue = issues.get(i);
                    TestingRunResult testingRunResult = testingRunResultList.get(i);
                    String issueKey = issue.getString("key");
                    TestResult testResult = getTestResultFromTestingRunResult(testingRunResult);

                    setIssueId(issueKey);
                    if(testResult != null) {
                        setOrigReq(testResult.getOriginalMessage());
                        setTestReq(testResult.getMessage());
                    } else {
                        loggerMaker.errorAndAddToDb("TestResult obj not found.", LoggerMaker.LogDb.DASHBOARD);
                    }
                    String status = attachFileToIssue();
                    if (status.equals(ERROR.toUpperCase())) {
                        return ERROR.toUpperCase();
                    }
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error processing Jira bulk issue response " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error making Jira bulk create request: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }

    private BasicDBObject jiraTicketPayloadCreator(JiraMetaData jiraMetaData, TestingRunIssues issue,
        Info info, Remediation remediation) {
        String endpoint = jiraMetaData.getEndPointStr().replace("Endpoint - ", "");
        String truncatedEndpoint = endpoint;
        if(endpoint.length() > 30) {
            truncatedEndpoint = endpoint.substring(0, 15) + "..." + endpoint.substring(endpoint.length() - 15);
        }

        String endpointMethod = jiraMetaData.getTestingIssueId().getApiInfoKey().getMethod().name();

        // issue title
        String summary = "Akto Report - " + jiraMetaData.getIssueTitle() + " (" + endpointMethod + " - " + truncatedEndpoint + ")";

        BasicDBList contentList = new BasicDBList();
        contentList.add(buildContentDetails(jiraMetaData.getHostStr(), null));
        contentList.add(buildContentDetails(jiraMetaData.getEndPointStr(), null));
        contentList.add(buildContentDetails("Issue link - Akto dashboard", jiraMetaData.getIssueUrl()));
        contentList.add(buildContentDetails(jiraMetaData.getIssueDescription(), null));

        List<BasicDBObject> additionalFields = buildAdditionalIssueFieldsForJira(info, issue, remediation);
        if (!CollectionUtils.isEmpty(additionalFields)) {
            contentList.addAll(additionalFields);
        }

        BasicDBObject fields = buildPayloadForJiraTicket(summary, this.projId, this.issueType, contentList,jiraMetaData.getAdditionalIssueFields());
        
        // Combine user labels with AKTO_SYNC label
        List<String> labelsList = new ArrayList<>();
        labelsList.add(JobConstants.TICKET_LABEL_AKTO_SYNC);
        
        // Parse and add user-provided labels
        if (jiraMetaData.getLabels() != null && !jiraMetaData.getLabels().trim().isEmpty()) {
            String[] userLabels = jiraMetaData.getLabels().split(",");
            for (String label : userLabels) {
                String trimmedLabel = label.trim();
                if (!trimmedLabel.isEmpty() && !labelsList.contains(trimmedLabel)) {
                    labelsList.add(trimmedLabel);
                }
            }
        }
        
        fields.put("labels", labelsList.toArray(new String[0]));
        return fields;
    }

    private Map<String, Remediation> getRemediationMap(List<String> testingSubCategories) {
        List<Remediation> remediationList = RemediationsDao.instance.findAll(
            Filters.in(Constants.ID,
                testingSubCategories.stream().map(this::getRemediationId)
                    .collect(Collectors.toList())));

        return remediationList.stream()
            .collect(Collectors.toMap(
                Remediation::getid,
                Function.identity()
            ));
    }

    private String getRemediationId(String testSubCategory) {
        return String.format(REMEDIATION_INFO_URL, testSubCategory);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getProjId() {
        return projId;
    }

    public void setProjId(String projId) {
        this.projId = projId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public JiraIntegration getJiraIntegration() {
        return jiraIntegration;
    }

    public void setJiraIntegration(JiraIntegration jiraIntegration) {
        this.jiraIntegration = jiraIntegration;
    }

    public String getOrigReq() {
        return origReq;
    }

    public void setOrigReq(String origReq) {
        this.origReq = origReq;
    }

    public String getTestReq() {
        return testReq;
    }

    public void setTestReq(String testReq) {
        this.testReq = testReq;
    }

    public String getIssueId() {
        return issueId;
    }

    public void setIssueId(String issueId) {
        this.issueId = issueId;
    }

    public Map<String, List<BasicDBObject>> getProjectAndIssueMap() {
        return projectAndIssueMap;
    }

    public void setProjectAndIssueMap(Map<String, List<BasicDBObject>> projectAndIssueMap) {
        this.projectAndIssueMap = projectAndIssueMap;
    }

    public JiraMetaData getJiraMetaData() {
        return jiraMetaData;
    }

    public void setJiraMetaData(JiraMetaData jiraMetaData) {
        this.jiraMetaData = jiraMetaData;
    }

    public String getJiraTicketKey() {
        return jiraTicketKey;
    }

    public void setJiraTicketKey(String jiraTicketKey) {
        this.jiraTicketKey = jiraTicketKey;
    }

    public void setIssuesIds(List<TestingIssuesId> issuesIds) {
        this.issuesIds = issuesIds;
    }

    public void setAktoDashboardHost(String aktoDashboardHost) {
        this.aktoDashboardHost = aktoDashboardHost;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Map<String, ProjectMapping> getProjectMappings() {
        return projectMappings;
    }

    public void setProjectMappings(Map<String, ProjectMapping> projectMappings) {
        this.projectMappings = projectMappings;
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.dashboardUrl = com.akto.utils.Utils.createDashboardUrlFromRequest(request);
    }

    public Map<String, Map<String, BasicDBList>> getCreateIssueFieldMetaData() {
        return createIssueFieldMetaData;
    }

    public void setCreateIssueFieldMetaData(Map<String, Map<String, BasicDBList>> createIssueFieldMetaData) {
        this.createIssueFieldMetaData = createIssueFieldMetaData;
    }

    private void addAktoHostUrl() {
        if (DashboardMode.isOnPremDeployment()) {

            AktoHostUrlConfig existingConfig = (AktoHostUrlConfig) ConfigsDao.instance.findOne(
                Filters.eq(Constants.ID, ConfigType.AKTO_DASHBOARD_HOST_URL.name()));

            int now = Context.now();

            if (existingConfig == null || (now - existingConfig.getLastSyncedAt()) > 3600) {
                AktoHostUrlConfig config = new AktoHostUrlConfig();
                config.setHostUrl(this.dashboardUrl);
                config.setLastSyncedAt(now);
                ConfigsDao.instance.updateOne(Filters.eq(Constants.ID, ConfigType.AKTO_DASHBOARD_HOST_URL.name()),
                    new Document("$set", config)
                );
            }
        }
    }

    private void syncBidirectionalSettings(JiraIntegration jiraIntegration) {
        try {

            Map<String, ProjectMapping> projectMappings = jiraIntegration.getProjectMappings();

            if (projectMappings == null || projectMappings.isEmpty()) {
                loggerMaker.info("No project mappings found in Jira integration");
                return;
            }

            int syncedCount = 0;

            for (Map.Entry<String, ProjectMapping> entry : projectMappings.entrySet()) {
                String projectKey = entry.getKey();
                ProjectMapping projectMapping = entry.getValue();

                if (projectMapping == null) {
                    continue;
                }

                BiDirectionalSyncSettings biDirectionalSettings = projectMapping.getBiDirectionalSyncSettings();

                // Check if entry already exists
                BidirectionalSyncSettings existingSettings = BidirectionalSyncSettingsDao.instance.findOne(
                    Filters.and(
                        Filters.eq(BidirectionalSyncSettings.SOURCE, TicketSource.JIRA.name()),
                        Filters.eq(BidirectionalSyncSettings.PROJECT_KEY, projectKey)
                    )
                );

                boolean isEnabled = biDirectionalSettings != null && biDirectionalSettings.isEnabled();

                if (isEnabled) {
                    if (existingSettings != null) {
                        // Update existing entry
                        // Check if we need to restart the job (if previously inactive)
                        ObjectId jobId = existingSettings.getJobId();
                        if (!existingSettings.isActive()) {
                            Job job = JobScheduler.restartJob(jobId);
                            if (job == null) {
                                loggerMaker.error("Error while restarting recurring job for project key: {}", projectKey);
                                continue;
                            }
                            loggerMaker.info("Restarted recurring job for project key: {}", projectKey);
                            jobId = job.getId();
                        }
                        BidirectionalSyncSettingsDao.instance.updateOne(
                            Filters.eq(BidirectionalSyncSettings.ID, existingSettings.getId()),
                            Updates.combine(
                                Updates.set(BidirectionalSyncSettings.ACTIVE, true),
                                Updates.set(BidirectionalSyncSettings.LAST_SYNCED_AT, Context.now()),
                                Updates.set(BidirectionalSyncSettings.JOB_ID, jobId)
                            )
                        );
                    } else {
                        Job job = createRecurringJob(projectKey);

                        if (job == null) {
                            loggerMaker.error("Error while creating recurring job for project key: {}", projectKey);
                            continue;
                        }

                        loggerMaker.info("Created recurring job for project key: {}", projectKey);

                        BidirectionalSyncSettings bidirectionalSyncSettings = new BidirectionalSyncSettings();
                        bidirectionalSyncSettings.setSource(TicketSource.JIRA);
                        bidirectionalSyncSettings.setProjectKey(projectKey);
                        bidirectionalSyncSettings.setActive(true);
                        bidirectionalSyncSettings.setLastSyncedAt(Context.now());
                        bidirectionalSyncSettings.setJobId(job.getId());
                        BidirectionalSyncSettingsDao.instance.insertOne(bidirectionalSyncSettings);
                    }
                    syncedCount++;
                } else if (existingSettings != null) {
                    // Update existing entry to set active=false
                    BidirectionalSyncSettingsDao.instance.updateOne(
                        Filters.eq(BidirectionalSyncSettings.ID, existingSettings.getId()),
                        Updates.combine(
                            Updates.set(BidirectionalSyncSettings.ACTIVE, false),
                            Updates.set(BidirectionalSyncSettings.LAST_SYNCED_AT, Context.now())
                        )
                    );

                    // Cancel the recurring job if it exists
                    ObjectId jobId = existingSettings.getJobId();
                    if (jobId != null) {
                        try {
                            JobScheduler.deleteJob(jobId);
                            loggerMaker.info("Cancelled recurring job for project key: {}", projectKey);
                        } catch (Exception e) {
                            loggerMaker.error("Error cancelling recurring job for project key: {}", projectKey, e);
                        }
                    }

                    loggerMaker.info("Disabled bidirectional sync for project key: {}", projectKey);
                    syncedCount++;
                }
            }

            loggerMaker.info("Synced {} bidirectional settings", syncedCount);

        } catch (Exception e) {
            loggerMaker.error("Error syncing bidirectional settings: {}", e.getMessage(), e);
            addActionError("Error syncing bidirectional settings: " + e.getMessage());
        }
    }

    private Job createRecurringJob(String projectKey) {
        return JobScheduler.scheduleRecurringJob(
            Context.accountId.get(),
            new TicketSyncJobParams(
                TicketSource.JIRA.name(),
                projectKey,
                Context.now()
            ),
            JobExecutorType.DASHBOARD,
            TICKET_SYNC_JOB_RECURRING_INTERVAL_SECONDS
        );
    }

    public String createGeneralJiraTicket() {
        jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if (jiraIntegration == null) {
            addActionError("Jira is not integrated.");
            return ERROR.toUpperCase();
        }

        try {
            BasicDBList contentList = new BasicDBList();
            contentList.add(buildContentDetails(this.description, null));
            BasicDBObject fields = buildPayloadForJiraTicket(this.title, this.projId, this.issueType, contentList, null);
            
            // Combine user labels with AKTO_SYNC label
            List<String> labelsList = new ArrayList<>();
            labelsList.add(JobConstants.TICKET_LABEL_AKTO_SYNC);
            
            // Parse and add user-provided labels
            if (this.labels != null && !this.labels.trim().isEmpty()) {
                String[] userLabels = this.labels.split(",");
                for (String label : userLabels) {
                    String trimmedLabel = label.trim();
                    if (!trimmedLabel.isEmpty() && !labelsList.contains(trimmedLabel)) {
                        labelsList.add(trimmedLabel);
                    }
                }
            }
            
            fields.put("labels", labelsList.toArray(new String[0]));

            BasicDBObject reqPayload = new BasicDBObject();
            reqPayload.put("fields", fields);
            Request.Builder requestBuilder = buildBasicRequest(
                jiraIntegration.getBaseUrl() + CREATE_ISSUE_ENDPOINT,
                jiraIntegration.getUserEmail(),
                jiraIntegration.getApiToken(),
                false
            );
            RequestBody body = RequestBody.create(
                reqPayload.toJson(),
                MediaType.parse("application/json")
            );
            Request request = requestBuilder.post(body).build();
            Response response = null;
            Call call = client.newCall(request);
            String responsePayload = null;
            try {
                response = call.execute();
                responsePayload = response.body().string();

                if (response.code() > 201 || responsePayload == null) {
                    String error = handleError(responsePayload);
                    if (error != null) {
                        addActionError("Error while creating Jira issue: " + error);
                    }
                    return Action.ERROR.toUpperCase();
                }

                Pair<String, String> pair = getJiraTicketUrlPair(responsePayload, jiraIntegration.getBaseUrl());
                this.jiraTicketKey = pair.getSecond();
                this.jiraTicketUrl = pair.getFirst();
                if (!StringUtils.isEmpty(this.actionItemType)) {
                    storeTicketUrlInAccountSettings(jiraTicketUrl);
                }

            } catch (Exception e) {
                // TODO: handle exception
                loggerMaker.errorAndAddToDb("Error creating general Jira ticket: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }

            if(!StringUtils.isEmpty(this.threatEventId)) {
               // utilise the updateMaliciousEventStatus to update the jiraTicket url in the malicious event
               SuspectSampleDataAction suspectSampleDataAction = new SuspectSampleDataAction();
               suspectSampleDataAction.setEventId(this.threatEventId);
               String result = suspectSampleDataAction.updateMaliciousEventJiraUrl(this.threatEventId, jiraTicketUrl);
               if(!result.equals(Action.SUCCESS.toUpperCase())) {
                addActionError("Error updating malicious event Jira URL: " + result);
               }
            }
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                "Error creating general Jira ticket: " + e.getMessage(),
                LoggerMaker.LogDb.DASHBOARD
            );
            return Action.ERROR.toUpperCase();
        }
    }

    private void storeTicketUrlInAccountSettings(String jiraTicketUrl) {
        try {
            AccountSettingsDao.instance.updateOneNoUpsert(AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.JIRA_TICKET_URL_MAP + "." + this.actionItemType, jiraTicketUrl)
            );
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error storing Jira URL: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }
    }
}