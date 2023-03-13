package com.akto.dto;

import com.akto.dto.type.RequestTemplate;
import com.akto.util.grpc.ProtoBufUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import okhttp3.HttpUrl;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

import static com.akto.util.grpc.ProtoBufUtils.DECODED_QUERY;
import static com.akto.util.grpc.ProtoBufUtils.RAW_QUERY;

public class OriginalHttpRequest {

    private static final Gson gson = new Gson();
    private final static ObjectMapper mapper = new ObjectMapper();
    private String url;
    private String type;
    private String queryParams;
    private String method;
    private String body;
    private Map<String, List<String>> headers;

    public OriginalHttpRequest() { }

    // before adding any fields make sure to add them to copy function as wel
    public OriginalHttpRequest(String url, String queryParams, String method, String body, Map<String, List<String>> headers, String type) {
        this.url = url;
        this.queryParams = queryParams;
        this.method = method;
        this.body = body;
        this.headers = headers;
        this.type = type;
    }

    public OriginalHttpRequest copy() {
        return new OriginalHttpRequest(
                this.url, this.queryParams, this.method, this.body, new HashMap<>(this.headers), this.type
        );
    }

    public void buildFromSampleMessage(String message) {
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String rawUrl = (String) json.get("path");
        String[] rawUrlArr = rawUrl.split("\\?");
        this.url = rawUrlArr[0];
        if (rawUrlArr.length > 1) {
            this.queryParams = rawUrlArr[1];
        }

        this.type = (String) json.get("type");

        this.method = (String) json.get("method");

        String requestPayload = (String) json.get("requestPayload");
        this.body = requestPayload.trim();

        this.headers = buildHeadersMap(json, "requestHeaders");
    }

    public String getJsonRequestBody() {
        return rawToJsonString(body, headers);
    }

    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String GRPC_CONTENT_TYPE = "application/grpc";

    public static String rawToJsonString(String rawRequest, Map<String,List<String>> requestHeaders) {
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
            HashMap<Object, Object> map = ProtoBufUtils.getInstance().decodeProto(rawRequest);
            if (map.isEmpty()) {
                return rawRequest;
            }
            return mapper.writeValueAsString(map);
        } catch (Exception e) {
            return rawRequest;
        }
    }

    public boolean isJsonRequest() {
        String acceptableContentType = getAcceptableContentType(this.headers);
        return acceptableContentType != null && acceptableContentType.equals(JSON_CONTENT_TYPE);
    }

    public static String convertFormUrlEncodedToJson(String rawRequest) {
        String myStringDecoded = null;
        try {
            myStringDecoded = URLDecoder.decode(rawRequest, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return rawRequest;
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
        } catch (JsonProcessingException e) {
            return rawRequest;
        }
    }

    public static String getAcceptableContentType(Map<String,List<String>> headers) {
        List<String> acceptableContentTypes = Arrays.asList(JSON_CONTENT_TYPE, FORM_URL_ENCODED_CONTENT_TYPE, GRPC_CONTENT_TYPE);
        List<String> contentTypeValues;
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

    public void buildFromApiSampleMessage(String message) {
        BasicDBObject ob = BasicDBObject.parse(message);
        BasicDBObject reqObj = (BasicDBObject) ob.get("request");
        Map<String, String> headersOg = new HashMap<>();
        headersOg = gson.fromJson(reqObj.getString("headers"), headersOg.getClass());
        this.headers = new HashMap<>();
        for (String key: headersOg.keySet()) {
            this.headers.put(key, Collections.singletonList(headersOg.get(key)));
        }

        this.url = reqObj.getString("url");
        this.queryParams = reqObj.getString("queryParams");
        this.method = reqObj.getString("method");
        this.body = reqObj.getString("body");
        this.type = reqObj.getString("type");

    }

    public String findHeaderValue(String headerName) {
        if (this.headers == null ) return null;
        List<String> values = this.headers.get(headerName.trim().toLowerCase());
        if (values == null || values.size() == 0) return null;
        return values.get(0);
    }

    // queryString2 overrides queryString1 use accordingly
    public static String combineQueryParams(String queryString1, String queryString2) {
        if (queryString1 == null || queryString1.isEmpty()) return queryString2;
        if (queryString2 == null || queryString2.isEmpty()) return queryString1;

        // www.example.com/foo?bar is valid
        if (!queryString2.contains("=")) return queryString2;
        if (!queryString1.contains("=")) return queryString1;

        String mockUrl1 = "url?" + queryString1;
        String mockUrl2 = "url?" + queryString2;

        BasicDBObject queryParamsObject1 = RequestTemplate.getQueryJSON(mockUrl1);
        BasicDBObject queryParamsObject2 = RequestTemplate.getQueryJSON(mockUrl2);

        for (String key: queryParamsObject2.keySet()) {
            queryParamsObject1.put(key, queryParamsObject2.get(key));
        }

        String json = queryParamsObject1.toJson();

        return getRawQueryFromJson(json);
    }

    public static String getRawQueryFromJson(String requestPayload) {
        HttpUrl.Builder builder = new HttpUrl.Builder()
                .scheme("https")
                .host("www.google.com");

        BasicDBObject obj = BasicDBObject.parse(requestPayload);
        Set<String> keySet = obj.keySet();
        if (keySet.isEmpty()) return null;

        for(String key: keySet) {
            Object val = obj.get(key);
            builder.addQueryParameter(key, val.toString());
        }

        URI uri = builder.build().uri();

        return uri.getRawQuery();
    }


    public static String makeUrlAbsolute(String url, String host, String protocol) throws Exception {
        if (host == null) throw new Exception("Host not found");
        if (!url.startsWith("/")) url = "/" + url;
        if (host.endsWith("/")) host = host.substring(0, host.length()-1);

        host = host.toLowerCase();
        if (!host.startsWith("http")) {
            if (protocol != null) {
                host = protocol + "://" + host;
            } else {
                String firstChar = host.split("")[0];
                try {
                    Integer.parseInt(firstChar);
                    host = "http://" + host;
                } catch (Exception e) {
                    host = "https://" + host;
                }
            }
        }

        url = host + url;

        return url;
    }

    public String findContentType() {
        return findHeaderValue("content-type");
    }

    public String findHostFromHeader() {
        return findHeaderValue("host");
    }

    public String findProtocolFromHeader() {
        return findHeaderValue("x-forwarded-proto");
    }

    public String getFullUrlIncludingDomain() throws Exception {
        if (!url.startsWith("http")) {
            return OriginalHttpRequest.makeUrlAbsolute(url, findHostFromHeader(), findProtocolFromHeader());
        }
        return url;
    }

    public String getFullUrlWithParams() {
        return getFullUrlWithParams(this.url, this.queryParams);
    }

    public static String getFullUrlWithParams(String url, String queryParams) {
        if (queryParams == null || queryParams.isEmpty()) return url;
        if (url.contains("?")) return url + "&" + queryParams;
        return url + "?" + queryParams;
    }

    public static Map<String,List<String>> buildHeadersMap(Map json, String key) {
        return buildHeadersMap((String) json.get(key));
    }

    public static Map<String,List<String>> buildHeadersMap(String headersString) {
        Map headersFromRequest = gson.fromJson(headersString, Map.class);
        Map<String,List<String>> headers = new HashMap<>();
        if (headersFromRequest == null) return headers;
        for (Object k: headersFromRequest.keySet()) {
            List<String> values = headers.getOrDefault(k,new ArrayList<>());
            values.add(headersFromRequest.get(k).toString());
            headers.put(k.toString().toLowerCase(),values);
        }
        return headers;
    }

    public void addHeaderFromLine(String line) {
        if (this.headers == null || this.headers.isEmpty()) {
            this.headers = new HashMap<>();
        } 

        int separator = line.indexOf(":");
        if (separator < 0 || separator > line.length()-2) {
            return;
        }
        String headerKey = line.substring(0, separator);
        List<String> headerValues = this.headers.get(headerKey);
        if (headerValues == null) {
            headerValues = new ArrayList<>();
            this.headers.put(headerKey, headerValues);
        }
                
        String headerValue = line.substring(separator+2);

        headerValues.add(headerValue);
    }

    public void appendToPayload(String line) {
        if (this.body == null) {
            this.body = "";
        }

        this.body += line;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean setMethodAndQP(String line) {
        String[] tokens = line.split(" ");
        if (tokens.length != 3) {
            return false;
        }

        this.method = tokens[0];
        String fullUrl = tokens[1];

        int qpIndex = fullUrl.indexOf("?");
        if (qpIndex > -1 && qpIndex <= fullUrl.length()) {
            this.url = fullUrl.substring(0, qpIndex);
            this.queryParams = fullUrl.substring(qpIndex+1);
        } else {
            this.url = fullUrl;
            this.queryParams = "";
        }

        this.type = tokens[2];

        return true;
    }

    @Override
    public String toString() {
        return "OriginalHttpRequest{" +
                "url='" + url + '\'' +
                ", type='" + type + '\'' +
                ", queryParams='" + queryParams + '\'' +
                ", method='" + method + '\'' +
                ", body='" + body + '\'' +
                ", headers=" + headers +
                '}';
    }
}
