package com.akto.service.insights.agentic;

import com.akto.dto.ApiCollection;
import com.akto.service.insights.HostCollectionResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The AGENTIC-collection inventory + host join, built once per bundle load from the already-loaded
 * `collections` list (no extra Mongo read) and shared by every Argus posture provider. Every
 * agentic read (guardrail hostSeverityCounts, audit rows keyed by hostCollectionId, ...) is
 * ultimately attributed back to an AgentRef through this class.
 */
public class AgentIndex {

    private final Map<Integer, AgentRef> byCollectionId = new HashMap<>();
    private final HostCollectionResolver resolver;

    public AgentIndex(List<ApiCollection> collections) {
        this.resolver = new HostCollectionResolver(collections);
        if (collections == null) return;
        for (ApiCollection c : collections) {
            if (c == null) continue;
            byCollectionId.put(c.getId(), new AgentRef(c));
        }
    }

    public AgentRef agentFor(int collectionId) {
        return byCollectionId.get(collectionId);
    }

    /** First agent whose collection a raw host string resolves to (exact/loose/claude-config —
     *  see HostCollectionResolver), or null when the host matches no known agentic collection. */
    public AgentRef agentForHost(String host) {
        List<Integer> ids = resolver.resolve(host);
        for (Integer id : ids) {
            AgentRef ref = byCollectionId.get(id);
            if (ref != null) return ref;
        }
        return null;
    }

    public List<Integer> collectionIdsForHost(String host) {
        return resolver.resolve(host);
    }

    public Collection<AgentRef> all() {
        return byCollectionId.values();
    }

    public List<Integer> allCollectionIds() {
        return byCollectionId.isEmpty() ? Collections.emptyList() : new ArrayList<>(byCollectionId.keySet());
    }

    public boolean isEmpty() {
        return byCollectionId.isEmpty();
    }
}
