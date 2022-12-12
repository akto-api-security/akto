package com.akto.listener;

import com.akto.DaoInit;
import com.akto.action.AdminSettingsAction;
import com.akto.action.observe.InventoryAction;
import com.akto.dao.*;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.BackwardCompatibilityDao;
import com.akto.dao.FilterSampleDataDao;
import com.akto.dao.UsersDao;
import com.akto.dao.testing.*;
import com.akto.dto.*;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.notifications.CustomWebhookResult;
import com.akto.dto.notifications.SlackWebhook;
import com.akto.dto.notifications.CustomWebhook.ActiveStatus;
import com.akto.dto.AccountSettings;
import com.akto.dto.BackwardCompatibility;
import com.akto.dto.CustomDataType;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Predicate;
import com.akto.dto.data_types.RegexPredicate;
import com.akto.dto.data_types.Conditions.Operator;
import com.akto.dto.pii.PIISource;
import com.akto.dto.pii.PIIType;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.notifications.email.WeeklyEmail;
import com.akto.notifications.slack.DailyUpdate;
import com.akto.util.Pair;
import com.akto.utils.RedactSampleData;
import com.google.gson.Gson;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sendgrid.helpers.mail.Mail;
import com.slack.api.Slack;
import com.slack.api.webhook.WebhookResponse;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.notifications.CustomWebhooksResultDao;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dao.pii.PIISourceDao;

import com.akto.testing.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContextListener;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

public class InitializerListener implements ServletContextListener {
    private static final Logger logger = LoggerFactory.getLogger(InitializerListener.class);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static String domain = null;
    public static String getDomain() {
        if(domain == null) {
            if (true) {
                domain = "https://staging.akto.io:8443";
            } else {
                domain = "http://localhost:8080";
            }
        }

        return domain;
    }

    private void setUpWeeklyScheduler() {

        Map<Integer, Integer> dayToDelay = new HashMap<Integer, Integer>();
        dayToDelay.put(Calendar.FRIDAY, 5);
        dayToDelay.put(Calendar.SATURDAY, 4);
        dayToDelay.put(Calendar.SUNDAY, 3);
        dayToDelay.put(Calendar.MONDAY, 2);
        dayToDelay.put(Calendar.TUESDAY, 1);
        dayToDelay.put(Calendar.WEDNESDAY, 0);
        dayToDelay.put(Calendar.THURSDAY, 6);
        Calendar with = Calendar.getInstance();
        Date aDate = new Date();
        with.setTime(aDate);
        int dayOfWeek = with.get(Calendar.DAY_OF_WEEK);
        int delayInDays = dayToDelay.get(dayOfWeek);

        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                try {
                    ChangesInfo changesInfo = getChangesInfo(31, 7);
                    if (changesInfo == null || (changesInfo.newEndpointsLast7Days.size() + changesInfo.newSensitiveParams.size()) == 0) {
                        return;
                    }
                    String sendTo = UsersDao.instance.findOne(new BasicDBObject()).getLogin();
                    logger.info("Sending weekly email");
                    Mail mail = WeeklyEmail.buildWeeklyEmail(
                        changesInfo.recentSentiiveParams, 
                        changesInfo.newEndpointsLast7Days.size(), 
                        changesInfo.newEndpointsLast31Days.size(), 
                        sendTo, 
                        changesInfo.newEndpointsLast7Days, 
                        changesInfo.newSensitiveParams.keySet()
                    );

                    WeeklyEmail.send(mail);

                } catch (Exception ex) {
                    ex.printStackTrace(); // or loggger would be better
                }
            }
        }, delayInDays, 7, TimeUnit.DAYS);

    }
    public void setUpPiiScheduler(){
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                String mongoURI = System.getenv("AKTO_MONGO_CONN");
                DaoInit.init(new ConnectionString(mongoURI));
                Context.accountId.set(1_000_000);

                executePIISourceFetch();
            }
        }, 0, 4, TimeUnit.HOURS);
    }

    static void executePIISourceFetch() {
        List<PIISource> piiSources = PIISourceDao.instance.findAll("active", true);
        for (PIISource piiSource: piiSources) {
            String fileUrl = piiSource.getFileUrl();
            String id = piiSource.getId();
            Map<String, PIIType> currTypes = piiSource.getMapNameToPIIType();
            if (currTypes == null) {
                currTypes = new HashMap<>();
            }

            try {
                if (fileUrl.startsWith("http")) {
                    String tempFileUrl = "temp_"+id;
                    FileUtils.copyURLToFile(new URL(fileUrl), new File(tempFileUrl));
                    fileUrl = tempFileUrl;
                }
                String fileContent = FileUtils.readFileToString(new File(fileUrl), StandardCharsets.UTF_8);
                BasicDBObject fileObj = BasicDBObject.parse(fileContent);
                BasicDBList dataTypes = (BasicDBList) (fileObj.get("types"));
                Bson findQ = Filters.eq("_id", id);

                for (Object dtObj: dataTypes) {
                    BasicDBObject dt = (BasicDBObject) dtObj;
                    String piiKey = dt.getString("name").toUpperCase();
                    PIIType piiType = new PIIType(
                        piiKey,
                        dt.getBoolean("sensitive"),
                        dt.getString("regexPattern"),
                        dt.getBoolean("onKey")
                    );

                    if (!dt.getBoolean("active", true)) {
                        PIISourceDao.instance.updateOne(findQ, Updates.unset("mapNameToPIIType."+piiKey));
                        CustomDataTypeDao.instance.updateOne("name", piiKey, Updates.set("active", false));
                    }

                    if (currTypes.containsKey(piiKey) && currTypes.get(piiKey).equals(piiType)) {
                        continue;
                    } else {
                        CustomDataTypeDao.instance.deleteAll(Filters.eq("name", piiKey));
                        if (!dt.getBoolean("active", true)) {
                            PIISourceDao.instance.updateOne(findQ, Updates.unset("mapNameToPIIType."+piiKey));
                            CustomDataTypeDao.instance.insertOne(getCustomDataTypeFromPiiType(piiSource, piiType,false));

                        } else {
                            Bson updateQ = Updates.set("mapNameToPIIType."+piiKey, piiType);
                            PIISourceDao.instance.updateOne(findQ, updateQ);
                            CustomDataTypeDao.instance.insertOne(getCustomDataTypeFromPiiType(piiSource, piiType,true));
                        } 
                        
                    }
                }

            } catch (IOException e) {
                logger.error("failed to read file", e);
                continue;
            }
        }
        SingleTypeInfo.fetchCustomDataTypes();
    }

    private static CustomDataType getCustomDataTypeFromPiiType(PIISource piiSource, PIIType piiType,Boolean active) {
        String piiKey = piiType.getName();

        List<Predicate> predicates = new ArrayList<>(); 
        Conditions conditions = new Conditions(predicates, Operator.OR);
        predicates.add(new RegexPredicate(piiType.getRegexPattern()));

        CustomDataType ret = new CustomDataType(
            piiKey, 
            piiType.getIsSensitive(), 
            Collections.emptyList(), 
            piiSource.getAddedByUser(), 
            active, 
            conditions, 
            (piiType.getOnKey() ? null : conditions), 
            Operator.OR
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
                        // System.out.println("debugSlack: " + slackWebhook.getLastSentTimestamp() + " " + slackWebhook.getFrequencyInSeconds() + " " +now );

                        if(slackWebhook.getFrequencyInSeconds()==0) {
                            slackWebhook.setFrequencyInSeconds(24*60*60);
                        }

                        boolean shouldSend = ( slackWebhook.getLastSentTimestamp() + slackWebhook.getFrequencyInSeconds() ) <= now ;
                        
                        if(!shouldSend){
                            continue;
                        }

                        System.out.println(slackWebhook);

                        ChangesInfo ci = getChangesInfo(now - slackWebhook.getLastSentTimestamp(), now - slackWebhook.getLastSentTimestamp());
                        if (ci == null || (ci.newEndpointsLast7Days.size() + ci.newSensitiveParams.size() + ci.recentSentiiveParams + ci.newParamsInExistingEndpoints) == 0) {
                            return;
                        }
    
                        DailyUpdate dailyUpdate = new DailyUpdate(
                            0, 0, 
                            ci.newSensitiveParams.size(), ci.newEndpointsLast7Days.size(),
                            ci.recentSentiiveParams, ci.newParamsInExistingEndpoints,
                            slackWebhook.getLastSentTimestamp(), now ,
                            ci.newSensitiveParams, slackWebhook.getDashboardUrl());
                        
                        slackWebhook.setLastSentTimestamp(now);
                        SlackWebhooksDao.instance.updateOne(eq("webhook",slackWebhook.getWebhook()), Updates.set("lastSentTimestamp", now)); 

                        String webhookUrl = slackWebhook.getWebhook();
                        String payload = dailyUpdate.toJSON();
                        System.out.println(payload);
                        WebhookResponse response = slack.send(webhookUrl, payload);
                        System.out.println(response); 
                    }

                } catch (Exception ex) {
                    ex.printStackTrace(); // or loggger would be better
                }
            }
        }, 0, 5, TimeUnit.MINUTES);

    }

    public static void webhookSenderUtil(CustomWebhook webhook){
        Gson gson = new Gson();
        int now = Context.now();

        boolean shouldSend = ( webhook.getLastSentTimestamp() + webhook.getFrequencyInSeconds() ) <= now ;

        if(webhook.getActiveStatus()!=ActiveStatus.ACTIVE || !shouldSend){
            return;
        }

        ChangesInfo ci = getChangesInfo(now - webhook.getLastSentTimestamp(), now - webhook.getLastSentTimestamp());
        if (ci == null || (ci.newEndpointsLast7Days.size() + ci.newSensitiveParams.size() + ci.recentSentiiveParams + ci.newParamsInExistingEndpoints) == 0) {
            return;
        }

        List<String> errors = new ArrayList<>();

        Map<String,Object> valueMap = new HashMap<>();

        valueMap.put("AKTO.changes_info.newSensitiveEndpoints", gson.toJson(ci.newSensitiveParams));
        valueMap.put("AKTO.changes_info.newSensitiveEndpointsCount",ci.newSensitiveParams.size());

        valueMap.put("AKTO.changes_info.newEndpoints",gson.toJson(ci.newEndpointsLast7Days));
        valueMap.put("AKTO.changes_info.newEndpointsCount",ci.newEndpointsLast7Days.size());

        valueMap.put("AKTO.changes_info.newSensitiveParametersCount",ci.recentSentiiveParams);
        valueMap.put("AKTO.changes_info.newParametersCount",ci.newParamsInExistingEndpoints);

        ApiWorkflowExecutor apiWorkflowExecutor = new ApiWorkflowExecutor();
        String payload = null;

        try{
            payload = apiWorkflowExecutor.replaceVariables(webhook.getBody(),valueMap);
        } catch(Exception e){
            errors.add("Failed to replace variables");
        }

        webhook.setLastSentTimestamp(now);
        CustomWebhooksDao.instance.updateOne(Filters.eq("_id",webhook.getId()), Updates.set("lastSentTimestamp", now));

        Map<String,List<String>> headers = OriginalHttpRequest.buildHeadersMap(webhook.getHeaderString());
        OriginalHttpRequest request = new OriginalHttpRequest(webhook.getUrl(),webhook.getQueryParams(),webhook.getMethod().toString(),payload,headers,"");
        OriginalHttpResponse response = null; // null response means api request failed. Do not use new OriginalHttpResponse() in such cases else the string parsing fails.

        try {
            response = ApiExecutor.sendRequest(request,true);
            System.out.println("webhook request sent");
        } catch(Exception e){
            errors.add("API execution failed");
        }

        String message = null;
        try{
            message = RedactSampleData.convertOriginalReqRespToString(request, response);
        } catch(Exception e){
            errors.add("Failed converting sample data");
        }

        CustomWebhookResult webhookResult = new CustomWebhookResult(webhook.getId(),webhook.getUserEmail(),now,message,errors);
        CustomWebhooksResultDao.instance.insertOne(webhookResult);
    }

    public void webhookSender() {
        try {
            List<CustomWebhook> listWebhooks = CustomWebhooksDao.instance.findAll(new BasicDBObject());
            if (listWebhooks == null || listWebhooks.isEmpty()) {
                return;
            }
            
            for(CustomWebhook webhook:listWebhooks) {
                webhookSenderUtil(webhook);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setUpWebhookScheduler(){
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
        public List<String> newEndpointsLast7Days = new ArrayList<>();
        public List<String> newEndpointsLast31Days = new ArrayList<>();
        public int totalSensitiveParams = 0;
        public int recentSentiiveParams = 0;
        public int newParamsInExistingEndpoints = 0;
    }


    public static String extractUrlFromBasicDbObject(BasicDBObject singleTypeInfo, Map<Integer, ApiCollection> apiCollectionMap)  {
        String method = singleTypeInfo.getString("method");
        String path = singleTypeInfo.getString("url");

        Object apiCollectionIdObj = singleTypeInfo.get("apiCollectionId");
        if (apiCollectionIdObj == null) return method + " " + path;

        int apiCollectionId = (int) apiCollectionIdObj;
        ApiCollection apiCollection = apiCollectionMap.get(apiCollectionId);

        String hostName = apiCollection != null ? apiCollection.getHostName() : "";
        String url;
        if (hostName != null) {
            url = path.startsWith("/") ? hostName + path : hostName + "/" + path;
        } else {
            url = path;
        }

        return  method + " " + url;
    }

    protected static ChangesInfo getChangesInfo(int newEndpointsFrequency, int newSensitiveParamsFrequency) {
        try {
            
            ChangesInfo ret = new ChangesInfo();
            int now = Context.now();
            List<BasicDBObject> newEndpointsSmallerDuration = new InventoryAction().fetchRecentEndpoints(now - newSensitiveParamsFrequency, now);
            List<BasicDBObject> newEndpointsBiggerDuration = new InventoryAction().fetchRecentEndpoints(now - newEndpointsFrequency, now);

            Map<Integer, ApiCollection> apiCollectionMap = ApiCollectionsDao.instance.generateApiCollectionMap();

            int newParamInNewEndpoint=0;

            for (BasicDBObject singleTypeInfo: newEndpointsSmallerDuration) {
                newParamInNewEndpoint += (int) singleTypeInfo.getOrDefault("countTs", 0);
                singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
                String url = extractUrlFromBasicDbObject(singleTypeInfo, apiCollectionMap);
                ret.newEndpointsLast7Days.add(url);
            }
    
            for (BasicDBObject singleTypeInfo: newEndpointsBiggerDuration) {
                singleTypeInfo = (BasicDBObject) (singleTypeInfo.getOrDefault("_id", new BasicDBObject()));
                String url = extractUrlFromBasicDbObject(singleTypeInfo, apiCollectionMap);
                ret.newEndpointsLast31Days.add(url);
            }
    
            List<SingleTypeInfo> sensitiveParamsList = new InventoryAction().fetchSensitiveParams();
            ret.totalSensitiveParams = sensitiveParamsList.size();
            ret.recentSentiiveParams = 0;
            int delta = newSensitiveParamsFrequency;
            Map<Pair<String, String>, Set<String>> endpointToSubTypes = new HashMap<>();
            for(SingleTypeInfo sti: sensitiveParamsList) {
                ApiCollection apiCollection = apiCollectionMap.get(sti.getApiCollectionId());
                String url = sti.getUrl();
                if (apiCollection != null && apiCollection.getHostName() != null) {
                    String hostName = apiCollection.getHostName();
                    url = url.startsWith("/") ? hostName + url : hostName + "/" + url;
                }

                String encoded = Base64.getEncoder().encodeToString((sti.getUrl() + " " + sti.getMethod()).getBytes());
                String link = "/dashboard/observe/inventory/"+sti.getApiCollectionId()+"/"+encoded;
                Pair<String, String> key = new Pair<>(sti.getMethod() + " " + url, link);
                String value = sti.getSubType().getName();
                if (sti.getTimestamp() >= now - delta) {
                    ret.recentSentiiveParams ++;
                    Set<String> subTypes = endpointToSubTypes.get(key);
                    if (subTypes == null) {
                        subTypes = new HashSet<>();
                        endpointToSubTypes.put(key, subTypes);
                    }
                    subTypes.add(value);
                }
            }

            for(Pair<String, String> key: endpointToSubTypes.keySet()) {
                ret.newSensitiveParams.put(key.getFirst() + ": " + StringUtils.join(endpointToSubTypes.get(key), ","), key.getSecond());
            }

            List<SingleTypeInfo> allNewParameters = new InventoryAction().fetchAllNewParams(now - newEndpointsFrequency, now);
            int totalNewParameters=allNewParameters.size();
            ret.newParamsInExistingEndpoints = Math.max(0, totalNewParameters - newParamInNewEndpoint);
            
            return ret;
        } catch (Exception e) {
            logger.error("get new endpoints", e);
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

    public void addAktoDataTypes(BackwardCompatibility backwardCompatibility){
        if(backwardCompatibility.getAddAktoDataTypes()==0){
            List<AktoDataType> aktoDataTypes = new ArrayList<>();
            int now = Context.now();
            aktoDataTypes.add(new AktoDataType("JWT", false, Arrays.asList(SingleTypeInfo.Position.RESPONSE_PAYLOAD, SingleTypeInfo.Position.RESPONSE_HEADER),now));
            aktoDataTypes.add(new AktoDataType("EMAIL", true, Collections.emptyList(),now));
            aktoDataTypes.add(new AktoDataType("CREDIT_CARD", true, Collections.emptyList(),now));
            aktoDataTypes.add(new AktoDataType("SSN", true, Collections.emptyList(),now));
            aktoDataTypes.add(new AktoDataType("ADDRESS", true, Collections.emptyList(),now));
            aktoDataTypes.add(new AktoDataType("IP_ADDRESS", false, Arrays.asList(SingleTypeInfo.Position.RESPONSE_PAYLOAD, SingleTypeInfo.Position.RESPONSE_HEADER),now));
            aktoDataTypes.add(new AktoDataType("PHONE_NUMBER", true, Collections.emptyList(),now));
            aktoDataTypes.add(new AktoDataType("UUID", false, Collections.emptyList(),now));
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
        String https = System.getenv("AKTO_HTTPS_FLAG");
        boolean httpsFlag = Objects.equals(https, "true");
        sce.getServletContext().getSessionCookieConfig().setSecure(httpsFlag);

        System.out.println("context initialized");

        // String mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";
        String mongoURI = System.getenv("AKTO_MONGO_CONN");
        System.out.println("MONGO URI " + mongoURI);


        DaoInit.init(new ConnectionString(mongoURI));

        Context.accountId.set(1_000_000);
        SingleTypeInfoDao.instance.createIndicesIfAbsent();
        TestRolesDao.instance.createIndicesIfAbsent();

        ApiInfoDao.instance.createIndicesIfAbsent();
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

            SingleTypeInfo.init();

            Context.accountId.set(1000000);
            
            if (PIISourceDao.instance.findOne("_id", "A") == null) {
                String fileUrl = "https://raw.githubusercontent.com/akto-api-security/pii-types/master/general.json";
                PIISource piiSource = new PIISource(fileUrl, 0, 1638571050, 0, new HashMap<>(), true);
                piiSource.setId("A");
        
                PIISourceDao.instance.insertOne(piiSource);
            } 

            setUpWeeklyScheduler();
            setUpDailyScheduler();
            setUpWebhookScheduler();
            setUpPiiScheduler();

            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
            dropSampleDataIfEarlierNotDroped(accountSettings);
        } catch (Exception e) {
            logger.error("error while setting up dashboard: " + e.getMessage());
        }

        try {
            AccountSettingsDao.instance.updateVersion(AccountSettings.DASHBOARD_VERSION);
        } catch (Exception e) {
            logger.error("error while updating dashboard version: " + e.getMessage());
        }
    }

    public void updateDeploymentStatus(BackwardCompatibility backwardCompatibility) {
        String ownerEmail = System.getenv("OWNER_EMAIL");
        if(ownerEmail == null) {
            logger.info("Owner email missing, might be an existing customer, skipping sending an slack and mixpanel alert");
            return;
        }
        if(backwardCompatibility.isDeploymentStatusUpdated()){
            logger.info("Deployment status has already been updated, skipping this");
            return;
        }
        String body = "{\n    \"ownerEmail\": \""+ ownerEmail +"\",\n    \"stackStatus\": \"COMPLETED\",\n    \"cloudType\": \"AWS\"\n}";
        String headers = "{\"Content-Type\": \"application/json\"}";
        OriginalHttpRequest request = new OriginalHttpRequest(getUpdateDeploymentStatusUrl(),"","POST", body, OriginalHttpRequest.buildHeadersMap(headers),"");
        try {
            OriginalHttpResponse response = ApiExecutor.sendRequest(request,false);
            logger.info("Update deployment status reponse: {}", response.getBody());
        } catch(Exception e){
            logger.error("Failed to update deployment status, will try again on next boot up", e);
            return;
        }
        BackwardCompatibilityDao.instance.updateOne(
                Filters.eq("_id", backwardCompatibility.getId()),
                Updates.set(BackwardCompatibility.DEPLOYMENT_STATUS_UPDATED, true)
        );
    }

    private String getUpdateDeploymentStatusUrl() {
        String url = System.getenv("UPDATE_DEPLOYMENT_STATUS_URL");
        return url != null ? url: "https://stairway.akto.io/deployment/status";
    }
}
