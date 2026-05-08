package com.akto.service;

import com.akto.dao.threat_detection.ApiHitCountInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.*;

/**
 * Service class for handling API Collection URL operations.
 * Separates database queries and business logic from the Action layer.
 */
public class ApiCollectionUrlService {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiCollectionUrlService.class, LogDb.DASHBOARD);
    private static final int URL_SAMPLING_LIMIT = 1000;
    
    /**
     * Populates URLs for MCP collections from api_hit_count_info.
     * 
     * @param apiCollection The API collection to populate URLs for
     */
    public void populateMcpCollectionUrls(ApiCollection apiCollection) {
        if (!apiCollection.isMcpCollection()) {
            apiCollection.setUrls(new HashSet<>());
            return;
        }
        
        int collectionId = apiCollection.getId();
        Set<String> urls = fetchUrlsForCollection(collectionId);
        
        if (!urls.isEmpty()) {
            apiCollection.setUrls(urls);
        } else {
            // Keep existing URLs if no data found in api_hit_count_info
            if (apiCollection.getUrls() == null) {
                apiCollection.setUrls(new HashSet<>());
            }
        }
    }
    
    /**
     * Fetches URLs for a specific collection from api_hit_count_info.
     * Uses sampling for large collections to maintain performance.
     * 
     * @param collectionId The ID of the collection
     * @return Set of URLs in "url method" format
     */
    private Set<String> fetchUrlsForCollection(int collectionId) {
        int totalUniqueUrls = countUniqueUrls(collectionId);
        
        if (totalUniqueUrls == 0) {
            return new HashSet<>();
        }
        
        if (totalUniqueUrls > URL_SAMPLING_LIMIT) {
            return fetchSampledUrls(collectionId, totalUniqueUrls);
        } else {
            return fetchAllUrls(collectionId);
        }
    }
    
    /**
     * Counts the total number of unique URLs for a collection.
     * 
     * @param collectionId The ID of the collection
     * @return Count of unique URLs
     */
    private int countUniqueUrls(int collectionId) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.eq("apiCollectionId", collectionId)));
        pipeline.add(Aggregates.group(
            new BasicDBObject("url", "$url").append("method", "$method")
        ));
        pipeline.add(Aggregates.count("totalCount"));
        
        try (MongoCursor<BasicDBObject> cursor = ApiHitCountInfoDao.instance.getMCollection()
            .aggregate(pipeline, BasicDBObject.class).cursor()) {
            
            if (cursor.hasNext()) {
                BasicDBObject result = cursor.next();
                return result.getInt("totalCount", 0);
            }
        }
        
        return 0;
    }
    
    /**
     * Fetches a sampled subset of URLs for large collections.
     * Uses MongoDB's $sample operator for random sampling.
     * 
     * @param collectionId The ID of the collection
     * @param totalUniqueUrls Total count of unique URLs
     * @return Set of sampled URLs
     */
    private Set<String> fetchSampledUrls(int collectionId, int totalUniqueUrls) {
        Set<String> urls = new HashSet<>();
        double samplingRate = (double) URL_SAMPLING_LIMIT / totalUniqueUrls;
        
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.eq("apiCollectionId", collectionId)));
        pipeline.add(Aggregates.group(
            new BasicDBObject("url", "$url").append("method", "$method"),
            Accumulators.first("count", "$count")
        ));
        pipeline.add(Aggregates.sample(Math.min(URL_SAMPLING_LIMIT, totalUniqueUrls)));
        
        try (MongoCursor<BasicDBObject> cursor = ApiHitCountInfoDao.instance.getMCollection()
            .aggregate(pipeline, BasicDBObject.class).cursor()) {
            
            urls = extractUrlsFromCursor(cursor);
        }
        
        loggerMaker.debugAndAddToDb(
            String.format("Collection %d: Sampled %d URLs from %d total (%.2f%% sampling rate)", 
                collectionId, urls.size(), totalUniqueUrls, samplingRate * 100),
            LogDb.DASHBOARD
        );
        
        return urls;
    }
    
    /**
     * Fetches all URLs for smaller collections.
     * 
     * @param collectionId The ID of the collection
     * @return Set of all URLs
     */
    private Set<String> fetchAllUrls(int collectionId) {
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.match(Filters.eq("apiCollectionId", collectionId)));
        pipeline.add(Aggregates.group(
            new BasicDBObject("url", "$url").append("method", "$method")
        ));
        
        try (MongoCursor<BasicDBObject> cursor = ApiHitCountInfoDao.instance.getMCollection()
            .aggregate(pipeline, BasicDBObject.class).cursor()) {
            
            return extractUrlsFromCursor(cursor);
        }
    }
    
    /**
     * Extracts URLs from a MongoDB cursor result.
     * 
     * @param cursor MongoDB cursor with aggregation results
     * @return Set of URLs in "url method" format
     */
    private Set<String> extractUrlsFromCursor(MongoCursor<BasicDBObject> cursor) {
        Set<String> urls = new HashSet<>();
        
        while (cursor.hasNext()) {
            BasicDBObject result = cursor.next();
            BasicDBObject id = (BasicDBObject) result.get("_id");
            
            if (id != null) {
                String url = id.getString("url");
                String method = id.getString("method");
                
                if (url != null && method != null) {
                    urls.add(url + " " + method);
                }
            }
        }
        
        return urls;
    }
}