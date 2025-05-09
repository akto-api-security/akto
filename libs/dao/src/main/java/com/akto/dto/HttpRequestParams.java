package com.akto.dto;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpRequestParams {
    public String method; // POST
    public String url;
    public String type; // HTTP/1.1
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

    public HttpRequestParams resetValues(String method, String url, String type, Map<String, List<String>> headers, String payload, int apiCollectionId) {
        this.method = method;
        this.url = url;
        this.type = type;
        this.headers = headers;
        this.payload = payload;
        this.apiCollectionId = apiCollectionId;
        return this;
    }


    public HttpRequestParams copy() {
        return new HttpRequestParams(this.method, this.url, this.type, this.headers, this.payload, this.apiCollectionId);
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

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return this.method;
    }

    public String setMethod(String method) {
        this.method = method;
        return this.method;
    } 

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
}
