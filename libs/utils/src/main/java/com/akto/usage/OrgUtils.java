package com.akto.usage;

import com.akto.dao.AccountsDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.Account;
import com.akto.dto.billing.Organization;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.List;

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
}
