package com.akto.util;

public class ConnectionInfo {
        
        public static final String GITHUB_SSO = "githubSso";
        public static final String CI_CD_INTEGRATIONS = "cicdIntegrations";
        public static final String AUTOMATED_TRAFFIC = "automatedTraffic";
        public static final String SLACK_ALERTS = "slackAlerts";
        public static final String INVITE_MEMBERS = "inviteMembers";

        public static final String IS_INTEGRATED = "isIntegrated";
        private boolean isIntegrated;

        public static final String LAST_SKIPPED = "lastSkipped";
        private int lastSkipped;

        public ConnectionInfo(){}

        public ConnectionInfo(int lastSkipped, boolean isIntegrated){
            this.lastSkipped = lastSkipped;
            this.isIntegrated = isIntegrated;
        }

        public int getLastSkipped() {
            return lastSkipped;
        }

        public void setLastSkipped(int lastSkipped) {
            this.lastSkipped = lastSkipped;
        }

        public boolean isIntegrated() {
            return isIntegrated;
        }

        public void setIntegrated(boolean isIntegrated) {
            this.isIntegrated = isIntegrated;
        }
    }

