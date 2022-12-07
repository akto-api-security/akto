package com.akto.dto.testing;

public class LoginVerificationCodeData {

    private String key;

    private String regex;

    public LoginVerificationCodeData() { }
    public LoginVerificationCodeData(String key, String regex) {
        this.key = key;
        this.regex = regex;
    }

    public String getKey() {
        return this.key;
    }

    public String getRegex() {
        return this.regex;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setRegex(String value) {
        this.regex = regex;
    }
}
