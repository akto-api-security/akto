package com.akto.filters.aggregators.window_based;

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