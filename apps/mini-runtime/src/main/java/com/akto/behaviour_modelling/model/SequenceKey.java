package com.akto.behaviour_modelling.model;

import java.util.Arrays;

/**
 * (hour bucket, context endpoint ids oldest first, next endpoint id). The last element of ids is next.
 */
public final class SequenceKey {

    private final int bucketStart;
    private final int[] ids;
    private final int hash;

    public SequenceKey(int bucketStart, int[] ids) {
        this.bucketStart = bucketStart;
        this.ids = ids;
        this.hash = 31 * bucketStart + Arrays.hashCode(ids);
    }

    public int getBucketStart() {
        return bucketStart;
    }

    public int[] getIds() {
        return ids;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SequenceKey)) return false;
        SequenceKey other = (SequenceKey) o;
        return bucketStart == other.bucketStart && Arrays.equals(ids, other.ids);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
