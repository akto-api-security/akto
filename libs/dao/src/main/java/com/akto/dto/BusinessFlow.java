package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessFlow {

    @BsonId
    private String id;

    private String scopeKey;
    public static final String SCOPE_KEY = "scopeKey";

    // lifecycle (steps within one entity) | journey (chain across entities)
    private String type;
    public static final String TYPE = "type";
    public static final String TYPE_LIFECYCLE = "lifecycle";
    public static final String TYPE_JOURNEY = "journey";

    private List<String> entityIds;
    private List<Node> nodes;
    private List<Edge> edges;

    // Journeys only: average comes-from and leads-to along the chain, and the weakest step's count
    private Double precedence;
    private Double probability;
    private Integer count;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Node {
        private String id;
        private String entityId;
        private String operation;
        private String action;
        private int repeats;
        private List<ExampleApi> exampleApis;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExampleApi {
        private int apiCollectionId;
        private String method;
        private String url;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Edge {
        private String from;
        private String to;
        // step (one direction) | loop (back and forth)
        private String kind;
        private int count;
        private Double leadsTo;
        private Double comesFrom;
        private Double dependency;
    }
}
