package com.akto.dto.test_editor;

import java.util.List;

import com.mongodb.BasicDBObject;

public class DataOperandsFilterResponse {
    
    private Boolean result;
    private List<String> matchedEntities;
    private List<BasicDBObject> contextEntities;
    private FilterNode extractNode;
    private String validationReason;

    public DataOperandsFilterResponse(Boolean result, List<String> matchedEntities, List<BasicDBObject> contextEntities, FilterNode extractNode) {
        this.result = result;
        this.matchedEntities = matchedEntities;
        this.contextEntities = contextEntities;
        this.extractNode = extractNode;
        this.validationReason = null;
    }

    public DataOperandsFilterResponse(Boolean result, List<String> matchedEntities, List<BasicDBObject> contextEntities, FilterNode extractNode, String validationReason) {
        this.result = result;
        this.matchedEntities = matchedEntities;
        this.contextEntities = contextEntities;
        this.extractNode = extractNode;
        this.validationReason = validationReason;
    }

    public DataOperandsFilterResponse() { }

    public Boolean getResult() {
        return result;
    }

    public void setResult(Boolean result) {
        this.result = result;
    }

    public List<String> getMatchedEntities() {
        return matchedEntities;
    }

    public void setMatchedEntities(List<String> matchedEntities) {
        this.matchedEntities = matchedEntities;
    }

    public List<BasicDBObject> getContextEntities() {
        return contextEntities;
    }

    public void setContextEntities(List<BasicDBObject> contextEntities) {
        this.contextEntities = contextEntities;
    }

    public FilterNode getExtractNode() {
        return extractNode;
    }

    public void setExtractNode(FilterNode extractNode) {
        this.extractNode = extractNode;
    }

    public String getValidationReason() {
        return validationReason;
    }

    public void setValidationReason(String validationReason) {
        this.validationReason = validationReason;
    }
}
