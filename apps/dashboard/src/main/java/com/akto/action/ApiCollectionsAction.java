package com.akto.action;

import java.util.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import com.akto.dao.APISpecDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;

public class ApiCollectionsAction extends UserAction {

    List<ApiCollection> apiCollections = new ArrayList<>();
    int apiCollectionId;

    public String fetchAllCollections() {
        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());

        Map<Integer, Integer> countMap = ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMap();

        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            Integer count = countMap.get(apiCollectionId);
            if (count != null && apiCollection.getHostName() != null) {
                apiCollection.setUrlsCount(count);
            } else {
                apiCollection.setUrlsCount(apiCollection.getUrls().size());
            }
        }

        return Action.SUCCESS.toUpperCase();
    }

    static int maxCollectionNameLength = 25;
    private String collectionName;
    public String createCollection() {
        if (this.collectionName == null) {
            addActionError("Invalid collection name");
            return ERROR.toUpperCase();
        }

        if (this.collectionName.length() > maxCollectionNameLength) {
            addActionError("Custom collections max length: " + maxCollectionNameLength);
            return ERROR.toUpperCase();
        }

        for (char c: this.collectionName.toCharArray()) {
            boolean alphabets = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            boolean numbers = c >= '0' && c <= '9';
            boolean specialChars = c == '-' || c == '.' || c == '_';

            if (!(alphabets || numbers || specialChars)) {
                addActionError("Collection names can only be alphanumeric and contain '-','.' and '_'");
                return ERROR.toUpperCase();
            }
        }

        // unique names
        ApiCollection sameNameCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if (sameNameCollection != null){
            addActionError("Collection names must be unique");
            return ERROR.toUpperCase();
        }

        // do not change hostName or vxlanId here
        ApiCollection apiCollection = new ApiCollection(Context.now(), collectionName,Context.now(),new HashSet<>(), null, 0);
        ApiCollectionsDao.instance.insertOne(apiCollection);
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(apiCollection);
        return Action.SUCCESS.toUpperCase();
    }

    public String deleteCollection() {
        
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(new ApiCollection(apiCollectionId, null, 0, null, null, 0));
        return this.deleteMultipleCollections();
    } 

    public String deleteMultipleCollections() {
        List<Integer> apiCollectionIds = new ArrayList<>();
        for(ApiCollection apiCollection: this.apiCollections) {
            if(apiCollection.getId() == 0) {
                continue;
            }
            apiCollectionIds.add(apiCollection.getId());
        }

        ApiCollectionsDao.instance.deleteAll(Filters.in("_id", apiCollectionIds));
        SingleTypeInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        APISpecDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        SensitiveParamInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));      
        // TODO : Markov and Relationship
        // MarkovDao.instance.deleteAll()
        // RelationshipDao.instance.deleteAll();
              

        return SUCCESS.toUpperCase();
    }

    public List<ApiCollection> getApiCollections() {
        return this.apiCollections;
    }

    public void setApiCollections(List<ApiCollection> apiCollections) {
        this.apiCollections = apiCollections;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }
  
    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    } 

}
