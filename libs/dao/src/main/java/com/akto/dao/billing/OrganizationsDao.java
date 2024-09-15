package com.akto.dao.billing;

import org.bson.conversions.Bson;

import com.akto.dao.BillingContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.Tokens;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

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
        BasicDBObject bDObject;
        int accountId = Context.accountId.get();
        Organization organization = OrganizationsDao.instance.findOne(
                Filters.in(Organization.ACCOUNTS, accountId)
        );
        if (organization == null) {
            return new BasicDBObject("error", "organization not found");
        }

        Tokens tokens;
        Bson filters = Filters.and(
                Filters.eq(Tokens.ORG_ID, organization.getId()),
                Filters.eq(Tokens.ACCOUNT_ID, accountId)
        );
        String errMessage = "";
        tokens = TokensDao.instance.findOne(filters);
        if (tokens == null) {
            errMessage = "error extracting ${akto_header}, token is missing";
        }
        if (tokens.isOldToken()) {
            errMessage = "error extracting ${akto_header}, token is old";
        }
        if(errMessage.length() > 0){
            bDObject = new BasicDBObject("error", errMessage);
        }else{
            bDObject = new BasicDBObject("token", tokens.getToken());
        }
        return bDObject;
    }

}
