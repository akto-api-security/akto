package com.akto.listener;

import static com.akto.dto.AccountSettings.defaultTrafficAlertThresholdSeconds;
import static com.akto.runtime.RuntimeUtil.matchesDefaultPayload;
import static com.akto.task.Cluster.callDibs;
import static com.akto.utils.Utils.deleteApis;
import static com.akto.utils.billing.OrganizationUtils.syncOrganizationWithAkto;
import static com.mongodb.client.model.Filters.eq;

import com.akto.dao.metrics.MetricDataDao;
import com.akto.dto.jobs.JobExecutorType;
import com.akto.utils.crons.JobsCron;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletContextListener;

import com.akto.dao.testing.*;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import com.akto.DaoInit;
import com.akto.action.AdminSettingsAction;
import com.akto.action.ApiCollectionsAction;
import com.akto.action.CustomDataTypeAction;
import com.akto.action.EventMetricsAction;
import com.akto.action.observe.InventoryAction;
import com.akto.action.settings.AdvancedTrafficFiltersAction;
import com.akto.action.testing.StartTestAction;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AccountsContextDao;
import com.akto.dao.AccountsDao;
import com.akto.dao.ActivitiesDao;
import com.akto.dao.AktoDataTypeDao;
import com.akto.dao.AnalyserLogsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.ApiTokensDao;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.BackwardCompatibilityDao;
import com.akto.dao.BillingLogsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.CustomDataTypeDao;
import com.akto.dao.DashboardLogsDao;
import com.akto.dao.DataIngestionLogsDao;
import com.akto.dao.DependencyFlowNodesDao;
import com.akto.dao.DependencyNodeDao;
import com.akto.dao.FilterSampleDataDao;
import com.akto.dao.LogsDao;
import com.akto.dao.MCollection;
import com.akto.dao.ProtectionLogsDao;
import com.akto.dao.PupeteerLogsDao;
import com.akto.dao.RBACDao;
import com.akto.dao.RuntimeLogsDao;
import com.akto.dao.SSOConfigsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SensitiveSampleDataDao;
import com.akto.dao.SetupDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.SuspectSampleDataDao;
import com.akto.dao.UsersDao;
import com.akto.dao.MaliciousEventDao;
import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.loaders.LoadersDao;
import com.akto.dao.monitoring.FilterYamlTemplateDao;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.notifications.EventsMetricsDao;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dao.pii.PIISourceDao;
import com.akto.dao.runtime_filters.AdvancedTrafficFiltersDao;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.traffic_metrics.RuntimeMetricsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dao.upload.FileUploadLogsDao;
import com.akto.dao.upload.FileUploadsDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.AktoDataType;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.ApiInfo;
import com.akto.dto.BackwardCompatibility;
import com.akto.dto.Config;
import com.akto.dto.Config.AzureConfig;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.Config.OktaConfig;
import com.akto.dto.CustomDataType;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.IgnoreData;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.TelemetrySettings;
import com.akto.dto.User;
import com.akto.dto.User.AktoUIMode;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Conditions.Operator;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.dependency_flow.DependencyFlow;
import com.akto.dto.events.EventsExample;
import com.akto.dto.events.EventsMetrics;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhook.ActiveStatus;
import com.akto.dto.notifications.CustomWebhook.WebhookOptions;
import com.akto.dto.notifications.CustomWebhook.WebhookType;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.pii.PIISource;
import com.akto.dto.pii.PIIType;
import com.akto.dto.settings.DefaultPayload;
import com.akto.dto.sso.SAMLConfig;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.TestLibrary;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.AllTestingEndpoints;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.ComplianceInfo;
import com.akto.dto.testing.ComplianceMapping;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.RegexTestingEndpoints;
import com.akto.dto.testing.Remediation;
import com.akto.dto.testing.RiskScoreTestingEndpoints;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingRunResultSummary;
import com.akto.dto.testing.custom_groups.AllAPIsGroup;
import com.akto.dto.testing.custom_groups.UnauthenticatedEndpoint;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.upload.FileUpload;
import com.akto.dto.usage.UsageMetric;
import com.akto.log.CacheLoggerMaker;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.notifications.TrafficUpdates;
import com.akto.notifications.TrafficUpdates.AlertResult;
import com.akto.notifications.TrafficUpdates.AlertType;
import com.akto.notifications.slack.DailyUpdate;
import com.akto.notifications.slack.TestSummaryGenerator;
import com.akto.notifications.webhook.WebhookSender;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.RuntimeUtil;
import com.akto.stigg.StiggReporterClient;
import com.akto.task.Cluster;
import com.akto.telemetry.TelemetryJob;
import com.akto.testing.ApiExecutor;
import com.akto.testing.HostDNSLookup;
import com.akto.testing.TemplateMapper;
import com.akto.testing.workflow_node_executor.Utils;
import com.akto.usage.UsageMetricCalculator;
import com.akto.usage.UsageMetricHandler;
import com.akto.util.AccountTask;
import com.akto.util.ConnectionInfo;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.DbMode;
import com.akto.util.EmailAccountName;
import com.akto.util.IntercomEventsUtil;
import com.akto.util.JSONUtils;
import com.akto.util.Pair;
import com.akto.util.UsageUtils;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TemplatePlan;
import com.akto.util.enums.GlobalEnums.TestCategory;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.akto.util.filter.DictionaryFilter;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.util.tasks.OrganizationTask;
import com.akto.utils.Auth0;
import com.akto.utils.AutomatedApiGroupsUtils;
import com.akto.utils.RSAKeyPairUtils;
import com.akto.utils.TestTemplateUtils;
import com.akto.utils.billing.OrganizationUtils;
import com.akto.utils.crons.Crons;
import com.akto.utils.crons.SyncCron;
import com.akto.utils.crons.TokenGeneratorCron;
import com.akto.utils.crons.UpdateSensitiveInfoInApiInfo;
import com.akto.utils.jobs.CleanInventory;
import com.akto.utils.jobs.DeactivateCollections;
import com.akto.utils.jobs.JobUtils;
import com.akto.utils.jobs.MatchingJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.slack.api.Slack;
import com.slack.api.util.http.SlackHttpClient;
import com.slack.api.webhook.WebhookResponse;

import io.intercom.api.Intercom;
import okhttp3.OkHttpClient;

public class InitializerListener implements ServletContextListener {

    private static final LoggerMaker logger = new LoggerMaker(InitializerListener.class, LogDb.DASHBOARD);

    private static final CacheLoggerMaker cacheLoggerMaker = new CacheLoggerMaker(InitializerListener.class);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static final boolean isSaas = "true".equals(System.getenv("IS_SAAS"));

    private static final int THREE_HOURS = 3*60*60;
    private static final int CONNECTION_TIMEOUT = 10 * 1000;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    public static String aktoVersion;
    private final ScheduledExecutorService telemetryExecutorService = Executors.newSingleThreadScheduledExecutor();

    public static boolean connectedToMongo = false;
    
    SyncCron syncCronInfo = new SyncCron();
    TokenGeneratorCron tokenGeneratorCron = new TokenGeneratorCron();
    UpdateSensitiveInfoInApiInfo updateSensitiveInfoInApiInfo = new UpdateSensitiveInfoInApiInfo();

    private static String domain = null;
    public static String subdomain = "https://app.akto.io";

    private static Map<String, String> piiFileMap;
    Crons crons = new Crons();

    public static String getDomain() {
        if (domain == null) {
            if (true) {
                domain = "https://staging.akto.io:8443";
            } else {
                domain = "http://localhost:8080";
            }
        }

        return domain;
    }

    private static boolean downloadFileCheck(String filePath){
        try {
            FileTime fileTime = Files.getLastModifiedTime(new File(filePath).toPath());
            if(fileTime.toMillis()/1000l >= (Context.now()-THREE_HOURS)){
                return false;
            }
        } catch (Exception e){
            return true;
        }
        return true;
    }

    private void setUpTrafficAlertScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            Set<Integer> deactivatedCollections = UsageMetricCalculator.getDeactivatedLatest();

                            List<String> deactivatedHosts = new ArrayList<>();
                            if (deactivatedCollections != null && !deactivatedCollections.isEmpty()) {
                                List<ApiCollection> metaForIds = ApiCollectionsDao.instance.getMetaForIds(new ArrayList<>(deactivatedCollections));
                                for (ApiCollection apiCollection: metaForIds) {
                                    String host = apiCollection.getHostName();
                                    if (host != null) deactivatedHosts.add(host);
                                }
                            }

                            // look back period 6 days
                            logger.debugAndAddToDb("starting traffic alert scheduler", LoggerMaker.LogDb.DASHBOARD);
                            TrafficUpdates trafficUpdates = new TrafficUpdates(60*60*24*6);
                            trafficUpdates.populate(deactivatedHosts);

                            boolean slackWebhookFound = true;
                            List<SlackWebhook> listWebhooks = SlackWebhooksDao.instance.findAll(new BasicDBObject());
                            if (listWebhooks == null || listWebhooks.isEmpty()) {
                                logger.debugAndAddToDb("No slack webhooks found", LogDb.DASHBOARD);
                                slackWebhookFound = false;
                            }

                            boolean teamsWebhooksFound = true;
                            List<CustomWebhook> teamsWebhooks = CustomWebhooksDao.instance.findAll(
                                    Filters.and(
                                            Filters.eq(
                                                    CustomWebhook.WEBHOOK_TYPE,
                                                    CustomWebhook.WebhookType.MICROSOFT_TEAMS.name()),
                                            Filters.in(CustomWebhook.SELECTED_WEBHOOK_OPTIONS,
                                                    CustomWebhook.WebhookOptions.TRAFFIC_ALERTS.name())));

                            if (teamsWebhooks == null || teamsWebhooks.isEmpty()) {
                                logger.debugAndAddToDb("No teams webhooks found", LogDb.DASHBOARD);
                                teamsWebhooksFound = false;
                            }

                            if (!(slackWebhookFound || teamsWebhooksFound)) {
                                return;
                            }

                            int thresholdSeconds = defaultTrafficAlertThresholdSeconds;
                            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                            if (accountSettings != null) {
                                // override with user supplied value
                                thresholdSeconds = accountSettings.getTrafficAlertThresholdSeconds();
                            }

                            logger.debugAndAddToDb("threshold seconds: " + thresholdSeconds, LoggerMaker.LogDb.DASHBOARD);

                            if (thresholdSeconds > 0) {
                                Map<AlertType, AlertResult> alertMap = trafficUpdates.createAlerts(thresholdSeconds, deactivatedHosts);
                                if (slackWebhookFound && listWebhooks != null && !listWebhooks.isEmpty()) {
                                    SlackWebhook webhook = listWebhooks.get(0);
                                    logger.debugAndAddToDb("Slack Webhook found: " + webhook.getWebhook(),
                                            LogDb.DASHBOARD);
                                    trafficUpdates.sendSlackAlerts(webhook.getWebhook(),
                                            getMetricsUrl(webhook.getDashboardUrl()), thresholdSeconds,
                                            alertMap);
                                }

                                if (teamsWebhooksFound && teamsWebhooks != null && !teamsWebhooks.isEmpty()) {
                                    for (CustomWebhook webhook : teamsWebhooks) {
                                        trafficUpdates.sendTeamsAlerts(webhook,
                                        getMetricsUrl(webhook.getDashboardUrl()),
                                                thresholdSeconds, alertMap);
                                    }
                                }
                                trafficUpdates.updateAlertSentTs(alertMap);
                            }
                        } catch (Exception e) {
                            logger.errorAndAddToDb(e,"Error while running traffic alerts: " + e.getMessage(), LogDb.DASHBOARD);
                        }
                    }
                }, "traffic-alerts-scheduler");
            }
        }, 0, 4, TimeUnit.HOURS);
    }

    private static String getMetricsUrl(String ogUrl){
        return ogUrl + "/dashboard/settings/metrics";
    }

    public void setUpAktoMixpanelEndpointsScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            raiseMixpanelEvent();

                        } catch (Exception e) {
                        }
                    }
                }, "akto-mixpanel-endpoints-scheduler");
            }
        }, 0, 4, TimeUnit.HOURS);
    }

    public void updateApiGroupsForAccounts() {
        List<Integer> accounts = new ArrayList<>(Arrays.asList(1_000_000, 1718042191, 1664578207, 1693004074, 1685916748, 1736798101));
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                for (int account : accounts) {
                    Context.accountId.set(account);
                    createFirstUnauthenticatedApiGroup();
                }
            }
        }, 0, 4, TimeUnit.HOURS);
    }

    private static void raiseMixpanelEvent() {

        int now = Context.now();
        List<BasicDBObject> totalEndpoints = new InventoryAction().fetchRecentEndpoints(0, now);
        List<BasicDBObject> newEndpoints  = new InventoryAction().fetchRecentEndpoints(now - 604800, now);

        DashboardMode dashboardMode = DashboardMode.getDashboardMode();        

        RBAC record = RBACDao.instance.findOne("role", Role.ADMIN.name());

        if (record == null) {
            return;
        }

        BasicDBObject mentionedUser = UsersDao.instance.getUserInfo(record.getUserId());
        String userEmail = (String) mentionedUser.get("name");

        String distinct_id = userEmail + "_" + dashboardMode;

        EmailAccountName emailAccountName = new EmailAccountName(userEmail);
        String accountName = emailAccountName.getAccountName();

        JSONObject props = new JSONObject();
        props.put("Email ID", userEmail);
        props.put("Dashboard Mode", dashboardMode);
        props.put("Account Name", accountName);
        props.put("Total Endpoints", totalEndpoints.size());
        props.put("New Endpoints", newEndpoints.size());

        AktoMixpanel aktoMixpanel = new AktoMixpanel();
        aktoMixpanel.sendEvent(distinct_id, "Endpoints Populated", props);

    }
    
    public void setUpPiiAndTestSourcesScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            executePIISourceFetch();
                        } catch (Exception e) {
                            e.printStackTrace();
                            logger.errorAndAddToDb("Error in fetching PII sources: " +  e.getMessage());
                        }
                        /*
                         * This job updates the test templates based on data types.
                         * Since this may cause a significant load on db on first release,
                         * commenting this.
                         * TODO: uncomment this later.
                         */
                        // try {
                        //     executeDataTypeToTemplate();
                        // } catch (Exception e) {
                        //     logger.errorAndAddToDb(e, "Error in executeDataTypeToTemplate " + e.toString());
                        // }
                    }
                }, "pii-scheduler");
            }
        }, 0, 4, TimeUnit.HOURS);
    }

    public static void executeDataTypeToTemplate() {
        TemplateMapper mapper = new TemplateMapper();
        int accountId = Context.accountId.get();
        for (Entry<String, CustomDataType> dataType : SingleTypeInfo.getCustomDataTypeMap(accountId).entrySet()) {
            mapper.createTestTemplateForCustomDataType(dataType.getValue());
        }
        for (Entry<String, AktoDataType> dataType : SingleTypeInfo.getAktoDataTypeMap(accountId).entrySet()) {
            mapper.createTestTemplateForAktoDataType(dataType.getValue());
        }
    }

    static TestCategory findTestCategory(String path, Map<String, TestCategory> shortNameToTestCategory) {
        path = path.replaceAll("-", "").replaceAll("_", "").toLowerCase();
        return shortNameToTestCategory.getOrDefault(path, TestCategory.UC);
    }

    static String findTestSubcategory(String path) {
        String parentPath = path.substring(0, path.lastIndexOf("/"));
        return parentPath.substring(parentPath.lastIndexOf("/") + 1);
    }

    static void executePiiCleaner(Set<Integer> whiteListCollectionSet) {
        final int BATCH_SIZE = 100;
        int currMarker = 0;
        Bson filterSsdQ =
                Filters.and(
                        Filters.ne("_id.responseCode", -1),
                        Filters.eq("_id.isHeader", false)
                );

        MongoCursor<SensitiveSampleData> cursor = null;
        int dataPoints = 0;
        List<SingleTypeInfo.ParamId> idsToDelete = new ArrayList<>();
        do {
            idsToDelete = new ArrayList<>();
            cursor = SensitiveSampleDataDao.instance.getMCollection().find(filterSsdQ).projection(Projections.exclude(SensitiveSampleData.SAMPLE_DATA)).skip(currMarker).limit(BATCH_SIZE).cursor();
            currMarker += BATCH_SIZE;
            dataPoints = 0;
            logger.debugAndAddToDb("processing batch: " + currMarker, LogDb.DASHBOARD);
            while(cursor.hasNext()) {
                SensitiveSampleData ssd = cursor.next();
                SingleTypeInfo.ParamId ssdId = ssd.getId();
                Bson filterCommonSampleData =
                        Filters.and(
                                Filters.eq("_id.method", ssdId.getMethod()),
                                Filters.eq("_id.url", ssdId.getUrl()),
                                Filters.eq("_id.apiCollectionId", ssdId.getApiCollectionId())
                        );


                SampleData commonSampleData = SampleDataDao.instance.findOne(filterCommonSampleData);
                if (commonSampleData == null) {
                    continue;
                }
                List<String> commonPayloads = commonSampleData.getSamples();

                if (!isSimilar(ssdId.getParam(), commonPayloads)) {
                    idsToDelete.add(ssdId);
                }

                dataPoints++;
            }

            bulkSensitiveInvalidate(idsToDelete, whiteListCollectionSet);
            bulkSingleTypeInfoDelete(idsToDelete, whiteListCollectionSet);

        } while (dataPoints == BATCH_SIZE);
    }

    private static void bulkSensitiveInvalidate(List<SingleTypeInfo.ParamId> idsToDelete, Set<Integer> whiteListCollectionSet) {
        ArrayList<WriteModel<SensitiveSampleData>> bulkSensitiveInvalidateUpdates = new ArrayList<>();
        for(SingleTypeInfo.ParamId paramId: idsToDelete) {
            String paramStr = "PII cleaner - invalidating: " + paramId.getApiCollectionId() + ": " + paramId.getMethod() + " " + paramId.getUrl() + " > " + paramId.getParam();
            String url = "dashboard/observe/inventory/"+paramId.getApiCollectionId()+"/"+Base64.getEncoder().encodeToString((paramId.getUrl() + " " + paramId.getMethod()).getBytes());
            logger.debugAndAddToDb(paramStr + url, LogDb.DASHBOARD);

            if (!whiteListCollectionSet.contains(paramId.getApiCollectionId())) continue;

            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.eq("url", paramId.getUrl()));
            filters.add(Filters.eq("method", paramId.getMethod()));
            filters.add(Filters.eq("responseCode", paramId.getResponseCode()));
            filters.add(Filters.eq("isHeader", paramId.getIsHeader()));
            filters.add(Filters.eq("param", paramId.getParam()));
            filters.add(Filters.eq("apiCollectionId", paramId.getApiCollectionId()));

            bulkSensitiveInvalidateUpdates.add(new UpdateOneModel<>(Filters.and(filters), Updates.set("invalid", true)));
        }

        if (!bulkSensitiveInvalidateUpdates.isEmpty()) {
            BulkWriteResult bwr =
                    SensitiveSampleDataDao.instance.getMCollection().bulkWrite(bulkSensitiveInvalidateUpdates, new BulkWriteOptions().ordered(false));

            logger.debugAndAddToDb("PII cleaner - modified " + bwr.getModifiedCount() + " from STI", LogDb.DASHBOARD);
        }

    }

    private static void bulkSingleTypeInfoDelete(List<SingleTypeInfo.ParamId> idsToDelete, Set<Integer> whiteListCollectionSet) {
        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSingleTypeInfo = new ArrayList<>();
        for(SingleTypeInfo.ParamId paramId: idsToDelete) {
            String paramStr = "PII cleaner - deleting: " + paramId.getApiCollectionId() + ": " + paramId.getMethod() + " " + paramId.getUrl() + " > " + paramId.getParam();
            logger.debugAndAddToDb(paramStr, LogDb.DASHBOARD);

            if (!whiteListCollectionSet.contains(paramId.getApiCollectionId())) continue;

            List<Bson> filters = new ArrayList<>();
            filters.add(Filters.eq("url", paramId.getUrl()));
            filters.add(Filters.eq("method", paramId.getMethod()));
            filters.add(Filters.eq("responseCode", paramId.getResponseCode()));
            filters.add(Filters.eq("isHeader", paramId.getIsHeader()));
            filters.add(Filters.eq("param", paramId.getParam()));
            filters.add(Filters.eq("apiCollectionId", paramId.getApiCollectionId()));

            bulkUpdatesForSingleTypeInfo.add(new DeleteOneModel<>(Filters.and(filters)));
        }

        if (!bulkUpdatesForSingleTypeInfo.isEmpty()) {
            BulkWriteResult bwr =
                    SingleTypeInfoDao.instance.getMCollection().bulkWrite(bulkUpdatesForSingleTypeInfo, new BulkWriteOptions().ordered(false));

            logger.debugAndAddToDb("PII cleaner - deleted " + bwr.getDeletedCount() + " from STI", LogDb.DASHBOARD);
        }

    }

    private static final Gson gson = new Gson();

    private static BasicDBObject extractJsonResponse(String message) {
        Map<String, Object> json = gson.fromJson(message, Map.class);

        String respPayload = (String) json.get("responsePayload");

        if (respPayload == null || respPayload.isEmpty()) {
            respPayload = "{}";
        }

        if(respPayload.startsWith("[")) {
            respPayload = "{\"json\": "+respPayload+"}";
        }

        BasicDBObject payload;
        try {
            payload = BasicDBObject.parse(respPayload);
        } catch (Exception e) {
            payload = BasicDBObject.parse("{}");
        }

        return payload;
    }

    private static boolean isSimilar(String param, List<String> commonPayloads) {
        for(String commonPayload: commonPayloads) {
//            if (commonPayload.equals(sensitivePayload)) {
//                continue;
//            }

            BasicDBObject commonPayloadObj = extractJsonResponse(commonPayload);
            if (JSONUtils.flatten(commonPayloadObj).containsKey(param)) {
                return true;
            }
        }

        return false;
    }

    private static String loadPIIFileFromResources(String fileUrl){
        String fileName = piiFileMap.get(fileUrl);
        if(fileName == null){
            logger.errorAndAddToDb("Unable to find file locally: " + fileUrl, LogDb.DASHBOARD);
            return null;
        }
        try {
            return convertStreamToString(InitializerListener.class.getResourceAsStream("/" + fileName));
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Exception while reading content locally: " + fileUrl, LogDb.DASHBOARD);
            return null;
        }
    }

    private static String fetchPIIFile(PIISource piiSource){
        String fileUrl = piiSource.getFileUrl();
        String id = piiSource.getId();
        String tempFileUrl = "temp_" + id;
        try {
            if (fileUrl.startsWith("http")) {
                if (downloadFileCheck(tempFileUrl)) {
                    FileUtils.copyURLToFile(new URL(fileUrl), new File(tempFileUrl), CONNECTION_TIMEOUT, CONNECTION_TIMEOUT);
                }
                fileUrl = tempFileUrl;
            }
            return FileUtils.readFileToString(new File(fileUrl), StandardCharsets.UTF_8);
        } catch (Exception e){
            e.printStackTrace();
            logger.errorAndAddToDb(e, String.format("failed to fetch PII file %s from github, trying locally", piiSource.getFileUrl()), LogDb.DASHBOARD);
            return loadPIIFileFromResources(piiSource.getFileUrl());
        }
    }

    public static void executePIISourceFetch() {
        List<PIISource> piiSources = PIISourceDao.instance.findAll("active", true);
        Map<String, CustomDataType> customDataTypesMap = new HashMap<>();
        logger.debugAndAddToDb("logging pii source size " + piiSources.size());
        for (PIISource piiSource : piiSources) {
            String id = piiSource.getId();
            logger.debugAndAddToDb("pii source id " + id);
            Map<String, PIIType> currTypes = piiSource.getMapNameToPIIType();
            if (currTypes == null) {
                currTypes = new HashMap<>();
            }

            String fileContent = fetchPIIFile(piiSource);
            if (fileContent == null) {
                logger.errorAndAddToDb("Failed to load file from github as well as resources: " + piiSource.getFileUrl(), LogDb.DASHBOARD);
                continue;
            }
            BasicDBObject fileObj = BasicDBObject.parse(fileContent);
            BasicDBList dataTypes = (BasicDBList) (fileObj.get("types"));
            Bson findQ = Filters.eq("_id", id);

            if(customDataTypesMap.isEmpty()){
                List<CustomDataType> customDataTypes = CustomDataTypeDao.instance.findAll(new BasicDBObject());
                for (CustomDataType customDataType : customDataTypes) {
                    customDataTypesMap.put(customDataType.getName(), customDataType);
                }
            }
            logger.debugAndAddToDb("customDataTypesMap size " + customDataTypesMap.size());

            List<Bson> piiUpdates = new ArrayList<>();

            for (Object dtObj : dataTypes) {
                BasicDBObject dt = (BasicDBObject) dtObj;
                String piiKey = dt.getString("name").toUpperCase();
                PIIType piiType = new PIIType(
                        piiKey,
                        dt.getBoolean("sensitive"),
                        dt.getString("regexPattern"),
                        dt.getBoolean("onKey"),
                        dt.getString("regexPatternOnValue"),
                        dt.getBoolean("onKeyAndPayload")
                );

                CustomDataType existingCDT = customDataTypesMap.getOrDefault(piiKey, null);
                CustomDataType newCDT = getCustomDataTypeFromPiiType(piiSource, piiType, false);

                boolean hasNotChangedCondition = currTypes.containsKey(piiKey) &&
                        (currTypes.get(piiKey).equals(piiType) && dt.getBoolean(PIISource.ACTIVE, true)) &&
                        (existingCDT != null && existingCDT.getDataTypePriority() != null)
                        && (existingCDT.getCategoriesList() != null && !existingCDT.getCategoriesList().isEmpty())
                        && existingCDT.systemFieldsCompare(newCDT);

                boolean userHasChangedCondition = false;
                if(existingCDT != null && existingCDT.getUserModifiedTimestamp() > 0){
                    userHasChangedCondition = true;
                }
                
                if (userHasChangedCondition || hasNotChangedCondition) {
                    continue;
                } else {
                    logger.debugAndAddToDb("found different " + piiType.getName());
                    Severity dtSeverity = null;
                    List<String> categoriesList = null;
                    categoriesList = (List<String>) dt.get(AktoDataType.TAGS_LIST);
                    if(dt.getString(AktoDataType.DATA_TYPE_PRIORITY) != null){
                        try {
                            dtSeverity = Severity.valueOf(dt.getString(AktoDataType.DATA_TYPE_PRIORITY));
                            newCDT.setDataTypePriority(dtSeverity);
                           
                        } catch (Exception e) {
                            e.printStackTrace();
                            logger.errorAndAddToDb("Invalid severity of dataType " + piiKey);
                        }
                        
                    }
                    if(categoriesList != null){
                        newCDT.setCategoriesList(categoriesList);
                    }
                    if (!dt.getBoolean(PIISource.ACTIVE, true)) {
                        if (currTypes.getOrDefault(piiKey, null) != null || piiSource.getLastSynced() == 0) {
                            piiUpdates.add(Updates.unset(PIISource.MAP_NAME_TO_PII_TYPE + "." + piiKey));
                        }
                    } else {
                        if (currTypes.getOrDefault(piiKey, null) != piiType || piiSource.getLastSynced() == 0) {
                            piiUpdates.add(Updates.set(PIISource.MAP_NAME_TO_PII_TYPE + "." + piiKey, piiType));
                        }
                        newCDT.setActive(true);
                    }

                    if (existingCDT == null) {
                        logger.debugAndAddToDb("inserting different " + piiType.getName());
                        CustomDataTypeDao.instance.insertOne(newCDT);
                    } else {
                        logger.debugAndAddToDb("updating different " + piiType.getName());
                        List<Bson> updates = getCustomDataTypeUpdates(existingCDT, newCDT);
                        if (!updates.isEmpty()) {
                            CustomDataTypeDao.instance.updateOne(
                                    Filters.eq(CustomDataType.NAME, piiKey),
                                    Updates.combine(updates)
                            );
                        }
                    }
                }

            }

            if (!piiUpdates.isEmpty()) {
                piiUpdates.add(Updates.set(PIISource.LAST_SYNCED, Context.now()));
                PIISourceDao.instance.updateOne(findQ, Updates.combine(piiUpdates));
            }

        }
    }

    private static List<Bson> getCustomDataTypeUpdates(CustomDataType existingCDT, CustomDataType newCDT){
        List<Bson> ret = new ArrayList<>();
        Severity oldSeverity = existingCDT.getDataTypePriority();
        Severity newSeverity = newCDT.getDataTypePriority();

        if(!Conditions.areEqual(existingCDT.getKeyConditions(), newCDT.getKeyConditions())){
            ret.add(Updates.set(CustomDataType.KEY_CONDITIONS, newCDT.getKeyConditions()));
        }
        if(!Conditions.areEqual(existingCDT.getValueConditions(), newCDT.getValueConditions())){
            ret.add(Updates.set(CustomDataType.VALUE_CONDITIONS, newCDT.getValueConditions()));
        }
        if(existingCDT.getOperator()!=newCDT.getOperator()){
            ret.add(Updates.set(CustomDataType.OPERATOR, newCDT.getOperator()));
        }
        if(newSeverity != null && (oldSeverity == null || (!oldSeverity.equals(newSeverity)))){
            ret.add(
                Updates.set(AktoDataType.DATA_TYPE_PRIORITY, newCDT.getDataTypePriority())
            );
        }
        if((existingCDT.getCategoriesList() == null || existingCDT.getCategoriesList().isEmpty()) && newCDT.getCategoriesList() != null){
            ret.add(
                Updates.set(AktoDataType.CATEGORIES_LIST, newCDT.getCategoriesList())
            );
        }

        if (!ret.isEmpty()) {
            ret.add(Updates.set(CustomDataType.TIMESTAMP, Context.now()));
        }

        return ret;
    }

    private static CustomDataType getCustomDataTypeFromPiiType(PIISource piiSource, PIIType piiType, Boolean active) {
        String piiKey = piiType.getName();

        List<Predicate> keyPredicates = new ArrayList<>();
        Conditions keyConditions = new Conditions(keyPredicates, Operator.OR);

        if(piiType.getOnKey() || piiType.getOnKeyAndPayload()){
            keyPredicates.add(new RegexPredicate(piiType.getRegexPattern()));
        }

        List<Predicate> valuePredicates = new ArrayList<>();
        Conditions valueConditions = new Conditions(valuePredicates, Operator.OR);
        if(!piiType.getOnKey() || piiType.getOnKeyAndPayload()){
            String valuePattern = piiType.getOnKeyAndPayload() ? piiType.getRegexPatternOnValue() : piiType.getRegexPattern();
            valuePredicates.add(new RegexPredicate(valuePattern));
        }

        IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
        CustomDataType ret = new CustomDataType(
                piiKey,
                piiType.getIsSensitive(),
                Collections.emptyList(),
                piiSource.getAddedByUser(),
                active,
                ((piiType.getOnKey() || piiType.getOnKeyAndPayload()) ? keyConditions : null),
                ((piiType.getOnKey() && !piiType.getOnKeyAndPayload()) ? null : valueConditions),
                piiType.getOnKeyAndPayload() ? Operator.AND : Operator.OR,
                ignoreData,
                false,
                true
        );

        return ret;
    }
    private void cleanInventoryJobRunner() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        boolean detectRedundantUrls = accountSettings.getAllowFilterLogs();
                        String shouldFilterApisFromYaml = System.getenv("DETECT_REDUNDANT_APIS_RETRO");
                        String shouldFilterOptionsAndHTMLApis = System.getenv("DETECT_OPTION_APIS_RETRO");

                        String shouldDelete = System.getenv("DELETE_REDUNDANT_APIS");
                        boolean shouldDeleteApis = accountSettings.getAllowDeletionOfUrls() || (shouldDelete != null && shouldDelete.equalsIgnoreCase("true"));

                        if(!detectRedundantUrls && shouldFilterApisFromYaml == null && shouldFilterOptionsAndHTMLApis == null){
                            return;
                        }
                        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(Filters.empty(),
                                Projections.include(Constants.ID, ApiCollection.NAME, ApiCollection.HOST_NAME));

                        String filePath = "./samples_"+account.getId()+".txt";

                        if((detectRedundantUrls || shouldFilterApisFromYaml != null && shouldFilterApisFromYaml.equalsIgnoreCase("true"))){
                            List<YamlTemplate> yamlTemplates = AdvancedTrafficFiltersDao.instance.findAll(
                                Filters.ne(YamlTemplate.INACTIVE, true)
                            );
                            List<String> redundantUrlList = accountSettings.getAllowRedundantEndpointsList();
                            try {
                                CleanInventory.cleanFilteredSampleDataFromAdvancedFilters(apiCollections , yamlTemplates, redundantUrlList,filePath, shouldDeleteApis, false);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        if(shouldFilterOptionsAndHTMLApis != null && shouldFilterOptionsAndHTMLApis.equalsIgnoreCase("true")){
                            CleanInventory.removeUnnecessaryEndpoints(apiCollections, shouldDeleteApis);
                        }
                    }
                }, "clean-inventory-job");
            }
        }, 0, 2 , TimeUnit.HOURS);
    }

    private void setUpDefaultPayloadRemover() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        Map<String, DefaultPayload> defaultPayloadMap = accountSettings.getDefaultPayloads();
                        if (defaultPayloadMap == null || defaultPayloadMap.isEmpty()) {
                            return;
                        }

                        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.getMetaAll();
                        Map<Integer, ApiCollection> apiCollectionMap = apiCollections.stream().collect(Collectors.toMap(ApiCollection::getId, Function.identity()));

                        for(Map.Entry<String, DefaultPayload> entry: defaultPayloadMap.entrySet()) {
                            DefaultPayload dp = entry.getValue();
                            String base64Url = entry.getKey();
                            if (!dp.getScannedExistingData()) {
                                try {
                                    List<SampleData> sampleDataList = new ArrayList<>();
                                    Bson filters = Filters.empty();
                                    int skip = 0;
                                    int limit = 100;
                                    Bson sort = Sorts.ascending("_id.apiCollectionId", "_id.url", "_id.method");
                                    do {
                                        sampleDataList = SampleDataDao.instance.findAll(filters, skip, limit, sort);
                                        skip += limit;
                                        List<Key> toBeDeleted = new ArrayList<>();
                                        for(SampleData sampleData: sampleDataList) {
                                            try {
                                                List<String> samples = sampleData.getSamples();
                                                ApiCollection apiCollection = apiCollectionMap.get(sampleData.getId().getApiCollectionId());
                                                if (apiCollection == null) continue;
                                                if (apiCollection.getHostName() != null && !dp.getId().equalsIgnoreCase(apiCollection.getHostName())) continue;
                                                if (samples == null || samples.isEmpty()) continue;

                                                boolean allMatchDefault = true;

                                                for (String sample : samples) {
                                                    HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);
                                                    if (!matchesDefaultPayload(httpResponseParams, defaultPayloadMap)) {
                                                        allMatchDefault = false;
                                                        break;
                                                    }
                                                }

                                                if (allMatchDefault) {
                                                    logger.errorAndAddToDb("Deleting API that matches default payload: " + toBeDeleted, LogDb.DASHBOARD);
                                                    toBeDeleted.add(sampleData.getId());
                                                }
                                            } catch (Exception e) {
                                                logger.errorAndAddToDb("Couldn't delete an api for default payload: " + sampleData.getId() + e.getMessage(), LogDb.DASHBOARD);
                                            }
                                        }

                                        deleteApis(toBeDeleted);

                                    } while (!sampleDataList.isEmpty());

                                    Bson completedScan = Updates.set(AccountSettings.DEFAULT_PAYLOADS+"."+base64Url+"."+DefaultPayload.SCANNED_EXISTING_DATA, true);
                                    AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), completedScan);

                                } catch (Exception e) {

                                    logger.errorAndAddToDb("Couldn't complete scan for default payload: " + e.getMessage(), LogDb.DASHBOARD);
                                }
                            }
                        }
                    }
                }, "setUpDefaultPayloadRemover");
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    private void setUpDailyScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    AccountTask.instance.executeTask(new Consumer<Account>() {
                        @Override
                        public void accept(Account t) {
                            List<SlackWebhook> listWebhooks = SlackWebhooksDao.instance.findAll(new BasicDBObject());
                            if (listWebhooks == null || listWebhooks.isEmpty()) {
                                return;
                            }

                    OkHttpClient httpClient = CoreHTTPClient.client.newBuilder().build();
                    SlackHttpClient slackHttpClient = new SlackHttpClient(httpClient);
                    Slack slack = Slack.getInstance(slackHttpClient);

                    for (SlackWebhook slackWebhook : listWebhooks) {
                        int now = Context.now();
                        // logger.debug("debugSlack: " + slackWebhook.getLastSentTimestamp() + " " + slackWebhook.getFrequencyInSeconds() + " " +now );

                                if(slackWebhook.getFrequencyInSeconds()==0) {
                                    slackWebhook.setFrequencyInSeconds(24*60*60);
                                }

                                boolean shouldSend = ( slackWebhook.getLastSentTimestamp() + slackWebhook.getFrequencyInSeconds() ) <= now ;

                                if(!shouldSend){
                                    continue;
                                }

                                logger.debugAndAddToDb(slackWebhook.toString(), LogDb.DASHBOARD);
                                TestSummaryGenerator testSummaryGenerator = new TestSummaryGenerator(Context.accountId.get());
                                String testSummaryPayload = testSummaryGenerator.toJson(slackWebhook.getDashboardUrl());

                                ChangesInfo ci = getChangesInfo(now - slackWebhook.getLastSentTimestamp(), now - slackWebhook.getLastSentTimestamp(), null, null, false);
                                DailyUpdate dailyUpdate = new DailyUpdate(
                                0, 0,
                                ci.newSensitiveParams.size(), ci.newEndpointsLast7Days.size(),
                                ci.recentSentiiveParams, ci.newParamsInExistingEndpoints,
                                slackWebhook.getLastSentTimestamp(), now,
                                ci.newSensitiveParams, slackWebhook.getDashboardUrl());

                                slackWebhook.setLastSentTimestamp(now);
                                SlackWebhooksDao.instance.updateOne(eq("webhook", slackWebhook.getWebhook()), Updates.set("lastSentTimestamp", now));

                                String webhookUrl = slackWebhook.getWebhook();
                                String payload = dailyUpdate.toJSON();
                                logger.debugAndAddToDb(payload, LogDb.DASHBOARD);
                                try {
                                    URI uri = URI.create(webhookUrl);
                                    if (!HostDNSLookup.isRequestValid(uri.getHost())) {
                                        throw new IllegalArgumentException("SSRF attack attempt");
                                    }
                                    logger.debugAndAddToDb("Payload for changes:" + payload, LogDb.DASHBOARD);
                                    WebhookResponse response = slack.send(webhookUrl, payload);
                                    logger.debugAndAddToDb("Changes webhook response: " + response.getBody(), LogDb.DASHBOARD);

                                    // slack testing notification
                                    logger.debugAndAddToDb("Payload for test summary:" + testSummaryPayload, LogDb.DASHBOARD);
                                    response = slack.send(webhookUrl, testSummaryPayload);
                                    logger.debugAndAddToDb("Test summary webhook response: " + response.getBody(), LogDb.DASHBOARD);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                    logger.errorAndAddToDb(e, "Error while sending slack alert: " + e.getMessage(), LogDb.DASHBOARD);
                                }
                            }
                        }
                    }, "setUpDailyScheduler");
                } catch (Exception ex) {
                    ex.printStackTrace(); // or loggger would be better
                }
            }
        }, 0, 4, TimeUnit.HOURS);

    }

    public static void webhookSenderUtil(CustomWebhook webhook) {
        int now = Context.now();

        boolean shouldSend = (webhook.getLastSentTimestamp() + webhook.getFrequencyInSeconds()) <= now;

        if (webhook.getActiveStatus() != ActiveStatus.ACTIVE || !shouldSend) {
            return;
        }

        /*
         * TESTING_RUN_RESULTS type webhooks are
         * triggered only on test complete not periodically.
         * 
         * TRAFFIC_ALERTS type webhooks have a separate cron.
         * PENDING_TESTS_ALERTS type webhooks have a separate job
         */


        if (webhook.getSelectedWebhookOptions() != null &&
                !webhook.getSelectedWebhookOptions().isEmpty()
                && (webhook.getSelectedWebhookOptions()
                        .contains(CustomWebhook.WebhookOptions.TESTING_RUN_RESULTS)
                        || webhook.getSelectedWebhookOptions()
                                .contains(CustomWebhook.WebhookOptions.TRAFFIC_ALERTS)
            || webhook.getSelectedWebhookOptions()
                .contains(WebhookOptions.PENDING_TESTS_ALERTS))) {
            return;
        }

        ChangesInfo ci = getChangesInfo(now - webhook.getLastSentTimestamp(), now - webhook.getLastSentTimestamp(), webhook.getNewEndpointCollections(), webhook.getNewSensitiveEndpointCollections(), true);

        boolean sendApiThreats = false;
        List<MaliciousEventDto> maliciousEvents = new ArrayList<>();
        if (webhook.getSelectedWebhookOptions() != null) {
            for (WebhookOptions option : webhook.getSelectedWebhookOptions()) {
                if (WebhookOptions.API_THREAT_PAYLOADS.equals(option)) {
                    // Fetch one record to see if data exists
                    String accountId = String.valueOf(Context.accountId.get());
                    maliciousEvents = MaliciousEventDao.instance.getCollection(accountId)
                            .find(Filters.and(
                                    Filters.gte("detectedAt", webhook.getLastSentTimestamp()),
                                    Filters.lt("detectedAt", now),
                                    Filters.eq("successfulExploit", true)))
                            .sort(Sorts.descending("detectedAt", Constants.ID))
                            .limit(1)
                            .into(new ArrayList<>());
                    if (maliciousEvents != null && !maliciousEvents.isEmpty()) {
                        sendApiThreats = true;
                    }
                    break;
                }
            }
        } 

        if (webhook.getBody() != null && !sendApiThreats) {
            String accountId = String.valueOf(Context.accountId.get());
            maliciousEvents = MaliciousEventDao.instance.getCollection(accountId)
                    .find(Filters.and(
                            Filters.gte("detectedAt", webhook.getLastSentTimestamp()),
                            Filters.lt("detectedAt", now),
                            Filters.eq("successfulExploit", true)))
                    .sort(Sorts.descending("detectedAt", Constants.ID))
                    .limit(1)
                    .into(new ArrayList<>());
            if (maliciousEvents != null && !maliciousEvents.isEmpty()) {
                sendApiThreats = webhook.getBody().contains("AKTO.changes_info.apiThreatPayloads");
            }
        }

        if ((ci == null ||
                (ci.newEndpointsLast7Days.size() + ci.newSensitiveParams.size() +
                        ci.recentSentiiveParams + ci.newParamsInExistingEndpoints) == 0)
                && (maliciousEvents == null || maliciousEvents.isEmpty())) {
            return;
        }

        Map<String, Object> valueMap = new HashMap<>();

        createBodyForWebhook(webhook);

        if (ci != null) {
            valueMap.put("AKTO.changes_info.newSensitiveEndpoints", ci.newSensitiveParamsObject);
            valueMap.put("AKTO.changes_info.newSensitiveEndpointsCount", ci.newSensitiveParams.size());
            
            valueMap.put("AKTO.changes_info.newEndpoints", ci.newEndpointsLast7DaysObject);
            valueMap.put("AKTO.changes_info.newEndpointsCount", ci.newEndpointsLast7Days.size());
            
            valueMap.put("AKTO.changes_info.newSensitiveParametersCount", ci.recentSentiiveParams);
            valueMap.put("AKTO.changes_info.newParametersCount", ci.newParamsInExistingEndpoints);
        }

        if(sendApiThreats){

            final int DATA_LIMIT = webhook.getBatchSize() > 0 ? Math.min(webhook.getBatchSize(), 20) : 20;
            int skip = 0;
            final int lastSentTimestamp = webhook.getLastSentTimestamp();
            String accountId = String.valueOf(Context.accountId.get());

            do {
                try {
                    maliciousEvents = MaliciousEventDao.instance.getCollection(accountId)
                            .find(Filters.and(
                                    Filters.gte("detectedAt", lastSentTimestamp),
                                    Filters.lt("detectedAt", now),
                                    Filters.eq("successfulExploit", true)))
                            .sort(Sorts.descending("detectedAt", Constants.ID))
                            .projection(Projections.exclude("latestApiOrig"))
                            .skip(skip)
                            .limit(DATA_LIMIT)
                            .into(new ArrayList<>());

                    if (maliciousEvents != null && !maliciousEvents.isEmpty()) {
                        valueMap.put("AKTO.changes_info.apiThreatPayloads", maliciousEvents);
                        actuallySendWebhook(webhook, valueMap, now);
                    }
                    skip += DATA_LIMIT;
                    valueMap.clear();

                    if ((maliciousEvents != null && maliciousEvents.size() < DATA_LIMIT) ||
                    // In case of a dry run / run once, we only want to send data once.
                            webhook.getFrequencyInSeconds() == 0) {
                        break;
                    }
                } catch (Exception e) {
                    logger.errorAndAddToDb(e, "Error in sending webhook API data " + e.getMessage(),
                            LogDb.DASHBOARD);
                }

            } while(maliciousEvents != null && !maliciousEvents.isEmpty());

        } else {
            actuallySendWebhook(webhook, valueMap, now);
        }

    }

    private static final ObjectMapper mapper = new ObjectMapper();
    
    private static String escapeJsonString(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String TEAMS_WEBHOOK_OPENING_BODY = "{\n" +
                "    \"type\": \"message\",\n" +
                "    \"attachments\": [\n" +
                "        {\n" +
                "            \"contentType\": \"application/vnd.microsoft.card.adaptive\",\n" +
                "            \"content\": {\n" +
                "                \"type\": \"AdaptiveCard\",\n" +
                "                \"$schema\": \"http://adaptivecards.io/schemas/adaptive-card.json\",\n" +
                "                \"version\": \"1.5\",\n" +
                "\"msteams\": { \"width\": \"full\" }," +
                "                \"body\": [";
    
    private static String TEAMS_WEBHOOK_CLOSING_BODY = "]\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}\n" +
                "";

    private static String createMicrosoftTeamsWorkflowWebhookPayload(CustomWebhook webhook, Map<String, Object> valueMap){
        StringBuilder body = new StringBuilder();
        body.append(TEAMS_WEBHOOK_OPENING_BODY);
        
        if (webhook.getSelectedWebhookOptions() != null) {
            for (WebhookOptions webhookOption : webhook.getSelectedWebhookOptions()) {
    
                String replaceString = webhookOption.getOptionReplaceString().substring(2, webhookOption.getOptionReplaceString().length() - 1);
                String name = webhookOption.getOptionName();
    
                Object value = valueMap.get(replaceString);
                if(replaceString.equals("AKTO.changes_info.apiThreatPayloads")){
                    buildApiThreatsTeamsWebhookBody(body, name, value);
                } else{
                    buildNormalTeamsWebhookBody(body, name, value);
                }
            }
        }
    
        body.append(TEAMS_WEBHOOK_CLOSING_BODY);
    
        return body.toString();
    }

    private static void buildNormalTeamsWebhookBody(StringBuilder body, String name, Object value) {
        if (value instanceof List) {
            body.append("        {\n" +
                    "            \"type\": \"TextBlock\",\n" +
                    "            \"text\": \"" + name + "\",\n" +
                    "            \"wrap\": true\n" +
                    "        },");
            
            List<Object> list = (List<Object>) value;
            boolean headerAdded = false;
   
            for (Object obj : list) {
                Map<String, Object> data = mapper.convertValue(obj, HashMap.class);
                
                if (!headerAdded) {
                    // Add the table headers (first row)
                    body.append("        {\n" +
                            "            \"type\": \"Table\",\n" +
                            "            \"columns\": [\n");
   
                    for (String key : data.keySet()) {
                        if(key=="id" || key=="sample"){
                            continue;
                        }

                        body.append("                {\"width\": 1},\n");
                    }
   
                    body.append("            ],\n" +
                            "            \"rows\": [\n" +
                            "                {\n" +
                            "                    \"type\": \"TableRow\",\n" +
                            "                    \"cells\": [\n");
   
                    // Add header row (keys)
                    for (String key : data.keySet()) {
                        if(key=="id" || key=="sample"){
                            continue;
                        }
                        body.append("                        {\n" +
                                "                            \"type\": \"TableCell\",\n" +
                                "                            \"items\": [\n" +
                                "                                {\n" +
                                "                                    \"type\": \"TextBlock\",\n" +
                                "                                    \"text\": \"" + key + "\",\n" +
                                "                                    \"wrap\": true\n" +
                                "                                }\n" +
                                "                            ]\n" +
                                "                        },\n");
                    }
   
                    body.append("                    ]\n" +
                            "                },\n");
   
                    headerAdded = true;
                }
   
                // Add each data row (values)
                body.append("                {\n" +
                        "                    \"type\": \"TableRow\",\n" +
                        "                    \"cells\": [\n");
   
                for (Entry<String,Object> entry : data.entrySet()) {
                    if(entry.getKey()=="id" || entry.getKey()=="sample"){
                        continue;
                    }
                    body.append("                        {\n" +
                            "                            \"type\": \"TableCell\",\n" +
                            "                            \"items\": [\n" +
                            "                                {\n" +
                            "                                    \"type\": \"TextBlock\",\n" +
                            "                                    \"text\": \"" + entry.getValue() + "\",\n" +
                            "                                    \"wrap\": true\n" +
                            "                                }\n" +
                            "                            ]\n" +
                            "                        },\n");
                }
   
                body.append("                    ]\n" +
                        "                },\n");
            }
   
            // Close the table structure
            body.append("            ]\n" +
                    "        },\n");
            
        } else {
            body.append("        {\n" +
                    "            \"type\": \"TextBlock\",\n" +
                    "            \"text\": \"" + name + "\",\n" +
                    "            \"wrap\": true\n" +
                    "        },");
        }
    }
    

    private static void buildApiThreatsTeamsWebhookBody(StringBuilder body, String name, Object value) {
        if (value instanceof List) {
            body.append("        {\n" +
                    "            \"type\": \"TextBlock\",\n" +
                    "            \"text\": \"" + escapeJsonString(name) + "\",\n" +
                    "            \"weight\": \"bolder\",\n" +
                    "            \"wrap\": true\n" +
                    "        },");
            
            List<Object> list = (List<Object>) value;
            boolean isFirstItem = true;

            for (Object obj : list) {
                Map<String, Object> data = mapper.convertValue(obj, HashMap.class);
                
                // Create a FactSet for each object (better for key-value pairs)
                body.append("        {\n" +
                        "            \"type\": \"FactSet\",\n" +
                        (isFirstItem ? "" : "            \"separator\": true,\n            \"spacing\": \"extraLarge\",\n") +
                        "            \"facts\": [\n");
                
                boolean firstFact = true;
                for (Entry<String, Object> entry : data.entrySet()) {
                    String key = entry.getKey();
                    if ("id".equals(key) || "sample".equals(key) || "metadata".equals(key)) {
                        continue;
                    }
                    
                    if (!firstFact) {
                        body.append(",\n");
                    }
                    
                    Object val = entry.getValue();
                    String valueStr = val == null ? "" : val.toString();
                    
                    body.append("                {\n" +
                            "                    \"title\": \"" + escapeJsonString(key) + ":\",\n" +
                            "                    \"value\": \"" + escapeJsonString(valueStr) + "\"\n" +
                            "                }");
                    firstFact = false;
                }
                
                body.append("\n            ]\n" +
                        "        },\n");
                isFirstItem = false;
            }
            
        } else {
            String valueStr = value == null ? "" : value.toString();
            body.append("        {\n" +
                    "            \"type\": \"TextBlock\",\n" +
                    "            \"text\": \"" + escapeJsonString(name) + ": " + escapeJsonString(valueStr) + "\",\n" +
                    "            \"wrap\": true\n" +
                    "        },");
        }
    }
        
    private static void actuallySendWebhook(CustomWebhook webhook, Map<String, Object> valueMap, int now){
        List<String> errors = new ArrayList<>();
        String payload = null;

        try {
            if(webhook.getWebhookType()!=null && WebhookType.MICROSOFT_TEAMS.equals(webhook.getWebhookType())){
                if (webhook.getSelectedWebhookOptions() != null && !webhook.getSelectedWebhookOptions().isEmpty()){
                    // in case the operation is provided but body is not available the we create the
                    // formatted body
                    // for Microsoft teams workflow webhooks
                    payload = createMicrosoftTeamsWorkflowWebhookPayload(webhook, valueMap);
                } else {
                    // in case the body is provided, then the data needs to escaped.
                    // for Microsoft teams workflow webhooks
                    payload = Utils.replaceVariables(webhook.getBody(), valueMap, true, true);
                } 
            } else {
                // default case.
                payload = Utils.replaceVariables(webhook.getBody(), valueMap, false, true);
            }
        } catch (Exception e) {
            errors.add("Failed to replace variables");
        }

        WebhookSender.sendCustomWebhook(webhook, payload, errors, now, LogDb.DASHBOARD);
    }

    private static void createBodyForWebhook(CustomWebhook webhook) {

        if (webhook.getSelectedWebhookOptions() == null || webhook.getSelectedWebhookOptions().isEmpty()) {// no filtering
            return;
        }

        String webhookBody = webhook.getBody();
        if (webhookBody.contains("$")) return; // use custom body

        StringBuilder body = new StringBuilder();
        body.append("{");

        List<WebhookOptions> allowedWebhookOptions = webhook.getSelectedWebhookOptions();
        for (WebhookOptions webhookOption : allowedWebhookOptions) {
            body.append("\"").append(webhookOption.getOptionName()).append("\":").append(webhookOption.getOptionReplaceString()).append(",");
        }
        body.deleteCharAt(body.length() - 1);//Removing last comma
        body.append("}");
        webhook.setBody(body.toString());
    }

    public void webhookSender() {
        try {
            List<CustomWebhook> listWebhooks = CustomWebhooksDao.instance.findAll(new BasicDBObject());
            if (listWebhooks == null || listWebhooks.isEmpty()) {
                return;
            }

            for (CustomWebhook webhook : listWebhooks) {
                webhookSenderUtil(webhook);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setUpWebhookScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        webhookSender();
                    }
                }, "webhook-sender");
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    static class ChangesInfo {
        public Map<String, String> newSensitiveParams = new HashMap<>();
        public List<BasicDBObject> newSensitiveParamsObject = new ArrayList<>();
        public List<String> newEndpointsLast7Days = new ArrayList<>();
        public List<BasicDBObject> newEndpointsLast7DaysObject = new ArrayList<>();
        public List<String> newEndpointsLast31Days = new ArrayList<>();
        public List<BasicDBObject> newEndpointsLast31DaysObject = new ArrayList<>();
        public int totalSensitiveParams = 0;
        public int recentSentiiveParams = 0;
        public int newParamsInExistingEndpoints = 0;
    }

    public static class UrlResult {
        String urlString;
        BasicDBObject urlObject;

        public UrlResult(String urlString, BasicDBObject urlObject) {
            this.urlString = urlString;
            this.urlObject = urlObject;
        }
    }


    public static UrlResult extractUrlFromBasicDbObject(BasicDBObject singleTypeInfo, Map<Integer, ApiCollection> apiCollectionMap, List<String> collectionList, boolean allowCollectionIds) {
        String method = singleTypeInfo.getString("method");
        String path = singleTypeInfo.getString("url");

        Object apiCollectionIdObj = singleTypeInfo.get("apiCollectionId");

        String urlString;

        BasicDBObject urlObject = new BasicDBObject();
        if (apiCollectionIdObj == null) {
            urlString = method + " " + path;
            urlObject.put("host", null);
            urlObject.put("path", path);
            urlObject.put("method", method);
            if (allowCollectionIds) {
                urlObject.put(SingleTypeInfo._API_COLLECTION_ID, null);
                urlObject.put(SingleTypeInfo.COLLECTION_NAME, null);
            }
            return new UrlResult(urlString, urlObject);
        }

        int apiCollectionId = (int) apiCollectionIdObj;
        ApiCollection apiCollection = apiCollectionMap.get(apiCollectionId);
        if (apiCollection == null) {
            apiCollection = new ApiCollection();
        }
        boolean apiCollectionContainsCondition = collectionList == null || collectionList.contains(apiCollection.getDisplayName());
        if (!apiCollectionContainsCondition) {
            return null;
        }

        String hostName = apiCollection.getHostName() == null ?  "" : apiCollection.getHostName();
        String url;
        if (hostName != null) {
            url = path.startsWith("/") ? hostName + path : hostName + "/" + path;
        } else {
            url = path;
        }

        urlString = method + " " + url;

        urlObject = new BasicDBObject();
        urlObject.put("host", hostName);
        urlObject.put("path", path);
        urlObject.put("method", method);
        if (allowCollectionIds) {
            urlObject.put(SingleTypeInfo._API_COLLECTION_ID, apiCollectionId);
            urlObject.put(SingleTypeInfo.COLLECTION_NAME, apiCollection.getDisplayName());
        }

        return new UrlResult(urlString, urlObject);
    }

    protected static ChangesInfo getChangesInfo(int newEndpointsFrequency, int newSensitiveParamsFrequency,
                                                List<String> newEndpointCollections, List<String> newSensitiveEndpointCollections,
                                                boolean includeCollectionIds) {
        ChangesInfo ret = new ChangesInfo();
        try {
            int now = Context.now();
            List<BasicDBObject> newEndpointsSmallerDuration = new InventoryAction().fetchRecentEndpoints(now - newSensitiveParamsFrequency, now);
            List<BasicDBObject> newEndpointsBiggerDuration = new InventoryAction().fetchRecentEndpoints(now - newEndpointsFrequency, now);

            Map<Integer, ApiCollection> apiCollectionMap = ApiCollectionsDao.instance.generateApiCollectionMap();

            int newParamInNewEndpoint = 0;

            for (BasicDBObject singleTypeInfo : newEndpointsSmallerDuration) {
                newParamInNewEndpoint += (int) singleTypeInfo.getOrDefault("countTs", 0);
                singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
                UrlResult urlResult = extractUrlFromBasicDbObject(singleTypeInfo, apiCollectionMap, newEndpointCollections, includeCollectionIds);
                if (urlResult == null) {
                    continue;
                }
                ret.newEndpointsLast7Days.add(urlResult.urlString);
                ret.newEndpointsLast7DaysObject.add(urlResult.urlObject);
            }

            for (BasicDBObject singleTypeInfo : newEndpointsBiggerDuration) {
                singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
                UrlResult urlResult = extractUrlFromBasicDbObject(singleTypeInfo, apiCollectionMap, null, includeCollectionIds);
                if (urlResult == null) {
                    continue;
                }
                ret.newEndpointsLast31Days.add(urlResult.urlString);
                ret.newEndpointsLast31DaysObject.add(urlResult.urlObject);
            }

            List<SingleTypeInfo> sensitiveParamsList = new InventoryAction().fetchSensitiveParams();
            ret.totalSensitiveParams = sensitiveParamsList.size();
            ret.recentSentiiveParams = 0;
            int delta = newSensitiveParamsFrequency;
            Map<Pair<String, String>, Set<String>> endpointToSubTypes = new HashMap<>();
            Map<Pair<String, String>, ApiCollection> endpointToApiCollection = new HashMap<>();
            for (SingleTypeInfo sti : sensitiveParamsList) {
                ApiCollection apiCollection = apiCollectionMap.get(sti.getApiCollectionId());
                String url = sti.getUrl();
                boolean skipAddingIntoMap = false;
                if (apiCollection != null && apiCollection.getHostName() != null) {
                    String hostName = apiCollection.getHostName();
                    url = url.startsWith("/") ? hostName + url : hostName + "/" + url;
                }

                if (newSensitiveEndpointCollections != null) {//case of filtering by collection for sensitive endpoints
                    skipAddingIntoMap = true;
                    if (apiCollection != null) {
                        skipAddingIntoMap = !newSensitiveEndpointCollections.contains(apiCollection.getDisplayName());
                    }
                }
                if (skipAddingIntoMap) {
                    continue;
                }

                String encoded = Base64.getEncoder().encodeToString((sti.getUrl() + " " + sti.getMethod()).getBytes());
                String link = "/dashboard/observe/inventory/" + sti.getApiCollectionId() + "/" + encoded;
                Pair<String, String> key = new Pair<>(sti.getMethod() + " " + url, link);
                String value = sti.getSubType().getName();
                if (sti.getTimestamp() >= now - delta) {
                    ret.recentSentiiveParams++;
                    Set<String> subTypes = endpointToSubTypes.get(key);
                    if (subTypes == null) {
                        subTypes = new HashSet<>();
                        endpointToSubTypes.put(key, subTypes);
                    }
                    endpointToApiCollection.put(key, apiCollection);
                    subTypes.add(value);
                }
            }

            for (Pair<String, String> key : endpointToSubTypes.keySet()) {
                String subTypes = StringUtils.join(endpointToSubTypes.get(key), ",");
                String methodPlusUrl = key.getFirst();
                ret.newSensitiveParams.put(methodPlusUrl + ": " + subTypes, key.getSecond());

                BasicDBObject basicDBObject = new BasicDBObject();
                String[] methodPlusUrlList = methodPlusUrl.split(" ");
                if (methodPlusUrlList.length != 2) continue;
                basicDBObject.put("url", methodPlusUrlList[1]);
                basicDBObject.put("method", methodPlusUrlList[0]);
                basicDBObject.put("subTypes", subTypes);
                basicDBObject.put("link", key.getSecond());
                ApiCollection collection = endpointToApiCollection.get(key);
                basicDBObject.put(SingleTypeInfo._API_COLLECTION_ID, collection != null ? collection.getId() : null);
                basicDBObject.put(SingleTypeInfo.COLLECTION_NAME, collection != null ? collection.getDisplayName() : null);
                ret.newSensitiveParamsObject.add(basicDBObject);
            }

            List<SingleTypeInfo> allNewParameters = new InventoryAction().fetchAllNewParams(now - newEndpointsFrequency, now);
            int totalNewParameters = allNewParameters.size();
            ret.newParamsInExistingEndpoints = Math.max(0, totalNewParameters - newParamInNewEndpoint);
            return ret;
        } catch (Exception e) {
            logger.errorAndAddToDb(e, String.format("get new endpoints %s", e.toString()), LogDb.DASHBOARD);
        }
        return ret;
    }

    public static void dropApiDependencies(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDropApiDependencies() == 0) {
            DependencyNodeDao.instance.getMCollection().drop();
            DependencyFlowNodesDao.instance.getMCollection().drop();
            DependencyNodeDao.instance.createIndicesIfAbsent();
            DependencyFlowNodesDao.instance.createIndicesIfAbsent();
        } else {
            return;
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DROP_API_DEPENDENCIES, Context.now())
        );
    }

    public static void dropFilterSampleDataCollection(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDropFilterSampleData() == 0) {
            FilterSampleDataDao.instance.getMCollection().drop();
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DROP_FILTER_SAMPLE_DATA, Context.now())
        );
    }

    public static void dropSpecialCharacterApiCollections(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDropSpecialCharacterApiCollections() == 0) {
            ApiCollectionsAction apiCollectionsAction = new ApiCollectionsAction();
            List<ApiCollection> collections = new ArrayList<>();
            try {
                List<ApiCollection> apiCollections = ApiCollectionsDao.fetchAllHosts();
                for (ApiCollection apiCollection: apiCollections) {
                    if (RuntimeUtil.hasSpecialCharacters(apiCollection.getHostName())) {
                        collections.add(new ApiCollection(apiCollection.getId(), null, 0, null, null, 0, false, true));
                    }
                }
                apiCollectionsAction.setApiCollections(collections);
                apiCollectionsAction.deleteMultipleCollections();
                BackwardCompatibilityDao.instance.updateOne(
                        Filters.eq("_id", backwardCompatibility.getId()),
                        Updates.set(BackwardCompatibility.DROP_SPECIAL_CHARACTER_API_COLLECTIONS, Context.now())
                );
            } catch (Exception e) {
                logger.errorAndAddToDb("error dropping special character collections " + e.getStackTrace());
            }

        }

    }

    
    public static void dropAuthMechanismData(BackwardCompatibility authMechanismData) {
        if (authMechanismData.getAuthMechanismData() == 0) {
            AuthMechanismsDao.instance.getMCollection().drop();
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", authMechanismData.getId()),
                Updates.set(BackwardCompatibility.AUTH_MECHANISM_DATA, Context.now())
        );
    }

    public static TestRoles createAndSaveAttackerRole(AuthMechanism authMechanism) {
        int createdTs = authMechanism.getId().getTimestamp();
        EndpointLogicalGroup endpointLogicalGroup = new EndpointLogicalGroup(new ObjectId(), createdTs, createdTs, "System", "All", new AllTestingEndpoints());
        EndpointLogicalGroupDao.instance.insertOne(endpointLogicalGroup);
        AuthWithCond authWithCond = new AuthWithCond(authMechanism, new HashMap<>(), null);
        List<AuthWithCond> authWithCondList = Collections.singletonList(authWithCond);
        TestRoles testRoles = new TestRoles(new ObjectId(), "ATTACKER_TOKEN_ALL", endpointLogicalGroup.getId(), authWithCondList, "System", createdTs, createdTs, null, "System");
        TestRolesDao.instance.insertOne(testRoles);
        return testRoles;
    }

    public static void moveAuthMechanismDataToRole(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getMoveAuthMechanismToRole() == 0) {

            AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());
            if (authMechanism != null) {
                createAndSaveAttackerRole(authMechanism);
            }

            BackwardCompatibilityDao.instance.updateOne(
                    Filters.eq("_id", backwardCompatibility.getId()),
                    Updates.set(BackwardCompatibility.MOVE_AUTH_MECHANISM_TO_ROLE, Context.now())
            );

        }
    }

    public static void createApiGroup(int id, String name, String regex) {
        ApiCollection loginGroup = new ApiCollection(id, name, Context.now(), new HashSet<>(), null, 0, false, false);
        loginGroup.setType(ApiCollection.Type.API_GROUP);
        ArrayList<TestingEndpoints> loginConditions = new ArrayList<>();
        loginConditions.add(new RegexTestingEndpoints(TestingEndpoints.Operator.OR, regex));
        loginGroup.setConditions(loginConditions);
        loginGroup.setAutomated(true);
        ApiCollectionUsers.removeFromCollectionsForCollectionId(loginGroup.getConditions(), id);

        ApiCollectionsDao.instance.insertOne(loginGroup);
        ApiCollectionUsers.addToCollectionsForCollectionId(loginGroup.getConditions(), loginGroup.getId());
    }

    public static void createLoginSignupGroups(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getLoginSignupGroups() == 0) {

            createApiGroup(111_111_128, "Login APIs", "^((https?):\\/\\/)?(www\\.)?.*?(login|signin|sign-in|authenticate|session)(.*?)(\\?.*|\\/?|#.*?)?$");
            createApiGroup(111_111_129, "Signup APIs", "^((https?):\\/\\/)?(www\\.)?.*?(register|signup|sign-up|users\\/create|account\\/create|account_create|create_account)(.*?)(\\?.*|\\/?|#.*?)?$");
            createApiGroup(111_111_130, "Password Reset APIs", "^((https?):\\/\\/)?(www\\.)?.*?(password-reset|reset-password|forgot-password|user\\/reset|account\\/recover|api\\/password_reset|password\\/reset|account\\/reset-password-request|password_reset_request|account_recovery)(.*?)(\\?.*|\\/?|#.*?)?$");

            BackwardCompatibilityDao.instance.updateOne(
                    Filters.eq("_id", backwardCompatibility.getId()),
                    Updates.set(BackwardCompatibility.LOGIN_SIGNUP_GROUPS, Context.now())
            );

        }
    }

    public static void createUnauthenticatedApiGroup() {

        ApiCollection collection = ApiCollectionsDao.instance.findOne(
                Filters.eq("_id", UnauthenticatedEndpoint.UNAUTHENTICATED_GROUP_ID));

        if (collection == null) {
            logger.warnAndAddToDb("AccountId: " + Context.accountId.get() + " Creating unauthenticated api group.", LogDb.DASHBOARD);
            ApiCollection unauthenticatedApisGroup = new ApiCollection(UnauthenticatedEndpoint.UNAUTHENTICATED_GROUP_ID,
                    "Unauthenticated APIs", Context.now(), new HashSet<>(), null, 0, false, false);

            unauthenticatedApisGroup.setAutomated(true);
            unauthenticatedApisGroup.setType(ApiCollection.Type.API_GROUP);
            List<TestingEndpoints> conditions = new ArrayList<>();
            conditions.add(new UnauthenticatedEndpoint());
            unauthenticatedApisGroup.setConditions(conditions);

            ApiCollectionsDao.instance.insertOne(unauthenticatedApisGroup);
        }else{
            int startTime = Context.now();
            ApiCollectionUsers.computeCollectionsForCollectionId(collection.getConditions(), collection.getId());
            logger.warnAndAddToDb("AccountId: " + Context.accountId.get() + " Computed unauthenticated api group in " + (Context.now() - startTime) + " seconds", LogDb.DASHBOARD);
        }

    }

    public static void createAllApisGroup() {
        ApiCollection collection = ApiCollectionsDao.instance
                .findOne(Filters.eq("_id", 111111121));
        if (collection == null) {
            logger.warnAndAddToDb("AccountId: " + Context.accountId.get() + " Creating all apis group.", LogDb.DASHBOARD);
            ApiCollection allApisGroup = new ApiCollection(111_111_121, "All Apis", Context.now(), new HashSet<>(),
                    null, 0, false, false);

            allApisGroup.setAutomated(true);
            allApisGroup.setType(ApiCollection.Type.API_GROUP);
            List<TestingEndpoints> conditions = new ArrayList<>();
            conditions.add(new AllAPIsGroup());
            allApisGroup.setConditions(conditions);

            ApiCollectionsDao.instance.insertOne(allApisGroup);
        }else{
            int startTime = Context.now();
            ApiCollectionUsers.computeCollectionsForCollectionId(collection.getConditions(), collection.getId());
            logger.warnAndAddToDb("AccountId: " + Context.accountId.get() + " Computed ALL apis group in " + (Context.now() - startTime) + " seconds", LogDb.DASHBOARD);
        }

    }
    

    public static void createFirstUnauthenticatedApiGroup(){
        createUnauthenticatedApiGroup();
        createAllApisGroup();
    }

    public static void createRiskScoreApiGroup(int id, String name, RiskScoreTestingEndpoints.RiskScoreGroupType riskScoreGroupType) {
        logger.debugAndAddToDb("Creating risk score group: " + name, LogDb.DASHBOARD);
        
        ApiCollection riskScoreGroup = new ApiCollection(id, name, Context.now(), new HashSet<>(), null, 0, false, false);
            
        List<TestingEndpoints> riskScoreConditions = new ArrayList<>();
        RiskScoreTestingEndpoints riskScoreTestingEndpoints = new RiskScoreTestingEndpoints(riskScoreGroupType);
        riskScoreConditions.add(riskScoreTestingEndpoints);

        riskScoreGroup.setConditions(riskScoreConditions);
        riskScoreGroup.setType(ApiCollection.Type.API_GROUP);
        riskScoreGroup.setAutomated(true);

        ApiCollectionsDao.instance.insertOne(riskScoreGroup); 
    }

    public static void createRiskScoreGroups(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getRiskScoreGroups() == 0) {
            createRiskScoreApiGroup(111_111_148, "Low Risk APIs", RiskScoreTestingEndpoints.RiskScoreGroupType.LOW);
            createRiskScoreApiGroup(111_111_149, "Medium Risk APIs", RiskScoreTestingEndpoints.RiskScoreGroupType.MEDIUM);
            createRiskScoreApiGroup(111_111_150, "High Risk APIs", RiskScoreTestingEndpoints.RiskScoreGroupType.HIGH);

            BackwardCompatibilityDao.instance.updateOne(
                    Filters.eq("_id", backwardCompatibility.getId()),
                    Updates.set(BackwardCompatibility.RISK_SCORE_GROUPS, Context.now())
            );
        }
    }

    public static void setApiCollectionAutomatedField(BackwardCompatibility backwardCompatibility) {
        // Set automated field of existing automated API collections
        if (backwardCompatibility.getApiCollectionAutomatedField() == 0) {
            int[] apiCollectionIds = {111_111_128, 111_111_129, 111_111_130, 111_111_148, 111_111_149, 111_111_150};

            for (int apiCollectionId : apiCollectionIds) {
                ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq("_id", apiCollectionId));

                if (apiCollection != null) {
                    if (!apiCollection.getAutomated()) {
                        apiCollection.setAutomated(true);
                        ApiCollectionsDao.instance.updateOne(
                            Filters.eq("_id", apiCollectionId), 
                            Updates.set(ApiCollection.AUTOMATED, true));
                    }
                }
            }

            BackwardCompatibilityDao.instance.updateOne(
                    Filters.eq("_id", backwardCompatibility.getId()),
                    Updates.set(BackwardCompatibility.API_COLLECTION_AUTOMATED_FIELD, Context.now())
            );
        }
    }

    public static void createAutomatedAPIGroups(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getAutomatedApiGroups() == 0) {
            // Fetch automated API groups csv records
            List<CSVRecord> apiGroupRecords = AutomatedApiGroupsUtils.fetchGroups();

            if (apiGroupRecords != null) {
                AutomatedApiGroupsUtils.processAutomatedGroups(apiGroupRecords);
            }

            BackwardCompatibilityDao.instance.updateOne(
                    Filters.eq("_id", backwardCompatibility.getId()),
                    Updates.set(BackwardCompatibility.AUTOMATED_API_GROUPS, Context.now())
            );
        }
    }


    public static void dropWorkflowTestResultCollection(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDropWorkflowTestResult() == 0) {
            WorkflowTestResultsDao.instance.getMCollection().drop();
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DROP_WORKFLOW_TEST_RESULT, Context.now())
        );
    }

    public static void resetSingleTypeInfoCount(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getResetSingleTypeInfoCount() == 0) {
            SingleTypeInfoDao.instance.resetCount();
        }

        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.RESET_SINGLE_TYPE_INFO_COUNT, Context.now())
        );
    }

    public static void dropSampleDataIfEarlierNotDroped(AccountSettings accountSettings) {
        if (accountSettings == null) return;
        if (accountSettings.isRedactPayload() && !accountSettings.isSampleDataCollectionDropped()) {
            AdminSettingsAction.dropCollections(Context.accountId.get());
        }
        ApiCollectionsAction.dropSampleDataForApiCollection();
        CustomDataTypeAction.handleDataTypeRedaction();

    }

    public static void deleteAccessListFromApiToken(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDeleteAccessListFromApiToken() == 0) {
            ApiTokensDao.instance.updateMany(new BasicDBObject(), Updates.unset("accessList"));
        }

        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DELETE_ACCESS_LIST_FROM_API_TOKEN, Context.now())
        );
    }

    public static void deleteNullSubCategoryIssues(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDeleteNullSubCategoryIssues() == 0) {
            TestingRunIssuesDao.instance.deleteAll(
                    Filters.or(
                            Filters.exists("_id.testSubCategory", false),
                            // ?? enum saved in db
                            Filters.eq("_id.testSubCategory", null)
                    )
            );
        }

        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DELETE_NULL_SUB_CATEGORY_ISSUES, Context.now())
        );
    }

    public static void enableNewMerging(BackwardCompatibility backwardCompatibility) {
        if (!DashboardMode.isLocalDeployment()) {
            return;
        }
        if (backwardCompatibility.getEnableNewMerging() == 0) {

            AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), 
                Updates.set(AccountSettings.URL_REGEX_MATCHING_ENABLED, true));
        }

        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.ENABLE_NEW_MERGING, Context.now())
        );
    }

    public static void readyForNewTestingFramework(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getReadyForNewTestingFramework() == 0) {
            TestingRunDao.instance.getMCollection().drop();
            TestingRunResultDao.instance.getMCollection().drop();
            TestingSchedulesDao.instance.getMCollection().drop();
            WorkflowTestResultsDao.instance.getMCollection().drop();

            BackwardCompatibilityDao.instance.updateOne(
                    Filters.eq("_id", backwardCompatibility.getId()),
                    Updates.set(BackwardCompatibility.READY_FOR_NEW_TESTING_FRAMEWORK, Context.now())
            );
        }
    }

    public static void addAktoDataTypes(BackwardCompatibility backwardCompatibility) {
        List<AktoDataType> dataTypes = AktoDataTypeDao.instance.findAll(
            Filters.exists(AktoDataType.DATA_TYPE_PRIORITY,true)
        );
        if (backwardCompatibility.getAddAktoDataTypes() == 0 || dataTypes == null || dataTypes.isEmpty()) {
            List<AktoDataType> aktoDataTypes = new ArrayList<>();
            int now = Context.now();
            IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
            aktoDataTypes.add(new AktoDataType("JWT", false, Arrays.asList(SingleTypeInfo.Position.RESPONSE_PAYLOAD, SingleTypeInfo.Position.RESPONSE_HEADER), now, ignoreData, false, true, Arrays.asList("AUTHENTICATION","TOKEN"), Severity.HIGH));
            aktoDataTypes.add(new AktoDataType("EMAIL", true, Collections.emptyList(), now, ignoreData, false, true,  Arrays.asList("PII"), Severity.MEDIUM));
            aktoDataTypes.add(new AktoDataType("CREDIT_CARD", true, Collections.emptyList(), now, ignoreData, false, true, Arrays.asList("FINANCIAL","PII"), Severity.MEDIUM));
            aktoDataTypes.add(new AktoDataType("VIN", true, Collections.emptyList(), now, ignoreData, false, true, Arrays.asList("PII"), Severity.MEDIUM));
            aktoDataTypes.add(new AktoDataType("SSN", true, Collections.emptyList(), now, ignoreData, false, true,  Arrays.asList("PII"), Severity.HIGH ));
            aktoDataTypes.add(new AktoDataType("ADDRESS", true, Collections.emptyList(), now, ignoreData, false, true,  Arrays.asList("PII"), Severity.LOW));
            aktoDataTypes.add(new AktoDataType("IP_ADDRESS", false, Arrays.asList(SingleTypeInfo.Position.RESPONSE_PAYLOAD, SingleTypeInfo.Position.RESPONSE_HEADER), now, ignoreData, false, true, Arrays.asList("DEVICE IDENTIFIER"), Severity.HIGH));
            aktoDataTypes.add(new AktoDataType("PHONE_NUMBER", true, Collections.emptyList(), now, ignoreData, false, true, Arrays.asList("PII"), Severity.MEDIUM));
            aktoDataTypes.add(new AktoDataType("UUID", false, Collections.emptyList(), now, ignoreData, false, true, Arrays.asList("IDENTIFIER"),Severity.MEDIUM));
            AktoDataTypeDao.instance.getMCollection().drop();
            AktoDataTypeDao.instance.insertMany(aktoDataTypes);

            BackwardCompatibilityDao.instance.updateOne(
                    Filters.eq("_id", backwardCompatibility.getId()),
                    Updates.set(BackwardCompatibility.ADD_AKTO_DATA_TYPES, Context.now())
            );
        }
    }
    public static byte[] loadTemplateFilesFromDirectory() {
        String resourceName = "/tests-library-master.zip";

        logger.debugAndAddToDb("Loading template files from directory", LogDb.DASHBOARD);

        try (InputStream is = InitializerListener.class.getResourceAsStream(resourceName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                if (is == null) {
                    logger.errorAndAddToDb("Resource not found: " + resourceName, LogDb.DASHBOARD);
                    return null;
                } else {
                    // Read the contents of the .zip file into a byte array
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }

                return baos.toByteArray();
            }
        } catch (Exception ex) {
            logger.errorAndAddToDb(ex, String.format("Error while loading templates files from directory. Error: %s", ex.getMessage()), LogDb.DASHBOARD);
        }
        return  null;
    }

    public static void setDefaultTelemetrySettings(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDefaultTelemetrySettings() == 0) {
            int now = Context.now();
            TelemetrySettings telemetrySettings = new TelemetrySettings();
            telemetrySettings.setCustomerEnabled(false);
            telemetrySettings.setCustomerEnabledAt(now);
            telemetrySettings.setStiggEnabledAt(now);
            telemetrySettings.setStiggEnabled(false);
            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            if (accountSettings != null) {
                logger.debugAndAddToDb("Setting default telemetry settings", LogDb.DASHBOARD);
                accountSettings.setTelemetrySettings(telemetrySettings);
                AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.TELEMETRY_SETTINGS, telemetrySettings));
            }
            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DEFAULT_TELEMETRY_SETTINGS, Context.now())
            );
            logger.debugAndAddToDb("Default telemetry settings set successfully", LogDb.DASHBOARD);
        }
    }

    public static void disableAwsSecretPiiType(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDisableAwsSecretPii() != 0) {
            return;
        }

        String piiType = "AWS SECRET ACCESS KEY";

        Bson filters = Filters.eq("subType", piiType);
        Bson update = Updates.set("subType", SingleTypeInfo.GENERIC.toString());

        try {
            SingleTypeInfoDao.instance.updateMany(filters, update);
        } catch (Exception e) {
            logger.errorAndAddToDb("error in disableAwsSecretPiiType " + e.getMessage(), LogDb.DASHBOARD);
        }

        try {
            Bson delFilter = Filters.eq("name", piiType);
            DeleteResult res = CustomDataTypeDao.instance.deleteAll(delFilter);
            logger.debugAndAddToDb("auth type : " + res.toString());
        } catch (Exception e) {
            logger.errorAndAddToDb("error deleting pii type " + piiType + " " + e.getMessage(), LogDb.DASHBOARD);
        }

        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DISABLE_AWS_SECRET_PII, Context.now())
        );
    }

    public static void setAktoDefaultNewUI(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getAktoDefaultNewUI() == 0){

            UsersDao.instance.updateMany(Filters.empty(), Updates.set(User.AKTO_UI_MODE, AktoUIMode.VERSION_2));

            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DEFAULT_NEW_UI, Context.now())
            );
        }
    }

    public static void initializeOrganizationAccountBelongsTo(BackwardCompatibility backwardCompatibility) {
        // lets keep this for now. This function is re-entrant.
        backwardCompatibility.setInitializeOrganizationAccountBelongsTo(0);
        if (backwardCompatibility.getInitializeOrganizationAccountBelongsTo() == 0) {
//            BackwardCompatibilityDao.instance.updateOne(
//                Filters.eq("_id", backwardCompatibility.getId()),
//                Updates.set(BackwardCompatibility.INITIALIZE_ORGANIZATION_ACCOUNT_BELONGS_TO, Context.now())
//            );

            createOrg(Context.accountId.get());

            // if (DashboardMode.isSaasDeployment()) {
            //     try {
            //         // Get rbac document of admin of current account
            //         int accountId = Context.accountId.get();

            //         // Check if account has already been assigned to an organization
            //         //Boolean isNewAccountOnSaaS = OrganizationUtils.checkIsNewAccountOnSaaS(accountId);
                
            //         logger.debugAndAddToDb(String.format("Initializing organization to which account %d belongs", accountId), LogDb.DASHBOARD);
            //         RBAC adminRbac = RBACDao.instance.findOne(
            //             Filters.and(
            //                 Filters.eq(RBAC.ACCOUNT_ID, accountId),  
            //                 Filters.eq(RBAC.ROLE, RBAC.Role.ADMIN)
            //             )
            //         );

            //         if (adminRbac == null) {
            //             logger.debugAndAddToDb(String.format("Account %d does not have an admin user", accountId), LogDb.DASHBOARD);
            //             return;
            //         }

            //         int adminUserId = adminRbac.getUserId();
            //         // Get admin user
            //         User admin = UsersDao.instance.findOne(
            //             Filters.eq(User.ID, adminUserId)
            //         );

            //         if (admin == null) {
            //             logger.debugAndAddToDb(String.format("Account %d admin user does not exist", accountId), LogDb.DASHBOARD);
            //             return;
            //         }

            //         String adminEmail = admin.getLogin();
            //         logger.debugAndAddToDb(String.format("Organization admin email: %s", adminEmail), LogDb.DASHBOARD);

            //         // Get accounts belonging to organization
            //         Set<Integer> accounts = OrganizationUtils.findAccountsBelongingToOrganization(adminUserId);

            //         // Check if organization exists
            //         Organization organization = OrganizationsDao.instance.findOne(
            //             Filters.eq(Organization.ADMIN_EMAIL, adminEmail) 
            //         );

            //         String organizationUUID;

            //         // Create organization if it doesn't exist
            //         if (organization == null) {
            //             String name = adminEmail;

            //             if (DashboardMode.isOnPremDeployment()) {
            //                 name = OrganizationUtils.determineEmailDomain(adminEmail);
            //             }

            //             logger.debugAndAddToDb(String.format("Organization %s does not exist, creating...", name), LogDb.DASHBOARD);

            //             // Insert organization
            //             organizationUUID = UUID.randomUUID().toString();
            //             organization = new Organization(organizationUUID, name, adminEmail, accounts);
            //             OrganizationsDao.instance.insertOne(organization);
            //         } 

            //         organizationUUID = organization.getId();

            //         // Update accounts if changed
            //         if (organization.getAccounts().size() != accounts.size()) {
            //             organization.setAccounts(accounts);
            //             OrganizationsDao.instance.updateOne(
            //                 Filters.eq(Organization.ID, organizationUUID),
            //                 Updates.set(Organization.ACCOUNTS, accounts)
            //             );
            //             logger.debugAndAddToDb(String.format("Organization %s - Updating accounts list", organization.getName()), LogDb.DASHBOARD);
            //         }

            //         Boolean syncedWithAkto = organization.getSyncedWithAkto();
            //         Boolean attemptSyncWithAktoSuccess = false;

            //         // Attempt to sync organization with akto
            //         if (!syncedWithAkto) {
            //             logger.debugAndAddToDb(String.format("Organization %s - Syncing with akto", organization.getName()), LogDb.DASHBOARD);
            //             attemptSyncWithAktoSuccess = OrganizationUtils.syncOrganizationWithAkto(organization);
                        
            //             if (!attemptSyncWithAktoSuccess) {
            //                 logger.debugAndAddToDb(String.format("Organization %s - Sync with akto failed", organization.getName()), LogDb.DASHBOARD);
            //                 return;
            //             } 

            //             logger.debugAndAddToDb(String.format("Organization %s - Sync with akto successful", organization.getName()), LogDb.DASHBOARD);
            //         } else {
            //             logger.debugAndAddToDb(String.format("Organization %s - Alredy Synced with akto. Skipping sync ... ", organization.getName()), LogDb.DASHBOARD);
            //         }
                    
            //         // Set backward compatibility dao
            //         BackwardCompatibilityDao.instance.updateOne(
            //             Filters.eq("_id", backwardCompatibility.getId()),
            //             Updates.set(BackwardCompatibility.INITIALIZE_ORGANIZATION_ACCOUNT_BELONGS_TO, Context.now())
            //         );
            //     } catch (Exception e) {
            //         logger.errorAndAddToDb(String.format("Error while initializing organization account belongs to. Error: %s", e.getMessage()), LogDb.DASHBOARD);
            //     }
            // }
        }
    }

    public static void createOrg(int accountId) {
        Bson filterQ = Filters.in(Organization.ACCOUNTS, accountId);
        Organization organization = OrganizationsDao.instance.findOne(filterQ);
        boolean alreadyExists = organization != null;
        if (alreadyExists) {
            logger.debugAndAddToDb("Org already exists for account: " + accountId, LogDb.DASHBOARD);
            return;
        }

        RBAC rbac = RBACDao.instance.findOne(RBAC.ACCOUNT_ID, accountId, RBAC.ROLE, Role.ADMIN.name());

        if (rbac == null) {
            logger.debugAndAddToDb("Admin is missing in DB", LogDb.DASHBOARD);
            RBACDao.instance.getMCollection().updateOne(Filters.and(Filters.eq(RBAC.ROLE, Role.ADMIN.name()), Filters.exists(RBAC.ACCOUNT_ID, false)), Updates.set(RBAC.ACCOUNT_ID, accountId), new UpdateOptions().upsert(false));
            rbac = RBACDao.instance.findOne(RBAC.ACCOUNT_ID, accountId, RBAC.ROLE, Role.ADMIN.name());
            if(rbac == null){
                logger.errorAndAddToDb("Admin is still missing in DB, making first user as admin", LogDb.DASHBOARD);
                User firstUser = UsersDao.instance.getFirstUser(accountId);
                if(firstUser != null){
                    RBACDao.instance.updateOne(
                        Filters.and(
                            Filters.eq(RBAC.ACCOUNT_ID,Context.accountId.get()),
                            Filters.eq(RBAC.USER_ID, firstUser.getId())
                        ),Updates.set(RBAC.ROLE, RBAC.Role.ADMIN.name()));
                } else {
                    logger.errorAndAddToDb("First user is also missing in DB, unable to make org.", LogDb.DASHBOARD);
                    return;
                }
            }
        }

        int userId = rbac.getUserId();

        User user = UsersDao.instance.findOne(User.ID, userId);
        if (user == null) {
            logger.errorAndAddToDb("User "+ userId +" is absent! Unable to make org.", LogDb.DASHBOARD);
            return;
        }

        Organization org = OrganizationsDao.instance.findOne(Organization.ADMIN_EMAIL, user.getLogin());

        if (org == null) {
            logger.debugAndAddToDb("Creating a new org for email id: " + user.getLogin() + " and acc: " + accountId, LogDb.DASHBOARD);
            org = new Organization(UUID.randomUUID().toString(), user.getLogin(), user.getLogin(), new HashSet<>(), !DashboardMode.isSaasDeployment());
            OrganizationsDao.instance.insertOne(org);
        } else {
            logger.debugAndAddToDb("Found a new org for acc: " + accountId + " org="+org.getId(), LogDb.DASHBOARD);
        }

        Bson updates = Updates.addToSet(Organization.ACCOUNTS, accountId);
        OrganizationsDao.instance.updateOne(Organization.ID, org.getId(), updates);
        org = OrganizationsDao.instance.findOne(Organization.ID, org.getId());
        OrganizationUtils.syncOrganizationWithAkto(org);

        fetchAndSaveFeatureWiseAllowed(org);
    }

    public void setUpFillCollectionIdArrayJob() {
        scheduler.schedule(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        fillCollectionIdArray();
                    }
                }, "fill-collection-id");
            }
        }, 0, TimeUnit.SECONDS);
    }

    public void fillCollectionIdArray() {
        Map<CollectionType, List<String>> matchKeyMap = new HashMap<CollectionType, List<String>>() {{

            put(CollectionType.ApiCollectionId,
                Arrays.asList("$apiCollectionId"));
            put(CollectionType.Id_ApiCollectionId,
                Arrays.asList("$_id.apiCollectionId"));
            put(CollectionType.Id_ApiInfoKey_ApiCollectionId,
                Arrays.asList("$_id.apiInfoKey.apiCollectionId"));
        }};

        Map<CollectionType, MCollection<?>[]> collectionsMap = ApiCollectionUsers.COLLECTIONS_WITH_API_COLLECTION_ID;

        Bson filter = Filters.exists(SingleTypeInfo._COLLECTION_IDS, false);

        for(Map.Entry<CollectionType, MCollection<?>[]> collections : collectionsMap.entrySet()){

            List<Bson> update = Arrays.asList(
                            Updates.set(SingleTypeInfo._COLLECTION_IDS, 
                            matchKeyMap.get(collections.getKey())));

            if(collections.getKey().equals(CollectionType.ApiCollectionId)){
                ApiCollectionUsers.updateCollectionsInBatches(collections.getValue(), filter, update);
            } else {
                ApiCollectionUsers.updateCollections(collections.getValue(), filter, update);
            }
        }
    }

    public void updateCustomCollections() {
        List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        for (ApiCollection apiCollection : apiCollections) {
            if (ApiCollection.Type.API_GROUP.equals(apiCollection.getType())) {
                List<TestingEndpoints> conditions = apiCollection.getConditions();

                // Don't update API groups that are delta update based
                boolean isDeltaUpdateBasedApiGroup = false;
                for (TestingEndpoints testingEndpoints : conditions) {
                    if (TestingEndpoints.checkDeltaUpdateBased(testingEndpoints.getType())) {
                        isDeltaUpdateBasedApiGroup = true;
                        break;
                    }
                }

                if (isDeltaUpdateBasedApiGroup) {
                    continue;
                }

                ApiCollectionUsers.computeCollectionsForCollectionId(conditions, apiCollection.getId());
            }
        }
    }

    private static void sendEventsToIntercom () throws Exception {
        EventsMetrics lastEventsMetrics = EventsMetricsDao.instance.findLatestOne(Filters.empty());
        EventsMetrics currentEventsMetrics = new EventsMetrics(
            false, new EventsExample(0, new ArrayList<>()), new HashMap<>(), new HashMap<>(), new HashMap<>(), 0, Context.now());
            
        int lastSentEpoch = 0;
        int lastApisCount = 0;
        if(lastEventsMetrics != null){
            lastSentEpoch = lastEventsMetrics.getCreatedAt();
            if(lastEventsMetrics.getMilestones() != null){
                lastApisCount = lastEventsMetrics.getMilestones().get(EventsMetrics.APIS_INFO_COUNT);
            }
        }
        int timeNow = Context.now();
        if(timeNow - lastSentEpoch >= (24 * 60 * 60)){
            IntercomEventsUtil.fillMetaDataForIntercomEvent(lastApisCount, currentEventsMetrics);
            IntercomEventsUtil.sendPostureMapToIntercom(lastSentEpoch, currentEventsMetrics);
            int businessLogicTests = YamlTemplateDao.instance.getNewCustomTemplates(lastSentEpoch);
            if(businessLogicTests > 0){
                currentEventsMetrics.setCustomTemplatesCount(businessLogicTests);
            }

            Map<String, Integer> issuesMap = TestingRunIssuesDao.instance.countIssuesMapForPrivilegeEscalations(lastSentEpoch);
            if(!issuesMap.isEmpty()){
                currentEventsMetrics.setSecurityTestFindings(issuesMap);
            }
            currentEventsMetrics.setCreatedAt(timeNow);
            EventsMetricsDao.instance.insertOne(currentEventsMetrics);
        }

        EventMetricsAction eventMetricsAction = new EventMetricsAction();
        eventMetricsAction.sendEventsToAllUsersInAccount(currentEventsMetrics);
    }

    public void setUpUpdateCustomCollections() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            updateCustomCollections();
                        } catch (Exception e){
                            logger.errorAndAddToDb(e, "Error while updating custom collections: " + e.getMessage(), LogDb.DASHBOARD);
                        }

                        try {
                            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(
                                AccountSettingsDao.generateFilter()
                            );
                            if(accountSettings != null && accountSettings.getAllowSendingEventsToIntercom()){
                                sendEventsToIntercom() ;
                            }
                        } catch (Exception e) {
                            logger.errorAndAddToDb(e, "Error while reporting event to Intercom: " + e.getMessage(), LogDb.DASHBOARD);
                        }
                    }
                }, "update-custom-collections");
            }
        }, 0, 24, TimeUnit.HOURS);
    }

    public static void fetchIntegratedConnections(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getComputeIntegratedConnections() == 0){
            Map<String,ConnectionInfo> infoMap = new HashMap<>();
            
            // check if mirroring is enabled for getting traffic.
            ApiCollection collection = ApiCollectionsDao.instance.findOne(Filters.exists(ApiCollection.HOST_NAME));
            if(collection != null){
                infoMap.put(ConnectionInfo.AUTOMATED_TRAFFIC, new ConnectionInfo(0,true));
            }

            // check if slack alerts are activated
            int countWebhooks = (int) SlackWebhooksDao.instance.getMCollection().countDocuments();
            if (countWebhooks > 0) {
                infoMap.put(ConnectionInfo.SLACK_ALERTS, new ConnectionInfo(0,true));
            }

            //check for github SSO,
            if (ConfigsDao.instance.findOne("_id", "GITHUB-ankush") != null) {
                infoMap.put(ConnectionInfo.GITHUB_SSO, new ConnectionInfo(0,true));
            }

            //check for team members
            if(UsersDao.instance.getMCollection().countDocuments() > 1){
                infoMap.put(ConnectionInfo.INVITE_MEMBERS, new ConnectionInfo(0,true));
            }

            //check for CI/CD pipeline
            StartTestAction testAction = new StartTestAction();
            int cicdRunsCount = (int) TestingRunDao.instance.getMCollection().countDocuments(Filters.in(Constants.ID, testAction.getCicdTests()));
            if(cicdRunsCount > 0){
                infoMap.put(ConnectionInfo.CI_CD_INTEGRATIONS, new ConnectionInfo(0,true));
            }

            AccountSettingsDao.instance.getMCollection().updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.CONNECTION_INTEGRATIONS_INFO, infoMap),
                new UpdateOptions().upsert(true)
            );

            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.COMPUTE_INTEGRATED_CONNECTIONS, Context.now())
            );
        }
    }

    private static void checkMongoConnection() throws Exception {
        AccountsDao.instance.getStats();
        connectedToMongo = true;
    }

    public static void setSubdomain(){
        if (System.getenv("AKTO_SUBDOMAIN") != null) {
            subdomain = System.getenv("AKTO_SUBDOMAIN");
        }

        subdomain += "/signup-google";
    }

    public static boolean isNotKubernetes() {
        return !DashboardMode.isKubernetes();
    } 


    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        setSubdomain();
        DictionaryFilter.readDictionaryBinary();

        String https = System.getenv("AKTO_HTTPS_FLAG");
        if (Objects.equals(https, "true")) {
            sce.getServletContext().getSessionCookieConfig().setSecure(true);
        }

        logger.debug("context initialized");

        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        logger.debug("MONGO URI " + mongoURI);

        try {
            readAndSaveBurpPluginVersion();
        } catch (Exception e) {
            e.printStackTrace();
        }

        boolean runJobFunctions = JobUtils.getRunJobFunctions();
        boolean runJobFunctionsAnyway = JobUtils.getRunJobFunctionsAnyway();

        executorService.schedule(new Runnable() {
            public void run() {

                ReadPreference readPreference = ReadPreference.primary();
                WriteConcern writeConcern = WriteConcern.ACKNOWLEDGED;
                if (runJobFunctions || DashboardMode.isSaasDeployment()) {
                    readPreference = ReadPreference.primary();
                    writeConcern = WriteConcern.W1;
                }
                DaoInit.init(new ConnectionString(mongoURI), readPreference, writeConcern);

                connectedToMongo = false;
                do {
                    connectedToMongo = MCollection.checkConnection();
                    if (!connectedToMongo) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {}
                    }
                } while (!connectedToMongo);

                if (DashboardMode.isOnPremDeployment()) {
                    Context.accountId.set(1_000_000);
                    logger.debugAndAddToDb("Dashboard started at " + Context.now());
                }

                setDashboardMode();
                updateGlobalAktoVersion();

                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        AccountSettingsDao.instance.getStats();
                        Intercom.setToken(System.getenv("INTERCOM_TOKEN"));
                        setDashboardVersionForAccount();
                    }
                }, "context-initializer");

                SingleTypeInfo.init();

                int now = Context.now();
                if (runJobFunctions || runJobFunctionsAnyway) {

                    logger.debug("Starting init functions and scheduling jobs at " + now);

                    AccountTask.instance.executeTask(new Consumer<Account>() {
                        @Override
                        public void accept(Account account) {
                            runInitializerFunctions();
                        }
                    }, "context-initializer-secondary");
                    logger.warn("Started webhook schedulers", LogDb.DASHBOARD);
                    setUpWebhookScheduler();
                    logger.warn("Started traffic alert schedulers", LogDb.DASHBOARD);
                    setUpTrafficAlertScheduler();
                    logger.warn("Started daily schedulers", LogDb.DASHBOARD);
                    setUpDailyScheduler();
                    if (DashboardMode.isMetered()) {
                        setupUsageScheduler();
                    }
                    updateSensitiveInfoInApiInfo.setUpSensitiveMapInApiInfoScheduler();
                    syncCronInfo.setUpUpdateCronScheduler();
                    setUpTestEditorTemplatesScheduler();
                    JobsCron.instance.jobsScheduler(JobExecutorType.DASHBOARD);
                    updateApiGroupsForAccounts(); 
                    setupAutomatedApiGroupsScheduler();
                    if(runJobFunctionsAnyway) {
                        crons.trafficAlertsScheduler();
                        crons.insertHistoricalDataJob();
                        if(DashboardMode.isOnPremDeployment()){
                            crons.insertHistoricalDataJobForOnPrem();
                        }

                        trimCappedCollectionsJob();
                        setUpPiiAndTestSourcesScheduler();
                        cleanInventoryJobRunner();
                        setUpDefaultPayloadRemover();
                        setUpDependencyFlowScheduler();
                        tokenGeneratorCron.tokenGeneratorScheduler();
                        crons.deleteTestRunsScheduler();
                        setUpUpdateCustomCollections();
                        setUpFillCollectionIdArrayJob();
                                               

                        // CleanInventory.cleanInventoryJobRunner();

                        // MatchingJob.MatchingJobRunner();
                    }

                    int now2 = Context.now();
                    int diffNow = now2 - now;
                    logger.debug(String.format("Completed init functions and scheduling jobs at %d , time taken : %d", now2, diffNow));
                } else {
                    logger.debug("Skipping init functions and scheduling jobs at " + now);
                }
                // setUpAktoMixpanelEndpointsScheduler();
                //fetchGithubZip();
                if(isSaas){
                    try {
                        Auth0.getInstance();
                        logger.debugAndAddToDb("Auth0 initialized", LogDb.DASHBOARD);
                    } catch (Exception e) {
                        logger.errorAndAddToDb("Failed to initialize Auth0 due to: " + e.getMessage(), LogDb.DASHBOARD);
                    }
                }

                // run backward fill job for query params
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        if(t.getId() == 1000000 || t.getId() == 1718042191 || t.getId() == 1736798101){
                            Context.accountId.set(t.getId());
                            Context.contextSource.set(com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE.API);
                            logger.infoAndAddToDb("Starting backfill query params for account " + t.getId());
                            int now = Context.now();
                            BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(Filters.empty());
                            if(backwardCompatibility.getFillQueryParams() == 0){
                                BackwardCompatibilityDao.instance.updateOne(
                                    Filters.eq("_id", backwardCompatibility.getId()),
                                    Updates.set("fillQueryParams", Context.now())
                                );
                                try {
                                    List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(
                                        Filters.and(
                                            Filters.ne(ApiCollection._DEACTIVATED, true),
                                            Filters.exists(ApiCollection.HOST_NAME, true)
                                        ), Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME)
                                    );
                                    logger.infoAndAddToDb("Fetched " + apiCollections.size() + " api collections");
                                    SingleTypeInfo.fetchCustomDataTypes(t.getId());
                                    for(ApiCollection apiCollection : apiCollections){
                                        SampleDataDao.instance.backFillIsQueryParamInSingleTypeInfo(apiCollection.getId());
                                    }
                                    logger.infoAndAddToDb("Completed backfill query params for account " + t.getId() + " in " + (Context.now() - now) + " seconds");
                                } catch (Exception e) {
                                    logger.errorAndAddToDb(e, "Error while filling query params");
                                }
                                
                            }
                            
                        }
                    }
                }, "backfill-query-params");
            }
        }, 0, TimeUnit.SECONDS);

    }


    private void setUpDependencyFlowScheduler() {
        int minutes = DashboardMode.isOnPremDeployment() ? 60 : 24*60;
        Context.accountId.set(1000_000);
        boolean dibs = callDibs(Cluster.DEPENDENCY_FLOW_CRON,  minutes, 60);
        if(!dibs){
            logger.debugAndAddToDb("Cron for updating dependency flow not acquired, thus skipping cron", LogDb.DASHBOARD);
            return;
        }
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            DependencyFlow dependencyFlow = new DependencyFlow();
                            logger.debugAndAddToDb("Starting dependency flow");
                            dependencyFlow.run(null);
                            if (dependencyFlow.resultNodes != null) {
                                logger.debugAndAddToDb("Result nodes size: " + dependencyFlow.resultNodes.size());
                            }
                            dependencyFlow.syncWithDb();
                            logger.debugAndAddToDb("Finished running dependency flow");
                        } catch (Exception e) {
                            logger.errorAndAddToDb(e,
                                    String.format("Error while running dependency flow %s", e.toString()),
                                    LogDb.DASHBOARD);
                        }
                    }
                }, "dependency_flow");
            }
        }, 0, minutes, TimeUnit.MINUTES);
    }

    private void updateGlobalAktoVersion() {
        try (InputStream in = getClass().getResourceAsStream("/version.txt")) {
            if (in != null) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
                bufferedReader.readLine();
                bufferedReader.readLine();
                InitializerListener.aktoVersion = bufferedReader.readLine();
            } else  {
                throw new Exception("Input stream null");
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error updating global akto version : " + e.getMessage(), LogDb.DASHBOARD );
        }
    }

    private void trimCappedCollectionsJob() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                logger.debug("Starting trimCappedCollectionsJob for all accounts at " + Context.now());

                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            trimCappedCollections();
                        } catch (Exception e) {
                            logger.errorAndAddToDb(e,
                                    String.format("Error while running trimCappedCollections for account %d %s",
                                            Context.accountId.get(), e.toString()),
                                    LogDb.DASHBOARD);
                        }
                    }
                }, "trim-capped-collections");

                logger.debug("Completed trimCappedCollectionsJob for all accounts at " + Context.now());
            }
        }, 0,1, TimeUnit.DAYS);
    }

    public static void trimCappedCollections() {
        clear(LoadersDao.instance, LoadersDao.maxDocuments);
        clear(RuntimeLogsDao.instance, RuntimeLogsDao.maxDocuments);
        clear(LogsDao.instance, LogsDao.maxDocuments);
        clear(PupeteerLogsDao.instance, PupeteerLogsDao.maxDocuments);
        clear(DashboardLogsDao.instance, DashboardLogsDao.maxDocuments);
        clear(DataIngestionLogsDao.instance, DataIngestionLogsDao.maxDocuments);
        clear(TrafficMetricsDao.instance, TrafficMetricsDao.maxDocuments);
        clear(AnalyserLogsDao.instance, AnalyserLogsDao.maxDocuments);
        clear(ActivitiesDao.instance, ActivitiesDao.maxDocuments);
        clear(BillingLogsDao.instance, BillingLogsDao.maxDocuments);
        /*
         * Removed the clear/delete function for Testing run results,
         * as it might delete vulnerable test results as well.
         * We eventually delete testing run results directly from the testing module.
         */
        clear(SuspectSampleDataDao.instance, SuspectSampleDataDao.maxDocuments);
        clear(RuntimeMetricsDao.instance, RuntimeMetricsDao.maxDocuments);
        clear(ProtectionLogsDao.instance, ProtectionLogsDao.maxDocuments);
        clear(MetricDataDao.instance, MetricDataDao.maxDocuments);
    }

    public static void clear(AccountsContextDao mCollection, int maxDocuments) {
        try {
            /*
             * If capped collections are allowed (mongoDB) and 
             * the collection is actually capped, only then skip.
             */
            if (DbMode.allowCappedCollections() && mCollection.isCapped()) return;

            /*
             * Primarily for initial cleanup. 
             * In cases the data up till now has accumulated to a very large size,
             * we do not want to overwhelm the db in those cases
             * as .drop() is far cheaper operation that .deleteMany()
             * and the data is majorly transient
             */
            boolean droppedIfGreaterThanThreeTimes = false;
            if(DbMode.allowCappedCollections()){
                droppedIfGreaterThanThreeTimes = mCollection.dropCollectionWithCondition(maxDocuments, 3);
            }

            if(droppedIfGreaterThanThreeTimes){
                /*
                 * Not creating capped collections again,
                 * because eventually we want to move away from capped collections 
                 * because they add serious startup time to mongo.
                 */
                return;
            }

            mCollection.trimCollection(maxDocuments);
        } catch (Exception e) {
            logger.errorAndAddToDb("Error while trimming collection " + mCollection.getCollName() + " : " + e.getMessage(), LogDb.DASHBOARD);
        }
    }

    public static Set<String> getAktoDefaultTestLibs() {
        return new HashSet<>(Arrays.asList("akto-api-security/tests-library:standard", "akto-api-security/tests-library:pro"));
    }

    public static Set<String> getAktoDefaultThreatPolicies() {
        return new HashSet<>(Arrays.asList("akto-api-security/tests-library:threat_policies_pro"));
    }

    public static void insertStateInAccountSettings(AccountSettings accountSettings) {
        if (DashboardMode.isMetered()) {
            insertAktoTestLibraries(accountSettings);
            insertAktoThreatPolicies(accountSettings);
        }
        
        insertCompliances(accountSettings);
    }

    private static void insertCompliances(AccountSettings accountSettings) {
        boolean complianceInfosAlreadyPresent = accountSettings != null && accountSettings.getComplianceInfosUpdatedTs() > 0;

        if (!complianceInfosAlreadyPresent) {
            AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.COMPLIANCE_INFOS_UPDATED_TS, 1));
        }
    }

    private static void insertAktoTestLibraries(AccountSettings accountSettings) {
        List<TestLibrary> testLibraries = accountSettings == null ? new ArrayList<>() : accountSettings.getTestLibraries();
        Set<String> aktoTestLibraries = getAktoDefaultTestLibs();

        if (testLibraries != null) {
            for (TestLibrary testLibrary: testLibraries) {
                String author = testLibrary.getAuthor();
                if (author.equals(Constants._AKTO)) {
                    aktoTestLibraries.remove(testLibrary.getRepositoryUrl());
                }
            }
        }

        for (String pendingLib: aktoTestLibraries) {
            AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), 
                Updates.addToSet(AccountSettings.TEST_LIBRARIES, new TestLibrary(pendingLib, Constants._AKTO, Context.now())));
        }
    }

    private static void insertAktoThreatPolicies(AccountSettings accountSettings) {
        List<TestLibrary> testLibraries = accountSettings == null ? new ArrayList<>() : accountSettings.getThreatPolicies();
        Set<String> aktoTestLibraries = getAktoDefaultThreatPolicies();

        if (testLibraries != null) {
            for (TestLibrary testLibrary: testLibraries) {
                String author = testLibrary.getAuthor();
                if (author.equals(Constants._AKTO)) {
                    aktoTestLibraries.remove(testLibrary.getRepositoryUrl());
                }
            }
        }

        for (String pendingLib: aktoTestLibraries) {
            AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), 
                Updates.addToSet(AccountSettings.THREAT_POLICIES, new TestLibrary(pendingLib, Constants._AKTO, Context.now())));
        }
    }

    public static void insertPiiSources(){
        Map<String, String> map = new HashMap<>();
        String fileUrl = "https://raw.githubusercontent.com/akto-api-security/pii-types/master/general.json";
        map.put(fileUrl, "general.json");
        if (PIISourceDao.instance.findOne("_id", "A") == null) {
            PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
            piiSource.setId("A");
            PIISourceDao.instance.insertOne(piiSource);
        }

        fileUrl = "https://raw.githubusercontent.com/akto-api-security/akto/master/pii-types/fintech.json";
        map.put(fileUrl, "fintech.json");
        if (PIISourceDao.instance.findOne("_id", "Fin") == null) {
            PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
            piiSource.setId("Fin");
            PIISourceDao.instance.insertOne(piiSource);
        }

        fileUrl = "https://raw.githubusercontent.com/akto-api-security/akto/master/pii-types/filetypes.json";
        map.put(fileUrl, "filetypes.json");
        if (PIISourceDao.instance.findOne("_id", "File") == null) {
            PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
            piiSource.setId("File");
            PIISourceDao.instance.insertOne(piiSource);
        }

        if(piiFileMap == null){
            piiFileMap = Collections.unmodifiableMap(map);
        }
    }

    static boolean executedOnce = false;

    private final static int REFRESH_INTERVAL = 60 * 15; // 15 minute

    public static Organization fetchAndSaveFeatureWiseAllowed(Organization organization) {
        
        int lastFeatureMapUpdate = organization.getLastFeatureMapUpdate();
        if((lastFeatureMapUpdate + REFRESH_INTERVAL) >= Context.now()){
            return organization;
        }
        HashMap<String, FeatureAccess> featureWiseAllowed = new HashMap<>();

        try {
            int gracePeriod = organization.getGracePeriod();
            String hotjarSiteId = organization.getHotjarSiteId();
            String planType = organization.getplanType();
            String trialMsg = organization.gettrialMsg();
            String protectionTrialMsg = organization.getprotectionTrialMsg();
            String agentTrialMsg = organization.getagentTrialMsg();
            String organizationId = organization.getId();
            /*
             * This ensures, we don't fetch feature wise allowed from akto too often.
             * This helps the dashboard to be more responsive.
             */

            HashMap<String, FeatureAccess> initialFeatureWiseAllowed = organization.getFeatureWiseAllowed();
            if (initialFeatureWiseAllowed == null) {
                initialFeatureWiseAllowed = new HashMap<>();
            }

            logger.debugAndAddToDb("Fetching featureMap",LogDb.DASHBOARD);

            BasicDBList entitlements = OrganizationUtils.fetchEntitlements(organizationId,
                    organization.getAdminEmail());

            featureWiseAllowed = OrganizationUtils.getFeatureWiseAllowed(entitlements);
            Set<Integer> accounts = organization.getAccounts();
            featureWiseAllowed = UsageMetricHandler.updateFeatureMapWithLocalUsageMetrics(featureWiseAllowed, accounts);

            logger.debugAndAddToDb(String.format("Processed %s features", featureWiseAllowed.size()),LogDb.DASHBOARD);

            if (featureWiseAllowed.isEmpty()) {
                /*
                 * If feature map unavailable and account belongs b/w
                 * 1724544000 -> Sunday, August 25, 2024 12:00:00 AM GMT
                 * 1724976000 -> Friday, August 30, 2024 12:00:00 AM GMT
                 * 
                 * then attempt to recreate org in stigg.
                 */
                if(accounts!=null && !accounts.isEmpty()){
                    int accountId = accounts.iterator().next();
                    if (accountId > 1724544000 && accountId < 1724976000) {
                        try {
                            String res = StiggReporterClient.instance.provisionCustomer(organization);
                            if(!res.contains("err")){
                                logger.debugAndAddToDb(String.format("Created org and set subs in stigg %s accountId : %s email: %s", organization.getId(), accountId, organization.getAdminEmail()));
                            }
                        } catch (Exception e) {
                            logger.errorAndAddToDb(e, String.format("Unable to create org in stigg %s accountId : %s email: %s error: %s", organization.getId(), accountId, organization.getAdminEmail(), e.toString()));
                        }
                    }
                }
            }

            for (Map.Entry<String, FeatureAccess> entry : featureWiseAllowed.entrySet()) {
                String label = entry.getKey();
                FeatureAccess value = entry.getValue();
                if (initialFeatureWiseAllowed.containsKey(label)) {
                    FeatureAccess initialValue = initialFeatureWiseAllowed.get(label);
                    if (initialValue.getOverageFirstDetected() != -1 &&
                            value.getOverageFirstDetected() != -1 &&
                            initialValue.getOverageFirstDetected() < value.getOverageFirstDetected()) {
                        value.setOverageFirstDetected(initialValue.getOverageFirstDetected());
                    }
                    featureWiseAllowed.put(label, value);
                }
            }

            logger.debugAndAddToDb("Fetching org metadata",LogDb.DASHBOARD);

            BasicDBObject metaData = OrganizationUtils.fetchOrgMetaData(organizationId, organization.getAdminEmail());
            gracePeriod = OrganizationUtils.fetchOrgGracePeriodFromMetaData(metaData);
            hotjarSiteId = OrganizationUtils.fetchHotjarSiteId(metaData);
            planType = OrganizationUtils.fetchplanType(metaData);
            trialMsg = OrganizationUtils.fetchtrialMsg(metaData);
            protectionTrialMsg = OrganizationUtils.fetchprotectionTrialMsg(metaData);
            agentTrialMsg = OrganizationUtils.fetchagentTrialMsg(metaData);
            boolean expired = OrganizationUtils.fetchExpired(metaData);
            if (DashboardMode.isOnPremDeployment()) {
                boolean telemetryEnabled = OrganizationUtils.fetchTelemetryEnabled(metaData);
                setTelemetrySettings(organization, telemetryEnabled);
            }
            boolean testTelemetryEnabled = OrganizationUtils.fetchTestTelemetryEnabled(metaData);
            organization.setTestTelemetryEnabled(testTelemetryEnabled);

            logger.debugAndAddToDb("Processed org metadata",LogDb.DASHBOARD);

            organization.setHotjarSiteId(hotjarSiteId);
            
            organization.setplanType(planType);

            organization.settrialMsg(trialMsg);

            organization.setprotectionTrialMsg(protectionTrialMsg);

            organization.setagentTrialMsg(agentTrialMsg);

            organization.setGracePeriod(gracePeriod);
            organization.setFeatureWiseAllowed(featureWiseAllowed);
            organization.setExpired(expired);

            /*
             * only update this field if we were able to update
             * i.e. if we were able to reach akto
             * or this is the first time being updated.
             */
            if (lastFeatureMapUpdate == 0 || (featureWiseAllowed != null && !featureWiseAllowed.isEmpty())) {
                lastFeatureMapUpdate = Context.now();
            }
            organization.setLastFeatureMapUpdate(lastFeatureMapUpdate);

            OrganizationsDao.instance.updateOne(
                    Filters.eq(Constants.ID, organizationId),
                    Updates.combine(
                            Updates.set(Organization.FEATURE_WISE_ALLOWED, featureWiseAllowed),
                            Updates.set(Organization.GRACE_PERIOD, gracePeriod),
                            Updates.set(Organization._EXPIRED, expired),
                            Updates.set(Organization.HOTJAR_SITE_ID, hotjarSiteId),
                            Updates.set(Organization.PLAN_TYPE, planType),
                            Updates.set(Organization.TRIAL_MSG, trialMsg),
                            Updates.set(Organization.AGENTTRIAL_MSG, agentTrialMsg),
                            Updates.set(Organization.PROTECTIONTRIAL_MSG, protectionTrialMsg),
                            Updates.set(Organization.TEST_TELEMETRY_ENABLED, testTelemetryEnabled),
                            Updates.set(Organization.LAST_FEATURE_MAP_UPDATE, lastFeatureMapUpdate)));

            logger.debugAndAddToDb("Updated org",LogDb.DASHBOARD);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, aktoVersion + " error while fetching feature wise allowed: " + e.toString(),
                    LogDb.DASHBOARD);
        }

        return organization;
    }

    private static void setTelemetrySettings(Organization organization, boolean telemetryEnabled){
        Set<Integer> accounts = organization.getAccounts();
        if(accounts ==null || accounts.isEmpty()){
            organization = OrganizationsDao.instance.findOne(Filters.eq(Organization.ID, organization.getId()));
            accounts = organization.getAccounts();
        }

        if(accounts == null || accounts.isEmpty()){
            logger.debugAndAddToDb(String.format("No accounts found for %s, skipping telemetry settings processing", organization.getId()), LogDb.DASHBOARD);
            return;
        }

        for (Integer accountId : accounts) {
            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(eq("_id", accountId));

            if(accountSettings == null){
                logger.debugAndAddToDb(String.format("No account settings found for %s account", accountId), LogDb.DASHBOARD);
                continue;
            }

            TelemetrySettings settings = accountSettings.getTelemetrySettings();

            if(settings == null){
                logger.debugAndAddToDb(String.format("No telemetry settings found for %s account", accountId), LogDb.DASHBOARD);
                continue;
            }

            if(settings.getStiggEnabled() != telemetryEnabled){
                logger.debugAndAddToDb(String.format("Current stigg setting: %s, new stigg setting: %s for accountId: %d", settings.getStiggEnabled(), telemetryEnabled, accountId), LogDb.DASHBOARD);
                settings.setStiggEnabled(telemetryEnabled);
                settings.setStiggEnabledAt(Context.now());
                accountSettings.setTelemetrySettings(settings);
                AccountSettingsDao.instance.updateOne(eq("_id", accountId), Updates.set(AccountSettings.TELEMETRY_SETTINGS, accountSettings.getTelemetrySettings()));
            }
            else {
                logger.debugAndAddToDb(String.format("Current stigg setting: %s, new stigg setting: %s for accountId: %d are same, not taking any action", settings.getStiggEnabled(), telemetryEnabled, accountId), LogDb.DASHBOARD);
            }
        }
    }

    private static void setOrganizationsInBilling(BackwardCompatibility backwardCompatibility) {
        backwardCompatibility.setOrgsInBilling(0);
        if (backwardCompatibility.getOrgsInBilling() == 0) {
            if (!executedOnce) {
                logger.debugAndAddToDb("in execute setOrganizationsInBilling", LogDb.DASHBOARD);
                OrganizationTask.instance.executeTask(new Consumer<Organization>() {
                    @Override
                    public void accept(Organization organization) {
                        if (!organization.getSyncedWithAkto()) {
                            logger.debugAndAddToDb("Syncing Akto billing for org: " + organization.getId(), LogDb.DASHBOARD);
                            syncOrganizationWithAkto(organization);
                        }
                    }
                }, "set-orgs-in-billing");

                executedOnce = true;
                BackwardCompatibilityDao.instance.updateOne(
                        Filters.eq("_id", backwardCompatibility.getId()),
                        Updates.set(BackwardCompatibility.ORGS_IN_BILLING, Context.now())
                );
            }
        }
    }

    private static void backFillDiscovered() {

        long count = ApiInfoDao.instance.count(Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false));
        if (count == 0) {
            logger.warnAndAddToDb("No need to backFillDiscovered for accountId: " + Context.accountId.get());
            return;
        }

        logger.warnAndAddToDb("Running back fill discovered for accountId: "  + Context.accountId.get());
        int startTime = Context.now();

        List<SingleTypeInfo> singleTypeInfos = new ArrayList<>();
        ObjectId id = null;
        Bson sort = Sorts.ascending("_id");
        do {
            Map<ApiInfo.ApiInfoKey, ApiInfo> apiInfoMap = new HashMap<>();
            Bson idFilter = id == null ? Filters.empty() : Filters.gt("_id", id);
            singleTypeInfos = SingleTypeInfoDao.instance.findAll(idFilter, 0, 100_000, sort, Projections.include(SingleTypeInfo._TIMESTAMP, SingleTypeInfo._URL, SingleTypeInfo._API_COLLECTION_ID, SingleTypeInfo._METHOD));
            for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
                id = singleTypeInfo.getId();
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(singleTypeInfo.getApiCollectionId(), singleTypeInfo.getUrl(), Method.fromString(singleTypeInfo.getMethod()));
                ApiInfo apiInfo = apiInfoMap.getOrDefault(apiInfoKey, new ApiInfo(apiInfoKey));
                if (apiInfo.getDiscoveredTimestamp() == 0 || apiInfo.getDiscoveredTimestamp() > singleTypeInfo.getTimestamp()) {
                    apiInfo.setDiscoveredTimestamp(singleTypeInfo.getTimestamp());
                }
                apiInfoMap.put(apiInfoKey, apiInfo);
            }

            List<WriteModel<ApiInfo>> updates = new ArrayList<>();
            for (ApiInfo apiInfo: apiInfoMap.values()) {
                updates.add(
                        new UpdateOneModel<>(
                                Filters.and(
                                    ApiInfoDao.getFilter(apiInfo.getId()),
                                    Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false)
                                ),
                                Updates.set(ApiInfo.DISCOVERED_TIMESTAMP, apiInfo.getDiscoveredTimestamp()),
                                new UpdateOptions().upsert(false)
                        )
                );

                updates.add(
                        new UpdateOneModel<>(
                                Filters.and(
                                    ApiInfoDao.getFilter(apiInfo.getId()),
                                    Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, true)
                                ),
                                Updates.min(ApiInfo.DISCOVERED_TIMESTAMP, apiInfo.getDiscoveredTimestamp()),
                                new UpdateOptions().upsert(false)
                        )
                );
            }
            if (!updates.isEmpty()) ApiInfoDao.instance.getMCollection().bulkWrite(updates);

        } while (!singleTypeInfos.isEmpty());

        logger.warnAndAddToDb("Finished running back fill discovered for accountId: "  + Context.accountId.get() + " in: " + (Context.now() - startTime) + " seconds");
    }

    private static void backFillStatusCodeType() {
        long count = ApiInfoDao.instance.count(Filters.exists(ApiInfo.API_TYPE, false));
        if (count == 0) {
            logger.warnAndAddToDb("No need to run backFillStatusCodeType");
            return;
        }

        logger.warnAndAddToDb("Running backFillStatusCodeType for accountId: " + Context.accountId.get());

        List<SampleData> sampleDataList = new ArrayList<>();
        Bson sort = Sorts.ascending("_id.apiCollectionId", "_id.url", "_id.method");
        int skip = 0;
        int startTime = Context.now();
        do {
            sampleDataList = SampleDataDao.instance.findAll(Filters.empty(), skip, 100, sort);
            skip += sampleDataList.size();
            List<ApiInfo> apiInfoList = new ArrayList<>();
            for (SampleData sampleData: sampleDataList) {
                Key id = sampleData.getId();
                List<String> samples = sampleData.getSamples();
                if (samples == null || samples.isEmpty()) continue;
                ApiInfo apiInfo = new ApiInfo(new ApiInfo.ApiInfoKey(id.getApiCollectionId(), id.getUrl(), id.getMethod()));
                apiInfo.setResponseCodes(new ArrayList<>());
                for (String sample: samples) {
                    try {
                        HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(sample);

                        int statusCode = httpResponseParams.getStatusCode();
                        if (!apiInfo.getResponseCodes().contains(statusCode)) {
                            apiInfo.getResponseCodes().add(statusCode);
                        }

                        ApiInfo.ApiType apiType = ApiInfo.findApiTypeFromResponseParams(httpResponseParams);
                        apiInfo.setApiType(apiType);
                    } catch (Exception e) {
                        continue;
                    }
                }
                apiInfoList.add(apiInfo);
            }

            List<WriteModel<ApiInfo>> updates = new ArrayList<>();
            for (ApiInfo apiInfo: apiInfoList) {
                List<Bson> subUpdates = new ArrayList<>();

                // response codes
                subUpdates.add(Updates.addEachToSet(ApiInfo.RESPONSE_CODES, apiInfo.getResponseCodes()));

                // api type
                subUpdates.add(Updates.set(ApiInfo.API_TYPE, apiInfo.getApiType()));
                updates.add(
                        new UpdateOneModel<>(
                                ApiInfoDao.getFilter(apiInfo.getId()),
                                Updates.combine(subUpdates),
                                new UpdateOptions().upsert(false)
                        )
                );
            }
            if (!updates.isEmpty()) ApiInfoDao.instance.getMCollection().bulkWrite(updates);

        } while (!sampleDataList.isEmpty());

        logger.warnAndAddToDb("Finished running backFillStatusCodeType for accountId: " + Context.accountId.get() + " in: " + (Context.now() - startTime) + " seconds");
    }

    private static void dropLastCronRunInfoField(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getDeleteLastCronRunInfo() == 0){
            AccountSettingsDao.instance.updateOne(Filters.empty(), Updates.unset(AccountSettings.LAST_UPDATED_CRON_INFO));

            BackwardCompatibilityDao.instance.updateOne(
                        Filters.eq("_id", backwardCompatibility.getId()),
                        Updates.set(BackwardCompatibility.DELETE_LAST_CRON_RUN_INFO, Context.now())
                );
        }
    }

    private static void makeFirstUserAdmin(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getAddAdminRoleIfAbsent() < 1733228772){
           
            User firstUser = UsersDao.instance.getFirstUser(Context.accountId.get());
            if(firstUser == null){
                return;
            }

            RBAC firstUserAdminRbac = RBACDao.instance.findOne(Filters.and(
                Filters.eq(RBAC.USER_ID, firstUser.getId()),
                Filters.eq(RBAC.ROLE, Role.ADMIN.name())
            ));

            if(firstUserAdminRbac != null){
                logger.debugAndAddToDb("Found admin rbac for first user: " + firstUser.getLogin() + " , thus deleting it's member role RBAC", LogDb.DASHBOARD);
                RBACDao.instance.deleteAll(Filters.and(
                    Filters.eq(RBAC.USER_ID, firstUser.getId()),
                    Filters.eq(RBAC.ROLE, Role.MEMBER.name())
                ));
            }else{
                logger.debugAndAddToDb("Found non-admin rbac for first user: " + firstUser.getLogin() + " , thus inserting admin role", LogDb.DASHBOARD);
                RBACDao.instance.insertOne(
                    new RBAC(firstUser.getId(), Role.ADMIN.name(), Context.accountId.get())
                );
            }

            BackwardCompatibilityDao.instance.updateOne(
                        Filters.eq("_id", backwardCompatibility.getId()),
                        Updates.set(BackwardCompatibility.ADD_ADMIN_ROLE, Context.now())
                );
        }
    }

    private static void addDefaultAdvancedFilters(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getAddDefaultFilters() == 0 || backwardCompatibility.getAddDefaultFilters() < 1734502264){
            String contentAllow = "id: DEFAULT_ALLOW_FILTER\nfilter:\n    url:\n        regex: '.*'";
            String contentBlock = "id: DEFAULT_BLOCK_FILTER\n" +
                                    "filter:\n" +
                                    "  or:\n" +
                                    "    - response_code:\n" +
                                    "        gte: 400\n" +
                                    "    - response_headers:\n" +
                                    "        for_one:\n" +
                                    "          key:\n" +
                                    "            eq: content-type\n" +
                                    "          value:\n" +
                                    "            contains_either:\n" +
                                    "              - html\n" +
                                    "              - text/html\n" +
                                    "    - request_headers:\n" +
                                    "        for_one:\n" +
                                    "          key:\n" +
                                    "            eq: host\n" +
                                    "          value:\n" +
                                    "            regex: .*localhost.*";

            if(!DashboardMode.isMetered()){
                contentBlock =  "id: DEFAULT_BLOCK_FILTER\nfilter:\n    response_code:\n        gte: 400";
            }


            AdvancedTrafficFiltersAction action = new AdvancedTrafficFiltersAction();
            action.setYamlContent(contentAllow);
            action.saveYamlTemplateForTrafficFilters();

            if(backwardCompatibility.getAddDefaultFilters() != 0 && DashboardMode.isMetered()){
                Bson defaultFilterQ = Filters.eq(Constants.ID, "DEFAULT_BLOCK_FILTER");
                YamlTemplate blockTemplate = AdvancedTrafficFiltersDao.instance.findOne(defaultFilterQ);
                if((blockTemplate.getUpdatedAt() - blockTemplate.getCreatedAt()) <= 10){
                    AdvancedTrafficFiltersDao.instance.deleteAll(defaultFilterQ);
                    action.setYamlContent(contentBlock);
                    action.saveYamlTemplateForTrafficFilters();
                }
            }else{
                action.setYamlContent(contentBlock);
                action.saveYamlTemplateForTrafficFilters();
            }

            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.ADD_DEFAULT_FILTERS, Context.now())
            );
        }
    }

    private static void moveAzureSamlConfig(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getMoveAzureSamlToNormalSaml() == 0){

            if(DashboardMode.isOnPremDeployment()){
                Bson filterQ = Filters.eq(Constants.ID, AzureConfig.CONFIG_ID);
                Config.AzureConfig azureConfig = (AzureConfig) ConfigsDao.instance.findOne(filterQ);
                if(azureConfig != null){
                    String adminEmail = "";
                    Organization org = OrganizationsDao.instance.findOne(Filters.empty());
                    if(org == null){
                        RBAC rbac = RBACDao.instance.findOne(Filters.eq(RBAC.ROLE, RBAC.Role.ADMIN.name()));
                        User adminUser = UsersDao.instance.findOne(Filters.eq("login", rbac.getUserId()));
                        adminEmail = adminUser.getLogin();
                    }else{
                        adminEmail = org.getAdminEmail();
                    }
                    
                    String domain = "";
                    if(!adminEmail.isEmpty()){
                        domain = OrganizationUtils.determineEmailDomain(adminEmail);
                    }

                    SAMLConfig samlConfig = SAMLConfig.convertAzureConfigToSAMLConfig(azureConfig);
                    samlConfig.setId("1000000");
                    samlConfig.setOrganizationDomain(domain);
                    if(SSOConfigsDao.instance.estimatedDocumentCount() == 0){
                        SSOConfigsDao.instance.insertOne(samlConfig);
                    }
                    ConfigsDao.instance.deleteAll(filterQ);
                }
            }
            
            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.MOVE_AZURE_SAML, Context.now())
            );
        }
    }

    private static void deleteOptionsAPIs(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getDeleteOptionsAPIs() == 0){
            List<ApiCollection> apiCollections = ApiCollectionsDao.instance.findAll(Filters.nin(Constants.ID,UsageMetricCalculator.getDeactivated()),
                                Projections.include(Constants.ID, ApiCollection.NAME, ApiCollection.HOST_NAME));
            CleanInventory.deleteOptionsAPIs(apiCollections);
            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DELETE_OPTIONS_API, Context.now())
            );
        }
    }

    private static void moveOktaOidcSSO(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getMoveOktaOidcSSO() == 0){
            String saltId = ConfigType.OKTA.name() + Config.CONFIG_SALT;
            Config.OktaConfig oktaConfig = (Config.OktaConfig) ConfigsDao.instance.findOne(
                Filters.eq(Constants.ID, saltId)
            );
            if(oktaConfig != null){
                int accountId = Context.accountId.get();
                oktaConfig.setId(OktaConfig.getOktaId(accountId));
                ConfigsDao.instance.insertOne(oktaConfig);
                ConfigsDao.instance.deleteAll(
                    Filters.eq(Constants.ID, saltId)
                );
            }
            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.MOVE_OKTA_OIDC_SSO, Context.now())
            );
        }
    }

    private static void markSummariesAsVulnerable(BackwardCompatibility backwardCompatibility){
        // case for the customers where vulnerable are stored in new collection and only testing runs are marked as new.

        if(backwardCompatibility.getMarkSummariesVulnerable() == 0){

            List<ObjectId> summaryIds = VulnerableTestingRunResultDao.instance.summaryIdsStoredForVulnerableTests();
            if(!summaryIds.isEmpty()){
                TestingRunResultSummariesDao.instance.updateMany(
                    Filters.in(Constants.ID, summaryIds), 
                    Updates.set(TestingRunResultSummary.IS_NEW_TESTING_RUN_RESULT_SUMMARY, true)
                );
            }

            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.MARK_SUMMARIES_NEW_FOR_VULNERABLE, Context.now())
            );
        }
    }

    private static void updateCustomDataTypeOperator(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getChangeOperatorConditionInCDT() == 0){
            CustomDataTypeDao.instance.updateOneNoUpsert(
                Filters.and(
                    Filters.eq(CustomDataType.NAME, "TOKEN"),
                    Filters.or(
                        Filters.exists(CustomDataType.USER_MODIFIED_TIMESTAMP, false),
                        Filters.eq(CustomDataType.USER_MODIFIED_TIMESTAMP, 0)
                    )  
                ),
                Updates.set(CustomDataType.OPERATOR, Operator.AND) 
            );

            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.CHANGE_OPERATOR_CONDITION_IN_CDT, Context.now())
            );
        }
    }

    private static void cleanupRbacEntriesForDeveloperRole(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getCleanupRbacEntries() == 0){
            int count = (int) RBACDao.instance.count(
                Filters.and(
                    Filters.eq(RBAC.ROLE, Role.DEVELOPER.name()),
                    Filters.eq(RBAC.USER_ID, 1696481097)
                )
            );
            if(count > 1){
                RBACDao.instance.deleteAll(
                    Filters.and(
                        Filters.eq(RBAC.ROLE, Role.DEVELOPER.name()),
                        Filters.eq(RBAC.USER_ID, 1696481097)
                    )
                );
                RBACDao.instance.insertOne(
                    new RBAC(1696481097, Role.DEVELOPER.name(), Context.accountId.get())
                );
            }

            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.CLEANUP_RBAC_ENTRIES, Context.now())
            );
        }
    }


    public static void setBackwardCompatibilities(BackwardCompatibility backwardCompatibility){
        if (DashboardMode.isMetered()) {
            initializeOrganizationAccountBelongsTo(backwardCompatibility);
            setOrganizationsInBilling(backwardCompatibility);
        }
        setAktoDefaultNewUI(backwardCompatibility);
        updateCustomDataTypeOperator(backwardCompatibility);
        markSummariesAsVulnerable(backwardCompatibility);
        dropLastCronRunInfoField(backwardCompatibility);
        cleanupRbacEntriesForDeveloperRole(backwardCompatibility);
        fetchIntegratedConnections(backwardCompatibility);
        dropFilterSampleDataCollection(backwardCompatibility);
        dropApiDependencies(backwardCompatibility);
        resetSingleTypeInfoCount(backwardCompatibility);
        dropWorkflowTestResultCollection(backwardCompatibility);
        readyForNewTestingFramework(backwardCompatibility);
        addAktoDataTypes(backwardCompatibility);
        updateDeploymentStatus(backwardCompatibility);
        dropAuthMechanismData(backwardCompatibility);
        moveAuthMechanismDataToRole(backwardCompatibility);
        createLoginSignupGroups(backwardCompatibility);
        createRiskScoreGroups(backwardCompatibility);
        setApiCollectionAutomatedField(backwardCompatibility);
        createAutomatedAPIGroups(backwardCompatibility);
        deleteOptionsAPIs(backwardCompatibility);
        deleteAccessListFromApiToken(backwardCompatibility);
        deleteNullSubCategoryIssues(backwardCompatibility);
        enableNewMerging(backwardCompatibility);
        setDefaultTelemetrySettings(backwardCompatibility);
        disableAwsSecretPiiType(backwardCompatibility);
        makeFirstUserAdmin(backwardCompatibility);
        dropSpecialCharacterApiCollections(backwardCompatibility);
        addDefaultAdvancedFilters(backwardCompatibility);
        moveAzureSamlConfig(backwardCompatibility);
        moveOktaOidcSSO(backwardCompatibility);
    }

    public static void printMultipleHosts(int apiCollectionId) {
        Map<SingleTypeInfo, Integer> singleTypeInfoMap = new HashMap<>();
        Bson filter = SingleTypeInfoDao.filterForHostHeader(apiCollectionId, true);
        List<SingleTypeInfo> singleTypeInfos = SingleTypeInfoDao.instance.findAll(filter, 0,10_000, Sorts.descending("timestamp"), Projections.exclude("values"));
        for (SingleTypeInfo singleTypeInfo: singleTypeInfos) {
            int count = singleTypeInfoMap.getOrDefault(singleTypeInfo, 0);
            singleTypeInfoMap.put(singleTypeInfo, count+1);
        }

        for (SingleTypeInfo singleTypeInfo: singleTypeInfoMap.keySet()) {
            Integer count = singleTypeInfoMap.get(singleTypeInfo);
            if (count == 1) continue;
            String val = singleTypeInfo.getApiCollectionId() + " " + singleTypeInfo.getUrl() + " " + URLMethods.Method.valueOf(singleTypeInfo.getMethod());
            logger.debugAndAddToDb(val + " - " + count, LoggerMaker.LogDb.DASHBOARD);
        }
    }

    public void runInitializerFunctions() {
        DaoInit.createIndices();

        BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
        if (backwardCompatibility == null) {
            backwardCompatibility = new BackwardCompatibility();
            BackwardCompatibilityDao.instance.insertOne(backwardCompatibility);
        }

        // backward compatibility
        try {
            setBackwardCompatibilities(backwardCompatibility);
            logger.warnAndAddToDb("Backward compatibilities set for " + Context.accountId.get(), LogDb.DASHBOARD);
            insertPiiSources();
            logger.warnAndAddToDb("PII sources inserted set for " + Context.accountId.get(), LogDb.DASHBOARD);

            // AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            // dropSampleDataIfEarlierNotDroped(accountSettings);

            backFillDiscovered();
            backFillStatusCodeType();
        } catch (Exception e) {
            logger.errorAndAddToDb(e,"error while setting up dashboard: " + e.toString(), LogDb.DASHBOARD);
        }

        if(DashboardMode.isOnPremDeployment()) {
            telemetryExecutorService.scheduleAtFixedRate(() -> {
                boolean dibs = callDibs(Cluster.TELEMETRY_CRON, 60, 60);
                if (!dibs) {
                    logger.debugAndAddToDb("Telemetry cron dibs not acquired, skipping telemetry cron", LoggerMaker.LogDb.DASHBOARD);
                    return;
                }
                TelemetryJob job = new TelemetryJob();
                OrganizationTask.instance.executeTask(job::run, "telemetry-cron");
            }, 0, 1, TimeUnit.MINUTES);
            logger.debugAndAddToDb("Registered telemetry cron", LogDb.DASHBOARD);
        }
    }

    private static void setDashboardVersionForAccount(){
        try {
            logger.debugAndAddToDb("Updating account version for " + Context.accountId.get(), LogDb.DASHBOARD);
            AccountSettingsDao.instance.updateVersion(AccountSettings.DASHBOARD_VERSION);
        } catch (Exception e) {
            logger.errorAndAddToDb(e,"error while updating dashboard version: " + e.toString(), LogDb.DASHBOARD);
        }
    }

    public static int burpPluginVersion = -1;

    public void readAndSaveBurpPluginVersion() {
        // todo get latest version from github
        burpPluginVersion = 5;
    }

    public static void setDashboardMode() {
        String dashboardMode = DashboardMode.getActualDashboardMode().toString();

        UpdateOptions updateOptions = new UpdateOptions();
        updateOptions.upsert(true);

        SetupDao.instance.getMCollection().updateOne(
                Filters.empty(),
                Updates.combine(
                        Updates.set("dashboardMode", dashboardMode)
                ),
                updateOptions
        );

    }

    public static void updateDeploymentStatus(BackwardCompatibility backwardCompatibility) {
        if(DashboardMode.isLocalDeployment()){
            return;
        }
        String ownerEmail = System.getenv("OWNER_EMAIL");
        if (ownerEmail == null) {
            logger.debug("Owner email missing, might be an existing customer, skipping sending an slack and mixpanel alert");
            return;
        }
        if (backwardCompatibility.isDeploymentStatusUpdated()) {
            logger.debugAndAddToDb("Deployment status has already been updated, skipping this", LogDb.DASHBOARD);
            return;
        }
        String body = "{\n    \"ownerEmail\": \"" + ownerEmail + "\",\n    \"stackStatus\": \"COMPLETED\",\n    \"cloudType\": \"AWS\"\n}";
        String headers = "{\"Content-Type\": \"application/json\"}";
        OriginalHttpRequest request = new OriginalHttpRequest(getUpdateDeploymentStatusUrl(), "", "POST", body, OriginalHttpRequest.buildHeadersMap(headers), "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, false, null, false, new ArrayList<>());
            logger.debugAndAddToDb(String.format("Update deployment status reponse: %s", response.getBody()), LogDb.DASHBOARD);
        } catch (Exception e) {
            logger.errorAndAddToDb(e,String.format("Failed to update deployment status, will try again on next boot up : %s", e.toString()), LogDb.DASHBOARD);
            return;
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DEPLOYMENT_STATUS_UPDATED, true)
        );
    }


    private static String getUpdateDeploymentStatusUrl() {
        String url = System.getenv("UPDATE_DEPLOYMENT_STATUS_URL");
        return url != null ? url : "https://stairway.akto.io/deployment/status";
    }

    public void setUpTestEditorTemplatesScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                byte[] testingTemplates = TestTemplateUtils.getTestingTemplates();
                if(testingTemplates == null){
                    logger.errorAndAddToDb("Error while fetching Test Editor Templates from Github and local", LogDb.DASHBOARD);
                    return;
                }

                try {
                    processRemedationFilesZip(testingTemplates);
                    processComplianceInfosFromZip(testingTemplates);
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.errorAndAddToDb("Unable to import remediations", LogDb.DASHBOARD);
                }
                Map<String, ComplianceInfo> complianceCommonMap = getFromCommonDb();
                Map<String, byte[]> allYamlTemplates = TestTemplateUtils.getZipFromMultipleRepoAndBranch(getAktoDefaultTestLibs());
                AccountTask.instance.executeTask((account) -> {
                    try {
                        logger.infoAndAddToDb("Updating Test Editor Templates for accountId: " + account.getId(), LogDb.DASHBOARD);
                        processTemplateFilesZip(testingTemplates, Constants._AKTO, YamlTemplateSource.AKTO_TEMPLATES.toString(), "");
                        if (!DashboardMode.isMetered()) return;

                        logger.infoAndAddToDb("Updating Pro and Standard Templates for accountId: " + account.getId(), LogDb.DASHBOARD);
                        
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());

                        if (accountSettings == null ||accountSettings.getTestLibraries() == null) return;

                        for(TestLibrary testLibrary: accountSettings.getTestLibraries()) {
                            String repoUrl = testLibrary.getRepositoryUrl();
                            if (repoUrl.contains("akto-api-security/tests-library")) {
                                byte[] zipFile = allYamlTemplates.get(testLibrary.getRepositoryUrl());
                                processTemplateFilesZip(zipFile, Constants._AKTO, YamlTemplateSource.AKTO_TEMPLATES.toString(), "");
                            }
                        }

                        if (accountSettings.getComplianceInfosUpdatedTs() > 0) {                            
                            logger.infoAndAddToDb("Updating Compliances for accountId: " + account.getId(), LogDb.DASHBOARD);
                            addComplianceFromCommonToAccount(complianceCommonMap);
                            replaceComplianceFromCommonToAccount(complianceCommonMap);    
                        }

                        if (accountSettings.getThreatPolicies() != null && !accountSettings.getThreatPolicies().isEmpty()) {
                            logger.infoAndAddToDb("Updating Threat Policies for accountId: " + account.getId(), LogDb.DASHBOARD);
                            processThreatFilterTemplateFilesZip(testingTemplates, Constants._AKTO, YamlTemplateSource.AKTO_TEMPLATES.toString(), "");
                        }
                         
                    } catch (Exception e) {
                        cacheLoggerMaker.errorAndAddToDb(e,
                                String.format("Error while updating Test Editor Files %s", e.toString()),
                                LogDb.DASHBOARD);
                    }
                }, "update-test-editor-templates-github");
            }
        }, 0, 4, TimeUnit.HOURS);
    }

    public static void processRemedationFilesZip(byte[] zipFile) {
        if (zipFile != null) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipFile);
                    ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

                ZipEntry entry;
                List<Remediation> remediations = RemediationsDao.instance.findAll(Filters.empty(), Projections.include(Remediation.TEST_ID, Remediation.HASH));
                Map<String, List<Remediation>> mapRemediationIdToHash = remediations.stream().collect(Collectors.groupingBy(Remediation::getid));

                int countUnchangedRemediations = 0;
                int countTotalRemediations = 0;

                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();

                        boolean isRemediation = entryName.contains("remediation");
                        if (!isRemediation) {
                            logger.debugAndAddToDb(
                                    String.format("%s not a remediation file, skipping", entryName),
                                    LogDb.DASHBOARD);
                            continue;
                        }

                        if (!entryName.endsWith(".md")) {
                            logger.debugAndAddToDb(
                                    String.format("%s not a md file, skipping", entryName),
                                    LogDb.DASHBOARD);
                            continue;
                        }

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        String templateContent = new String(outputStream.toByteArray(), "UTF-8");

                        List<Remediation> remediationsInDb = mapRemediationIdToHash.get(entryName);
                        int remediationHashFromFile = templateContent.hashCode();

                        countTotalRemediations++;
                        if (remediationsInDb != null && remediationsInDb.size() >= 1) {
                            int remediationHashInDb = remediationsInDb.get(0).getHash();

                            if (remediationHashFromFile == remediationHashInDb) {
                                countUnchangedRemediations++;
                            } else {
                                logger.debugAndAddToDb("Updating remediation content: " + entryName, LogDb.DASHBOARD);
                                Bson updates = 
                                    Updates.combine(
                                        Updates.set(Remediation.REMEDIATION_TEXT, templateContent),
                                        Updates.set(Remediation.HASH, remediationHashFromFile)
                                    );
                                        
                                RemediationsDao.instance.updateOne(Remediation.TEST_ID, Remediation.REMEDIATION_TEXT, updates);
                            }
                        } else {
                            logger.debugAndAddToDb("Inserting remediation content: " + entryName, LogDb.DASHBOARD);
                            RemediationsDao.instance.insertOne(new Remediation(entryName, templateContent, remediationHashFromFile));
                        }
                    }

                    zipInputStream.closeEntry();
                }

                if (countTotalRemediations != countUnchangedRemediations) {
                    logger.debugAndAddToDb(countUnchangedRemediations + "/" + countTotalRemediations + "remediations unchanged", LogDb.DASHBOARD);
                }
        
            } catch (Exception ex) {
                cacheLoggerMaker.errorAndAddToDb(ex,
                        String.format("Error while processing Test template files zip. Error %s", ex.getMessage()),
                        LogDb.DASHBOARD);
            }
        } else {
            logger.debugAndAddToDb("Received null zip file");
        }        
    }

    private static Map<String, ComplianceInfo> getFromCommonDb() {
        Bson emptyFilter = Filters.empty();
        List<ComplianceInfo> complianceInfosInDb = ComplianceInfosDao.instance.findAll(emptyFilter);
        Map<String, ComplianceInfo> mapIdToComplianceInDb = complianceInfosInDb.stream().collect(Collectors.toMap(ComplianceInfo::getId, Function.identity()));
        return mapIdToComplianceInDb;        
    }

    public static void replaceComplianceFromCommonToAccount(Map<String, ComplianceInfo> mapIdToComplianceInCommon) {
        try {
            String ic = "info.compliance.";
            for(String fileSourceId: mapIdToComplianceInCommon.keySet()) {
                ComplianceInfo complianceInfoInCommon = mapIdToComplianceInCommon.get(fileSourceId);

                Bson filters = 
                    Filters.and(
                        Filters.eq("info.compliance.source", fileSourceId), 
                        Filters.ne(ic+ComplianceInfo.HASH, complianceInfoInCommon.getHash())
                    );

                Bson updates = Updates.combine(
                    Updates.set(ic+ComplianceInfo.HASH, complianceInfoInCommon.getHash()),
                    Updates.set(ic+ComplianceInfo.MAP_COMPLIANCE_TO_LIST_CLAUSES, complianceInfoInCommon.getMapComplianceToListClauses())
                );                 
                UpdateResult updateResult = YamlTemplateDao.instance.updateMany(filters, updates);
                logger.debugAndAddToDb("replaceComplianceFromCommonToAccount: " + Context.accountId.get() + " : " +fileSourceId+" "+ updateResult);
            }
        } catch (Exception e) {
            logger.errorAndAddToDb("Error in replaceComplianceFromCommonToAccount: " + Context.accountId.get() + " : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void addComplianceFromCommonToAccount(Map<String, ComplianceInfo> mapIdToComplianceInCommon) {
        try {
            
            for(String fileSourceId: mapIdToComplianceInCommon.keySet()) {
                ComplianceInfo complianceInfoInCommon = mapIdToComplianceInCommon.get(fileSourceId);
                String compId = complianceInfoInCommon.getId().split("/")[1].split("\\.")[0].toUpperCase();

                boolean isCategoryTemplate = EnumUtils.getEnum(TestCategory.class, compId.toUpperCase()) != null;

                if (isCategoryTemplate) continue;

                Bson filters = 
                    Filters.and(
                        Filters.eq(Constants.ID, compId), 
                        Filters.or(Filters.exists("info.compliance", false), Filters.ne("info.compliance.source", fileSourceId))
                    );

                ComplianceMapping complianceMapping = ComplianceMapping.createFromInfo(complianceInfoInCommon);    
                UpdateResult updateResult = YamlTemplateDao.instance.updateMany(filters, Updates.set("info.compliance", complianceMapping));
                logger.debugAndAddToDb("addComplianceFromCommonToAccount for test id: " + Context.accountId.get() + " : " + compId + " " + updateResult);
            }            


            for(String fileSourceId: mapIdToComplianceInCommon.keySet()) {
                ComplianceInfo complianceInfoInCommon = mapIdToComplianceInCommon.get(fileSourceId);
                String compId = complianceInfoInCommon.getId().split("/")[1].split("\\.")[0].toUpperCase();

                boolean isCategoryTemplate = EnumUtils.getEnum(TestCategory.class, compId.toUpperCase()) != null;

                if (!isCategoryTemplate) continue;

                Bson filters = 
                    Filters.and(
                        Filters.eq("info.category.name", compId.toUpperCase()), 
                        Filters.exists("info.compliance", false)
                    );

                ComplianceMapping complianceMapping = ComplianceMapping.createFromInfo(complianceInfoInCommon);    
                UpdateResult updateResult = YamlTemplateDao.instance.updateMany(filters, Updates.set("info.compliance", complianceMapping));
                logger.debugAndAddToDb("addComplianceFromCommonToAccount for category: " + Context.accountId.get() + " : " + compId + " "  + updateResult);
            }            
        } catch (Exception e) {
            logger.errorAndAddToDb("Error in addComplianceFromCommonToAccount: " + Context.accountId.get() + " : " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void processComplianceInfosFromZip(byte[] zipFile) {
        if (zipFile != null) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipFile);
                    ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

                ZipEntry entry;

                int countUnchangedCompliances = 0;
                int countTotalCompliances = 0;

                Map<String, ComplianceInfo> mapIdToComplianceInDb = getFromCommonDb();

                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();

                        boolean isCompliance = entryName.contains("compliance/");
                        if (!isCompliance) {
                            logger.debugAndAddToDb(
                                    String.format("%s not a compliance file, skipping", entryName),
                                    LogDb.DASHBOARD);
                            continue;
                        }

                        if (!entryName.endsWith(".conf") && !entryName.endsWith(".yml") && !entryName.endsWith(".yaml")) {
                            logger.debugAndAddToDb(
                                    String.format("%s not a yaml file, skipping", entryName),
                                    LogDb.DASHBOARD);
                            continue;
                        }

                        String[] filePathTokens = entryName.split("/");

                        if (filePathTokens.length <= 1) {
                            logger.debugAndAddToDb(
                                String.format("%s has no directory patthern", entryName),
                                LogDb.DASHBOARD);
                            continue;
                        }

                        String fileSourceId = "compliance/"+filePathTokens[filePathTokens.length-1];

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        String templateContent = new String(outputStream.toByteArray(), "UTF-8");
                        int templateHashCode = templateContent.hashCode();

                        Map<String, List<String>> contentMap = TestConfigYamlParser.parseComplianceTemplate(templateContent);

                        ComplianceInfo complianceInfoInDb = mapIdToComplianceInDb.get(fileSourceId);
                        countTotalCompliances++;

                        if (complianceInfoInDb == null) {
                            ComplianceInfo newComplianceInfo = new ComplianceInfo(fileSourceId, contentMap, Constants._AKTO, templateHashCode, "");
                            logger.debugAndAddToDb("Inserting compliance content: " + entryName + " c=" + templateContent + " ci: " + newComplianceInfo, LogDb.DASHBOARD);
                            ComplianceInfosDao.instance.insertOne(newComplianceInfo);

                        } else if (complianceInfoInDb.getHash() == templateHashCode ) {
                            countUnchangedCompliances++;
                        } else {
                            Bson updates = Updates.combine(Updates.set(ComplianceInfo.MAP_COMPLIANCE_TO_LIST_CLAUSES, contentMap), Updates.set(ComplianceInfo.HASH, templateHashCode));
                            logger.debugAndAddToDb("Updating compliance content: " + entryName, LogDb.DASHBOARD);
                            ComplianceInfosDao.instance.updateOne(Constants.ID, fileSourceId, updates);
                        }
                    }

                    zipInputStream.closeEntry();
                }

                if (countTotalCompliances != countUnchangedCompliances) {
                    logger.debugAndAddToDb(countUnchangedCompliances + "/" + countTotalCompliances + "compliances unchanged", LogDb.DASHBOARD);
                }
        
            } catch (Exception ex) {
                cacheLoggerMaker.errorAndAddToDb(ex,
                        String.format("Error while processing Test template files zip. Error %s", ex.getMessage()),
                        LogDb.DASHBOARD);
                        ex.printStackTrace();
            }
        } else {
            logger.debugAndAddToDb("Received null zip file");
        }        
    }

    public static void processTemplateFilesZip(byte[] zipFile, String author, String source, String repositoryUrl) {
        if (zipFile != null) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipFile);
                    ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

                ZipEntry entry;

                Bson proj = Projections.include(YamlTemplate.HASH);
                List<YamlTemplate> hashValueTemplates = YamlTemplateDao.instance.findAll(Filters.empty(), proj);

                Map<String, List<YamlTemplate>> mapIdToHash = hashValueTemplates.stream().collect(Collectors.groupingBy(YamlTemplate::getId));

                int countTotalTemplates = 0;
                int countUnchangedTemplates = 0;
                Set<String> multiNodesIds = new HashSet<>();
                int skipped = 0;
                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();

                        if (entryName.contains("Threat-Protection") || !(entryName.endsWith(".yaml") || entryName.endsWith(".yml"))) {
                            logger.debugAndAddToDb(
                                    String.format("%s not a YAML template file, skipping", entryName),
                                    LogDb.DASHBOARD);
                            continue;
                        }

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        String templateContent = new String(outputStream.toByteArray(), "UTF-8");

                        TestConfig testConfig = null;
                        countTotalTemplates++;
                        List<YamlTemplate> existingTemplatesInDb = new ArrayList<>();
                        try {
                            testConfig = TestConfigYamlParser.parseTemplate(templateContent);
                            String testConfigId = testConfig.getId();

                            existingTemplatesInDb = mapIdToHash.get(testConfigId);

                            if (existingTemplatesInDb != null && existingTemplatesInDb.size() == 1) {
                                int existingTemplateHash = existingTemplatesInDb.get(0).getHash();
                                if (existingTemplateHash == templateContent.hashCode()) {
                                    countUnchangedTemplates++;
                                    if(TestConfig.isTestMultiNode(testConfig)){
                                        multiNodesIds.add(testConfigId);
                                    }
                                    continue;
                                } else {
                                    logger.infoAndAddToDb("Updating test yaml: " + testConfigId, LogDb.DASHBOARD);
                                }
                            }

                        } catch (Exception e) {
                            logger.errorAndAddToDb(e,
                                    String.format("Error parsing yaml template file %s %s", entryName, e.toString()),
                                    LogDb.DASHBOARD);
                        }

                        // new or updated template
                        if (testConfig != null) {
                            boolean hasSettings = testConfig.getAttributes() != null;

                            if (hasSettings && !testConfig.getAttributes().getPlan().equals(TemplatePlan.FREE) && !DashboardMode.isMetered()) {
                                skipped++;
                                continue;
                            }

                            String id = testConfig.getId();
                            int createdAt = Context.now();
                            int updatedAt = Context.now();

                            if (TestConfig.isTestMultiNode(testConfig)) {
                                if(existingTemplatesInDb != null && existingTemplatesInDb.size() == 1){
                                    multiNodesIds.add(id);
                                }else if(!DashboardMode.isMetered()){
                                    continue;
                                }
                            }

                            List<Bson> updates = new ArrayList<>(
                                    Arrays.asList(
                                            Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
                                            Updates.setOnInsert(YamlTemplate.AUTHOR, author),
                                            Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                                            Updates.set(YamlTemplate.HASH, templateContent.hashCode()),
                                            Updates.set(YamlTemplate.CONTENT, templateContent),
                                            Updates.set(YamlTemplate.INFO, testConfig.getInfo()),
                                            Updates.set(YamlTemplate.SETTINGS, testConfig.getAttributes())));

                            try {
                                Object inactiveObject = TestConfigYamlParser.getFieldIfExists(templateContent,
                                        YamlTemplate.INACTIVE);
                                if (inactiveObject != null && inactiveObject instanceof Boolean) {
                                    boolean inactive = (boolean) inactiveObject;
                                    updates.add(Updates.set(YamlTemplate.INACTIVE, inactive));
                                }
                            } catch (Exception e) {
                            }

                            if (Constants._AKTO.equals(author)) {
                                YamlTemplateDao.instance.updateOne(
                                        Filters.eq(Constants.ID, id),
                                        Updates.combine(updates));
                            } else {
                                updates.add(
                                        Updates.setOnInsert(YamlTemplate.SOURCE, source)
                                );
                                updates.add(
                                        Updates.setOnInsert(YamlTemplate.REPOSITORY_URL, repositoryUrl)
                                );

                                try {
                                    YamlTemplateDao.instance.getMCollection().findOneAndUpdate(
                                        Filters.and(Filters.eq(Constants.ID, id),
                                                Filters.ne(YamlTemplate.AUTHOR, Constants._AKTO)),
                                        Updates.combine(updates),
                                        new FindOneAndUpdateOptions().upsert(true));
                                } catch (Exception e){
                                    logger.errorAndAddToDb(
                                            String.format("Error while inserting Test template %s Error: %s", id, e.getMessage()),
                                            LogDb.DASHBOARD);
                                }
                            }

                        }
                    }

                    // Close the current entry to proceed to the next one
                    zipInputStream.closeEntry();
                }

                if(!DashboardMode.isMetered()){
                    logger.debugAndAddToDb("Deleting " + multiNodesIds.size() + " templates for local deployment", LogDb.DASHBOARD);
                    YamlTemplateDao.instance.deleteAll(
                        Filters.in(Constants.ID, multiNodesIds)
                    );
                }

                if (countTotalTemplates != countUnchangedTemplates) {
                    logger.debugAndAddToDb(countUnchangedTemplates + "/" + countTotalTemplates + " unchanged", LogDb.DASHBOARD);
                }

                logger.debugAndAddToDb("Skipped " + skipped + " test templates for account: " + Context.accountId.get());

                YamlTemplateDao.instance.deleteAll(Filters.in("_id", Arrays.asList("LocalFileInclusionLFIRFI", "SQLInjection", "SSRF", "SecurityMisconfig", "XSS")));

            } catch (Exception ex) {
                cacheLoggerMaker.errorAndAddToDb(ex,
                        String.format("Error while processing Test template files zip. Error %s", ex.getMessage()),
                        LogDb.DASHBOARD);
            }
        } else {
            logger.debugAndAddToDb("Received null zip file");
        }
    }

    /**
     * 
     * @param newContent The new template content from GitHub
     * @param existingContent The existing template content in the database
     * @return The merged content with ignore section preserved
     */
    private static String mergeIgnoreSectionFromExisting(String newContent, String existingContent) {
        if (existingContent == null || existingContent.isEmpty()) {
            return newContent;
        }
        
        try {
            // Parse both YAML contents
            com.fasterxml.jackson.databind.ObjectMapper yamlMapper = 
                new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory());
            
            Map<String, Object> existingMap = yamlMapper.readValue(existingContent, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            Map<String, Object> newMap = yamlMapper.readValue(newContent, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            
            // If existing template has an ignore section, preserve it
            if (existingMap.containsKey("ignore")) {
                newMap.put("ignore", existingMap.get("ignore"));
                logger.infoAndAddToDb("Preserving ignore section for template during sync");
            }
            
            // Convert back to YAML string
            return yamlMapper.writeValueAsString(newMap);
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error merging ignore section, using new content as-is: " + e.getMessage());
            return newContent;
        }
    }

    public static void processThreatFilterTemplateFilesZip(byte[] zipFile, String author, String source, String repositoryUrl) {
        if (zipFile != null) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipFile);
                    ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {

                ZipEntry entry;

                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();
                        if (!entry.getName().contains("Threat-Protection")) {
                            logger.infoAndAddToDb(
                                    String.format("%s not a Threat directory, skipping", entryName),
                                    LogDb.DASHBOARD);
                            continue;
                        }
                        if (!(entryName.endsWith(".yaml") || entryName.endsWith(".yml"))) {
                            logger.infoAndAddToDb(
                                    String.format("%s not a YAML template file, skipping", entryName),
                                    LogDb.DASHBOARD);
                            continue;
                        }

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }

                        String templateContent = new String(outputStream.toByteArray(), "UTF-8");

                        TestConfig testConfig = null;
                        try {
                            testConfig = TestConfigYamlParser.parseTemplate(templateContent);
                        } catch (Exception e) {
                            logger.errorAndAddToDb(e,
                                    String.format("Error parsing Threat yaml template file %s %s", entryName, e.toString()),
                                    LogDb.DASHBOARD);
                            continue;
                        }

                        // new or updated template
                        if (testConfig != null) {
                            String id = testConfig.getId();
                            int createdAt = Context.now();
                            int updatedAt = Context.now();
                            int newHashCode = templateContent.hashCode();
                            try {
                                YamlTemplate threatPolicy = FilterYamlTemplateDao.instance.findOne(
                                    Filters.eq(Constants.ID, id), 
                                    Projections.include(YamlTemplate.HASH, YamlTemplate.CONTENT)
                                );

                                String finalContent = templateContent;
                                int finalHashCode = newHashCode;
                                
                                if (threatPolicy == null) {
                                    // New template - use content as-is
                                    logger.infoAndAddToDb("Adding new threat template=" + id +" for account " + Context.accountId, LogDb.DASHBOARD);
                                    YamlTemplate insertNew = new YamlTemplate(id, createdAt, author, updatedAt, finalContent, null, null);
                                    insertNew.setSource(YamlTemplateSource.AKTO_TEMPLATES);
                                    FilterYamlTemplateDao.instance.insertOne(insertNew);
                                } else if (threatPolicy.getHash() != newHashCode) {
                                    // Template exists and has changed - merge ignore section from existing
                                    logger.infoAndAddToDb("Modifying threat template=" + id +" for account " + Context.accountId, LogDb.DASHBOARD);
                                    
                                    // Merge: take new fields from repo, preserve ignore from existing
                                    finalContent = mergeIgnoreSectionFromExisting(templateContent, threatPolicy.getContent());
                                    finalHashCode = finalContent.hashCode();
                                    
                                    // Update all fields with merged content (new fields + preserved ignore)
                                    List<Bson> updates = new ArrayList<>(
                                        Arrays.asList(
                                                Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                                                Updates.set(YamlTemplate.CONTENT, finalContent),
                                                Updates.set(YamlTemplate.SOURCE, source),
                                                Updates.set(YamlTemplate.HASH, finalHashCode)
                                        ));
                                    
                                    FilterYamlTemplateDao.instance.getMCollection().findOneAndUpdate(
                                        Filters.eq(Constants.ID, id),
                                        Updates.combine(updates),
                                        new FindOneAndUpdateOptions().upsert(false)
                                    );    
                                }
                            } catch (Exception e) {
                                cacheLoggerMaker.errorAndAddToDb(e,
                                    String.format("Error while processing Threat template files zip. Error %s", e.getMessage()),
                                    LogDb.DASHBOARD);
                            }
                            
                        }
                    }

                    // Close the current entry to proceed to the next one
                    zipInputStream.closeEntry();
                }

            } catch (Exception ex) {
                cacheLoggerMaker.errorAndAddToDb(ex,
                        String.format("Error while processing Threat template files zip. Error %s", ex.getMessage()),
                        LogDb.DASHBOARD);
            }
        } else {
            logger.infoAndAddToDb("Received null zip file");
        }
    }

    public static void saveLLmTemplates() {
        List<String> templatePaths = new ArrayList<>();
        logger.debugAndAddToDb("saving llm templates", LoggerMaker.LogDb.DASHBOARD);
        try {
            templatePaths = convertStreamToListString(InitializerListener.class.getResourceAsStream("/inbuilt_llm_test_yaml_files"));
        } catch (Exception e) {
            logger.errorAndAddToDb(e, String.format("failed to read test yaml folder %s", e.toString()), LogDb.DASHBOARD);
        }

        String template = null;
        for (String path: templatePaths) {
            try {
                template = convertStreamToString(InitializerListener.class.getResourceAsStream("/inbuilt_llm_test_yaml_files/" + path));
                //logger.debug(template);
                TestConfig testConfig = null;
                try {
                    testConfig = TestConfigYamlParser.parseTemplate(template);
                } catch (Exception e) {
                    logger.error("invalid parsing yaml template for file " + path, e);
                }

                if (testConfig == null) {
                    logger.error("parsed template for file is null " + path);
                }

                String id = testConfig.getId();

                int createdAt = Context.now();
                int updatedAt = Context.now();
                String author = "AKTO";

                YamlTemplateDao.instance.updateOne(
                    Filters.eq("_id", id),
                    Updates.combine(
                            Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
                            Updates.setOnInsert(YamlTemplate.AUTHOR, author),
                            Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                            Updates.set(YamlTemplate.CONTENT, template),
                            Updates.set(YamlTemplate.INFO, testConfig.getInfo())
                    )
                );
            } catch (Exception e) {
                logger.errorAndAddToDb(e, String.format("failed to read test yaml path %s %s", template, e.toString()), LogDb.DASHBOARD);
            }
        }
        logger.debugAndAddToDb("finished saving llm templates", LoggerMaker.LogDb.DASHBOARD);
    }

    private static List<String> convertStreamToListString(InputStream in) throws Exception {

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line = null;
        List<String> files = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            files.add(line);
        }
        in.close();
        reader.close();
        return files;
    }

    public static String convertStreamToString(InputStream in) throws Exception {

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder stringbuilder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            stringbuilder.append(line + "\n");
        }
        in.close();
        reader.close();
        return stringbuilder.toString();
    }

    static boolean isCalcUsageRunning = false;
    public static void calcUsage() {
        if (isCalcUsageRunning) {
            return;
        }

        isCalcUsageRunning = true;
        AccountTask.instance.executeTask(new Consumer<Account>() {
            @Override
            public void accept(Account a) {
                int accountId = a.getId();
                UsageMetricHandler.calcAndSyncAccountUsage(accountId);
            }
        }, "usage-scheduler");

        DeactivateCollections.deactivateCollectionsJob();

        isCalcUsageRunning = false;
    }

    static boolean isSyncWithAktoRunning = false;
    public static void syncWithAkto() {
        if (isSyncWithAktoRunning) return;

        isSyncWithAktoRunning = true;
        logger.debug("Running usage sync scheduler");
        try {
            List<UsageMetric> usageMetrics = UsageMetricsDao.instance.findAll(
                    Filters.eq(UsageMetric.SYNCED_WITH_AKTO, false)
            );

            logger.debugAndAddToDb(String.format("Syncing %d usage metrics", usageMetrics.size()), LogDb.DASHBOARD);

            for (UsageMetric usageMetric : usageMetrics) {
                logger.debugAndAddToDb(String.format("Syncing usage metric %s  %s/%d %s",
                                usageMetric.getId().toString(), usageMetric.getOrganizationId(), usageMetric.getAccountId(), usageMetric.getMetricType().toString()),
                        LogDb.DASHBOARD
                );
                UsageMetricUtils.syncUsageMetricWithAkto(usageMetric);
                
                // Send to mixpanel
                UsageMetricUtils.syncUsageMetricWithMixpanel(usageMetric);
            }
        } catch (Exception e) {
            logger.errorAndAddToDb(e, String.format("Error while syncing usage metrics. Error: %s", e.getMessage()), LogDb.DASHBOARD);
        } finally {
            isSyncWithAktoRunning = false;
        }
    }

    public void setupUsageScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                Context.accountId.set(1000_000);
                boolean dibs = callDibs("usage-scheduler", 60*60, 60);
                if (!dibs) {
                    logger.debugAndAddToDb("Usage cron dibs not acquired, skipping usage cron", LoggerMaker.LogDb.DASHBOARD);
                    return;
                }
                /*
                 * This syncs existing entries in db.
                 * This is needed in case the machine were down,
                 * and any usage for an interval has not been reported.
                 */
                syncWithAkto();
                /*
                 * This recalculates usage and syncs the same.
                 * The existing usage contains real-time data, which is calculated with some
                 * error rate, for fast calculations.
                 * This is needed, to correctly calculate the usage and report it back.
                 */
                calcUsage();
            }
        }, 0, 1, UsageUtils.USAGE_CRON_PERIOD);
    }

    public static void deleteFileUploads(int accountId){
        Context.accountId.set(accountId);
        List<FileUpload> markedForDeletion = FileUploadsDao.instance.findAll(eq("markedForDeletion", true));
        logger.debugAndAddToDb(String.format("Deleting %d file uploads", markedForDeletion.size()), LogDb.DASHBOARD);
        for (FileUpload fileUpload : markedForDeletion) {
            logger.debugAndAddToDb(String.format("Deleting file upload logs for uploadId: %s", fileUpload.getId()), LogDb.DASHBOARD);
            FileUploadLogsDao.instance.deleteAll(eq("uploadId", fileUpload.getId().toString()));
            logger.debugAndAddToDb(String.format("Deleting file upload: %s", fileUpload.getId()), LogDb.DASHBOARD);
            FileUploadsDao.instance.deleteAll(eq("_id", fileUpload.getId()));
            logger.debugAndAddToDb(String.format("Deleted file upload: %s", fileUpload.getId()), LogDb.DASHBOARD);
        }
    }

    public void setupAutomatedApiGroupsScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {

                // Acquire dibs for updating automated API groups
                Context.accountId.set(1000_000);
                boolean dibs = callDibs(Cluster.AUTOMATED_API_GROUPS_CRON,  4*60*60, 60);
                if(!dibs){
                    logger.debugAndAddToDb("Cron for updating automated API groups not acquired, thus skipping cron", LogDb.DASHBOARD);
                    return;
                }

                logger.debugAndAddToDb("Cron for updating automated API groups picked up.", LogDb.DASHBOARD);

                // Fetch automated API groups csv records
                List<CSVRecord> apiGroupRecords = AutomatedApiGroupsUtils.fetchGroups();

                if (apiGroupRecords == null) {
                    return;
                }

                AutomatedApiGroupsUtils.delete_account_ctr = 0;
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            AutomatedApiGroupsUtils.processAutomatedGroups(apiGroupRecords);
                        } catch (Exception e) {
                            logger.errorAndAddToDb("Error while processing automated api groups: " + e.getMessage(), LogDb.DASHBOARD);
                        }
                    }
                }, "automated-api-groups-scheduler");
            }
        }, 0, 4, TimeUnit.HOURS);
    }
}

