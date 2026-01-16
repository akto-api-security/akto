package com.akto.util;

import com.akto.cache.IconCache;
import com.akto.dao.ApiCollectionIconsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionIcon;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

import static com.akto.util.Constants.FAVICON_SOURCE_URL;

/**
 * Utility class for handling API collection icon operations
 */
public class IconUtils {
    
    private static final LoggerMaker loggerMaker = new LoggerMaker(IconUtils.class, LogDb.DASHBOARD);
    private static final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    /**
     * Process icons for collections in the background
     * Extracts hostnames and triggers icon fetching if not in cache
     */
    public static void processIconsForCollections(List<ApiCollection> apiCollections) {
        if (apiCollections == null || apiCollections.isEmpty()) {
            return;
        }

        IconCache iconCache = IconCache.getInstance();

        // Process each hostname with cache-first approach
        for (ApiCollection collection : apiCollections) {
            // First check cache (includes domain-stripping logic)
            String hostname = collection.getHostName();
            IconCache.IconData cachedIcon = iconCache.getIconData(hostname);
            if (cachedIcon != null) {
                continue;
            }

            // Not in cache, submit for async fetching from Google Favicon API
            executorService.submit(() -> {
                try {
                    fetchAndStoreIconWithCascade(hostname);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error processing icon for hostname: " + hostname, LogDb.DASHBOARD);
                }
            });
        }
    }


    /**
     * Fetch icon from Google Favicon API with cascading domain fallback and store in database
     * Tries full hostname first, then progressively removes subdomains until success or TLD reached
     * Optimized with pre-built candidate domains to avoid repeated string operations
     */
    public static void fetchAndStoreIconWithCascade(String fullHostname) {
        if (fullHostname == null || fullHostname.trim().isEmpty()) {
            return;
        }
        
        String cleanHostName = fullHostname.trim().toLowerCase();
        String[] parts = cleanHostName.split("\\.");
        
        if (parts.length < 2) {
            // Single part domain, try as-is
            tryFetchAndStoreIcon(cleanHostName, cleanHostName);
            return;
        }
        
        // Pre-build all candidate domains to avoid repeated string operations
        String[] candidateDomains = buildCandidateDomains(parts);
        
        // Try each candidate domain in order (full hostname -> main domain)
        for (String candidateDomain : candidateDomains) {
            if (tryFetchAndStoreIcon(candidateDomain, fullHostname)) {
                return; // Success - stop trying other levels
            }
        }
        
        loggerMaker.infoAndAddToDb("Failed to fetch icon for hostname: " + fullHostname + " after trying all domain levels", LogDb.DASHBOARD);
    }
    
    /**
     * Pre-build all candidate domains from hostname parts to avoid repeated string operations
     * Example: ["api", "staging", "example", "com"] -> ["api.staging.example.com", "staging.example.com", "example.com"]
     * Optimized for performance with efficient string joining
     */
    private static String[] buildCandidateDomains(String[] parts) {
        int numCandidates = parts.length - 1; // Don't include just TLD
        String[] candidates = new String[numCandidates];
        
        // Use efficient string joining instead of nested loops
        for (int level = 0; level < numCandidates; level++) {
            // Calculate the required capacity to avoid StringBuilder resizing
            int capacity = 0;
            for (int j = level; j < parts.length; j++) {
                capacity += parts[j].length();
                if (j > level) capacity++; // for the dot
            }
            
            StringBuilder domainBuilder = new StringBuilder(capacity);
            for (int j = level; j < parts.length; j++) {
                if (j > level) domainBuilder.append(".");
                domainBuilder.append(parts[j]);
            }
            candidates[level] = domainBuilder.toString();
        }
        
        return candidates;
    }

    /**
     * Try to fetch icon from Google API for a specific domain level
     * Returns true if successful, false if should try next level
     */
    private static boolean tryFetchAndStoreIcon(String domain, String originalHostname) {
        try {
            String googleApiUrl = FAVICON_SOURCE_URL + domain + "&size=64";
            
            URL url = new URL(googleApiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                byte[] imageBytes = buffer.toByteArray();
                if (imageBytes.length > 0) {
                    String base64Data = Base64.getEncoder().encodeToString(imageBytes);
                    
                    // Check if domain already exists in database
                    ApiCollectionIcon existingIcon = ApiCollectionIconsDao.instance.findOne(
                        Filters.eq(ApiCollectionIcon.DOMAIN_NAME, domain)
                    );
                    
                    if (existingIcon != null) {
                        // Update existing entry - add hostname to matchingHostnames if not already present
                        if (existingIcon.getMatchingHostnames() == null || !existingIcon.getMatchingHostnames().contains(originalHostname)) {
                            ApiCollectionIconsDao.instance.updateOne(
                                Filters.eq("_id", existingIcon.getId()),
                                Updates.combine(
                                    Updates.addToSet(ApiCollectionIcon.MATCHING_HOSTNAMES, originalHostname),
                                    Updates.set(ApiCollectionIcon.UPDATED_AT, Context.now())
                                )
                            );
                            
                            // Update cache with new hostname mapping to avoid waiting for 30-minute refresh
                            IconCache.getInstance().addHostnameMapping(originalHostname, domain, existingIcon.getId());
                            
                            loggerMaker.infoAndAddToDb("Updated existing domain entry: " + domain + " with hostname: " + originalHostname, LogDb.DASHBOARD);
                        } else {
                            loggerMaker.infoAndAddToDb("Domain: " + domain + " already contains hostname: " + originalHostname, LogDb.DASHBOARD);
                        }
                    } else {
                        // Create new entry
                        List<String> matchingHostnames = new ArrayList<>();
                        matchingHostnames.add(originalHostname);
                        
                        ApiCollectionIcon icon = new ApiCollectionIcon(domain, matchingHostnames, base64Data);
                        ApiCollectionIconsDao.instance.insertOne(icon);
                        
                        // For new entries, update cache immediately by reloading to get ObjectId
                        // This ensures cache consistency without waiting for 30-minute refresh
                        ApiCollectionIcon newlyCreatedIcon = ApiCollectionIconsDao.instance.findOne(
                            Filters.eq(ApiCollectionIcon.DOMAIN_NAME, domain)
                        );
                        
                        if (newlyCreatedIcon != null && newlyCreatedIcon.getId() != null) {
                            IconCache.getInstance().addHostnameMapping(originalHostname, domain, newlyCreatedIcon.getId());
                            loggerMaker.infoAndAddToDb("Successfully fetched and cached new icon for domain: " + domain + " (from hostname: " + originalHostname + ") and updated cache", LogDb.DASHBOARD);
                        } else {
                            loggerMaker.infoAndAddToDb("Successfully fetched and cached new icon for domain: " + domain + " (from hostname: " + originalHostname + ") but failed to update cache - will update on next refresh", LogDb.DASHBOARD);
                        }
                    }
                    
                    return true;
                }
            }
            
            // Failed at this level - try next level
            loggerMaker.infoAndAddToDb("No valid icon at domain level: " + domain + ", trying next level", LogDb.DASHBOARD);
            return false;
                
        } catch (IOException e) {
            loggerMaker.infoAndAddToDb("IO error for domain: " + domain + ", trying next level", LogDb.DASHBOARD);
            return false;
        } catch (Exception e) {
            loggerMaker.infoAndAddToDb("Error for domain: " + domain + ", trying next level", LogDb.DASHBOARD);
            return false;
        }
    }
}