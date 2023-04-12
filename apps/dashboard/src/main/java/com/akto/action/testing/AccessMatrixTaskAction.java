package com.akto.action.testing;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.action.UserAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AccessMatrixTaskInfosDao;
import com.akto.dao.testing.AccessMatrixUrlToRolesDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.AccessMatrixTaskInfo;
import com.akto.dto.testing.AccessMatrixUrlToRole;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

public class AccessMatrixTaskAction extends UserAction{
    
    private List<AccessMatrixTaskInfo> accessMatrixTaskInfos;
    private List<AccessMatrixUrlToRole> accessMatrixUrlToRoles;
    private List<ApiInfoKey> apiInfoKeys;
    private int apiCollectionId;
    private int frequencyInSeconds;
    private String hexId;

    public String fetchAccessMatrixTaskInfos(){
        accessMatrixTaskInfos = AccessMatrixTaskInfosDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    public String fetchAccessMatrixUrlToRoles(){
        accessMatrixUrlToRoles = AccessMatrixUrlToRolesDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    private boolean sanityCheck(){
        if (ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId)) == null
                && (apiInfoKeys == null || apiInfoKeys.isEmpty())) {
            addActionError("No endpoints found to create access matrix");
            return false;
        }
        if (frequencyInSeconds <= 0) {
            frequencyInSeconds = 86400;
        }
        return true;
    }

    public String createAccessMatrixTask(){
        if(!sanityCheck()){
            return ERROR.toUpperCase();
        }

        AccessMatrixTaskInfo accessMatrixTaskInfo = new AccessMatrixTaskInfo(apiInfoKeys, apiCollectionId, frequencyInSeconds, 0, Context.now());
        AccessMatrixTaskInfosDao.instance.insertOne(accessMatrixTaskInfo);
        
        return SUCCESS.toUpperCase();
    }

    public String updateAccessMatrixTask(){
        if (!sanityCheck()) {
            return ERROR.toUpperCase();
        }
        try{
            ObjectId id = new ObjectId(hexId);
            Bson q = Filters.eq(Constants.ID, id);
            Bson update = Updates.combine(
                Updates.set(AccessMatrixTaskInfo.API_COLLECTION_ID,apiCollectionId),
                Updates.set(AccessMatrixTaskInfo.FREQUENCY_IN_SECONDS,frequencyInSeconds),
                Updates.set(AccessMatrixTaskInfo.API_INFO_KEYS,apiInfoKeys)
            );
            UpdateOptions opts = new UpdateOptions().upsert(true);
            AccessMatrixTaskInfosDao.instance.getMCollection().updateOne(q, update, opts);
        } catch (Exception e) {
            addActionError("invalid request");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String deleteAccessMatrixTask(){
        try {
            ObjectId id = new ObjectId(hexId);
            Bson q = Filters.eq(Constants.ID, id);
            AccessMatrixTaskInfosDao.instance.deleteAll(q);
        } catch (Exception e) {
            addActionError("unable to delete");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public List<AccessMatrixTaskInfo> getAccessMatrixTaskInfos() {
        return accessMatrixTaskInfos;
    }
    public void setAccessMatrixTaskInfos(List<AccessMatrixTaskInfo> accessMatrixTaskInfos) {
        this.accessMatrixTaskInfos = accessMatrixTaskInfos;
    }
    public List<AccessMatrixUrlToRole> getAccessMatrixUrlToRoles() {
        return accessMatrixUrlToRoles;
    }
    public void setAccessMatrixUrlToRoles(List<AccessMatrixUrlToRole> accessMatrixUrlToRoles) {
        this.accessMatrixUrlToRoles = accessMatrixUrlToRoles;
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