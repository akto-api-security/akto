package com.akto.dao.billing;

import com.akto.dao.BillingContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.billing.Organization;
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

}
