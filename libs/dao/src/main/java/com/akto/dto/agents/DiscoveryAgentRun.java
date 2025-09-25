package com.akto.dto.agents;

import java.util.List;
import java.util.Map;

import com.akto.dto.HttpResponseParams.Source;
import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class DiscoveryAgentRun extends AgentRun {

    Source discoverySource;
    List<BasicDBObject> agentStats;
    int accountId;
    int apiCollectionId;
    Map<String, Object> results;
    public static final String _RESULTS = "results";
    public DiscoveryAgentRun(String processId, Map<String, Object> agentInitDocument, Agent agent, int createdTimestamp, int startTimestamp, int endTimestamp, State state, Model model, Map<String, String> privateData, Source discoverySource, List<BasicDBObject> agentStats, int accountId, int apiCollectionId) {
        super(processId, agentInitDocument, agent, createdTimestamp, startTimestamp, endTimestamp, state, model, privateData);
        this.discoverySource = discoverySource;
        this.agentStats = agentStats;
        this.accountId = accountId;
        this.apiCollectionId = apiCollectionId;
    }
}
