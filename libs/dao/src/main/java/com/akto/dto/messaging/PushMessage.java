package com.akto.dto.messaging;

import com.mongodb.BasicDBList;

public class PushMessage extends Message {
    @Override
    public String toJSON() {
        return null;
    }

    public static class MsgContent {
        public BasicDBList getButtons() {
            return buttons;
        }

        public void setButtons(BasicDBList buttons) {
            this.buttons = buttons;
        }

        BasicDBList buttons;

        public MsgContent(BasicDBList buttons) {
            this.buttons = buttons;
        }
    }

    public MsgContent getMsgContent() {
        return msgContent;
    }

    public void setMsgContent(MsgContent msgContent) {
        this.msgContent = msgContent;
    }

    MsgContent msgContent;
    public PushMessage(InboxParams params, MsgContent msgContent) {
        this.inboxParams = params;
        this.msgContent = msgContent;
        this.mode = Mode.PUSH;
    }
}
