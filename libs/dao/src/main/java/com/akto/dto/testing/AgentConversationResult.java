package com.akto.dto.testing;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgentConversationResult {
    
    private String conversationId;
    private String prompt;
    private String response;
    private List<String> conversation;
    private int timestamp;
    private boolean validation;
    private String validationMessage;
    private String finalSentPrompt;
    private String remediationMessage;
    private Map<String, Object> toolsMetadata;
}
