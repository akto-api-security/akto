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

    public static final String SERVICE_ID = "serviceId";
    public static final String UNIQUE_USER_ID = "uniqueUserId";
    public static final String SESSION_IDENTIFIER = "sessionIdentifier";
    public static final String QUERY_PAYLOAD = "queryPayload";
    public static final String TIME_STAMP = "timeStamp";
    public static final String INPUT_TOKENS = "inputTokens";
    public static final String OUTPUT_TOKENS = "outputTokens";

    private String serviceId;
    private String uniqueUserId;
    private String sessionIdentifier;
    private String queryPayload;
    private String responsePayload;
    private long timeStamp;
    private int inputTokens;
    private int outputTokens;
} 