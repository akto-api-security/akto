package com.akto.dto;


import com.akto.types.CappedList;

import java.util.Arrays;
import java.util.List;

public class FilterSampleData {
    private FilterKey id;
    public static final String SAMPLES = "samples";
    private CappedList<String> samples;
    List<Integer> collectionIds;

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
        this.samples = new CappedList<>(cap, true);
        if(id != null){
            this.collectionIds = Arrays.asList(id.getApiCollectionId());
        }
    }

    public void merge(FilterSampleData that) {
        for (String s: that.samples.get()) {
            if (this.getSamples().get().size() > cap) {
                return;
            }
            this.getSamples().get().add(s);
        }
    }

    public FilterKey getId() {
        return id;
    }

    public void setId(FilterKey id) {
        this.id = id;
    }

    public CappedList<String> getSamples() {
        return samples;
    }

    public void setSamples(CappedList<String> samples) {
        this.samples = samples;
    }

    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
    }
}
