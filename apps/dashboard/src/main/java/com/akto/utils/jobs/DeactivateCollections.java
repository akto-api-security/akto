package com.akto.utils.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.akto.action.ApiCollectionsAction;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.Account;
import com.akto.dto.ApiCollection;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.usage.MetricTypes;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class DeactivateCollections {

    final static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public static void deactivateCollectionsJob() {
        executorService.schedule(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        deactivateCollectionsForAccount(account.getId());
                    }
                }, "deactivate-collections");

            }
        }, 0, TimeUnit.SECONDS);
    }

    private static void deactivateCollectionsForAccount(int accountId) {

        FeatureAccess featureAccess = UsageMetricUtils.calcFeatureAccess(accountId, MetricTypes.ACTIVE_ENDPOINTS);

        if (!featureAccess.checkInvalidAccess()) {
            return;
        }

        ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
        apiCollectionsAction.fetchAllCollections();
        List<ApiCollection> apiCollections = apiCollectionsAction.getApiCollections();

        List<Integer> demoIds = UsageMetricCalculator.getDemos();
        apiCollections.removeIf(apiCollection -> demoIds.contains(apiCollection.getId()));
        apiCollections.removeIf(apiCollection -> apiCollection.isDeactivated());
        
        Map<Integer, Integer> lastTrafficSeenMap = ApiInfoDao.instance.getLastTrafficSeen();

        apiCollections.sort((ApiCollection o1, ApiCollection o2) -> {
            int t1 = lastTrafficSeenMap.getOrDefault(o1.getId(), 0);
            int t2 = lastTrafficSeenMap.getOrDefault(o2.getId(), 0);
            if (t1 == t2) {
                return 0;
            }
            return t1 < t2 ? -1 : 1;
        });

        int overage = featureAccess.getUsage() - featureAccess.getUsageLimit();

        List<Integer> apiCollectionIds = new ArrayList<>();

        for (ApiCollection apiCollection : apiCollections) {

            if (overage <= 0) {
                break;
            }
            if (apiCollection.isDeactivated()) {
                continue;
            }
            overage -= apiCollection.getUrlsCount();
            apiCollectionIds.add(apiCollection.getId());
        }

        ApiCollectionsDao.instance.updateMany(Filters.in(Constants.ID, apiCollectionIds),
                Updates.set(ApiCollection._DEACTIVATED, true));
    }
}
