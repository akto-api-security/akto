package com.akto.utils.crons;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OrganizationCache {
    private static final LoggerMaker logger = new LoggerMaker(OrganizationCache.class, LogDb.DASHBOARD);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    // Cache for admin email domain -> Pair(orgId, adminEmail) mapping
    private static final Map<String, Pair<String, String>> domainToOrgInfoCache = Collections.synchronizedMap(new HashMap<>());
    
    // Cache refresh interval: 10 minutes
    private static final int CACHE_REFRESH_INTERVAL_MINUTES = 10;
    
    public void setUpOrganizationCacheScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    logger.debug("Starting organization cache update");
                    refreshOrganizationCaches();
                    logger.debug("Completed organization cache update. Cached " + 
                        domainToOrgInfoCache.size() + " organization mappings");
                } catch (Exception e) {
                    logger.errorAndAddToDb(e, "Error in organization cache update");
                }
            }
        }, 0, CACHE_REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }
    
    private void refreshOrganizationCaches() {
        // Clear existing cache
        domainToOrgInfoCache.clear();

        List<Organization> organizations = OrganizationsDao.instance.findAll(
            new BasicDBObject(), 
            Projections.include(Organization.ID, Organization.ADMIN_EMAIL)
        );
        
        for (Organization org : organizations) {
            try {
                String orgId = org.getId();
                String adminEmail = org.getAdminEmail();
                
                if (orgId != null && adminEmail != null && adminEmail.contains("@")) {
                    // Extract domain and cache domain -> Pair(orgId, adminEmail)
                    String adminEmailDomain = adminEmail.split("@")[1].toLowerCase();
                    Pair<String, String> orgInfo = new Pair<>(orgId, adminEmail);
                    domainToOrgInfoCache.put(adminEmailDomain, orgInfo);
                    
                    logger.debug("Cached organization: " + orgId + " with domain: " + adminEmailDomain + " and email: " + adminEmail);
                }
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error while caching organization: " + 
                    (org.getId() != null ? org.getId() : "null_id"));
            }
        }
    }
    
    /**
     * Get organization info (orgId, adminEmail) by admin email domain for signup matching
     */
    public static Pair<String, String> getOrganizationInfoByDomain(String emailDomain) {
        if (emailDomain == null) {
            return null;
        }
        return domainToOrgInfoCache.get(emailDomain.toLowerCase());
    }
    
    /**
     * Check if cache is populated
     */
    public static boolean isCachePopulated() {
        return !domainToOrgInfoCache.isEmpty();
    }
    
    /**
     * Get cache size for monitoring
     */
    public static int getCacheSize() {
        return domainToOrgInfoCache.size();
    }
}