package com.akto.dto.redteam_learnings;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What has been learned about one target agent up to a point in one testing run summary. A new
 * version is written each time a batch of results is consolidated and once more when the run
 * ends. _id is {apiCollectionId}:{method}:{urlHash}:{summaryId}:{batchNo}, so repeating a
 * consolidation rewrites the same version. A version is only ever written with content: a
 * failed or empty consolidation leaves the previous version as the latest.
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentRedteamProfile extends AgentRedteamRecord {

    public static final String BATCH_NO = "batchNo";
    public static final String FINAL_VERSION = "finalVersion";
    public static final String TOTALS = "totals";
    public static final String CELLS = "cells";
    public static final String INSIGHTS = "insights";

    public static final int MAX_INSIGHTS = 20;
    public static final int MAX_CELLS = 700;

    private int batchNo;
    /** True for the version written when the run ended. */
    private boolean finalVersion;

    private VerdictCounts totals = new VerdictCounts();
    /** Conversations with no verdict (target error, judge failure): kept out of the totals, never mixed into them. */
    private int unjudgedConversations;

    /** Counts per base prompt x strategy, computed by the database, never by the LLM. */
    private List<GridCell> cells = new ArrayList<>();
    private List<Insight> insights = new ArrayList<>();

    public static String buildVersionId(int apiCollectionId, String method, String url, String testingRunResultSummaryId, int batchNo) {
        return identityIdPrefix(apiCollectionId, method, url) + ":" + testingRunResultSummaryId + ":" + batchNo;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class GridCell {
        private TemplateGridKey gridKey = new TemplateGridKey();
        /** Only set when the template had neither wordlist, so the cell is the test itself. */
        private String testSubType;
        private VerdictCounts counts = new VerdictCounts();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Insight {
        public static final String KIND_REFUSAL_STYLE = "REFUSAL_STYLE";
        public static final String KIND_BYPASS = "BYPASS";
        public static final String KIND_BLOCKED = "BLOCKED";
        public static final String KIND_TOOLS = "TOOLS";
        /** What to try in the next run. */
        public static final String KIND_ADVICE = "ADVICE";

        public static final int MAX_TEXT_CHARS = 500;

        private String id;
        private String kind;
        private String text;
        /** Raised when later results support the insight, lowered when they contradict it; dropped at 0. */
        private int importance;
        /** Observations this insight rests on. An insight without any is not stored. */
        private List<String> evidenceObservationIds = new ArrayList<>();
    }
}
