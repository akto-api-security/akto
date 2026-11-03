package com.akto.har;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.HarReaderMode;
import de.sstoehr.harreader.model.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HAR {
    private final static ObjectMapper mapper = new ObjectMapper();
    private final List<String> errors = new ArrayList<>();
    private static final LoggerMaker loggerMaker = new LoggerMaker(Har.class);
    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String WEBSOCKET_TYPE = "WEBSOCKET";
    private static final String WEBSOCKET_MESSAGES = "_webSocketMessages";
    // partial JSON frames
    private static final Pattern MESSAGE_TYPE_PATTERN = Pattern.compile("\"(type|messageType|event)\"\\s*:\\s*\"([^\"]+)\"");
    public List<String> getMessages(String harString, int collection_id, int accountId, HttpResponseParams.Source source) throws HarReaderException {
        HarReader harReader = new HarReader();
        Har har = harReader.readFromString(harString, HarReaderMode.LAX);

        HarLog log = har.getLog();
        List<HarEntry> entries = log.getEntries();

        List<String> entriesList =  new ArrayList<>();
        int idx=0;
        for (HarEntry entry: entries) {
            idx += 1;
            try {
                List<Map<String,String>> results = isWebSocket(entry) ? getWebSocketResultMaps(entry, accountId) : Collections.singletonList(getResultMap(entry, accountId));
                for (Map<String,String> result: results) {
                    if (result == null) continue;
                    result.put("akto_vxlan_id", collection_id+"");
                    result.put("source", source.name());
                    entriesList.add(mapper.writeValueAsString(result));
                }

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error while parsing har file on entry: " + idx + " ERROR: " + e.toString(), LogDb.DASHBOARD);
                errors.add("Error in entry " + idx);
            }
        }

        return entriesList;
    }

    public static Map<String,String> getResultMap(HarEntry entry, int accountId) throws Exception {
        HarRequest request = entry.getRequest();
        HarResponse response = entry.getResponse();

        List<HarHeader> requestHarHeaders = request.getHeaders();
        List<HarHeader> responseHarHeaders = response.getHeaders();

        Map<String,String> requestHeaderMap = convertHarHeadersToMap(requestHarHeaders);
        Map<String,String> responseHeaderMap = convertHarHeadersToMap(responseHarHeaders);

        String requestContentType = getContentType(requestHarHeaders);

        String requestPayload = request.getPostData().getText();
        if (requestPayload == null) requestPayload = "";

        String akto_account_id = accountId + "";
        String urlString = getPath(request);

        String path = urlString;


        String requestHeaders = mapper.writeValueAsString(requestHeaderMap);
        String responseHeaders = mapper.writeValueAsString(responseHeaderMap);
        String method = request.getMethod().toString();
        String responsePayload = response.getContent().getText();
        String ip = "null"; // TODO:
        String time = Context.now() + "";
        String statusCode = response.getStatus() + "";
        String type = request.getHttpVersion();
        String status = response.getStatusText();
        String contentType = getContentType(responseHarHeaders);

        Map<String,String> result = new HashMap<>();
        result.put("akto_account_id",akto_account_id);
        result.put("path",path);
        result.put("requestHeaders",requestHeaders);
        result.put("responseHeaders",responseHeaders);
        result.put("method",method);
        result.put("requestPayload",requestPayload);
        result.put("responsePayload",responsePayload);
        result.put("ip",ip);
        result.put("time",time);
        result.put("statusCode",statusCode);
        result.put("type",type);
        result.put("status",status);
        result.put("contentType",contentType);
        result.put("source", "HAR");

        return result;
    }

    public static boolean isWebSocket(HarEntry entry) {
        return entry.getResponse().getStatus() == 101 || entry.getAdditional().get(WEBSOCKET_MESSAGES) instanceof List;
    }


    public static List<Map<String,String>> getWebSocketResultMaps(HarEntry entry, int accountId) throws Exception {
        List<Map<String,String>> results = new ArrayList<>();

        Map<String,String> handshake = getResultMap(entry, accountId);
        // ws(s):// is not a java.net.URL protocol; the WebSocket executor maps http(s) back to ws(s)
        handshake.put("path", handshake.get("path").replaceFirst("^ws(s?)://", "http$1://"));
        handshake.put("type", WEBSOCKET_TYPE);
        handshake.put("statusCode", "101");
        handshake.put("status", "Switching Protocols");
        handshake.put("requestPayload", "");
        handshake.put("responsePayload", "");
        results.add(handshake);

        Object frames = entry.getAdditional().get(WEBSOCKET_MESSAGES);
        if (!(frames instanceof List)) return results;

        for (Object frame: (List<?>) frames) {
            if (!(frame instanceof Map)) continue;
            Map<?,?> frameMap = (Map<?,?>) frame;

            // control frames (close, ping, pong) are not endpoints
            int opcode = frameMap.get("opcode") instanceof Number ? ((Number) frameMap.get("opcode")).intValue() : -1;
            if (opcode < 0 || opcode > 7) continue;

            String data = frameMap.get("data") == null ? "" : frameMap.get("data").toString();
            int time = frameMap.get("time") instanceof Number ? ((Number) frameMap.get("time")).intValue() : Context.now();

            boolean sentByClient = "send".equals(frameMap.get("type"));

            Map<String,Object> event = new LinkedHashMap<>();
            event.put("direction", sentByClient ? "incoming" : "outgoing");
            event.put("opcode", opcode);
            event.put("payload", data);
            event.put("fin", true);
            event.put("timestamp", time);
            String eventJson = mapper.writeValueAsString(event);

            Map<String,String> result = new HashMap<>(handshake);
            result.put("method", "WS");
            result.put("path", "/" + getWebSocketMessageType(data));
            // the client's frame is the request body, so its params show up in the inventory; server frames have none
            if (sentByClient) result.put("requestPayload", data);
            result.put("responseHeaders", "{}");
            result.put("responsePayload", eventJson);
            result.put("events", "[" + eventJson + "]");
            result.put("time", time + "");
            results.add(result);
        }

        return results;
    }

    // "type", "messageType" or "event" of a JSON frame, else "data_frame", as mini-runtime's WebSocketMessageParser does (plus "event")
    public static String getWebSocketMessageType(String data) {
        try {
            JsonNode node = mapper.readTree(data);
            if (node != null && node.isObject()) {
                if (node.hasNonNull("type")) return node.get("type").asText();
                if (node.hasNonNull("messageType")) return node.get("messageType").asText();
                if (node.hasNonNull("event")) return node.get("event").asText();
            }
        } catch (Exception e) {
            Matcher matcher = MESSAGE_TYPE_PATTERN.matcher(data);
            if (matcher.find()) return matcher.group(2);
        }
        return "data_frame";
    }

    public static boolean isApiRequest(List<HarHeader> headers) {
        String contentType = getContentType(headers);
        if (contentType == null) {
            return false;
        }
        return contentType.contains(JSON_CONTENT_TYPE);
     }

     public static String getContentType(List<HarHeader> headers) {
         for (HarHeader harHeader: headers) {
             if (harHeader.getName().equalsIgnoreCase("content-type")) {
                 return harHeader.getValue();
             }
         }
         return null;
     }

     public static Map<String,String> convertHarHeadersToMap(List<HarHeader> headers) {
        Map<String,String> headerMap = new HashMap<>();

        for (HarHeader harHeader: headers) {
            if (harHeader != null) {
                if (harHeader.getName().contains(" ")) {
                    continue;
                }
                headerMap.put(harHeader.getName(), harHeader.getValue());
            }
        }

        return headerMap;
     }

    public static void addQueryStringToMap(List<HarQueryParam> params, Map<String,Object> paramsMap) {
        // TODO: which will take preference querystring or post value
        for (HarQueryParam param: params) {
            paramsMap.put(param.getName(), param.getValue());
        }
    }

    public static String getPath(HarRequest request) throws Exception {
        String path = request.getUrl();
        if (path == null) throw new Exception("url path is null in har");
        return path;
    }

    public List<String> getErrors() {
        return errors;
    }
}
