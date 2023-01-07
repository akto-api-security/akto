package com.akto.dto;

public class RecordedLoginFlowInput {
    
    private String content;
    private String tokenFetchCommand;

    public RecordedLoginFlowInput(String content, String tokenFetchCommand, String message, int timestamp) {
        this.content = content;
        this.tokenFetchCommand = tokenFetchCommand;
    }

    public RecordedLoginFlowInput() { }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getTokenFetchCommand() {
        return tokenFetchCommand;
    }

    public void setTokenFetchCommand(String tokenFetchCommand) {
        this.tokenFetchCommand = tokenFetchCommand;
    }

}
