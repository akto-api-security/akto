package com.akto.dao.usage;

import com.akto.dao.BillingContextDao;
import com.akto.dto.usage.MetricTypes;
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

    public UsageMetricInfo findOneOrInsert(String organizationId, int accountId, MetricTypes metricType) {

        UsageMetricInfo usageMetricInfo = instance.findOne(
                UsageMetricsDao.generateFilter(organizationId, accountId, metricType));

        if (usageMetricInfo == null) {
            usageMetricInfo = new UsageMetricInfo(organizationId, accountId, metricType);
            instance.insertOne(usageMetricInfo);
        }

        return usageMetricInfo;
    }

}
