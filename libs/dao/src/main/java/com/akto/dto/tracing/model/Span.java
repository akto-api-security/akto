package com.akto.dto.tracing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Span {

    @BsonId
    private String id;
    private String traceId;
    private String parentSpanId;
    private String spanKind; // workflow, agent, llm, tool, mcp_server, rag, vector_db, embedding, reranker, api, http, database, cache, queue, stream, function, task, batch, custom, unknown
    private String name;
    private Long startTimeMillis;
    private Long endTimeMillis;
    private String status;
    private String errorMessage;
    private String stackTrace;
    private Map<String, Object> input;
    private Map<String, Object> output;
    private Map<String, Object> metadata;
    private String modelName;
    private ToolDefinition toolDefinition;
    private int depth;  
    private List<String> tags;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolDefinition {
        private String name;
        private String description;
        private String type;
        private Map<String, Object> parameters;
        private Map<String, Object> schema;
    }
}
