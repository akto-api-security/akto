package com.akto.dao.usage;

import com.akto.dao.BillingContextDao;
import com.akto.dto.usage.UsageMetricInfo;

public class UsageMetricInfoDao extends BillingContextDao<UsageMetricInfo>{

    public static final UsageMetricInfoDao instance = new UsageMetricInfoDao();

    @Override
    public String getCollName() {
        return "usage_metric_info";
    }

    @Override
    public Class<UsageMetricInfo> getClassT() {
        return UsageMetricInfo.class;
    }
}
