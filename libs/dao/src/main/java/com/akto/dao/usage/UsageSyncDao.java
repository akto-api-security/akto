package com.akto.dao.usage;

import org.bson.conversions.Bson;
import org.springframework.jmx.support.MetricType;

import com.akto.dao.BillingContextDao;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.dto.usage.UsageSync;
import com.mongodb.client.model.Filters;

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
