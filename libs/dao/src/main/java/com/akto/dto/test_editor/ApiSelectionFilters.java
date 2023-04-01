package com.akto.dto.test_editor;

import java.util.List;

public class ApiSelectionFilters {
    
    private List<String> urlContains;

    private List<ContainsParam> containsParams;

    private String respStatus;

    private Boolean authenticated;

    private String apiType;

    private List<String> excludeMethods;

    private Boolean isPrivate;

    private List<String> mustContainHeaders;

    private List<String> userIdParamRegex;
    
    private String versionMatchRegex;

    private List<String> paginationKeyWords;

    private List<MustContainKeys> mustContainKeys;

    public ApiSelectionFilters(List<String> urlContains, List<ContainsParam> containsParams, String respStatus, Boolean authenticated, String apiType,
            List<String> excludeMethods, Boolean isPrivate, List<String> mustContainHeaders, List<String> userIdParamRegex,
            String versionMatchRegex, List<String> paginationKeyWords, List<MustContainKeys> mustContainKeys) {
        this.urlContains = urlContains;
        this.containsParams = containsParams;
        this.respStatus = respStatus;
        this.authenticated = authenticated;
        this.apiType = apiType;
        this.excludeMethods = excludeMethods;
        this.isPrivate = isPrivate;
        this.mustContainHeaders = mustContainHeaders;
        this.userIdParamRegex = userIdParamRegex;
        this.versionMatchRegex = versionMatchRegex;
        this.paginationKeyWords = paginationKeyWords;
        this.mustContainKeys = mustContainKeys;
    }

    public ApiSelectionFilters() { }

    public List<String> getUrlContains() {
        return urlContains;
    }

    public void setUrlContains(List<String> urlContains) {
        this.urlContains = urlContains;
    }

    public String getRespStatus() {
        return respStatus;
    }

    public void setRespStatus(String respStatus) {
        this.respStatus = respStatus;
    }

    public Boolean getAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(Boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String getApiType() {
        return apiType;
    }

    public void setApiType(String apiType) {
        this.apiType = apiType;
    }

    public List<String> getExcludeMethods() {
        return excludeMethods;
    }

    public void setExcludeMethods(List<String> excludeMethods) {
        this.excludeMethods = excludeMethods;
    }

    public Boolean getIsPrivate() {
        return isPrivate;
    }

    public void setIsPrivate(Boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public List<String> getMustContainHeaders() {
        return mustContainHeaders;
    }

    public void setMustContainHeaders(List<String> mustContainHeaders) {
        this.mustContainHeaders = mustContainHeaders;
    }

    public List<String> getUserIdParamRegex() {
        return userIdParamRegex;
    }

    public void setUserIdParamRegex(List<String> userIdParamRegex) {
        this.userIdParamRegex = userIdParamRegex;
    }

    public String getVersionMatchRegex() {
        return versionMatchRegex;
    }

    public void setVersionMatchRegex(String versionMatchRegex) {
        this.versionMatchRegex = versionMatchRegex;
    }

    public List<String> getPaginationKeyWords() {
        return paginationKeyWords;
    }

    public void setPaginationKeyWords(List<String> paginationKeyWords) {
        this.paginationKeyWords = paginationKeyWords;
    }

    public List<ContainsParam> getContainsParams() {
        return containsParams;
    }

    public void setContainsParams(List<ContainsParam> containsParams) {
        this.containsParams = containsParams;
    }
    
    public List<MustContainKeys> getMustContainKeys() {
        return mustContainKeys;
    }

    public void setMustContainKeys(List<MustContainKeys> mustContainKeys) {
        this.mustContainKeys = mustContainKeys;
    }

}
