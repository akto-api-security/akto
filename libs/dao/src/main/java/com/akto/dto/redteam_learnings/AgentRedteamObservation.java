package com.akto.dto.redteam_learnings;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What one finished test conversation showed about the target agent. Built only from what the
 * judge already returned; the target's full response is never stored. _id is the conversationId.
 * expireAt is a BSON Date because only a Date is honoured by a TTL index.
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentRedteamObservation extends AgentRedteamRecord {

    public static final String OUTCOME = "outcome";
    public static final String VERDICT = "verdict";
    public static final String AGENT_BEHAVIOR_CLASS = "agentBehaviorClass";
    public static final String TEST_SUB_TYPE = "testSubType";
    public static final String GRID_KEY = "gridKey";
    public static final String EVIDENCE_QUOTE = "evidenceQuote";
    public static final String JUDGE_REASONING = "judgeReasoning";
    public static final String TOOLS_SEEN = "toolsSeen";
    public static final String EVENT_TIME_MS = "eventTimeMs";
    public static final String EXPIRE_AT = "expireAt";

    /** Observations are only needed until their run is consolidated; what lasts is the profile. */
    public static final int RETENTION_DAYS = 30;

    /** The conversation was judged, so its verdict counts. */
    public static final String OUTCOME_JUDGED = "JUDGED";
    /** The target returned an error and validation was skipped: not evidence about the agent either way. */
    public static final String OUTCOME_TARGET_ERROR = "TARGET_ERROR";
    /** The judge failed: the conversation has no verdict. */
    public static final String OUTCOME_JUDGE_ERROR = "JUDGE_ERROR";

    public static final String VERDICT_NORMAL = "0";
    public static final String VERDICT_ATTEMPTED = "1";
    public static final String VERDICT_CRITICAL = "2";

    public static final int MAX_EVIDENCE_QUOTE_CHARS = 500;
    public static final int MAX_JUDGE_REASONING_CHARS = 800;
    public static final int MAX_TOOLS_SEEN = 20;

    private String outcome;
    /** "0", "1" or "2" as the judge returned it; null unless outcome is JUDGED. */
    private String verdict;
    private String agentBehaviorClass;
    /** The judge's own confidence, 0-100; null when it did not compute one. */
    private Integer judgeConfidence;

    private String testSubType;
    /** Null when the template has no category to report. */
    private String testCategory;
    private TemplateGridKey gridKey = new TemplateGridKey();

    /** Verbatim text from the agent's output that the judge cited; only present for a critical verdict. */
    private String evidenceQuote;
    /** The judge's own summary of which dimensions it answered YES to. Free text: it is not parsed into flags. */
    private String judgeReasoning;
    private List<String> toolsSeen = new ArrayList<>();
    private int turns;
    private int responseChars;

    /** When the conversation was judged, in milliseconds; createdAtMs is when it was written. */
    private long eventTimeMs;
    private Date expireAt;
}
