package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

public class CustomRoleDao extends AccountsContextDao<CustomRole> {

    public static final CustomRoleDao instance = new CustomRoleDao();

    public void createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get()+"";
        MongoDatabase db = clients[0].getDatabase(dbName);
        for (String col: db.listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            db.createCollection(getCollName());
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { CustomRole._NAME }, false);
    }

    public CustomRole findRoleByName(String roleName) {
        return instance.findOne(Filters.eq(CustomRole._NAME, roleName));
    }

    @Override
    public String getCollName() {
        return "custom_roles";
    }

    @Override
    public Class<CustomRole> getClassT() {
        return CustomRole.class;
    }

}
