package com.akto.dto.testing;

import org.bson.codecs.pojo.annotations.BsonId;

public class TestingSchedule {
    
    String authorEmail;
    int creationTimestamp;

    String lastEditorEmail;
    int lastUpdateTimestamp;

    @BsonId
    int id;

    public static final String START_TIMESTAMP = "startTimestamp";
    int startTimestamp;
    boolean recurring;
    TestingRun sampleTestingRun;

    public TestingSchedule() {
    }

    public TestingSchedule(String authorEmail, int creationTimestamp, String lastEditorEmail, int lastUpdateTimestamp, int id, int startTimestamp, boolean recurring, TestingRun sampleTestingRun) {
        this.authorEmail = authorEmail;
        this.creationTimestamp = creationTimestamp;
        this.lastEditorEmail = lastEditorEmail;
        this.lastUpdateTimestamp = lastUpdateTimestamp;
        this.id = id;
        this.startTimestamp = startTimestamp;
        this.recurring = recurring;
        this.sampleTestingRun = sampleTestingRun;
}

    public String getAuthorEmail() {
        return this.authorEmail;
    }

    public void setAuthorEmail(String authorEmail) {
        this.authorEmail = authorEmail;
    }

    public int getCreationTimestamp() {
        return this.creationTimestamp;
    }

    public void setCreationTimestamp(int creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }

    public String getLastEditorEmail() {
        return this.lastEditorEmail;
    }

    public void setLastEditorEmail(String lastEditorEmail) {
        this.lastEditorEmail = lastEditorEmail;
    }

    public int getLastUpdateTimestamp() {
        return this.lastUpdateTimestamp;
    }

    public void setLastUpdateTimestamp(int lastUpdateTimestamp) {
        this.lastUpdateTimestamp = lastUpdateTimestamp;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getStartTimestamp() {
        return this.startTimestamp;
    }

    public void setStartTimestamp(int startTimestamp) {
        this.startTimestamp = startTimestamp;
    }

    public boolean isRecurring() {
        return this.recurring;
    }

    public boolean getRecurring() {
        return this.recurring;
    }

    public void setRecurring(boolean recurring) {
        this.recurring = recurring;
    }

    public TestingRun getSampleTestingRun() {
        return this.sampleTestingRun;
    }

    public void setSampleTestingRun(TestingRun sampleTestingRun) {
        this.sampleTestingRun = sampleTestingRun;
    }

    @Override
    public String toString() {
        return "{" +
            " authorEmail='" + getAuthorEmail() + "'" +
            ", creationTimestamp='" + getCreationTimestamp() + "'" +
            ", lastEditorEmail='" + getLastEditorEmail() + "'" +
            ", lastUpdateTimestamp='" + getLastUpdateTimestamp() + "'" +
            ", id='" + getId() + "'" +
            ", startTimestamp='" + getStartTimestamp() + "'" +
            ", recurring='" + isRecurring() + "'" +
            ", sampleTestingRun='" + getSampleTestingRun() + "'" +
            "}";
    }

}