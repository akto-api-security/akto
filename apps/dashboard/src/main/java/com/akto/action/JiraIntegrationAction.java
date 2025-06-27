package com.akto.action;

import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.bson.conversions.Bson;

import com.akto.dao.JiraIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.JiraIntegration;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dao.common.LoggerMaker;
import com.akto.dao.common.LoggerMaker.LogDb;
import com.akto.parsers.HttpCallParser;
import com.akto.testing.ApiExecutor;
import com.akto.util.http_util.CoreHTTPClient;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class JiraIntegrationAction extends UserAction {

    private String baseUrl;
    private String projId;
    private String userEmail;
    private String apiToken;
    private String issueType;
    private JiraIntegration jiraIntegration;
    
    private String issueTitle;
    private String hostStr;
    private String endPointStr;
    private String issueUrl;
    private String issueDescription;
    private String jiraTicketUrl;
    private String jiraTicketKey;
    private TestingIssuesId testingIssueId;

    private String origReq;
    private String testReq;
    private String issueId;

    private final String META_ENDPOINT = "/rest/api/3/issue/createmeta";
    private final String CREATE_ISSUE_ENDPOINT = "/rest/api/3/issue";
    private final String ATTACH_FILE_ENDPOINT = "/attachments";
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiExecutor.class);
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder().build();

    public String testIntegration() {

        String url = baseUrl + META_ENDPOINT;
        String authHeader = Base64.getEncoder().encodeToString((userEmail + ":" + apiToken).getBytes());

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + authHeader));
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "GET", null, headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responsePayload = response.getBody();
            loggerMaker.errorAndAddToDb("triggered meta api for test integration step, requestbody " + request.getBody() + " ,responsebody " + response.getBody() + " ,responsestatus " + response.getStatusCode() + " ,url " + url, LoggerMaker.LogDb.DASHBOARD);
            if (response.getStatusCode() != 200 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("error while testing jira integration, url not accessible", LoggerMaker.LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                BasicDBList projects = (BasicDBList) payloadObj.get("projects");
                for (Object projObj: projects) {
                    BasicDBObject obj = (BasicDBObject) projObj;
                    String key = obj.getString("key");
                    loggerMaker.errorAndAddToDb("evaluating issuetype for project key " + key + " ,actualProjId " + projId, LoggerMaker.LogDb.DASHBOARD);
                    if (!key.equalsIgnoreCase(projId)) {
                        continue;
                    }
                    loggerMaker.errorAndAddToDb("evaluating issuetype for project key " + key + ", project json obj " + obj, LoggerMaker.LogDb.DASHBOARD);
                    BasicDBList issueTypes = (BasicDBList) obj.get("issuetypes");
                    issueType = determineIssueType(issueTypes, "TASK");
                    loggerMaker.infoAndAddToDb("evaluated issue type for TASK type " + issueType, LoggerMaker.LogDb.DASHBOARD);
                    if (issueType == null) {
                        issueType = determineIssueType(issueTypes, "BUG");
                        loggerMaker.infoAndAddToDb("evaluated issue type for BUG type " + issueType, LoggerMaker.LogDb.DASHBOARD);
                    }
                    if (issueType == null) {
                        issueType = determineIssueType(issueTypes, "");
                        loggerMaker.infoAndAddToDb("evaluated issue type for ANY type " + issueType, LoggerMaker.LogDb.DASHBOARD);
                    }
                }
                if (issueType == null) {
                    loggerMaker.errorAndAddToDb("error while testing jira integration, unable to resolve issue type id", LoggerMaker.LogDb.DASHBOARD);
                    return Action.ERROR.toUpperCase();
                }
            } catch(Exception e) {
                return null;
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error while testing jira integration, " + e, LoggerMaker.LogDb.DASHBOARD);
            return null;
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String determineIssueType(BasicDBList issueTypes, String jiraIssueType) {

        String issueType = null;
        for (Object issueObj: issueTypes) {
            BasicDBObject obj2 = (BasicDBObject) issueObj;
            String issueName = obj2.getString("name");
            if (!jiraIssueType.equals("") && !issueName.equalsIgnoreCase(jiraIssueType)) {
                continue;
            }
            issueType = obj2.getString("id");
        }
        return issueType;
    }

    public String addIntegration() {

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);

        JiraIntegrationDao.instance.getMCollection().updateOne(
                new BasicDBObject(),
                Updates.combine(
                        Updates.set("baseUrl", baseUrl),
                        Updates.set("projId", projId),
                        Updates.set("userEmail", userEmail),
                        Updates.set("apiToken", apiToken),
                        Updates.set("issueType", issueType),
                        Updates.setOnInsert("createdTs", Context.now()),
                        Updates.set("updatedTs", Context.now())
                ),
                updateOptions
        );

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchIntegration() {
        jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if(jiraIntegration != null){
            jiraIntegration.setApiToken("****************************");
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String createIssue() {

        BasicDBObject reqPayload = new BasicDBObject();
        BasicDBObject fields = new BasicDBObject();

        // issue title
        fields.put("summary", "Akto Report - " + issueTitle);
        jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        Bson filters = Filters.and(
            Filters.eq("_id.apiInfoKey.apiCollectionId", testingIssueId.getApiInfoKey().getApiCollectionId()),
            Filters.eq("_id.apiInfoKey.method", testingIssueId.getApiInfoKey().getMethod()),
            Filters.eq("_id.apiInfoKey.url", testingIssueId.getApiInfoKey().getUrl()),
            Filters.eq("_id" + "." + TestingIssuesId.TEST_SUB_CATEGORY, testingIssueId.getTestSubCategory()),
            Filters.in("_id" + "." + TestingIssuesId.TEST_CATEGORY_FROM_SOURCE_CONFIG, testingIssueId.getTestCategoryFromSourceConfig())
        );
        TestingRunIssues testingRunIssues = TestingRunIssuesDao.instance.findOne(filters);

        // issue type (TASK)
        BasicDBObject issueType = new BasicDBObject();
        issueType.put("id", jiraIntegration.getIssueType());
        fields.put("issuetype", issueType);

        // project id
        BasicDBObject project = new BasicDBObject();
        project.put("key", jiraIntegration.getProjId());
        fields.put("project", project);

        // issue description
        BasicDBObject description = new BasicDBObject();
        description.put("type", "doc");
        description.put("version", 1);
        BasicDBList contentList = new BasicDBList();
        contentList.add(buildContentDetails(hostStr, null));
        contentList.add(buildContentDetails(endPointStr, null));
        contentList.add(buildContentDetails("Issue link - Akto dashboard", issueUrl));
        contentList.add(buildContentDetails(issueDescription, null));
        description.put("content", contentList);

        fields.put("description", description);

        reqPayload.put("fields", fields);

        String url = jiraIntegration.getBaseUrl() + CREATE_ISSUE_ENDPOINT;
        String authHeader = Base64.getEncoder().encodeToString((jiraIntegration.getUserEmail() + ":" + jiraIntegration.getApiToken()).getBytes());

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + authHeader));
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", reqPayload.toString(), headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responsePayload = response.getBody();
            if (response.getStatusCode() > 201 || responsePayload == null) {
                loggerMaker.errorAndAddToDb("error while creating jira issue, url not accessible, requestbody " + request.getBody() + " ,responsebody " + response.getBody() + " ,responsestatus " + response.getStatusCode(), LoggerMaker.LogDb.DASHBOARD);
                if (responsePayload != null) {
                    try {
                        BasicDBObject obj = BasicDBObject.parse(responsePayload);
                        List<String> errorMessages = (List) obj.get("errorMessages");
                        String error;
                        if (errorMessages.size() == 0) {
                            BasicDBObject errObj = BasicDBObject.parse(obj.getString("errors"));
                            error = errObj.getString("project");
                        } else {
                            error = errorMessages.get(0);
                        }
                        addActionError(error);
                    } catch (Exception e) {
                        // TODO: handle exception
                    }
                }
                return Action.ERROR.toUpperCase();
            }
            BasicDBObject payloadObj;
            try {
                payloadObj =  BasicDBObject.parse(responsePayload);
                jiraTicketKey = payloadObj.getString("key");
                jiraTicketUrl = jiraIntegration.getBaseUrl() + "/browse/" + jiraTicketKey;
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb(e, "error making jira issue url " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                return null;
            }
        } catch(Exception e) {
            return null;
        }

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(false);

        TestingRunIssuesDao.instance.getMCollection().updateOne(
                filters,
                Updates.combine(
                        Updates.set("jiraIssueUrl", jiraTicketUrl)
                ),
                updateOptions
        );

        return Action.SUCCESS.toUpperCase();
    }

    public String attachFileToIssue() {

        String origCurl, testCurl;

        try {
            jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
            // issueId = "KAN-34";
            String url = jiraIntegration.getBaseUrl() + CREATE_ISSUE_ENDPOINT + "/" + issueId + ATTACH_FILE_ENDPOINT;
            String authHeader = Base64.getEncoder().encodeToString((jiraIntegration.getUserEmail() + ":" + jiraIntegration.getApiToken()).getBytes());

            origCurl = ExportSampleDataAction.getCurl(origReq);
            testCurl = ExportSampleDataAction.getCurl(testReq);
            HttpResponseParams origObj = HttpCallParser.parseKafkaMessage(origReq);
            BasicDBObject respObj = BasicDBObject.parse(testReq);
            BasicDBObject respPayloaObj = BasicDBObject.parse(respObj.getString("response"));
            String resp = respPayloaObj.getString("body");

            File tmpOutputFile = File.createTempFile("output", ".txt");

            FileUtils.writeStringToFile(new File(tmpOutputFile.getPath()), "Original Curl ----- \n\n", (String) null);
            FileUtils.writeStringToFile(new File(tmpOutputFile.getPath()), origCurl + "\n\n", (String) null, true);
            FileUtils.writeStringToFile(new File(tmpOutputFile.getPath()), "Original Api Response ----- \n\n", (String) null, true);
            FileUtils.writeStringToFile(new File(tmpOutputFile.getPath()), origObj.getPayload() + "\n\n", (String) null, true);

            FileUtils.writeStringToFile(new File(tmpOutputFile.getPath()), "Test Curl ----- \n\n", (String) null, true);
            FileUtils.writeStringToFile(new File(tmpOutputFile.getPath()), testCurl + "\n\n", (String) null, true);
            FileUtils.writeStringToFile(new File(tmpOutputFile.getPath()), "Test Api Response ----- \n\n", (String) null, true);
            FileUtils.writeStringToFile(new File(tmpOutputFile.getPath()), resp + "\n\n", (String) null, true);


            MediaType mType = MediaType.parse("application/octet-stream");
            RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", tmpOutputFile.getName(),
                            RequestBody.create(tmpOutputFile, mType))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Authorization", "Basic " + authHeader)
                    .header("X-Atlassian-Token", "nocheck")
                    .build();

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

    public String getIssueTitle() {
        return issueTitle;
    }

    public void setIssueTitle(String issueTitle) {
        this.issueTitle = issueTitle;
    }

    public String getHostStr() {
        return hostStr;
    }

    public void setHostStr(String hostStr) {
        this.hostStr = hostStr;
    }

    public String getEndPointStr() {
        return endPointStr;
    }

    public void setEndPointStr(String endPointStr) {
        this.endPointStr = endPointStr;
    }

    public String getIssueUrl() {
        return issueUrl;
    }

    public void setIssueUrl(String issueUrl) {
        this.issueUrl = issueUrl;
    }

    public String getIssueDescription() {
        return issueDescription;
    }

    public void setIssueDescription(String issueDescription) {
        this.issueDescription = issueDescription;
    }

    public String getJiraTicketUrl() {
        return jiraTicketUrl;
    }

    public void setJiraTicketUrl(String jiraTicketUrl) {
        this.jiraTicketUrl = jiraTicketUrl;
    }

    public TestingIssuesId getTestingIssueId() {
        return testingIssueId;
    }

    public void setTestingIssueId(TestingIssuesId testingIssueId) {
        this.testingIssueId = testingIssueId;
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

    public String getJiraTicketKey() {
        return jiraTicketKey;
    }

    public void setJiraTicketKey(String jiraTicketKey) {
        this.jiraTicketKey = jiraTicketKey;
    }

    
}
