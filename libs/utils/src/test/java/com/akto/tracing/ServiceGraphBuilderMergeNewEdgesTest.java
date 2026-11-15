package com.akto.tracing;

import com.akto.dto.ApiCollection.ServiceGraphEdgeInfo;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ServiceGraphBuilder.mergeNewEdges: what updateServiceGraph merges, and when it can skip the write. */
public class ServiceGraphBuilderMergeNewEdgesTest {

    private static ServiceGraphEdgeInfo edge(String to, String marker) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("marker", marker);
        return new ServiceGraphEdgeInfo("User", to, metadata);
    }

    @Test
    public void addsOnlyMissingEdgesAndNeverReplaces() {
        Map<String, ServiceGraphEdgeInfo> existingEdges = new HashMap<>();
        ServiceGraphEdgeInfo stored = edge("a", "stored");
        existingEdges.put("a", stored);

        Map<String, ServiceGraphEdgeInfo> incomingEdges = new HashMap<>();
        incomingEdges.put("a", edge("a", "incoming"));
        assertFalse(ServiceGraphBuilder.mergeNewEdges(existingEdges, incomingEdges), "nothing new, so nothing to write");
        assertSame(stored, existingEdges.get("a"));

        ServiceGraphEdgeInfo added = edge("b", "incoming");
        incomingEdges.put("b", added);
        assertTrue(ServiceGraphBuilder.mergeNewEdges(existingEdges, incomingEdges));
        assertSame(stored, existingEdges.get("a"), "the first stored edge still wins");
        assertSame(added, existingEdges.get("b"));
        assertEquals(2, existingEdges.size());
    }

    @Test
    public void anEdgeStoredAsNullCountsAsMissing() {
        Map<String, ServiceGraphEdgeInfo> existingEdges = new HashMap<>();
        existingEdges.put("a", null);
        Map<String, ServiceGraphEdgeInfo> incomingEdges = new HashMap<>();
        ServiceGraphEdgeInfo fresh = edge("a", "incoming");
        incomingEdges.put("a", fresh);
        assertTrue(ServiceGraphBuilder.mergeNewEdges(existingEdges, incomingEdges));
        assertSame(fresh, existingEdges.get("a"));
    }

    @Test
    public void anEmptyIncomingMapChangesNothing() {
        Map<String, ServiceGraphEdgeInfo> existingEdges = new HashMap<>();
        existingEdges.put("a", edge("a", "stored"));
        assertFalse(ServiceGraphBuilder.mergeNewEdges(existingEdges, new HashMap<>()));
        assertEquals(1, existingEdges.size());
    }
}
