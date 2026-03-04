package com.akto.dto.agentic_sessions;

import org.bson.types.ObjectId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;

public class SessionDocument {

    // Field name constants for MongoDB queries
    public static final String ID = "_id";
    public static final String SESSION_IDENTIFIER = "sessionIdentifier";
    public static final String CONVERSATION_INFO = "conversationInfo";
    public static final String CREATED_AT = "createdAt";
    public static final String IS_MALICIOUS = "isMalicious";
    public static final String SESSION_SUMMARY = "sessionSummary";
    public static final String BLOCKED_REASON = "blockedReason";
    public static final String UPDATED_AT = "updatedAt";

    @JsonIgnore
    private ObjectId id;
    private String sessionIdentifier;
    private List<ConversationInfo> conversationInfo;
    private long createdAt;
    private boolean isMalicious;
    private String sessionSummary;
    private String blockedReason;
    private long updatedAt;

    public static class ConversationInfo {
        private String requestId;
        private String requestPayload;
        private String responsePayload;
        private long timestamp;

        public ConversationInfo() {}

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public String getRequestPayload() {
            return requestPayload;
        }

        public void setRequestPayload(String requestPayload) {
            this.requestPayload = requestPayload;
        }

        public String getResponsePayload() {
            return responsePayload;
        }

        public void setResponsePayload(String responsePayload) {
            this.responsePayload = responsePayload;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }
    }

    public SessionDocument() {}

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getSessionIdentifier() {
        return sessionIdentifier;
    }

    public void setSessionIdentifier(String sessionIdentifier) {
        this.sessionIdentifier = sessionIdentifier;
    }

    public List<ConversationInfo> getConversationInfo() {
        return conversationInfo;
    }

    public void setConversationInfo(List<ConversationInfo> conversationInfo) {
        this.conversationInfo = conversationInfo;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isMalicious() {
        return isMalicious;
    }

    public void setMalicious(boolean malicious) {
        isMalicious = malicious;
    }

    public String getSessionSummary() {
        return sessionSummary;
    }

    public void setSessionSummary(String sessionSummary) {
        this.sessionSummary = sessionSummary;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public void setBlockedReason(String blockedReason) {
        this.blockedReason = blockedReason;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
