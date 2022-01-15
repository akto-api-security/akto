package com.akto.dto.traffic;

import java.util.Map;

import com.akto.dto.type.URLMethods.Method;

import org.bson.codecs.pojo.annotations.BsonId;

public class TrafficInfo {
    
    public static class Key {
        int apiCollectionId;
        public String url;
        public Method method;
        public int responseCode;
        int bucketStartEpoch;
        int bucketEndEpoch;

        public Key() {}

        public Key(int apiCollectionId, String url, Method method, int responseCode, int bucketStartEpoch, int bucketEndEpoch) {
            this.apiCollectionId = apiCollectionId;
            this.url = url;
            this.method = method;
            this.responseCode = responseCode;
            this.bucketStartEpoch = bucketStartEpoch;
            this.bucketEndEpoch = bucketEndEpoch;
        }

        public int getApiCollectionId() {
            return this.apiCollectionId;
        }

        public void setApiCollectionId(int apiCollectionId) {
            this.apiCollectionId = apiCollectionId;
        }

        public String getUrl() {
            return this.url;
        }
    
        public void setUrl(String url) {
            this.url = url;
        }
    
        public Method getMethod() {
            return this.method;
        }
    
        public void setMethod(Method method) {
            this.method = method;
        }
    
        public int getResponseCode() {
            return this.responseCode;
        }
    
        public void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }
    
        public int getBucketStartEpoch() {
            return this.bucketStartEpoch;
        }
    
        public void setBucketStartEpoch(int bucketStartEpoch) {
            this.bucketStartEpoch = bucketStartEpoch;
        }
    
        public int getBucketEndEpoch() {
            return this.bucketEndEpoch;
        }
    
        public void setBucketEndEpoch(int bucketEndEpoch) {
            this.bucketEndEpoch = bucketEndEpoch;
        }
    
    }

    @BsonId
    Key _id;
    public Map<Integer, Integer> mapHoursToCount;

    public TrafficInfo() {
    }

    public TrafficInfo(Key _id, Map<Integer,Integer> mapHoursToCount) {
        this._id = _id;
        this.mapHoursToCount = mapHoursToCount;
    }

    public Key get_id() {
        return this._id;
    }

    public void set_id(Key _id) {
        this._id = _id;
    }

    public Map<Integer,Integer> getMapHoursToCount() {
        return this.mapHoursToCount;
    }

    public void setMapHoursToCount(Map<Integer,Integer> mapHoursToCount) {
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
