package com.akto.action.testing;

import java.util.List;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.action.UserAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.testing.AccessMatrixTaskInfosDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.AccessMatrixTaskInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

public class AccessMatrixTaskAction extends UserAction{
    
    private List<AccessMatrixTaskInfo> accessMatrixTaskInfos;
    private List<ApiInfoKey> apiInfoKeys;
    private int apiCollectionId;
    private int frequencyInSeconds;
    private String hexId;

    public String getAllAccessMatrixTaskInfos(){
        accessMatrixTaskInfos = AccessMatrixTaskInfosDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    public String updateAccessMatixTask(){
        if (ApiCollectionsDao.instance.findOne(Filters.eq("_id", apiCollectionId)) == null
                && (apiInfoKeys == null || apiInfoKeys.isEmpty())) {
            addActionError("No endpoints found to create access matrix");
            return ERROR.toUpperCase();
        }

        if(frequencyInSeconds<=0){
            frequencyInSeconds = 0;
        }

        
        Bson q = Filters.eq("_id", new ObjectId(hexId));
        Bson update = Updates.combine(
            Updates.set("apiCollectionId",apiCollectionId),
            Updates.set("frequencyInSeconds",frequencyInSeconds),
            Updates.set("apiInfoKeys",apiInfoKeys)
        );
        UpdateOptions opts = new UpdateOptions().upsert(true);
        AccessMatrixTaskInfosDao.instance.getMCollection().updateOne(q, update, opts);

        return SUCCESS.toUpperCase();
    }

    public List<AccessMatrixTaskInfo> getAccessMatrixTaskInfos() {
        return accessMatrixTaskInfos;
    }
    public void setAccessMatrixTaskInfos(List<AccessMatrixTaskInfo> accessMatrixTaskInfos) {
        this.accessMatrixTaskInfos = accessMatrixTaskInfos;
    }
    public List<ApiInfoKey> getApiInfoKeys() {
        return apiInfoKeys;
    }
    public void setApiInfoKeys(List<ApiInfoKey> apiInfoKeys) {
        this.apiInfoKeys = apiInfoKeys;
    }
    public int getApiCollectionId() {
        return apiCollectionId;
    }
    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
    public int getFrequencyInSeconds() {
        return frequencyInSeconds;
    }
    public void setFrequencyInSeconds(int frequencyInSeconds) {
        this.frequencyInSeconds = frequencyInSeconds;
    }

    public String getHexId() {
        return hexId;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }


}