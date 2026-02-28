package com.akto.dao.billing;

import java.util.UUID;

import org.bson.conversions.Bson;

import com.akto.dao.BillingContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.akto.util.UsageUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class OrganizationsDao extends BillingContextDao<Organization>{

    public static final OrganizationsDao instance = new OrganizationsDao();

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
        
        // If token is missing or old, regenerate a fresh token
        if (tokens == null || tokens.isOldToken()) {
            Bson updates;
            
            if (tokens == null) {
                // Create new token entry
                updates = Updates.combine(
                        Updates.set(Tokens.UPDATED_AT, Context.now()),
                        Updates.setOnInsert(Tokens.CREATED_AT, Context.now()),
                        Updates.setOnInsert(Tokens.ORG_ID, organization.getId()),
                        Updates.setOnInsert(Tokens.ACCOUNT_ID, accountId)
                );
            } else {
                // Update existing token
                updates = Updates.set(Tokens.UPDATED_AT, Context.now());
            }
            
            // Generate new token: orgId_accountId_UUID
            String newToken = organization.getId() + "_" + accountId + "_" + UUID.randomUUID().toString().replace("-", "");
            
            // Save the new token
            UsageUtils.saveToken(organization.getId(), accountId, updates, filters, newToken);
            
            return new BasicDBObject("token", newToken);
        }
        
        // Return existing valid token
        return new BasicDBObject("token", tokens.getToken());
    }

}
