package com.akto.dto.type;

import java.util.HashMap;
import java.util.Map;

import com.akto.types.CappedList;

public class TrafficRecorder {
    
    Map<String, Integer> trafficMapSinceLastSync = new HashMap<>();
    CappedList<String> sampleMessages = new CappedList<String>(10, true);
    public void incr(int timestamp) {
        int hoursSinceEpoch = timestamp/3600;
        trafficMapSinceLastSync.compute(""+hoursSinceEpoch, (k,v) -> { return v == null ? 1 : ++v;});
    }

    public TrafficRecorder() {
    }

    public void recordMessage(String message) {
        this.sampleMessages.add(message);
    }

    public void mergeFrom(TrafficRecorder that) {
        for(String sampleMessage: that.getSampleMessages().get()) {
            this.sampleMessages.add(sampleMessage);
        }
        
        for(String hourSinceEpoch: that.trafficMapSinceLastSync.keySet()) {
            int count = that.trafficMapSinceLastSync.getOrDefault(hourSinceEpoch, 0);
            trafficMapSinceLastSync.compute(hourSinceEpoch, (k,v) -> {return (v == null ? 0 : v) + count;});
        }
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

    public CappedList<String> getSampleMessages() {
        return this.sampleMessages;
    }

    @Override
    public String toString() {
        return "{" +
            " trafficMapSinceLastSync='" + getTrafficMapSinceLastSync() + "'" +
            "}";
    }
}
