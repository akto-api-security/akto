package com.akto.threat.backend.dao;

import com.mongodb.client.MongoClient;

/**
 * Initializes all threat detection DAOs with the threat protection MongoDB client.
 * This follows the same pattern as DaoInit in the main codebase.
 */
public class ThreatDetectionDaoInit {

    /**
     * Initialize all threat detection DAOs with the provided MongoClient.
     * This sets up the static client holder that all DAOs will use.
     *
     * @param mongoClient The MongoDB client for threat protection database
     */
    public static void init(MongoClient mongoClient) {
        // Set the static client that all AccountBasedDao instances will use
        AccountBasedDao.clients[0] = mongoClient;
    }
}
