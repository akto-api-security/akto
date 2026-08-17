package com.akto.dao.billing;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.bson.conversions.Bson;

import com.akto.dao.BillingContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.util.UsageUtils;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.util.enums.GlobalEnums.DashboardCategory;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class OrganizationsDao extends BillingContextDao<Organization>{

    public static final OrganizationsDao instance = new OrganizationsDao();

    private static final DashboardCategory[] CATEGORY_PRECEDENCE = {
            DashboardCategory.API_SECURITY,
            DashboardCategory.SECURITY_TYPE_AGENTIC,
            DashboardCategory.ENDPOINT_SECURITY,
            DashboardCategory.AKTO_DAST
    };

    private static boolean isCategoryGranted(HashMap<String, FeatureAccess> featureWiseAllowed, DashboardCategory category) {
        FeatureAccess access = featureWiseAllowed.get(category.name());
        return access != null && access.getIsGranted();
    }

    public static DashboardCategory getDefaultDashboardCategoryEnum(int accountId) {
        try {
            Organization organization = instance.findOne(Filters.in(Organization.ACCOUNTS, accountId));
            if (organization != null && organization.getFeatureWiseAllowed() != null
                    && !organization.getFeatureWiseAllowed().isEmpty()) {
                HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
                for (DashboardCategory category : CATEGORY_PRECEDENCE) {
                    if (isCategoryGranted(featureWiseAllowed, category)) {
                        return category;
                    }
                }
            }
        } catch (Exception e) {
        }

        return DashboardCategory.API_SECURITY;
    }

    public static String getDefaultDashboardCategory(int accountId) {
        return getDefaultDashboardCategoryEnum(accountId).getDashboardCategory();
    }

    private static String scopeOf(DashboardCategory category) {
        switch (category) {
            case SECURITY_TYPE_AGENTIC:
                return CONTEXT_SOURCE.AGENTIC.name();
            case ENDPOINT_SECURITY:
                return CONTEXT_SOURCE.ENDPOINT.name();
            case AKTO_DAST:
                return CONTEXT_SOURCE.DAST.name();
            default:
                return CONTEXT_SOURCE.API.name();
        }
    }

    public static Set<String> getEntitledScopes(int accountId) {
        Set<String> scopes = new LinkedHashSet<>();
        try {
            Organization organization = instance.findOne(Filters.in(Organization.ACCOUNTS, accountId));
            if (organization != null && organization.getFeatureWiseAllowed() != null
                    && !organization.getFeatureWiseAllowed().isEmpty()) {
                HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
                for (DashboardCategory category : CATEGORY_PRECEDENCE) {
                    if (isCategoryGranted(featureWiseAllowed, category)) {
                        scopes.add(scopeOf(category));
                    }
                }
            }
        } catch (Exception e) {
            // entitlements unavailable — fall through to the API default below
        }

        if (scopes.isEmpty()) {
            scopes.add(CONTEXT_SOURCE.API.name());
        }
        return scopes;
    }

    public static void createIndexIfAbsent() {
        {
            String[] fieldNames = {Organization.ACCOUNTS};
            MCollection.createIndexIfAbsent(instance.getDBName(), instance.getCollName(), fieldNames, true);
        }
        {
            String[] fieldNames = {Organization.SYNCED_WITH_AKTO};
            MCollection.createIndexIfAbsent(instance.getDBName(), instance.getCollName(), fieldNames, true);
        }
        {
            String[] fieldNames = {Organization.ADMIN_EMAIL};
            MCollection.createIndexIfAbsent(instance.getDBName(), instance.getCollName(), fieldNames, true);
        }
    }

    @Override
    public String getCollName() {
        return "organizations";
    }

    @Override
    public Class<Organization> getClassT() {
        return Organization.class;
    }

    public Organization findOneByAccountId(int accountId) {
        return OrganizationsDao.instance.findOne(
                Filters.in(Organization.ACCOUNTS, accountId));
    }

    public static BasicDBObject getBillingTokenForAuth() {
        int accountId = Context.accountId.get();
        Organization organization = OrganizationsDao.instance.findOne(
                Filters.in(Organization.ACCOUNTS, accountId)
        );
        if (organization == null) {
            return new BasicDBObject("error", "organization not found");
        }

        Bson filters = Filters.and(
                Filters.eq(Tokens.ORG_ID, organization.getId()),
                Filters.eq(Tokens.ACCOUNT_ID, accountId)
        );
        
        Tokens tokens = TokensDao.instance.findOne(filters);
        
        if (tokens == null || tokens.isOldToken()) {
            Bson updates;
            String newToken = null;
            
            if (tokens == null) {
                // Create new token entry
                newToken = organization.getId() + "_" + accountId + "_" + UUID.randomUUID().toString().replace("-", "");
                updates = Updates.combine(
                        Updates.set(Tokens.UPDATED_AT, Context.now()),
                        Updates.setOnInsert(Tokens.CREATED_AT, Context.now()),
                        Updates.setOnInsert(Tokens.ORG_ID, organization.getId()),
                        Updates.setOnInsert(Tokens.ACCOUNT_ID, accountId),
                        Updates.setOnInsert(Tokens.TOKEN, newToken)
                );
            } else {
                // Update existing token
                updates = Updates.set(Tokens.UPDATED_AT, Context.now());
                newToken = tokens.getToken();
            }
            UsageUtils.saveToken(organization.getId(), accountId, updates, filters, newToken);
            
            return new BasicDBObject("token", newToken);
        }
        
        return new BasicDBObject("token", tokens.getToken());
    }

}
