package com.akto.tracing;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** ServiceGraphBuilder.refreshMerge: what updateServiceGraph(…, true) stores (Alibaba Cloud). */
public class ServiceGraphBuilderRefreshTest {

    private static ServiceGraphEdgeInfo edge(String from, String to, Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return new ServiceGraphEdgeInfo(from, to, m);
    }

    @Test
    public void incomingValuesReplaceStoredOnesAndOtherKeysAreKept() {
        Map<String, ServiceGraphEdgeInfo> existing = new HashMap<>();
        existing.put("gw", edge("User", "gw", "type", "gateway", "plugins", Arrays.asList("key-auth"), "endpointUrl", "old"));
        existing.put("other", edge("User", "other", "type", "agent"));

        Map<String, ServiceGraphEdgeInfo> fresh = new HashMap<>();
        fresh.put("gw", edge("User", "gw", "type", "gateway", "plugins", Arrays.asList("key-auth", "akto-guardrails")));

        Map<String, ServiceGraphEdgeInfo> merged = ServiceGraphBuilder.refreshMerge(existing, fresh);
        assertEquals(Arrays.asList("key-auth", "akto-guardrails"), merged.get("gw").getMetadata().get("plugins"), "current plug-ins shown");
        assertEquals("old", merged.get("gw").getMetadata().get("endpointUrl"), "keys the new record lacks are kept");
        assertTrue(merged.containsKey("other"), "untouched nodes stay");
    }

    @Test
    public void aMovedNodeTakesItsNewSource() {
        Map<String, ServiceGraphEdgeInfo> existing = new HashMap<>();
        existing.put("qwen-plus", edge("User", "qwen-plus", "type", "llmCall"));
        Map<String, ServiceGraphEdgeInfo> fresh = new HashMap<>();
        fresh.put("qwen-plus", edge("qwen-chat", "qwen-plus", "type", "llmCall"));
        assertEquals("qwen-chat", ServiceGraphBuilder.refreshMerge(existing, fresh).get("qwen-plus").getSourceService());
    }

    @Test
    public void nullsAreSafe() {
        assertTrue(ServiceGraphBuilder.refreshMerge(null, null).isEmpty());
        Map<String, ServiceGraphEdgeInfo> fresh = new HashMap<>();
        fresh.put("a", edge("User", "a", "type", "agent"));
        assertEquals(1, ServiceGraphBuilder.refreshMerge(null, fresh).size());
    }
}
