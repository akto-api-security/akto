package com.akto.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.akto.dao.APISpecDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SensitiveParamInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.util.Pair;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;

import org.bson.conversions.Bson;

public class ApiCollectionsAction extends UserAction {
    
    List<ApiCollection> apiCollections = new ArrayList<>();
    int apiCollectionId;

    public String fetchAllCollections() {
        this.apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject(), Projections.exclude("urls"));

        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId = 
            new BasicDBObject("apiCollectionId", "$apiCollectionId")
            .append("url", "$url")
            .append("method", "$method");
        pipeline.add(Aggregates.group(groupedId, Accumulators.min("startTs", "$timestamp")));
        MongoCursor<BasicDBObject> endpointsCursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        Map<Integer, Pair<Integer, Integer>> mapIdToCountAndTs = new HashMap<>();
        
        while(endpointsCursor.hasNext()) {
            BasicDBObject endpoint = endpointsCursor.next();
            BasicDBObject endpointId = (BasicDBObject) endpoint.get("_id");
            int apiCollectionId = endpointId.getInt("apiCollectionId");

            mapIdToCountAndTs.putIfAbsent(apiCollectionId, new Pair<Integer, Integer>(0, 0));

            Pair<Integer, Integer> countAndTs = mapIdToCountAndTs.get(apiCollectionId);
            int currCount = countAndTs.getFirst();
            int currTs = countAndTs.getSecond();
            countAndTs.setFirst(currCount + 1);
            countAndTs.setSecond(Math.max(currTs, endpoint.getInt("startTs")));
        }

        for (ApiCollection apiCollection: this.apiCollections) {
            Pair<Integer, Integer> countAndTs = mapIdToCountAndTs.get(apiCollection.getId());
            if (countAndTs == null) {
                countAndTs = new Pair<>(0, 0);
            }
            apiCollection.setUrls(new HashSet<>());
            apiCollection.getUrls().add(countAndTs.getFirst()+"_"+countAndTs.getSecond());
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

        ApiCollection apiCollection = new ApiCollection(Context.now(), collectionName,Context.now(),new HashSet<>(), null, 0);
        ApiCollectionsDao.instance.insertOne(apiCollection);
        this.apiCollections = new ArrayList<>();
        this.apiCollections.add(apiCollection);
        return Action.SUCCESS.toUpperCase();
    }

    public String deleteCollection() {
        if(apiCollectionId == 0) {
            return Action.SUCCESS.toUpperCase();
        }
        ApiCollectionsDao.instance.deleteAll(Filters.eq("_id", apiCollectionId));
        SingleTypeInfoDao.instance.deleteAll(Filters.eq("apiCollectionId", apiCollectionId));
        APISpecDao.instance.deleteAll(Filters.eq("apiCollectionId", apiCollectionId));
        SensitiveParamInfoDao.instance.deleteAll(Filters.eq("apiCollectionId", apiCollectionId));
        // TODO : Markov and Relationship
        // MarkovDao.instance.deleteAll()
        // RelationshipDao.instance.deleteAll();
        return Action.SUCCESS.toUpperCase();
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
