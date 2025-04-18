package com.akto.jobs.executors;

import com.akto.dao.JiraIntegrationDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.dto.jira_integration.JiraMetaData;
import com.akto.dto.jobs.AutoTicketParams;
import com.akto.dto.jobs.Job;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.jobs.JobExecutor;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
import com.akto.util.http_util.CoreHTTPClient;

import com.akto.utils.FileUtils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import okhttp3.*;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.io.File;
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
    private static final String CREATE_ISSUE_ENDPOINT_BULK = "/rest/api/3/issue/bulk";
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

        Map<String, Info> infoMap = fetchYamlInfoMap(issues);

        List<JiraMetaData> batchMetaList = new ArrayList<>();

        for (TestingRunIssues issue : issues) {

            TestingIssuesId id = issue.getId();
            Info info = infoMap.get(id.getTestSubCategory());
            if (info == null) {
                logger.error("Yaml Template not found. issueId: {}", id);
                continue;
            }

            TestingRunResult testingRunResult = TestingRunResultDao.instance.findOne(Filters.and(
                Filters.in(TestingRunResult.TEST_SUB_TYPE, issue.getId().getTestSubCategory()),
                Filters.in(TestingRunResult.API_INFO_KEY, issue.getId().getApiInfoKey())
            ), Projections.include("_id", TestingRunResult.TEST_RESULTS));

            if(testingRunResult == null) {
                logger.errorAndAddToDb("Error: Testing Run Result not found", LogDb.DASHBOARD);
                continue;
            }

            JiraMetaData meta;
            try {
                URL url = new URL(id.getApiInfoKey().getUrl());
                meta = new JiraMetaData(
                    info.getName(),
                    "Host - " + url.getHost(),
                    url.getPath(),
                    "https://app.akto.io/dashboard/issues?result=" + testingRunResult.getId().toHexString(),
                    info.getDescription(),
                    id
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
/*        List<JiraMetaData> jiraMetaDataList = enrichIssuesWithMeta(issues, infoMap);
        if (jiraMetaDataList.isEmpty()) {
            return;
        }

        BasicDBObject jiraPayload = buildJiraPayload(jiraMetaDataList, issueType, projId);
        List<String> createdIssues = sendJiraBulkCreate(jira, jiraPayload, jiraMetaDataList);

        List<TestingRunResult> resultList = fetchRunResults(jiraMetaDataList);
        attachFilesToIssues(jira, createdIssues, resultList);*/
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
            Filters.exists(TestingRunIssues.JIRA_ISSUE_URL, false)
        );
        return TestingRunIssuesDao.instance.findAll(filter);
    }

    private Map<String, Info> fetchYamlInfoMap(List<TestingRunIssues> issues) {
        List<String> subCategories = issues.stream()
            .map(i -> i.getId().getTestSubCategory())
            .distinct()
            .collect(Collectors.toList());
        List<YamlTemplate> templates = YamlTemplateDao.instance.findAll(
            Filters.in("_id", subCategories), Projections.include("info")
        );

        Map<String, Info> infoMap = new HashMap<>();
        for (YamlTemplate template : templates) {
            if (template != null && template.getInfo() != null) {
                Info info = template.getInfo();
                infoMap.put(info.getSubCategory(), info);
            }
        }
        return infoMap;
    }

    private void processJiraBatch(List<JiraMetaData> batch, String issueType, String projId, JiraIntegration jira) {
        BasicDBObject payload = buildJiraPayload(batch, issueType, projId);
        List<String> createdKeys = sendJiraBulkCreate(jira, payload, batch);

        List<TestingRunResult> results = fetchRunResults(batch);
        attachFilesToIssues(jira, createdKeys, results);
    }

    private JiraMetaData enrichIssuesWithMeta(TestingRunIssues issue, Map<String, Info> infoMap) {
        /*List<JiraMetaData> list = new ArrayList<>();
        for (TestingRunIssues issue : issues)
        return list;*/
            //if (issue.getJiraIssueUrl() != null && !issue.getJiraIssueUrl().isEmpty()) continue;

            TestingIssuesId id = issue.getId();
            Info info = infoMap.get(id.getTestSubCategory());
            if (info == null) {
                logger.error("YAML Template is empty. SubCategory: {}", id.getTestSubCategory());
                return null;
            }

            try {
                URL url = new URL(id.getApiInfoKey().getUrl());
                return new JiraMetaData(
                    info.getName(),
                    "Host - " + url.getHost(),
                    url.getPath(),
                    "https://app.akto.io/dashboard/issues?result=" + id.getTestSubCategory(),
                    info.getDescription(),
                    id
                );
            } catch (Exception e) {
                logger.error("Error parsing URL: {}", e.getMessage());
                return null;
            }
    }

    private List<TestingRunResult> fetchRunResults(List<JiraMetaData> metaDataList) {
        List<TestingRunResult> results = new ArrayList<>();
        for (JiraMetaData data : metaDataList) {
            TestingIssuesId id = data.getTestingIssueId();
            TestingRunResult result = TestingRunResultDao.instance.findOne(
                Filters.and(
                    Filters.eq("testSubType", id.getTestSubCategory()),
                    Filters.eq("apiInfoKey", id.getApiInfoKey())
                ),
                Projections.include("_id", "testResults")
            );
            if (result != null) results.add(result);
        }
        return results;
    }

    private BasicDBObject buildJiraPayload(List<JiraMetaData> metaList, String issueType, String projId) {
        BasicDBList issueUpdates = new BasicDBList();
        for (JiraMetaData meta : metaList) {
            BasicDBObject fields = jiraTicketPayloadCreator(meta, issueType, projId);
            BasicDBObject issueObject = new BasicDBObject("fields", fields);
            issueUpdates.add(issueObject);
        }
        BasicDBObject payload = new BasicDBObject("issueUpdates", issueUpdates);
        return payload;
    }

    private List<String> sendJiraBulkCreate(JiraIntegration jira, BasicDBObject payload, List<JiraMetaData> metaList) {
        String url = jira.getBaseUrl() + CREATE_ISSUE_ENDPOINT_BULK;
        String authHeader = Base64.getEncoder().encodeToString((jira.getUserEmail() + ":" + jira.getApiToken()).getBytes());

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic " + authHeader));

        OriginalHttpRequest request = new OriginalHttpRequest(url, "", "POST", payload.toString(), headers, "");

        List<String> createdKeys = new ArrayList<>();
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            if (response.getStatusCode() > 201 || response.getBody() == null) {
                logger.error("Failed Jira bulk create. Status: {}, Body: {}", response.getStatusCode(), response.getBody());
                return createdKeys;
            }

            BasicDBObject resObj = BasicDBObject.parse(response.getBody());
            List<BasicDBObject> issues = (List<BasicDBObject>) resObj.get("issues");

            for (int i = 0; i < issues.size(); i++) {
                BasicDBObject issue = issues.get(i);
                String key = issue.getString("key");
                createdKeys.add(key);

                JiraMetaData meta = metaList.get(i);
                TestingRunIssuesDao.instance.getMCollection().updateOne(
                    Filters.eq(Constants.ID, meta.getTestingIssueId()),
                    Updates.set("jiraIssueUrl", jira.getBaseUrl() + "/browse/" + key),
                    new UpdateOptions().upsert(false)
                );
            }

        } catch (Exception e) {
            logger.error("Exception in Jira bulk create: {}", e.getMessage());
        }

        return createdKeys;
    }

    private void attachFilesToIssues(JiraIntegration jira, List<String> issueKeys, List<TestingRunResult> results) {
        for (int i = 0; i < issueKeys.size(); i++) {
            String issueKey = issueKeys.get(i);
            TestResult result = (TestResult) results.get(i).getTestResults().get(results.get(i).getTestResults().size() - 1);
            attachFileToIssue(jira, issueKey, result.getOriginalMessage(), result.getMessage());
        }
    }

    private void attachFileToIssue(JiraIntegration jira, String issueId, String origReq, String testReq) {
        try {
            String url = jira.getBaseUrl() + "/rest/api/3/issue/" + issueId + ATTACH_FILE_ENDPOINT;
            String authHeader = Base64.getEncoder().encodeToString((jira.getUserEmail() + ":" + jira.getApiToken()).getBytes());

            File file = FileUtils.createRequestFile(origReq, testReq);
            if (file == null) return;

            MediaType mediaType = MediaType.parse("application/octet-stream");
            RequestBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getName(), RequestBody.create(file, mediaType))
                .build();

            Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .header("Authorization", "Basic " + authHeader)
                .header("X-Atlassian-Token", "nocheck")
                .build();

            try (Response ignored = client.newCall(request).execute()) {
            } catch (Exception ex) {
                logger.errorAndAddToDb(ex,
                    String.format("Failed to call jira from url %s. Error %s", url, ex.getMessage()),
                    LogDb.DASHBOARD);
            }

        } catch (Exception e) {
            logger.error("Error attaching file to Jira: {}", e.getMessage());
        }
    }

    private BasicDBObject jiraTicketPayloadCreator(JiraMetaData meta, String issueType, String projId) {
        BasicDBObject fields = new BasicDBObject();
        String method = meta.getTestingIssueId().getApiInfoKey().getMethod().name();
        String endpoint = meta.getEndPointStr().replace("Endpoint - ", "");
        String truncated = endpoint.length() > 30 ? endpoint.substring(0, 15) + "..." + endpoint.substring(endpoint.length() - 15) : endpoint;

        fields.put("summary", "Akto Report - " + meta.getIssueTitle() + " (" + method + " - " + truncated + ")");

        fields.put("issuetype", new BasicDBObject("id", issueType));
        fields.put("project", new BasicDBObject("key", projId));

        BasicDBList contentList = new BasicDBList();
        contentList.add(buildContentDetails(meta.getHostStr(), null));
        contentList.add(buildContentDetails(meta.getEndPointStr(), null));
        contentList.add(buildContentDetails("Issue link - Akto dashboard", meta.getIssueUrl()));
        contentList.add(buildContentDetails(meta.getIssueDescription(), null));

        BasicDBObject description = new BasicDBObject("type", "doc").append("version", 1).append("content", contentList);
        fields.put("description", description);

        return fields;
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
