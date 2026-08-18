package com.akto.dto.agents;

import org.bson.codecs.pojo.annotations.BsonId;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cache of an AI agent's base risk score, keyed by the agent's identity string as _id - the
 * "bot-id" tag on ApiCollection.tagsList when present (a stable GUID from the source system,
 * e.g. Copilot Studio), otherwise the agent's display name from serviceGraphEdges as a fallback.
 * See AgentBaseRiskScoreAnalyzer.extractAgentCacheKey().
 *
 * Multiple ApiCollection docs can represent the same logical agent (ApiCollection._id is
 * hashCode(hostName), and hostName encodes more than just the agent name), so this cache lives
 * in its own collection rather than being looked up on api_collections - a plain _id point-read
 * per agent, refreshed whenever a fresh LLM score is computed for that agent.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentBaseRiskScore {

    @BsonId
    String id;
    public static final String ID = "_id";

    // One of -1, 0, 0.5, 1, 1.5, 2.
    Double baseRiskScore;
    public static final String BASE_RISK_SCORE = "baseRiskScore";

    String baseRiskScoreReason;
    public static final String BASE_RISK_SCORE_REASON = "baseRiskScoreReason";

    // Epoch seconds.
    Integer baseRiskScoreCalculatedAt;
    public static final String BASE_RISK_SCORE_CALCULATED_AT = "baseRiskScoreCalculatedAt";
}
