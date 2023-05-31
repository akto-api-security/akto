package com.akto.listener;

import com.akto.DaoInit;
import com.akto.action.AdminSettingsAction;
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
import com.akto.dao.testing.sources.TestSourceConfigsDao;
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
import com.akto.dto.test_editor.Metadata;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.github.GithubFile;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.slack.DailyUpdate;
import com.akto.notifications.slack.TestSummaryGenerator;
import com.akto.testing.ApiExecutor;
import com.akto.testing.ApiWorkflowExecutor;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestCategory;
import com.akto.utils.DashboardMode;
import com.akto.utils.DashboardVersion;
import com.akto.utils.GithubSync;
import com.akto.utils.HttpUtils;
import com.akto.utils.RedactSampleData;
import com.itextpdf.text.Meta;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.slack.api.Slack;
import com.slack.api.webhook.WebhookResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.checkerframework.checker.units.qual.A;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import javax.servlet.ServletContextListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.mongodb.client.model.Filters.eq;

public class InitializerListener implements ServletContextListener {
    private static final Logger logger = LoggerFactory.getLogger(InitializerListener.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(InitializerListener.class);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public static boolean connectedToMongo = false;

    private static String domain = null;

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

    public void setUpPiiAndTestSourcesScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                String mongoURI = System.getenv("AKTO_MONGO_CONN");
                DaoInit.init(new ConnectionString(mongoURI));
                Context.accountId.set(1_000_000);
                try {
                    executeTestSourcesFetch();
                    editTestSourceConfig();
                } catch (Exception e) {

                }

                try {
                    executePIISourceFetch();
                } catch (Exception e) {

                }
            }
        }, 0, 4, TimeUnit.HOURS);
    }
    static void editTestSourceConfig() throws IOException{
        List<TestSourceConfig> detailsTest = TestSourceConfigsDao.instance.findAll(new BasicDBObject()) ;
        for(TestSourceConfig tsc : detailsTest){
            String filePath = tsc.getId() ;
            filePath = filePath.replace("https://github.com/", "https://raw.githubusercontent.com/").replace("/blob/", "/");
            try {
                FileUtils.copyURLToFile(new URL(filePath), new File(filePath));
            } catch (IOException e1) {
                e1.printStackTrace();
                continue;
            }

            Yaml yaml = new Yaml();
            InputStream inputStream = java.nio.file.Files.newInputStream(new File(filePath).toPath());
            try {
                Map<String, Map<String,Object>> data = yaml.load(inputStream);
                if (data == null) data = new HashMap<>();

                Map<String ,Object> currObj = new HashMap<>() ;
                if(data != null){
                    currObj = data.get("info");
                }
                if(currObj != null){
                    String description = (String) currObj.get("name");
                    if(description != null){
                        tsc.setDescription(description);
                    }

                    String severity = (String) currObj.get("severity");
                    Severity castedSeverity = Severity.LOW ;
                    if(severity != null && severity.toLowerCase() != "unknown"){
                        castedSeverity = Severity.valueOf(severity.toUpperCase()) ;
                        tsc.setSeverity(castedSeverity);
                    }
                    
                    String stringTags = (String) currObj.get("tags") ;
                    List<String> tags = new ArrayList<>();
                    if(stringTags != null){
                        tags = Arrays.asList(stringTags.split(","));
                        tsc.setTags(tags);
                    }

                    TestSourceConfigsDao.instance.updateOne(Filters.eq("_id", tsc.getId()),
                            Updates.combine(Updates.set("description", description),
                                    Updates.set("severity", castedSeverity), Updates.set("tags", tags)));
                }
            } catch (Exception e) {
                // TODO: handle exception
            }
            
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

    static void executeTestSourcesFetch() {
        try {
            TestCategory[] testCategories = TestCategory.values();
            Map<String, TestCategory> shortNameToTestCategory = new HashMap<>();
            for (TestCategory tc : testCategories) {
                String sn = tc.getShortName().replaceAll("-", "").replaceAll("_", "")
                        .replaceAll(" ", "").toLowerCase();
                shortNameToTestCategory.put(sn, tc);
            }

            String testingSourcesRepoTree = "https://api.github.com/repos/akto-api-security/tests-library/git/trees/master?recursive=1";
            String tempFilename = "temp_testingSourcesRepoTree.json";
            FileUtils.copyURLToFile(new URL(testingSourcesRepoTree), new File(tempFilename));
            String fileContent = FileUtils.readFileToString(new File(tempFilename), StandardCharsets.UTF_8);
            BasicDBObject fileList = BasicDBObject.parse(fileContent);
            BasicDBList files = (BasicDBList) (fileList.get("tree"));

            BasicDBObject systemTestsQuery = new BasicDBObject(TestSourceConfig.CREATOR, TestSourceConfig.DEFAULT);
            List<TestSourceConfig> currConfigs = TestSourceConfigsDao.instance.findAll(systemTestsQuery);
            Map<String, TestSourceConfig> currConfigsMap = new HashMap<>();
            for (TestSourceConfig tsc : currConfigs) {

                if (tsc.getCategory() == null || tsc.getCategory().equals(TestCategory.UC)) {
                    Bson deleteQ = Filters.eq("_id", tsc.getId());
                    TestSourceConfigsDao.instance.getMCollection().deleteOne(deleteQ);
                } else {
                    currConfigsMap.put(tsc.getId(), tsc);
                }
            }

            if (files == null) return;
            for (Object fileObj : files) {
                BasicDBObject fileDetails = (BasicDBObject) fileObj;
                String filePath = fileDetails.getString("path");
                if (filePath.endsWith(".yaml") || filePath.endsWith(".yml")) {
                    if(filePath.contains("business-logic")){
                        continue;
                    }
                    String categoryFolder = filePath.split("/")[0];
                    filePath = "https://github.com/akto-api-security/tests-library/blob/master/" + filePath;
                    if (!currConfigsMap.containsKey(filePath)) {
                        TestCategory testCategory = findTestCategory(categoryFolder, shortNameToTestCategory);
                        String subcategory = findTestSubcategory(filePath);
                        TestSourceConfig testSourceConfig = new TestSourceConfig(filePath, testCategory, subcategory, Severity.HIGH, "", TestSourceConfig.DEFAULT, Context.now(), new ArrayList<>());
                        TestSourceConfigsDao.instance.insertOne(testSourceConfig);
                    }
                    currConfigsMap.remove(filePath);
                }
            }

            for (String toBeDeleted : currConfigsMap.keySet()) {
                TestSourceConfigsDao.instance.getMCollection().deleteOne(new BasicDBObject("_id", toBeDeleted));
            }


        } catch (IOException e1) {
        }


    }

    static void executePIISourceFetch() {
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
                    FileUtils.copyURLToFile(new URL(fileUrl), new File(tempFileUrl));
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
                continue;
            }
        }
        SingleTypeInfo.fetchCustomDataTypes();
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
                    Context.accountId.set(1_000_000);
                    List<SlackWebhook> listWebhooks = SlackWebhooksDao.instance.findAll(new BasicDBObject());
                    if (listWebhooks == null || listWebhooks.isEmpty()) {
                        return;
                    }

                    Slack slack = Slack.getInstance();
        
                    for(SlackWebhook slackWebhook: listWebhooks) {
                        int now =Context.now();

                        if (slackWebhook.getFrequencyInSeconds() == 0) {
                            slackWebhook.setFrequencyInSeconds(24 * 60 * 60);
                        }

                        boolean shouldSend = (slackWebhook.getLastSentTimestamp() + slackWebhook.getFrequencyInSeconds()) <= now;

                        if (!shouldSend) {
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
                        WebhookResponse response = slack.send(webhookUrl, payload);
                        loggerMaker.infoAndAddToDb("*********************************************************", LogDb.DASHBOARD);

                        // slack testing notification
                        loggerMaker.infoAndAddToDb("******************TESTING SUMMARY SLACK******************", LogDb.DASHBOARD);
                        TestSummaryGenerator testSummaryGenerator = new TestSummaryGenerator(1_000_000);
                        payload = testSummaryGenerator.toJson(slackWebhook.getDashboardUrl());
                        loggerMaker.infoAndAddToDb(payload, LogDb.DASHBOARD);
                        response = slack.send(webhookUrl, payload);
                        loggerMaker.infoAndAddToDb("*********************************************************", LogDb.DASHBOARD);

                    }

                } catch (Exception ex) {
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
            response = ApiExecutor.sendRequest(request, true);
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
        }
    }

    public void setUpWebhookScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                String mongoURI = System.getenv("AKTO_MONGO_CONN");
                DaoInit.init(new ConnectionString(mongoURI));
                Context.accountId.set(1_000_000);

                webhookSender();
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

    public void dropFilterSampleDataCollection(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDropFilterSampleData() == 0) {
            FilterSampleDataDao.instance.getMCollection().drop();
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DROP_FILTER_SAMPLE_DATA, Context.now())
        );
    }

    public void dropAuthMechanismData(BackwardCompatibility authMechanismData) {
        if (authMechanismData.getAuthMechanismData() == 0) {
            AuthMechanismsDao.instance.getMCollection().drop();
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", authMechanismData.getId()),
                Updates.set(BackwardCompatibility.AUTH_MECHANISM_DATA, Context.now())
        );
    }

    public void dropWorkflowTestResultCollection(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDropWorkflowTestResult() == 0) {
            WorkflowTestResultsDao.instance.getMCollection().drop();
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DROP_WORKFLOW_TEST_RESULT, Context.now())
        );
    }

    public void resetSingleTypeInfoCount(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getResetSingleTypeInfoCount() == 0) {
            SingleTypeInfoDao.instance.resetCount();
        }

        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.RESET_SINGLE_TYPE_INFO_COUNT, Context.now())
        );
    }

    public void dropSampleDataIfEarlierNotDroped(AccountSettings accountSettings) {
        if (accountSettings == null) return;
        if (accountSettings.isRedactPayload() && !accountSettings.isSampleDataCollectionDropped()) {
            AdminSettingsAction.dropCollections(Context.accountId.get());
        }

    }

    public void deleteAccessListFromApiToken(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getDeleteAccessListFromApiToken() == 0) {
            ApiTokensDao.instance.updateMany(new BasicDBObject(), Updates.unset("accessList"));
        }

        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DELETE_ACCESS_LIST_FROM_API_TOKEN, Context.now())
        );
    }

    public void deleteNullSubCategoryIssues(BackwardCompatibility backwardCompatibility) {
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

    public void enableNewMerging(BackwardCompatibility backwardCompatibility) {
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

    public void readyForNewTestingFramework(BackwardCompatibility backwardCompatibility) {
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

    public void addAktoDataTypes(BackwardCompatibility backwardCompatibility) {
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

    @Override
    public void contextInitialized(javax.servlet.ServletContextEvent sce) {
        sce.getServletContext().getSessionCookieConfig().setSecure(HttpUtils.isHttpsEnabled());

        logger.info("context initialized");

        // String mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        logger.info("MONGO URI " + mongoURI);


        executorService.schedule(new Runnable() {
            public void run() {
                boolean calledOnce = false;
                do {
                    try {
                        if (!calledOnce) {
                            DaoInit.init(new ConnectionString(mongoURI));
                            Context.accountId.set(1_000_000);
                            calledOnce = true;
                        }
                        AccountSettingsDao.instance.getStats();
                        connectedToMongo = true;
                        runInitializerFunctions();
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

    public void runInitializerFunctions() {
        SingleTypeInfoDao.instance.createIndicesIfAbsent();
        TrafficMetricsDao.instance.createIndicesIfAbsent();
        TestRolesDao.instance.createIndicesIfAbsent();

        ApiInfoDao.instance.createIndicesIfAbsent();
        RuntimeLogsDao.instance.createIndicesIfAbsent();
        LogsDao.instance.createIndicesIfAbsent();
        DashboardLogsDao.instance.createIndicesIfAbsent();
        LoadersDao.instance.createIndicesIfAbsent();
        BackwardCompatibility backwardCompatibility = BackwardCompatibilityDao.instance.findOne(new BasicDBObject());
        if (backwardCompatibility == null) {
            backwardCompatibility = new BackwardCompatibility();
            BackwardCompatibilityDao.instance.insertOne(backwardCompatibility);
        }

        // backward compatibility
        try {
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
            //loadTemplateFilesFromDirectory(backwardCompatibility);

            SingleTypeInfo.init();

            Context.accountId.set(1000000);

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

            //remove later
            updateTestEditorTemplatesFromGithub();
            setUpDailyScheduler();
            setUpWebhookScheduler();
            setUpPiiAndTestSourcesScheduler();
            setUpTestEditorTemplatesScheduler();

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

    public void updateDeploymentStatus(BackwardCompatibility backwardCompatibility) {
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
            OriginalHttpResponse response = ApiExecutor.sendRequest(request, false);
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

    private String getUpdateDeploymentStatusUrl() {
        String url = System.getenv("UPDATE_DEPLOYMENT_STATUS_URL");
        return url != null ? url : "https://stairway.akto.io/deployment/status";
    }

    public void setUpTestEditorTemplatesScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                String mongoURI = System.getenv("AKTO_MONGO_CONN");
                DaoInit.init(new ConnectionString(mongoURI));
                Context.accountId.set(1_000_000);
                try {
                    updateTestEditorTemplatesFromGithub();
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(String.format("Error while updating Test Editor Files %s", e.toString()), LogDb.DASHBOARD);
                }
            }
        }, 0, 4, TimeUnit.HOURS);
    }

    public static void updateTestEditorTemplatesFromGithub() {   
        logger.info("Updating test template files from Github");

        //Get existing template sha values 
        Map<String, String> yamlTemplatesGithubFileSha = new HashMap<>();
        List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(new BasicDBObject());

        for(YamlTemplate yamlTemplate: yamlTemplates) {
            String fileName = yamlTemplate.getFileName();
            String sha = yamlTemplate.getSha();

            //ignore custom temlates
            if (fileName == null || fileName == "" || sha == null || sha == "") {
                continue;
            }

            yamlTemplatesGithubFileSha.put(yamlTemplate.getFileName(), yamlTemplate.getSha());
        }

        GithubSync githubSync = new GithubSync();
        Map<String, GithubFile> templates = githubSync.syncDir("akto-api-security/akto", "apps/dashboard/src/main/resources/inbuilt_test_yaml_files/", yamlTemplatesGithubFileSha);

        if (templates != null) {
            for (GithubFile template : templates.values()) {
                if (template == null) {
                    continue;
                }

                String templateContent = template.getContent();
                String fileName = template.getName();
                String sha = template.getSha();
                
                TestConfig testConfig = null;

                try {
                    testConfig = TestConfigYamlParser.parseTemplate(templateContent);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(String.format("Error parsing yaml template file %s %s", template.getName(), e.toString()), LogDb.DASHBOARD);
                }

                if (testConfig == null) {
                    loggerMaker.errorAndAddToDb(String.format("Error parsing yaml template file %s", template.getName()), LogDb.DASHBOARD);
                } else {
                    Metadata metadata = testConfig.getMetadata();

                    // Get deployment type and the appropriate template version
                    String templateMinimumAktoVersion = null;

                    if (DashboardMode.isLocalDeployment()) {
                        templateMinimumAktoVersion = metadata.getMinAktoVersion();
                    } else {
                        templateMinimumAktoVersion = metadata.getMinOnpremVersion(); 
                    }

                    // Ignore template if templateMinimumAktoVersion is not a semantic version string
                    if(!DashboardVersion.isSemanticVersionString(templateMinimumAktoVersion)) {
                        logger.info(String.format("Template %s has malformatted akto versionspecified.", fileName));
                        continue;
                    }

                    // Get dashboard version
                    String dashboardVersion = DashboardVersion.getDashboardVersion();

                    // Check if updated template is not supported by dashboard
                    if (DashboardVersion.isSemanticVersionString(dashboardVersion)) {
                        if (DashboardVersion.compareVersions(templateMinimumAktoVersion, dashboardVersion) > 0) {
                            logger.info(String.format("Updated Template %s requires minimum akto version %s, skipping update.", fileName, templateMinimumAktoVersion));
                            continue;
                        } 
                    } 

                    String id = testConfig.getId();
                    int createdAt = Context.now();
                    int updatedAt = Context.now();
                    String author = "AKTO";
                    Boolean isActive = metadata.getIsActive();
                    
                    YamlTemplateDao.instance.updateOne(
                        Filters.eq("_id", id),
                        Updates.combine(
                                Updates.setOnInsert(YamlTemplate.CREATED_AT, createdAt),
                                Updates.setOnInsert(YamlTemplate.AUTHOR, author),
                                Updates.setOnInsert(YamlTemplate.FILE_NAME, fileName),
                                Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                                Updates.set(YamlTemplate.CONTENT, templateContent),
                                Updates.set(YamlTemplate.INFO, testConfig.getInfo()),
                                Updates.set(YamlTemplate.SHA, sha),
                                Updates.set(YamlTemplate.IS_ACTIVE, isActive)
                        )
                    );
                }
            }

            // Set templates to inactive if not present in Github
            Set<String> githubFileNames = templates.keySet();

            for(YamlTemplate yamlTemplate: yamlTemplates) {
                String id = yamlTemplate.getId();
                String fileName = yamlTemplate.getFileName();

                if (!githubFileNames.contains(fileName)) {
                    logger.info(String.format("%s not present in Github, marking as inactive", fileName));

                    int updatedAt = Context.now();
                    Boolean isActive = false;

                    YamlTemplateDao.instance.updateOne(
                            Filters.eq("_id", id),
                            Updates.combine(
                                    Updates.set(YamlTemplate.UPDATED_AT, updatedAt),
                                    Updates.set(YamlTemplate.IS_ACTIVE, isActive)
                            )
                        );
                }
            }
        }
    }

    public void loadTemplateFilesFromDirectory(BackwardCompatibility backwardCompatibility) {
        if (backwardCompatibility.getLoadTemplateFilesFromDirectory() == 0) {
            //Load Templates from folder when instance is initialized
            loggerMaker.infoAndAddToDb("Loading test template files from directory", LogDb.DASHBOARD);
            Map<String, String> templates = new HashMap<>();

            // Get list of template file paths
            List<String> templatePaths = new ArrayList<>();
            try {
                templatePaths = convertStreamToListString(InitializerListener.class.getResourceAsStream("/inbuilt_test_yaml_files"));
            } catch (Exception ex) {
                loggerMaker.errorAndAddToDb(String.format("failed to read test yaml folder %s", ex.toString()), LogDb.DASHBOARD);
            }

            // Get templates from files
            for (String path: templatePaths) {
                try {
                    String templateContent = convertStreamToString(InitializerListener.class.getResourceAsStream("/inbuilt_test_yaml_files/" + path));
                    templates.put(path, templateContent);
                } catch (Exception ex) {
                    loggerMaker.errorAndAddToDb(String.format("failed to read test yaml path %s %s", path, ex.toString()), LogDb.DASHBOARD);
                }
            }
            
            for (Map.Entry<String,String> template : templates.entrySet()) {
                String fileName = template.getKey();
                String templateContent = template.getValue();

                TestConfig testConfig = null;

                try {
                    testConfig = TestConfigYamlParser.parseTemplate(templateContent);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(String.format("Error parsing yaml template file  %s %s", fileName, e.toString()), LogDb.DASHBOARD);
                }


                if (testConfig == null) {
                    loggerMaker.errorAndAddToDb(String.format("Error parsing yaml template file  %s", fileName), LogDb.DASHBOARD);
                } else {

                    String id = testConfig.getId();
                    
                    Metadata metadata = testConfig.getMetadata();
                    Boolean isActive = true;

                    if (metadata != null) {
                        isActive = metadata.getIsActive();
                    } 

                    int createdAt = Context.now();
                    int updatedAt = Context.now();
                    String author = "AKTO";
                    String sha = "0";

                    YamlTemplate yamlTemplate = new YamlTemplate(id, createdAt, author, updatedAt, templateContent, testConfig.getInfo(), sha, fileName, isActive);
                    YamlTemplateDao.instance.insertOne(yamlTemplate);
                }
            }

            BackwardCompatibilityDao.instance.updateOne(
                    Filters.eq("_id", backwardCompatibility.getId()),
                    Updates.set(BackwardCompatibility.LOAD_TEMPLATES_FILES_FROM_DIRECTORY, Context.now())
            );
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
        return stringbuilder.toString();
    }
}