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
    public static final String HEADER_INSTALLER    = "x-akto-installer";
    public static final String HEADER_USER_EMAIL   = "user_email";
    public static final String HEADER_SESSION_ID   = "session_id";
    public static final String HEADER_CONVERSATION_ID = "conversation_id";
    public static final String HEADER_GENERATION_ID   = "generation_id";
    public static final String HEADER_MODEL            = "model";
    public static final String HEADER_TRANSCRIPT_PATH  = "transcript_path";
    public static final String HEADER_CWD              = "cwd";
    public static final String HEADER_PERMISSION_MODE  = "permission_mode";
    public static final String HEADER_HOOK_EVENT_NAME  = "hook_event_name";

    // from x-akto-installer
    private String serviceId;

    // from user_email
    private String uniqueUserId;

    // from session_id
    private String sessionIdentifier;

    // from conversation_id
    private String conversationId;

    // from generation_id
    private String generationId;

    // from model
    private String model;

    // from transcript_path
    private String transcriptPath;

    // from cwd
    private String cwd;

    // from permission_mode
    private String permissionMode;

    // from hook_event_name
    private String hookEventName;

    private String queryPayload;
    private String responsePayload;
    private long timeStamp;
    private int inputTokens;
    private int outputTokens;
} 