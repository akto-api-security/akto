package com.akto.dao.billing;

import com.akto.dao.BillingContextDao;
import com.akto.dto.billing.Organization;

public class OrganizationsDao extends BillingContextDao<Organization>{

    public static final OrganizationsDao instance = new OrganizationsDao();

    @Override
    public String getCollName() {
        return "organizations";
    }

    @Override
    public Class<Organization> getClassT() {
        return Organization.class;
    }
}
