package com.akto.dto;


import com.akto.dao.context.Context;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.google.protobuf.TextFormat;

import java.util.*;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

public class HttpResponseParams {

    public enum Source {
        HAR, PCAP, MIRRORING, SDK, OTHER, POSTMAN, OPEN_API, BURP, IMPERVA
    }

    public String accountId;
    public String type; // HTTP/1.1
    public int statusCode; // 200
    public String status; // OK

    @Getter
    @Setter
    public Map<String, List<String>> headers = new HashMap<>();
    private String payload;
    private int time;
    public HttpRequestParams requestParams;
    boolean isPending;
    Source source = Source.OTHER;
    String orig;
    String sourceIP;
    String destIP;
    String direction;
    Supplier<String> originalMsg;

    public HttpResponseParams() {}

    public HttpResponseParams(String type, int statusCode, String status, Map<String, List<String>> headers, String payload,
                              HttpRequestParams requestParams, int time, String accountId, boolean isPending, Source source, 
                              String orig, String sourceIP) {
        this(type, statusCode, status, headers, payload, requestParams, time, accountId, isPending, source, orig,
                sourceIP, "", "");
    }

    public HttpResponseParams(String type, int statusCode, String status, Map<String, List<String>> headers, String payload,
                              HttpRequestParams requestParams, int time, String accountId, boolean isPending, Source source,
                              String orig, String sourceIP, String destIP, String direction) {
        this.type = type;
        this.statusCode = statusCode;
        this.status = status;
        this.headers = headers;
        this.payload = payload;
        this.requestParams = requestParams;
        this.time = time;
        this.accountId = accountId;
        this.isPending = isPending;
        this.source = source;
        this.orig = orig;
        this.sourceIP = sourceIP;
        this.destIP = destIP;
        this.direction = direction;
    }

    public HttpResponseParams resetValues(String type, int statusCode, String status, Map<String, List<String>> headers, String payload,
                              HttpRequestParams requestParams, int time, String accountId, boolean isPending, Source source,
                              String orig, String sourceIP, String destIP, String direction, Supplier<String> originalMsg) {
        this.type = type;
        this.statusCode = statusCode;
        this.status = status;
        this.headers = headers;
        this.payload = payload;
        this.requestParams = requestParams;
        this.time = time;
        this.accountId = accountId;
        this.isPending = isPending;
        this.source = source;
        this.orig = orig;
        this.sourceIP = sourceIP;
        this.destIP = destIP;
        this.direction = direction;
        this.originalMsg = originalMsg;

        return this;
    }

    public static boolean validHttpResponseCode(int statusCode)  {
        return statusCode >= 200 && (statusCode < 300 || statusCode == 302);
    }

    public HttpResponseParams copy() {
        return new HttpResponseParams(
                this.type,
                this.statusCode,
                this.status,
                new HashMap<>(this.headers),
                this.payload,
                this.requestParams.copy(),
                this.time,
                this.accountId,
                this.isPending,
                this.source,
                this.orig,
                this.sourceIP
        );
    }

    private static final Set<String> allowedPath = new HashSet<>();

    static {
        allowedPath.add("graphql");
        allowedPath.add("graph");
    }
    public static final String QUERY = "query";


    public static boolean isGraphql(HttpResponseParams responseParams) {
        boolean isAllowedForParse = false;
        String path = responseParams.getRequestParams().getURL();
        String requestPayload = responseParams.getRequestParams().getPayload();

        for (String graphqlPath : allowedPath) {
            if (path != null && path.contains(graphqlPath)) {
                isAllowedForParse = true;
                break;
            }
        }
        return isAllowedForParse && requestPayload.contains(QUERY);
    }

    public static boolean isGraphQLEndpoint(String url) {
        for (String keyword : allowedPath) {
            if (url.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public int getTimeOrNow() {
        return getTime() == 0 ? Context.now() : getTime();
    }


    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public HttpRequestParams getRequestParams() {
        return this.requestParams;
    }

    public int getStatusCode() {
        return this.statusCode;
    }

    public Map<String, List<String>> getHeaders() {
        return this.headers;
    }

    public int getTime() {
        return time;
    }

    public String getAccountId() {
        return accountId;
    }

    public boolean getIsPending() {
        return this.isPending;
    }

    public void setIsPending(boolean isPending) {
        this.isPending = isPending;
    }

    public Source getSource() {
        return this.source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public String getOrig() {
        return this.orig;
    }

    public void setOrig(String orig) {
        this.orig = orig;
    }

    public String getSourceIP() {
        return this.sourceIP;
    }

    public void setSourceIP(String sourceIP) {
        this.sourceIP = sourceIP;
    }

    public String getDestIP() {
        return destIP;
    }

    public void setDestIP(String destIP) {
        this.destIP = destIP;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public void setRequestParams(HttpRequestParams requestParams) {
        this.requestParams = requestParams;
    }

    public Supplier<String> getOriginalMsg() {
        return originalMsg;
    }

    public void setOriginalMsg(Supplier<String> originalMsg) {
        this.originalMsg = originalMsg;
    }

    public static String getSampleStringFromProtoString(String httpResponseParamProtoString){

        String origStr = "";
        HttpResponseParam.Builder httpBuilder = HttpResponseParam.newBuilder();
        try {
          TextFormat.getParser().merge(httpResponseParamProtoString, httpBuilder);
        } catch (Exception e) {
          return httpResponseParamProtoString;
        }
        HttpResponseParam httpResponseParamProto = httpBuilder.build();

        BasicDBObject origObj = new BasicDBObject();
        ObjectMapper objectMapper = new ObjectMapper();

        String sourceStr = httpResponseParamProto.getSource();
        if (sourceStr == null || sourceStr.isEmpty()) {
          sourceStr = HttpResponseParams.Source.OTHER.name();
        }
        Map<String, List<String>> reqHeaders = (Map) httpResponseParamProto.getRequestHeadersMap();
        Map<String, String> reqHeadersStr = new HashMap<>();
        Map<String, String> respHeadersStr = new HashMap<>();
        Map<String, List<String>> respHeaders = (Map) httpResponseParamProto.getResponseHeadersMap();
        String reqHeaderStr2 = "";
        String respHeaderStr2 = "";

        for (Map.Entry<String, List<String>> entry : reqHeaders.entrySet()) {
            reqHeadersStr.put(entry.getKey(), entry.getValue().get(0));
        }

        for (Map.Entry<String, List<String>> entry : respHeaders.entrySet()) {
            respHeadersStr.put(entry.getKey(), entry.getValue().get(0));
        }

        try {
            reqHeaderStr2 = objectMapper.writeValueAsString(reqHeadersStr);
            respHeaderStr2 = objectMapper.writeValueAsString(respHeadersStr);
        } catch (Exception e) {
            return httpResponseParamProtoString;
        }

        origObj.put("method", httpResponseParamProto.getMethod());
        origObj.put("requestPayload", httpResponseParamProto.getRequestPayload());
        origObj.put("responsePayload", httpResponseParamProto.getResponsePayload());
        origObj.put("ip", httpResponseParamProto.getIp());
        origObj.put("destIp", httpResponseParamProto.getDestIp());
        origObj.put("source", sourceStr);
        origObj.put("type", httpResponseParamProto.getType());
        origObj.put("akto_vxlan_id", httpResponseParamProto.getAktoVxlanId());
        origObj.put("path", httpResponseParamProto.getPath());
        origObj.put("requestHeaders", reqHeaderStr2);
        origObj.put("responseHeaders", respHeaderStr2);
        origObj.put("time", httpResponseParamProto.getTime());
        origObj.put("akto_account_id", httpResponseParamProto.getAktoAccountId());
        origObj.put("statusCode", httpResponseParamProto.getStatusCode());
        origObj.put("status", httpResponseParamProto.getStatus());

        try {
          origStr = objectMapper.writeValueAsString(origObj);
        } catch (Exception e) {
            return httpResponseParamProtoString;
        }

        return origStr;
    }

    public static String addPathParamToUrl(String url, String pathParam) {
        String[] onlyUrl = url.split("\\?");
        if (onlyUrl.length < 1) {
            return url;
        }

        url = onlyUrl[0];
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        url = url + "/" + pathParam;
        if (onlyUrl.length == 2) {
            String queryParams = onlyUrl[1];
            if (StringUtils.isNotBlank(queryParams)) {
                url = url + "?" + queryParams;
            }
        }
        return url;
    }
}
