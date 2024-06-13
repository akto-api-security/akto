package com.akto.dao.billing;

import com.akto.dao.BillingContextDao;
import com.akto.dto.billing.OrganizationFlags;

public class OrganizationFlagsDao extends BillingContextDao<OrganizationFlags> {
    public static final OrganizationFlagsDao instance = new OrganizationFlagsDao();

    @Override
    public String getCollName() {
        return "organization_flags";
    }

    @Override
    public Class<OrganizationFlags> getClassT() {
        return OrganizationFlags.class;
    }
}
