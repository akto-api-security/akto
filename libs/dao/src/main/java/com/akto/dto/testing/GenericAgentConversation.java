package com.akto.dto.testing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GenericAgentConversation {

    public enum ConversationType {
        ASK_AKTO,
        TEST_EXECUTION_RESULT,
        PROMPT_PLAYGROUND,
        ANALYZE_REQUESTS,
        DOCS_AGENT,
        ANALYZE_DASHBOARD_DATA
    }

    private String title;
    private String conversationId;
    public static final String _CONVERSATION_ID = "conversationId";
    private String prompt;
    private String response;
    private String finalSentPrompt;
    private int createdAt;
    private int lastUpdatedAt;
    public static final String _LAST_UPDATED_AT = "lastUpdatedAt";
    public static final String _TIMESTAMP = "timestamp";
    private int tokensUsed;
    private int externalApiTokens;
    private int tokensLimit;
    private ConversationType conversationType;
}
