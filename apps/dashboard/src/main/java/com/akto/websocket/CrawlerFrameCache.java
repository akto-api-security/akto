package com.akto.websocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache for storing latest crawler frames per crawlId.
 * Used for live browser view feature - stores only the most recent frame per crawl.
 */
public class CrawlerFrameCache {
    public static final CrawlerFrameCache instance = new CrawlerFrameCache();

    // Map: crawlId -> latest frame JSON
    private final Map<String, String> latestFrames = new ConcurrentHashMap<>();

    private CrawlerFrameCache() {}

    /**
     * Store latest frame for a crawl (replaces any existing frame)
     */
    public void storeFrame(String crawlId, String frameJson) {
        latestFrames.put(crawlId, frameJson);
    }

    /**
     * Get latest frame for a crawl
     * @return frame JSON or null if no frame exists
     */
    public String getLatestFrame(String crawlId) {
        return latestFrames.get(crawlId);
    }

    /**
     * Clear frame for a crawl (called when crawl completes)
     */
    public void clearFrame(String crawlId) {
        latestFrames.remove(crawlId);
    }

    /**
     * Get number of cached frames
     */
    public int size() {
        return latestFrames.size();
    }
}
