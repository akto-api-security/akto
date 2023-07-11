package com.akto.dao;


import com.akto.dto.RBAC;
import com.mongodb.client.model.Filters;

public class RBACDao extends CommonContextDao<RBAC> {
    public static final RBACDao instance = new RBACDao();

    public boolean isAdmin(int userId, int accountId) {
        RBAC rbac = RBACDao.instance.findOne(
                Filters.and(
                        Filters.eq(RBAC.USER_ID, userId),
                        Filters.eq(RBAC.ROLE, RBAC.Role.ADMIN),
                        Filters.eq(RBAC.ACCOUNT_ID, accountId)
                )
        );
        return rbac != null;
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
