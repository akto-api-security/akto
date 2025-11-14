package com.akto.threat.detection.cache;

import com.akto.data_actor.DataActor;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.List;
import java.util.Map;

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
            this.cachedConfig = new AccountConfig(
                accountSettings.getId(),
                accountSettings.isRedactPayload(),
                apiCollections
            );
            this.lastRefreshTime = System.currentTimeMillis();
            logger.infoAndAddToDb("Account configuration cache refreshed successfully. AccountId: " +
                                  accountSettings.getId() + ", API Collections: " + apiCollections.size());
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
