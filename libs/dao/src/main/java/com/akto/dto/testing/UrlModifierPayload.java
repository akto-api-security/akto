package com.akto.dto.testing;

public class UrlModifierPayload {
    
    String regex;
    Integer position;
    String replaceWith;
    String operationType;

    public UrlModifierPayload() {}

    public UrlModifierPayload(String regex, Integer position, String replaceWith, String operationType) {
        this.regex = regex;
        this.position = position;
        this.replaceWith = replaceWith;
        this.operationType = operationType;
    }

    public String getRegex() {
        return regex;
    }
    public void setRegex(String regex) {
        this.regex = regex;
    }
    public Integer getPosition() {
        return position;
    }
    public void setPosition(Integer position) {
        this.position = position;
    }
    public String getReplaceWith() {
        return replaceWith;
    }
    public void setReplaceWith(String replaceWith) {
        this.replaceWith = replaceWith;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

}
