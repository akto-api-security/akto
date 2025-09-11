package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.akto.util.Constants;
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
import org.bson.types.ObjectId;

import java.net.URL;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AgentImportAction extends UserAction{
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentImportAction.class, LoggerMaker.LogDb.DASHBOARD);
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    
    @Getter @Setter
    private String url;
    
    @Getter @Setter
    private String testRoleId;
    
    @Getter @Setter
    private String requestBody;
    
    private int apiCollectionId;
    
    public String importFromUrl() {
        try {
            if (StringUtils.isEmpty(url)) {
                addActionError("URL is required");
                return Action.ERROR.toUpperCase();
            }

            loggerMaker.info("Starting URL import for: " + url, LoggerMaker.LogDb.DASHBOARD);

            // Create API collection based on host name
            createApiCollectionFromUrl();

            // Execute API call in separate thread
            int accountId = Context.accountId.get();
            executorService.schedule(new Runnable() {
                public void run() {
                    Context.accountId.set(accountId);
                    executeApiCall();
                }
            }, 0, TimeUnit.SECONDS);

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.error("Error during URL import: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            addActionError("Error importing from URL: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }
    
    private void createApiCollectionFromUrl() {
        try {
            URL parsedUrl = new URL(url);
            String hostName = parsedUrl.getHost();
            if (hostName == null || hostName.trim().isEmpty()) {
                throw new Exception("Invalid URL: no host found");
            }
            
            hostName = hostName.toLowerCase().trim();
            int id = hostName.hashCode();

            // Check if collection already exists for this host
            ApiCollection existingCollection = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, id));
            if (existingCollection != null) {
                apiCollectionId = existingCollection.getId();
                loggerMaker.info("Found existing API collection with ID: " + apiCollectionId + " for host: " + hostName, LoggerMaker.LogDb.DASHBOARD);
                // Add Gen AI tag if not already present
                addGenAiTagIfNeeded(existingCollection);
                return;
            }
            
            // Create new collection using DAO
            ApiCollection newCollection = ApiCollection.createManualCollection(id, hostName);

            // Insert the new collection
            ApiCollectionsDao.instance.insertOne(newCollection);
            apiCollectionId = newCollection.getId();
            
            // Add Gen AI tag to the new collection
            addGenAiTagToCollection(apiCollectionId);
            loggerMaker.info("Created new API collection with ID: " + apiCollectionId + " for host: " + hostName, LoggerMaker.LogDb.DASHBOARD);
            
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
    
    private void executeApiCall() {
        try {
            Map<String, List<String>> headers = new HashMap<>();
            headers.put("Content-Type", Arrays.asList("application/json"));

            String method = !StringUtils.isEmpty(requestBody) ? "POST" : "GET";
            OriginalHttpRequest request = new OriginalHttpRequest(url, "", method, requestBody, headers, "HTTP/1.1");
            
            if (!StringUtils.isEmpty(testRoleId)) {
                TestRoles testRole = TestRolesDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(testRoleId)));
                if (testRole != null) {
                    AuthMechanism authMechanism = testRole.findDefaultAuthMechanism();
                    authMechanism.addAuthToRequest(request, false);
                }
            }
            
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, false, null, false, new ArrayList<>(), false);

            if (response == null) {
                loggerMaker.error("Failed to get response from URL: " + url, LoggerMaker.LogDb.DASHBOARD);
                return;
            }

            loggerMaker.info("Successfully received response from URL", LoggerMaker.LogDb.DASHBOARD);
            
            // Remove x-akto-ignore header before converting to message
            headers.remove(Constants.AKTO_IGNORE_FLAG);

            String message = convertToMessage(url, headers, requestBody, response);
            saveToDatabase(Arrays.asList(message));

        } catch (Exception e) {
            loggerMaker.error("Error during async API execution: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
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
        result.put("responsePayload", response.getBody());
        result.put("ip", "null");
        result.put("time", Context.now() + "");
        result.put("statusCode", response.getStatusCode() + "");
        result.put("type", "HTTP/1.1");
        result.put("status", "OK");
        result.put("contentType", getContentTypeFromHeaders(response.getHeaders()));
        result.put("source", HttpResponseParams.Source.HAR.name());
        
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
