package com.akto.dto;

import com.akto.dto.testing.TLSAuthParam;
import com.akto.dto.type.RequestTemplate;
import com.akto.util.Constants;
import com.akto.util.HttpRequestResponseUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.Setter;
import okhttp3.HttpUrl;
import okhttp3.Headers;

import java.net.URI;
import java.util.*;

public class OriginalHttpRequest {

    private static final Gson gson = new Gson();
    private String url;
    private String type;
    private String queryParams;
    private String method;
    private String body;
    private String sourceIp;
    private String destinationIp;
    private Map<String, List<String>> headers;

    @Getter
    @Setter
    private TLSAuthParam tlsAuthParam;

    public OriginalHttpRequest() { }

    // before adding any fields make sure to add them to copy function as well
    public OriginalHttpRequest(String url, String queryParams, String method, String body, Map<String, List<String>> headers, String type) {
        this.url = url;
        this.queryParams = queryParams;
        this.method = method;
        this.body = body;
        this.headers = headers;
        this.type = type;
    }

    public OriginalHttpRequest(String url, String queryParams, String method, String body, String sourceIp, String destinationIp, Map<String, List<String>> headers, String type, TLSAuthParam tlsAuthParam) {
        this(url, queryParams, method, body, headers, type);
        this.sourceIp = sourceIp;
        this.destinationIp = destinationIp;
        this.tlsAuthParam = tlsAuthParam;
    }

    public OriginalHttpRequest copy() {
        Map<String, List<String>> headersCopy = new HashMap<>();
        for(Map.Entry<String, List<String>> headerKV: this.headers.entrySet()) {
            ArrayList<String> headerValues = new ArrayList<>();
            headerValues.addAll(headerKV.getValue());
            headersCopy.put(headerKV.getKey(), headerValues);
        }
        return new OriginalHttpRequest(
                this.url, this.queryParams, this.method, this.body, this.sourceIp, this.destinationIp, headersCopy, this.type, 
                this.tlsAuthParam
        );
    }

    public void buildFromSampleMessage(String message, boolean useUrlToFillHost) {
        buildFromSampleMessage(message);
        if(useUrlToFillHost){
            try {
                if(this.headers.getOrDefault("host", null) == null){
                    URI uri = new URI(this.url);
                    String calculatedHost = uri.getHost() != null ? uri.getHost() : "";
                    this.headers.put("host", Arrays.asList(calculatedHost));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
        }
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
        this.sourceIp = (String) json.get("ip");
        this.destinationIp = (String) json.get("destIp");

        this.headers = buildHeadersMap(json, "requestHeaders");
    }

    public void buildFromSampleMessageNew(HttpResponseParams responseParam) {
        String rawUrl = responseParam.getRequestParams().getURL();
        String[] rawUrlArr = rawUrl.split("\\?");
        this.url = rawUrlArr[0];
        if (rawUrlArr.length > 1) {
            this.queryParams = rawUrlArr[1];
        }

        this.type = responseParam.getRequestParams().type;

        this.method = responseParam.getRequestParams().getMethod();

        String requestPayload = responseParam.getRequestParams().getPayload();
        this.body = requestPayload.trim();

        this.sourceIp = responseParam.getSourceIP();
        this.destinationIp = responseParam.getDestIP();

        this.headers = responseParam.getRequestParams().getHeaders();
    }

    public String getJsonRequestBody() {
        return HttpRequestResponseUtils.rawToJsonString(this.body, this.headers);
    }

    public static final String JSON_CONTENT_TYPE = "application/json";

//    public boolean isJsonRequest() {
//        String acceptableContentType = HttpRequestResponseUtils.getAcceptableContentType(this.headers);
//        return acceptableContentType != null && acceptableContentType.equals(JSON_CONTENT_TYPE);
//    }

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
        this.sourceIp = reqObj.getString("ip");
        this.destinationIp = reqObj.getString("destIp");

    }

    public String findHeaderValue(String headerName) {
        if (this.headers == null ) return null;
        List<String> values = this.headers.get(headerName.trim().toLowerCase());
        if (values == null || values.size() == 0) return null;
        return values.get(0);
    }

    public String findHeaderValueIncludingInCookie(String headerName) {
        String value = findHeaderValue(headerName);
        if (value != null) return value;

        List<String> cookieList = headers.getOrDefault("cookie", new ArrayList<>());
        if (cookieList.isEmpty()) return null;

        for (String cookieValues : cookieList) {
            String[] cookies = cookieValues.split(";");
            for (String cookie : cookies) {
                cookie=cookie.trim();
                String[] cookieFields = cookie.split("=");
                if (cookieFields.length == 2) {
                    String cookieKey = cookieFields[0].toLowerCase();
                    if(cookieKey.equals(headerName.toLowerCase())){
                        return cookieFields[1];
                    }
                }
            }
        }

        return null;
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
        String host;
        host = findHeaderValue("host");
        if (host == null) {
            host = findHeaderValue(":authority");//http2 header for host
            if (host == null) {
                host = findHeaderValue("authority");
            }
        }
        return host;
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

    public String getUrlPath() throws Exception {
        if (!url.startsWith("http")) {
            return url;
        } else {
            try {
                URI uri = new URI(url);
                return uri.getPath();
            } catch (Exception e) {
                return url;
            }
        }        
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
        JSONObject headersFromRequest = JSON.parseObject(headersString);
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

    public String fetchHeadersJsonString() {
        Map<String, List<String>> headersMap = this.getHeaders();
        List<String> forbiddenHeaders = Arrays.asList("content-length", "accept-encoding");
        if (headersMap == null)
            headersMap = new HashMap<>();
        headersMap.put(Constants.AKTO_IGNORE_FLAG, Collections.singletonList("0"));
        Map<String, String> filteredHeaders = new HashMap<>();
        for (String headerName : headersMap.keySet()) {
            if (forbiddenHeaders.contains(headerName))
                continue;
            if (headerName.contains(" "))
                continue;
            if(headerName.startsWith(":")) continue;
            List<String> headerValueList = headersMap.get(headerName);
            if (headerValueList == null || headerValueList.isEmpty())
                continue;
            for (String headerValue : headerValueList) {
                if (headerValue == null)
                    continue;
                filteredHeaders.put(headerName, headerValue);
                break;
            }
        }
        return gson.toJson(filteredHeaders);
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

    public String getSourceIp() {
        return this.sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }
    
    public String getDestinationIp() {
        return this.destinationIp;
    }

    public void setDestinationIp(String destinationIp) {
        this.destinationIp = destinationIp;
    }

//    public boolean setMethodAndQP(String line) {
//        String[] tokens = line.split(" ");
//        if (tokens.length != 3) {
//            return false;
//        }
//
//        this.method = tokens[0];
//        String fullUrl = tokens[1];
//
//        int qpIndex = fullUrl.indexOf("?");
//        if (qpIndex > -1 && qpIndex <= fullUrl.length()) {
//            this.url = fullUrl.substring(0, qpIndex);
//            this.queryParams = fullUrl.substring(qpIndex+1);
//        } else {
//            this.url = fullUrl;
//            this.queryParams = "";
//        }
//
//        this.type = tokens[2];
//
//        return true;
//    }
//
    public String getPath(){
       try {
            String path = URI.create(this.url).getPath();
            if (path == null || path.isEmpty()) {
                return "/";
            }
            return path;
        } catch (Exception e) {
            String strippedUrl = this.url.replaceAll("^(https?://[^/]+)", "");
            return strippedUrl.isEmpty() ? "/" : strippedUrl;
        }
    }

    public String getPathWithQueryParams() {
        URI uri = URI.create(this.url);
        String path = uri.getPath();
        String query = uri.getQuery();

        if (path == null) {
            path = "";
        }

        if (query == null || query.isEmpty()) {
            return path;
        } else {
            return path + "?" + query;
        }
    }

    /**
     * Converts the headers map to OkHttp Headers object
     * @return OkHttp Headers object
     */
    public Headers toOkHttpHeaders() {
        if (this.headers == null || this.headers.isEmpty()) {
            return new Headers.Builder().build();
        }
        
        Headers.Builder builder = new Headers.Builder();
        for (Map.Entry<String, List<String>> entry : this.headers.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            if (values != null) {
                for (String value : values) {
                    if (value != null) {
                        builder.add(key, value);
                    }
                }
            }
        }
        return builder.build();
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
                ", sourceIp=" + sourceIp +
                ", destinationIp=" + destinationIp +
                '}';
    }
}
