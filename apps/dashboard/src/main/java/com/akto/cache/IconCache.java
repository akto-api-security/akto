package com.akto.cache;

import com.akto.dao.ApiCollectionIconsDao;
import com.akto.dto.ApiCollectionIcon;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe singleton cache for API collection icons.
 * Provides two-level caching:
 * 1. (hostname,domainName -> ObjectId) mapping cache
 * 2. ObjectId->imageData mapping cache
 * 
 * Automatically refreshes data every 30 minutes when accessed.
 */
public class IconCache {
    private static volatile IconCache INSTANCE;
    private static final LoggerMaker logger = new LoggerMaker(IconCache.class, LogDb.DASHBOARD);

    // Cache configuration constants
    private static final long REFRESH_INTERVAL_MS = 30 * 60 * 1000; // 30 minutes TTL
    private static final String CACHE_NAME = "IconCache";

    // Static final concurrent cache data structures - thread safe
    private static final ConcurrentHashMap<String, String> hostnameToObjectIdCache = new ConcurrentHashMap<>();  // hostname -> ObjectId.toString()
    private static final ConcurrentHashMap<String, IconData> objectIdToIconDataCache = new ConcurrentHashMap<>(); // ObjectId.toString() -> IconData
    
    private volatile long lastRefreshTime = 0;
    private final Object lock = new Object();

    private IconCache() {
        // Constructor now empty - static maps are initialized above
    }

    /**
     * Get singleton instance of the cache
     */
    public static IconCache getInstance() {
        if (INSTANCE == null) {
            synchronized (IconCache.class) {
                if (INSTANCE == null) {
                    INSTANCE = new IconCache();
                }
            }
        }
        return INSTANCE;
    }


    /**
     * Inner class to hold icon data
     */
    public static class IconData {
        private final String domainName;
        private final String imageData;
        private final String contentType;
        private final int updatedAt;

        public IconData(String domainName, String imageData, String contentType, int updatedAt) {
            this.domainName = domainName;
            this.imageData = imageData;
            this.contentType = contentType;
            this.updatedAt = updatedAt;
        }

        public String getDomainName() { return domainName; }
        public String getImageData() { return imageData; }
        public String getContentType() { return contentType; }
        public int getUpdatedAt() { return updatedAt; }
    }

    /**
     * Get icon data for hostname with cache-first approach and domain stripping
     * @param hostname The hostname to lookup
     * @return IconData if found, null otherwise
     */
    public IconData getIconData(String hostname) {
        if (hostname == null || hostname.trim().isEmpty()) {
            return null;
        }

        refreshCacheIfExpired();
        
        // First, try to find exact hostname match in cache
        IconData exactMatch = findExactHostnameMatch(hostname);
        if (exactMatch != null) {
            logger.infoAndAddToDb("Icon cache exact hit for hostname: " + hostname);
            return exactMatch;
        }

        // Strip hostname progressively and check for domain matches
        IconData domainMatch = findDomainMatchByStripping(hostname);
        if (domainMatch != null) {
            logger.infoAndAddToDb("Icon cache domain hit for hostname: " + hostname + " -> domain: " + domainMatch.getDomainName());
            return domainMatch;
        }

        logger.infoAndAddToDb("Icon cache miss for hostname: " + hostname);
        return null;
    }

    /**
     * Find exact hostname match in cache by checking composite keys
     */
    private IconData findExactHostnameMatch(String hostname) {
        for (Map.Entry<String, String> entry : hostnameToObjectIdCache.entrySet()) {
            String compositeKey = entry.getKey();
            // Extract hostname from composite key "hostname,domain"
            String[] parts = compositeKey.split(",", 2);
            if (parts.length == 2 && hostname.equals(parts[0])) {
                String objectIdStr = entry.getValue();
                IconData iconData = objectIdToIconDataCache.get(objectIdStr);
                if (iconData != null) {
                    return iconData;
                }
            }
        }
        return null;
    }

    /**
     * Find domain match by progressively stripping hostname to find matching domains
     * Examples: abc.tapestry.com -> check tapestry.com -> found -> add cache entry for abc.tapestry.com
     */
    private IconData findDomainMatchByStripping(String hostname) {
        String[] hostParts = hostname.split("\\.");
        if (hostParts.length < 2) {
            return null;
        }

        // Strip hostname progressively (abc.tapestry.com -> tapestry.com -> com)
        for (int i = 1; i <= hostParts.length - 2; i++) {
            StringBuilder domainBuilder = new StringBuilder();
            for (int j = i; j < hostParts.length; j++) {
                if (j > i) domainBuilder.append(".");
                domainBuilder.append(hostParts[j]);
            }
            String candidateDomain = domainBuilder.toString();

            // Check if this domain exists in cache by looking at domain part of composite keys
            for (Map.Entry<String, String> entry : hostnameToObjectIdCache.entrySet()) {
                String compositeKey = entry.getKey();
                String[] parts = compositeKey.split(",", 2);
                if (parts.length == 2 && candidateDomain.equals(parts[1])) {
                    String objectIdStr = entry.getValue();
                    IconData iconData = objectIdToIconDataCache.get(objectIdStr);
                    if (iconData != null) {
                        // Found domain match - add new cache entry for this hostname
                        String newCompositeKey = hostname + "," + candidateDomain;
                        hostnameToObjectIdCache.put(newCompositeKey, objectIdStr);
                        logger.infoAndAddToDb("Added cache entry for hostname: " + hostname + " with domain: " + candidateDomain);
                        return iconData;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Update cache when a new hostname is added to an existing domain's matchingHostnames
     * @param newHostname The new hostname to add to cache
     * @param domainName The domain name for the composite key
     * @param existingObjectId The ObjectId of existing icon document
     */
    public void addHostnameMapping(String newHostname, String domainName, ObjectId existingObjectId) {
        if (newHostname == null || domainName == null || existingObjectId == null) {
            return;
        }

        String objectIdStr = existingObjectId.toString();

        // Only add hostname mapping if we have the icon data already cached
        if (objectIdToIconDataCache.containsKey(objectIdStr)) {
            String compositeKey = newHostname + "," + domainName;
            hostnameToObjectIdCache.put(compositeKey, objectIdStr);
            logger.infoAndAddToDb("Added hostname mapping to cache: " + compositeKey + " -> " + objectIdStr);
        }
    }


    /**
     * Check if cache needs refresh and refresh if expired.
     * Thread-safe with double-check locking pattern similar to AccountConfigurationCache.
     */
    private void refreshCacheIfExpired() {
        long currentTime = System.currentTimeMillis();

        // Check if cache is expired or empty
        if (hostnameToObjectIdCache.isEmpty() || (currentTime - lastRefreshTime) >= REFRESH_INTERVAL_MS) {
            synchronized (lock) {
                // Double-check after acquiring lock to prevent multiple refreshes
                if (hostnameToObjectIdCache.isEmpty() || (currentTime - lastRefreshTime) >= REFRESH_INTERVAL_MS) {
                    refreshCache();
                }
            }
        }
    }


    private void refreshCache() {
        try {
            logger.infoAndAddToDb("Refreshing " + CACHE_NAME + " from database");

            // Load all icons from database with proper field selection
            List<ApiCollectionIcon> allIcons = ApiCollectionIconsDao.instance.findAll(
                Filters.empty(),
                // Project only required fields: _id, domainName, matchingHostnames, imageData
                Projections.include("_id", ApiCollectionIcon.DOMAIN_NAME, ApiCollectionIcon.MATCHING_HOSTNAMES, ApiCollectionIcon.IMAGE_DATA)
            );

            if (allIcons == null) {
                logger.errorAndAddToDb("Database query returned null. Cannot refresh " + CACHE_NAME);
                return; // Keep old cache
            }

            logger.infoAndAddToDb("Fetched " + allIcons.size() + " icon records from database");

            // Clear existing static cache maps
            synchronized (lock) {
                hostnameToObjectIdCache.clear();
                objectIdToIconDataCache.clear();
            }

            int validIconsCount = 0;
            int invalidIconsCount = 0;
            for (ApiCollectionIcon icon : allIcons) {
                // Debug each icon record
                boolean hasId = icon.getId() != null;
                boolean hasDomain = icon.getDomainName() != null && !icon.getDomainName().trim().isEmpty();
                boolean isAvailable = icon.isAvailable();
                
                logger.infoAndAddToDb("Processing icon: domain=" + icon.getDomainName() + 
                                    ", hasId=" + hasId + 
                                    ", hasDomain=" + hasDomain + 
                                    ", isAvailable=" + isAvailable + 
                                    ", imageDataLength=" + (icon.getImageData() != null ? icon.getImageData().length() : 0));
                
                if (hasId && hasDomain && isAvailable) {
                    String objectIdStr = icon.getId().toString();
                    
                    // Create IconData with available information
                    IconData iconDataObj = new IconData(
                        icon.getDomainName(),
                        icon.getImageData(),
                        "image/png", // Always PNG as specified
                        icon.getUpdatedAt() > 0 ? icon.getUpdatedAt() : icon.getCreatedAt()
                    );

                    // Add to static objectId cache
                    objectIdToIconDataCache.put(objectIdStr, iconDataObj);
                    validIconsCount++;

                    // Add hostname mappings directly as string keys
                    if (icon.getMatchingHostnames() != null && !icon.getMatchingHostnames().isEmpty()) {
                        for (String hostname : icon.getMatchingHostnames()) {
                            if (hostname != null && !hostname.trim().isEmpty()) {
                                // Store hostname directly as key (format: "hostname,domain")
                                String compositeKey = hostname + "," + icon.getDomainName();
                                hostnameToObjectIdCache.put(compositeKey, objectIdStr);
                            }
                        }
                    }
                } else {
                    invalidIconsCount++;
                    logger.infoAndAddToDb("Skipping invalid icon: domain=" + icon.getDomainName() + 
                                        " (reason: " + (!hasId ? "no ID " : "") + 
                                        (!hasDomain ? "no domain " : "") + 
                                        (!isAvailable ? "not available" : "") + ")");
                }
            }

            // Update refresh timestamp
            this.lastRefreshTime = System.currentTimeMillis();

            logger.infoAndAddToDb(CACHE_NAME + " refreshed successfully. Valid icons: " + validIconsCount + 
                                  ", Invalid icons: " + invalidIconsCount + 
                                  ", Hostname mappings: " + hostnameToObjectIdCache.size() + 
                                  ", Icon data entries: " + objectIdToIconDataCache.size());

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error refreshing " + CACHE_NAME + ". Keeping old cache if available.");
            // Graceful degradation - keep existing cache
        }
    }


    /**
     * Clear the cache (useful for testing or manual refresh).
     * Thread-safe operation following AccountConfigurationCache pattern.
     */
    public void clear() {
        synchronized (lock) {
            hostnameToObjectIdCache.clear();
            objectIdToIconDataCache.clear();
            this.lastRefreshTime = 0;
            logger.infoAndAddToDb(CACHE_NAME + " cleared");
        }
    }


    /**
     * Check if cache is expired or empty.
     * Used for monitoring and diagnostics.
     */
    public boolean isExpired() {
        if (lastRefreshTime == 0) return true; // Never initialized
        long elapsed = System.currentTimeMillis() - lastRefreshTime;
        return elapsed >= REFRESH_INTERVAL_MS;
    }

    /**
     * Get the hostname to ObjectId cache for UI
     * @return Map of hostname string to ObjectId string
     */
    public Map<String, String> getHostnameToObjectIdCache() {
        refreshCacheIfExpired();
        return new HashMap<>(hostnameToObjectIdCache);
    }

    /**
     * Get the ObjectId to IconData cache for UI
     * @return Map of ObjectId string to IconData
     */
    public Map<String, IconData> getObjectIdToIconDataCache() {
        refreshCacheIfExpired();
        return new HashMap<>(objectIdToIconDataCache);
    }
}