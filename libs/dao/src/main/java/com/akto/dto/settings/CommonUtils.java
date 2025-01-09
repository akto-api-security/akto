package com.akto.dto.settings;

import java.util.List;

public class CommonUtils {
    
    public static final String ACCOUNTS = "accountIds";
    List<Integer> accountIds;

    public static final String JOB_TYPE = "jobType";
    String jobType;

    public enum JOB_TYPES{
        DELETE_NON_VULNERABLE_TESTS
    }
    
    public static final String LAST_JOB_EPOCH = "lastJobEpoch";
    int lastJobEpoch;

    public CommonUtils () {}

    public CommonUtils (List<Integer> accountIds, String jobType, int lastJobEpoch) {
        this.accountIds = accountIds;
        this.jobType = jobType;
        this.lastJobEpoch = lastJobEpoch;
    }


    public List<Integer> getAccountIds() {
        return accountIds;
    }
    public void setAccountIds(List<Integer> accountIds) {
        this.accountIds = accountIds;
    }

    public String getJobType() {
        return jobType;
    }
    public void setJobType(String jobType) {
        this.jobType = jobType;
    }
    
    public int getLastJobEpoch() {
        return lastJobEpoch;
    }
    public void setLastJobEpoch(int lastJobEpoch) {
        this.lastJobEpoch = lastJobEpoch;
    }

}
