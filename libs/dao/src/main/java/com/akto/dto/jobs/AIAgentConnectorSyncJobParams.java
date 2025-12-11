package com.akto.dto.jobs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@BsonDiscriminator
@ToString
public class AIAgentConnectorSyncJobParams extends JobParams {

    private String connectorType; // N8N, LANGCHAIN, COPILOT_STUDIO
    private Map<String, String> config; // Configuration (URLs, API keys, etc.)
    private int lastSyncedAt;

    @Override
    public JobType getJobType() {
        return JobType.AI_AGENT_CONNECTOR_SYNC;
    }

    @BsonIgnore
    @Override
    public Class<? extends JobParams> getParamsClass() {
        return AIAgentConnectorSyncJobParams.class;
    }
}
