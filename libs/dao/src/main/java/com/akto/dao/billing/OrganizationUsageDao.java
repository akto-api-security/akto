package com.akto.dao.billing;

import com.akto.dao.BillingContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.billing.OrganizationUsage;

import static com.akto.dto.billing.OrganizationUsage.*;

public class OrganizationUsageDao extends BillingContextDao<OrganizationUsage> {

    public static final OrganizationUsageDao instance = new OrganizationUsageDao();

    public static void createIndexIfAbsent() {
        {
            String[] fieldNames = {ORG_ID, DATE};
            MCollection.createIndexIfAbsent(instance.getDBName(), instance.getCollName(), fieldNames, false);
        }
        {
            String[] fieldNames = {ORG_ID, SINKS};
            MCollection.createIndexIfAbsent(instance.getDBName(), instance.getCollName(), fieldNames, false);
        }
    }

    private OrganizationUsageDao() {}

    @Override
    public String getCollName() {
        return "organization_usage";
    }

    @Override
    public Class<OrganizationUsage> getClassT() {
        return OrganizationUsage.class;
    }
}
