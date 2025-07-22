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

import org.apache.commons.lang3.StringUtils;

import com.akto.dao.JiraIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.jira_integration.JiraIntegration;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.akto.util.Pair;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import okhttp3.Request;

public class Utils {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Utils.class, LogDb.DASHBOARD);

    private static final String CREATE_ISSUE_FIELD_METADATA_ENDPOINT = "/rest/api/3/issue/createmeta/%s/issuetypes/%s"; 
    private static final String FIELD_SEARCH_ENDPOINT = "/rest/api/3/field/search";

    //Caching for Account Wise Jira Integration Issue Creation Fields
    private static final ConcurrentHashMap<Integer, Pair<Map<String, Map<String, BasicDBList>>, Integer>> accountWiseJiraFieldsMap = new ConcurrentHashMap<>();
    private static final int EXPIRY_TIME = 30 * 60; // 30 minutes
    //private static final int EXPIRY_TIME = 30; // 15 seconds 

    // Thread pool for making calls to jira in parallel
    private static final ExecutorService multiFieldPool = Executors.newFixedThreadPool(10);

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
        int startAt = 0;
        int maxResults = 50;
        String fieldSearchRequestUrl = baseUrl + FIELD_SEARCH_ENDPOINT;
        String queryParamsFormatStr = "startAt=%d&expand=isUnscreenable&expand=isLocked";
        Map<String, BasicDBObject> fieldSearchMap = new HashMap<>();
        List<Future<Map<String, BasicDBObject>>> fieldSearchFutures = new ArrayList<>();

        // Initial request to get the total number of fields and the first page of
        // results
        try {
            String queryParams = String.format(queryParamsFormatStr, startAt);
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

            BasicDBObject payloadObj;
            try {
                payloadObj = BasicDBObject.parse(responsePayload);
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
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error while making jira field search request. ");
        }

        // Fetch the remaining fields search pages in parallel
        while (true) {
            startAt += maxResults;
            if (startAt >= total) {
                break;
            }

            String queryParams = String.format(queryParamsFormatStr, startAt);
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
                        BasicDBObject payloadObj;
                        try {
                            payloadObj = BasicDBObject.parse(responsePayload);

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
                                    paginatedFieldSearchMap.put(id, value);
                                }
                            }

                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error while parsing jira field search results.");
                        }
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while making jira field search request. ");
                }

                return paginatedFieldSearchMap;
            }));
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

        Map<String, Map<String, BasicDBList>> createIssueFieldMetaData = new HashMap<>();
        
        List<Future<Map.Entry<String, Map<String, BasicDBList>>>> futures = new ArrayList<>();

        for (Map.Entry<String, List<BasicDBObject>> entry : projectIdsMap.entrySet()) {
            String projectKey = entry.getKey();
            List<BasicDBObject> issueTypes = entry.getValue();

            futures.add(multiFieldPool.submit(() -> {
                Context.accountId.set(accountId);
                Map<String, BasicDBList> fieldsForProject = new HashMap<>();
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
                            continue;
                        }

                        BasicDBObject payloadObj;
                        try {
                            payloadObj = BasicDBObject.parse(responsePayload);
                            BasicDBList issueFields = (BasicDBList) payloadObj.get("fields");

                            if (issueFields == null)
                                continue;
                            for (Object issueField : issueFields) {
                                if (issueField == null)
                                    continue;

                                BasicDBObject issueFieldBasicDBObject = (BasicDBObject) issueField;
                                String fieldId = issueFieldBasicDBObject.getString("fieldId");
                                String fieldName = issueFieldBasicDBObject.getString("name");

                                if (fieldId == null || fieldName == null)
                                    continue;

                                // check if fieldId is present in fieldSearchMap
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
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error while parsing issue fields response for project: "
                                    + projectKey + ", issueType: " + issueType);
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

    public static Pair<String, String> getJiraTicketUrlPair(String responsePayload, String jiraBaseUrl) {
        if (StringUtils.isEmpty(responsePayload)) {
            return null;
        }
        BasicDBObject obj = BasicDBObject.parse(responsePayload);
        String jiraTicketKey = obj.getString("key");
        return new Pair<>(jiraBaseUrl + "/browse/" + jiraTicketKey, jiraTicketKey);
    }

    public static BasicDBObject buildPayloadForJiraTicket(String summary, String projectKey, String issueType, BasicDBList contentList, Map<String, Object> additionalIssueFields) {
        BasicDBObject fields = new BasicDBObject();
        fields.put("summary", summary);
        fields.put("project", new BasicDBObject("key", projectKey));
        fields.put("issuetype", new BasicDBObject("id", issueType));
        fields.put("description", new BasicDBObject("type", "doc").append("version", 1).append("content", contentList));

        if (additionalIssueFields != null) {
            try {
                Object fieldsObj = additionalIssueFields.get("mandatoryCreateJiraIssueFields");
                if (fieldsObj != null && fieldsObj instanceof List) {
                    List<?> mandatoryCreateJiraIssueFields = (List<?>) fieldsObj;
                    for (Object fieldObj : mandatoryCreateJiraIssueFields) {
                        if (fieldObj instanceof Map<?, ?>) {
                            Map<?, ?> mandatoryField = (Map<?, ?>) fieldObj;
                            Object fieldName = mandatoryField.get("fieldId");
                            if (fieldName == null || !(fieldName instanceof String)) {
                                continue;
                            }
                            String fieldNameStr = (String) fieldName;
                            Object fieldValue = mandatoryField.get("fieldValue");
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
}