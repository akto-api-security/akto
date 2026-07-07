package com.akto.dto.agent_classifiers;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentGuardCorpusQueueEntry {

    public static final String AGENT_HOST = "agentHost";
    private String agentHost;

    // The extracted instruction unit's text.
    public static final String UNIT_TEXT = "unitText";
    private String unitText;

    public static final String CREATED_AT = "createdAt";
    private long createdAt;
}
