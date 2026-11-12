package com.akto.dto.testing;

import org.bson.types.ObjectId;

/**
 * One step of a smart-testing run, in the same shape as TestingRunResult.aiSummaryTraces ({phase, attempt, content})
 * so the dashboard renders live steps and finished traces with the same component (AiExecutionJourney).
 */
public class SmartTestingEvent {

    public static final String ID = "_id";
    private ObjectId id;

    public static final String RUN_KEY = "runKey";
    private String runKey;
    public static final String SEQ = "seq";
    private int seq; // order within the run, from 1

    public static final String PHASE = "phase";
    private String phase; // plan, action, observation, validation, status, user
    public static final String ATTEMPT = "attempt";
    private int attempt;
    public static final String CONTENT = "content";
    private String content;
    public static final String TIMESTAMP = "timestamp";
    private int timestamp;

    public SmartTestingEvent() { }

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getRunKey() { return runKey; }
    public void setRunKey(String runKey) { this.runKey = runKey; }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }

    public int getAttempt() { return attempt; }
    public void setAttempt(int attempt) { this.attempt = attempt; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public int getTimestamp() { return timestamp; }
    public void setTimestamp(int timestamp) { this.timestamp = timestamp; }
}
