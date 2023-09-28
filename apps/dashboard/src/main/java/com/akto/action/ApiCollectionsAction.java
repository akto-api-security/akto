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
import com.akto.dao.testing.TestingRunDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionTestStatus;
import com.akto.dto.testing.TestingEndpoints;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Sorts;
import com.opensymphony.xwork2.Action;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.Document;

public class ApiCollectionsAction extends UserAction {

    List<ApiCollection> apiCollections = new ArrayList<>();
    List<ApiCollectionTestStatus> apiCollectionTestStatus = new ArrayList<>();
    int apiCollectionId;

    public String fetchAllCollections() {
        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        this.apiCollectionTestStatus = new ArrayList<>();

        Map<Integer, Integer> countMap = ApiCollectionsDao.instance.buildEndpointsCountToApiCollectionMap();
        Map<Integer, Pair<String,Integer>> map = new HashMap<>();
        try (MongoCursor<BasicDBObject> cursor = TestingRunDao.instance.getMCollection().aggregate(
                Arrays.asList(
                        Aggregates.match(Filters.eq("testingEndpoints.type", TestingEndpoints.Type.COLLECTION_WISE.name())),
                        Aggregates.sort(Sorts.descending(Arrays.asList("testingEndpoints.apiCollectionId", "endTimestamp"))),
                        new Document("$group", new Document("_id", "$testingEndpoints.apiCollectionId")
                                .append("latestRun", new Document("$first", "$$ROOT")))
                ), BasicDBObject.class
        ).cursor()) {
            while (cursor.hasNext()) {
                BasicDBObject basicDBObject = cursor.next();
                BasicDBObject latestRun = (BasicDBObject) basicDBObject.get("latestRun");
                String state = latestRun.getString("state");
                int endTimestamp = latestRun.getInt("endTimestamp", -1);
                int collectionId = ((BasicDBObject)latestRun.get("testingEndpoints")).getInt("apiCollectionId");
                map.put(collectionId, Pair.of(state, endTimestamp));
            }
        }

        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            Integer count = countMap.get(apiCollectionId);
            if (count != null && apiCollection.getHostName() != null) {
                apiCollection.setUrlsCount(count);
            } else {
                apiCollection.setUrlsCount(apiCollection.getUrls().size());
            }
            if(map.containsKey(apiCollectionId)) {
                Pair<String, Integer> pair = map.get(apiCollectionId);
                apiCollectionTestStatus.add(new ApiCollectionTestStatus(apiCollection.getId(), pair.getRight(), pair.getLeft()));
            } else {
                apiCollectionTestStatus.add(new ApiCollectionTestStatus(apiCollection.getId(), -1, null));
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

    public List<ApiCollectionTestStatus> getApiCollectionTestStatus() {
        return apiCollectionTestStatus;
    }

    public void setApiCollectionTestStatus(List<ApiCollectionTestStatus> apiCollectionTestStatus) {
        this.apiCollectionTestStatus = apiCollectionTestStatus;
    }
}
