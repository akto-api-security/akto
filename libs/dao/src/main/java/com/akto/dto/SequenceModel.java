package com.akto.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SequenceModel {

    public static final String API_COLLECTION_ID = "apiCollectionId";
    private int apiCollectionId;

    public static final String CONTEXT = "context";
    private List<String> context;

    public static final String CONTEXT_KEY = "contextKey";
    private String contextKey;

    public static final String PARENT_CONTEXT_KEY = "parentContextKey";
    private String parentContextKey;

    public static final String NEXT = "next";
    private String next;

    public static final String ORDER = "order";
    private int order;

    public static final String COUNT_24H = "count24h";
    private long count24h;

    public static final String CONTEXT_TOTAL_24H = "contextTotal24h";
    private long contextTotal24h;

    public static final String PROBABILITY = "probability";
    private double probability;

    public static final String CI_LOW = "ciLow";
    private double ciLow;

    public static final String CI_HIGH = "ciHigh";
    private double ciHigh;

    public static final String COLLAPSED = "collapsed";
    private boolean collapsed;

    public static final String PRECEDENCE = "precedence";
    private double precedence;

    public static final String UPDATED_AT = "updatedAt";
    private int updatedAt;
}
