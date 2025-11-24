package com.akto.threat.detection.cache;

import com.akto.data_actor.DataActor;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.APICatalog;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.RuntimeUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thread-safe singleton cache for account configuration.
 * Automatically refreshes data every 15 minutes when accessed.
 */
public class AccountConfigurationCache {
    private static volatile AccountConfigurationCache INSTANCE;
    private static final LoggerMaker logger = new LoggerMaker(AccountConfigurationCache.class, LogDb.THREAT_DETECTION);

    private volatile AccountConfig cachedConfig;
    private volatile long lastRefreshTime = 0;
    private final long REFRESH_INTERVAL_MS = 15 * 60 * 1000; // 15 minutes

    private final Object lock = new Object();

    private AccountConfigurationCache() {}

    /**
     * Get singleton instance of the cache
     */
    public static AccountConfigurationCache getInstance() {
        if (INSTANCE == null) {
            synchronized (AccountConfigurationCache.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AccountConfigurationCache();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Gets the cached config, refreshes if expired.
     * Thread-safe with double-check locking.
     *
     * @param dataActor DataActor instance to fetch data if refresh is needed
     * @return AccountConfig containing account settings and API collections
     */
    public AccountConfig getConfig(DataActor dataActor) {
        long currentTime = System.currentTimeMillis();

        // Check if cache is expired
        if (cachedConfig == null || (currentTime - lastRefreshTime) >= REFRESH_INTERVAL_MS) {
            synchronized (lock) {
                // Double-check after acquiring lock to prevent multiple refreshes
                if (cachedConfig == null || (currentTime - lastRefreshTime) >= REFRESH_INTERVAL_MS) {
                    refreshConfig(dataActor);
                }
            }
        }

        return cachedConfig;
    }

    /**
     * Force refresh the cache with fresh data from database.
     * If refresh fails, keeps the old cache (graceful degradation).
     */
    private void refreshConfig(DataActor dataActor) {
        try {
            logger.infoAndAddToDb("Refreshing account configuration cache");
            AccountSettings accountSettings = dataActor.fetchAccountSettings();
            List <ApiCollection> apiCollections = dataActor.fetchAllApiCollections();
            // This will fetch paginated apiInfos with _id, rateLimits fields.
            List<ApiInfo> apiInfos = dataActor.fetchApiRateLimits(null);

            // Build API info metadata structures - always non-null
            Map<Integer, List<URLTemplate>> apiCollectionUrlTemplates = new HashMap<>();
            Map<String, Set<com.akto.dto.type.URLMethods.Method>> apiInfoUrlToMethods = new HashMap<>();

            // Process API infos only if available
            if (apiInfos != null && !apiInfos.isEmpty()) {
                for (ApiInfo apiInfo : apiInfos) {
                    String url = apiInfo.getId().getUrl();
                    int apiCollectionId = apiInfo.getId().getApiCollectionId();
                    com.akto.dto.type.URLMethods.Method method = apiInfo.getId().getMethod();

                    // Build URL to methods map with key format: "apiCollectionId:url"
                    String urlKey = apiCollectionId + ":" + url;
                    apiInfoUrlToMethods.computeIfAbsent(urlKey, k -> new HashSet<>()).add(method);

                    // Build URL templates for parameterized URLs
                    if (APICatalog.isTemplateUrl(url)) {
                        URLTemplate urlTemplate = RuntimeUtil.createUrlTemplate(url, method);

                        if (!apiCollectionUrlTemplates.containsKey(apiCollectionId)) {
                            apiCollectionUrlTemplates.put(apiCollectionId, new ArrayList<>());
                        }

                        apiCollectionUrlTemplates.get(apiCollectionId).add(urlTemplate);
                    }
                }
            }
            // Note: Maps remain empty (not null) if apiInfos is null/empty

            this.cachedConfig = new AccountConfig(
                accountSettings.getId(),
                accountSettings.isRedactPayload(),
                apiCollections,
                apiInfos,
                apiCollectionUrlTemplates,
                apiInfoUrlToMethods
            );
            this.lastRefreshTime = System.currentTimeMillis();
            logger.infoAndAddToDb("Account configuration cache refreshed successfully. AccountId: " +
                                  accountSettings.getId() + ", API Collections: " + apiCollections.size() +
                                  ", API Infos: " + (apiInfos != null ? apiInfos.size() : 0));
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error refreshing account configuration cache. Keeping old cache if available.");
        }
    }

    /**
     * Clear the cache (useful for testing or manual refresh).
     */
    public void clear() {
        synchronized (lock) {
            this.cachedConfig = null;
            this.lastRefreshTime = 0;
            logger.infoAndAddToDb("Account configuration cache cleared");
        }
    }

    /**
     * For testing only - directly set cache config without DataActor.
     * This allows tests to inject test data without mocking DataActor.
     */
    public void setConfigForTesting(AccountConfig config) {
        synchronized (lock) {
            this.cachedConfig = config;
            this.lastRefreshTime = System.currentTimeMillis();
            logger.infoAndAddToDb("Account configuration cache set for testing");
        }
    }

    /**
     * Get time remaining until next auto-refresh (in seconds).
     */
    public long getTimeUntilRefresh() {
        if (cachedConfig == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastRefreshTime;
        long remaining = REFRESH_INTERVAL_MS - elapsed;
        return remaining > 0 ? remaining / 1000 : 0;
    }
}
