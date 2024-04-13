package com.akto.dao.usage;

import com.akto.dao.BillingContextDao;
import com.akto.dto.usage.UsageSync;

public class UsageSyncDao extends BillingContextDao<UsageSync>{

    public static final UsageSyncDao instance = new UsageSyncDao();

    @Override
    public String getCollName() {
        return "usage_sync";
    }

    @Override
    public Class<UsageSync> getClassT() {
        return UsageSync.class;
    }
}
