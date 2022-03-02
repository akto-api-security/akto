package com.akto.dto;


import java.util.ArrayList;
import java.util.List;

public class FilterSampleData {
    private ApiInfo.ApiInfoKey id;
    public static final String FILTER_ID = "filterId";
    private int filterId;
    public static final String SAMPLES = "samples";
    private List<String> samples;

    public static final int cap = 10;

    public FilterSampleData() {
    }


    public FilterSampleData(ApiInfo.ApiInfoKey id, Integer filterId) {
        this.id = id;
        this.filterId = filterId;
        this.samples = new ArrayList<>();
    }

    public ApiInfo.ApiInfoKey getId() {
        return id;
    }

    public void setId(ApiInfo.ApiInfoKey id) {
        this.id = id;
    }

    public List<String> getSamples() {
        return samples;
    }

    public void setSamples(List<String> samples) {
        this.samples = samples;
    }

    public int getFilterId() {
        return filterId;
    }

    public void setFilterId(int filterId) {
        this.filterId = filterId;
    }
}
