package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionIcon;
import com.akto.util.Constants;

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

        String[] imageDataIndex = { ApiCollectionIcon.IMAGE_DATA };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), imageDataIndex, true);

        String[] createdAtIndex = { ApiCollectionIcon.CREATED_AT };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), createdAtIndex, true);

        String[] compoundIndex = { Constants.ID, ApiCollectionIcon.IMAGE_DATA };
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), compoundIndex, true);
    }
}