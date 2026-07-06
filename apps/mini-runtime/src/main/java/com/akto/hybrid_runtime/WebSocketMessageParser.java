package com.akto.hybrid_runtime;

import com.alibaba.fastjson2.JSON;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.*;

public class WebSocketMessageParser {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(WebSocketMessageParser.class, LogDb.RUNTIME);
    private static final String WEBSOCKET_TYPE = "WEBSOCKET";
    
    // WebSocket frame opcodes
    // private static final int OPCODE_TEXT = 1;
    // private static final int OPCODE_BINARY = 2;
    // private static final int OPCODE_CLOSE = 8;
    // private static final int OPCODE_PING = 9;
    // private static final int OPCODE_PONG = 10;
    
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
        if (typeObj != null && WEBSOCKET_TYPE.equalsIgnoreCase(typeObj.toString())) {
            return true;
        }
        
        Object connectionTypeObj = jsonMap.get("connection_type");
        if (connectionTypeObj != null && WEBSOCKET_TYPE.equalsIgnoreCase(connectionTypeObj.toString())) {
            return true;
        }
        
        Object sourceObj = jsonMap.get("source");
        return sourceObj != null && sourceObj.toString().equalsIgnoreCase("WEBSOCKET_TRAFFIC");
    }
    
    public static List<WebSocketEvent> parseWebSocketEvents(Map<String, Object> jsonMap) {
        List<WebSocketEvent> events = new ArrayList<>();
        
        try {
            Object eventsObj = jsonMap.get("events");
            if (eventsObj == null) {
                loggerMaker.debug("WebSocket message has no events field");
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
                    
                    if (events.isEmpty()) {
                        loggerMaker.debug("No extractable messageTypes found in " + eventList.size() + " WebSocket frames (likely control frames: PING/PONG)");
                    }
                } catch (Exception ex) {
                    loggerMaker.warn("Failed to parse events as JSON array: " + ex.getMessage());
                }
            }
            
        } catch (Exception e) {
            loggerMaker.warn("Error parsing WebSocket events: " + e.getMessage());
        }
        
        return events;
    }
    
    private static String extractMessageType(Map<String, Object> eventMap) {
        int opcode = getOpcode(eventMap);
        
        // Only process DATA frames (opcodes 0-7 are data, 8-15 are control)
        // Opcodes 0-7: continuation, text, binary, and reserved (non-control)
        // Opcodes 8-15: PING, PONG, CLOSE, and control frame opcodes
        if (opcode >= 0 && opcode <= 7) {
            Object payloadObj = eventMap.get("payload");
            if (payloadObj != null && !payloadObj.toString().isEmpty()) {
                String payload = payloadObj.toString();
                
                // Try full JSON parse first
                try {
                    Map<String, Object> payloadMap = JSON.parseObject(payload);
                    Object typeObj = payloadMap.get("type");
                    if (typeObj != null) {
                        return typeObj.toString();
                    }
                    Object messageTypeObj = payloadMap.get("messageType");
                    if (messageTypeObj != null) {
                        return messageTypeObj.toString();
                    }
                } catch (Exception e) {
                    // If full JSON fails, try to extract "type" field using regex
                    // This handles fragmented frames where payload is partial JSON
                    java.util.regex.Pattern typePattern = java.util.regex.Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");
                    java.util.regex.Matcher typeMatcher = typePattern.matcher(payload);
                    if (typeMatcher.find()) {
                        return typeMatcher.group(1);
                    }
                    
                    java.util.regex.Pattern msgTypePattern = java.util.regex.Pattern.compile("\"messageType\"\\s*:\\s*\"([^\"]+)\"");
                    java.util.regex.Matcher msgTypeMatcher = msgTypePattern.matcher(payload);
                    if (msgTypeMatcher.find()) {
                        return msgTypeMatcher.group(1);
                    }
                }
            }
            return "data_frame";
        }
        
        // Control frames (8-15) don't represent API endpoints, skip them
        return null;
    }
    
    private static int getOpcode(Map<String, Object> eventMap) {
        Object opcodeObj = eventMap.get("opcode");
        if (opcodeObj != null) {
            try {
                return Integer.parseInt(opcodeObj.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }
    
    public static String wrapSingleEventAsArray(String originalEventJson) {
        if (originalEventJson == null || originalEventJson.isEmpty()) {
            return "[]";
        }
        return "[" + originalEventJson + "]";
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
