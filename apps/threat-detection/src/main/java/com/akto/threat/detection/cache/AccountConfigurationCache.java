package com.akto.threat.detection.cache;

import com.akto.ProtoMessageUtils;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ThreatConfiguration;
import com.akto.runtime.RuntimeUtil;
import com.akto.testing.ApiExecutor;
import com.akto.threat.detection.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
     * @return AccountConfig containing account settings and API collections, or default config with redact=false if cache fails
     */
    public AccountConfig getConfig(DataActor dataActor) {
        long currentTime = System.currentTimeMillis();
        if(Context.accountId.get() == 1758787662){
            return getDefaultConfig();
        }

        // Check if cache is expired
        if (cachedConfig == null || (currentTime - lastRefreshTime) >= REFRESH_INTERVAL_MS) {
            synchronized (lock) {
                // Double-check after acquiring lock to prevent multiple refreshes
                if (cachedConfig == null || (currentTime - lastRefreshTime) >= REFRESH_INTERVAL_MS) {
                    refreshConfig(dataActor);
                }
            }
        }

        if (cachedConfig == null) {
            logger.errorAndAddToDb("getConfig returning null - cache refresh failed, returning default config with redact=false");
            return getDefaultConfig();
        }

        return cachedConfig;
    }

    /**
     * Returns a default AccountConfig with redaction disabled.
     * Used as fallback when cache refresh fails.
     *
     * @return Default AccountConfig with accountId=0, isRedacted=false, and empty collections
     */
    private AccountConfig getDefaultConfig() {
        return new AccountConfig(
            0,                          // accountId
            false,                      // isRedacted = false
            false,                      // isHyperscanEnabled = false (default to filter yaml)
            new ArrayList<>(),          // empty apiCollections
            new ArrayList<>(),          // empty apiInfos
            new HashMap<>(),            // empty apiCollectionUrlTemplates
            new HashMap<>(),            // empty apiInfoUrlToMethods
            null                        // no threatConfiguration
        );
    }

    /**
     * Force refresh the cache with fresh data from database.
     * If refresh fails, keeps the old cache (graceful degradation).
     */
    private void refreshConfig(DataActor dataActor) {
        try {
            logger.infoAndAddToDb("Refreshing account configuration cache");
            AccountSettings accountSettings = dataActor.fetchAccountSettings();

            logger.infoAndAddToDb("Fetched accountSettings in configuration cache. accountSettings is null: " + (accountSettings == null));

            if (accountSettings == null) {
                logger.errorAndAddToDb("fetchAccountSettings returned null. Cannot refresh cache");
                return;
            }

            logger.infoAndAddToDb("AccountSettings ID: " + accountSettings.getId());

            // Check Stigg feature flag for hyperscan mode
            boolean isHyperscanEnabled = false;
            try {
                Organization organization = dataActor.fetchOrganization(accountSettings.getId());
                if (organization != null && organization.getFeatureWiseAllowed() != null) {
                    FeatureAccess hyperscanAccess = organization.getFeatureWiseAllowed()
                            .getOrDefault("THREAT_DETECTION_HYPERSCAN", FeatureAccess.noAccess);
                    isHyperscanEnabled = hyperscanAccess.getIsGranted();
                }
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error fetching organization for hyperscan feature flag: " + e.getMessage());
            }
            logger.infoAndAddToDb("Hyperscan feature flag: " + isHyperscanEnabled);

            List <ApiCollection> apiCollections = new ArrayList<>();
            if (accountSettings.getId() != 1758179941) {
                apiCollections = dataActor.fetchAllApiCollections();
            }
            // This will fetch paginated apiInfos with _id, rateLimits fields.
            List<ApiInfo> apiInfos = new ArrayList<>();

            if (accountSettings.getId() == 1763355072) {
                apiInfos = dataActor.fetchApiRateLimits(null);
            }

            // Build API info metadata structures - always non-null
            Map<Integer, List<URLTemplate>> apiCollectionUrlTemplates = new HashMap<>();
            Map<String, Set<com.akto.dto.type.URLMethods.Method>> apiInfoUrlToMethods = new HashMap<>();

            // Process API infos only if available
            RuntimeUtil.fillURLTemplatesMap(apiInfos, apiInfoUrlToMethods, apiCollectionUrlTemplates, null);
            // Note: Maps remain empty (not null) if apiInfos is null/empty

            ThreatConfiguration threatConfiguration = fetchThreatConfiguration();

            this.cachedConfig = new AccountConfig(
                accountSettings.getId(),
                accountSettings.isRedactPayload(),
                isHyperscanEnabled,
                apiCollections,
                apiInfos,
                apiCollectionUrlTemplates,
                apiInfoUrlToMethods,
                threatConfiguration
            );
            this.lastRefreshTime = System.currentTimeMillis();
            logger.infoAndAddToDb("Account configuration cache refreshed successfully. AccountId: " +
                                  accountSettings.getId() + ", API Collections: " + apiCollections.size() +
                                  ", API Infos: " + (apiInfos != null ? apiInfos.size() : 0));

        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error refreshing account configuration cache. Keeping old cache if available.");
            e.printStackTrace();
        }
    }

    private ThreatConfiguration fetchThreatConfiguration() {
        Map<String, List<String>> headers = Utils.buildHeaders();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        OriginalHttpRequest request = new OriginalHttpRequest(
                Utils.getThreatProtectionBackendUrl() + "/api/dashboard/get_threat_configuration", "", "GET", null,
                headers, "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, null);
            String responsePayload = response.getBody();
            if (response.getStatusCode() != 200 || responsePayload == null) {
                logger.errorAndAddToDb("non 2xx response in get_threat_configuration");
                return null;
            }
            return ProtoMessageUtils.<ThreatConfiguration>toProtoMessage(ThreatConfiguration.class, responsePayload)
                    .orElse(null);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error fetching threat configuration");
            return null;
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
