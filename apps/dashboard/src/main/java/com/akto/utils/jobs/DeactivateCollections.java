package com.akto.utils.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.akto.action.ApiCollectionsAction;
import com.akto.billing.UsageMetricHandler;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.tasks.OrganizationTask;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class DeactivateCollections {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DeactivateCollections.class);

    final static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public static void deactivateCollectionsJob() {
        executorService.schedule(new Runnable() {
            public void run() {
                OrganizationTask.instance.executeTask(new Consumer<Organization>() {
                    @Override
                    public void accept(Organization organization) {
                        deactivateCollectionsForOrganization(organization);
                    }
                }, "deactivate-collections");
            }
        }, 0, TimeUnit.SECONDS);
    }

    private static void deactivateCollectionsForOrganization(Organization organization) {
        try {
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(organization, MetricTypes.ACTIVE_ENDPOINTS);
            if (!featureAccess.checkInvalidAccess()) {
                return;
            }
            int overage = featureAccess.getUsage() - featureAccess.getUsageLimit();
            String organizationId = organization.getId();

            for (int accountId : organization.getAccounts()) {
                Context.accountId.set(accountId);
                UsageMetricInfo usageMetricInfo = UsageMetricInfoDao.instance.findOne(
                        UsageMetricsDao.generateFilter(organizationId, accountId, MetricTypes.ACTIVE_ENDPOINTS));
                int measureEpoch = Context.now();
                if (usageMetricInfo != null) {
                    measureEpoch = usageMetricInfo.getMeasureEpoch();
                }
                overage = deactivateCollectionsForAccount(overage, measureEpoch);
            }
        } catch (Exception e) {
            String errorMessage = String.format("Unable to deactivate collections for %s ", organization.getId());
            loggerMaker.errorAndAddToDb(e, errorMessage, LogDb.DASHBOARD);
        }

        UsageMetricHandler.calcAndFetchFeatureAccess(MetricTypes.ACTIVE_ENDPOINTS, Context.accountId.get());
    }

    private static int deactivateCollectionsForAccount(int overage, int measureEpoch) {
        
        ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
        apiCollectionsAction.fetchAllCollections();
        List<ApiCollection> apiCollections = apiCollectionsAction.getApiCollections();

        Set<Integer> demoIds = UsageMetricCalculator.getDemos();
        apiCollections.removeIf(apiCollection -> demoIds.contains(apiCollection.getId()));
        apiCollections.removeIf(apiCollection -> apiCollection.isDeactivated());
        apiCollections.removeIf(apiCollection -> ApiCollection.Type.API_GROUP.equals(apiCollection.getType()));
        
        Map<Integer, Integer> lastTrafficSeenMap = ApiInfoDao.instance.getLastTrafficSeen();

        apiCollections.sort((ApiCollection o1, ApiCollection o2) -> {
            int t1 = lastTrafficSeenMap.getOrDefault(o1.getId(), 0);
            int t2 = lastTrafficSeenMap.getOrDefault(o2.getId(), 0);
            if (t1 == t2) {
                return 0;
            }
            return t1 < t2 ? -1 : 1;
        });

        List<Integer> apiCollectionIds = new ArrayList<>();

        for (ApiCollection apiCollection : apiCollections) {

            if (overage <= 0) {
                break;
            }
            if (apiCollection.isDeactivated()) {
                continue;
            }

            /*
             * Since we take only endpoints lastSeen/discovered after measureEpoch while
             * calculating endpoints usage, 
             * we should only deactivate the collections to which they belong.
             */
            if(lastTrafficSeenMap.getOrDefault(apiCollection.getId(), 0) <= measureEpoch){
                continue;
            }

            overage -= apiCollection.getUrlsCount();
            apiCollectionIds.add(apiCollection.getId());
        }

        ApiCollectionsDao.instance.updateMany(Filters.in(Constants.ID, apiCollectionIds),
                Updates.set(ApiCollection._DEACTIVATED, true));

        // TODO: handle case for API groups.

        return overage;
    }
}
