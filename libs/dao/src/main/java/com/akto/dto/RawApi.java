package com.akto.dto;

import java.util.List;
import java.util.Map;

import com.akto.dto.type.RequestTemplate;
import com.mongodb.BasicDBObject;

public class RawApi {

    private OriginalHttpRequest request;
    private OriginalHttpResponse response;
    private String originalMessage;

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
        BasicDBObject payload = BasicDBObject.parse(reqBody);
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
        queryParams = queryParams.substring(0, queryParams.length() - 1);

        // recheck
        req.setQueryParams(queryParams);
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
