package com.akto.action;

import java.util.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import org.bson.conversions.Bson;

public class ApiCollectionsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiCollectionsAction.class);

    List<ApiCollection> apiCollections = new ArrayList<>();
    int apiCollectionId;

    boolean redacted;

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
        SampleDataDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
        TrafficInfoDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));
        ApiInfoDao.instance.deleteAll(Filters.in("_id.apiCollectionId", apiCollectionIds));

        return SUCCESS.toUpperCase();
    }

    public static void dropSampleDataForApiCollection() {
        loggerMaker.infoAndAddToDb("Dropping sample data for all api collections", LoggerMaker.LogDb.DASHBOARD);
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(Filters.eq(ApiCollection.SAMPLE_COLLECTIONS_DROPPED, false));
        for (ApiCollection apiCollection: apiCollections) {
            int apiCollectionId = apiCollection.getId();
            loggerMaker.infoAndAddToDb("Dropping sti data for api collection: " + apiCollectionId, LoggerMaker.LogDb.DASHBOARD);
            SingleTypeInfoDao.instance.deleteAll(Filters.eq("_id", Collections.singletonList(apiCollectionId)));
            loggerMaker.infoAndAddToDb("Dropping sample data for api collection: " + apiCollectionId, LoggerMaker.LogDb.DASHBOARD);
            SampleDataDao.instance.deleteAll(Filters.eq("_id.apiCollectionId", apiCollectionId));
            loggerMaker.infoAndAddToDb("Dropping sensitive sample data for api collection: " + apiCollectionId, LoggerMaker.LogDb.DASHBOARD);
            SensitiveSampleDataDao.instance.deleteAll(Filters.eq("_id.apiCollectionId", apiCollectionId));
            ApiCollectionsDao.instance.updateOneNoUpsert(Filters.eq("_id", apiCollectionId), Updates.set(ApiCollection.SAMPLE_COLLECTIONS_DROPPED, true));
        }
    }

    public String redactCollection() {
        List<Bson> updates = Arrays.asList(
            Updates.set(ApiCollection.REDACT, redacted),
            Updates.set(ApiCollection.SAMPLE_COLLECTIONS_DROPPED, !redacted)
        );
        ApiCollectionsDao.instance.updateOneNoUpsert(Filters.eq("_id", apiCollectionId), Updates.combine(updates));
        if(redacted){
            int accountId = Context.accountId.get();
            Runnable r = () -> {
                Context.accountId.set(accountId);
                loggerMaker.infoAndAddToDb("Triggered job to delete sample data", LoggerMaker.LogDb.DASHBOARD);
                dropSampleDataForApiCollection();
            };
            new Thread(r).start();
        }
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

    public boolean isRedacted() {
        return redacted;
    }

    public void setRedacted(boolean redacted) {
        this.redacted = redacted;
    }
}
