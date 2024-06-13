package com.akto.dao;


import com.akto.dao.context.Context;
import com.akto.dto.RBAC;
import com.mongodb.client.model.Filters;

public class RBACDao extends CommonContextDao<RBAC> {
    public static final RBACDao instance = new RBACDao();

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {RBAC.USER_ID};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);
    }

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
