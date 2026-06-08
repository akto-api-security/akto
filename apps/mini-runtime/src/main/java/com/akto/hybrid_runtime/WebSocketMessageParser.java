package com.akto.hybrid_runtime;

import com.alibaba.fastjson2.JSON;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.*;

public class WebSocketMessageParser {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(WebSocketMessageParser.class, LogDb.RUNTIME);
    private static final String WEBSOCKET_TYPE = "WEBSOCKET";
    
    public static class WebSocketEvent {
        public String messageType;
        public String originalEventJson;
        
        public WebSocketEvent(String messageType, String originalEventJson) {
            this.messageType = messageType;
            this.originalEventJson = originalEventJson;
        }
    }
    
    public static boolean isWebSocketMessage(Map<String, Object> jsonMap) {
        Object typeObj = jsonMap.get("type");
        return typeObj != null && WEBSOCKET_TYPE.equalsIgnoreCase(typeObj.toString());
    }
    
    public static List<WebSocketEvent> parseWebSocketEvents(Map<String, Object> jsonMap) {
        List<WebSocketEvent> events = new ArrayList<>();
        
        try {
            Object eventsObj = jsonMap.get("events");
            if (eventsObj == null) {
                loggerMaker.warn("WebSocket message has no events field");
                return events;
            }
            
            String eventsStr = eventsObj.toString();
            
            try {
                Map<String, Object> eventMap = JSON.parseObject(eventsStr);
                String messageType = extractMessageType(eventMap);
                if (messageType != null && !messageType.isEmpty()) {
                    events.add(new WebSocketEvent(messageType, eventsStr));
                }
            } catch (Exception e) {
                try {
                    List<Object> eventList = JSON.parseArray(eventsStr);
                    for (Object event : eventList) {
                        if (event instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> eventMap = (Map<String, Object>) event;
                            String messageType = extractMessageType(eventMap);
                            if (messageType != null && !messageType.isEmpty()) {
                                events.add(new WebSocketEvent(messageType, JSON.toJSONString(eventMap)));
                            }
                        }
                    }
                } catch (Exception ex) {
                    loggerMaker.warn("Failed to parse events as JSON: " + ex.getMessage());
                }
            }
            
        } catch (Exception e) {
            loggerMaker.warn("Error parsing WebSocket events: " + e.getMessage());
        }
        
        return events;
    }
    
    private static String extractMessageType(Map<String, Object> eventMap) {
        Object typeObj = eventMap.get("type");
        if (typeObj != null) {
            return typeObj.toString();
        }
        
        Object messageTypeObj = eventMap.get("messageType");
        if (messageTypeObj != null) {
            return messageTypeObj.toString();
        }
        
        return null;
    }
    
    public static String buildWebSocketUrl(String basePath, String messageType) {
        if (basePath == null || basePath.isEmpty()) {
            basePath = "/";
        }
        if (messageType == null || messageType.isEmpty()) {
            return basePath;
        }
        
        if (basePath.endsWith("/")) {
            return basePath + messageType;
        } else {
            return basePath + "/" + messageType;
        }
    }
}
