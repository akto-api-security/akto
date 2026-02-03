package com.akto.dto.tracing;

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
public class Trace {
    @BsonId
    private String id;
    private String rootSpanId;
    private String aiAgentName;
    private String environment;
    private String name;
    private Long startTimeMillis;
    private Long endTimeMillis;
    private String status;
    private int totalSpans;
    private int totalTokens;
    private int totalInputTokens;
    private int totalOutputTokens;
    private Map<String, Object> rootInput;
    private Map<String, Object> rootOutput;
    private Map<String, Object> metadata;
    private List<String> spanIds;
}
