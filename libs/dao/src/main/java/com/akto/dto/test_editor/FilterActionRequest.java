package com.akto.dto.test_editor;

import java.util.List;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.mongodb.BasicDBObject;

public class FilterActionRequest {
    
    private Object querySet;
    private RawApi rawApi;
    private RawApi testRunRawApi;
    private ApiInfo.ApiInfoKey apiInfoKey;
    private String concernedProperty;
    private String concernedSubProperty;
    private List<String> matchingKeySet;
    private List<BasicDBObject> contextEntities;
    private String operand;
    private String context;
    private Boolean keyValOperandSeen;
    private String bodyOperand;
    private String contextProperty;
    private String collectionProperty;

    public FilterActionRequest(Object querySet, RawApi rawApi, RawApi testRunRawApi, ApiInfo.ApiInfoKey apiInfoKey,
            String concernedProperty, String concernedSubProperty, List<String> matchingKeySet, List<BasicDBObject> contextEntities,
            String operand, String context, Boolean keyValOperandSeen, String bodyOperand, String contextProperty, String collectionProperty) {
        this.querySet = querySet;
        this.rawApi = rawApi;
        this.testRunRawApi = testRunRawApi;
        this.apiInfoKey = apiInfoKey;
        this.concernedProperty = concernedProperty;
        this.concernedSubProperty = concernedSubProperty;
        this.matchingKeySet = matchingKeySet;
        this.contextEntities = contextEntities;
        this.operand = operand;
        this.context = context;
        this.keyValOperandSeen = keyValOperandSeen;
        this.bodyOperand = bodyOperand;
        this.contextProperty = contextProperty;
        this.collectionProperty = collectionProperty;
    }

    public FilterActionRequest() { }

    public Object getQuerySet() {
        return querySet;
    }

    public void setQuerySet(Object querySet) {
        this.querySet = querySet;
    }

    public RawApi getRawApi() {
        return rawApi;
    }

    public void setRawApi(RawApi rawApi) {
        this.rawApi = rawApi;
    }

    public RawApi getTestRunRawApi() {
        return testRunRawApi;
    }

    public void setTestRunRawApi(RawApi testRunRawApi) {
        this.testRunRawApi = testRunRawApi;
    }

    public ApiInfo.ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(ApiInfo.ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public String getConcernedProperty() {
        return concernedProperty;
    }

    public void setConcernedProperty(String concernedProperty) {
        this.concernedProperty = concernedProperty;
    }

    public String getConcernedSubProperty() {
        return concernedSubProperty;
    }

    public void setConcernedSubProperty(String concernedSubProperty) {
        this.concernedSubProperty = concernedSubProperty;
    }

    public List<String> getMatchingKeySet() {
        return matchingKeySet;
    }

    public void setMatchingKeySet(List<String> matchingKeySet) {
        this.matchingKeySet = matchingKeySet;
    }

    public List<BasicDBObject> getContextEntities() {
        return contextEntities;
    }

    public void setContextEntities(List<BasicDBObject> contextEntities) {
        this.contextEntities = contextEntities;
    }

    public String getOperand() {
        return operand;
    }

    public void setOperand(String operand) {
        this.operand = operand;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public RawApi fetchRawApiBasedOnContext() {
        if (this.getContext().equals("filter")) {
            return this.getRawApi();
        } else {
            return this.getTestRunRawApi();
        }
    }

    public RawApi fetchRawApi(Boolean isSample) {
        if (isSample) {
            return this.getRawApi();
        } else {
            return this.getTestRunRawApi();
        }
    }

    public Boolean getKeyValOperandSeen() {
        return keyValOperandSeen;
    }

    public void setKeyValOperandSeen(Boolean keyValOperandSeen) {
        this.keyValOperandSeen = keyValOperandSeen;
    }

    public String getBodyOperand() {
        return bodyOperand;
    }

    public void setBodyOperand(String bodyOperand) {
        this.bodyOperand = bodyOperand;
    }

    public String getContextProperty() {
        return contextProperty;
    }

    public void setContextProperty(String contextProperty) {
        this.contextProperty = contextProperty;
    }

    public String getCollectionProperty() {
        return collectionProperty;
    }

    public void setCollectionProperty(String collectionProperty) {
        this.collectionProperty = collectionProperty;
    }

    public boolean isValidationContext() {
        return !"filter".equals(this.context);
    }
}
