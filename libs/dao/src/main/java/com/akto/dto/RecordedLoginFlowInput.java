package com.akto.dto;

public class RecordedLoginFlowInput {
    
    private String content;
    private String tokenFetchCommand;
    private String outputFilePath;
    private String errorFilePath;

    public RecordedLoginFlowInput(String content, String tokenFetchCommand, String outputFilePath, String errorFilePath) {
        this.content = content;
        this.tokenFetchCommand = tokenFetchCommand;
        this.outputFilePath = outputFilePath;
        this.errorFilePath = errorFilePath;
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

    public String getOutputFilePath() {
        return outputFilePath;
    }

    public void setOutputFilePath(String outputFilePath) {
        this.outputFilePath = outputFilePath;
    }

    public String getErrorFilePath() {
        return errorFilePath;
    }

    public void setErrorFilePath(String errorFilePath) {
        this.errorFilePath = errorFilePath;
    }

}
