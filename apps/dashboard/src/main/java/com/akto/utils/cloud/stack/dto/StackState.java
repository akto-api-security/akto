package com.akto.utils.cloud.stack.dto;

public class StackState {
    private String status;
    private long creationTime;

    public StackState(String status, long creationTime) {
        this.status = status;
        this.creationTime = creationTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(long creationTime) {
        this.creationTime = creationTime;
    }
}
