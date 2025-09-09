package com.akto.usage;

import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.metadata.ActiveAccounts;
import com.akto.log.LoggerMaker;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

public class UsageMetricCalculator {
    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageMetricCalculator.class);
    public static Set<Integer> getDemos() {
        ApiCollection juiceShop = ApiCollectionsDao.instance.findByName("juice_shop_demo");

        Set<Integer> demos = new HashSet<>();
        demos.add(1111111111);

        if (juiceShop != null) {
            demos.add(juiceShop.getId());
        }

        return demos;
    }

    /*
     * to handle multiple accounts using static maps.
     */
    /*
     * RBAC_FEATURE is advanced RBAC, for collection based RBAC and custom roles.
     * RBAC_BASIC is basic RBAC for inviting with multiple roles.
     */
    private final static String FEATURE_LABEL_STRING = "RBAC_FEATURE";
    private final static String BASIC_RBAC_FEATURE = "RBAC_BASIC";
    private static Map<Integer, Integer> lastDeactivatedFetchedMap = new HashMap<>();
    private static final int REFRESH_INTERVAL = 60 * 2; // 2 minutes.
    private static final int REFRESH_INTERVAL_RBAC = 60 * 60; // 1 hour.
    private static Map<Integer, Set<Integer>> deactivatedCollectionsMap = new HashMap<>();

    private static final ConcurrentHashMap<Integer, Pair<Boolean, Integer>> hasRbacFeatureEnabledMap = new ConcurrentHashMap<>();

    public static Set<Integer> getDeactivated() {
        int accountId = Context.accountId.get();
        if (lastDeactivatedFetchedMap.containsKey(accountId)
                && (lastDeactivatedFetchedMap.get(accountId) + REFRESH_INTERVAL) >= Context.now()
                && deactivatedCollectionsMap.containsKey(accountId)) {
            return deactivatedCollectionsMap.get(accountId);
        }

        deactivatedCollectionsMap.put(accountId, getDeactivatedLatest());
        lastDeactivatedFetchedMap.put(accountId, Context.now());
        return deactivatedCollectionsMap.get(accountId);
    }

    private static boolean checkForPaidFeature(int accountId){
        Organization organization = OrganizationsDao.instance.findOne(Filters.in(Organization.ACCOUNTS, accountId));
        if(organization == null || organization.getFeatureWiseAllowed() == null || organization.getFeatureWiseAllowed().isEmpty()){
            return true;
        }

        HashMap<String, FeatureAccess> featureWiseAllowed = organization.getFeatureWiseAllowed();
        FeatureAccess featureAccess = featureWiseAllowed.getOrDefault(FEATURE_LABEL_STRING, FeatureAccess.noAccess);
        FeatureAccess basicAccess = featureWiseAllowed.getOrDefault(BASIC_RBAC_FEATURE, FeatureAccess.noAccess);
        return featureAccess.getIsGranted() || basicAccess.getIsGranted();
    }

    public static boolean isRbacFeatureAvailable(int accountId){
        int timeNow = Context.now();
        Pair<Boolean, Integer> prevVal = hasRbacFeatureEnabledMap.getOrDefault(accountId, new Pair<>(false, timeNow));
        boolean ans = prevVal.getFirst();
        int lastCalTime = prevVal.getSecond();
        if(!hasRbacFeatureEnabledMap.contains(accountId) || (lastCalTime + REFRESH_INTERVAL_RBAC < timeNow)){
            ans = checkForPaidFeature(accountId);
            hasRbacFeatureEnabledMap.put(accountId, new Pair<>(ans, timeNow));
        }
        return ans;
    }

    public static Set<Integer> getDeactivatedLatest(){
        List<ApiCollection> deactivated = ApiCollectionsDao.instance
                .findAll(Filters.eq(ApiCollection._DEACTIVATED, true));
        Set<Integer> deactivatedIds = new HashSet<>(
                deactivated.stream().map(apiCollection -> apiCollection.getId()).collect(Collectors.toList()));

        return deactivatedIds;
    }
    
    public static Bson excludeDemosAndDeactivated(String key){
        List<Integer> demos = new ArrayList<>(getDemos());
        List<Integer> deactivated = new ArrayList<>(getDeactivatedLatest());
        deactivated.addAll(demos);
        return Filters.nin(key, deactivated);
    }

    public static Set<Integer> getDemosAndDeactivated() {
        Set<Integer> ret = new HashSet<>();
        ret.addAll(getDeactivated());
        ret.addAll(getDemos());
        return ret;
    }

    public static Set<Integer> getMcpCollections() {
        return UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.MCP);
    }

    public static Set<Integer> getGenAiCollections() {
        return UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.GEN_AI);
    }

    public static Set<Integer> getApiCollections() {
        return UsersCollectionsList.getContextCollections(CONTEXT_SOURCE.API);
    }

    public static List<String> getInvalidTestErrors() {
        List<String> invalidErrors = new ArrayList<String>() {{
            add(TestResult.TestError.DEACTIVATED_ENDPOINT.getMessage());
            add(TestResult.TestError.USAGE_EXCEEDED.getMessage());
        }};
        return invalidErrors;
    }

    public static int calculateActiveEndpoints() {
        /*
         * Count all endpoints.
         * Same query being used on dashboard.
         */
        Set<Integer> collectionsToDiscard = getGenAiCollections();
        collectionsToDiscard.addAll(getMcpCollections());
        return calculateEndpoints(collectionsToDiscard);
    }

    private static int calculateMCPAssets() {
        /*
         * To count MCP endpoints, we need to filter out the collections that does not have
         * MCP tag in tagsList.
         */
        Set<Integer> collectionsToDiscard = getGenAiCollections();
        collectionsToDiscard.addAll(getApiCollections());
        return calculateEndpoints(collectionsToDiscard);
    }

    private static int calculateGenAiAssets() {
        /*
         * To count GenAI endpoints, we need to filter out the collections that does not have
         * GenAI tag in tagsList.
         */
        Set<Integer> collectionsToDiscard = getMcpCollections();
        collectionsToDiscard.addAll(getApiCollections());
        return calculateEndpoints(collectionsToDiscard);
    }

    private static int calculateEndpoints(Set<Integer> collectionsIdsToDiscard) {
        collectionsIdsToDiscard.addAll(getDemosAndDeactivated());
        return (int) SingleTypeInfoDao.instance.fetchEndpointsCount(0, Context.now(), collectionsIdsToDiscard, false);
    }

    public static int calculateCustomTests(UsageMetric usageMetric) {
        int customTemplates = (int) YamlTemplateDao.instance.count(
            Filters.eq(YamlTemplate.SOURCE, YamlTemplateSource.CUSTOM)
        );
        return customTemplates;
    }

    public static int calculateTestRuns(UsageMetric usageMetric) {
        int measureEpoch = usageMetric.getMeasureEpoch();

        Bson demoAndDeactivatedCollFilter = excludeDemosAndDeactivated(TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.API_COLLECTION_ID);

        List<Bson> filters = new ArrayList<Bson>(){{
            add(Filters.gt(TestingRunResult.END_TIMESTAMP, measureEpoch));
            add(demoAndDeactivatedCollFilter);
        }};

        // TODO: When we shift vulnerable test results into new collection completely {without making copy}, fix count here then.

        int testRuns = (int) TestingRunResultDao.instance.count(Filters.and(filters));

        /*
         * NOTE: not using a single nin query,
         * because this approach uses indexes more efficiently.
         */

        filters.add(Filters.in(TestResult.TEST_RESULTS_ERRORS, getInvalidTestErrors()));
        int invalidTestRuns = (int) TestingRunResultDao.instance.count(Filters.and(filters));
        int finalCount = Math.max(testRuns - invalidTestRuns, 0);

        return finalCount;
    }

    public static int calculateActiveAccounts(UsageMetric usageMetric) {
        String organizationId = usageMetric.getOrganizationId();

        if (organizationId == null) {
            return 0;
        }

        // Get organization
        Organization organization = OrganizationsDao.instance.findOne(
            Filters.eq(Organization.ID, organizationId)
        );

        if (organization == null) {
            return 0;
        }

        // String adminEmail = organization.getAdminEmail();

        // // Get admin user id
        // User admin = UsersDao.instance.findOne(
        //     Filters.eq(User.LOGIN, adminEmail)
        // );

        // if (admin == null) {
        //     return 0;
        // }

        // int adminUserId = admin.getId();

        // Get accounts belonging to organization
        //Set<Integer> accounts = OrganizationUtils.findAccountsBelongingToOrganization(adminUserId);
        Set<Integer> accounts = organization.getAccounts();

        Gson gson = new Gson();
        ActiveAccounts activeAccounts = new ActiveAccounts(accounts);
        String jsonString = gson.toJson(activeAccounts);
        usageMetric.setMetadata(jsonString);

        /*
         * since we are running this query for each account,
         * and while consolidating the usage metrics
         * we are summing up the usage metrics for each account,
         * thus to avoid over counting, we should just return 1 here.
         */
        return 1;
    }
    
    public static void calculateUsageMetric(UsageMetric usageMetric) {
        MetricTypes metricType = usageMetric.getMetricType();
        int usage = 0;

        usageMetric.setRecordedAt(Context.now());
        int now = Context.now();
        loggerMaker.infoAndAddToDb("calculating as of measure-epoch: " + usageMetric.getMeasureEpoch() + " " + (now-usageMetric.getMeasureEpoch())/60 + " minutes ago", LoggerMaker.LogDb.DASHBOARD);
        loggerMaker.infoAndAddToDb("calculate "+metricType+" for account: "+ Context.accountId.get() +" org: " + usageMetric.getOrganizationId(), LoggerMaker.LogDb.DASHBOARD);

        if (metricType != null) {
            switch (metricType) {
                case ACTIVE_ENDPOINTS:
                    usage = calculateActiveEndpoints();
                    break;
                case MCP_ASSET_COUNT:
                    usage = calculateMCPAssets();
                    break;
                case AI_ASSET_COUNT:
                    usage = calculateGenAiAssets();
                    break;
                case CUSTOM_TESTS:
                    usage = calculateCustomTests(usageMetric);
                    break;
                case TEST_RUNS:
                    usage = calculateTestRuns(usageMetric);
                    break;
                case ACTIVE_ACCOUNTS:
                    usage = calculateActiveAccounts(usageMetric);
                    break;
                default:
                    usage = 0;
                    break;
            }
        }

        loggerMaker.infoAndAddToDb("measured "+metricType+": " + usage +" for account: "+ Context.accountId.get() +" epoch: " + usageMetric.getMeasureEpoch(), LoggerMaker.LogDb.DASHBOARD);

        usageMetric.setUsage(usage);
    }      

}
