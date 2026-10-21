package com.akto.dto.agents;

import java.util.List;

// Akto's own per-service model chain, tried in order when no account override is configured for a
// call. Chain entries can carry an apiKey in their params: never return this from an action.
public class LlmServiceConfig {

    public static final String CHAIN = "chain";

    private String id; // service key, e.g. "skill_validation"
    private List<Model> chain;

    public LlmServiceConfig() {
    }

    public LlmServiceConfig(String id, List<Model> chain) {
        this.id = id;
        this.chain = chain;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Model> getChain() {
        return chain;
    }

    public void setChain(List<Model> chain) {
        this.chain = chain;
    }
}
