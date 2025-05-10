package com.akto.threat.backend.db;

public class SplunkIntegrationModel {
    private String splunkUrl;
    private String splunkToken;
    private int accountId;

    public SplunkIntegrationModel(String splunkUrl, String splunkToken, int accountId) {
        this.splunkUrl = splunkUrl;
        this.splunkToken = splunkToken;
        this.accountId = accountId;
    }

    public SplunkIntegrationModel(Builder builder) {
        this.splunkUrl = builder.splunkUrl;
        this.splunkToken = builder.splunkToken;
        this.accountId = builder.accountId;
    }

    public static class Builder {
        private String splunkUrl;
        private String splunkToken;
        private int accountId;

        public Builder setSplunkUrl(String splunkUrl) {
            this.splunkUrl = splunkUrl;
            return this;
        }

        public Builder setSplunkToken(String splunkToken) {
            this.splunkToken = splunkToken;
            return this;
        }

        public Builder setAccountId(int accountId) {
            this.accountId = accountId;
            return this;
        }
        
        public SplunkIntegrationModel build() {
            return new SplunkIntegrationModel(this);
        }
    }

    public String getSplunkUrl() {
        return splunkUrl;
    }

    public String getSplunkToken() {
        return splunkToken;
    }

    public int getAccountId() {
        return accountId;
    }    
    
    public static Builder newBuilder() {
        return new Builder();
    }

}
