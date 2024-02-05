package com.akto.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.akto.util.grpc.ProtoBufUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

import static com.akto.dto.OriginalHttpRequest.*;
import static com.akto.util.grpc.ProtoBufUtils.DECODED_QUERY;
import static com.akto.util.grpc.ProtoBufUtils.RAW_QUERY;

public class HttpRequestResponseUtils {

    private final static ObjectMapper mapper = new ObjectMapper();

    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String GRPC_CONTENT_TYPE = "application/grpc";

    public static Map<String, Set<Object>> extractValuesFromPayload(String body) {
        if (body == null) return new HashMap<>();
        if (body.startsWith("[")) body = "{\"json\": "+body+"}";
        BasicDBObject respPayloadObj;
        try {
            respPayloadObj = BasicDBObject.parse(body);
        } catch (Exception e) {
            respPayloadObj = BasicDBObject.parse("{}");
        }
        return JSONUtils.flatten(respPayloadObj);
    }

    public static String rawToJsonString(String rawRequest, Map<String,List<String>> requestHeaders) {
        if (rawRequest == null) return null;
        rawRequest = rawRequest.trim();
        String acceptableContentType = getAcceptableContentType(requestHeaders);
        if (acceptableContentType != null && rawRequest.length() > 0) {
            // only if request payload is of FORM_URL_ENCODED_CONTENT_TYPE we convert it to json
            if (acceptableContentType.equals(FORM_URL_ENCODED_CONTENT_TYPE)) {
                return convertFormUrlEncodedToJson(rawRequest);
            } else if (acceptableContentType.equals(GRPC_CONTENT_TYPE)) {
                return convertGRPCEncodedToJson(rawRequest);
            }
        }

        return rawRequest;
    }

    public static String convertGRPCEncodedToJson(String rawRequest) {
        try {
            Map<Object, Object> map = ProtoBufUtils.getInstance().decodeProto(rawRequest);
            if (map.isEmpty()) {
                return rawRequest;
            }
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return rawRequest;
        }
    }
    
    public static String getAcceptableContentType(Map<String,List<String>> headers) {
        List<String> acceptableContentTypes = Arrays.asList(JSON_CONTENT_TYPE, FORM_URL_ENCODED_CONTENT_TYPE, GRPC_CONTENT_TYPE);
        List<String> contentTypeValues;
        if (headers == null) return null;
        for (String k: headers.keySet()) {
            if (k.equalsIgnoreCase("content-type")) {
                contentTypeValues = headers.get(k);
                for (String value: contentTypeValues) {
                    for (String acceptableContentType: acceptableContentTypes) {
                        if (value.contains(acceptableContentType)) {
                            return acceptableContentType;
                        }
                    }
                }
            }
        }
        return null;
    }
    
    public static String convertFormUrlEncodedToJson(String rawRequest) {
        String myStringDecoded = null;
        try {
            myStringDecoded = URLDecoder.decode(rawRequest, "UTF-8");
        } catch (Exception e) {
            return null;
        }
        String[] parts = myStringDecoded.split("&");
        Map<String,String> valueMap = new HashMap<>();

        for(String part: parts){
            String[] keyVal = part.split("="); // The equal separates key and values
            if (keyVal.length == 2) {
                valueMap.put(keyVal[0], keyVal[1]);
            }
        }
        try {
            return mapper.writeValueAsString(valueMap);
        } catch (Exception e) {
            return null;
        }
    }

    public static String jsonToFormUrlEncoded(String requestPayload) {
        JSONObject jsonObject = new JSONObject(requestPayload);

        StringBuilder formUrlEncoded = new StringBuilder();

        // Iterate over the keys in the JSON object
        for (String key : jsonObject.keySet()) {
            // Encode the key and value, and append them to the string builder
            try {
                formUrlEncoded.append(encode(key))
                        .append("=")
                        .append(encode(jsonObject.getString(key)))
                        .append("&");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Remove the last "&"
        if (formUrlEncoded.length() > 0) {
            formUrlEncoded.setLength(formUrlEncoded.length() - 1);
        }

        return formUrlEncoded.toString();
    }
    public static String encode(String s) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.name())
                .replaceAll("\\+", "%20")
                .replaceAll("\\%21", "!")
                .replaceAll("\\%27", "'")
                .replaceAll("\\%28", "(")
                .replaceAll("\\%29", ")")
                .replaceAll("\\%7E", "~")
                .replaceAll("\\%5B", "[")
                .replaceAll("\\%5D", "]");
    }

}
