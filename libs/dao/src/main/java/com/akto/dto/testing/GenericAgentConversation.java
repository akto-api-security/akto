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
        DOCS_AGENT
    }

    private String title;
    private String conversationId;
    private String prompt;
    private String response;
    private String finalSentPrompt;
    private int createdAt;
    private int lastUpdatedAt;
    private int tokensUsed;
    private int externalApiTokens;
    private int tokensLimit;
    private ConversationType conversationType;
}
