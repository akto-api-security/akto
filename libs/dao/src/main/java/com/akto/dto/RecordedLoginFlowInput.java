package com.akto.dto;

public class RecordedLoginFlowInput {

    public static final String TOKEN_RESULT = "tokenResult";

    private String content;
    private String tokenFetchCommand;
    private String outputFilePath;
    private String errorFilePath;

    private String tokenResult;

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

    public String getTokenResult() {
        return tokenResult;
    }

    public void setTokenResult(String tokenResult) {
        this.tokenResult = tokenResult;
    }

}
