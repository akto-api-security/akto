package com.akto.parsers;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;

import com.google.gson.Gson;
import com.mongodb.ConnectionString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpCallParser {
    
    private static final String AKTO_REQUEST = "##AKTO_REQUEST##";
    private static final String AKTO_RESPONSE = "##AKTO_RESPONSE##";
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

        public HttpRequestParams() {}

        public HttpRequestParams(String method, String url, String type, Map<String, List<String>> headers, String payload) {
            this.method = method;
            this.url = url;
            this.type = type;
            this.headers = headers;
            this.payload = payload;
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
    }

    public static class HttpResponseParams {
        String accountId;
        String type; // HTTP/1.1
        int statusCode; // 200
        String status; // OK
        Map<String, List<String>> headers = new HashMap<>();
        private String payload;
        private int time;
        HttpRequestParams requestParams;

        public HttpResponseParams() {}

        public HttpResponseParams(String type, int statusCode, String status, Map<String, List<String>> headers,
                                  String payload, HttpRequestParams requestParams, int time, String accountId) {
            this.type = type;
            this.statusCode = statusCode;
            this.status = status;
            this.headers = headers;
            this.payload = payload;
            this.requestParams = requestParams;
            this.time = time;
            this.accountId = accountId;
        }

        public static List<HttpResponseParams> parseResponse(String response) throws IOException {

            List<HttpResponseParams> ret = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new StringReader(response));
            String line = reader.readLine();
            while(true) {
                HttpResponseParams httpResponseParams = new HttpResponseParams();
                String[] tokens = line.split(" ");
                httpResponseParams.type = tokens[0];
                httpResponseParams.statusCode = Integer.parseInt(tokens[1]);
                httpResponseParams.status = tokens[2];

                int contentLength = 0;

                while((line = reader.readLine()) != null) {
                    if (line.length() > 0 && line.charAt(0) != '{') {
                        tokens = line.split(": ");
                        List<String> headerValues = httpResponseParams.headers.get(tokens[0]);
                        if (headerValues == null) {
                            headerValues = new ArrayList<>();
                            httpResponseParams.headers.put(tokens[0], headerValues);
                        }
                        
                        headerValues.add(tokens[1]);
                        if (tokens[0].toLowerCase().equals("content-length")) {
                            contentLength = Integer.parseInt(tokens[1]);
                        }

                    } else {
                        break;
                    }
                }

                char[] content = new char[contentLength];

                reader.read(content);

                String payload = new String(content);
                httpResponseParams.setPayload(payload);
                ret.add(httpResponseParams);
                String restOfLine = reader.readLine();
                if (restOfLine.length() > 0) {
                    line = restOfLine;
                } else {
                    break;
                }
            }

            return ret;
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
    }

    APICatalogSync apiCatalogSync;
    public HttpCallParser(String userIdentifier, int thresh, int sync_threshold_count, int sync_threshold_time) {
        last_synced = Context.now();
        this.sync_threshold_count = sync_threshold_count;
        this.sync_threshold_time = sync_threshold_time;
        apiCatalogSync = new APICatalogSync(userIdentifier,thresh);

    }
    
    public static int counter = 0;
    public List<HttpResponseParams> parseFile(String filePath) throws IOException {
        counter++;
        List<HttpResponseParams> responseParams = new ArrayList<>();
        try {

            FileReader fileReader = new FileReader(new File(filePath));
            BufferedReader reader = new BufferedReader(fileReader);

            String line = null;
            String str = "";

            boolean isRequest = true;
            List<HttpRequestParams> requestParams = null;
            while((line = reader.readLine()) != null) {
                if (AKTO_REQUEST.equals(line) || AKTO_RESPONSE.equals(line)) {
                    
                    if (isRequest) {
                        requestParams = processStrRequest(str);
                    } else {
                        responseParams.addAll(processStrResponse(str, requestParams));

                        requestParams = null;
                    }
                    str = "";
                    isRequest = AKTO_REQUEST.equals(line);
                } else {
                    str += (line+"\n");
                }
            }
        } catch (FileNotFoundException e) {
            
        }

        return responseParams;

    }

    public static HttpResponseParams parseKafkaMessage(String message) {
        Gson gson = new Gson();

        //convert java object to JSON format
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String method = (String) json.get("method");
        String url = (String) json.get("path");
        String type = (String) json.get("type");
        String requestPayload = (String) json.get("requestPayload");

        Map<String,List<String>> requestHeaders = getHeaders(gson, json, "requestHeaders");
        HttpRequestParams requestParams = new HttpRequestParams(
                method,url,type, requestHeaders, requestPayload
        );

        int statusCode = Integer.parseInt(json.get("statusCode").toString());
        String status = (String) json.get("status");
        Map<String,List<String>> responseHeaders = getHeaders(gson, json, "responseHeaders");
        String payload = (String) json.get("responsePayload");
        int time = Integer.parseInt(json.get("time").toString());
        String accountId = (String) json.get("akto_account_id");

        return new HttpResponseParams(
                type,statusCode, status, responseHeaders, payload, requestParams, time, accountId
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

    public static void main(String[] args) throws IOException {
        String mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";
        DaoInit.init(new ConnectionString(mongoURI));
        Context.accountId.set(77);
        List<String> kk = new ArrayList<>();

        FileInputStream fstream = new FileInputStream("/home/avneesh/Desktop/akto-security/api-runtime/src/resources/log_check.json");
        BufferedReader br = new BufferedReader(new InputStreamReader(fstream));
        String strLine;
        while ((strLine = br.readLine()) != null)   {
            if (strLine.length() < 10) {
                continue;
            }
            kk.add(strLine);
        }
        fstream.close();

        HttpCallParser h =  new HttpCallParser("access-token", 0,0,10);
        List<HttpCallParser.HttpResponseParams> hh = new ArrayList<>();
        for (String p: kk) {
            hh.add(h.parseKafkaMessage(p));
        }
        h.syncFunction(hh);
    }

    public void syncFunction(List<HttpResponseParams> responseParams)  {
        apiCatalogSync.buildFromDB(1);

        aggregate(responseParams);

        apiCatalogSync.computeDelta(aggregator, false);

        this.sync_count += responseParams.size();
        if (this.sync_count >= sync_threshold_count || (Context.now() - this.last_synced) > this.sync_threshold_time) {
            apiCatalogSync.syncWithDB();
            this.last_synced = Context.now();
            this.sync_count = 0;
        }
    }

    URLAggregator aggregator = new URLAggregator();
    public void aggregate(List<HttpResponseParams> responses) {
        int count = 0;
        for (HttpResponseParams responseParams: responses) {
            try {
                aggregator.addURL(responseParams);
                count++;
            } catch (Exception  e) {
                
            }
        }
        
        logger.info("added " + count + " urls");
        return;
    }

    private static List<HttpResponseParams> processStrResponse(String str, List<HttpRequestParams> requestParams) throws IOException {
        if (str == null || str.length() == 0) return new ArrayList<>();

        List<HttpResponseParams> ret  = HttpResponseParams.parseResponse(str);

        if (ret != null && ret.size() > 0 && requestParams != null && requestParams.size() > 0) {
            if (ret.size() == requestParams.size()) {
                for (int i = 0;i < ret.size(); i++) {
                    ret.get(i).requestParams = requestParams.get(i);
                }
            }
        }

        return ret;
    }

    private static List<HttpRequestParams> processStrRequest(String str) throws IOException {
        if (str == null || str.length() == 0) return new ArrayList<>();

        return HttpRequestParams.parseRequest(str);
    }

}
