package com.akto.dto.messaging;

import com.akto.dto.notifications.content.Content;
import com.akto.dto.notifications.content.StringContent;

public class InboxParams {
    public enum Source {
        ActionItem, Metric, Dashboard, Discussion
    }

    public enum Purpose {
        Created, Updated, Deleted, Alert, Reminder, Followup
    }

    public static class Trigger {
        Source source;

        public void setSource(Source source) {
            this.source = source;
        }

        public void setPurpose(Purpose purpose) {
            this.purpose = purpose;
        }

        Purpose purpose;

        public Trigger () {}

        public Trigger(Source source, Purpose purpose) {
            this.source = source;
            this.purpose = purpose;
        }

        public Source getSource() {
            return source;
        }

        public Purpose getPurpose() {
            return purpose;
        }
    }

    public InboxParams() {}

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public int getSenderID() {
        return senderID;
    }

    public void setSenderID(int senderID) {
        this.senderID = senderID;
    }

    public int getReceiverID() {
        return receiverID;
    }

    public void setReceiverID(int receiverID) {
        this.receiverID = receiverID;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public Trigger getTrigger() {
        return trigger;
    }

    public void setTrigger(Trigger trigger) {
        this.trigger = trigger;
    }

    int timestamp;
    int senderID, receiverID;
    String title;
    Trigger trigger;
    Content content;

    public InboxParams(int timestamp, int senderID, int receiverID, String title, Content content, Trigger trigger) {

        this.timestamp = timestamp;
        this.senderID = senderID;
        this.receiverID = receiverID;
        this.title = title;
        this.content = content;
        this.trigger = trigger;
    }

    public InboxParams(int timestamp, int senderID, int receiverID, String title, String content, Trigger trigger) {

        this.timestamp = timestamp;
        this.senderID = senderID;
        this.receiverID = receiverID;
        this.title = title;
        this.content = new StringContent(content, 14, "#47466A");
        this.trigger = trigger;
    }
}
