package com.akto.hybrid_parsers;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** IBM watsonx Orchestrate connector traffic: one collection per agent, named by its display name. */
class HttpCallParserIbmWatsonxTest {

    private static HttpResponseParams orchestrateTraffic(String tagsJson) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("host", Collections.singletonList("watson-orchestrate.ibm.com"));
        HttpRequestParams request = new HttpRequestParams("POST", "/v1/orchestrate/agents/agent-1/runs", "HTTP/1.1",
                headers, "{\"messages\":[]}", 0);
        return new HttpResponseParams("HTTP/1.1", 200, "OK", new HashMap<>(), "{}", request, 1760000000, "1000", false,
                HttpResponseParams.Source.MIRRORING, "", "member-1", "", "1", tagsJson, new ArrayList<>());
    }

    @Test
    void collectionIsNamedAfterTheAgent() {
        assertEquals("AKTO Supervisor Agent", HttpCallParser.getHostnameForCollection(
                orchestrateTraffic("{\"source\":\"IBM_WATSONX\",\"bot-name\":\"AKTO Supervisor Agent\"}")));
    }

    @Test
    void withoutAnAgentNameTheHostIsUsed() {
        assertEquals("watson-orchestrate.ibm.com", HttpCallParser.getHostnameForCollection(
                orchestrateTraffic("{\"source\":\"IBM_WATSONX\"}")));
    }
}
