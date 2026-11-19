package com.akto.dto.traffic;

import java.util.Arrays;
import java.util.List;

import com.akto.util.Util;

public class SampleData {
    Key id;

    public static final String SAMPLES = "samples";
    List<String> samples;
    public static final String SAMPLE_IDS = "sampleIds";
    List<String> sampleIds;
    public static final String EVENTS = "events";
    List<String> events;
    List<String> traces;

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
        id == null ? null : (Integer)id.getApiCollectionId(), 
        this.id == null ? null : (Integer)this.id.getApiCollectionId());
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

    public List<String> getSampleIds() {
        return sampleIds;
    }

    public void setSampleIds(List<String> sampleIds) {
        this.sampleIds = sampleIds;
    }

    public List<String> getEvents() {
        return events;
    }

    public void setEvents(List<String> events) {
        this.events = events;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", samples='" + getSamples() + "'" +
            "}";
    }


}
