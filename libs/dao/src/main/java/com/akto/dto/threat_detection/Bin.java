package com.akto.dto.threat_detection;

public class Bin {
    int binId;
    long count;

    public Bin(int binId, long count) {
        this.binId = binId;
        this.count = count;
    }

    public int getBinId() {
        return binId;
    }

    public long getCount() {
        return count;
    }
}