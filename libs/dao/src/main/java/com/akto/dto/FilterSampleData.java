package com.akto.dto;


import java.util.ArrayList;
import java.util.List;

public class FilterSampleData {
    private FilterKey id;
    public static final String SAMPLES = "samples";
    private List<String> samples;

    public static final int cap = 10;

    public FilterSampleData() {
    }

    public static class FilterKey {
        public ApiInfo.ApiInfoKey apiInfoKey;
        public int filterId;

        public FilterKey() {
        }

        public FilterKey(ApiInfo.ApiInfoKey apiInfoKey, int filterId) {
            this.apiInfoKey = apiInfoKey;
            this.filterId = filterId;
        }

        public ApiInfo.ApiInfoKey getApiInfoKey() {
            return apiInfoKey;
        }

        public void setApiInfoKey(ApiInfo.ApiInfoKey apiInfoKey) {
            this.apiInfoKey = apiInfoKey;
        }

        public int getFilterId() {
            return filterId;
        }

        public void setFilterId(int filterId) {
            this.filterId = filterId;
        }
    }


    public FilterSampleData(ApiInfo.ApiInfoKey id, Integer filterId) {
        this.id = new FilterKey(id,filterId);
        this.samples = new ArrayList<>();
    }

    public FilterKey getId() {
        return id;
    }

    public void setId(FilterKey id) {
        this.id = id;
    }

    public List<String> getSamples() {
        return samples;
    }

    public void setSamples(List<String> samples) {
        this.samples = samples;
    }

}
