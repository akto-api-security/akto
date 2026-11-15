package com.akto.dto;

import java.util.List;

public class SequenceCount {

    public static final String API_COLLECTION_ID = "apiCollectionId";
    private int apiCollectionId;

    public static final String CONTEXT = "context";
    private List<String> context;

    public static final String NEXT = "next";
    private String next;

    public static final String BUCKET_START = "bucketStart";
    private int bucketStart;

    public static final String COUNT = "count";
    private long count;

    public SequenceCount() {
    }

    public SequenceCount(int apiCollectionId, List<String> context, String next, int bucketStart, long count) {
        this.apiCollectionId = apiCollectionId;
        this.context = context;
        this.next = next;
        this.bucketStart = bucketStart;
        this.count = count;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public List<String> getContext() {
        return context;
    }

    public void setContext(List<String> context) {
        this.context = context;
    }

    public String getNext() {
        return next;
    }

    public void setNext(String next) {
        this.next = next;
    }

    public int getBucketStart() {
        return bucketStart;
    }

    public void setBucketStart(int bucketStart) {
        this.bucketStart = bucketStart;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return context + " -> " + next + " @" + bucketStart + " x" + count;
    }
}
