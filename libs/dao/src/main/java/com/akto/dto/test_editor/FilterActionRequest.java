package com.akto.dto.test_editor;

import java.util.List;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;

public class FilterActionRequest {
    
    private Object querySet;
    private RawApi rawApi;
    private RawApi testRunRawApi;
    private ApiInfo.ApiInfoKey apiInfoKey;
    private String concernedProperty;
    private String concernedSubProperty;
    private List<String> matchingKeySet;
    private String operand;
    private String context;
    private Boolean keyOperandSeen;

    public FilterActionRequest(Object querySet, RawApi rawApi, RawApi testRunRawApi, ApiInfo.ApiInfoKey apiInfoKey,
            String concernedProperty, String concernedSubProperty, List<String> matchingKeySet, String operand, String context, Boolean keyOperandSeen) {
        this.querySet = querySet;
        this.rawApi = rawApi;
        this.testRunRawApi = testRunRawApi;
        this.apiInfoKey = apiInfoKey;
        this.concernedProperty = concernedProperty;
        this.concernedSubProperty = concernedSubProperty;
        this.matchingKeySet = matchingKeySet;
        this.operand = operand;
        this.context = context;
        this.keyOperandSeen = keyOperandSeen;
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
        if (this.getContext() == "filter") {
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

    public Boolean getKeyOperandSeen() {
        return keyOperandSeen;
    }

    public void setKeyOperandSeen(Boolean keyOperandSeen) {
        this.keyOperandSeen = keyOperandSeen;
    }

}
