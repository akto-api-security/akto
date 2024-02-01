package com.akto.utils.usage;

import java.util.ArrayList;
import java.util.Set;

import com.akto.action.observe.Utils;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.UsersDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.User;
import com.akto.dto.billing.Organization;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.dto.usage.metadata.ActiveAccounts;
import com.akto.log.LoggerMaker;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.akto.utils.billing.OrganizationUtils;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

public class UsageMetricCalculator {
    private static final LoggerMaker loggerMaker = new LoggerMaker(UsageMetricCalculator.class);
    public static Bson excludeDemos(String key) {
        ApiCollection juiceShop = ApiCollectionsDao.instance.findByName("juice_shop_demo");

        ArrayList<Integer> demos = new ArrayList<>();
        demos.add(1111111111);

        if (juiceShop != null) {
            demos.add(juiceShop.getId());
        }

        return Filters.nin(key, demos);
    }
    public static int calculateActiveEndpoints(int measureEpoch) {
        int activeEndpoints = Utils.countEndpoints(
                Filters.and(Filters.or(
                        Filters.gt(SingleTypeInfo.LAST_SEEN, measureEpoch),
                        Filters.gt(SingleTypeInfo._TIMESTAMP, measureEpoch)),
                excludeDemos(SingleTypeInfo._API_COLLECTION_ID)));
        
        return activeEndpoints;
    }

    public static int calculateCustomTests(int measureEpoch) {
        int customTemplates = (int) YamlTemplateDao.instance.count(
            Filters.eq(YamlTemplate.SOURCE, YamlTemplateSource.CUSTOM)
        );
        return customTemplates;
    }

    public static int calculateTestRuns(int measureEpoch) {
        Bson demoCollFilter = excludeDemos(TestingRunResult.API_INFO_KEY + "." + ApiInfo.ApiInfoKey.API_COLLECTION_ID);

        int testRuns = (int) TestingRunResultDao.instance.count(
            Filters.and(Filters.gt(TestingRunResult.END_TIMESTAMP, measureEpoch), demoCollFilter)
        );
        return testRuns;
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

        return accounts.size();
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
                    usage = calculateActiveEndpoints(usageMetric.getMeasureEpoch());
                    break;
                case CUSTOM_TESTS:
                    usage = calculateCustomTests(usageMetric.getMeasureEpoch());
                    break;
                case TEST_RUNS:
                    usage = calculateTestRuns(usageMetric.getMeasureEpoch());
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
