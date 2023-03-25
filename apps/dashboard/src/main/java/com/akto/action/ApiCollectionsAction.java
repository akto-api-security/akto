package com.akto.action;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.bson.conversions.Bson;

import com.akto.action.testing.TestRolesAction.RolesConditionUtils;
import com.akto.dao.APISpecDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.SingleTypeInfoViewDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.FetchApiCollectionResponse;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.util.LogicalGroupUtil;
import com.mongodb.client.model.Filters;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;

public class ApiCollectionsAction extends UserAction {

    List<ApiCollection> apiCollections = new ArrayList<>();
    List<FetchApiCollectionResponse> apiCollectionResponse = new ArrayList<>();
    RolesConditionUtils andConditions;
    RolesConditionUtils orConditions;
    Boolean isLogicalGroup;

    int apiCollectionId;

    public String fetchAllCollections() {
        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        // todo: add logic for logical groups
        for (ApiCollection apiCollection: apiCollections) {
            int urlCount = SingleTypeInfoViewDao.instance.getUrlCount(apiCollection.getId());
            apiCollectionResponse.add(
                new FetchApiCollectionResponse(apiCollection.getId(), apiCollection.getName(), 
                    apiCollection.getDisplayName(), apiCollection.getStartTs(), apiCollection.getUrls(), 
                    apiCollection.getHostName(), apiCollection.getVxlanId(), false, urlCount, "System")
                );
        }

        Bson logicalGroupFilter = Filters.eq("groupType", "LOGICAL_GROUP");
        List<EndpointLogicalGroup> endpointLogicalGroups = EndpointLogicalGroupDao.instance.findAll(logicalGroupFilter);

        for (EndpointLogicalGroup endpointLogicalGroup: endpointLogicalGroups) {
            int urlCount = SingleTypeInfoViewDao.instance.getUrlCountLogicalGroup(endpointLogicalGroup.getId());
            apiCollectionResponse.add(
                new FetchApiCollectionResponse(endpointLogicalGroup.getId(), endpointLogicalGroup.getGroupName(), 
                    endpointLogicalGroup.getGroupName(), endpointLogicalGroup.getCreatedTs(), new HashSet<>(), 
                "", 0, true, urlCount, endpointLogicalGroup.getCreatedBy())
                );
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
            boolean spaces = c == ' ';

            if (!(alphabets || numbers || specialChars || spaces)) {
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

        Bson logicalGroupFilter = Filters.and(Filters.eq("groupType", "LOGICAL_GROUP"), Filters.eq("groupName", "collectionName"));
        EndpointLogicalGroup logicalGroup = EndpointLogicalGroupDao.instance.findOne(logicalGroupFilter);

        if (logicalGroup != null) {
            addActionError("Collection names must be unique");
            return ERROR.toUpperCase();
        }

        this.apiCollections = new ArrayList<>();

        LogicalGroupUtil logicalGroupUtil = new LogicalGroupUtil();

        if (andConditions != null || orConditions != null) {
            Conditions orConditions = null;
            if (this.orConditions != null) {
                orConditions = new Conditions();
                orConditions.setOperator(this.orConditions.getOperator());
                orConditions.setPredicates(logicalGroupUtil.getPredicatesFromPredicatesObject(this.orConditions.getPredicates()));
            }
            Conditions andConditions = null;
            if (this.andConditions != null) {
                andConditions = new Conditions();
                andConditions.setOperator(this.andConditions.getOperator());
                andConditions.setPredicates(logicalGroupUtil.getPredicatesFromPredicatesObject(this.andConditions.getPredicates()));
            }
            EndpointLogicalGroup endpointLogicalGroup = EndpointLogicalGroupDao.instance.createLogicalGroup(collectionName, andConditions,orConditions, 
                this.getSUser().getLogin(), "LOGICAL_GROUP");

            SingleTypeInfoViewDao.instance.buildLogicalGroup(endpointLogicalGroup);

            this.apiCollections.add(new ApiCollection(endpointLogicalGroup.getId(), endpointLogicalGroup.getGroupName(), endpointLogicalGroup.getCreatedTs(), 
                new HashSet<>(), "", 0));

        } else {
            // do not change hostName or vxlanId here
            ApiCollection apiCollection = new ApiCollection(Context.now(), collectionName,Context.now(),new HashSet<>(), null, 0);
            ApiCollectionsDao.instance.insertOne(apiCollection);
            this.apiCollections.add(apiCollection);
        }
        return Action.SUCCESS.toUpperCase();
    }

    public String deleteCollection() {
        
        this.apiCollectionResponse = new ArrayList<>();
        this.apiCollectionResponse.add(new FetchApiCollectionResponse(apiCollectionId, null, null, 0, null, null, 0, isLogicalGroup, 0, null));
        return this.deleteMultipleCollections();
    } 

    public String deleteMultipleCollections() {
        List<Integer> apiCollectionIds = new ArrayList<>();
        List<Integer> logicalGroupIds = new ArrayList<>();
        for(FetchApiCollectionResponse apiCollection: this.apiCollectionResponse) {
            if(apiCollection.getId() == 0) {
                continue;
            }
            if (apiCollection.getIsLogicalGroup() == true) {
                logicalGroupIds.add(apiCollection.getId());
            } else {
                apiCollectionIds.add(apiCollection.getId());
            }
        }

        ApiCollectionsDao.instance.deleteAll(Filters.in("_id", apiCollectionIds));
        SingleTypeInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        APISpecDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));
        SensitiveParamInfoDao.instance.deleteAll(Filters.in("apiCollectionId", apiCollectionIds));                    

        for (int logicalGroupId : logicalGroupIds) {
            SingleTypeInfoViewDao.instance.removeLogicalGroup(logicalGroupId);
        }

        return SUCCESS.toUpperCase();
    }

    public List<ApiCollection> getApiCollections() {
        return this.apiCollections;
    }

    public void setApiCollections(List<ApiCollection> apiCollections) {
        this.apiCollections = apiCollections;
    }

    public List<FetchApiCollectionResponse> getApiCollectionResponse() {
        return this.apiCollectionResponse;
    }

    public void setApiCollectionResponse(List<FetchApiCollectionResponse> apiCollectionResponse) {
        this.apiCollectionResponse = apiCollectionResponse;
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

    public Boolean getIsLogicalGroup() {
        return this.isLogicalGroup;
    }
  
    public void setIsLogicalGroup(Boolean isLogicalGroup) {
        this.isLogicalGroup = isLogicalGroup;
    }

    public RolesConditionUtils getAndConditions() {
        return this.andConditions;
    }
  
    public void setAndConditions(RolesConditionUtils andConditions) {
        this.andConditions = andConditions;
    }

    public RolesConditionUtils getOrConditions() {
        return this.orConditions;
    }
  
    public void setOrConditions(RolesConditionUtils orConditions) {
        this.orConditions = orConditions;
    }

}
