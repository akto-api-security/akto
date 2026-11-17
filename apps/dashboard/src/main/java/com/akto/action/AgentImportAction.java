package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.akto.testing.HostValidator;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.utils.TestRoleAuthUtils;
import com.akto.utils.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class AgentImportAction extends UserAction{
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentImportAction.class, LoggerMaker.LogDb.DASHBOARD);
    
    @Getter @Setter
    private String url;
    
    @Getter @Setter
    private String testRoleId;
    
    @Getter @Setter
    private String requestBody;

    @Getter @Setter
    private Map<String, String> customHeaders;

    @Getter @Setter
    private String collectionName;

    @Getter @Setter
    private String customResponseBody;

    @Getter @Setter
    private Integer customResponseStatusCode;

    @Getter @Setter
    private Map<String, String> customResponseHeaders;

    @Getter @Setter
    private String miniTestingName;

    private int apiCollectionId;
    
    public String importFromUrl() {
        try {
            if (StringUtils.isEmpty(url)) {
                addActionError("URL is required");
                return Action.ERROR.toUpperCase();
            }

            loggerMaker.info("Starting URL import for: " + url, LoggerMaker.LogDb.DASHBOARD);

            String message = executeApiCall();

            boolean collectionCreated = createApiCollectionFromUrl();
            try {
                saveToDatabase(Arrays.asList(message));
            } catch (Exception e) {
                if (collectionCreated) {
                    ApiCollectionsDao.instance.deleteAll(Filters.eq(Constants.ID, apiCollectionId));
                }
                throw e;
            }

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.error("Error during URL import: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            addActionError("Error importing from URL: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
    
    private boolean createApiCollectionFromUrl() {
        try {
            String name = !StringUtils.isEmpty(collectionName)
                    ? collectionName.trim()
                    : HttpRequestResponseUtils.extractHostFromUrl(url);

            int id = name.hashCode();

            // Check if collection already exists for this host
            ApiCollection existingCollection = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, id));
            if (existingCollection != null) {
                apiCollectionId = existingCollection.getId();
                loggerMaker.info("Found existing API collection with ID: " + apiCollectionId + " for name: " + name, LoggerMaker.LogDb.DASHBOARD);
                // Add Gen AI tag if not already present
                addGenAiTagIfNeeded(existingCollection);
                return false;
            }
            
            // Create new collection using DAO
            ApiCollection newCollection = ApiCollection.createManualCollection(id, name);

            // Insert the new collection
            ApiCollectionsDao.instance.insertOne(newCollection);
            apiCollectionId = newCollection.getId();
            
            // Add Gen AI tag to the new collection
            addGenAiTagToCollection(apiCollectionId);
            loggerMaker.info("Created new API collection with ID: " + apiCollectionId + " for name: " + name, LoggerMaker.LogDb.DASHBOARD);
            return true;
        } catch (Exception e) {
            loggerMaker.error("Error creating API collection from URL: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            throw new RuntimeException("Failed to create API collection", e);
        }
    }
    
    private void addGenAiTagIfNeeded(ApiCollection collection) {
        try {
            // Check if Gen AI tag already exists
            List<CollectionTags> existingTags = collection.getTagsList();
            boolean hasGenAiTag = existingTags != null && existingTags.stream()
                .anyMatch(tag -> Constants.AKTO_GEN_AI_TAG.equals(tag.getKeyName()));
                
            if (!hasGenAiTag) {
                addGenAiTagToCollection(collection.getId());
            }
        } catch (Exception e) {
            loggerMaker.error("Error checking/adding Gen AI tag: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }
    }
    
    private void addGenAiTagToCollection(int collectionId) {
        try {
            List<CollectionTags> tagsList = Collections.singletonList(
                new CollectionTags(Context.now(), Constants.AKTO_GEN_AI_TAG, "Gen AI Import", CollectionTags.TagSource.USER)
            );
            
            FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
            updateOptions.returnDocument(ReturnDocument.AFTER);
            
            ApiCollectionsDao.instance.getMCollection()
                .findOneAndUpdate(
                    Filters.eq(ApiCollection.ID, collectionId), 
                    Updates.addToSet(ApiCollection.TAGS_STRING, tagsList.get(0)), 
                    updateOptions
                );
                
            loggerMaker.info("Added Gen AI tag to collection ID: " + collectionId, LoggerMaker.LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.error("Error adding Gen AI tag to collection: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
        }
    }
    
    private String executeApiCall() throws Exception {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Arrays.asList("application/json"));

        String method = !StringUtils.isEmpty(requestBody) ? "POST" : "GET";
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", method, requestBody, headers, "HTTP/1.1");

        if (customHeaders != null) {
            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                if (!StringUtils.isEmpty(entry.getKey())) {
                    headers.put(entry.getKey(), Arrays.asList(entry.getValue()));
                }
            }
        }

        OriginalHttpResponse response;
        if (!StringUtils.isEmpty(customResponseBody)) {
            // User supplied the expected response directly (e.g. the target is unreachable from
            // the dashboard's network) — skip the live call and save that response as-is.
            loggerMaker.info("Using user-provided custom response for URL: " + url, LoggerMaker.LogDb.DASHBOARD);
            Map<String, List<String>> customRespHeaders = new HashMap<>();
            if (customResponseHeaders != null) {
                for (Map.Entry<String, String> entry : customResponseHeaders.entrySet()) {
                    if (!StringUtils.isEmpty(entry.getKey())) {
                        customRespHeaders.put(entry.getKey(), Arrays.asList(entry.getValue()));
                    }
                }
            }
            int statusCode = customResponseStatusCode != null ? customResponseStatusCode : 200;
            response = new OriginalHttpResponse(customResponseBody, customRespHeaders, statusCode);
        } else {
            try {
                OriginalHttpRequest requestToSend = request;
                if (!StringUtils.isEmpty(testRoleId)) {
                    // Auth goes on a copy so the token/cookie is sent but never persisted in the saved sample.
                    requestToSend = request.copy();
                    TestRoleAuthUtils.applyTestRoleAuth(requestToSend, testRoleId);
                }

                if (!StringUtils.isEmpty(miniTestingName)) {
                    // Target is internal to the customer's network — have their mini-testing send it.
                    loggerMaker.info("Sending request via mini-testing " + miniTestingName + " for URL: " + url, LoggerMaker.LogDb.DASHBOARD);
                    response = com.akto.testing.Utils.runRequestOnHybridTesting(requestToSend, miniTestingName);
                } else {
                    response = ApiExecutor.sendRequest(requestToSend, false, null, false, new ArrayList<>(), HostValidator.SKIP_SSRF_CHECK);
                }
                if (response == null || response.getStatusCode() <= 0) {
                    throw new Exception("No response received from URL: " + url);
                }
                loggerMaker.info("Successfully received response from URL", LoggerMaker.LogDb.DASHBOARD);
            } catch (Exception e) {
                loggerMaker.error("Error calling URL: " + url + " - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                throw e;
            }
        }

        try {
            // Remove x-akto-ignore header before converting to message
            headers.remove(Constants.AKTO_IGNORE_FLAG);

            return convertToMessage(url, headers, requestBody, response);
        } catch (Exception e) {
            loggerMaker.error("Error during async API execution: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            throw e;
        }
    }
    
    private final static ObjectMapper mapper = new ObjectMapper();

    private String convertToMessage(String url, Map<String, List<String>> requestHeaders, String requestBody, OriginalHttpResponse response) throws Exception {
        Map<String, String> result = new HashMap<>();
        
        Map<String, String> requestHeadersFlat = flattenHeaders(requestHeaders);
        Map<String, String> responseHeadersFlat = flattenHeaders(response.getHeaders());
        
        result.put("akto_account_id", Context.accountId.get() + "");
        result.put("path", url);
        result.put("requestHeaders", mapper.writeValueAsString(requestHeadersFlat));
        result.put("method", requestBody != null ? "POST" : "GET");
        result.put("requestPayload", requestBody != null ? requestBody : "");
        result.put("responsePayload", response.getBody() != null ? response.getBody() : "");
        result.put("ip", "null");
        result.put("time", Context.now() + "");
        result.put("statusCode", response.getStatusCode() + "");
        result.put("type", "HTTP/1.1");

        // statusCode <= 0 means the call never got a real response (SSRF block, DNS/connection
        // failure, etc). The runtime pipeline's filterHttpResponseParams() drops any non-2xx/302
        // status code unless source == POSTMAN, which is the existing carve-out for "no response"
        // imports (e.g. Postman collections without live responses) — reuse it here so the
        // attempted call still shows up instead of being silently discarded downstream.
        boolean isNoResponse = response.getStatusCode() <= 0;
        result.put("status", isNoResponse ? "ERROR" : "OK");
        result.put("contentType", getContentTypeFromHeaders(response.getHeaders()));
        result.put("source", (isNoResponse ? HttpResponseParams.Source.POSTMAN : HttpResponseParams.Source.HAR).name());
        
        result.put("responseHeaders", mapper.writeValueAsString(responseHeadersFlat));
        
        return mapper.writeValueAsString(result);
    }
    
    private Map<String, String> flattenHeaders(Map<String, List<String>> headers) {
        Map<String, String> flattened = new HashMap<>();
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    flattened.put(entry.getKey(), entry.getValue().get(0));
                }
            }
        }
        return flattened;
    }
    
    private String getContentTypeFromHeaders(Map<String, List<String>> headers) {
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if ("Content-Type".equalsIgnoreCase(entry.getKey()) && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
        }
        return "application/json";
    }
    
    private void saveToDatabase(List<String> messages) throws Exception {
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topic == null) topic = "akto.api.logs";
        
        loggerMaker.info("Saving imported data to database for collection: " + apiCollectionId, LoggerMaker.LogDb.DASHBOARD);
        
        Utils.pushDataToKafka(apiCollectionId, topic, messages, new ArrayList<>(), true, true, true);
        
        loggerMaker.info("Successfully saved imported data to database", LoggerMaker.LogDb.DASHBOARD);
    }
}
