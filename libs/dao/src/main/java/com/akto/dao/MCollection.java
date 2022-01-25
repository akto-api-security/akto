package com.akto.dao;

import com.mongodb.client.*;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;
import org.bson.Document;
import com.mongodb.client.result.UpdateResult;

import org.bson.conversions.Bson;

import java.util.*;

import static com.mongodb.client.model.Filters.*;

public abstract class MCollection<T> {

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
        MongoCursor<T> cursor = this.getMCollection().find(q).cursor();

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
}
