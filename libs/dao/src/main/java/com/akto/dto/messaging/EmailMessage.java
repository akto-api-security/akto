package com.akto.dto.messaging;

public class EmailMessage extends Message {

    public static class MsgContent {
        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        String subject, body;
        public MsgContent(String subject, String body) {
            this.subject = subject;
            this.body = body;
        }
    }

    public MsgContent getMsgContent() {
        return msgContent;
    }

    public void setMsgContent(MsgContent msgContent) {
        this.msgContent = msgContent;
    }

    MsgContent msgContent;

    public EmailMessage(InboxParams params, MsgContent msgContent) {
        this.inboxParams = params;
        this.msgContent = msgContent;
        this.mode = Mode.EMAIL;
    }

    @Override
    public String toJSON() {
        return null;
    }
}
