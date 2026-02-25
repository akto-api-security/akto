package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.List;

import com.akto.util.Pair;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentConversationResult extends GenericAgentConversation {
    
    private boolean validation;
    private String validationMessage;
    private String remediationMessage;
    private List<String> conversation;
    private List<Pair<String, String>> addedConversations;

    public AgentConversationResult(String conversationId, String originalPrompt, String response, List<String> conversation, int timestamp, boolean validation, String validationMessage, String finalSentPrompt, String remediationMessage){
        super("", conversationId, originalPrompt, response,finalSentPrompt, timestamp, timestamp, 0, 0, ConversationType.TEST_EXECUTION_RESULT, null);
        this.validation = validation;
        this.validationMessage = validationMessage;
        this.remediationMessage = remediationMessage;
        this.conversation = conversation;
        this.addedConversations = new ArrayList<>();
    }
}
