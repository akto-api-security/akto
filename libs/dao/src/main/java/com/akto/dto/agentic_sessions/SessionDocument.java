package com.akto.dto.agentic_sessions;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SessionDocument {

    private String id; // sessionIdentifier

    public static final String SESSION_IDENTIFIER = "sessionIdentifier";
    private String sessionIdentifier;

    public static final String SESSION_SUMMARY = "sessionSummary";
    private String sessionSummary;

    public static final String CONVERSATION_INFO = "conversationInfo";
    private List<ConversationEntry> conversationInfo;

    public static final String IS_MALICIOUS = "isMalicious";
    private boolean isMalicious;

    public static final String BLOCKED_REASON = "blockedReason";
    @Getter @Setter
    private String blockedReason;

    public static final String UPDATED_AT = "updatedAt";
    private long updatedAt;

    public static final String CREATED_AT = "createdAt";
    private long createdAt;
}
