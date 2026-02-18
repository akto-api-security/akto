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
        ASK_AKTO,          // deprecated, kept for backward compatibility with existing DB documents
        DOCS_AGENT,        // deprecated, kept for backward compatibility with existing DB documents
        TEST_EXECUTION_RESULT,
        PROMPT_PLAYGROUND,
        ANALYZE_REQUESTS,
        ANALYZE_DASHBOARD_DATA;

        public String getExternalName() {
            switch (this) {
                case ASK_AKTO: return "API_SEC_CHAT";
                case DOCS_AGENT: return "AI_SEC_CHAT";
                default: return this.name();
            }
        }
    }

    private String title;
    private String conversationId;
    private String prompt;
    private String response;
    private String finalSentPrompt;
    private int createdAt;
    private int lastUpdatedAt;
    private int tokensUsed;
    private int tokensLimit;
    private ConversationType conversationType;
}
