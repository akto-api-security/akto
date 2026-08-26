package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;

/**
 * Staging area for ApiInfos that ApiInfoBackfillJob rebuilt for endpoints missing from api_info.
 * Documents share the ApiInfo shape so they can be promoted into api_info as a straight copy once
 * reviewed. Nothing reads this collection at runtime.
 */
public class MissingApiInfoDao extends AccountsContextDao<ApiInfo> {

    public static final MissingApiInfoDao instance = new MissingApiInfoDao();

    @Override
    public String getCollName() {
        return "missing_api_info";
    }

    @Override
    public Class<ApiInfo> getClassT() {
        return ApiInfo.class;
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        }

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        // mirrors api_info's {apiCollectionId, url} index — the backfill looks up by those two and
        // matches method in memory, so a third field would only add index weight
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[]{"_id." + ApiInfo.ApiInfoKey.API_COLLECTION_ID, "_id." + ApiInfo.ApiInfoKey.URL}, true);
    }
}
