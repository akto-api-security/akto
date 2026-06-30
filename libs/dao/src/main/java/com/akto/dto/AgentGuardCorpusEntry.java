package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AgentGuardCorpusEntry {

    @BsonId
    private String id;
    public static final String ID = "_id";

    public static final String AGENT_HOST = "agentHost";
    private String agentHost;

    // Intent category: "benign", "prompt_injection", "toxicity", "banned_finance", etc.
    public static final String TASK_INTENT = "taskIntent";
    private String taskIntent;

    // Verdict bucket: "allow" | "blocked"
    public static final String SCOPE_BUCKET = "scopeBucket";
    private String scopeBucket;

    // true for "allow", false for "blocked" — derived from scopeBucket, stored for fast read.
    public static final String IS_VALID = "isValid";
    private boolean isValid;

    // Rolling window of MiniLM embeddings (384-dim each) for this bucket, max 50.
    // Maintained by $push + $slice(-50) on upsert — no application-level trimming needed.
    public static final String VECTORS = "vectors";
    private List<List<Double>> vectors;

    public static final String UPDATED_AT = "updatedAt";
    private int updatedAt;
}
