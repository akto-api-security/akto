package com.akto.listener;

import static com.akto.dto.AccountSettings.defaultTrafficAlertThresholdSeconds;
import static com.akto.runtime.RuntimeUtil.matchesDefaultPayload;
import static com.akto.task.Cluster.callDibs;
import static com.akto.utils.billing.OrganizationUtils.syncOrganizationWithAkto;
import static com.mongodb.client.model.Filters.eq;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletContextListener;

import com.akto.DaoInit;
import com.akto.action.AdminSettingsAction;
import com.akto.action.ApiCollectionsAction;
import com.akto.action.CustomDataTypeAction;
import com.akto.action.observe.InventoryAction;
import com.akto.action.testing.StartTestAction;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.*;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dao.loaders.LoadersDao;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.notifications.CustomWebhooksResultDao;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dao.pii.PIISourceDao;
import com.akto.dao.test_editor.TestConfigYamlParser;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.*;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.traffic_collector.TrafficCollectorInfoDao;
import com.akto.dao.traffic_collector.TrafficCollectorMetricsDao;
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dao.upload.FileUploadLogsDao;
import com.akto.dao.upload.FileUploadsDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.*;
import com.akto.dto.ApiCollectionUsers.CollectionType;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User.AktoUIMode;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Conditions.Operator;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.dependency_flow.DependencyFlow;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhook.ActiveStatus;
import com.akto.dto.notifications.CustomWebhook.WebhookOptions;
import com.akto.dto.notifications.CustomWebhookResult;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.pii.PIISource;
import com.akto.dto.pii.PIIType;
import com.akto.dto.settings.DefaultPayload;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.*;
import com.akto.dto.testing.custom_groups.AllAPIsGroup;
import com.akto.dto.testing.custom_groups.UnauthenticatedEndpoint;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.upload.FileUpload;
import com.akto.dto.usage.UsageMetric;
import com.akto.log.CacheLoggerMaker;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.notifications.slack.DailyUpdate;
import com.akto.notifications.slack.TestSummaryGenerator;
import com.akto.parsers.HttpCallParser;
import com.akto.task.Cluster;
import com.akto.telemetry.TelemetryJob;
import com.akto.testing.ApiExecutor;
import com.akto.testing.ApiWorkflowExecutor;
import com.akto.testing.HostDNSLookup;
import com.akto.testing.workflow_node_executor.Utils;
import com.akto.usage.UsageMetricHandler;
import com.akto.util.*;
import com.akto.util.enums.GlobalEnums.TestCategory;
import com.akto.util.enums.GlobalEnums.YamlTemplateSource;
import com.akto.util.filter.DictionaryFilter;
import com.akto.util.http_util.CoreHTTPClient;
import com.akto.util.tasks.OrganizationTask;
import com.akto.utils.Auth0;
import com.akto.utils.AutomatedApiGroupsUtils;
import com.akto.utils.RedactSampleData;
import com.akto.utils.TestTemplateUtils;
import com.akto.utils.billing.OrganizationUtils;
import com.akto.utils.crons.Crons;
import com.akto.utils.crons.SyncCron;
import com.akto.utils.crons.TokenGeneratorCron;
import com.akto.utils.crons.UpdateSensitiveInfoInApiInfo;
import com.akto.utils.jobs.DeactivateCollections;
import com.akto.utils.notifications.TrafficUpdates;
import com.google.gson.Gson;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.ReadPreference;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import com.mongodb.client.result.DeleteResult;
import com.slack.api.Slack;
import com.slack.api.util.http.SlackHttpClient;
import com.slack.api.webhook.WebhookResponse;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.OkHttpClient;

public class InitializerListener implements ServletContextListener {
    private static final Logger logger = LoggerFactory.getLogger(InitializerListener.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(InitializerListener.class);
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

    public void setUpTrafficAlertScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            // look back period 6 days
                            loggerMaker.infoAndAddToDb("starting traffic alert scheduler", LoggerMaker.LogDb.DASHBOARD);
                            TrafficUpdates trafficUpdates = new TrafficUpdates(60*60*24*6);
                            trafficUpdates.populate();

                            List<SlackWebhook> listWebhooks = SlackWebhooksDao.instance.findAll(new BasicDBObject());
                            if (listWebhooks == null || listWebhooks.isEmpty()) {
                                loggerMaker.infoAndAddToDb("No slack webhooks found", LogDb.DASHBOARD);
                                return;
                            }
                            SlackWebhook webhook = listWebhooks.get(0);
                            loggerMaker.infoAndAddToDb("Slack Webhook found: " + webhook.getWebhook(), LogDb.DASHBOARD);

                            int thresholdSeconds = defaultTrafficAlertThresholdSeconds;
                            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                            if (accountSettings != null) {
                                // override with user supplied value
                                thresholdSeconds = accountSettings.getTrafficAlertThresholdSeconds();
                            }

                            loggerMaker.infoAndAddToDb("threshold seconds: " + thresholdSeconds, LoggerMaker.LogDb.DASHBOARD);

                            if (thresholdSeconds > 0) {
                                trafficUpdates.sendAlerts(webhook.getWebhook(),webhook.getDashboardUrl()+"/dashboard/settings#Metrics", thresholdSeconds);
                            }
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e,"Error while running traffic alerts: " + e.getMessage(), LogDb.DASHBOARD);
                        }
                    }
                }, "traffic-alerts-scheduler");
            }
        }, 0, 4, TimeUnit.HOURS);
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
        List<Integer> accounts = new ArrayList<>(Arrays.asList(1_000_000, 1718042191, 1664578207, 1693004074, 1685916748));
        for (int account : accounts) {
            Context.accountId.set(account);
            createFirstUnauthenticatedApiGroup();
        }
    }

    private static void raiseMixpanelEvent() {

        int now = Context.now();
        List<BasicDBObject> totalEndpoints = new InventoryAction().fetchRecentEndpoints(0, now);
        List<BasicDBObject> newEndpoints  = new InventoryAction().fetchRecentEndpoints(now - 604800, now);

        DashboardMode dashboardMode = DashboardMode.getDashboardMode();        

        RBAC record = RBACDao.instance.findOne("role", Role.ADMIN);

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
                        }
                    }
                }, "pii-scheduler");
            }
        }, 0, 4, TimeUnit.HOURS);
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
            loggerMaker.infoAndAddToDb("processing batch: " + currMarker, LogDb.DASHBOARD);
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
            loggerMaker.infoAndAddToDb(paramStr + url, LogDb.DASHBOARD);

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

            loggerMaker.infoAndAddToDb("PII cleaner - modified " + bwr.getModifiedCount() + " from STI", LogDb.DASHBOARD);
        }

    }

    private static void bulkSingleTypeInfoDelete(List<SingleTypeInfo.ParamId> idsToDelete, Set<Integer> whiteListCollectionSet) {
        ArrayList<WriteModel<SingleTypeInfo>> bulkUpdatesForSingleTypeInfo = new ArrayList<>();
        for(SingleTypeInfo.ParamId paramId: idsToDelete) {
            String paramStr = "PII cleaner - deleting: " + paramId.getApiCollectionId() + ": " + paramId.getMethod() + " " + paramId.getUrl() + " > " + paramId.getParam();
            loggerMaker.infoAndAddToDb(paramStr, LogDb.DASHBOARD);

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

            loggerMaker.infoAndAddToDb("PII cleaner - deleted " + bwr.getDeletedCount() + " from STI", LogDb.DASHBOARD);
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
            loggerMaker.errorAndAddToDb("Unable to find file locally: " + fileUrl, LogDb.DASHBOARD);
            return null;
        }
        try {
            return convertStreamToString(InitializerListener.class.getResourceAsStream("/" + fileName));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Exception while reading content locally: " + fileUrl, LogDb.DASHBOARD);
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
            loggerMaker.errorAndAddToDb(e, String.format("failed to fetch PII file %s from github, trying locally", piiSource.getFileUrl()), LogDb.DASHBOARD);
            return loadPIIFileFromResources(piiSource.getFileUrl());
        }
    }

    public static void executePIISourceFetch() {
        List<PIISource> piiSources = PIISourceDao.instance.findAll("active", true);
        for (PIISource piiSource : piiSources) {
            String id = piiSource.getId();
            Map<String, PIIType> currTypes = piiSource.getMapNameToPIIType();
            if (currTypes == null) {
                currTypes = new HashMap<>();
            }

            String fileContent = fetchPIIFile(piiSource);
            if (fileContent == null) {
                loggerMaker.errorAndAddToDb("Failed to load file from github as well as resources: " + piiSource.getFileUrl(), LogDb.DASHBOARD);
                continue;
            }
            BasicDBObject fileObj = BasicDBObject.parse(fileContent);
            BasicDBList dataTypes = (BasicDBList) (fileObj.get("types"));
            Bson findQ = Filters.eq("_id", id);

            List<CustomDataType> customDataTypes = CustomDataTypeDao.instance.findAll(new BasicDBObject());
            Map<String, CustomDataType> customDataTypesMap = new HashMap<>();
            for (CustomDataType customDataType : customDataTypes) {
                customDataTypesMap.put(customDataType.getName(), customDataType);
            }

            List<Bson> piiUpdates = new ArrayList<>();

            for (Object dtObj : dataTypes) {
                BasicDBObject dt = (BasicDBObject) dtObj;
                String piiKey = dt.getString("name").toUpperCase();
                PIIType piiType = new PIIType(
                        piiKey,
                        dt.getBoolean("sensitive"),
                        dt.getString("regexPattern"),
                        dt.getBoolean("onKey")
                );

                CustomDataType existingCDT = customDataTypesMap.getOrDefault(piiKey, null);
                CustomDataType newCDT = getCustomDataTypeFromPiiType(piiSource, piiType, false);

                if (currTypes.containsKey(piiKey) &&
                        (currTypes.get(piiKey).equals(piiType) &&
                                dt.getBoolean(PIISource.ACTIVE, true))) {
                    continue;
                } else {
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
                        CustomDataTypeDao.instance.insertOne(newCDT);
                    } else {
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

        if(!Conditions.areEqual(existingCDT.getKeyConditions(), newCDT.getKeyConditions())){
            ret.add(Updates.set(CustomDataType.KEY_CONDITIONS, newCDT.getKeyConditions()));
        }
        if(!Conditions.areEqual(existingCDT.getValueConditions(), newCDT.getValueConditions())){
            ret.add(Updates.set(CustomDataType.VALUE_CONDITIONS, newCDT.getValueConditions()));
        }
        if(existingCDT.getOperator()!=newCDT.getOperator()){
            ret.add(Updates.set(CustomDataType.OPERATOR, newCDT.getOperator()));
        }

        if (!ret.isEmpty()) {
            ret.add(Updates.set(CustomDataType.TIMESTAMP, Context.now()));
        }

        return ret;
    }

    private static CustomDataType getCustomDataTypeFromPiiType(PIISource piiSource, PIIType piiType, Boolean active) {
        String piiKey = piiType.getName();

        List<Predicate> predicates = new ArrayList<>();
        Conditions conditions = new Conditions(predicates, Operator.OR);
        predicates.add(new RegexPredicate(piiType.getRegexPattern()));
        IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
        CustomDataType ret = new CustomDataType(
                piiKey,
                piiType.getIsSensitive(),
                Collections.emptyList(),
                piiSource.getAddedByUser(),
                active,
                (piiType.getOnKey() ? conditions : null),
                (piiType.getOnKey() ? null : conditions),
                Operator.OR,
                ignoreData,
                false,
                true
        );

        return ret;
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
                                                    loggerMaker.errorAndAddToDb("Deleting API that matches default payload: " + toBeDeleted, LogDb.DASHBOARD);
                                                    toBeDeleted.add(sampleData.getId());
                                                }
                                            } catch (Exception e) {
                                                loggerMaker.errorAndAddToDb("Couldn't delete an api for default payload: " + sampleData.getId() + e.getMessage(), LogDb.DASHBOARD);
                                            }
                                        }

                                        deleteApis(toBeDeleted);

                                    } while (!sampleDataList.isEmpty());

                                    Bson completedScan = Updates.set(AccountSettings.DEFAULT_PAYLOADS+"."+base64Url+"."+DefaultPayload.SCANNED_EXISTING_DATA, true);
                                    AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), completedScan);

                                } catch (Exception e) {

                                    loggerMaker.errorAndAddToDb("Couldn't complete scan for default payload: " + e.getMessage(), LogDb.DASHBOARD);
                                }
                            }
                        }
                    }
                }, "setUpDefaultPayloadRemover");
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    private <T> void deleteApisPerDao(List<Key> toBeDeleted, AccountsContextDao<T> dao, String prefix) {
        if (toBeDeleted == null || toBeDeleted.isEmpty()) return;
        List<WriteModel<T>> stiList = new ArrayList<>();

        for(Key key: toBeDeleted) {
            stiList.add(new DeleteManyModel<>(Filters.and(
                    Filters.eq(prefix + "apiCollectionId", key.getApiCollectionId()),
                    Filters.eq(prefix + "method", key.getMethod()),
                    Filters.eq(prefix + "url", key.getUrl())
            )));
        }

        dao.bulkWrite(stiList, new BulkWriteOptions().ordered(false));
    }

    private void deleteApis(List<Key> toBeDeleted) {

        String id = "_id.";

        deleteApisPerDao(toBeDeleted, SingleTypeInfoDao.instance, "");
        deleteApisPerDao(toBeDeleted, ApiInfoDao.instance, id);
        deleteApisPerDao(toBeDeleted, SampleDataDao.instance, id);
        deleteApisPerDao(toBeDeleted, TrafficInfoDao.instance, id);
        deleteApisPerDao(toBeDeleted, SensitiveSampleDataDao.instance, id);
        deleteApisPerDao(toBeDeleted, SensitiveParamInfoDao.instance, "");
        deleteApisPerDao(toBeDeleted, FilterSampleDataDao.instance, id);

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
                        // logger.info("debugSlack: " + slackWebhook.getLastSentTimestamp() + " " + slackWebhook.getFrequencyInSeconds() + " " +now );

                                if(slackWebhook.getFrequencyInSeconds()==0) {
                                    slackWebhook.setFrequencyInSeconds(24*60*60);
                                }

                                boolean shouldSend = ( slackWebhook.getLastSentTimestamp() + slackWebhook.getFrequencyInSeconds() ) <= now ;

                                if(!shouldSend){
                                    continue;
                                }

                                loggerMaker.infoAndAddToDb(slackWebhook.toString(), LogDb.DASHBOARD);
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
                                loggerMaker.infoAndAddToDb(payload, LogDb.DASHBOARD);
                                try {
                                    URI uri = URI.create(webhookUrl);
                                    if (!HostDNSLookup.isRequestValid(uri.getHost())) {
                                        throw new IllegalArgumentException("SSRF attack attempt");
                                    }
                                    loggerMaker.infoAndAddToDb("Payload for changes:" + payload, LogDb.DASHBOARD);
                                    WebhookResponse response = slack.send(webhookUrl, payload);
                                    loggerMaker.infoAndAddToDb("Changes webhook response: " + response.getBody(), LogDb.DASHBOARD);

                                    // slack testing notification
                                    loggerMaker.infoAndAddToDb("Payload for test summary:" + testSummaryPayload, LogDb.DASHBOARD);
                                    response = slack.send(webhookUrl, testSummaryPayload);
                                    loggerMaker.infoAndAddToDb("Test summary webhook response: " + response.getBody(), LogDb.DASHBOARD);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                    loggerMaker.errorAndAddToDb(e, "Error while sending slack alert: " + e.getMessage(), LogDb.DASHBOARD);
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

        ChangesInfo ci = getChangesInfo(now - webhook.getLastSentTimestamp(), now - webhook.getLastSentTimestamp(), webhook.getNewEndpointCollections(), webhook.getNewSensitiveEndpointCollections(), true);
        if (ci == null || (ci.newEndpointsLast7Days.size() + ci.newSensitiveParams.size() + ci.recentSentiiveParams + ci.newParamsInExistingEndpoints) == 0) {
            return;
        }

        List<String> errors = new ArrayList<>();

        Map<String, Object> valueMap = new HashMap<>();

        createBodyForWebhook(webhook);

        valueMap.put("AKTO.changes_info.newSensitiveEndpoints", ci.newSensitiveParamsObject);
        valueMap.put("AKTO.changes_info.newSensitiveEndpointsCount", ci.newSensitiveParams.size());

        valueMap.put("AKTO.changes_info.newEndpoints", ci.newEndpointsLast7DaysObject);
        valueMap.put("AKTO.changes_info.newEndpointsCount", ci.newEndpointsLast7Days.size());

        valueMap.put("AKTO.changes_info.newSensitiveParametersCount", ci.recentSentiiveParams);
        valueMap.put("AKTO.changes_info.newParametersCount", ci.newParamsInExistingEndpoints);

        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        String payload = null;

        try {
            payload = Utils.replaceVariables(webhook.getBody(), valueMap, false);
        } catch (Exception e) {
            errors.add("Failed to replace variables");
        }

        webhook.setLastSentTimestamp(now);
        CustomWebhooksDao.instance.updateOne(Filters.eq("_id", webhook.getId()), Updates.set("lastSentTimestamp", now));

        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(webhook.getHeaderString());
        OriginalHttpRequest request = new OriginalHttpRequest(webhook.getUrl(), webhook.getQueryParams(), webhook.getMethod().toString(), payload, headers, "");
        OriginalHttpResponse response = null; // null response means api request failed. Do not use new OriginalHttpResponse() in such cases else the string parsing fails.

        try {
            response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
            loggerMaker.infoAndAddToDb("webhook request sent", LogDb.DASHBOARD);
        } catch (Exception e) {
            errors.add("API execution failed");
        }

        String message = null;
        try {
            message = RedactSampleData.convertOriginalReqRespToString(request, response);
        } catch (Exception e) {
            errors.add("Failed converting sample data");
        }

        CustomWebhookResult webhookResult = new CustomWebhookResult(webhook.getId(), webhook.getUserEmail(), now, message, errors);
        CustomWebhooksResultDao.instance.insertOne(webhookResult);
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
                }, "webhook-sener");
            }
        }, 0, 1, TimeUnit.HOURS);
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
            loggerMaker.errorAndAddToDb(e, String.format("get new endpoints %s", e.toString()), LogDb.DASHBOARD);
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
        TestRoles testRoles = new TestRoles(new ObjectId(), "ATTACKER_TOKEN_ALL", endpointLogicalGroup.getId(), authWithCondList, "System", createdTs, createdTs, null);
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

        if (ApiCollectionsDao.instance.findOne(
                Filters.eq("_id", UnauthenticatedEndpoint.UNAUTHENTICATED_GROUP_ID)) == null) {
            loggerMaker.infoAndAddToDb("AccountId: " + Context.accountId.get() + " Creating unauthenticated api group.", LogDb.DASHBOARD);
            ApiCollection unauthenticatedApisGroup = new ApiCollection(UnauthenticatedEndpoint.UNAUTHENTICATED_GROUP_ID,
                    "Unauthenticated Apis", Context.now(), new HashSet<>(), null, 0, false, false);

            unauthenticatedApisGroup.setAutomated(true);
            unauthenticatedApisGroup.setType(ApiCollection.Type.API_GROUP);
            List<TestingEndpoints> conditions = new ArrayList<>();
            conditions.add(new UnauthenticatedEndpoint());
            unauthenticatedApisGroup.setConditions(conditions);

            ApiCollectionsDao.instance.insertOne(unauthenticatedApisGroup);
        }

    }

    public static void createAllApisGroup() {
        if (ApiCollectionsDao.instance
                .findOne(Filters.eq("_id", 111111121)) == null) {
            loggerMaker.infoAndAddToDb("AccountId: " + Context.accountId.get() + " Creating all apis group.", LogDb.DASHBOARD);
            ApiCollection allApisGroup = new ApiCollection(111_111_121, "All Apis", Context.now(), new HashSet<>(),
                    null, 0, false, false);

            allApisGroup.setAutomated(true);
            allApisGroup.setType(ApiCollection.Type.API_GROUP);
            List<TestingEndpoints> conditions = new ArrayList<>();
            conditions.add(new AllAPIsGroup());
            allApisGroup.setConditions(conditions);

            ApiCollectionsDao.instance.insertOne(allApisGroup);
        }

    }
    

    public static void createFirstUnauthenticatedApiGroup(){
        createUnauthenticatedApiGroup();
        createAllApisGroup();
    }

    public static void createRiskScoreApiGroup(int id, String name, RiskScoreTestingEndpoints.RiskScoreGroupType riskScoreGroupType) {
        loggerMaker.infoAndAddToDb("Creating risk score group: " + name, LogDb.DASHBOARD);
        
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
        if (backwardCompatibility.getAddAktoDataTypes() == 0) {
            List<AktoDataType> aktoDataTypes = new ArrayList<>();
            int now = Context.now();
            IgnoreData ignoreData = new IgnoreData(new HashMap<>(), new HashSet<>());
            aktoDataTypes.add(new AktoDataType("JWT", false, Arrays.asList(SingleTypeInfo.Position.RESPONSE_PAYLOAD, SingleTypeInfo.Position.RESPONSE_HEADER), now, ignoreData, false, true));
            aktoDataTypes.add(new AktoDataType("EMAIL", true, Collections.emptyList(), now, ignoreData, false, true));
            aktoDataTypes.add(new AktoDataType("CREDIT_CARD", true, Collections.emptyList(), now, ignoreData, false, true));
            aktoDataTypes.add(new AktoDataType("SSN", true, Collections.emptyList(), now, ignoreData, false, true));
            aktoDataTypes.add(new AktoDataType("ADDRESS", true, Collections.emptyList(), now, ignoreData, false, true));
            aktoDataTypes.add(new AktoDataType("IP_ADDRESS", false, Arrays.asList(SingleTypeInfo.Position.RESPONSE_PAYLOAD, SingleTypeInfo.Position.RESPONSE_HEADER), now, ignoreData, false, true));
            aktoDataTypes.add(new AktoDataType("PHONE_NUMBER", true, Collections.emptyList(), now, ignoreData, false, true));
            aktoDataTypes.add(new AktoDataType("UUID", false, Collections.emptyList(), now, ignoreData, false, true));
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

        loggerMaker.infoAndAddToDb("Loading template files from directory", LogDb.DASHBOARD);

        try (InputStream is = InitializerListener.class.getResourceAsStream(resourceName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                if (is == null) {
                    loggerMaker.errorAndAddToDb("Resource not found: " + resourceName, LogDb.DASHBOARD);
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
            loggerMaker.errorAndAddToDb(ex, String.format("Error while loading templates files from directory. Error: %s", ex.getMessage()), LogDb.DASHBOARD);
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
                loggerMaker.infoAndAddToDb("Setting default telemetry settings", LogDb.DASHBOARD);
                accountSettings.setTelemetrySettings(telemetrySettings);
                AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), Updates.set(AccountSettings.TELEMETRY_SETTINGS, telemetrySettings));
            }
            BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DEFAULT_TELEMETRY_SETTINGS, Context.now())
            );
            loggerMaker.infoAndAddToDb("Default telemetry settings set successfully", LogDb.DASHBOARD);
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
            loggerMaker.errorAndAddToDb("error in disableAwsSecretPiiType " + e.getMessage(), LogDb.DASHBOARD);
        }

        try {
            Bson delFilter = Filters.eq("name", piiType);
            DeleteResult res = CustomDataTypeDao.instance.deleteAll(delFilter);
            loggerMaker.infoAndAddToDb("auth type : " + res.toString());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error deleting pii type " + piiType + " " + e.getMessage(), LogDb.DASHBOARD);
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
                
            //         loggerMaker.infoAndAddToDb(String.format("Initializing organization to which account %d belongs", accountId), LogDb.DASHBOARD);
            //         RBAC adminRbac = RBACDao.instance.findOne(
            //             Filters.and(
            //                 Filters.eq(RBAC.ACCOUNT_ID, accountId),  
            //                 Filters.eq(RBAC.ROLE, RBAC.Role.ADMIN)
            //             )
            //         );

            //         if (adminRbac == null) {
            //             loggerMaker.infoAndAddToDb(String.format("Account %d does not have an admin user", accountId), LogDb.DASHBOARD);
            //             return;
            //         }

            //         int adminUserId = adminRbac.getUserId();
            //         // Get admin user
            //         User admin = UsersDao.instance.findOne(
            //             Filters.eq(User.ID, adminUserId)
            //         );

            //         if (admin == null) {
            //             loggerMaker.infoAndAddToDb(String.format("Account %d admin user does not exist", accountId), LogDb.DASHBOARD);
            //             return;
            //         }

            //         String adminEmail = admin.getLogin();
            //         loggerMaker.infoAndAddToDb(String.format("Organization admin email: %s", adminEmail), LogDb.DASHBOARD);

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

            //             loggerMaker.infoAndAddToDb(String.format("Organization %s does not exist, creating...", name), LogDb.DASHBOARD);

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
            //             loggerMaker.infoAndAddToDb(String.format("Organization %s - Updating accounts list", organization.getName()), LogDb.DASHBOARD);
            //         }

            //         Boolean syncedWithAkto = organization.getSyncedWithAkto();
            //         Boolean attemptSyncWithAktoSuccess = false;

            //         // Attempt to sync organization with akto
            //         if (!syncedWithAkto) {
            //             loggerMaker.infoAndAddToDb(String.format("Organization %s - Syncing with akto", organization.getName()), LogDb.DASHBOARD);
            //             attemptSyncWithAktoSuccess = OrganizationUtils.syncOrganizationWithAkto(organization);
                        
            //             if (!attemptSyncWithAktoSuccess) {
            //                 loggerMaker.infoAndAddToDb(String.format("Organization %s - Sync with akto failed", organization.getName()), LogDb.DASHBOARD);
            //                 return;
            //             } 

            //             loggerMaker.infoAndAddToDb(String.format("Organization %s - Sync with akto successful", organization.getName()), LogDb.DASHBOARD);
            //         } else {
            //             loggerMaker.infoAndAddToDb(String.format("Organization %s - Alredy Synced with akto. Skipping sync ... ", organization.getName()), LogDb.DASHBOARD);
            //         }
                    
            //         // Set backward compatibility dao
            //         BackwardCompatibilityDao.instance.updateOne(
            //             Filters.eq("_id", backwardCompatibility.getId()),
            //             Updates.set(BackwardCompatibility.INITIALIZE_ORGANIZATION_ACCOUNT_BELONGS_TO, Context.now())
            //         );
            //     } catch (Exception e) {
            //         loggerMaker.errorAndAddToDb(String.format("Error while initializing organization account belongs to. Error: %s", e.getMessage()), LogDb.DASHBOARD);
            //     }
            // }
        }
    }

    public static void createOrg(int accountId) {
        Bson filterQ = Filters.in(Organization.ACCOUNTS, accountId);
        Organization organization = OrganizationsDao.instance.findOne(filterQ);
        boolean alreadyExists = organization != null;
        if (alreadyExists) {
            fetchAndSaveFeatureWiseAllowed(organization);
            loggerMaker.infoAndAddToDb("Org already exists for account: " + accountId, LogDb.DASHBOARD);
            return;
        }

        RBAC rbac = RBACDao.instance.findOne(RBAC.ACCOUNT_ID, accountId, RBAC.ROLE, Role.ADMIN);

        if (rbac == null) {
            loggerMaker.infoAndAddToDb("Admin is missing in DB", LogDb.DASHBOARD);
            RBACDao.instance.getMCollection().updateOne(Filters.and(Filters.eq(RBAC.ROLE, Role.ADMIN), Filters.exists(RBAC.ACCOUNT_ID, false)), Updates.set(RBAC.ACCOUNT_ID, accountId), new UpdateOptions().upsert(false));
            rbac = RBACDao.instance.findOne(RBAC.ACCOUNT_ID, accountId, RBAC.ROLE, Role.ADMIN);
            if(rbac == null){
                loggerMaker.errorAndAddToDb("Admin is still missing in DB, making first user as admin", LogDb.DASHBOARD);
                User firstUser = UsersDao.instance.getFirstUser(accountId);
                if(firstUser != null){
                    rbac = new RBAC(firstUser.getId(), Role.ADMIN, accountId);
                    RBACDao.instance.insertOne(rbac);
                } else {
                    loggerMaker.errorAndAddToDb("First user is also missing in DB, unable to make org.", LogDb.DASHBOARD);
                    return;
                }
            }
        }

        int userId = rbac.getUserId();

        User user = UsersDao.instance.findOne(User.ID, userId);
        if (user == null) {
            loggerMaker.errorAndAddToDb("User "+ userId +" is absent! Unable to make org.", LogDb.DASHBOARD);
            return;
        }

        Organization org = OrganizationsDao.instance.findOne(Organization.ADMIN_EMAIL, user.getLogin());

        if (org == null) {
            loggerMaker.infoAndAddToDb("Creating a new org for email id: " + user.getLogin() + " and acc: " + accountId, LogDb.DASHBOARD);
            org = new Organization(UUID.randomUUID().toString(), user.getLogin(), user.getLogin(), new HashSet<>(), !DashboardMode.isSaasDeployment());
            OrganizationsDao.instance.insertOne(org);
        } else {
            loggerMaker.infoAndAddToDb("Found a new org for acc: " + accountId + " org="+org.getId(), LogDb.DASHBOARD);
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

    public void setUpUpdateCustomCollections() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            updateCustomCollections();
                        } catch (Exception e){
                            loggerMaker.errorAndAddToDb(e, "Error while updating custom collections: " + e.getMessage(), LogDb.DASHBOARD);
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
            if(DashboardMode.isOnPremDeployment()) {
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

        logger.info("context initialized");

        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        logger.info("MONGO URI " + mongoURI);

        try {
            readAndSaveBurpPluginVersion();
        } catch (Exception e) {
            e.printStackTrace();
        }


        executorService.schedule(new Runnable() {
            public void run() {
                DaoInit.init(new ConnectionString(mongoURI), ReadPreference.primary());

                connectedToMongo = false;
                do {
                    connectedToMongo = MCollection.checkConnection();
                    if (!connectedToMongo) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {}
                    }
                } while (!connectedToMongo);

                setDashboardMode();
                updateGlobalAktoVersion();

                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        AccountSettingsDao.instance.getStats();
                        runInitializerFunctions();
                    }
                }, "context-initializer");

                if (DashboardMode.isMetered()) {
                    setupUsageScheduler();
                    setupUsageSyncScheduler();
                }
                trimCappedCollections();
                setUpPiiAndTestSourcesScheduler();
                setUpTrafficAlertScheduler();
                // setUpAktoMixpanelEndpointsScheduler();
                SingleTypeInfo.init();
                setUpDailyScheduler();
                setUpWebhookScheduler();
                setUpDefaultPayloadRemover();
                setUpTestEditorTemplatesScheduler();
                setUpDependencyFlowScheduler();
                tokenGeneratorCron.tokenGeneratorScheduler();
                crons.deleteTestRunsScheduler();
                updateSensitiveInfoInApiInfo.setUpSensitiveMapInApiInfoScheduler();
                syncCronInfo.setUpUpdateCronScheduler();
                // setUpAktoMixpanelEndpointsScheduler();
                //fetchGithubZip();
                if(isSaas){
                    try {
                        Auth0.getInstance();
                        loggerMaker.infoAndAddToDb("Auth0 initialized", LogDb.DASHBOARD);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Failed to initialize Auth0 due to: " + e.getMessage(), LogDb.DASHBOARD);
                    }
                }
                updateApiGroupsForAccounts();
                setUpUpdateCustomCollections();
                setUpFillCollectionIdArrayJob();
                setupAutomatedApiGroupsScheduler();
            }
        }, 0, TimeUnit.SECONDS);


    }


    private void setUpDependencyFlowScheduler() {
        int minutes = DashboardMode.isOnPremDeployment() ? 60 : 24*60;
        Context.accountId.set(1000_000);
        boolean dibs = callDibs(Cluster.DEPENDENCY_FLOW_CRON,  minutes, 60);
        if(!dibs){
            loggerMaker.infoAndAddToDb("Cron for updating dependency flow not acquired, thus skipping cron", LogDb.DASHBOARD);
            return;
        }
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            DependencyFlow dependencyFlow = new DependencyFlow();
                            loggerMaker.infoAndAddToDb("Starting dependency flow");
                            dependencyFlow.run(null);
                            dependencyFlow.syncWithDb();
                            loggerMaker.infoAndAddToDb("Finished running dependency flow");
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e,
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
            loggerMaker.errorAndAddToDb("Error updating global akto version : " + e.getMessage(), LogDb.DASHBOARD );
        }
    }

    public static void trimCappedCollections() {
        if (DbMode.allowCappedCollections()) return;

        clear(LoadersDao.instance, LoadersDao.maxDocuments);
        clear(RuntimeLogsDao.instance, RuntimeLogsDao.maxDocuments);
        clear(LogsDao.instance, LogsDao.maxDocuments);
        clear(DashboardLogsDao.instance, DashboardLogsDao.maxDocuments);
        clear(TrafficMetricsDao.instance, TrafficMetricsDao.maxDocuments);
        clear(AnalyserLogsDao.instance, AnalyserLogsDao.maxDocuments);
        clear(ActivitiesDao.instance, ActivitiesDao.maxDocuments);
        clear(BillingLogsDao.instance, BillingLogsDao.maxDocuments);
        clear(TestingRunResultDao.instance, TestingRunResultDao.maxDocuments);
        clear(TrafficCollectorInfoDao.instance, TrafficCollectorInfoDao.maxDocuments);
        clear(TrafficCollectorMetricsDao.instance, TrafficCollectorInfoDao.maxDocuments);
    }

    public static void clear(AccountsContextDao mCollection, int maxDocuments) {
        try {
            mCollection.trimCollection(maxDocuments);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while trimming collection " + mCollection.getCollName() + " : " + e.getMessage(), LogDb.DASHBOARD);
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

    private final static int REFRESH_INTERVAL = 60 * 1; // 1 minute

    public static Organization fetchAndSaveFeatureWiseAllowed(Organization organization) {

        HashMap<String, FeatureAccess> featureWiseAllowed = new HashMap<>();

        try {
            int gracePeriod = organization.getGracePeriod();
            String hotjarSiteId = organization.getHotjarSiteId();
            String organizationId = organization.getId();

            int lastFeatureMapUpdate = organization.getLastFeatureMapUpdate();

            /*
             * This ensures, we don't fetch feature wise allowed from akto too often.
             * This helps the dashboard to be more responsive.
             */
            if(lastFeatureMapUpdate + REFRESH_INTERVAL > Context.now()){
                return organization;
            }

            HashMap<String, FeatureAccess> initialFeatureWiseAllowed = organization.getFeatureWiseAllowed();
            if (initialFeatureWiseAllowed == null) {
                initialFeatureWiseAllowed = new HashMap<>();
            }

            loggerMaker.infoAndAddToDb("Fetching featureMap",LogDb.DASHBOARD);

            BasicDBList entitlements = OrganizationUtils.fetchEntitlements(organizationId,
                    organization.getAdminEmail());

            featureWiseAllowed = OrganizationUtils.getFeatureWiseAllowed(entitlements);
            Set<Integer> accounts = organization.getAccounts();
            featureWiseAllowed = UsageMetricHandler.updateFeatureMapWithLocalUsageMetrics(featureWiseAllowed, accounts);

            loggerMaker.infoAndAddToDb(String.format("Processed %s features", featureWiseAllowed.size()),LogDb.DASHBOARD);

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

            loggerMaker.infoAndAddToDb("Fetching org metadata",LogDb.DASHBOARD);

            BasicDBObject metaData = OrganizationUtils.fetchOrgMetaData(organizationId, organization.getAdminEmail());
            gracePeriod = OrganizationUtils.fetchOrgGracePeriodFromMetaData(metaData);
            hotjarSiteId = OrganizationUtils.fetchHotjarSiteId(metaData);
            boolean expired = OrganizationUtils.fetchExpired(metaData);
            boolean telemetryEnabled = OrganizationUtils.fetchTelemetryEnabled(metaData);
            setTelemetrySettings(organization, telemetryEnabled);
            boolean testTelemetryEnabled = OrganizationUtils.fetchTestTelemetryEnabled(metaData);
            organization.setTestTelemetryEnabled(testTelemetryEnabled);

            loggerMaker.infoAndAddToDb("Processed org metadata",LogDb.DASHBOARD);

            organization.setHotjarSiteId(hotjarSiteId);

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
                            Updates.set(Organization.TEST_TELEMETRY_ENABLED, testTelemetryEnabled),
                            Updates.set(Organization.LAST_FEATURE_MAP_UPDATE, lastFeatureMapUpdate)));

            loggerMaker.infoAndAddToDb("Updated org",LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, aktoVersion + " error while fetching feature wise allowed: " + e.toString(),
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
            loggerMaker.infoAndAddToDb(String.format("No accounts found for %s, skipping telemetry settings processing", organization.getId()), LogDb.DASHBOARD);
            return;
        }

        for (Integer accountId : accounts) {
            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(eq("_id", accountId));

            if(accountSettings == null){
                loggerMaker.infoAndAddToDb(String.format("No account settings found for %s account", accountId), LogDb.DASHBOARD);
                continue;
            }

            TelemetrySettings settings = accountSettings.getTelemetrySettings();

            if(settings == null){
                loggerMaker.infoAndAddToDb(String.format("No telemetry settings found for %s account", accountId), LogDb.DASHBOARD);
                continue;
            }

            if(settings.getStiggEnabled() != telemetryEnabled){
                loggerMaker.infoAndAddToDb(String.format("Current stigg setting: %s, new stigg setting: %s for accountId: %d", settings.getStiggEnabled(), telemetryEnabled, accountId), LogDb.DASHBOARD);
                settings.setStiggEnabled(telemetryEnabled);
                settings.setStiggEnabledAt(Context.now());
                accountSettings.setTelemetrySettings(settings);
                AccountSettingsDao.instance.updateOne(eq("_id", accountId), Updates.set(AccountSettings.TELEMETRY_SETTINGS, accountSettings.getTelemetrySettings()));
            }
            else {
                loggerMaker.infoAndAddToDb(String.format("Current stigg setting: %s, new stigg setting: %s for accountId: %d are same, not taking any action", settings.getStiggEnabled(), telemetryEnabled, accountId), LogDb.DASHBOARD);
            }
        }
    }

    private static void setOrganizationsInBilling(BackwardCompatibility backwardCompatibility) {
        backwardCompatibility.setOrgsInBilling(0);
        if (backwardCompatibility.getOrgsInBilling() == 0) {
            if (!executedOnce) {
                loggerMaker.infoAndAddToDb("in execute setOrganizationsInBilling", LogDb.DASHBOARD);
                OrganizationTask.instance.executeTask(new Consumer<Organization>() {
                    @Override
                    public void accept(Organization organization) {
                        if (!organization.getSyncedWithAkto()) {
                            loggerMaker.infoAndAddToDb("Syncing Akto billing for org: " + organization.getId(), LogDb.DASHBOARD);
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

    private static void dropLastCronRunInfoField(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getDeleteLastCronRunInfo() == 0){
            AccountSettingsDao.instance.updateOne(Filters.empty(), Updates.unset(AccountSettings.LAST_UPDATED_CRON_INFO));

            BackwardCompatibilityDao.instance.updateOne(
                        Filters.eq("_id", backwardCompatibility.getId()),
                        Updates.set(BackwardCompatibility.DELETE_LAST_CRON_RUN_INFO, Context.now())
                );
        }
    }

    public static void setBackwardCompatibilities(BackwardCompatibility backwardCompatibility){
        initializeOrganizationAccountBelongsTo(backwardCompatibility);
        if (DashboardMode.isMetered()) {
            setOrganizationsInBilling(backwardCompatibility);
        }
        setAktoDefaultNewUI(backwardCompatibility);
        dropLastCronRunInfoField(backwardCompatibility);
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
        deleteAccessListFromApiToken(backwardCompatibility);
        deleteNullSubCategoryIssues(backwardCompatibility);
        enableNewMerging(backwardCompatibility);
        setDefaultTelemetrySettings(backwardCompatibility);
        disableAwsSecretPiiType(backwardCompatibility);
        if (DashboardMode.isMetered()) {
            initializeOrganizationAccountBelongsTo(backwardCompatibility);
        }
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
            loggerMaker.infoAndAddToDb(val + " - " + count, LoggerMaker.LogDb.DASHBOARD);
        }
    }

    public void runInitializerFunctions() {
        // DaoInit.createIndices();


        BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
        if (backwardCompatibility == null) {
            backwardCompatibility = new BackwardCompatibility();
            BackwardCompatibilityDao.instance.insertOne(backwardCompatibility);
        }

        // backward compatibility
        try {
            setBackwardCompatibilities(backwardCompatibility);
            loggerMaker.infoAndAddToDb("Backward compatibilities set for " + Context.accountId.get(), LogDb.DASHBOARD);
            insertPiiSources();
            loggerMaker.infoAndAddToDb("PII sources inserted set for " + Context.accountId.get(), LogDb.DASHBOARD);

//            setUpPiiCleanerScheduler();
//            setUpDailyScheduler();
//            setUpWebhookScheduler();
//            setUpPiiAndTestSourcesScheduler();

            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            dropSampleDataIfEarlierNotDroped(accountSettings);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"error while setting up dashboard: " + e.toString(), LogDb.DASHBOARD);
        }

        try {
            loggerMaker.infoAndAddToDb("Updating account version for " + Context.accountId.get(), LogDb.DASHBOARD);
            AccountSettingsDao.instance.updateVersion(AccountSettings.DASHBOARD_VERSION);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,"error while updating dashboard version: " + e.toString(), LogDb.DASHBOARD);
        }


        if(DashboardMode.isOnPremDeployment()) {
            telemetryExecutorService.scheduleAtFixedRate(() -> {
                boolean dibs = callDibs(Cluster.TELEMETRY_CRON, 60, 60);
                if (!dibs) {
                    loggerMaker.infoAndAddToDb("Telemetry cron dibs not acquired, skipping telemetry cron", LoggerMaker.LogDb.DASHBOARD);
                    return;
                }
                TelemetryJob job = new TelemetryJob();
                OrganizationTask.instance.executeTask(job::run, "telemetry-cron");
            }, 0, 1, TimeUnit.MINUTES);
            loggerMaker.infoAndAddToDb("Registered telemetry cron", LogDb.DASHBOARD);
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
            logger.info("Owner email missing, might be an existing customer, skipping sending an slack and mixpanel alert");
            return;
        }
        if (backwardCompatibility.isDeploymentStatusUpdated()) {
            loggerMaker.infoAndAddToDb("Deployment status has already been updated, skipping this", LogDb.DASHBOARD);
            return;
        }
        String body = "{\n    \"ownerEmail\": \"" + ownerEmail + "\",\n    \"stackStatus\": \"COMPLETED\",\n    \"cloudType\": \"AWS\"\n}";
        String headers = "{\"Content-Type\": \"application/json\"}";
        OriginalHttpRequest request = new OriginalHttpRequest(getUpdateDeploymentStatusUrl(), "", "POST", body, OriginalHttpRequest.buildHeadersMap(headers), "");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, false, null, false, new ArrayList<>());
            loggerMaker.infoAndAddToDb(String.format("Update deployment status reponse: %s", response.getBody()), LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e,String.format("Failed to update deployment status, will try again on next boot up : %s", e.toString()), LogDb.DASHBOARD);
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

    public final static String _AKTO = "AKTO";

    public void setUpTestEditorTemplatesScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                byte[] testingTemplates = TestTemplateUtils.getTestingTemplates();
                if(testingTemplates == null){
                    loggerMaker.errorAndAddToDb("Error while fetching Test Editor Templates from Github and local", LogDb.DASHBOARD);
                    return;
                }
                AccountTask.instance.executeTask((account) -> {
                    try {
                        loggerMaker.infoAndAddToDb("Updating Test Editor Templates for accountId: " + account.getId(), LogDb.DASHBOARD);
                        processTemplateFilesZip(testingTemplates, _AKTO, YamlTemplateSource.AKTO_TEMPLATES.toString(), "");
                    } catch (Exception e) {
                        cacheLoggerMaker.errorAndAddToDb(e,
                                String.format("Error while updating Test Editor Files %s", e.toString()),
                                LogDb.DASHBOARD);
                    }
                }, "update-test-editor-templates-github");
            }
        }, 0, 4, TimeUnit.HOURS);
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

                while ((entry = zipInputStream.getNextEntry()) != null) {
                    if (!entry.isDirectory()) {
                        String entryName = entry.getName();

                        if (!(entryName.endsWith(".yaml") || entryName.endsWith(".yml"))) {
                            loggerMaker.infoAndAddToDb(
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
                        try {
                            testConfig = TestConfigYamlParser.parseTemplate(templateContent);
                            String testConfigId = testConfig.getId();

                            List<YamlTemplate> existingTemplatesInDb = mapIdToHash.get(testConfigId);

                            if (existingTemplatesInDb != null && existingTemplatesInDb.size() == 1) {
                                int existingTemplateHash = existingTemplatesInDb.get(0).getHash();
                                if (existingTemplateHash == templateContent.hashCode()) {
                                    countUnchangedTemplates++;
                                    continue;
                                } else {
                                    loggerMaker.infoAndAddToDb("Updating test yaml: " + testConfigId, LogDb.DASHBOARD);
                                }
                            }

                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e,
                                    String.format("Error parsing yaml template file %s %s", entryName, e.toString()),
                                    LogDb.DASHBOARD);
                        }

                        if (testConfig != null) {
                            String id = testConfig.getId();
                            int createdAt = Context.now();
                            int updatedAt = Context.now();

                            List<Bson> updates = new ArrayList<>(
                                    Arrays.asList(
                                            Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
                                            Updates.setOnInsert(YamlTemplate.AUTHOR, author),
                                            Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                                            Updates.set(YamlTemplate.HASH, templateContent.hashCode()),
                                            Updates.set(YamlTemplate.CONTENT, templateContent),
                                            Updates.set(YamlTemplate.INFO, testConfig.getInfo())));

                            try {
                                Object inactiveObject = TestConfigYamlParser.getFieldIfExists(templateContent,
                                        YamlTemplate.INACTIVE);
                                if (inactiveObject != null && inactiveObject instanceof Boolean) {
                                    boolean inactive = (boolean) inactiveObject;
                                    updates.add(Updates.set(YamlTemplate.INACTIVE, inactive));
                                }
                            } catch (Exception e) {
                            }

                            if (_AKTO.equals(author)) {
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
                                                Filters.ne(YamlTemplate.AUTHOR, _AKTO)),
                                        Updates.combine(updates),
                                        new FindOneAndUpdateOptions().upsert(true));
                                } catch (Exception e){
                                    loggerMaker.errorAndAddToDb(
                                            String.format("Error while inserting Test template %s Error: %s", id, e.getMessage()),
                                            LogDb.DASHBOARD);
                                }
                            }

                        }
                    }

                    // Close the current entry to proceed to the next one
                    zipInputStream.closeEntry();
                }

                if (countTotalTemplates != countUnchangedTemplates) {
                    loggerMaker.infoAndAddToDb(countUnchangedTemplates + "/" + countTotalTemplates + " unchanged", LogDb.DASHBOARD);
                }
            } catch (Exception ex) {
                cacheLoggerMaker.errorAndAddToDb(ex,
                        String.format("Error while processing Test template files zip. Error %s", ex.getMessage()),
                        LogDb.DASHBOARD);
            }
        }
    }

    public static void saveLLmTemplates() {
        List<String> templatePaths = new ArrayList<>();
        loggerMaker.infoAndAddToDb("saving llm templates", LoggerMaker.LogDb.DASHBOARD);
        try {
            templatePaths = convertStreamToListString(InitializerListener.class.getResourceAsStream("/inbuilt_llm_test_yaml_files"));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("failed to read test yaml folder %s", e.toString()), LogDb.DASHBOARD);
        }

        String template = null;
        for (String path: templatePaths) {
            try {
                template = convertStreamToString(InitializerListener.class.getResourceAsStream("/inbuilt_llm_test_yaml_files/" + path));
                //logger.info(template);
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
                loggerMaker.errorAndAddToDb(e, String.format("failed to read test yaml path %s %s", template, e.toString()), LogDb.DASHBOARD);
            }
        }
        loggerMaker.infoAndAddToDb("finished saving llm templates", LoggerMaker.LogDb.DASHBOARD);
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
        logger.info("Running usage sync scheduler");
        try {
            List<UsageMetric> usageMetrics = UsageMetricsDao.instance.findAll(
                    Filters.eq(UsageMetric.SYNCED_WITH_AKTO, false)
            );

            loggerMaker.infoAndAddToDb(String.format("Syncing %d usage metrics", usageMetrics.size()), LogDb.DASHBOARD);

            for (UsageMetric usageMetric : usageMetrics) {
                loggerMaker.infoAndAddToDb(String.format("Syncing usage metric %s  %s/%d %s",
                                usageMetric.getId().toString(), usageMetric.getOrganizationId(), usageMetric.getAccountId(), usageMetric.getMetricType().toString()),
                        LogDb.DASHBOARD
                );
                UsageMetricUtils.syncUsageMetricWithAkto(usageMetric);
                
                // Send to mixpanel
                UsageMetricUtils.syncUsageMetricWithMixpanel(usageMetric);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, String.format("Error while syncing usage metrics. Error: %s", e.getMessage()), LogDb.DASHBOARD);
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
                    loggerMaker.infoAndAddToDb("Usage cron dibs not acquired, skipping usage cron", LoggerMaker.LogDb.DASHBOARD);
                    return;
                }
                calcUsage();
            }
        }, 0, 1, UsageUtils.USAGE_CRON_PERIOD);
    }

    public void setupUsageSyncScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                syncWithAkto();
            }
        }, 0, 1, UsageUtils.USAGE_CRON_PERIOD);
    }

    public static void deleteFileUploads(int accountId){
        Context.accountId.set(accountId);
        List<FileUpload> markedForDeletion = FileUploadsDao.instance.findAll(eq("markedForDeletion", true));
        loggerMaker.infoAndAddToDb(String.format("Deleting %d file uploads", markedForDeletion.size()), LogDb.DASHBOARD);
        for (FileUpload fileUpload : markedForDeletion) {
            loggerMaker.infoAndAddToDb(String.format("Deleting file upload logs for uploadId: %s", fileUpload.getId()), LogDb.DASHBOARD);
            FileUploadLogsDao.instance.deleteAll(eq("uploadId", fileUpload.getId().toString()));
            loggerMaker.infoAndAddToDb(String.format("Deleting file upload: %s", fileUpload.getId()), LogDb.DASHBOARD);
            FileUploadsDao.instance.deleteAll(eq("_id", fileUpload.getId()));
            loggerMaker.infoAndAddToDb(String.format("Deleted file upload: %s", fileUpload.getId()), LogDb.DASHBOARD);
        }
    }

    public void setupAutomatedApiGroupsScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {

                // Acquire dibs for updating automated API groups
                Context.accountId.set(1000_000);
                boolean dibs = callDibs(Cluster.AUTOMATED_API_GROUPS_CRON,  4*60*60, 60);
                if(!dibs){
                    loggerMaker.infoAndAddToDb("Cron for updating automated API groups not acquired, thus skipping cron", LogDb.DASHBOARD);
                    return;
                }

                loggerMaker.infoAndAddToDb("Cron for updating automated API groups picked up.", LogDb.DASHBOARD);

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
                            loggerMaker.errorAndAddToDb("Error while processing automated api groups: " + e.getMessage(), LogDb.DASHBOARD);
                        }
                    }
                }, "automated-api-groups-scheduler");
            }
        }, 0, 4, TimeUnit.HOURS);
    }
}

