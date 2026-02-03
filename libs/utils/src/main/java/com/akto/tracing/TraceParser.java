package com.akto.tracing;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import java.util.Map;

public interface TraceParser {

    boolean canParse(Object input);

    TraceParseResult parse(Object input) throws Exception;

    Map<String, ServiceGraphEdgeInfo> extractServiceGraph(Object input) throws Exception;

    String getSourceType();
}
