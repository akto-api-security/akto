package com.akto.dto.messaging;

import com.mongodb.BasicDBList;

public class SlackMessage extends Message {

    public static class MsgContent {
        public BasicDBList getAttachments() {
            return attachments;
        }

        public void setAttachments(BasicDBList attachments) {
            this.attachments = attachments;
        }

        public BasicDBList getBlocks() {
            return blocks;
        }

        public void setBlocks(BasicDBList blocks) {
            this.blocks = blocks;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public boolean isUnfurlLinks() {
            return unfurlLinks;
        }

        public void setUnfurlLinks(boolean unfurlLinks) {
            this.unfurlLinks = unfurlLinks;
        }

        public boolean isUnfurlMedia() {
            return unfurlMedia;
        }

        public void setUnfurlMedia(boolean unfurlMedia) {
            this.unfurlMedia = unfurlMedia;
        }

        BasicDBList attachments, blocks;
        String text;
        boolean unfurlLinks = false, unfurlMedia = false;

        public MsgContent(String text, BasicDBList attachments, BasicDBList blocks) {
            this.text = text;
            this.attachments = attachments;
            this.blocks = blocks;
        }
    }

    public boolean isAsUser() {
        return asUser;
    }

    public void setAsUser(boolean asUser) {
        this.asUser = asUser;
    }

    public String getChannelID() {
        return channelID;
    }

    public void setChannelID(String channelID) {
        this.channelID = channelID;
    }

    public int getReplyTs() {
        return replyTs;
    }

    public void setReplyTs(int replyTs) {
        this.replyTs = replyTs;
    }

    public MsgContent getMsgContent() {
        return msgContent;
    }

    public void setMsgContent(MsgContent msgContent) {
        this.msgContent = msgContent;
    }

    boolean asUser;
    String channelID;
    int replyTs;
    MsgContent msgContent;

    public SlackMessage(InboxParams params, boolean asUser, String channelID, int replyTs, MsgContent msgContent) {
        this.inboxParams = params;
        this.asUser = asUser;
        this.channelID = channelID;
        this.replyTs = replyTs;
        this.msgContent = msgContent;
        this.mode = Mode.SLACK;

    }

    @Override
    public String toJSON() {
        return null;
    }
}
