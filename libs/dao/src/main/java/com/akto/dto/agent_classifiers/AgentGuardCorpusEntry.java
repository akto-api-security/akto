package com.akto.dto.agent_classifiers;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
@Getter
@Setter
@NoArgsConstructor
public class AgentGuardCorpusEntry {

    @BsonId
    private String id;
    public static final String ID = "_id";

    // ---- carried over from the queue row, unchanged -----------------------

    public static final String AGENT_HOST = "agentHost";
    private String agentHost;

    // How the worker's regex cascade segmented this unit:
    // "structured" | "content_type" | "deterministic" | "heuristic".
    public static final String EXTRACTION_METHOD = "extractionMethod";
    private String extractionMethod;

    public static final String CREATED_AT = "createdAt";
    private int createdAt;

    public static final String TASK_INTENT = "taskIntent";
    private String taskIntent = "";

    public static final String RISK_CATEGORY = "riskCategory";
    private String riskCategory = "";
    public static final String BREAKDOWN = "breakdown";
    private Breakdown breakdown;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Breakdown {

        public static final String GROUND_TRUTH_SOURCE_KEY = "groundTruthSourceKey";
        private String groundTruthSourceKey;

        public static final String GROUND_TRUTH_INSTRUCTION_TEXT = "groundTruthInstructionText";
        private String groundTruthInstructionText;
    }
}
