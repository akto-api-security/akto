package com.akto.dao.usage;

import com.akto.dao.MCollection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.mongodb.client.model.Sorts;
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

    private final String LATEST_DOCUMENT = "latestDocument";
    private final String TOTAL_USAGE = "totalUsage";

    public Map<String, Integer> findLatestUsageMetricsForOrganization(String organizationId) {

        BasicDBObject groupedId = new BasicDBObject(UsageMetric.METRIC_TYPE, Util.prefixDollar(UsageMetric.METRIC_TYPE))
                .append(UsageMetric.ACCOUNT_ID, Util.prefixDollar(UsageMetric.ACCOUNT_ID));

        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(Filters.eq(UsageMetric.ORGANIZATION_ID, organizationId)),
                Aggregates.sort(Sorts.descending(UsageMetric.RECORDED_AT)),
                Aggregates.group(groupedId, Accumulators.first(LATEST_DOCUMENT, MCollection.ROOT_ELEMENT)),
                Aggregates.replaceRoot(Util.prefixDollar(LATEST_DOCUMENT)),
                Aggregates.group(Util.prefixDollar(UsageMetric.METRIC_TYPE),
                        Accumulators.sum(TOTAL_USAGE, Util.prefixDollar(UsageMetric._USAGE))));

        MongoCursor<BasicDBObject> cursor = UsageMetricsDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();

        Map<String, Integer> consolidatedUsage = new HashMap<>();

        while (cursor.hasNext()) {
            BasicDBObject v = cursor.next();
            try {
                consolidatedUsage.put((String) v.get(Constants.ID), (int) v.get(TOTAL_USAGE));
            } catch (Exception e) {
            }
        }

        return consolidatedUsage;

    }

}
