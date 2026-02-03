package com.akto.utils.crons;

import com.akto.action.InviteUserAction;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.billing.Organization;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Pair;
import com.akto.util.OrganizationInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Projections;

import java.util.ArrayList;
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
    
    // Cache for admin email domain -> OrganizationInfo mapping
    public static final Map<String, OrganizationInfo> domainToOrgInfoCache = Collections.synchronizedMap(new HashMap<>());
    
    // Cache refresh interval: 10 minutes
    private static final int CACHE_REFRESH_INTERVAL_MINUTES = 60;
    
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
            Projections.include(Organization.ID, Organization.ADMIN_EMAIL, Organization.PLAN_TYPE)
        );
        
        // Group organizations by domain to handle priority logic
        Map<String, List<Organization>> domainToOrganizations = new HashMap<>();
        
        for (Organization org : organizations) {
            try {
                String orgId = org.getId();
                String adminEmail = org.getAdminEmail();

                if (orgId != null && adminEmail != null && adminEmail.contains("@")) {
                    String adminEmailDomain = adminEmail.split("@")[1].toLowerCase();
                    domainToOrganizations.computeIfAbsent(adminEmailDomain, k -> new ArrayList<>()).add(org);
                }
            } catch (Exception e) {
                logger.errorAndAddToDb(e, "Error while processing organization: " + 
                    (org.getId() != null ? org.getId() : "null_id"));
            }
        }
        
        // For each domain, prioritize organizations with non-empty, non-null planType
        for (Map.Entry<String, List<Organization>> entry : domainToOrganizations.entrySet()) {
            try {
                String domain = entry.getKey();
                List<Organization> orgsForDomain = entry.getValue();

                Organization selectedOrg = null;

                // First, try to find an organization with non-empty, non-null planType
                for (Organization org : orgsForDomain) {
                    String planType = org.getplanType();
                    if (planType != null && !planType.isEmpty() && !"planType".equals(planType)) {
                        selectedOrg = org;
                        break;
                    }
                }

                // If no organization with valid planType found, use the first one
                if (selectedOrg == null && !orgsForDomain.isEmpty()) {
                    selectedOrg = orgsForDomain.get(0);
                }

                if (selectedOrg != null) {
                    String orgId = selectedOrg.getId();
                    String adminEmail = selectedOrg.getAdminEmail();
                    String planType = selectedOrg.getplanType();

                    OrganizationInfo orgInfo = new OrganizationInfo(orgId, adminEmail, planType);
                    domainToOrgInfoCache.put(domain, orgInfo);

                    logger.debug("Cached organization: " + orgId + " with domain: " + domain +
                            ", email: " + adminEmail + ", planType: " + planType +
                            " (selected from " + orgsForDomain.size() + " organizations)");
                }
            } catch (Exception e){
                logger.errorAndAddToDb(e, "Error while processing organization" );
            }
        }
    }
    
    /**
     * Get organization info (orgId, adminEmail, planType) by admin email domain for signup matching
     * Handles bidirectional domain mapping using InviteUserAction.commonOrganisationsMap
     */
    public static OrganizationInfo getOrganizationInfoByDomain(String emailDomain) {
        if (emailDomain == null) {
            return null;
        }
        
        // First, try direct lookup
        OrganizationInfo orgInfo = domainToOrgInfoCache.get(emailDomain);
        if (orgInfo != null) {
            return orgInfo;
        }
        
        // If not found, check bidirectional mapping
        // Check if this domain has a canonical mapping (input domain -> canonical domain)
        String canonicalDomain = InviteUserAction.commonOrganisationsMap.get(emailDomain);
        if (canonicalDomain != null) {
            canonicalDomain = canonicalDomain.trim().toLowerCase();
            orgInfo = domainToOrgInfoCache.get(canonicalDomain);
            if (orgInfo != null) {
                logger.debug("Found organization for domain: " + emailDomain + " via canonical mapping to: " + canonicalDomain);
                return orgInfo;
            }
        }
        return null;
    }
    
    /**
     * Legacy method for backward compatibility - returns only orgId and adminEmail
     * @deprecated Use getOrganizationInfoByDomain instead
     */
    @Deprecated
    public static Pair<String, String> getOrganizationInfoByDomainLegacy(String emailDomain) {
        OrganizationInfo orgInfo = getOrganizationInfoByDomain(emailDomain);
        if (orgInfo != null) {
            return new Pair<>(orgInfo.getOrganizationId(), orgInfo.getAdminEmail());
        }
        return null;
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