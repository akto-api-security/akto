package com.akto.dto.traffic;

import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

public class TrafficInfo {
    
    @BsonId
    Key id;
    public Map<String, Integer> mapHoursToCount;

    public TrafficInfo() {
    }

    public TrafficInfo(Key id, Map<String,Integer> mapHoursToCount) {
        this.id = id;
        this.mapHoursToCount = mapHoursToCount;
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

    @Override
    public String toString() {
        return "{" +
            " _id='" + getId() + "'" +
            ", mapHoursToCount='" + getMapHoursToCount() + "'" +
            "}";
    }
}
