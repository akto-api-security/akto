package com.akto.dao.threat_detection;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.threat_detection.IpLevelApiHitCount;

public class IpLevelApiHitCountDao extends AccountsContextDao<IpLevelApiHitCount> {
    
    public static final IpLevelApiHitCountDao instance = new IpLevelApiHitCountDao();

    @Override
    public String getCollName() {
        return "ip_level_api_hit_count";
    }

    @Override
    public Class<IpLevelApiHitCount> getClassT() {
        return IpLevelApiHitCount.class;
    }
}
