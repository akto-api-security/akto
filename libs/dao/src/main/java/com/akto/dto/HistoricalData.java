package com.akto.dto;

import com.akto.dao.context.Context;
import org.bson.types.ObjectId;

public class HistoricalData {

    private ObjectId id;
    public static final String API_COLLECTION_ID = "apiCollectionId";
    private int apiCollectionId;
    private int totalApis;
    private float riskScore;
    private int apisTested;
    public static final String TIME = "time";
    private int time;

    public HistoricalData(int apiCollectionId, int totalApis, float riskScore, int apisTested, int time) {
        this.apiCollectionId = apiCollectionId;
        this.totalApis = totalApis;
        this.riskScore = riskScore;
        this.apisTested = apisTested;
        this.time = time;
    }

    public HistoricalData() {
    }



    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public int getTotalApis() {
        return totalApis;
    }

    public void setTotalApis(int totalApis) {
        this.totalApis = totalApis;
    }

    public float getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(float riskScore) {
        this.riskScore = riskScore;
    }

    public int getApisTested() {
        return apisTested;
    }

    public void setApisTested(int apisTested) {
        this.apisTested = apisTested;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }
}
