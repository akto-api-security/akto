package com.akto.dto.agentic_sessions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ADX-backend substitute for Elasticsearch's topic/subTopic field write-back: Azure Data
 * Explorer is append-only and has no in-place per-row update, so classified topic/subTopic is
 * stored here (keyed by docId, scoped to the account's own database) and joined against ADX
 * query results at read time instead.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AgentQueryTopicMapping {

    public static final String TOPIC     = "topic";
    public static final String SUB_TOPIC = "subTopic";
    public static final String TIMESTAMP = "timestamp";

    // Field named "id" maps to MongoDB _id by convention; value is the ADX row's docId.
    private String id;
    private String topic    = "";
    private String subTopic = "";
    private long timestamp;
}
