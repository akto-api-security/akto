package com.akto.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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

    public static final String CONTEXT_KEY = "contextKey";
    public static final String PARENT_CONTEXT_KEY = "parentContextKey";
    public static final String EXPIRES_AT = "expiresAt";
}
