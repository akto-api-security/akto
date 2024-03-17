package com.akto.dto.traffic;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

public class TrafficInfo {
    
    @BsonId
    Key id;
    public Map<String, Integer> mapHoursToCount;
    List<Integer> collectionIds;

    public TrafficInfo() {
    }

    public TrafficInfo(Key id, Map<String,Integer> mapHoursToCount) {
        this.id = id;
        this.mapHoursToCount = mapHoursToCount;
        if(id != null){
            this.collectionIds = Arrays.asList(id.getApiCollectionId());
        }
    }

    public Key getId() {
        return this.id;
    }

    public void setId(Key id) {
        this.id = id;
    }

    public Map<String,Integer> getMapHoursToCount() {
        return this.mapHoursToCount;
    }

    public void setMapHoursToCount(Map<String,Integer> mapHoursToCount) {
        this.mapHoursToCount = mapHoursToCount;
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
            " _id='" + getId() + "'" +
            ", mapHoursToCount='" + getMapHoursToCount() + "'" +
            "}";
    }
}
