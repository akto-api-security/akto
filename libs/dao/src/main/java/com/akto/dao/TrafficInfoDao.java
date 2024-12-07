package com.akto.dao;

import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.traffic.TrafficInfo;

public class TrafficInfoDao extends AccountsContextDaoWithRbac<TrafficInfo> {

    public static final TrafficInfoDao instance = new TrafficInfoDao();

    @Override
    public String getCollName() {
        return "traffic_info";
    }

    @Override
    public Class<TrafficInfo> getClassT() {
        return TrafficInfo.class;
    }

    @Override
    public String getFilterKeyString() {
        return TestingEndpoints.getFilterPrefix(ApiCollectionUsers.CollectionType.Id_ApiCollectionId) + ApiInfo.ApiInfoKey.API_COLLECTION_ID;
    }
}
