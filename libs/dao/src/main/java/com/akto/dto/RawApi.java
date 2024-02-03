package com.akto.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.akto.dto.type.RequestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;

public class RawApi {

    private OriginalHttpRequest request;
    private OriginalHttpResponse response;
    private String originalMessage;
    ObjectMapper om = new ObjectMapper();

    public RawApi(OriginalHttpRequest request, OriginalHttpResponse response, String originalMessage) {
        this.request = request;
        this.response = response;
        this.originalMessage = originalMessage;
    }

    public static RawApi buildFromMessage(String message) {
        OriginalHttpRequest request = new OriginalHttpRequest();
        request.buildFromSampleMessage(message);

        OriginalHttpResponse response = new OriginalHttpResponse();
        response.buildFromSampleMessage(message);

        return new RawApi(request, response, message);
    }

    public BasicDBObject fetchReqPayload() {
        OriginalHttpRequest req = this.getRequest();
        String reqBody = req.getBody();
        BasicDBObject payload;
        try {
             payload = BasicDBObject.parse(reqBody);
        } catch (Exception e) {
            payload = new BasicDBObject();
        }
        return payload;
    }

    public void modifyReqPayload(BasicDBObject payload) {
        OriginalHttpRequest req = this.getRequest();
        req.setBody(payload.toJson());
        this.setRequest(req);
    }

    public void modifyUrl(String url) {
        OriginalHttpRequest req = this.getRequest();
        req.setUrl(url);
        this.setRequest(req);
    }

    public void modifyMethod(String method) {
        OriginalHttpRequest req = this.getRequest();
        req.setMethod(method);
        this.setRequest(req);
    }

    public Map<String, List<String>> fetchReqHeaders() {
        OriginalHttpRequest req = this.getRequest();
        return req.getHeaders();
    }

    public void modifyReqHeaders(Map<String, List<String>> headers) {
        OriginalHttpRequest req = this.getRequest();
        req.setHeaders(headers);
        this.setRequest(req);
    }

    public BasicDBObject fetchQueryParam() {
        OriginalHttpRequest req = this.getRequest();
        String queryParams = req.getQueryParams();
        String url = req.getUrl();
        BasicDBObject queryParamObj = RequestTemplate.getQueryJSON(url + "?" + queryParams);
        return queryParamObj;
    }

    public void modifyQueryParam(BasicDBObject payload) {
        OriginalHttpRequest req = this.getRequest();

        String queryParams = "";
        for (String key: payload.keySet()) {
            queryParams = queryParams + key + "=" + payload.get(key) + "&";
        }
        if (queryParams.length() > 0) {
            queryParams = queryParams.substring(0, queryParams.length() - 1);
        }

        // recheck
        req.setQueryParams(queryParams);
    }

    public boolean equals(RawApi compareWithRawApi) {
        String payload = this.getRequest().getBody().replaceAll("\\s+","");
        String compareWithPayload = compareWithRawApi.getRequest().getBody().replaceAll("\\s+","");
        
        if (!isPayloadEqual(payload, compareWithPayload)) {
            return false;
        }
        
        // System.out.println(m1);
        // System.out.println(m2);
        // System.out.println(m1.equals(m2));
        // if (!payload.equalsIgnoreCase(compareWithPayload)) {
        //     return false;
        // }

        Map<String, List<String>> headers = this.getRequest().getHeaders();
        Map<String, List<String>> compareWithHeaders = compareWithRawApi.getRequest().getHeaders();
        if (headers.size() != compareWithHeaders.size()) {
            return false;
        }

        for (String k: headers.keySet()) {
            List<String> val = headers.get(k);
            List<String> cVal = compareWithHeaders.get(k);
            if (cVal == null || (cVal.size() != val.size())) {
                return false;
            }

            List<String> sourceList = new ArrayList<String>(val);
            List<String> destinationList = new ArrayList<String>(cVal);
            sourceList.removeAll(destinationList);
            if (sourceList.size() > 0) {
                return false;
            }
        }

        String url = this.getRequest().getFullUrlWithParams().trim();
        String cUrl = compareWithRawApi.getRequest().getFullUrlWithParams().trim();
        if (!url.equalsIgnoreCase(cUrl)) {
            return false;
        }

        return true;
    }

    public boolean isPayloadEqual(String payload, String compareWithPayload) {

        if (payload == null) {
            return compareWithPayload == null;
        }

        if (payload.equals("")) {
            return compareWithPayload.equals("");
        }

        boolean areBothJson = true;
        boolean areBothNonJson = true;
        Map<String, Object> m1 = new HashMap<>();
        Map<String, Object> m2 = new HashMap<>();
        try {
            m1 = (Map<String, Object>)(om.readValue(payload, Map.class));
            areBothNonJson = false;
        } catch (Exception e) {
            areBothJson = false;
        }

        try {
            m2 = (Map<String, Object>)(om.readValue(compareWithPayload, Map.class));
            areBothNonJson = false;
        } catch (Exception e) {
            areBothJson = false;
        }

        if (areBothNonJson && payload.equals(compareWithPayload)) {
            return true;
        }
       
        if (!areBothJson) {
            return false;
        }

        if (!m1.equals(m2)) {
            return false;
        }
        return true;
    }

    public RawApi copy() {
        return new RawApi(this.request.copy(), this.response.copy(), this.originalMessage);
    }

    public RawApi() { }

    public OriginalHttpRequest getRequest() {
        return request;
    }

    public void setRequest(OriginalHttpRequest request) {
        this.request = request;
    }

    public OriginalHttpResponse getResponse() {
        return response;
    }

    public void setResponse(OriginalHttpResponse response) {
        this.response = response;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }
}
