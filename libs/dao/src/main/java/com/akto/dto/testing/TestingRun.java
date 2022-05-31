package com.akto.dto.testing;

import com.akto.dto.ApiInfo;
import org.bson.types.ObjectId;

import java.util.List;

public class TestingRun {
    private ObjectId id;
    public static final String SCHEDULE_TIMESTAMP = "scheduleTimestamp";
    private int scheduleTimestamp;
    public static final String PICKED_UP_TIMESTAMP = "pickedUpTimestamp";
    private int pickedUpTimestamp;
    public static final String END_TIMESTAMP = "endTimestamp";
    private int endTimestamp;
    private String userEmail;
    // TODO: discuss a better way to represent apisList and testConfig so that if new APIs come then they get tested too
    private List<ApiInfo.ApiInfoKey> apisList;
    private int testIdConfig;

    public TestingRun() { }

    public TestingRun(int scheduleTimestamp, String userEmail, List<ApiInfo.ApiInfoKey> apisList, int testIdConfig) {
        this.scheduleTimestamp = scheduleTimestamp;
        this.endTimestamp = -1;
        this.pickedUpTimestamp = -1;
        this.userEmail = userEmail;
        this.apisList = apisList;
        this.testIdConfig = testIdConfig;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public int getScheduleTimestamp() {
        return scheduleTimestamp;
    }

    public void setScheduleTimestamp(int scheduleTimestamp) {
        this.scheduleTimestamp = scheduleTimestamp;
    }

    public int getEndTimestamp() {
        return endTimestamp;
    }

    public void setEndTimestamp(int endTimestamp) {
        this.endTimestamp = endTimestamp;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public List<ApiInfo.ApiInfoKey> getApisList() {
        return apisList;
    }

    public void setApisList(List<ApiInfo.ApiInfoKey> apisList) {
        this.apisList = apisList;
    }

    public int getTestIdConfig() {
        return testIdConfig;
    }

    public void setTestIdConfig(int testIdConfig) {
        this.testIdConfig = testIdConfig;
    }

    public int getPickedUpTimestamp() {
        return pickedUpTimestamp;
    }

    public void setPickedUpTimestamp(int pickedUpTimestamp) {
        this.pickedUpTimestamp = pickedUpTimestamp;
    }
}
