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

    // Cache data structures
    private volatile Map<HostnameDomainKey, String> hostnameToObjectIdCache;  // (hostname, domainName) -> ObjectId.toString()
    private volatile Map<String, IconData> objectIdToIconDataCache; // ObjectId.toString() -> IconData
    
    private volatile long lastRefreshTime = 0;
    private final Object lock = new Object();

    private IconCache() {
        this.hostnameToObjectIdCache = new ConcurrentHashMap<>();
        this.objectIdToIconDataCache = new ConcurrentHashMap<>();
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
     * Compound key for hostname-domain mapping
     */
    public static class HostnameDomainKey {
        private final String hostname;
        private final String domainName;

        public HostnameDomainKey(String hostname, String domainName) {
            this.hostname = hostname;
            this.domainName = domainName;
        }

        public String getHostname() { return hostname; }
        public String getDomainName() { return domainName; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            HostnameDomainKey that = (HostnameDomainKey) obj;
            return Objects.equals(hostname, that.hostname) && Objects.equals(domainName, that.domainName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostname, domainName);
        }

        @Override
        public String toString() {
            return "(" + hostname + ", " + domainName + ")";
        }
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
     * Find exact hostname match in cache by checking all compound keys
     */
    private IconData findExactHostnameMatch(String hostname) {
        for (Map.Entry<HostnameDomainKey, String> entry : hostnameToObjectIdCache.entrySet()) {
            if (hostname.equals(entry.getKey().getHostname())) {
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
        String[] parts = hostname.split("\\.");
        if (parts.length < 2) {
            return null;
        }

        // Strip hostname progressively (abc.tapestry.com -> tapestry.com -> com)
        for (int i = 1; i <= parts.length - 2; i++) {
            StringBuilder domainBuilder = new StringBuilder();
            for (int j = i; j < parts.length; j++) {
                if (j > i) domainBuilder.append(".");
                domainBuilder.append(parts[j]);
            }
            String candidateDomain = domainBuilder.toString();

            // Check if this domain exists in cache
            for (Map.Entry<HostnameDomainKey, String> entry : hostnameToObjectIdCache.entrySet()) {
                if (candidateDomain.equals(entry.getKey().getDomainName())) {
                    String objectIdStr = entry.getValue();
                    IconData iconData = objectIdToIconDataCache.get(objectIdStr);
                    if (iconData != null) {
                        // Found domain match - add new cache entry for this hostname
                        HostnameDomainKey newKey = new HostnameDomainKey(hostname, candidateDomain);
                        hostnameToObjectIdCache.put(newKey, objectIdStr);
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
     * @param domainName The domain name for the compound key
     * @param existingObjectId The ObjectId of existing icon document
     */
    public void addHostnameMapping(String newHostname, String domainName, ObjectId existingObjectId) {
        if (newHostname == null || domainName == null || existingObjectId == null) {
            return;
        }

        String objectIdStr = existingObjectId.toString();

        // Only add hostname mapping if we have the icon data already cached
        if (objectIdToIconDataCache.containsKey(objectIdStr)) {
            HostnameDomainKey key = new HostnameDomainKey(newHostname, domainName);
            hostnameToObjectIdCache.put(key, objectIdStr);
            logger.infoAndAddToDb("Added hostname mapping to cache: " + key + " -> " + objectIdStr);
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

    /**
     * Refresh cache from database with graceful degradation.
     * Follows the same pattern as AccountConfigurationCache.
     * If refresh fails, keeps the old cache (graceful degradation).
     */
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

            // Build new cache structures
            Map<HostnameDomainKey, String> newHostnameCache = new ConcurrentHashMap<>();
            Map<String, IconData> newObjectIdCache = new ConcurrentHashMap<>();

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
                    IconData iconData = new IconData(
                        icon.getDomainName(),
                        icon.getImageData(),
                        "image/png", // Always PNG as specified
                        icon.getUpdatedAt() > 0 ? icon.getUpdatedAt() : icon.getCreatedAt()
                    );

                    // Add to objectId cache
                    newObjectIdCache.put(objectIdStr, iconData);
                    validIconsCount++;

                    // Add hostname mappings using compound keys
                    if (icon.getMatchingHostnames() != null && !icon.getMatchingHostnames().isEmpty()) {
                        for (String hostname : icon.getMatchingHostnames()) {
                            if (hostname != null && !hostname.trim().isEmpty()) {
                                HostnameDomainKey key = new HostnameDomainKey(
                                    hostname, 
                                    icon.getDomainName()
                                );
                                newHostnameCache.put(key, objectIdStr);
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

            // Atomically update cache (same pattern as AccountConfigurationCache)
            this.hostnameToObjectIdCache = newHostnameCache;
            this.objectIdToIconDataCache = newObjectIdCache;
            this.lastRefreshTime = System.currentTimeMillis();

            logger.infoAndAddToDb(CACHE_NAME + " refreshed successfully. Valid icons: " + validIconsCount + 
                                  ", Invalid icons: " + invalidIconsCount + 
                                  ", Hostname mappings: " + newHostnameCache.size() + 
                                  ", Icon data entries: " + newObjectIdCache.size());

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
            this.hostnameToObjectIdCache = new ConcurrentHashMap<>();
            this.objectIdToIconDataCache = new ConcurrentHashMap<>();
            this.lastRefreshTime = 0;
            logger.infoAndAddToDb(CACHE_NAME + " cleared");
        }
    }

    /**
     * Force refresh the cache with fresh data from database.
     * If refresh fails, keeps the old cache (graceful degradation).
     * Similar to AccountConfigurationCache behavior.
     */
    public void forceRefresh() {
        synchronized (lock) {
            refreshCache();
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
     * @return Map of HostnameDomainKey to ObjectId string
     */
    public Map<HostnameDomainKey, String> getHostnameToObjectIdCache() {
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