package com.akto.dto.agentic_sessions;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationEntry {

    public static final String REQUEST_PAYLOAD = "requestPayload";
    private String requestPayload;

    public static final String RESPONSE_PAYLOAD = "responsePayload";
    private String responsePayload;

    public static final String REQUEST_ID = "requestId";
    private String requestId;

    public static final String TIMESTAMP = "timestamp";
    private long timestamp;
}
