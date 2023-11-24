package com.akto.dto.usage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import com.akto.dao.ApiInfoDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dao.usage.UsageMetricInfoDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.dto.billing.Organization;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.UsageUtils;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class UsageMetric {
    
    @BsonId
    private ObjectId id;
    public static final String ID = "_id";
    private String organizationId;
    public static final String ORGANIZATION_ID = "organizationId";
    private int accountId;
    public static final String ACCOUNT_ID = "accountId";
    private MetricTypes metricType;
    public static final String METRIC_TYPE = "metricType";
    private int recordedAt;
    private int usage;
    private String dashboardMode;
    private String dashboardVersion;
    private int minutesSinceLastSync;
    private Map<String, Object> metadata;
    private boolean syncedWithAkto;
    public static final String SYNCED_WITH_AKTO = "syncedWithAkto";
    private String ipAddress;
    private int syncEpoch;
    private int measureEpoch;
    private int aktoSaveEpoch;
    public static final String AKTO_SAVE_EPOCH = "aktoSaveEpoch";

    // Constructors
    public UsageMetric() { }

    public UsageMetric(String organizationId, int accountId, MetricTypes metricType, int syncEpoch, int measureEpoch, String dashboardMode, String dashboardVersion) {
        this.organizationId = organizationId;
        this.accountId = accountId;
        this.metricType = metricType;
        this.dashboardMode = dashboardMode;
        this.dashboardVersion = dashboardVersion;
        this.syncedWithAkto = false;
        this.syncEpoch = syncEpoch;
        this.measureEpoch = measureEpoch;

        if (syncEpoch == -1) {
            this.minutesSinceLastSync = -1;
        } else {
            this.minutesSinceLastSync = (Context.now() - syncEpoch) / 60;
        }
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public MetricTypes getMetricType() {
        return metricType;
    }

    public void setMetricType(MetricTypes metricType) {
        this.metricType = metricType;
    }

    public int getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(int recordedAt) {
        this.recordedAt = recordedAt;
    }

    public int getUsage() {
        return usage;
    }

    public void setUsage(int usage) {
        this.usage = usage;
    }

    public String getDashboardMode() {
        return dashboardMode;
    }

    public void setDashboardMode(String dashboardMode) {
        this.dashboardMode = dashboardMode;
    }

    public String getDashboardVersion() {
        return dashboardVersion;
    }

    public void setDashboardVersion(String dashboardVersion) {
        this.dashboardVersion = dashboardVersion;
    }

    public int getMinutesSinceLastSync() {
        return minutesSinceLastSync;
    }

    public void setMinutesSinceLastSync(int minutesSinceLastSync) {
        this.minutesSinceLastSync = minutesSinceLastSync;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public boolean isSyncedWithAkto() {
        return syncedWithAkto;
    }

    public void setSyncedWithAkto(boolean syncedWithAkto) {
        this.syncedWithAkto = syncedWithAkto;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getSyncEpoch() {
        return syncEpoch;
    }

    public void setSyncEpoch(int syncEpoch) {
        this.syncEpoch = syncEpoch;
    }

    public int getMeasureEpoch() {
        return measureEpoch;
    }

    public void setMeasureEpoch(int measureEpoch) {
        this.measureEpoch = measureEpoch;
    }

    public int getAktoSaveEpoch() {
        return aktoSaveEpoch;
    }

    public void setAktoSaveEpoch(int aktoSaveEpoch) {
        this.aktoSaveEpoch = aktoSaveEpoch;
    }

    public void calculateUsage() {
        Context.accountId.set(accountId);
        recordedAt = Context.now();

        switch (metricType) {
            case ACTIVE_ENDPOINTS:
                usage = calculateActiveEndpoints();
                break;
            case CUSTOM_TESTS:
                usage = calculateCustomTests();
                break;
            case TEST_RUNS:
                usage = calculateTestRuns();
                break;
            case ACTIVE_ACCOUNTS:
                usage = calculateActiveAccounts();
                break;
            default:
                this.usage = 0;
        }
    }

    private int calculateActiveEndpoints() {
        /* todo:
         * Active endpoints def - endpoints which have been tested
         */
        int activeEndpoints = (int) ApiInfoDao.instance.count(
            Filters.gt(ApiInfo.LAST_SEEN, measureEpoch)
        );
        
        return activeEndpoints;
    }

    public int calculateCustomTests() {
        int customTemplates = (int) YamlTemplateDao.instance.count(
            Filters.eq(YamlTemplate.SOURCE, YamlTemplateSource.CUSTOM)
        );
        return customTemplates;
    }

    public int calculateTestRuns() {
        /*
         * Compute metric according to definition
         */
        int testRuns = (int) TestingRunResultDao.instance.count(new BasicDBObject());
        return testRuns;
    }

    public int calculateActiveAccounts() {

        // Get organization
        Organization organization = OrganizationsDao.instance.findOne(
            Filters.eq(Organization.ID, organizationId)
        );

        if (organization == null) {
            return -1;
        }

        String adminEmail = organization.getAdminEmail();

        // Get admin user id
        User admin = UsersDao.instance.findOne(
            Filters.eq(User.LOGIN, adminEmail)
        );

        if (admin == null) {
            return -1;
        }

        int adminUserId = admin.getId();

        // Get accounts belonging to organization
        Set<Integer> accounts = Organization.findAccountsBelongingToOrganization(adminUserId);

        return accounts.size();
    }

    public static void syncWithAkto(UsageMetric usageMetric) {
        Gson gson = new Gson();
        Map<String, UsageMetric> wrapper = new HashMap<>();
        wrapper.put("usageMetric", usageMetric);
        String json = gson.toJson(wrapper);

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(UsageUtils.getUsageServiceUrl() + "/api/ingestUsage") 
                .post(body)
                .build();

        OkHttpClient client = new OkHttpClient();
        Response response = null;
                
        try {
            response = client.newCall(request).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            
            UsageMetricsDao.instance.updateOne(
                Filters.eq(UsageMetric.ID, usageMetric.getId()), 
                Updates.set(UsageMetric.SYNCED_WITH_AKTO, true)
            );

            UsageMetricInfoDao.instance.updateOne(
                Filters.and(
                    Filters.eq(UsageMetricInfo.ORGANIZATION_ID, usageMetric.getOrganizationId()),
                    Filters.eq(UsageMetricInfo.ACCOUNT_ID, usageMetric.getAccountId()),
                    Filters.eq(UsageMetricInfo.METRIC_TYPE, usageMetric.getMetricType())
                ), 
                Updates.set(UsageMetricInfo.SYNC_EPOCH, Context.now())
            );
        } catch (IOException e) {
            System.out.println("Failed to sync usage metric with Akto. Error - " +  e.getMessage());
        }
        finally {
            if (response != null) {
                response.close(); // Manually close the response body
            }
        }
    }

    public static void syncWithMixpanel(UsageMetric usageMetric) {
        /*
         * event name - metricType
         * distinct_id - organization_id_account_id
         * 
         * this.organizationId = organizationId;
         * this.accountId = accountId;
         * this.metricType = metricType;
         * this.dashboardMode = dashboardMode;
         * this.dashboardVersion = dashboardVersion;
         * usage
         * 
         * organization name
         */
    }
}
