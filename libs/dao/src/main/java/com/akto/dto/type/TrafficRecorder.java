package com.akto.dto.type;

import java.util.HashMap;
import java.util.Map;

public class TrafficRecorder {
    
    Map<String, Integer> trafficMapSinceLastSync = new HashMap<>();

    public void incr(int timestamp) {
        int hoursSinceEpoch = timestamp/3600;
        trafficMapSinceLastSync.compute(""+hoursSinceEpoch, (k,v) -> { return v == null ? 1 : ++v;});
    }

    public TrafficRecorder() {
    }

    public boolean isEmpty() {
        return trafficMapSinceLastSync.isEmpty();
    }

    public TrafficRecorder(Map<String,Integer> trafficMapSinceLastSync) {
        this.trafficMapSinceLastSync = trafficMapSinceLastSync;
    }

    public Map<String,Integer> getTrafficMapSinceLastSync() {
        return this.trafficMapSinceLastSync;
    }

    public void setTrafficMapSinceLastSync(Map<String,Integer> trafficMapSinceLastSync) {
        this.trafficMapSinceLastSync = trafficMapSinceLastSync;
    }

    @Override
    public String toString() {
        return "{" +
            " trafficMapSinceLastSync='" + getTrafficMapSinceLastSync() + "'" +
            "}";
    }
}
