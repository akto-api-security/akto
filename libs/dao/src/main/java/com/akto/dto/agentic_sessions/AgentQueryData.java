package com.akto.dto.agentic_sessions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter

// use this in future for learning the user type, build the flow graph of the agent
public class AgentQueryData {

    // DB field name constants
    public static final String SERVICE_ID = "serviceId";
    public static final String UNIQUE_USER_ID = "uniqueUserId";
    public static final String SESSION_IDENTIFIER = "sessionIdentifier";
    public static final String QUERY_PAYLOAD = "queryPayload";
    public static final String TIME_STAMP = "timeStamp";
    public static final String INPUT_TOKENS = "inputTokens";
    public static final String OUTPUT_TOKENS = "outputTokens";

    // Request header name constants
    public static final String HEADER_PREFIX    = "x-akto-installer-";
    public static final String HEADER_USER_EMAIL   = "user_email";
    public static final String HEADER_SESSION_ID   = "session_id";
    public static final String HEADER_CONVERSATION_ID = "conversation_id";
    public static final String HEADER_GENERATION_ID   = "generation_id";
    
    private String serviceId;
    private String deviceId;
    private String userName;
    private String sessionIdentifier;
    private String conversationId;
    private String queryPayload;
    private String responsePayload;
    private long timeStamp;
    private int inputTokens;
    private int outputTokens;
} 