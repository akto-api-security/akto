package com.akto.dao;


import com.akto.dto.RBAC;
import com.mongodb.client.model.Filters;

public class RBACDao extends CommonContextDao<RBAC> {
    public static final RBACDao instance = new RBACDao();

    public boolean isAdmin(int userId, int accountId) {
        RBAC rbac = RBACDao.instance.findOne(
                Filters.or(Filters.and(
                                Filters.eq(RBAC.USER_ID, userId),
                                Filters.eq(RBAC.ROLE, RBAC.Role.ADMIN),
                                Filters.eq(RBAC.ACCOUNT_ID, accountId)
                        ),
                        Filters.and(
                                Filters.eq(RBAC.USER_ID, userId),
                                Filters.eq(RBAC.ROLE, RBAC.Role.ADMIN),
                                Filters.exists(RBAC.ACCOUNT_ID, false)
                                )
                )
        );
        if (rbac != null && rbac.getAccountId() == 0) {//case where account id doesn't exists belonged to older 1_000_000 account
            rbac.setAccountId(1_000_000);
        }
        return rbac != null && rbac.getAccountId() == accountId;
    }

    @Override
    public String getCollName() {
        return "rbac";
    }

    @Override
    public Class<RBAC> getClassT() {
        return RBAC.class;
    }
}
