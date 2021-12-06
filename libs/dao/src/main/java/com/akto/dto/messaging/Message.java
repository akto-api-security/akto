package com.akto.dto.messaging;

public abstract class Message {

    public enum Mode {
        SLACK, EMAIL, PUSH;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public InboxParams getInboxParams() {
        return inboxParams;
    }

    public void setInboxParams(InboxParams inboxParams) {
        this.inboxParams = inboxParams;
    }

    Mode mode;
    InboxParams inboxParams;
    public Mode getMode() {
        return mode;
    }
    abstract public String toJSON();
}
