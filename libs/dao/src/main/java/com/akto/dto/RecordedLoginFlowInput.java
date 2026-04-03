package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
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
}
