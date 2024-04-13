package com.akto.dto.testing;

public class LoginVerificationCodeData {

    private String key;

    private String regexString;

    public LoginVerificationCodeData() { }
    public LoginVerificationCodeData(String key, String regexString) {
        this.key = key;
        this.regexString = regexString;
    }

    public String getKey() {
        return this.key;
    }

    public String getRegexString() {
        return this.regexString;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setRegexString(String regexString) {
        this.regexString = regexString;
    }
}
