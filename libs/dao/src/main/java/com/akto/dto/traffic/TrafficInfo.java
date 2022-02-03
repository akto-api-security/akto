package com.akto.dto.traffic;

import java.util.Map;

import org.bson.codecs.pojo.annotations.BsonId;

public class TrafficInfo {
    
    @BsonId
    Key _id;
    public Map<String, Integer> mapHoursToCount;

    public TrafficInfo() {
    }

    public TrafficInfo(Key _id, Map<String,Integer> mapHoursToCount) {
        this._id = _id;
        this.mapHoursToCount = mapHoursToCount;
    }

    public Key get_id() {
        return this._id;
    }

    public void set_id(Key _id) {
        this._id = _id;
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
            " _id='" + get_id() + "'" +
            ", mapHoursToCount='" + getMapHoursToCount() + "'" +
            "}";
    }
}
