package com.akto.dto.agents;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Akto's own per-service model chain, tried in order. Chain params can carry an apiKey: never
// return this from an action.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LlmServiceConfig {

    public static final String CHAIN = "chain";

    private String id; // service key
    private List<Model> chain;
}
