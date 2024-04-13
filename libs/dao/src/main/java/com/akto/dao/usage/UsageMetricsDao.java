package com.akto.dao.usage;

import com.akto.dao.MCollection;
import org.bson.conversions.Bson;

import com.akto.dao.BillingContextDao;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.mongodb.client.model.Filters;

public class UsageMetricsDao extends BillingContextDao<UsageMetric>{

    public static final UsageMetricsDao instance = new UsageMetricsDao();

    public static void createIndexIfAbsent() {
        {
            String[] fieldNames = {UsageMetric.ACCOUNT_ID, UsageMetric.AKTO_SAVE_EPOCH};
            MCollection.createIndexIfAbsent(instance.getDBName(), instance.getCollName(), fieldNames, true);
        }
        {
            String[] fieldNames = {UsageMetric.SYNCED_WITH_AKTO};
            MCollection.createIndexIfAbsent(instance.getDBName(), instance.getCollName(), fieldNames, true);
        }
    }

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
