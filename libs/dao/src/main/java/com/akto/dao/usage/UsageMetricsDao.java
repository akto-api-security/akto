package com.akto.dao.usage;

import org.bson.conversions.Bson;
import org.springframework.jmx.support.MetricType;

import com.akto.dao.BillingContextDao;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.mongodb.client.model.Filters;

public class UsageMetricsDao extends BillingContextDao<UsageMetric>{

    public static final UsageMetricsDao instance = new UsageMetricsDao();

    @Override
    public String getCollName() {
        return "usage_metrics";
    }

    @Override
    public Class<UsageMetric> getClassT() {
        return UsageMetric.class;
    }

    public static Bson generateFilter(String organizationId, int accountId, MetricTypes metricType) {
        return Filters.and(
            Filters.eq(UsageMetricInfo.ORGANIZATION_ID, organizationId),
            Filters.eq(UsageMetricInfo.ACCOUNT_ID, accountId),
            Filters.eq(UsageMetricInfo.METRIC_TYPE, metricType.toString())
        );
    }
}
