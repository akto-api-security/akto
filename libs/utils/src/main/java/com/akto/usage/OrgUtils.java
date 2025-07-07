package com.akto.usage;

import com.akto.dao.AccountsDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.Account;
import com.akto.dto.billing.CachedOrganization;
import com.akto.dto.billing.Organization;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OrgUtils {

    public static List<Account> getSiblingAccounts(int accountId) {

        Organization organization = OrganizationsDao.instance.findOne(
                Filters.and(
                        Filters.eq(Organization.ACCOUNTS, accountId)
                )
        );

        if(organization == null) return new ArrayList<>();

        return AccountsDao.instance.findAll(
                Filters.and(
                        Filters.in(Constants.ID, organization.getAccounts())
                ));
    }

    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static final int CACHE_DURATION = 60 * 5; // 5 minutes
    private static final Map<Integer, CachedOrganization> orgCache = new ConcurrentHashMap<>();

    public static Organization getOrganizationCached(int accountId) {
        CachedOrganization cached = orgCache.get(accountId);
        int now = Context.now();
        if (cached != null) {
                if (now - cached.getCachedAt() < CACHE_DURATION) {
                        return cached.getOrganization();
                }
        }

        Organization organization = dataActor.fetchOrganization(accountId);

        if (organization == null) {
                if(cached!= null) {
                        return cached.getOrganization();
                }
                return null;
        }

        orgCache.put(accountId, new CachedOrganization(organization, now));
        return organization;
    }
}
