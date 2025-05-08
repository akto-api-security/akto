package com.akto.dao.threat_detection;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.threat_detection.ApiHitCountInfo;

public class ApiHitCountInfoDao extends AccountsContextDao<ApiHitCountInfo> {

    public static final ApiHitCountInfoDao instance = new ApiHitCountInfoDao();

    @Override
    public String getCollName() {
        return "api_hit_count_info";
    }

    @Override
    public Class<ApiHitCountInfo> getClassT() {
        return ApiHitCountInfo.class;
    }
    
}
