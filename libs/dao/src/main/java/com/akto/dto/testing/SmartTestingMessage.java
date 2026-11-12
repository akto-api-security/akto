package com.akto.dto.testing;

import org.bson.types.ObjectId;

/**
 * What the person watching a smart-testing run sends to it: a message that steers the agent (more context, another
 * direction), or a request to stop. The smart-testing service reads new ones between the agent's turns.
 */
public class SmartTestingMessage {

    public enum Kind { MESSAGE, STOP }

    public static final String ID = "_id";
    private ObjectId id;

    /** The runKey of a message sent to a whole test run: every smart-testing run of that summary reads it. */
    public static String summaryRunKey(String testingRunResultSummaryHexId) { return "summary:" + testingRunResultSummaryHexId; }

    public static final String RUN_KEY = "runKey";
    private String runKey;
    public static final String KIND = "kind";
    private Kind kind;
    public static final String TEXT = "text";
    private String text;
    public static final String TIMESTAMP = "timestamp";
    private int timestamp;
    private String createdBy;

    public SmartTestingMessage() { }

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public String getRunKey() { return runKey; }
    public void setRunKey(String runKey) { this.runKey = runKey; }

    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public int getTimestamp() { return timestamp; }
    public void setTimestamp(int timestamp) { this.timestamp = timestamp; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
