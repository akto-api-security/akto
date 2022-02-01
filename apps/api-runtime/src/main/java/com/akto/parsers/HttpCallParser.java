package com.akto.parsers;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.parsers.HttpCallParser.HttpResponseParams.Source;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.ConnectionString;

import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpCallParser {
    
    private static final String AKTO_REQUEST = "##AKTO_REQUEST##";
    private static final String AKTO_RESPONSE = "##AKTO_RESPONSE##";
    public static final String JSON_CONTENT_TYPE = "application/json";
    public static final String FORM_URL_ENCODED_CONTENT_TYPE = "application/x-www-form-urlencoded";
    private final static ObjectMapper mapper = new ObjectMapper();
    private final int sync_threshold_count;
    private final int sync_threshold_time;
    private int sync_count = 0;
    private int last_synced;
    private static final Logger logger = LoggerFactory.getLogger(HttpCallParser.class);

    public static class HttpRequestParams {
        String method; // POST
        String url; 
        String type; // HTTP/1.1
        private Map<String, List<String>> headers = new HashMap<>();
        private String payload;
        private int apiCollectionId;

        public HttpRequestParams() {}

        public HttpRequestParams(String method, String url, String type, Map<String, List<String>> headers, String payload, int apiCollectionId) {
            this.method = method;
            this.url = url;
            this.type = type;
            this.headers = headers;
            this.payload = payload;
            this.apiCollectionId = apiCollectionId;

        }

        public static List<HttpRequestParams> parseRequest(String request) throws IOException {

            List<HttpRequestParams> requests = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new StringReader(request));
            String line = reader.readLine();

            while (true) {
                String[] tokens = line.split(" ");
                HttpRequestParams httpRequestParams = new HttpRequestParams();
                httpRequestParams.method = tokens[0];
                httpRequestParams.url = tokens[1];
                httpRequestParams.type = tokens[2];

                int contentLength = 0;

                while((line = reader.readLine()) != null) {
                    if (line.length() > 0 && line.charAt(0) != '{') {
                        tokens = line.split(": ");
                        List<String> headerValues = httpRequestParams.getHeaders().get(tokens[0]);
                        if (headerValues == null) {
                            headerValues = new ArrayList<>();
                            httpRequestParams.getHeaders().put(tokens[0], headerValues);
                        }
                        
                        headerValues.add(tokens[1]);
                        if (tokens[0].toLowerCase().equals("content-length")) {
                            contentLength = Integer.parseInt(tokens[1]);
                        }

                    } else {
                        break;
                    }
                }

                line = reader.readLine();

                String payload = line.substring(0, contentLength);
                httpRequestParams.setPayload(payload);
                requests.add(httpRequestParams);
                String restOfLine = line.substring(contentLength);
                if (restOfLine.length() > 0) {
                    line = restOfLine;
                } else {
                    break;
                }
            }

            return requests;
        }
        
        public Map<String, List<String>> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, List<String>> headers) {
            this.headers = headers;
        }

        public String getPayload() {
            return payload;
        }

        public void setPayload(String payload) {
            this.payload = payload;
        }

        public String getURL() {
            return this.url;
        }

        public String getMethod() {
            return this.method;
        }

        public int getApiCollectionId() {
            return this.apiCollectionId;
        }

        public void setApiCollectionId(int apiCollectionId) {
            this.apiCollectionId = apiCollectionId;
        }
    }

    public static class HttpResponseParams {

        public enum Source {
            HAR, PCAP, MIRRORING, SDK, OTHER
        }

        String accountId;
        String type; // HTTP/1.1
        int statusCode; // 200
        String status; // OK
        Map<String, List<String>> headers = new HashMap<>();
        private String payload;
        private int time;
        HttpRequestParams requestParams;
        boolean isPending;
        Source source = Source.OTHER;
        String orig;

        public HttpResponseParams() {}

        public HttpResponseParams(String type, int statusCode, String status, Map<String, List<String>> headers, String payload, 
                                    HttpRequestParams requestParams, int time, String accountId, boolean isPending, Source source, String orig) {
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
    }

    APICatalogSync apiCatalogSync;
    public HttpCallParser(String userIdentifier, int thresh, int sync_threshold_count, int sync_threshold_time) {
        last_synced = 0;
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        apiCatalogSync = new APICatalogSync(userIdentifier,thresh);
        apiCatalogSync.buildFromDB(false);
    }
    
    public static HttpResponseParams parseKafkaMessage(String message) throws Exception {
        Gson gson = new Gson();

        //convert java object to JSON format
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String method = (String) json.get("method");
        String url = (String) json.get("path");
        String type = (String) json.get("type");
        Map<String,List<String>> requestHeaders = getHeaders(gson, json, "requestHeaders");

        String requestPayload = (String) json.get("requestPayload");
        requestPayload = requestPayload.trim();
        String acceptableContentType = getAcceptableContentType(requestHeaders);
        if (acceptableContentType != null && requestPayload.length() > 0) {
            // only if request payload is of FORM_URL_ENCODED_CONTENT_TYPE we convert it to json
            if (acceptableContentType.equals(FORM_URL_ENCODED_CONTENT_TYPE)) {
                String myStringDecoded = URLDecoder.decode((String) json.get("requestPayload"), "UTF-8");
                String[] parts = myStringDecoded.split("&");
                Map<String,String> valueMap = new HashMap<>();

                for(String part: parts){
                    String[] keyVal = part.split("="); // The equal separates key and values
                    if (keyVal.length == 2) {
                        valueMap.put(keyVal[0], keyVal[1]);
                    }
                }
                requestPayload = mapper.writeValueAsString(valueMap);
            }
        }

        String apiCollectionIdStr = json.getOrDefault("akto_vxlan_id", "0").toString();
        int apiCollectionId = 0;
        if (NumberUtils.isDigits(apiCollectionIdStr)) {
            apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
        }

        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload, apiCollectionId
        );

        int statusCode = Integer.parseInt(json.get("statusCode").toString());
        String status = (String) json.get("status");
        Map<String,List<String>> responseHeaders = getHeaders(gson, json, "responseHeaders");
        String payload = (String) json.get("responsePayload");
        int time = Integer.parseInt(json.get("time").toString());
        String accountId = (String) json.get("akto_account_id");

        String isPendingStr = (String) json.getOrDefault("is_pending", "false");
        boolean isPending = !isPendingStr.toLowerCase().equals("false");
        String sourceStr = (String) json.getOrDefault("source", Source.OTHER.name());
        Source source = Source.valueOf(sourceStr);
        
        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId, isPending, source, message
        );

    }

    public static Map<String,List<String>> getHeaders(Gson gson, Map json, String key) {
        Map headersFromRequest = gson.fromJson((String) json.get(key),Map.class);
        Map<String,List<String>> headers = new HashMap<>();
        for (Object k: headersFromRequest.keySet()) {
            List<String> values = headers.getOrDefault(k,new ArrayList<>());
            values.add(headersFromRequest.get(k).toString());
            headers.put(k.toString().toLowerCase(),values);
        }
        return headers;
    }

    public static String getAcceptableContentType(Map<String,List<String>> headers) {
        List<String> acceptableContentTypes = Arrays.asList(JSON_CONTENT_TYPE, FORM_URL_ENCODED_CONTENT_TYPE);
        List<String> contentTypeValues = new ArrayList<>();
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

    public void syncFunction(List<HttpResponseParams> responseParams)  {

        boolean isHarOrPcap = aggregate(responseParams);

        for (int apiCollectionId: aggregatorMap.keySet()) {
            URLAggregator aggregator = aggregatorMap.get(apiCollectionId);
            apiCatalogSync.computeDelta(aggregator, false, apiCollectionId);
        }

        this.sync_count += responseParams.size();
        if (this.sync_count >= sync_threshold_count || (Context.now() - this.last_synced) > this.sync_threshold_time || isHarOrPcap) {
            apiCatalogSync.syncWithDB();
            this.last_synced = Context.now();
            this.sync_count = 0;
        }
    }

    Map<Integer, URLAggregator> aggregatorMap = new HashMap<>();
    public boolean aggregate(List<HttpResponseParams> responses) {
        int count = 0;
        boolean ret = false;
        for (HttpResponseParams responseParams: responses) {
            if (responseParams.getSource() == Source.HAR || responseParams.getSource() == Source.PCAP) {
                ret = true;
            }
            try {
                int collId = responseParams.getRequestParams().getApiCollectionId();
                URLAggregator aggregator = aggregatorMap.get(collId);
                if (aggregator == null) {
                    aggregator = new URLAggregator();
                    aggregatorMap.put(collId, aggregator);
                }

                aggregator.addURL(responseParams);
                count++;
            } catch (Exception  e) {
                
            }
        }
        
        logger.info("added " + count + " urls");
        return ret;
    }

    public int getLastSyncTime() {
        return this.last_synced;
    }

    public int getSyncCount() {
        return this.sync_count;
    }
}
