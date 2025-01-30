package com.akto.dto.traffic;

import java.util.Arrays;
import java.util.List;
import com.akto.util.Util;

public class SampleData {
    Key id;

    public static final String SAMPLES = "samples";
    List<String> samples;
    List<Integer> collectionIds;

    public SampleData() {
    }

    public SampleData(Key id, List<String> samples) {
        this.id = id;
        this.samples = samples;
        if(id != null){
            this.collectionIds = Arrays.asList(id.getApiCollectionId());
        }
    }

    public Key getId() {
        return this.id;
    }

    public void setId(Key id) {
        this.collectionIds = Util.replaceElementInList(this.collectionIds, 
        id == null ? null : (Integer) id.getApiCollectionId(), 
        this.id == null ? null : (Integer) this.id.getApiCollectionId());
        this.id = id;
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
