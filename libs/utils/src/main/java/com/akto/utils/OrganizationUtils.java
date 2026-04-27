package com.akto.utils;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.billing.Organization;
import com.mongodb.client.model.Filters;

public class OrganizationUtils {

    private static final Logger logger = LoggerFactory.getLogger(OrganizationUtils.class);
    
    private static final ConcurrentHashMap<Integer, Organization> orgCache = new ConcurrentHashMap<>();

    /**
     * Returns the Organization for the given accountId.
     * Serves from cache if already fetched; otherwise queries and caches the result.
     * Returns null if not found or on error.
     */
    public static Organization fetchOrganization(int accountId) {
        Organization cached = orgCache.get(accountId);
        if (cached != null) {
            return cached;
        }
        try {
            Organization org = OrganizationsDao.instance.findOne(Filters.in(Organization.ACCOUNTS, accountId));
            if (org != null) {
                orgCache.put(accountId, org);
            }
            return org;
        } catch (Exception e) {
            logger.error("Failed to fetch organization for accountId=" + accountId);
            return null;
        }
    }
}
