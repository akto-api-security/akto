package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessEntityApi {

    @BsonId
    private String id;

    private String scopeKey;
    public static final String SCOPE_KEY = "scopeKey";

    // Slug of the business entity this API operates on
    private String entityId;
    public static final String ENTITY_ID = "entityId";

    // create | read | list | update | delete | action (plumbing / none are never served)
    private String operation;
    public static final String OPERATION = "operation";

    private Api api;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {
        private int apiCollectionId;
        private String method;
        private String url;
        private String hostName;
    }
}
