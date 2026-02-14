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
     * Also detects redirects (e.g., short.example → example.com) and uses final destination
     */
    public static void fetchAndStoreIconWithCascade(String fullHostname) {
        if (fullHostname == null || fullHostname.trim().isEmpty()) {
            return;
        }

        String cleanHostName = fullHostname.trim().toLowerCase();

        // Detect redirects: try to follow redirects and extract final destination domain
        String finalDomain = detectRedirectDomain(cleanHostName);
        if (finalDomain != null && !finalDomain.equals(cleanHostName)) {
            loggerMaker.infoAndAddToDb("Detected redirect: " + cleanHostName + " → " + finalDomain, LogDb.DASHBOARD);
            cleanHostName = finalDomain;
        }

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

        loggerMaker.infoAndAddToDb("Failed to fetch icon for hostname: " + fullHostname + " after trying all domain levels", LogDb.DASHBOARD);
    }

    /**
     * Detect if domain redirects and extract final destination
     * Returns the final domain after following redirects, or null if detection fails
     */
    private static String detectRedirectDomain(String hostname) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://" + hostname);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);

            connection.getResponseCode(); // Trigger connection to follow redirects

            // Check if redirect occurred by comparing request URL with final URL
            String finalUrl = connection.getURL().toString();
            URL parsedFinalUrl = new URL(finalUrl);
            String finalHost = parsedFinalUrl.getHost().replaceFirst("^www\\.", "");

            // Extract root domain (last 2 parts) from final destination
            String[] parts = finalHost.split("\\.");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "." + parts[parts.length - 1];
            }
            return finalHost;

        } catch (Exception e) {
            // Redirect detection failed - not critical, return null to use original
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
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
                        
                        loggerMaker.infoAndAddToDb("Successfully fetched and cached new icon for domain: " + domain + " (from hostname: " + originalHostname + ")", LogDb.DASHBOARD);
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