package com.akto.dto.traffic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SampleData {
    Key id;
    List<String> samples;
    List<Integer> collectionIds;

    public SampleData() {
    }

    public SampleData(Key id, List<String> samples) {
        this.id = id;
        this.samples = samples;
        this.collectionIds = Arrays.asList(id.getApiCollectionId());
    }

    public Key getId() {
        return this.id;
    }

    public void setId(Key id) {
        this.id = id;
        if(this.collectionIds==null){
            this.collectionIds = new ArrayList<>();
        }
        if(id!=null && !this.collectionIds.contains(id.getApiCollectionId())){
            this.collectionIds.add(id.getApiCollectionId());
        }
    }

    public List<String> getSamples() {
        return this.samples;
    }

    public void setSamples(List<String> samples) {
        this.samples = samples;
    }

    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", samples='" + getSamples() + "'" +
            "}";
    }


}
