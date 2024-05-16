package com.akto.dao.usage;

import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.billing.FeatureAccess;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.BillingContextDao;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.util.Constants;
import com.akto.util.Util;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

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

    private final String LATEST_DOCUMENT = "latestDocument";
    private final String TOTAL_USAGE = "totalUsage";
    private final String LAST_MEASURED = "lastMeasured";

    public Map<String, FeatureAccess> findLatestUsageMetricsForOrganization(Set<Integer> accounts) {

        BasicDBObject groupedId = new BasicDBObject(UsageMetric.METRIC_TYPE, Util.prefixDollar(UsageMetric.METRIC_TYPE))
                .append(UsageMetric.ACCOUNT_ID, Util.prefixDollar(UsageMetric.ACCOUNT_ID));

        /*
         * check usage metrics for this measureEpoch only
         * and since it can only be as big as its period
         * we check the entire period.
         */
        int lastMinMeasureEpoch = Context.now() - (UsageMetricInfo.MEASURE_PERIOD + 86400) ;

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(
                    Filters.and(
                        Filters.in(UsageMetric.ACCOUNT_ID, accounts),
                        Filters.gte(UsageMetric.AKTO_SAVE_EPOCH, lastMinMeasureEpoch)
                    )
                ),
                Aggregates.sort(Sorts.descending(UsageMetric.AKTO_SAVE_EPOCH)),
                Aggregates.group(groupedId, Accumulators.first(LATEST_DOCUMENT, MCollection.ROOT_ELEMENT)),
                Aggregates.replaceRoot(Util.prefixDollar(LATEST_DOCUMENT)),
                Aggregates.group(Util.prefixDollar(UsageMetric.METRIC_TYPE),
                        Accumulators.sum(TOTAL_USAGE, Util.prefixDollar(UsageMetric._USAGE)),
                        Accumulators.max(LAST_MEASURED, Util.prefixDollar(UsageMetric.AKTO_SAVE_EPOCH))));

        MongoCursor<BasicDBObject> cursor = UsageMetricsDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();

        Map<String, FeatureAccess> consolidatedUsage = new HashMap<>();

        while (cursor.hasNext()) {
            BasicDBObject v = cursor.next();
            try {
                String metricType = (String) v.get(Constants.ID);
                int usage = (int) v.get(TOTAL_USAGE);
                int lastMeasured = (int) v.get(LAST_MEASURED);
                FeatureAccess featureAccess = new FeatureAccess(true, lastMeasured, -1, usage);
                consolidatedUsage.put(metricType, featureAccess);
            } catch (Exception e) {
            }
        }

        return consolidatedUsage;

    }
}
