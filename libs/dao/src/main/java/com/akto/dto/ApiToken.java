package com.akto.dto;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ApiToken {
    private int id;
    private int accountId;
    public static final String ACCOUNT_ID = "accountId";
    private String name;
    private String key;
    public static final String KEY ="key";
    private int timestamp;
    private String username;
    public static final String USER_NAME = "username";
    private Utility utility;
    public static final String UTILITY = "utility";

    public static final String FULL_STRING_ALLOWED_API = "*";

    public enum Utility{
        BURP(Arrays.asList("/api/uploadHar", "/api/importInBurp", "/api/sendHealthCheck")),
        EXTERNAL_API(Collections.singletonList(FULL_STRING_ALLOWED_API)),
        SLACK(Collections.emptyList()),
        CICD(Arrays.asList("/api/startTest", "/api/retrieveAllCollectionTests", "/api/fetchTestingRunResultSummaries", 
        "/api/fetchTestingRunResults","/api/fetchWorkflowTestingRun","/api/fetchWorkflowTestingSchedule",
        "/api/stopAllTests","/api/fetchTestRunResultDetails","/api/fetchIssueFromTestRunResultDetails",
        "/api/fetchTestingRunResultSummary"));
        private final List<String> accessList;

        Utility(List<String> accessList) {
            this.accessList = accessList;
        }

        public List<String> getAccessList() {
            return accessList;
        }
    }

    public ApiToken() {}


    public ApiToken(int id,int accountId,String name,String key, int timestamp, String username, Utility utility) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.key = key;
        this.timestamp = timestamp;
        this.username = username;
        this.utility = utility;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Utility getUtility() {
        return utility;
    }

    public void setUtility(Utility utility) {
        this.utility = utility;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }
}
