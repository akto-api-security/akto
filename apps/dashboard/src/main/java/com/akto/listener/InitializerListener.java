package com.akto.listener;

import com.akto.DaoInit;
import com.akto.action.AdminSettingsAction;
import com.akto.action.ApiCollectionsAction;
import com.akto.action.observe.InventoryAction;
import com.akto.dao.*;
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
import com.akto.dao.traffic_metrics.TrafficMetricsDao;
import com.akto.dto.*;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Conditions.Operator;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhook.ActiveStatus;
import com.akto.dto.notifications.CustomWebhook.WebhookOptions;
import com.akto.dto.notifications.CustomWebhookResult;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.pii.PIISource;
import com.akto.dto.pii.PIIType;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.rate_limit.ApiRateLimit;
import com.akto.dto.testing.rate_limit.GlobalApiRateLimit;
import com.akto.dto.testing.rate_limit.RateLimitHandler;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.email.WeeklyEmail;
import com.akto.notifications.slack.DailyUpdate;
import com.akto.notifications.slack.TestSummaryGenerator;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.Main;
import com.akto.runtime.policies.AktoPolicyNew;
import com.akto.testing.ApiExecutor;
import com.akto.testing.ApiWorkflowExecutor;
import com.akto.testing.HostDNSLookup;
import com.akto.util.AccountTask;
import com.akto.util.JSONUtils;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.TestCategory;
import com.akto.utils.Auth0;
import com.akto.utils.DashboardMode;
import com.akto.utils.RedactSampleData;
import com.akto.utils.Utils;
import com.akto.utils.scripts.FixMultiSTIs;
import com.google.gson.Gson;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.*;
import com.slack.api.Slack;
import com.slack.api.webhook.WebhookResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContextListener;
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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.mongodb.client.model.Filters.eq;

public class InitializerListener implements ServletContextListener {
    private static final Logger logger = LoggerFactory.getLogger(InitializerListener.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(InitializerListener.class);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static final boolean isSaas = "true".equals(System.getenv("IS_SAAS"));
    private static final int THREE_HOURS = 3*60*60;
    private static final int CONNECTION_TIMEOUT = 10 * 1000;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public static boolean connectedToMongo = false;

    private static String domain = null;
    public static String subdomain = "https://app.akto.io";

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

    public void setUpPiiCleanerScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            Set<Integer> whiteListCollectionSet = new HashSet<>();
                            whiteListCollectionSet.add(-122281555);
                            executePiiCleaner(whiteListCollectionSet);
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb("Error while running executePiiCleaner: " + e.getMessage(), LogDb.DASHBOARD);
                        }

                        Set<Integer> whiteList = new HashSet<>();
                        whiteList.add(-1027775082);
                        whiteList.add(-1088931039);
                        whiteList.add(-1097655237);
                        whiteList.add(-1118670922);
                        whiteList.add(-1140971867);
                        whiteList.add(-1186432212);
                        whiteList.add(-1203891926);
                        whiteList.add(-1209406502);
                        whiteList.add(-1217614242);
                        whiteList.add(-1271633658);
                        whiteList.add(-1309163021);
                        whiteList.add(-1314472448);
                        whiteList.add(-1375943771);
                        whiteList.add(-1377024902);
                        whiteList.add(-1380042638);
                        whiteList.add(-1388740288);
                        whiteList.add(-1475376397);
                        whiteList.add(-1476745903);
                        whiteList.add(-1481427444);
                        whiteList.add(-1482846450);
                        whiteList.add(-1484484778);
                        whiteList.add(-1525973443);
                        whiteList.add(-153399999);
                        whiteList.add(-1625817190);
                        whiteList.add(-1681262209);
                        whiteList.add(-1711078378);
                        whiteList.add(-1711942201);
                        whiteList.add(-172432381);
                        whiteList.add(-1794391977);
                        whiteList.add(-1839474064);
                        whiteList.add(-1890904552);
                        whiteList.add(-192108566);
                        whiteList.add(-1959668442);
                        whiteList.add(-196500695);
                        whiteList.add(-2002390621);
                        whiteList.add(-2010737526);
                        whiteList.add(-2024915123);
                        whiteList.add(-2106083830);
                        whiteList.add(-211436318);
                        whiteList.add(-298709478);
                        whiteList.add(-354731142);
                        whiteList.add(-357559655);
                        whiteList.add(-400200158);
                        whiteList.add(-441065708);
                        whiteList.add(-456924262);
                        whiteList.add(-466725525);
                        whiteList.add(-48038533);
                        whiteList.add(-48796631);
                        whiteList.add(-554843054);
                        whiteList.add(-591275450);
                        whiteList.add(-670990607);
                        whiteList.add(-671936487);
                        whiteList.add(-672863319);
                        whiteList.add(-842565355);
                        whiteList.add(-880699618);
                        whiteList.add(-916813562);
                        whiteList.add(-943618203);
                        whiteList.add(-955905463);
                        whiteList.add(-976518501);
                        whiteList.add(-990322506);
                        whiteList.add(-995352132);
                        whiteList.add(1027418909);
                        whiteList.add(105174556);
                        whiteList.add(1100396685);
                        whiteList.add(1182226);
                        whiteList.add(1236801018);
                        whiteList.add(1242749826);
                        whiteList.add(125554525);
                        whiteList.add(126750582);
                        whiteList.add(1297169588);
                        whiteList.add(1336747391);
                        whiteList.add(1420676141);
                        whiteList.add(142929664);
                        whiteList.add(146111437);
                        whiteList.add(1500768650);
                        whiteList.add(1533695572);
                        whiteList.add(1632066865);
                        whiteList.add(1659693267);
                        whiteList.add(1660119032);
                        whiteList.add(16610327);
                        whiteList.add(1661396548);
                        whiteList.add(1679350553);
                        whiteList.add(1679752531);
                        whiteList.add(1685406);
                        whiteList.add(1689322334);
                        whiteList.add(1788237014);
                        whiteList.add(1818436373);
                        whiteList.add(1855045033);
                        whiteList.add(1957435020);
                        whiteList.add(200307236);
                        whiteList.add(2006838690);
                        whiteList.add(2012237417);
                        whiteList.add(2093029222);
                        whiteList.add(2134611839);
                        whiteList.add(225605510);
                        whiteList.add(242880073);
                        whiteList.add(29531932);
                        whiteList.add(30649496);
                        whiteList.add(307844703);
                        whiteList.add(312400629);
                        whiteList.add(342125588);
                        whiteList.add(363683299);
                        whiteList.add(366452191);
                        whiteList.add(410773812);
                        whiteList.add(419598135);
                        whiteList.add(435249936);
                        whiteList.add(500047679);
                        whiteList.add(524163174);
                        whiteList.add(542683879);
                        whiteList.add(585487033);
                        whiteList.add(598965837);
                        whiteList.add(616884866);
                        whiteList.add(652910700);
                        whiteList.add(711466550);
                        whiteList.add(765725407);
                        whiteList.add(790058525);
                        whiteList.add(821241207);
                        whiteList.add(823491229);
                        whiteList.add(836953198);
                        whiteList.add(841585854);
                        whiteList.add(846348890);
                        whiteList.add(895129364);
                        whiteList.add(901341288);
                        whiteList.add(913107144);
                        whiteList.add(939543314);
                        whiteList.add(946156679);
                        whiteList.add(995617404);
                        whiteList.add(998620205);

                        try {
                            FixMultiSTIs.run(whiteList);
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb("Error while running FixMultiSTIs: " + e.getMessage(), LogDb.DASHBOARD);
                        }

                        try {
                            executePIISourceFetch();
                        } catch (Exception e) {
                        }
                        for (Integer apiCollectionId: whiteList) {
                            try {
                                loggerMaker.infoAndAddToDb("Starting print multiple host for : " + apiCollectionId, LogDb.DASHBOARD);
                                printMultipleHosts(apiCollectionId);
                                loggerMaker.infoAndAddToDb("Finished print multiple host for : " + apiCollectionId, LogDb.DASHBOARD);
                            } catch (Exception e) {
                                loggerMaker.errorAndAddToDb("Error while running print multiple host: " + e.getMessage(), LogDb.DASHBOARD);
                            }
                        }
                    }
                }, "pii-scheduler");
            }
        }, 0, 4, TimeUnit.HOURS);
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

    public static void executePIISourceFetch() {
        List<PIISource> piiSources = PIISourceDao.instance.findAll("active", true);
        for (PIISource piiSource : piiSources) {
            String fileUrl = piiSource.getFileUrl();
            String id = piiSource.getId();
            Map<String, PIIType> currTypes = piiSource.getMapNameToPIIType();
            if (currTypes == null) {
                currTypes = new HashMap<>();
            }

            try {
                if (fileUrl.startsWith("http")) {
                    String tempFileUrl = "temp_" + id;
                    if(downloadFileCheck(tempFileUrl)){
                        FileUtils.copyURLToFile(new URL(fileUrl), new File(tempFileUrl), CONNECTION_TIMEOUT, CONNECTION_TIMEOUT);
                    }
                    fileUrl = tempFileUrl;
                }
                String fileContent = FileUtils.readFileToString(new File(fileUrl), StandardCharsets.UTF_8);
                BasicDBObject fileObj = BasicDBObject.parse(fileContent);
                BasicDBList dataTypes = (BasicDBList) (fileObj.get("types"));
                Bson findQ = Filters.eq("_id", id);

                for (Object dtObj : dataTypes) {
                    BasicDBObject dt = (BasicDBObject) dtObj;
                    String piiKey = dt.getString("name").toUpperCase();
                    PIIType piiType = new PIIType(
                            piiKey,
                            dt.getBoolean("sensitive"),
                            dt.getString("regexPattern"),
                            dt.getBoolean("onKey")
                    );

                    if (!dt.getBoolean("active", true)) {
                        PIISourceDao.instance.updateOne(findQ, Updates.unset("mapNameToPIIType." + piiKey));
                        CustomDataType existingCDT = CustomDataTypeDao.instance.findOne("name", piiKey);
                        if (existingCDT == null) {
                            CustomDataTypeDao.instance.insertOne(getCustomDataTypeFromPiiType(piiSource, piiType, false));
                            continue;
                        } else {
                            CustomDataTypeDao.instance.updateOne("name", piiKey, Updates.set("active", false));
                        }
                    }

                    if (currTypes.containsKey(piiKey) && currTypes.get(piiKey).equals(piiType)) {
                        continue;
                    } else {
                        CustomDataTypeDao.instance.deleteAll(Filters.eq("name", piiKey));
                        if (!dt.getBoolean("active", true)) {
                            PIISourceDao.instance.updateOne(findQ, Updates.unset("mapNameToPIIType." + piiKey));
                            CustomDataTypeDao.instance.insertOne(getCustomDataTypeFromPiiType(piiSource, piiType, false));

                        } else {
                            Bson updateQ = Updates.set("mapNameToPIIType." + piiKey, piiType);
                            PIISourceDao.instance.updateOne(findQ, updateQ);
                            CustomDataTypeDao.instance.insertOne(getCustomDataTypeFromPiiType(piiSource, piiType, true));
                        }

                    }
                }

            } catch (IOException e) {
                loggerMaker.errorAndAddToDb(String.format("failed to read file %s", e.toString()), LogDb.DASHBOARD);
            }
        }
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
                conditions,
                (piiType.getOnKey() ? null : conditions),
                Operator.OR,
                ignoreData
        );

        return ret;
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

                    Slack slack = Slack.getInstance();

                    for (SlackWebhook slackWebhook : listWebhooks) {
                        int now = Context.now();
                        // System.out.println("debugSlack: " + slackWebhook.getLastSentTimestamp() + " " + slackWebhook.getFrequencyInSeconds() + " " +now );

                                if(slackWebhook.getFrequencyInSeconds()==0) {
                                    slackWebhook.setFrequencyInSeconds(24*60*60);
                                }

                                boolean shouldSend = ( slackWebhook.getLastSentTimestamp() + slackWebhook.getFrequencyInSeconds() ) <= now ;

                                if(!shouldSend){
                                    continue;
                                }

                                loggerMaker.infoAndAddToDb(slackWebhook.toString(), LogDb.DASHBOARD);

                                ChangesInfo ci = getChangesInfo(now - slackWebhook.getLastSentTimestamp(), now - slackWebhook.getLastSentTimestamp(), null, null, false);
                                if (ci == null || (ci.newEndpointsLast7Days.size() + ci.newSensitiveParams.size() + ci.recentSentiiveParams + ci.newParamsInExistingEndpoints) == 0) {
                                    return;
                                }

                                DailyUpdate dailyUpdate = new DailyUpdate(
                                0, 0,
                                ci.newSensitiveParams.size(), ci.newEndpointsLast7Days.size(),
                                ci.recentSentiiveParams, ci.newParamsInExistingEndpoints,
                                slackWebhook.getLastSentTimestamp(), now,
                                ci.newSensitiveParams, slackWebhook.getDashboardUrl());

                                slackWebhook.setLastSentTimestamp(now);
                                SlackWebhooksDao.instance.updateOne(eq("webhook", slackWebhook.getWebhook()), Updates.set("lastSentTimestamp", now));

                                loggerMaker.infoAndAddToDb("******************DAILY INVENTORY SLACK******************", LogDb.DASHBOARD);
                                String webhookUrl = slackWebhook.getWebhook();
                                String payload = dailyUpdate.toJSON();
                                loggerMaker.infoAndAddToDb(payload, LogDb.DASHBOARD);
                                try {
                                    URI uri = URI.create(webhookUrl);
                                    if (!HostDNSLookup.isRequestValid(uri.getHost())) {
                                        throw new IllegalArgumentException("SSRF attack attempt");
                                    }
                                    WebhookResponse response = slack.send(webhookUrl, payload);
                                    loggerMaker.infoAndAddToDb("*********************************************************", LogDb.DASHBOARD);

                                    // slack testing notification
                                    loggerMaker.infoAndAddToDb("******************TESTING SUMMARY SLACK******************", LogDb.DASHBOARD);
                                    TestSummaryGenerator testSummaryGenerator = new TestSummaryGenerator(Context.accountId.get());
                                    payload = testSummaryGenerator.toJson(slackWebhook.getDashboardUrl());
                                    loggerMaker.infoAndAddToDb(payload, LogDb.DASHBOARD);
                                    response = slack.send(webhookUrl, payload);
                                    loggerMaker.infoAndAddToDb("*********************************************************", LogDb.DASHBOARD);

                                } catch (Exception e) {
                                    e.printStackTrace();
                                    loggerMaker.errorAndAddToDb("Error while sending slack alert: " + e.getMessage(), LogDb.DASHBOARD);
                                }
                            }
                        }
                    }, "setUpDailyScheduler");
                } catch (Exception ex) {
                    ex.printStackTrace(); // or loggger would be better
                }
            }
        }, 0, 5, TimeUnit.MINUTES);

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
            payload = apiWorkflowExecutor.replaceVariables(webhook.getBody(), valueMap, false);
        } catch (Exception e) {
            errors.add("Failed to replace variables");
        }

        webhook.setLastSentTimestamp(now);
        CustomWebhooksDao.instance.updateOne(Filters.eq("_id", webhook.getId()), Updates.set("lastSentTimestamp", now));

        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(webhook.getHeaderString());
        OriginalHttpRequest request = new OriginalHttpRequest(webhook.getUrl(), webhook.getQueryParams(), webhook.getMethod().toString(), payload, headers, "");
        OriginalHttpResponse response = null; // null response means api request failed. Do not use new OriginalHttpResponse() in such cases else the string parsing fails.

        try {
            response = ApiExecutor.sendRequest(request, true, null);
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
        }, 0, 15, TimeUnit.MINUTES);
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
        try {

            ChangesInfo ret = new ChangesInfo();
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
            loggerMaker.errorAndAddToDb(String.format("get new endpoints %s", e.toString()), LogDb.DASHBOARD);
        }

        return null;
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

    public static void enableMergeAsyncOutside(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getEnableMergeAsyncOutside()== 0) {
            AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(), 
                Updates.set(AccountSettings.MERGE_ASYNC_OUTSIDE, true));
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.ENABLE_ASYNC_MERGE_OUTSIDE, Context.now())
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
            aktoDataTypes.add(new AktoDataType("JWT", false, Arrays.asList(SingleTypeInfo.Position.RESPONSE_PAYLOAD, SingleTypeInfo.Position.RESPONSE_HEADER), now, ignoreData));
            aktoDataTypes.add(new AktoDataType("EMAIL", true, Collections.emptyList(), now, ignoreData));
            aktoDataTypes.add(new AktoDataType("CREDIT_CARD", true, Collections.emptyList(), now, ignoreData));
            aktoDataTypes.add(new AktoDataType("SSN", true, Collections.emptyList(), now, ignoreData));
            aktoDataTypes.add(new AktoDataType("ADDRESS", true, Collections.emptyList(), now, ignoreData));
            aktoDataTypes.add(new AktoDataType("IP_ADDRESS", false, Arrays.asList(SingleTypeInfo.Position.RESPONSE_PAYLOAD, SingleTypeInfo.Position.RESPONSE_HEADER), now, ignoreData));
            aktoDataTypes.add(new AktoDataType("PHONE_NUMBER", true, Collections.emptyList(), now, ignoreData));
            aktoDataTypes.add(new AktoDataType("UUID", false, Collections.emptyList(), now, ignoreData));
            AktoDataTypeDao.instance.getMCollection().drop();
            AktoDataTypeDao.instance.insertMany(aktoDataTypes);

            BackwardCompatibilityDao.instance.updateOne(
                    Filters.eq("_id", backwardCompatibility.getId()),
                    Updates.set(BackwardCompatibility.ADD_AKTO_DATA_TYPES, Context.now())
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

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        setSubdomain();

        System.out.println("context initialized");

        // String mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        System.out.println("MONGO URI " + mongoURI);


        executorService.schedule(new Runnable() {
            public void run() {
                boolean calledOnce = false;
                do {
                    try {

                        if (!calledOnce) {
                            DaoInit.init(new ConnectionString(mongoURI));
                            calledOnce = true;
                        }
                        checkMongoConnection();
                        AccountTask.instance.executeTask(new Consumer<Account>() {
                            @Override
                            public void accept(Account account) {
                                AccountSettingsDao.instance.getStats();
                                runInitializerFunctions();
                            }
                        }, "context-initializer");
                        setUpDailyScheduler();
                        setUpWebhookScheduler();
                        setUpPiiAndTestSourcesScheduler();
                        if(isSaas){
                            try {
                                Auth0.getInstance();
                                loggerMaker.infoAndAddToDb("Auth0 initialized", LogDb.DASHBOARD);
                            } catch (Exception e) {
                                loggerMaker.errorAndAddToDb("Failed to initialize Auth0 due to: " + e.getMessage(), LogDb.DASHBOARD);
                            }
                        }
                    } catch (Exception e) {
//                        e.printStackTrace();
                    } finally {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } while (!connectedToMongo);
            }
        }, 0, TimeUnit.SECONDS);
    }

    public static void insertPiiSources(){
        if (PIISourceDao.instance.findOne("_id", "A") == null) {
            String fileUrl = "https://raw.githubusercontent.com/akto-api-security/pii-types/master/general.json";
            PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
            piiSource.setId("A");

            PIISourceDao.instance.insertOne(piiSource);
        }

        if (PIISourceDao.instance.findOne("_id", "Fin") == null) {
            String fileUrl = "https://raw.githubusercontent.com/akto-api-security/akto/master/pii-types/fintech.json";
            PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
            piiSource.setId("Fin");
            PIISourceDao.instance.insertOne(piiSource);
        }

        if (PIISourceDao.instance.findOne("_id", "File") == null) {
            String fileUrl = "https://raw.githubusercontent.com/akto-api-security/akto/master/pii-types/filetypes.json";
            PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
            piiSource.setId("File");
            PIISourceDao.instance.insertOne(piiSource);
        }
    }

    public static void setBackwardCompatibilities(BackwardCompatibility backwardCompatibility){
        dropFilterSampleDataCollection(backwardCompatibility);
        resetSingleTypeInfoCount(backwardCompatibility);
        dropWorkflowTestResultCollection(backwardCompatibility);
        readyForNewTestingFramework(backwardCompatibility);
        addAktoDataTypes(backwardCompatibility);
        updateDeploymentStatus(backwardCompatibility);
        dropAuthMechanismData(backwardCompatibility);
        deleteAccessListFromApiToken(backwardCompatibility);
        deleteNullSubCategoryIssues(backwardCompatibility);
        enableNewMerging(backwardCompatibility);
        enableMergeAsyncOutside(backwardCompatibility);
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
        SingleTypeInfoDao.instance.createIndicesIfAbsent();
        TrafficMetricsDao.instance.createIndicesIfAbsent();
        TestRolesDao.instance.createIndicesIfAbsent();

        saveTestEditorYaml();

        ApiInfoDao.instance.createIndicesIfAbsent();
        RuntimeLogsDao.instance.createIndicesIfAbsent();
        LogsDao.instance.createIndicesIfAbsent();
        DashboardLogsDao.instance.createIndicesIfAbsent();
        AnalyserLogsDao.instance.createIndicesIfAbsent();
        LoadersDao.instance.createIndicesIfAbsent();
        BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
        if (backwardCompatibility == null) {
            backwardCompatibility = new BackwardCompatibility();
            BackwardCompatibilityDao.instance.insertOne(backwardCompatibility);
        }

        // backward compatibility
        try {
            setBackwardCompatibilities(backwardCompatibility);

            SingleTypeInfo.init();

            insertPiiSources();

//            setUpPiiCleanerScheduler();
            setUpDailyScheduler();
            setUpWebhookScheduler();
            setUpPiiAndTestSourcesScheduler();

            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            dropSampleDataIfEarlierNotDroped(accountSettings);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error while setting up dashboard: " + e.toString(), LogDb.DASHBOARD);
        }

        try {
            AccountSettingsDao.instance.updateVersion(AccountSettings.DASHBOARD_VERSION);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("error while updating dashboard version: " + e.toString(), LogDb.DASHBOARD);
        }

        try {
            readAndSaveBurpPluginVersion();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static int burpPluginVersion = -1;

    public void readAndSaveBurpPluginVersion() {
        URL url = this.getClass().getResource("/Akto.jar");
        if (url == null) return;

        try (JarFile jarFile = new JarFile(url.getPath())) {
            Enumeration<JarEntry> jarEntries = jarFile.entries();

            while (jarEntries.hasMoreElements()) {
                JarEntry entry = jarEntries.nextElement();
                if (entry.getName().contains("AktoVersion.txt")) {
                    InputStream inputStream = jarFile.getInputStream(entry);
                    String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                    burpPluginVersion = Integer.parseInt(result.trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

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
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, false, null);
            loggerMaker.infoAndAddToDb(String.format("Update deployment status reponse: %s", response.getBody()), LogDb.DASHBOARD);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(String.format("Failed to update deployment status, will try again on next boot up : %s", e.toString()), LogDb.DASHBOARD);
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

    public static void saveTestEditorYaml() {
        List<String> templatePaths = new ArrayList<>();
        try {
            templatePaths = convertStreamToListString(InitializerListener.class.getResourceAsStream("/inbuilt_test_yaml_files"));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(String.format("failed to read test yaml folder %s", e.toString()), LogDb.DASHBOARD);
        }

        String template = null;
        for (String path: templatePaths) {
            try {
                template = convertStreamToString(InitializerListener.class.getResourceAsStream("/inbuilt_test_yaml_files/" + path));
                //System.out.println(template);
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
                loggerMaker.errorAndAddToDb(String.format("failed to read test yaml path %s %s", template, e.toString()), LogDb.DASHBOARD);
            }
        }
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

    private static String convertStreamToString(InputStream in) throws Exception {

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
}