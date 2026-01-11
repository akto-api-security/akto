package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionIcon;

public class ApiCollectionIconsDao extends CommonContextDao<ApiCollectionIcon> {

    public static final ApiCollectionIconsDao instance = new ApiCollectionIconsDao();

    private ApiCollectionIconsDao() {}

    @Override
    public String getCollName() {
        return "api_collection_icons";
    }

    @Override
    public Class<ApiCollectionIcon> getClassT() {
        return ApiCollectionIcon.class;
    }


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

        String[] domainNameIndex = { ApiCollectionIcon.DOMAIN_NAME };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), domainNameIndex, false);

        String[] matchingHostnamesIndex = { ApiCollectionIcon.MATCHING_HOSTNAMES };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), matchingHostnamesIndex, false);
        

    }
}