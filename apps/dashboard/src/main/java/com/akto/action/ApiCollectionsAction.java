package com.akto.action;

import java.util.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.dao.APISpecDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.CollectionConditions.ApiListCondition;
import com.akto.dto.CollectionConditions.CollectionCondition;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;

public class ApiCollectionsAction extends UserAction {

    List<ApiCollection> apiCollections = new ArrayList<>();
    int apiCollectionId;
    List<ApiInfoKey> apiList;

    public List<ApiInfoKey> getApiList() {
        return apiList;
    }

    public void setApiList(List<ApiInfoKey> apiList) {
        this.apiList = apiList;
    }

    public String fetchAllCollections() {
        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());

        Map<Integer, Integer> countMap = ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMap();

        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            Integer count = countMap.get(apiCollectionId);
            if (count != null && apiCollection.getHostName() != null ) {
                apiCollection.setUrlsCount(count);
            } else {
                apiCollection.setUrlsCount(apiCollection.getUrls()!=null ? apiCollection.getUrls().size() : 0);
            }
            apiCollection.setUrls(new HashSet<>());
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String fetchCollection() {
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(ApiCollectionsDao.instance.findOne(Filters.eq(Constants.ID, apiCollectionId)));
        return Action.SUCCESS.toUpperCase();
    }

    static int maxCollectionNameLength = 25;
    private String collectionName;

    private boolean isValidApiCollectionName(){
        if (this.collectionName == null) {
            addActionError("Invalid collection name");
            return false;
        }

        if (this.collectionName.length() > maxCollectionNameLength) {
            addActionError("Custom collections max length: " + maxCollectionNameLength);
            return false;
        }

        for (char c: this.collectionName.toCharArray()) {
            boolean alphabets = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            boolean numbers = c >= '0' && c <= '9';
            boolean specialChars = c == '-' || c == '.' || c == '_';
            boolean spaces = c == ' ';

            if (!(alphabets || numbers || specialChars || spaces)) {
                addActionError("Collection names can only be alphanumeric and contain '-','.' and '_'");
                return false;
            }
        }

        // unique names
        ApiCollection sameNameCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if (sameNameCollection != null){
            addActionError("Collection names must be unique");
            return false;
        }

        return true;
    }

    public String createCollection() {
        
        if(!isValidApiCollectionName()){
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

        Bson filter = Filters.in(SingleTypeInfo._COLLECTION_IDS, apiCollectionIds);
        Bson update = Updates.pullAll(SingleTypeInfo._COLLECTION_IDS, apiCollectionIds);

        SingleTypeInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        SingleTypeInfoDao.instance.updateMany(filter, update);
        APISpecDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        SensitiveParamInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));                    
        SensitiveParamInfoDao.instance.updateMany(filter, update);
        return SUCCESS.toUpperCase();
    }

    private void doUpdate(ApiCollection apiCollection, List<CollectionCondition> conditions, Set<ApiInfoKey> apisToRemove, boolean add){
        Set<ApiInfoKey> apis = new HashSet<>();
        conditions.forEach((condition) -> {
            apis.addAll(condition.returnApis());
        });

        Set<String> urls = new HashSet<>();
        apis.forEach((api) -> {
            urls.add(api.getUrl() + " " + api.getMethod());
        });

        // upsert is true, by default.
        ApiCollectionsDao.instance.updateOne(
            Filters.and(
                Filters.eq(Constants.ID, apiCollection.getId()),
                Filters.eq(ApiCollection.NAME, apiCollection.getName())
            ),
            Updates.combine(
                    Updates.set(ApiCollection.CONDITIONS_STRING, conditions),
                    Updates.set(ApiCollection.URLS_STRING, urls)));

        if(add){
            ApiCollectionUsers.addApisInCollectionsForCollectionId(apis, apiCollection.getId());
        } else {
            ApiCollectionUsers.removeApisFromCollectionsForCollectionId(apisToRemove, apiCollection.getId());
        }

    }

    public String addApisToCustomCollection(){

        if(apiList.isEmpty()){
            addActionError("No APIs selected");
            return ERROR.toUpperCase();
        }

        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if(apiCollection == null){
            
            if(!isValidApiCollectionName()){
                return ERROR.toUpperCase();
            }

            apiCollection = new ApiCollection(Context.now(), collectionName, new ArrayList<>() );

        } else if(!apiCollection.getType().equals(ApiCollection.Type.API_GROUP)){
            addActionError("Invalid api collection group.");
            return ERROR.toUpperCase();
        }

        List<CollectionCondition> conditions = apiCollection.getConditions();
        
        if(conditions == null){
            conditions = new ArrayList<>();
        }

        boolean found = false;

        for(CollectionCondition condition : conditions){
            if(condition.getType().equals(CollectionCondition.Type.API_LIST)){
                Set<ApiInfoKey> tmp = condition.returnApis();
                tmp.addAll(apiList);
                ((ApiListCondition) condition).setApiList(tmp);
                found = true;
            }
        }
        
        if(!found){
            conditions.add(new ApiListCondition(new HashSet<>(apiList)));
        }

        doUpdate(apiCollection, conditions, new HashSet<>(), true);
        fetchAllCollections();

        return SUCCESS.toUpperCase();
    }

    public String removeApisFromCustomCollection(){

        if(apiList.isEmpty()){
            addActionError("No APIs selected");
            return ERROR.toUpperCase();
        }

        if(collectionName == null){
            addActionError("Invalid collection name");
            return ERROR.toUpperCase();
        }

        ApiCollection apiCollection = ApiCollectionsDao.instance.findByName(collectionName);
        if(apiCollection == null || !apiCollection.getType().equals(ApiCollection.Type.API_GROUP)){
            addActionError("Invalid api collection group");
            return ERROR.toUpperCase();
        }

        List<CollectionCondition> conditions = apiCollection.getConditions();

        if(conditions == null){
            conditions = new ArrayList<>();
        }

        for(CollectionCondition condition : conditions){
            if(condition.getType().equals(CollectionCondition.Type.API_LIST)){
                Set<ApiInfoKey> tmp = condition.returnApis();
                tmp.removeAll(new HashSet<>(apiList));
                ((ApiListCondition) condition).setApiList(tmp);
            }
        }

        doUpdate(apiCollection, conditions, new HashSet<>(apiList), false);
        fetchAllCollections();
    
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
