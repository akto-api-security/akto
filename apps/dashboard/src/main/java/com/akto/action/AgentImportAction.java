package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.log.LoggerMaker;
import com.akto.testing.ApiExecutor;
import com.akto.testing.HostValidator;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.utils.TestRoleAuthUtils;
import com.akto.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

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

    // GET or POST; when empty, POST if there is a request body, else GET
    @Getter @Setter
    private String method;

    private int apiCollectionId;

    // request headers of the agent's WebSocket connection, reused for an event sent to it by agent name
    private Map<String, List<String>> connectionHeaders = new HashMap<>();

    public String importFromUrl() {
        try {
            // no URL with an event: send it to the WebSocket connection imported earlier under this agent name
            if (StringUtils.isEmpty(url) && !StringUtils.isEmpty(collectionName) && !StringUtils.isEmpty(requestBody)) {
                url = loadConnectionUrl(collectionName.trim().hashCode());
                if (url == null) {
                    addActionError("No WebSocket connection for this agent, import its WebSocket URL first");
                    return Action.ERROR.toUpperCase();
                }
            }

            if (StringUtils.isEmpty(url)) {
                addActionError("URL is required");
                return Action.ERROR.toUpperCase();
            }

            if (isWebSocketUrl(url) && StringUtils.isEmpty(miniTestingName) && StringUtils.isEmpty(customResponseBody)) {
                addActionError("WebSocket agents are reached via a mini-testing module, select one");
                return Action.ERROR.toUpperCase();
            }

            loggerMaker.info("Starting URL import for: " + url, LoggerMaker.LogDb.DASHBOARD);

            if (isWebSocketUrl(url) && StringUtils.isEmpty(requestBody)) {
                return importWebSocketConnection();
            }

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
            String name = !StringUtils.isEmpty(collectionName)
                    ? collectionName.trim()
                    : HttpRequestResponseUtils.extractHostFromUrl(httpScheme(url));

            int id = name.hashCode();

            // Check if collection already exists for this host
            ApiCollection existingCollection = ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, id));
            if (existingCollection != null) {
                apiCollectionId = existingCollection.getId();
                loggerMaker.info("Found existing API collection with ID: " + apiCollectionId + " for name: " + name, LoggerMaker.LogDb.DASHBOARD);
                // Add Gen AI tag if not already present
                addGenAiTagIfNeeded(existingCollection);
                return;
            }
            
            // Create new collection using DAO
            ApiCollection newCollection = ApiCollection.createManualCollection(id, name);

            // Insert the new collection
            ApiCollectionsDao.instance.insertOne(newCollection);
            apiCollectionId = newCollection.getId();
            
            // Add Gen AI tag to the new collection
            addGenAiTagToCollection(apiCollectionId);
            loggerMaker.info("Created new API collection with ID: " + apiCollectionId + " for name: " + name, LoggerMaker.LogDb.DASHBOARD);
            
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
        boolean webSocket = isWebSocketUrl(url);
        Map<String, List<String>> headers = new HashMap<>(connectionHeaders);
        headers.put("Content-Type", Arrays.asList("application/json"));

        String method = !StringUtils.isEmpty(this.method) ? this.method.toUpperCase()
                : !StringUtils.isEmpty(requestBody) ? "POST" : "GET";
        String body = "GET".equals(method) && !webSocket ? null : requestBody;
        // a WebSocket event is sent by mini-testing over the agent's connection
        OriginalHttpRequest request = webSocket ? webSocketRequest(url, requestBody, headers)
                : new OriginalHttpRequest(url, "", method, body, headers, "HTTP/1.1");

        addCustomHeaders(headers);

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
            response = sendRequest(request, 5 * 60);
        }

        try {
            // Remove x-akto-ignore header before converting to message
            headers.remove(Constants.AKTO_IGNORE_FLAG);

            // a WebSocket event and the agent's reply are saved as request/response of "WS /<event type>",
            // with the frames as events, as WebSocket discovery and HAR imports save them
            String message = webSocket
                    ? convertToMessage("/" + messageType(requestBody), headers, "WS", requestBody, response, WEBSOCKET_TYPE,
                            webSocketEvents(requestBody, response))
                    : convertToMessage(url, headers, method, body, response, "HTTP/1.1", null);
            saveToDatabase(Arrays.asList(message));
        } catch (Exception e) {
            loggerMaker.error("Error during async API execution: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
        }
    }

    private OriginalHttpResponse sendRequest(OriginalHttpRequest request, int hybridTimeoutSeconds) {
        try {
            if (!StringUtils.isEmpty(testRoleId)) {
                // Auth goes on the request's own headers, so it's also in the saved sample: a test with the role
                // replaces auth headers that are present in the sample
                TestRoleAuthUtils.applyTestRoleAuth(request, testRoleId);
            }

            OriginalHttpResponse response;
            if (!StringUtils.isEmpty(miniTestingName)) {
                // Target is internal to the customer's network — have their mini-testing send it.
                loggerMaker.info("Sending request via mini-testing " + miniTestingName + " for URL: " + url, LoggerMaker.LogDb.DASHBOARD);
                response = com.akto.testing.Utils.runRequestOnHybridTesting(request, miniTestingName, hybridTimeoutSeconds);
            } else {
                response = ApiExecutor.sendRequest(request, false, null, false, new ArrayList<>(), HostValidator.SKIP_SSRF_CHECK);
            }
            if (response == null) {
                throw new Exception("No response received from URL: " + url);
            }
            loggerMaker.info("Successfully received response from URL", LoggerMaker.LogDb.DASHBOARD);
            return response;
        } catch (Exception e) {
            // Request never reached the target (SSRF block, DNS/connection failure, timeout, etc).
            // Save a placeholder entry so the attempted call still shows up instead of vanishing silently.
            loggerMaker.error("Error calling URL: " + url + " - " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            return new OriginalHttpResponse("", new HashMap<>(), 0);
        }
    }

    private void addCustomHeaders(Map<String, List<String>> headers) {
        if (customHeaders != null) {
            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                if (!StringUtils.isEmpty(entry.getKey())) {
                    headers.put(entry.getKey(), Arrays.asList(entry.getValue()));
                }
            }
        }
    }

    private static final String WEBSOCKET_TYPE = "WEBSOCKET";
    // how long the user waits for mini-testing to check a WebSocket connection
    private static final int CONNECTION_CHECK_TIMEOUT_SECONDS = 60;

    /*
     * A WebSocket URL without an event is the agent's connection string, which its events and WebSocket tests
     * connect to. Mini-testing does the handshake first and it is saved only if accepted (101), so a wrong URL
     * or an expired session shows up here.
     */
    private String importWebSocketConnection() throws Exception {
        Map<String, List<String>> headers = new HashMap<>();
        addCustomHeaders(headers);
        // with a custom response there's no live call, so an accepted handshake without headers is saved
        OriginalHttpResponse handshake = new OriginalHttpResponse("", new HashMap<>(), 101);
        if (StringUtils.isEmpty(customResponseBody)) {
            // saved as mini-testing returned it: 101 with the server's headers and no body, as a connection string's must be
            handshake = sendRequest(webSocketRequest(url, "", headers), CONNECTION_CHECK_TIMEOUT_SECONDS);
            if (handshake.getStatusCode() != 101) {
                addActionError(handshake.getStatusCode() > 0
                        ? "WebSocket handshake failed with HTTP " + handshake.getStatusCode()
                        : "No response from mini-testing " + miniTestingName);
                return Action.ERROR.toUpperCase();
            }
        }

        createApiCollectionFromUrl();
        // a handshake's own headers, as a captured one has them: the inventory lists an endpoint by its params
        headers.put("host", Arrays.asList(new java.net.URI(httpScheme(url)).getRawAuthority()));
        headers.put("upgrade", Arrays.asList("websocket"));
        headers.put("connection", Arrays.asList("Upgrade"));
        saveToDatabase(Arrays.asList(convertToMessage(httpScheme(url), headers, "GET", "", handshake, WEBSOCKET_TYPE, null)));
        return Action.SUCCESS.toUpperCase();
    }

    // sent to mini-testing as the http(s) handshake URL marked WEBSOCKET; an empty body means only the handshake
    private static OriginalHttpRequest webSocketRequest(String url, String body, Map<String, List<String>> headers) {
        return new OriginalHttpRequest(httpScheme(url), "", "GET", body, headers, WEBSOCKET_TYPE);
    }

    // the agent's connection string as a ws(s) URL, keeping its request headers for the event; null if none
    String loadConnectionUrl(int collectionId) throws Exception {
        ApiInfo connection = ApiInfoDao.instance.findOne(Filters.and(
                Filters.eq(ApiInfo.ID_API_COLLECTION_ID, collectionId), Filters.eq(ApiInfo.IS_CONNECTION_STRING, true)));
        if (connection == null) {
            return null;
        }
        SampleData sampleData = SampleDataDao.instance.fetchSampleDataForApi(collectionId, connection.getId().getUrl(), connection.getId().getMethod());
        if (sampleData != null && sampleData.getSamples() != null && !sampleData.getSamples().isEmpty()) {
            String sampleHeaders = BasicDBObject.parse(sampleData.getSamples().get(0)).getString("requestHeaders");
            if (sampleHeaders != null) {
                Map<String, Object> headerMap = mapper.readValue(sampleHeaders, Map.class);
                for (Map.Entry<String, Object> header : headerMap.entrySet()) {
                    connectionHeaders.put(header.getKey(), new ArrayList<>(Arrays.asList(String.valueOf(header.getValue()))));
                }
            }
        }
        return connection.getId().getUrl().replaceFirst("(?i)^http(s?)://", "ws$1://");
    }

    private static boolean isWebSocketUrl(String url) {
        return url != null && url.trim().toLowerCase().matches("^wss?://.*");
    }

    // ws(s) isn't a java.net.URL protocol, so the handshake URL is stored as http(s)
    private static String httpScheme(String url) {
        return url.trim().replaceFirst("(?i)^ws(s?)://", "http$1://");
    }

    // a WebSocket event's endpoint is named after its type, as WebSocket discovery does
    static String messageType(String event) {
        try {
            JsonNode node = mapper.readTree(event);
            for (String key : Arrays.asList("type", "messageType", "event")) {
                if (node != null && node.hasNonNull(key)) {
                    return node.get(key).asText();
                }
            }
        } catch (Exception e) {
            // not JSON
        }
        return "data_frame";
    }

    // the event sent (incoming, as the server sees it) and each frame of the agent's reply (outgoing); mini-testing
    // returns several reply frames as a JSON array
    private static String webSocketEvents(String event, OriginalHttpResponse response) throws Exception {
        List<Map<String, Object>> events = new ArrayList<>();
        events.add(webSocketEvent("incoming", event));
        String reply = response.getBody();
        // any other status is mini-testing's error, not a frame
        if (response.getStatusCode() == 200 && !StringUtils.isEmpty(reply)) {
            JsonNode frames = null;
            try {
                frames = mapper.readTree(reply);
            } catch (Exception e) {
                // a single frame that isn't JSON
            }
            if (frames != null && frames.isArray()) {
                for (JsonNode frame : frames) {
                    events.add(webSocketEvent("outgoing", frame.isTextual() ? frame.asText() : frame.toString()));
                }
            } else {
                events.add(webSocketEvent("outgoing", reply));
            }
        }
        return mapper.writeValueAsString(events);
    }

    private static Map<String, Object> webSocketEvent(String direction, String payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("direction", direction);
        event.put("opcode", 1);
        event.put("payload", payload);
        event.put("fin", true);
        event.put("timestamp", Context.now());
        return event;
    }

    private final static ObjectMapper mapper = new ObjectMapper();

    private String convertToMessage(String url, Map<String, List<String>> requestHeaders, String method, String requestBody, OriginalHttpResponse response, String type, String events) throws Exception {
        Map<String, String> result = new HashMap<>();
        
        Map<String, String> requestHeadersFlat = flattenHeaders(requestHeaders);
        Map<String, String> responseHeadersFlat = flattenHeaders(response.getHeaders());
        
        result.put("akto_account_id", Context.accountId.get() + "");
        result.put("path", url);
        result.put("requestHeaders", mapper.writeValueAsString(requestHeadersFlat));
        result.put("method", method);
        result.put("requestPayload", requestBody != null ? requestBody : "");
        result.put("responsePayload", response.getBody() != null ? response.getBody() : "");
        result.put("ip", "null");
        result.put("time", Context.now() + "");
        result.put("statusCode", response.getStatusCode() + "");
        result.put("type", type);
        if (events != null) {
            result.put("events", events);
        }

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
