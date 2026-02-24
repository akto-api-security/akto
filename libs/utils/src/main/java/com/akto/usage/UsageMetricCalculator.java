package com.akto.usage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.billing.Organization;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.metadata.ActiveAccounts;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.google.gson.Gson;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

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
    private static Map<Integer, Integer> lastDeactivatedFetchedMap = new HashMap<>();
    private static final int REFRESH_INTERVAL = 60 * 60 * 48; // 2 days.
    private static Map<Integer, Set<Integer>> deactivatedCollectionsMap = new HashMap<>();

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

    public static Set<Integer> getDeactivatedLatest(){
        List<ApiCollection> deactivated = ApiCollectionsDao.instance
                .findAll(Filters.eq(ApiCollection._DEACTIVATED, true), Projections.include(Constants.ID));
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

    public static List<String> getInvalidTestErrors() {
        List<String> invalidErrors = new ArrayList<String>() {{
            add(TestResult.TestError.DEACTIVATED_ENDPOINT.getMessage());
            add(TestResult.TestError.USAGE_EXCEEDED.getMessage());
        }};
        return invalidErrors;
    }

    public static int calculateActiveEndpoints(UsageMetric usageMetric) {
        int measureEpoch = usageMetric.getMeasureEpoch();
        int activeEndpoints = SingleTypeInfoDao.instance.countEndpoints(
                Filters.and(Filters.or(
                        Filters.gt(SingleTypeInfo.LAST_SEEN, measureEpoch),
                        Filters.gt(SingleTypeInfo._TIMESTAMP, measureEpoch)),
                excludeDemosAndDeactivated(SingleTypeInfo._API_COLLECTION_ID)));
        
        return activeEndpoints;
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
                    usage = calculateActiveEndpoints(usageMetric);
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
