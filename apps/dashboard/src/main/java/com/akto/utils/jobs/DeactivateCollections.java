package com.akto.utils.jobs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.json.JSONObject;

import com.akto.action.ApiCollectionsAction;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.BackwardCompatibilityDao;
import com.akto.dao.context.Context;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.BackwardCompatibility;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetricInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.usage.UsageMetricHandler;
import com.akto.util.tasks.OrganizationTask;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

public class DeactivateCollections {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DeactivateCollections.class, LogDb.DASHBOARD);

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

    private static void raiseMixpanelEvent(Organization organization, int accountId, int overage) {
        String eventName = "DEACTIVATED_COLLECTIONS";
        JSONObject props = new JSONObject();
        props.put("Overage", overage);
        UsageMetricUtils.raiseUsageMixpanelEvent(organization, accountId, eventName, props);
    }

    protected static void sendToSlack(Organization organization, int accountId, int overage, int usageLimit) {
        if (organization == null) {
            return;
        }
        String txt = String.format("Overage found for %s %s acc: %s limit: %s overage: %s . Deactivating collections.",
                organization.getId(), organization.getAdminEmail(), accountId, usageLimit, overage);
        UsageMetricUtils.sendToUsageSlack(txt);
    }

    private static void deactivateCollectionsForOrganization(Organization organization) {
        try {
            Set<Integer> accounts = organization.getAccounts();
            HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
            featureWiseAllowed = UsageMetricHandler.updateFeatureMapWithLocalUsageMetrics(featureWiseAllowed, accounts);
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccess(organization, MetricTypes.ACTIVE_ENDPOINTS);
            if (!featureAccess.checkInvalidAccess()) {
                return;
            }
            int overage = Math.max(featureAccess.getUsage() - featureAccess.getUsageLimit(), 0);
            String organizationId = organization.getId();

            String infoMessage = String.format("Overage found org: %s , overage: %s , deactivating collections",
                    organizationId, overage);
            loggerMaker.debugAndAddToDb(infoMessage);

            for (int accountId : organization.getAccounts()) {
                Context.accountId.set(accountId);

                BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance
                        .findOne(new BasicDBObject());
                if (backwardCompatibility == null) {
                    continue;
                }
                // we need to run this job only once.
                if (backwardCompatibility.getDeactivateCollections() != 0) {
                    loggerMaker.debugAndAddToDb("This account's collections have been deactivated previously");
                    continue;
                }

                UsageMetricInfo usageMetricInfo = UsageMetricInfoDao.instance.findOne(
                        UsageMetricsDao.generateFilter(organizationId, accountId, MetricTypes.ACTIVE_ENDPOINTS));
                int measureEpoch = Context.now();
                if (usageMetricInfo != null) {
                    measureEpoch = usageMetricInfo.getMeasureEpoch();
                }
                raiseMixpanelEvent(organization, accountId, overage);
                sendToSlack(organization, accountId, overage, featureAccess.getUsageLimit());
                overage = deactivateCollectionsForAccount(overage, measureEpoch);
                BackwardCompatibilityDao.instance.updateOne(
                        Filters.eq("_id", backwardCompatibility.getId()),
                        Updates.set(BackwardCompatibility.DEACTIVATE_COLLECTIONS, Context.now()));
            }

            loggerMaker.debugAndAddToDb("overage after deactivating: " + overage);

            int deltaUsage = (-1) * (Math.max(featureAccess.getUsage() - featureAccess.getUsageLimit(), 0) - overage);
            UsageMetricHandler.calcAndFetchFeatureAccessUsingDeltaUsage(MetricTypes.ACTIVE_ENDPOINTS, Context.accountId.get(), deltaUsage);

        } catch (Exception e) {
            String errorMessage = String.format("Unable to deactivate collections for %s ", organization.getId());
            loggerMaker.errorAndAddToDb(e, errorMessage);
        }

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
            if (lastTrafficSeenMap.getOrDefault(apiCollection.getId(), 0) <= measureEpoch) {
                continue;
            }

            overage -= apiCollection.getUrlsCount();
            apiCollectionIds.add(apiCollection.getId());
        }

        /*
         * Un comment this in next release.
         */
        // ApiCollectionsDao.instance.updateMany(Filters.in(Constants.ID, apiCollectionIds),
        //         Updates.set(ApiCollection._DEACTIVATED, true));

        // String infoMessage = String.format("Deactivated collections : %s", apiCollectionIds.toString());
        // loggerMaker.debugAndAddToDb(infoMessage);

        // TODO: handle case for API groups.

        return overage;
    }
}
