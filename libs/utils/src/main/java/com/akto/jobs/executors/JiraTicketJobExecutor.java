package com.akto.jobs.executors;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.Config.AktoHostUrlConfig;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.ApiCollection;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.dto.jira_integration.JiraMetaData;
import com.akto.dto.jobs.AutoTicketParams;
import com.akto.dto.jobs.Job;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.MultiExecTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.jobs.JobExecutor;
import com.akto.jobs.utils.JobConstants;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.akto.util.enums.GlobalEnums.TicketSource;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.utils.FileUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import okhttp3.*;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class JiraTicketJobExecutor extends JobExecutor<AutoTicketParams> {

    public static final JiraTicketJobExecutor INSTANCE = new JiraTicketJobExecutor();
    private static final int BATCH_SIZE = 10;

    private JiraTicketJobExecutor() {
        super(AutoTicketParams.class);
    }

    private static final LoggerMaker logger = new LoggerMaker(JiraTicketJobExecutor.class);
    private static final String ATTACH_FILE_ENDPOINT = "/attachments";
    private static final OkHttpClient client = CoreHTTPClient.client.newBuilder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build();

    @Override
    protected void runJob(Job job) throws Exception {
        AutoTicketParams jobParams = paramClass.cast(job.getJobParams());
        JiraIntegration jira = loadJiraIntegration();
        ObjectId summaryId = jobParams.getSummaryId();
        String projId = jobParams.getProjectId();
        String issueType = jobParams.getIssueType();
        List<String> severities = jobParams.getSeverities();

        issueType = validateAndGetIssueType(jira, projId, issueType);

        List<TestingRunIssues> issues = fetchTestRunIssues(summaryId, severities);
        if (issues.isEmpty()) {
            logger.info("No issues found for severities: {}", severities);
            return;
        }

        String dashboardUrl = Constants.DEFAULT_AKTO_DASHBOARD_URL;
        if (DashboardMode.isOnPremDeployment()) {
            AktoHostUrlConfig aktoUrlConfig = (AktoHostUrlConfig) ConfigsDao.instance.findOne(
                Filters.eq(Constants.ID, ConfigType.AKTO_DASHBOARD_HOST_URL.name()));
            if (aktoUrlConfig == null) {
                logger.error("Akto Dashboard URL not found. jobId: {}", job.getId());
            } else {
                dashboardUrl = aktoUrlConfig.getHostUrl();
            }
        }

        List<JiraMetaData> batchMetaList = new ArrayList<>();

        for (TestingRunIssues issue : issues) {

            if (issue.getJiraIssueUrl() != null && !issue.getJiraIssueUrl().isEmpty()) {
                logger.info("Skipping already ticketed issue: {}", issue.getId());
                continue;
            }

            TestingIssuesId id = issue.getId();
            Info info = YamlTemplateDao.instance.fetchTestInfoMap(Filters.eq(YamlTemplateDao.ID, id.getTestSubCategory())).get(id.getTestSubCategory());
            if (info == null) {
                logger.error("Yaml Template not found. issueId: {}", id);
                continue;
            }

            TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(Filters.and(
                Filters.in(TestingRunResult.TEST_SUB_TYPE, issue.getId().getTestSubCategory()),
                Filters.in(TestingRunResult.API_INFO_KEY, issue.getId().getApiInfoKey()),
                Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryId)
            ));

            if(testingRunResult == null) {
                logger.error("Testing Run Result not found. issueId: {}", issue.getId());
                continue;
            }

            JiraMetaData meta;
            try {
                String url = issue.getId().getApiInfoKey().getUrl();
                String host = "";
                try {
                    URI uri = new URI(url);
                    host = uri.getHost();
                } catch (Exception e) {
                    // TODO: handle exception
                }
                meta = new JiraMetaData(
                    info.getName(),
                    "Host - " + host,
                    url,
                    dashboardUrl + "/dashboard/issues?result=" + testingRunResult.getId().toHexString(),
                    info.getDescription(),
                    id,
                    summaryId,
                    null,
                    ""
                );
            } catch (Exception e) {
                logger.error("Error parsing URL for issue {}: {}", id, e.getMessage(), e);
                continue;
            }

            batchMetaList.add(meta);

            if (batchMetaList.size() == BATCH_SIZE) {
                processJiraBatch(batchMetaList, issueType, projId, jira);
                batchMetaList.clear();
                updateJobHeartbeat(job);
            }
        }

        if (!batchMetaList.isEmpty()) {
            processJiraBatch(batchMetaList, issueType, projId, jira);
            updateJobHeartbeat(job);
        }
    }

    private JiraIntegration loadJiraIntegration() throws Exception {
        JiraIntegration integration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if (integration == null) {
            throw new Exception("Jira is not integrated");
        }
        return integration;
    }

    private String validateAndGetIssueType(JiraIntegration jira, String projId, String issueType) throws Exception {

        if (!jira.getProjectIdsMap().containsKey(projId)) {
            throw new Exception("Project id not found in jira integration.");
        }

        Optional<BasicDBObject> issueTypeOpt = jira.getProjectIdsMap().get(projId).stream()
            .filter(basicDBObject -> basicDBObject.getString("issueType").equals(issueType))
            .findFirst();
        if (!issueTypeOpt.isPresent()) {
            throw new Exception("IssueType is not present");
        }
        return issueTypeOpt.get().getString("issueId");
    }

    private List<TestingRunIssues> fetchTestRunIssues(ObjectId summaryId, List<String> severities) {
        Bson filter = Filters.and(
            Filters.eq(TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID, summaryId),
            Filters.in(TestingRunIssues.KEY_SEVERITY, severities),
            Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN),
            Filters.exists(TestingRunIssues.JIRA_ISSUE_URL, false)
        );
        return TestingRunIssuesDao.instance.findAll(filter);
    }

    private void processJiraBatch(List<JiraMetaData> batch, String issueType, String projId, JiraIntegration jira) throws Exception {
        BasicDBObject payload = buildJiraPayload(batch, issueType, projId, jira);
        List<String> createdKeys = sendJiraBulkCreate(jira, payload, batch, projId);
        logger.info("Created {} Jira issues out of {} Akto issues", createdKeys.size(), batch.size());
        List<TestingRunResult> results = fetchRunResults(batch);
        attachFilesToIssues(jira, createdKeys, results);
    }

    private List<TestingRunResult> fetchRunResults(List<JiraMetaData> metaDataList) {
        List<TestingRunResult> results = new ArrayList<>();
        for (JiraMetaData data : metaDataList) {
            TestingIssuesId id = data.getTestingIssueId();
            TestingRunResult result = TestingRunResultDao.instance.findOne(
                Filters.and(
                    Filters.eq(TestingRunResult.TEST_SUB_TYPE, id.getTestSubCategory()),
                    Filters.eq(TestingRunResult.API_INFO_KEY, id.getApiInfoKey()),
                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, data.getTestSummaryId())
                )
            );
            if (result != null) results.add(result);
        }
        return results;
    }

    private BasicDBObject buildJiraPayload(List<JiraMetaData> metaList, String issueType, String projId, JiraIntegration jira) {
        BasicDBList issueUpdates = new BasicDBList();
        for (JiraMetaData meta : metaList) {
            BasicDBObject fields = buildJiraTicketFields(meta, issueType, projId, jira);
            BasicDBObject issueObject = new BasicDBObject("fields", fields);
            issueUpdates.add(issueObject);
        }
        BasicDBObject payload = new BasicDBObject("issueUpdates", issueUpdates);
        return payload;
    }

    private BasicDBObject buildJiraTicketFields(JiraMetaData meta, String issueType, String projId, JiraIntegration jira) {
        String method = meta.getTestingIssueId().getApiInfoKey().getMethod().name();
        String endpoint = meta.getEndPointStr().replace("Endpoint - ", "");
        String truncated = endpoint.length() > 30 ? endpoint.substring(0, 15) + "..." + endpoint.substring(endpoint.length() - 15) : endpoint;
        String summary = "Akto Report - " + meta.getIssueTitle() + " (" + method + " - " + truncated + ")";

        BasicDBObject fields = new BasicDBObject();
        fields.put("summary", summary);
        fields.put("project", new BasicDBObject("key", projId));
        fields.put("issuetype", new BasicDBObject("id", issueType));
        fields.put("labels", new String[]{JobConstants.TICKET_LABEL_AKTO_SYNC});

        // Build description content
        List<BasicDBObject> contentList = new ArrayList<>();
        contentList.add(buildContentDetails(meta.getHostStr(), null));
        contentList.add(buildContentDetails(meta.getEndPointStr(), null));
        contentList.add(buildContentDetails("Issue link - Akto dashboard", meta.getIssueUrl()));
        contentList.add(buildContentDetails(meta.getIssueDescription(), null));

        boolean isDataCenter = jira.getJiraType() == JiraIntegration.JiraType.DATA_CENTER;
        
        if (isDataCenter) {
            // Data Center uses Wiki Markup format
            StringBuilder description = new StringBuilder();
            for (BasicDBObject content : contentList) {
                BasicDBList innerContent = (BasicDBList) content.get("content");
                if (innerContent != null && !innerContent.isEmpty()) {
                    BasicDBObject textObj = (BasicDBObject) innerContent.get(0);
                    String text = textObj.getString("text");
                    Object marks = textObj.get("marks");
                    
                    // Handle links
                    if (marks != null && marks instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<BasicDBObject> marksList = (List<BasicDBObject>) marks;
                        if (!marksList.isEmpty()) {
                            BasicDBObject mark = marksList.get(0);
                            if ("link".equals(mark.getString("type"))) {
                                BasicDBObject attrs = (BasicDBObject) mark.get("attrs");
                                String href = attrs.getString("href");
                                description.append("[").append(text).append("|").append(href).append("]\n\n");
                                continue;
                            }
                        }
                    }
                    description.append(text).append("\n\n");
                }
            }
            fields.put("description", description.toString().trim());
        } else {
            // Cloud uses Atlassian Document Format (ADF)
            BasicDBList fullContentList = new BasicDBList();
            fullContentList.addAll(contentList);
            BasicDBObject description = new BasicDBObject("type", "doc")
                .append("version", 1)
                .append("content", fullContentList);
            fields.put("description", description);
        }

        return fields;
    }

    private List<String> sendJiraBulkCreate(JiraIntegration jira, BasicDBObject payload, List<JiraMetaData> metaList,
        String projId) throws Exception {
        boolean isDataCenter = jira.getJiraType() == JiraIntegration.JiraType.DATA_CENTER;
        String endpoint = isDataCenter ? "/rest/api/2/issue/bulk" : "/rest/api/3/issue/bulk";
        String url = jira.getBaseUrl() + endpoint;

        Map<String, List<String>> headers = new HashMap<>();
        if (isDataCenter) {
            headers.put("Authorization", Collections.singletonList("Bearer " + jira.getApiToken()));
        } else {
            String authHeader = Base64.getEncoder().encodeToString(
                (jira.getUserEmail() + ":" + jira.getApiToken()).getBytes());
            headers.put("Authorization", Collections.singletonList("Basic " + authHeader));
        }

        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", payload.toString(), headers, "");

        List<String> createdKeys = new ArrayList<>();
        OriginalHttpResponse response;
        try {
            response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            if (response.getStatusCode() > 201) {
                logger.error("Failed Jira bulk create. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
                return createdKeys;
            }
        } catch (Exception e) {
            logger.error("Exception in Jira bulk create: {}", e.getMessage());
            throw e;
        }

        BasicDBObject resObj = BasicDBObject.parse(response.getBody());
        List<BasicDBObject> issues = (List<BasicDBObject>) resObj.get("issues");

        for (int i = 0; i < issues.size(); i++) {
            BasicDBObject issue = issues.get(i);
            String key = issue.getString("key");
            createdKeys.add(key);

            JiraMetaData meta = metaList.get(i);
            String jiraIssueUrl = jira.getBaseUrl() + "/browse/" + key;
            
            TestingRunIssuesDao.instance.getMCollection().updateOne(
                Filters.eq(Constants.ID, meta.getTestingIssueId()),
                Updates.combine(
                    Updates.set("jiraIssueUrl", jiraIssueUrl),
                    Updates.set(TestingRunIssues.TICKET_SOURCE, TicketSource.JIRA.name()),
                    Updates.set(TestingRunIssues.TICKET_PROJECT_KEY, projId),
                    Updates.set(TestingRunIssues.TICKET_ID, key),
                    Updates.set(TestingRunIssues.TICKET_LAST_UPDATED_AT, Context.now())
                ),
                new UpdateOptions().upsert(false)
            );
            logger.info("Created Jira issue: {} for TestingRunIssue ID: {}", key, meta.getTestingIssueId());
        }

        return createdKeys;
    }

    private void attachFilesToIssues(JiraIntegration jira, List<String> issueKeys, List<TestingRunResult> results) {
        for (int i = 0; i < issueKeys.size(); i++) {
            String issueKey = issueKeys.get(i);
            TestResult result = getTestResultFromTestingRunResult(results.get(i));
            attachFileToIssue(jira, issueKey, result.getOriginalMessage(), result.getMessage());
        }
    }

    private TestResult getTestResultFromTestingRunResult(TestingRunResult testingRunResult) {
        TestResult testResult;
        try {
            GenericTestResult gtr = testingRunResult.getTestResults().get(testingRunResult.getTestResults().size() - 1);
            if (gtr instanceof TestResult) {
                testResult = (TestResult) gtr;
            } else if (gtr instanceof MultiExecTestResult) {
                MultiExecTestResult multiTestRes = (MultiExecTestResult) gtr;
                List<GenericTestResult> genericTestResults = multiTestRes.convertToExistingTestResult(testingRunResult);
                GenericTestResult genericTestResult = genericTestResults.get(genericTestResults.size() - 1);
                if (genericTestResult instanceof TestResult) {
                    testResult = (TestResult) genericTestResult;
                } else {

                    testResult = null;
                }
            } else {
                testResult = null;
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error while casting GenericTestResult obj to TestResult obj: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            testResult = null;
        }

        return testResult;
    }

    private void attachFileToIssue(JiraIntegration jira, String issueId, String origReq, String testReq) {
        try {
            boolean isDataCenter = jira.getJiraType() == JiraIntegration.JiraType.DATA_CENTER;
            String apiVersion = isDataCenter ? "2" : "3";
            String url = jira.getBaseUrl() + "/rest/api/" + apiVersion + "/issue/" + issueId + ATTACH_FILE_ENDPOINT;
            
            File file = FileUtils.createRequestFile(origReq, testReq);
            if (file == null) return;

            MediaType mediaType = MediaType.parse("application/octet-stream");
            RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(file, mediaType))
                .build();

            Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("X-Atlassian-Token", "nocheck");

            // Set authentication based on deployment type
            if (isDataCenter) {
                // Data Center uses Bearer token
                requestBuilder.header("Authorization", "Bearer " + jira.getApiToken());
            } else {
                // Cloud uses Basic auth
                String authHeader = Base64.getEncoder().encodeToString((jira.getUserEmail() + ":" + jira.getApiToken()).getBytes());
                requestBuilder.header("Authorization", "Basic " + authHeader);
            }

            Request request = requestBuilder.build();

            try (Response ignored = client.newCall(request).execute()) {
                logger.info("File attached to Jira issue: {}", issueId);
            }
        } catch (Exception e) {
            logger.error("Error attaching file to Jira: issueId: {}", issueId, e.getMessage());
        }
    }

    private BasicDBObject buildContentDetails(String text, String link) {
        BasicDBObject content = new BasicDBObject("type", "paragraph");
        BasicDBList contentInner = new BasicDBList();
        BasicDBObject inner = new BasicDBObject("text", text).append("type", "text");

        if (link != null) {
            inner.put("marks", Collections.singletonList(
                new BasicDBObject("type", "link").append("attrs", new BasicDBObject("href", link))));
        }

        contentInner.add(inner);
        content.put("content", contentInner);
        return content;
    }
}
