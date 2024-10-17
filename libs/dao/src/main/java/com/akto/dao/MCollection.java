package com.akto.dao;

import com.akto.util.DbMode;
import com.mongodb.BasicDBObject;
import com.mongodb.CreateIndexCommitQuorum;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertManyResult;
import com.mongodb.client.result.InsertOneResult;

import org.bson.Document;
import com.mongodb.client.result.UpdateResult;

import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.mongodb.client.model.Filters.*;
import static java.util.Collections.singletonList;

public abstract class MCollection<T> {
    private Logger logger = LoggerFactory.getLogger(getClassT());
    public static MongoClient[] clients = new MongoClient[1];
    public static final String SET = "$set";
    public static final String ID = "_id";
    public static final String NAME = "name";
    public static final String ROOT_ELEMENT = "$$ROOT";
    public static final String _COUNT = "count";
    public static final String _SIZE = "size";
    abstract public String getDBName();
    abstract public String getCollName();
    abstract public Class<T> getClassT();
    public static final Bson noMatchFilter = Filters.nor(new BasicDBObject());;

    public Document getStats() {
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());
        return mongoDatabase.runCommand(new Document("serverStatus",1));
    }

    public boolean isCapped() {
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());

        for (Document collection: mongoDatabase.listCollections()) {
            if (collection.getString("name").equals(getCollName())) {
                return collection.get("options", new Document()).getBoolean("capped", false);
            }
        }
        return false;
    }
    public Document convertToCappedCollection(long sizeInBytes) {
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());
        for (Document collection: mongoDatabase.listCollections()) {
            if (collection.getString("name").equals(getCollName())) {
                return mongoDatabase.runCommand(new Document("convertToCapped", getCollName())
                        .append("size", sizeInBytes));
            }
        }
        return null;
    }

    public MongoCollection<T> getMCollection() {
        return getMCollection(getDBName(), getCollName(), getClassT());
    }

    public static <T> MongoCollection<T> getMCollection(String dbName, String collectionName, Class<T> classT) {
        MongoDatabase mongoDatabase = clients[0].getDatabase(dbName);
        return mongoDatabase.getCollection(collectionName, classT);
    }

    public static boolean checkConnection() {
        try {
            clients[0].listDatabaseNames().first();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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

    public long count(Bson q) {
        return (int)this.getMCollection().countDocuments(q);
    }

    public long estimatedDocumentCount(){
        return this.getMCollection().estimatedDocumentCount();
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

    public T findOne(Bson q, Bson projection) {
        MongoCursor<T> cursor = this.getMCollection().find(q).projection(projection).cursor();

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

    public T updateOneNoUpsert(Bson q, Bson obj) {
        return this.getMCollection().findOneAndUpdate(q, obj, new FindOneAndUpdateOptions().upsert(false));
    }

    public UpdateResult updateMany (Bson q, Bson obj) {
        return this.getMCollection().updateMany(q, obj);
    }
    public UpdateResult updateManyNoUpsert (Bson q, Bson obj) {
        return this.getMCollection().updateMany(q, obj, new UpdateOptions().upsert(false));
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

    public static boolean createCollectionIfAbsent(String dbName, String collName, CreateCollectionOptions options){
        try{
            boolean exists = false;
            MongoDatabase db = clients[0].getDatabase(dbName);
            for (String col: db.listCollectionNames()){
                if (collName.equalsIgnoreCase(col)){
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                db.createCollection(collName, options);
                return true;
            }
        } catch (Exception e){
            return false;
        }
        return false;
    }

    public static boolean createIndexIfAbsent(String dbName, String collName, Bson idx, IndexOptions options) {
        try{
            MongoDatabase db = clients[0].getDatabase(dbName);

            MongoCursor<Document> cursor = db.getCollection(collName).listIndexes().cursor();
            List<Document> indices = new ArrayList<>();

            while (cursor.hasNext()) {
                indices.add(cursor.next());
            }

            for (Document index: indices) {
                if (index.get(NAME).equals(options.getName())) {
                    return true;
                }
            }

            IndexModel indexModel = new IndexModel(idx, options);

            CreateIndexOptions createIndexOptions = new CreateIndexOptions();
            createIndexOptions.maxTime(5, TimeUnit.MINUTES);
            if (DbMode.setupType.equals(DbMode.SetupType.CLUSTER)) {
                createIndexOptions.commitQuorum(CreateIndexCommitQuorum.create(1));
            }

            db.getCollection(collName).createIndexes(singletonList(indexModel), createIndexOptions);
        } catch (Exception e){
            return false;
        }

        return false;

    }

    public static boolean createUniqueIndex(String dbName, String collName, String[] fieldNames, boolean isAscending) {

        Bson indexInfo = isAscending ? Indexes.ascending(fieldNames) : Indexes.descending(fieldNames);
        String name = generateIndexName(fieldNames, isAscending);
        return createIndexIfAbsent(dbName, collName, indexInfo, new IndexOptions().name(name).unique(true));
    }

    public static boolean createIndexIfAbsent(String dbName, String collName, String[] fieldNames, boolean isAscending) {

        Bson indexInfo = isAscending ? Indexes.ascending(fieldNames) : Indexes.descending(fieldNames);
        String name = generateIndexName(fieldNames, isAscending);
        return createIndexIfAbsent(dbName, collName, indexInfo, new IndexOptions().name(name));
    }

    public static String generateIndexName(String[] fieldNames, boolean isAscending) {
        String name = "";

        int lenPerField = 30/fieldNames.length - 1;

        for (String field: fieldNames) {

            String[] tokens = field.split("\\.");
            String lastToken = tokens[tokens.length-1];
            lastToken = lastToken.substring(0, Math.min(lenPerField, lastToken.length()));
            if (!name.isEmpty()) {
                name += "-";
            }
            name += lastToken;
        }

        name += ("_");
        name += (isAscending ? "1" : "-1");
        return name;
    }

    public ObjectId findNthDocumentIdFromEnd(int n) {
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());
        MongoCursor<Document> cursor = mongoDatabase.getCollection(getCollName(), Document.class).find(new BasicDBObject())
                .sort(Sorts.descending(ID))
                .skip(n)
                .limit(1)
                .cursor();

        return cursor.hasNext() ? cursor.next().getObjectId(ID) : null;
    }

    public void trimCollection(int maxDocuments) {
        long count = this.getMCollection().estimatedDocumentCount();
        if (count <= maxDocuments) return;
        long deleteCount =  maxDocuments / 2;
        ObjectId objectId = findNthDocumentIdFromEnd((int) deleteCount);
        if (objectId == null) return;

        DeleteResult deleteResult = this.getMCollection().deleteMany(lt(ID, objectId));
        logger.info("Trimmed : " + deleteResult.getDeletedCount());
    }

    public Document getCollectionStats(){
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());
        return mongoDatabase.runCommand(new Document("collStats",getCollName()));
    }

    public Document compactCollection() {
        MongoDatabase mongoDatabase = clients[0].getDatabase(getDBName());
        return mongoDatabase.runCommand(new Document("compact", getCollName()));
    }

}
