package com.akto.utils.jira;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;

import com.akto.calendar.DateUtils;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.ComplianceMapping;
import com.akto.dto.testing.Remediation;

import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import okhttp3.Request;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;

public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class, LogDb.DASHBOARD);

    private static final String CREATE_ISSUE_FIELD_METADATA_ENDPOINT = "/rest/api/3/issue/createmeta/%s/issuetypes/%s"; 
    private static final String FIELD_SEARCH_ENDPOINT = "/rest/api/3/field/search";
    
    //Caching for Account Wise Jira Integration Issue Creation Fields
    private static final ConcurrentHashMap<Integer, Pair<Map<String, Map<String, BasicDBList>>, Integer>> accountWiseJiraFieldsMap = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 30 * 60; // 30 minutes

    // Thread pool for making calls to jira in parallel
    private static final ExecutorService multiFieldPool = Executors.newFixedThreadPool(10);

    public static Pair<Integer, Map<String, BasicDBObject>> parseJiraFieldSearchPayload(String responsePayload) {
        int total = 0;
        Map<String, BasicDBObject> fieldSearchMap = new HashMap<>();
        
        try {
            BasicDBObject payloadObj = BasicDBObject.parse(responsePayload);
            total = payloadObj.getInt("total", 0);

            BasicDBList values = (BasicDBList) payloadObj.get("values");
            if (values != null) {
                for (Object valueObj : values) {
                    if (valueObj == null)
                        continue;
                    BasicDBObject value = (BasicDBObject) valueObj;
                    String id = value.getString("id", "");
                    if (id == null || id.isEmpty()) {
                        continue;
                    }
                    fieldSearchMap.put(id, value);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while parsing jira field search results.");
        }

        return new Pair<>(total, fieldSearchMap);
    }    

    public static BasicDBList parseJiraFieldsForIssuePayload(String responsePayload, String projectKey, String issueType, Map<String, BasicDBObject> fieldSearchMap) {
        BasicDBList fieldsForIssueType = new BasicDBList();
        
        try {
            BasicDBObject payloadObj = BasicDBObject.parse(responsePayload);
            BasicDBList issueFields = (BasicDBList) payloadObj.get("fields");

            if (issueFields != null) {
                for (Object issueField : issueFields) {
                    if (issueField == null)
                        continue;

                    BasicDBObject issueFieldBasicDBObject = (BasicDBObject) issueField;
                    String fieldId = issueFieldBasicDBObject.getString("fieldId");
                    String fieldName = issueFieldBasicDBObject.getString("name");

                    if (fieldId == null || fieldName == null)
                        continue;

                    // check if fieldId is present in fieldSearchMap which contains more metadata about the field
                    if (fieldSearchMap.containsKey(fieldId)) {
                        Boolean ignoreField = false;
                        BasicDBObject fieldValue = fieldSearchMap.get(fieldId);
                        if (fieldValue == null)
                            ignoreField = true;
                        else {
                            Boolean isUnscreenable = fieldValue.getBoolean("isUnscreenable", false);
                            Boolean isLocked = fieldValue.getBoolean("isLocked", false);
                            if (isUnscreenable == true || isLocked == true)
                                ignoreField = true;
                        }

                        if (ignoreField)
                            continue;
                    }

                    fieldsForIssueType.add(issueFieldBasicDBObject);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while parsing issue fields response for project: "
                                    + projectKey + ", issueType: " + issueType);
        }

        return fieldsForIssueType;
    }    

    public static Map<String, Map<String, BasicDBList>> fetchAccountJiraFields() {
        JiraIntegration jiraIntegration = JiraIntegrationDao.instance.findOne(new BasicDBObject());
        if (jiraIntegration == null) {
            loggerMaker.errorAndAddToDb("Jira integration not found. Cannot fetch create issue field metadata.");
            return Collections.emptyMap();
        }

        String baseUrl = jiraIntegration.getBaseUrl();
        Map<String, List<String>> headers = new HashMap<>();
        String authHeader = Base64.getEncoder()
                .encodeToString((jiraIntegration.getUserEmail() + ":" + jiraIntegration.getApiToken()).getBytes());
        headers.put("Authorization", Collections.singletonList("Basic " + authHeader));

        int accountId = Context.accountId.get();

        int total = 0;
        AtomicInteger startAt = new AtomicInteger(0);
        int maxResults = 50;
        String fieldSearchRequestUrl = baseUrl + FIELD_SEARCH_ENDPOINT;
        String queryParamsFormatStr = "startAt=%d&expand=isUnscreenable&expand=isLocked";
        Map<String, BasicDBObject> fieldSearchMap = new HashMap<>();
        List<Future<Map<String, BasicDBObject>>> fieldSearchFutures = new ArrayList<>();

        // Initial request to get the total number of fields and the first page of
        // results
        try {
            String queryParams = String.format(queryParamsFormatStr, startAt.get());
            OriginalHttpRequest request = new OriginalHttpRequest(fieldSearchRequestUrl, queryParams, "GET", "",
                    headers, "");
            loggerMaker.infoAndAddToDb("Performing jira field search request: " + request.getUrl() + "?" + queryParams);

            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            String responsePayload = response.getBody();

            if (response.getStatusCode() > 201 || responsePayload == null) {
                loggerMaker.errorAndAddToDb(String.format(
                        "Error while making jira field search request. Response Code %d", response.getStatusCode()));
                return Collections.emptyMap();
            }

            //BasicDBObject payloadObj;
            Pair<Integer, Map<String, BasicDBObject>> parsedPayload = parseJiraFieldSearchPayload(responsePayload);
            
            total = parsedPayload.getFirst();
            Map<String, BasicDBObject> initialFieldSearchMap = parsedPayload.getSecond();
            if (initialFieldSearchMap != null) {
                fieldSearchMap.putAll(initialFieldSearchMap);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while making jira field search request. ");
        }

        // Fetch the remaining fields search pages in parallel
        while (true) {
            if (startAt.get() >= total) {
                break;
            }

            String queryParams = String.format(queryParamsFormatStr, startAt.get());
            OriginalHttpRequest request = new OriginalHttpRequest(fieldSearchRequestUrl, queryParams, "GET", "",
                    headers, "");

            fieldSearchFutures.add(multiFieldPool.submit(() -> {
                Context.accountId.set(accountId);
                Map<String, BasicDBObject> paginatedFieldSearchMap = new HashMap<>();

                try {
                    loggerMaker.infoAndAddToDb(
                            "Performing jira field search request: " + request.getUrl() + "?" + queryParams);

                    OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false,
                            new ArrayList<>());
                    String responsePayload = response.getBody();

                    if (response.getStatusCode() > 201 || responsePayload == null) {
                        loggerMaker.errorAndAddToDb(
                                String.format("Error while making jira field search request. Response Code %d",
                                        response.getStatusCode()));
                    } else {
                        Pair<Integer, Map<String, BasicDBObject>> parsedPayload = parseJiraFieldSearchPayload(responsePayload);

                        if (parsedPayload.getSecond() != null) {
                            paginatedFieldSearchMap.putAll(parsedPayload.getSecond());
                        }
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while making jira field search request. ");
                }

                return paginatedFieldSearchMap;
            }));

            startAt.addAndGet(maxResults);
        }

        // Wait for all tasks to finish and fill the field search map
        for (Future<Map<String, BasicDBObject>> future : fieldSearchFutures) {
            try {
                Map<String, BasicDBObject> paginatedFieldSearchMap = future.get(60, TimeUnit.SECONDS);
                if (paginatedFieldSearchMap != null) {
                    fieldSearchMap.putAll(paginatedFieldSearchMap);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error waiting for Jira field search task");
            }
        }

        Map<String, List<BasicDBObject>> projectIdsMap = jiraIntegration.getProjectIdsMap();
        if (projectIdsMap == null || projectIdsMap.isEmpty()) {
            return Collections.emptyMap();
        }

        // Map to hold the information required to create issues for each project and issue type
        Map<String, Map<String, BasicDBList>> createIssueFieldMetaData = new HashMap<>();
        
        List<Future<Map.Entry<String, Map<String, BasicDBList>>>> futures = new ArrayList<>();

        // Iterate for each project
        for (Map.Entry<String, List<BasicDBObject>> entry : projectIdsMap.entrySet()) {
            String projectKey = entry.getKey();
            List<BasicDBObject> issueTypes = entry.getValue();

            // Create a task for each project to fetch issue type fields in parallel
            futures.add(multiFieldPool.submit(() -> {
                Context.accountId.set(accountId);
                Map<String, BasicDBList> fieldsForProject = new HashMap<>();

                // Iterate over each issue type in the project
                for (BasicDBObject issueTypeObj : issueTypes) {
                    if (issueTypeObj == null)
                        continue;
                    String issueId = issueTypeObj.getString("issueId");
                    String issueType = issueTypeObj.getString("issueType");

                    if (issueId == null || issueType == null)
                        continue;
                    BasicDBList fieldsForIssueType = new BasicDBList();
                    loggerMaker.infoAndAddToDb("Fetching fields for issueType: " + issueType);

                    try {
                        // Fetch fields for a issue type
                        String requestUrl = String.format(baseUrl + CREATE_ISSUE_FIELD_METADATA_ENDPOINT, projectKey,
                                issueId);
                        String queryParams = "maxResults=200";
                        OriginalHttpRequest request = new OriginalHttpRequest(requestUrl, queryParams, "GET", "",
                                headers, "");
                        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false,
                                new ArrayList<>());
                        String responsePayload = response.getBody();

                        if (response.getStatusCode() > 201 || responsePayload == null) {
                            loggerMaker.errorAndAddToDb(
                                    String.format("Error while fetching issue fields. %s %s Response Code %d",
                                            projectKey, issueType, response.getStatusCode()));
                        } else {
                            fieldsForIssueType = parseJiraFieldsForIssuePayload(responsePayload, projectKey, issueType, fieldSearchMap);
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error while fetching issue fields for project: " + projectKey
                                + ", issueType: " + issueType);
                    }

                    fieldsForProject.put(issueId, fieldsForIssueType);
                }
                return new AbstractMap.SimpleEntry<>(projectKey, fieldsForProject);
            }));
        }

        // Wait for all tasks to finish and fill the map
        for (Future<Map.Entry<String, Map<String, BasicDBList>>> future : futures) {
            try {
                Map.Entry<String, Map<String, BasicDBList>> entry = future.get(60, TimeUnit.SECONDS);
                if (entry != null) {
                    createIssueFieldMetaData.put(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error waiting for Jira metadata fetch task");
            }
        }

        return createIssueFieldMetaData;
    }

    public static Map<String, Map<String, BasicDBList>> getAccountJiraFields() {
        Integer accountId = Context.accountId.get();
        Pair<Map<String, Map<String, BasicDBList>>, Integer> cacheEntry = accountWiseJiraFieldsMap.get(accountId);
        Map<String, Map<String, BasicDBList>> createIssueFieldMetaData;

        if (cacheEntry == null || (Context.now() - cacheEntry.getSecond() > EXPIRY_TIME)) {
            createIssueFieldMetaData = fetchAccountJiraFields();
            
            accountWiseJiraFieldsMap.put(accountId, new Pair<>(createIssueFieldMetaData, Context.now()));
        } else {
            createIssueFieldMetaData = cacheEntry.getFirst();
        }

        return createIssueFieldMetaData;
    }

    public static String buildApiToken(String apiKey){
        if (StringUtils.isEmpty(apiKey)) {
            return null;
        }
        if (apiKey.contains("******")) {
            JiraIntegration jiraIntegration = JiraIntegrationDao.instance.findOne(Filters.empty(), Projections.include(JiraIntegration.API_TOKEN));
            if (jiraIntegration != null) {
                return jiraIntegration.getApiToken();
            }
        }
        return apiKey;
    }

    public static Request.Builder buildBasicRequest(String url, String userEmail, String apiToken, boolean isGzipEnabled) {
        String authHeader = Base64.getEncoder().encodeToString((userEmail + ":" + apiToken).getBytes());
        Request.Builder builder = new Request.Builder();
        builder.addHeader("Authorization", "Basic " + authHeader);
        if (isGzipEnabled) {
            builder.addHeader("Accept-Encoding", "gzip");
        }else{
            builder.addHeader("Accept", "application/json");
        }
        builder = builder.url(url);
        return builder;
    }

    /**
     * Builds HTTP request with Bearer token authentication for Jira Data Center
     * Data Center uses Personal Access Tokens (PAT) with Bearer authentication
     */
    public static Request.Builder buildBearerRequest(String url, String apiToken, boolean isGzipEnabled) {
        Request.Builder builder = new Request.Builder();
        builder.addHeader("Authorization", "Bearer " + apiToken);
        if (isGzipEnabled) {
            builder.addHeader("Accept-Encoding", "gzip");
        } else {
            builder.addHeader("Accept", "application/json");
        }
        builder = builder.url(url);
        return builder;
    }

    public static Request.Builder buildJiraRequest(String url, String userEmail, String apiToken, boolean isGzipEnabled, boolean isDataCenter) {
        if (isDataCenter) {
            return buildBearerRequest(url, apiToken, isGzipEnabled);
        } else {
            return buildBasicRequest(url, userEmail, apiToken, isGzipEnabled);
        }
    }

    public static Request retryWithoutGzipRequest(Request.Builder builder, String url) {
        builder.removeHeader("Accept-Encoding");
        builder = builder.url(url);
        return builder.build();
    }

    public static String handleError(String responsePayload){
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
                return error;
            } catch (Exception e) {
                return "Error parsing response: " + e.getMessage();
            }
        }
        return null;
    }

    public static boolean isFieldError(String responsePayload, String fieldKey) {
        if (responsePayload != null) {
            try {
                BasicDBObject obj = BasicDBObject.parse(responsePayload);
                BasicDBObject errors = (BasicDBObject) obj.get("errors");
                if (errors != null) {
                    String fieldError = errors.getString(fieldKey);
                    return fieldError != null && fieldError.contains("Field '" + fieldKey + "' cannot be set");
                }
            } catch (Exception e) {
                // If parsing fails, return false
            }
        }
        return false;
    }

    public static boolean isLabelsFieldError(String responsePayload) {
        return isFieldError(responsePayload, "labels");
    }

    public static Pair<String, String> getJiraTicketUrlPair(String responsePayload, String jiraBaseUrl) {
        if (StringUtils.isEmpty(responsePayload)) {
            return null;
        }
        BasicDBObject obj = BasicDBObject.parse(responsePayload);
        String jiraTicketKey = obj.getString("key");
        return new Pair<>(jiraBaseUrl + "/browse/" + jiraTicketKey, jiraTicketKey);
    }



    public static BasicDBObject buildPayloadForJiraTicket(String summary, String projectKey, String issueType, Object description, Map<String, Object> additionalIssueFields, Severity severity, JiraIntegration jiraIntegration) {
        BasicDBObject fields = new BasicDBObject();
        fields.put("summary", summary);
        fields.put("project", new BasicDBObject("key", projectKey));
        fields.put("issuetype", new BasicDBObject("id", issueType));
        fields.put("description", description);

        // Apply severity to priority mapping
        if (severity != null && jiraIntegration != null) {
            try {
                Map<String, String> severityToPriorityMap = jiraIntegration.getIssueSeverityToPriorityMap();
                loggerMaker.infoAndAddToDb("Severity: " + severity.name() + ", Priority Map: " + severityToPriorityMap, LogDb.DASHBOARD);

                if (severityToPriorityMap != null && !severityToPriorityMap.isEmpty()) {
                    String priorityId = severityToPriorityMap.get(severity.name());
                    loggerMaker.infoAndAddToDb("Priority ID from map: " + priorityId + " for severity: " + severity.name(), LogDb.DASHBOARD);

                    if (priorityId != null && !priorityId.isEmpty()) {
                        fields.put("priority", new BasicDBObject("id", priorityId));
                        loggerMaker.infoAndAddToDb("Set priority field with ID: " + priorityId, LogDb.DASHBOARD);
                    } else {
                        loggerMaker.infoAndAddToDb("Priority ID is null or empty for severity: " + severity.name(), LogDb.DASHBOARD);
                    }
                } else {
                    loggerMaker.infoAndAddToDb("Priority map is null or empty", LogDb.DASHBOARD);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error setting Jira priority from severity mapping");
            }
        } else {
            loggerMaker.infoAndAddToDb("Severity or JiraIntegration is null - Severity: " + severity + ", JiraIntegration: " + jiraIntegration, LogDb.DASHBOARD);
        }

        if (additionalIssueFields != null) {
            try {
                Object fieldsObj = additionalIssueFields.get("customIssueFields");
                if (fieldsObj != null && fieldsObj instanceof List) {
                    List<?> customIssueFields = (List<?>) fieldsObj;
                    for (Object fieldObj : customIssueFields) {
                        if (fieldObj instanceof Map<?, ?>) {
                            Map<?, ?> customIssueField = (Map<?, ?>) fieldObj;
                            Object fieldName = customIssueField.get("fieldId");
                            if (fieldName == null || !(fieldName instanceof String)) {
                                continue;
                            }
                            String fieldNameStr = (String) fieldName;
                            Object fieldValue = customIssueField.get("fieldValue");
                            fields.put(fieldNameStr, fieldValue);
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return fields;
    }

    public static Object buildJiraDescription(List<BasicDBObject> baseContent, List<BasicDBObject> additionalContent, JiraIntegration.JiraType jiraType) {
        if (jiraType == JiraIntegration.JiraType.DATA_CENTER) {
            // Jira Data Center uses Wiki Markup format
            StringBuilder description = new StringBuilder();

            for (BasicDBObject content : baseContent) {
                String type = content.getString("type");
                if ("paragraph".equals(type)) {
                    String text = content.getString("text");
                    if (text != null && !text.isEmpty()) {
                        description.append(convertMarkdownToWikiMarkup(text)).append("\n\n");
                    }
                }
            }

            for (BasicDBObject content : additionalContent) {
                String type = content.getString("type");
                if ("heading".equals(type)) {
                    int level = content.getInt("level", 1);
                    // Jira Wiki Markup: h1. heading, h2. heading, h3. heading, etc.
                    description.append("h").append(level).append(". ").append(content.getString("text")).append("\n\n");
                } else if ("paragraph".equals(type)) {
                    String text = content.getString("text");
                    if (text != null && !text.isEmpty()) {
                        description.append(convertMarkdownToWikiMarkup(text)).append("\n\n");
                    }
                } else if ("listItem".equals(type)) {
                    // Jira Wiki Markup: * for bullet points
                    description.append("* ").append(content.getString("text")).append("\n");
                }
            }

            return description.toString().trim();
        } else {
            BasicDBList contentList = new BasicDBList();
            contentList.addAll(baseContent);
            contentList.addAll(additionalContent);
            return new BasicDBObject("type", "doc").append("version", 1).append("content", contentList);
        }
    }

    /**
     * Converts Markdown format to Jira Wiki Markup format
     * Handles code blocks, inline code, headings, etc.
     */
    private static String convertMarkdownToWikiMarkup(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Convert code blocks: ```language\ncode\n``` to {code:language}code{code}
        text = text.replaceAll("```(\\w+)\\n([\\s\\S]*?)```", "{code:$1}\n$2{code}");
        // Convert code blocks without language: ```\ncode\n``` to {code}code{code}
        text = text.replaceAll("```\\n([\\s\\S]*?)```", "{code}\n$1{code}");
        // Convert inline code: `code` to {{code}}
        text = text.replaceAll("`([^`]+)`", "{{$1}}");
        // Convert markdown headings: ### Heading to h3. Heading
        text = text.replaceAll("(?m)^### (.+)$", "h3. $1");
        text = text.replaceAll("(?m)^## (.+)$", "h2. $1");
        text = text.replaceAll("(?m)^# (.+)$", "h1. $1");

        return text;
    }

    public static List<BasicDBObject> buildAdditionalIssueFieldsForJira(Info info,
        TestingRunIssues issue, Remediation remediation, JiraIntegration.JiraType jiraType) {

        List<BasicDBObject> contentList = new ArrayList<>();
        boolean isDataCenter = jiraType == JiraIntegration.JiraType.DATA_CENTER;

        try {
            contentList.add(isDataCenter ? addPlainTextHeading(3, "Overview") : addHeading(3, "Overview"));
            addTextSection(contentList, 4, "Severity", issue.getSeverity().name(), isDataCenter);
            addListSection(contentList, 3, "Timelines (UTC)", getIssueTimelines(issue), isDataCenter);

            if (info != null) {
                addTextSection(contentList, 4, "Impact", info.getImpact(), isDataCenter);
                addListSection(contentList, 4, "Tags", info.getTags(), isDataCenter);
                addComplianceSection(contentList, info.getCompliance(), isDataCenter);
                addListSection(contentList, 4, "CWE", info.getCwe(), isDataCenter);
                addListSection(contentList, 4, "CVE", info.getCve(), isDataCenter);
                addListSection(contentList, 4, "References", info.getReferences(), isDataCenter);

                String remediationText = StringUtils.isNotBlank(info.getRemediation())
                    ? info.getRemediation()
                    : remediation != null ? remediation.getRemediationText() : "No remediation provided.";

                addTextSection(contentList, 3, "Remediation", remediationText, isDataCenter);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,
                "Error while adding additional issue details in Jira Payload: " + e.getMessage(),
                LoggerMaker.LogDb.DASHBOARD);
        }
        return contentList;
    }

    private static List<String> getIssueTimelines(TestingRunIssues issue) {
        List<String> timelines = new ArrayList<>();

        timelines.add("Issue Found on " + DateUtils.convertToUtcLocaleDate(issue.getCreationTime() * 1000L));
        if (issue.getTestRunIssueStatus() == TestRunIssueStatus.IGNORED) {
            timelines.add(
                "Issue marked as IGNORED on " + DateUtils.convertToUtcLocaleDate(issue.getLastUpdated() * 1000L)
                    + ". Reason: " + StringUtils.defaultIfBlank(issue.getIgnoreReason(), "No reason provided."));
        }
        if (issue.getTestRunIssueStatus() == TestRunIssueStatus.FIXED) {
            timelines.add(
                "Issue marked as FIXED on " + DateUtils.convertToUtcLocaleDate(issue.getLastUpdated() * 1000L));
        }
        return timelines;
    }

    private static void addTextSection(List<BasicDBObject> list, int level, String heading, String text, boolean isDataCenter) {
        list.add(isDataCenter ? addPlainTextHeading(level, heading) : addHeading(level, heading));
        list.add(isDataCenter ? addPlainTextParagraph(text) : addParagraph(text));
    }

    private static void addListSection(List<BasicDBObject> list, int level, String heading, List<String> items, boolean isDataCenter) {
        if (CollectionUtils.isNotEmpty(items)) {
            list.add(isDataCenter ? addPlainTextHeading(level, heading) : addHeading(level, heading));
            if (isDataCenter) {
                for (String item : items) {
                    list.add(addPlainTextListItem(item));
                }
            } else {
                list.add(addList(items));
            }
        }
    }

    private static void addComplianceSection(List<BasicDBObject> list, ComplianceMapping compliance, boolean isDataCenter) {
        if (compliance == null || MapUtils.isEmpty(compliance.getMapComplianceToListClauses())) return;

        list.add(isDataCenter ? addPlainTextHeading(4, "Compliance") : addHeading(4, "Compliance"));
        for (Map.Entry<String, List<String>> entry : compliance.getMapComplianceToListClauses().entrySet()) {
            list.add(isDataCenter ? addPlainTextHeading(5, entry.getKey()) : addHeading(5, entry.getKey()));
            if (CollectionUtils.isNotEmpty(entry.getValue())) {
                if (isDataCenter) {
                    for (String item : entry.getValue()) {
                        list.add(addPlainTextListItem(item));
                    }
                } else {
                    list.add(addList(entry.getValue()));
                }
            } else {
                list.add(isDataCenter ? addPlainTextParagraph("No clauses available.") : addParagraph("No clauses available."));
            }
        }
    }

    private static BasicDBObject addHeading(int level, String text) {
        return new BasicDBObject("type", "heading")
            .append("attrs", new BasicDBObject("level", level))
            .append("content", Collections.singletonList(
                new BasicDBObject("type", "text").append("text", StringUtils.defaultString(text, ""))));
    }

    private static BasicDBObject addParagraph(String text) {
        return new BasicDBObject("type", "paragraph")
            .append("content", Collections.singletonList(
                new BasicDBObject("type", "text").append("text", StringUtils.defaultString(text, ""))));
    }

    private static BasicDBObject addList(List<String> items) {
        BasicDBList listItems = new BasicDBList();
        for (String item : items) {
            listItems.add(addListItem(item));
        }
        return new BasicDBObject("type", "bulletList")
            .append("content", listItems);
    }

    private static BasicDBObject addListItem(String text) {
        return new BasicDBObject("type", "listItem")
            .append("content", Collections.singletonList(
                new BasicDBObject("type", "paragraph")
                    .append("content",
                        Collections.singletonList(
                            new BasicDBObject("type", "text").append("text", StringUtils.defaultString(text, ""))))
            ));
    }

    private static BasicDBObject addPlainTextHeading(int level, String text) {
        return new BasicDBObject("type", "heading")
            .append("level", level)
            .append("text", StringUtils.defaultString(text, ""));
    }

    private static BasicDBObject addPlainTextParagraph(String text) {
        return new BasicDBObject("type", "paragraph")
            .append("text", StringUtils.defaultString(text, ""));
    }

    private static BasicDBObject addPlainTextListItem(String text) {
        return new BasicDBObject("type", "listItem")
            .append("text", StringUtils.defaultString(text, ""));
    }
}