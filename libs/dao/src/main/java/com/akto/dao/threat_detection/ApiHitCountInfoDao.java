package com.akto.dao.threat_detection;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.type.SingleTypeInfo;

public class ApiHitCountInfoDao extends AccountsContextDao<ApiHitCountInfo> {

    public static final ApiHitCountInfoDao instance = new ApiHitCountInfoDao();

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

        String[] fieldNames = {"ts"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        fieldNames = new String[]{"apiCollectionId", "url", "method"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, true);

        // Compound index to support time-bounded queries per endpoint
        fieldNames = new String[]{"apiCollectionId", "url", "method", "ts"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    @Override
    public String getCollName() {
        return "api_hit_count_info";
    }

    @Override
    public Class<ApiHitCountInfo> getClassT() {
        return ApiHitCountInfo.class;
    }

}