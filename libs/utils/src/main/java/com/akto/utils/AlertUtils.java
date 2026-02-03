package com.akto.utils;

import com.akto.log.LoggerMaker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Utility class for alert management and validation
 * Provides centralized logic for plan type validation and alert caching
 */
public class AlertUtils {
    
    private static final LoggerMaker logger = new LoggerMaker(AlertUtils.class, LoggerMaker.LogDb.DASHBOARD);
    
    // Valid plan types - can be extended in future
    private static final List<String> VALID_PLAN_TYPES = Arrays.asList("enterprise", "professional", "trial");
    
    // Cache to prevent duplicate alerts - stores "userEmail:orgId" as keys
    private static final ConcurrentMap<String, Boolean> alertCache = new ConcurrentHashMap<>();
    
    /**
     * Check if we should send a Slack alert for this user/org combination
     * Returns true if alert was never sent for this combination
     * 
     * @param userEmail the user's email address
     * @param organizationId the organization ID
     * @return true if alert should be sent, false if already sent
     */
    public static boolean shouldSendAlert(String userEmail, String organizationId) {
        if (userEmail == null || organizationId == null) {
            return false;
        }
        
        String alertKey = userEmail + ":" + organizationId;
        
        // If combination doesn't exist in cache, send alert and add to cache
        if (!alertCache.containsKey(alertKey)) {
            alertCache.put(alertKey, true);
            
            // Periodic cleanup - remove entries periodically for memory management
            if (alertCache.size() % 100 == 0) { // Clean up every 100 entries
                performCacheCleanup();
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Periodic cleanup of cache for memory management
     * In real implementation, this could be based on last access time or other criteria
     */
    private static void performCacheCleanup() {
        // For now, if cache gets too large (>1000), clear oldest 50% entries
        // This is a simple implementation - in production, consider using a proper cache with TTL
        if (alertCache.size() > 1000) {
            List<String> keysToRemove = new ArrayList<>();
            int removeCount = alertCache.size() / 2;
            int count = 0;
            
            for (String key : alertCache.keySet()) {
                if (count++ >= removeCount) break;
                keysToRemove.add(key);
            }
            
            for (String key : keysToRemove) {
                alertCache.remove(key);
            }
            
            logger.infoAndAddToDb("Performed alert cache cleanup, removed " + keysToRemove.size() + " entries");
        }
    }
    
    /**
     * Check if the given plan type is valid
     * 
     * @param planType the plan type to validate
     * @return true if plan type is valid, false otherwise
     */
    public static boolean isValidPlanType(String planType) {
        if (planType == null) {
            return false;
        }
        
        for (String validType : VALID_PLAN_TYPES) {
            if (validType.equalsIgnoreCase(planType)) {
                return true;
            }
        }
        return false;
    }
}