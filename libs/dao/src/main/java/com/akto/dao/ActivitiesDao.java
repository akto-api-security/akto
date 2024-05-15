package com.akto.dao;
import java.util.ArrayList;
import java.util.List;

import com.akto.util.DbMode;
import org.bson.Document;
import org.bson.conversions.Bson;

import com.akto.dao.context.Context;
import com.akto.dto.Activity;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;

public class ActivitiesDao extends AccountsContextDao<Activity> {

    public static final ActivitiesDao instance = new ActivitiesDao();

    public static final int maxDocuments = 1000;
    public static final int sizeInBytes = 100_000_000;
    public void createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get()+"";
        MongoDatabase db = clients[0].getDatabase(dbName);
        for (String col: db.listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            if (DbMode.allowCappedCollections()) {
                db.createCollection(getCollName(), new CreateCollectionOptions().capped(true).maxDocuments(maxDocuments).sizeInBytes(sizeInBytes));
            } else {
                db.createCollection(getCollName());
            }
        }
        
        MongoCursor<Document> cursor = db.getCollection(getCollName()).listIndexes().cursor();
        List<Document> indices = new ArrayList<>();

        while (cursor.hasNext()) {
            indices.add(cursor.next());
        }

        if (indices.size() == 1) {
            instance.getMCollection().createIndex(Indexes.descending(Activity.TIME_STAMP));
        }
    }

    public void insertActivity(String type, String description){
        Activity activity = new Activity(type, description, Context.now());
        instance.insertOne(activity);
    }

    public List<Activity> fetchRecentActivitiesFeed(int skip, int limit){
        List<Activity> activities = new ArrayList<>();
        Bson sort = Sorts.orderBy(Sorts.descending(Activity.TIME_STAMP));
        activities = instance.findAll(Filters.empty(), skip, limit, sort);
        return activities;
    }

    @Override
    public String getCollName() {
        return "activities";
    }

    @Override
    public Class<Activity> getClassT() {
        return Activity.class;
    }
    
}
