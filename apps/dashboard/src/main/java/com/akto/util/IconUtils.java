package com.akto.util;

// IconCache removed - no longer needed
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

        Set<String> processedRootDomains = new HashSet<>();

        // Process each hostname for background icon fetching
        for (ApiCollection collection : apiCollections) {
            String hostname = collection.getHostName();
            if (hostname == null || hostname.trim().isEmpty()) continue;

            // Extract root domain (last 2 parts) to deduplicate
            String[] parts = hostname.trim().toLowerCase().split("\\.");
            String rootDomain = parts.length >= 2 ? parts[parts.length - 2] + "." + parts[parts.length - 1] : hostname;

            if (processedRootDomains.contains(rootDomain)) continue;

            processedRootDomains.add(rootDomain);
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

        // Try from full hostname down to main domain (last 2 parts)
        for (int i = 0; i <= parts.length - 2; i++) {
            StringBuilder domainBuilder = new StringBuilder();
            for (int j = i; j < parts.length; j++) {
                if (j > i) domainBuilder.append(".");
                domainBuilder.append(parts[j]);
            }
            String candidateDomain = domainBuilder.toString();

            // Try to fetch icon for this domain level
            if (tryFetchAndStoreIcon(candidateDomain, fullHostname)) {
                return; // Success - stop trying other levels
            }
        }
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
                        } else {
                           //Domain already contains hostname
                        }
                    } else {
                        // Create new entry
                        List<String> matchingHostnames = new ArrayList<>();
                        matchingHostnames.add(originalHostname);
                        
                        ApiCollectionIcon icon = new ApiCollectionIcon(domain, matchingHostnames, base64Data);
                        ApiCollectionIconsDao.instance.insertOne(icon);
                    }
                    
                    return true;
                }
            }
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