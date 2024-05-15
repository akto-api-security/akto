package com.akto.dto;

import java.util.Map;

public class PolicyCatalog {
    private ApiInfo apiInfo;
    private Map<Integer, FilterSampleData> filterSampleDataMap;
    private boolean seenEarlier = false;

    public PolicyCatalog() {}

    public PolicyCatalog(ApiInfo apiInfo, Map<Integer, FilterSampleData> filterSampleDataMap) {
        this.apiInfo = apiInfo;
        this.filterSampleDataMap = filterSampleDataMap;
    }

    public ApiInfo getApiInfo() {
        return apiInfo;
    }

    public void setApiInfo(ApiInfo apiInfo) {
        this.apiInfo = apiInfo;
    }

    public Map<Integer, FilterSampleData> getFilterSampleDataMap() {
        return filterSampleDataMap;
    }

    public void setFilterSampleDataMap(Map<Integer, FilterSampleData> filterSampleDataMap) {
        this.filterSampleDataMap = filterSampleDataMap;
    }

    public boolean isSeenEarlier() {
        return seenEarlier;
    }

    public void setSeenEarlier(boolean seenEarlier) {
        this.seenEarlier = seenEarlier;
    }
}
