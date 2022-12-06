package com.akto.dto.testing;

import com.akto.dto.ApiInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class LogicalGroupTestingEndpoint extends TestingEndpoints{
    private String regex;
    private List<ApiInfo.ApiInfoKey> includedApiInfoKey;
    private List<ApiInfo.ApiInfoKey> excludedApiInfoKey;

    public LogicalGroupTestingEndpoint(Type type) {
        super(Type.LOGICAL_GROUP);
    }

    @Override
    public boolean containsApi (ApiInfo.ApiInfoKey key) {
        return true;
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }

    public List<ApiInfo.ApiInfoKey> getIncludedApiInfoKey() {
        return includedApiInfoKey;
    }

    public void setIncludedApiInfoKey(List<ApiInfo.ApiInfoKey> includedApiInfoKey) {
        this.includedApiInfoKey = includedApiInfoKey;
    }

    public List<ApiInfo.ApiInfoKey> getExcludedApiInfoKey() {
        return excludedApiInfoKey;
    }

    public void setExcludedApiInfoKey(List<ApiInfo.ApiInfoKey> excludedApiInfoKey) {
        this.excludedApiInfoKey = excludedApiInfoKey;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        ArrayList<ApiInfo.ApiInfoKey> list = new ArrayList<>();

        return list;
    }
}
