package com.akto.tracing;

import com.akto.dto.tracing.model.Trace;
import com.akto.dto.tracing.model.Span;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TraceParseResult {
    private Trace trace;
    private List<Span> spans;
    private Map<String, Object> metadata;
    private String workflowId;
    private String sourceIdentifier;
}
