package com.akto.hybrid_runtime;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.alibaba.fastjson2.JSON;

import java.util.*;

public class WebSocketEventProcessor {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(WebSocketEventProcessor.class, LogDb.RUNTIME);
    private static final String WEBSOCKET_TYPE = "WEBSOCKET";
    
    public static List<HttpResponseParams> processWebSocketMessage(Map<String, Object> jsonMap) {
        List<HttpResponseParams> responseParamsList = new ArrayList<>();
        
        try {

            if (!jsonMap.containsKey("events") && jsonMap.containsKey("path")) {
                HttpResponseParams handshakeParam = buildHandshakeResponseParam(jsonMap);
                if (handshakeParam != null) {
                    responseParamsList.add(handshakeParam);
                }
                return responseParamsList;
            }


            if (jsonMap.containsKey("events") && !jsonMap.containsKey("path")) {
                if (!hasCompleteFrames(jsonMap)) {
                    loggerMaker.debug("Skipping WebSocket events - fragmented/incomplete frame data without path");
                    return responseParamsList;
                }
            }

            List<WebSocketMessageParser.WebSocketEvent> events = WebSocketMessageParser.parseWebSocketEvents(jsonMap);
            
            if (events.isEmpty()) {
                loggerMaker.debug("No valid WebSocket events found in message (control frames only)");
                return responseParamsList;
            }
            

            String basePath = (String) jsonMap.get("path");
            if (basePath == null || basePath.isEmpty() || isInvalidWebSocketPath(basePath)) {
                basePath = "/ws";  // Use default WebSocket path
            }
            
            Object apiCollectionIdObj = jsonMap.get("akto_vxlan_id");
            String accountId = (String) jsonMap.get("akto_account_id");
            
            int apiCollectionId = 0;
            if (apiCollectionIdObj != null) {
                try {
                    apiCollectionId = Integer.parseInt(apiCollectionIdObj.toString());
                } catch (NumberFormatException ignored) {
                }
            }
            
            Map<String, List<String>> requestHeaders = buildHeadersMap(jsonMap, "headers");
            Map<String, List<String>> responseHeaders = new HashMap<>();
            
            int statusCode = 101;
            String status = "Switching Protocols";
            
            for (WebSocketMessageParser.WebSocketEvent event : events) {
                // if (!isOutgoingEvent(event.originalEventJson)) {
                //     continue;
                // }

                String wsEndpointPath = "/" + event.messageType;
                
                HttpRequestParams requestParams = new HttpRequestParams(
                    "WS",
                    wsEndpointPath,  // Store as path, not as full URL
                    WEBSOCKET_TYPE,
                    requestHeaders,
                    "",
                    apiCollectionId
                );
                
                int time = resolveEventTime(event, jsonMap);
                
                HttpResponseParams responseParams = new HttpResponseParams(
                    WEBSOCKET_TYPE,
                    statusCode,
                    status,
                    responseHeaders,
                    event.originalEventJson,
                    requestParams,
                    time,
                    accountId,
                    false,
                    HttpResponseParams.Source.MIRRORING,  
                    buildOriginalMessage(jsonMap, wsEndpointPath, event),
                    ""
                );
                
                responseParamsList.add(responseParams);
            }
            
        } catch (Exception e) {
            loggerMaker.error("Error processing WebSocket message: " + e.getMessage());
        }
        
        return responseParamsList;
    }
    
    private static boolean hasCompleteFrames(Map<String, Object> jsonMap) {
        try {
            Object eventsObj = jsonMap.get("events");
            if (eventsObj == null) {
                return false;
            }
            
            String eventsStr = eventsObj.toString();
            List<Object> eventList = JSON.parseArray(eventsStr);
            
            if (eventList == null || eventList.isEmpty()) {
                return false;
            }
            
            for (Object event : eventList) {
                if (event instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> eventMap = (Map<String, Object>) event;
                    
                    // Check if this is a final frame (fin=true)
                    Object finObj = eventMap.get("fin");
                    boolean isFinal = finObj != null && Boolean.parseBoolean(finObj.toString());
                    
                    if (isFinal) {
                        return true;  // Has at least one complete frame
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.debug("Error checking for complete frames: " + e.getMessage());
        }
        
        return false;
    }
    
    private static boolean isInvalidWebSocketPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        // Check for malformed paths with type tokens (STRING, INTEGER, etc.)
        return path.contains("STRING") || path.contains("INTEGER") || path.contains("FLOAT") || 
               path.contains("OBJECT_ID") || path.contains("ws://") || path.contains("wss://");
    }
    
    private static Map<String, List<String>> buildHeadersMap(Map<String, Object> jsonMap, String headersKey) {
        Map<String, List<String>> headers = new HashMap<>();
        
        try {
            Object headersObj = jsonMap.get(headersKey);
            if (headersObj == null) {
                return headers;
            }
            
            if (headersObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> headersMap = (Map<String, Object>) headersObj;
                for (Map.Entry<String, Object> entry : headersMap.entrySet()) {
                    List<String> values = new ArrayList<>();
                    if (entry.getValue() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> list = (List<String>) entry.getValue();
                        values = list;
                    } else if (entry.getValue() != null) {
                        values.add(entry.getValue().toString());
                    }
                    headers.put(entry.getKey().toLowerCase(), values);
                }
            } else if (headersObj instanceof String) {
                Map<String, Object> parsedHeaders = JSON.parseObject((String) headersObj);
                for (Map.Entry<String, Object> entry : parsedHeaders.entrySet()) {
                    List<String> values = new ArrayList<>();
                    if (entry.getValue() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> list = (List<String>) entry.getValue();
                        values = list;
                    } else if (entry.getValue() != null) {
                        values.add(entry.getValue().toString());
                    }
                    headers.put(entry.getKey().toLowerCase(), values);
                }
            }
        } catch (Exception e) {
            loggerMaker.warn("Error building headers map: " + e.getMessage());
        }
        
        return headers;
    }
    
    private static HttpResponseParams buildHandshakeResponseParam(Map<String, Object> jsonMap) {
        try {
            String path = (String) jsonMap.get("path");
            // Sanitize path if it contains type tokens
            if (path != null && (path.contains("STRING") || path.contains("INTEGER") || 
                path.contains("FLOAT") || path.contains("OBJECT_ID"))) {
                path = "/ws";  // Use default fallback path
            }
            if (path == null || path.isEmpty()) {
                path = "/ws";
            }
            
            String method = jsonMap.getOrDefault("method", "GET").toString();
            String accountId = (String) jsonMap.get("akto_account_id");

            Object apiCollectionIdObj = jsonMap.get("akto_vxlan_id");
            int apiCollectionId = 0;
            if (apiCollectionIdObj != null) {
                try { apiCollectionId = Integer.parseInt(apiCollectionIdObj.toString()); } catch (NumberFormatException ignored) {}
            }

            // For WebSocket handshake, store just the path (like HTTP APIs do)
            // Host information is in headers, not in the canonical URL
            String wsEndpointPath = path;

            Map<String, List<String>> requestHeaders = buildHeadersMap(jsonMap, "requestHeaders");
            Map<String, List<String>> responseHeaders = buildHeadersMap(jsonMap, "responseHeaders");

            int time = (int) (System.currentTimeMillis() / 1000);
            Object timeObj = jsonMap.get("time");
            if (timeObj != null) {
                try { time = Integer.parseInt(timeObj.toString()); } catch (NumberFormatException ignored) {}
            }

            HttpRequestParams requestParams = new HttpRequestParams(
                method, wsEndpointPath, WEBSOCKET_TYPE, requestHeaders, "", apiCollectionId
            );

            return new HttpResponseParams(
                WEBSOCKET_TYPE, 101, "Switching Protocols", responseHeaders, "",
                requestParams, time, accountId, false,
                HttpResponseParams.Source.MIRRORING,
                buildOriginalMessage(jsonMap, wsEndpointPath, null), ""
            );
        } catch (Exception e) {
            loggerMaker.error("Error building WebSocket handshake response param: " + e.getMessage());
            return null;
        }
    }

    private static String buildOriginalMessage(Map<String, Object> jsonMap, String wsEndpointPath,
            WebSocketMessageParser.WebSocketEvent event) {
        Map<String, Object> messageMap = new java.util.HashMap<>(jsonMap);

        String path = wsEndpointPath != null ? wsEndpointPath : "/ws";
        messageMap.put("path", path);
        normalizeWebSocketHeaders(messageMap);

        if (event != null) {
            messageMap.put("events", WebSocketMessageParser.wrapSingleEventAsArray(event.originalEventJson));
            if (isBlankPayload(messageMap.get("responsePayload"))) {
                messageMap.put("responsePayload", event.originalEventJson);
            }
        }

        if (!messageMap.containsKey("requestPayload")) {
            messageMap.put("requestPayload", "");
        }
        if (!messageMap.containsKey("responsePayload")) {
            messageMap.put("responsePayload", "");
        }
        if (!messageMap.containsKey("type")) {
            messageMap.put("type", "WEBSOCKET");
        }
        if (!messageMap.containsKey("method")) {
            messageMap.put("method", "GET");
        }
        if (!messageMap.containsKey("statusCode")) {
            messageMap.put("statusCode", 101);
        }
        if (!messageMap.containsKey("responseHeaders")) {
            messageMap.put("responseHeaders", "{}");
        }
        if (!messageMap.containsKey("requestHeaders")) {
            messageMap.put("requestHeaders", "{}");
        }
        return JSON.toJSONString(messageMap);
    }

    private static void normalizeWebSocketHeaders(Map<String, Object> messageMap) {
        if (!isBlankPayload(messageMap.get("requestHeaders"))) {
            return;
        }
        Object headers = messageMap.get("headers");
        if (headers != null && !headers.toString().isEmpty()) {
            messageMap.put("requestHeaders", headers instanceof String ? headers : JSON.toJSONString(headers));
        }
    }

    private static boolean isOutgoingEvent(String originalEventJson) {
        try {
            Map<String, Object> eventMap = JSON.parseObject(originalEventJson);
            Object direction = eventMap.get("direction");
            if (direction == null || direction.toString().isEmpty()) {
                return true;
            }
            return "outgoing".equalsIgnoreCase(direction.toString());
        } catch (Exception e) {
            return true;
        }
    }

    private static int resolveEventTime(WebSocketMessageParser.WebSocketEvent event, Map<String, Object> jsonMap) {
        try {
            Map<String, Object> eventMap = JSON.parseObject(event.originalEventJson);
            Object timestamp = eventMap.get("timestamp");
            if (timestamp != null) {
                return Integer.parseInt(timestamp.toString());
            }
        } catch (Exception ignored) {
        }

        Object timeObj = jsonMap.get("time");
        if (timeObj != null) {
            try {
                return Integer.parseInt(timeObj.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return (int) (System.currentTimeMillis() / 1000);
    }

    private static boolean isBlankPayload(Object payload) {
        if (payload == null) {
            return true;
        }
        String value = payload.toString().trim();
        return value.isEmpty() || "{}".equals(value);
    }
}
