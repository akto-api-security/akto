package com.akto.threat.backend.dao;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * Base class for all threat detection DAOs.
 * Provides common database access using a static MongoClient.
 *
 * Two types of DAOs can extend this:
 * 1. Fixed collection DAOs (extend and implement getCollectionName/getClassType)
 * 2. Dynamic collection DAOs (extend but use getDatabase() directly)
 */
public abstract class AccountBasedDao<T> {

    // Static client holder - shared across all threat detection DAOs
    protected static MongoClient[] clients = new MongoClient[1];

    /**
     * Get database for a specific account.
     * Can be used by both fixed and dynamic collection DAOs.
     */
    protected MongoDatabase getDatabase(String accountId) {
        return clients[0].getDatabase(accountId);
    }

    /**
     * Get collection with fixed name (for DAOs with single collection).
     * Only works if getCollectionName() and getClassType() are properly implemented.
     */
    protected MongoCollection<T> getCollection(String accountId) {
        return getDatabase(accountId).getCollection(getCollectionName(), getClassType());
    }

    /**
     * Override this for DAOs with a fixed collection name.
     * Return null if your DAO handles dynamic collection names.
     */
    protected abstract String getCollectionName();

    /**
     * Override this to return the model class type.
     */
    protected abstract Class<T> getClassType();
}
