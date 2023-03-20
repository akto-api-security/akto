package com.akto.dao;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import org.bson.Document;
import com.mongodb.client.result.UpdateResult;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;

public abstract class MCollection<T> {
    private Logger logger = LoggerFactory.getLogger(getClassT());
    public static MongoClient[] clients = new MongoClient[1];
    abstract public String getDBName();
    abstract public String getCollName();
    abstract public Class<T> getClassT();

    public Document getStats() {
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());
        return mongoDatabase.runCommand(new Document("serverStatus",1));
    }

    public MongoCollection<T> getMCollection() {
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());
        return mongoDatabase.getCollection(getCollName(), getClassT());
    }

    public<V> List<T> findAll(String key, V value) {
        return this.findAll(eq(key, value));
    }

    public<V, U> List<T> findAll(String key1, V value1, String key2, U value2) {
        return this.findAll(and(eq(key1, value1), eq(key2, value2)));
    }

    public<V> List<T> findAll(String key, Collection<V> values) {
        MongoCursor<T> cursor = this.getMCollection().find(in(key, values)).cursor();

        ArrayList<T> ret = new ArrayList<T>();

        while(cursor.hasNext()) {
            T elem = cursor.next();
            ret.add(elem);
        }

        return ret;
    }
    public List<T> findAll(Bson q) {
        return findAll(q, null);
    }

    public long findCount(Bson q) {
        return this.getMCollection().countDocuments(q);
    }

    public List<T> findAll(Bson q, Bson projection) {
        return findAll(q, 0, 1_000_000, null, projection);
    }

    public List<T> findAll(Bson q, int skip, int limit, Bson sort) {
        return findAll(q, skip, limit, sort, null);
    }

    public List<T> findAll(Bson q, int skip, int limit, Bson sort, Bson projection) {
        
        FindIterable<T> commands = this.getMCollection().find(q).skip(skip).limit(limit);

        if (projection != null) {
            commands.projection(projection);
        }

        if (sort != null) {
            commands = commands.sort(sort);
        }

        MongoCursor<T> cursor = commands.maxTime(30, TimeUnit.SECONDS).cursor();

        ArrayList<T> ret = new ArrayList<T>();

        while(cursor.hasNext()) {
            T elem = cursor.next();
            ret.add(elem);
        }

        return ret;
    }

    public<V> T findOne(String key, V value) {
        return this.findOne(eq(key, value));
    }

    public<V, U> T findOne(String key1, V value1, String key2, U value2) {
        return this.findOne(and(eq(key1, value1), eq(key2, value2)));
    }

    public<V> T findOne(String key, Collection<V> values) {
        return this.findOne(eq(key, values));
    }

    public T findLatestOne(Bson q) {
        MongoCursor<T> cursor = this.getMCollection().find(q).limit(1).sort(Sorts.descending("_id")).cursor();

        while(cursor.hasNext()) {
            T elem = cursor.next();
            return elem;
        }

        return null;
    }

    public T findOne(Bson q) {
        MongoCursor<T> cursor = this.getMCollection().find(q).cursor();

        while(cursor.hasNext()) {
            T elem = cursor.next();
            return elem;
        }

        return null;
    }

    public<V> T updateOne(String key, V value, Bson obj) {
       return this.updateOne(eq(key, value), obj);
    }

    public<V, U> T updateOne(String key1, V value1, String key2, U value2, Bson obj) {
        return this.updateOne(and(eq(key1, value1), eq(key2, value2)), obj);
    }

    public<V> T updateOne(String key, Collection<V> values, Bson obj) {
        return this.updateOne(eq(key, values), obj);
    }

    public T updateOne(Bson q, Bson obj) {
        return this.getMCollection().findOneAndUpdate(q, obj, new FindOneAndUpdateOptions().upsert(true));
    }

    public UpdateResult updateMany (Bson q, Bson obj) {
        return this.getMCollection().updateMany(q, obj);
    }
    public BulkWriteResult bulkWrite (List<WriteModel<T>> modelList, BulkWriteOptions options) {
        return this.getMCollection().bulkWrite(modelList, options);
    }

    public UpdateResult replaceOne(Bson q, T obj) {
        return this.getMCollection().replaceOne(q, obj, new ReplaceOptions().upsert(true));
    }

    public InsertOneResult insertOne(T elem) {
        return getMCollection().insertOne(elem);
    }

    public InsertManyResult insertMany(List<T> elems) {

        return getMCollection().insertMany(elems);
    }


    
    public DeleteResult deleteAll(Bson q) {
        return this.getMCollection().deleteMany(q);
    }
 

    public <TResult> Set<TResult> findDistinctFields(String fieldName, Class<TResult> resultClass, Bson filter) {
        DistinctIterable<TResult> r = getMCollection().distinct(fieldName,filter,resultClass);
        Set<TResult> result = new HashSet<>();
        MongoCursor<TResult> cursor = r.cursor();
        while (cursor.hasNext()) {
            result.add(cursor.next());
        }
        return result;
    }

    public Logger getLogger() {
        return logger;
    }

    public void createView(String viewName, List<Bson> pipeline) {
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());
        mongoDatabase.createView(viewName, getCollName(), pipeline);
    }

    public void createOnDemandView(List<Bson> pipeline) {
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());
        AggregateIterable<Document> res = mongoDatabase.getCollection(getCollName()).aggregate(pipeline);
        Document doc = res.first();
    }

    public void mergeCollections(List<Bson> pipeline) {
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());
        AggregateIterable<Document> res = mongoDatabase.getCollection(getCollName()).aggregate(pipeline);
        Document doc = res.first();
    }

}
