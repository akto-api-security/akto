package com.akto.action;

import com.akto.ApiRequest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.CrawlerRunDao;
import com.akto.dao.CrawlerUrlDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.Config;
import com.akto.dto.CrawlerRun;
import com.akto.dto.CrawlerRun.CrawlerRunStatus;
import com.akto.dto.CrawlerUrl;
import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.testing.*;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.CollectionTags.TagSource;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.notifications.slack.CrawlerInitiationAlert;
import com.akto.notifications.slack.SlackAlerts;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;
import com.akto.util.RecordedLoginFlowUtil;
import com.akto.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.io.File;
import java.net.URL;
import java.util.*;

public class AktoJaxAction extends UserAction {

    private String hostname;
    private String username;
    private String password;
    private String apiKey;
    private String dashboardUrl;
    private String testRoleHaxId;

    private String outscopeUrls;

    private String crawlerData;

    private String apiCollectionId;

    // Fields for saveCrawlerUrl API
    private String url;
    private boolean accepted;
    private int timestamp;
    private String crawlId;
    private String sourceUrl;
    private String sourceXpath;
    private String buttonText;
    private int crawlingTime;
    private String selectedModuleName;

    private static final LoggerMaker loggerMaker = new LoggerMaker(AktoJaxAction.class, LogDb.DASHBOARD);

    public String initiateCrawler() {
        try {
            if(crawlingTime < 600 || crawlingTime > 345600) {
                addActionError("Invalid crawling time");
                return ERROR.toUpperCase();
            }

            loggerMaker.infoAndAddToDb("Initializing Crawler");

            URL parsedUrl = new URL(hostname);
            String host = parsedUrl.getHost();

            ApiCollectionsAction collectionsAction = new ApiCollectionsAction();
            collectionsAction.setCollectionName(host);
            String collectionStatus = collectionsAction.createCollection();
            int collectionId = 0;
            ApiCollection apiCollection = null;
            if(collectionStatus.equalsIgnoreCase(Action.SUCCESS)) {
                List<ApiCollection> apiCollections = collectionsAction.getApiCollections();
                if (apiCollections != null && !apiCollections.isEmpty()) {
                    apiCollection = apiCollections.get(0);
                    collectionId = apiCollection.getId();
                } else {
                    apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.NAME, host));
                    if (apiCollection != null) {
                        collectionId = apiCollection.getId();
                    }
                }
            } else {
                apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.NAME, host));
                if (apiCollection != null) {
                    collectionId = apiCollection.getId();
                }
            }

            loggerMaker.infoAndAddToDb("Crawler collection id: " + collectionId);
            if (apiCollection != null && !apiCollection.isDastCollection()) {
                ApiCollectionsDao.instance.getMCollection().updateOne(
                        Filters.eq(Constants.ID, collectionId),
                        Updates.set(ApiCollection.TAGS_STRING, Collections.singletonList(
                                new CollectionTags(Context.now(), Constants.AKTO_DAST_TAG, "DAST", TagSource.USER))),
                            new UpdateOptions().upsert(false));
                loggerMaker.infoAndAddToDb("Updated Collection with tag: " + collectionId);
            }

            String crawlId = UUID.randomUUID().toString();

            Object cookies = null;
            if(!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
            } else if(testRoleHaxId != null && !testRoleHaxId.isEmpty()) {
                TestRoles testRole = TestRolesDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(testRoleHaxId)));
                AuthMechanism authMechanismForRole = testRole.findDefaultAuthMechanism();
                if (testRole != null && !testRole.getAuthWithCondList().isEmpty() && testRole.getAuthWithCondList().get(0).getRecordedLoginFlowInput() != null) {
                    try {
                        RecordedLoginFlowInput recordedLoginFlowInput = authMechanismForRole.getRecordedLoginFlowInput();
                        String payload = recordedLoginFlowInput.getContent().toString();
                        File tmpOutputFile;
                        File tmpErrorFile;
                        tmpOutputFile = File.createTempFile("output", ".json");
                        tmpErrorFile = File.createTempFile("recordedFlowOutput", ".txt");
                        RecordedLoginFlowUtil.triggerFlow(recordedLoginFlowInput.getTokenFetchCommand(), payload, tmpOutputFile.getPath(), tmpErrorFile.getPath(), getSUser().getId());

                        String token = RecordedLoginFlowUtil.fetchToken(tmpOutputFile.getPath(), tmpErrorFile.getPath());
                        BasicDBObject parseToken = BasicDBObject.parse(token);
                        if (parseToken != null) {
                            loggerMaker.infoAndAddToDb("Got the cookies from test role for crawler");
                            BasicDBList allCookies = (BasicDBList) parseToken.get("all_cookies");
                            cookies = allCookies;
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error while fetching cookies/token from test role using jsonRecording. Error: " + e.getMessage());
                        return ERROR.toUpperCase();
                    }
                } else {
                    try {
                        TestExecutor testExecutor = new TestExecutor();
                        LoginFlowParams loginFlowParams = new LoginFlowParams(getSUser().getId(), true, "x1");
                        LoginFlowResponse loginFlowResponse = testExecutor.executeLoginFlow(authMechanismForRole, loginFlowParams, testRole.getName());

                        if (!loginFlowResponse.getSuccess()) {
                            addActionError("Error while fetching accessToken.");
                            return ERROR.toUpperCase();
                        }

                        List<AuthParam> authParamsToUse = authMechanismForRole.getAuthParamsFromAuthMechanism();
                        AuthParam authParam = authParamsToUse.get(0);

                        cookies = "Bearer " + authParam.getValue();
                    } catch (Exception ex) {
                        addActionError(ex.getMessage());
                        loggerMaker.errorAndAddToDb("Error while fetching cookies/token from test role using loginStepBuilder. Error: " + ex.getMessage());
                        return ERROR.toUpperCase();
                    }
                }
            }

            ModuleInfo module = ModuleInfoDao.instance.findOne(
                Filters.and(
                    Filters.eq(ModuleInfo.NAME, selectedModuleName),
                    Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.DAST.name()),
                    Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, Context.now() - 300)
                )
            );

            if (module == null) {
                addActionError("Selected DAST module is not available. Please select another module.");
                return ERROR.toUpperCase();
            }

            int currentTimestamp = Context.now();
            CrawlerRun crawlerRun = new CrawlerRun(
                getSUser().getLogin(),
                currentTimestamp,
                0,
                crawlId,
                hostname,
                outscopeUrls
            );

            crawlerRun.setStatus(CrawlerRunStatus.PENDING);
            crawlerRun.setModuleName(selectedModuleName);
            crawlerRun.setUsername(username);
            crawlerRun.setPassword(password);
            crawlerRun.setApiKey(apiKey);
            crawlerRun.setDashboardUrl(dashboardUrl);
            crawlerRun.setCollectionId(collectionId);
            crawlerRun.setAccountId(Context.accountId.get());
            crawlerRun.setCookies(cookies);
            crawlerRun.setCrawlingTime(crawlingTime);

            CrawlerRunDao.instance.insertOne(crawlerRun);

            // Send Slack alert for crawler initiation
            try {
                Config config = ConfigsDao.instance.findOne(
                    Filters.eq("configType", Config.ConfigType.SLACK_ALERT_INTERNAL.name())
                );

                if (config != null) {
                    Config.SlackAlertInternalConfig slackConfig = (Config.SlackAlertInternalConfig) config;
                    String slackWebhookUrl = slackConfig.getSlackWebhookUrl();

                    if (slackWebhookUrl != null && !slackWebhookUrl.isEmpty()) {
                        String collectionName = null;
                        if (collectionId != 0 && apiCollection != null) {
                            collectionName = apiCollection.getName();
                        }

                        SlackAlerts crawlerAlert = new CrawlerInitiationAlert(
                            getSUser().getLogin(),
                            hostname,
                            selectedModuleName,
                            collectionId,
                            collectionName,
                            crawlingTime,
                            outscopeUrls,
                            crawlId,
                            Context.now(),
                            username,
                            (username != null && !username.isEmpty()) ||
                            (password != null && !password.isEmpty()) ||
                            (cookies != null)
                        );

                        // Send alert directly with webhook URL (async)
                        final String webhookUrl = slackWebhookUrl;
                        final String payload = crawlerAlert.toJson();
                        new Thread(() -> {
                            try {
                                com.slack.api.Slack.getInstance().send(webhookUrl, payload);
                            } catch (Exception ex) {
                                loggerMaker.errorAndAddToDb("Failed to send Slack alert: " + ex.getMessage());
                            }
                        }).start();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                loggerMaker.errorAndAddToDb("Failed to send Slack alert for crawler initiation: " + e.getMessage());
            }

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.error("Error while initiating the Akto crawler. Error: " + e.getMessage());
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }
    }

    public String uploadCrawlerData() {
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topic == null) topic = "akto.api.logs";

        loggerMaker.infoAndAddToDb("uploadCrawlerData() - Crawler topic: " + topic);

        // fetch collection id
        ApiCollection apiCollection = null;
        MongoCursor<ApiCollection> cursor = ApiCollectionsDao.instance.getMCollection().find(Filters.eq("_id", Integer.valueOf(apiCollectionId))).cursor();
        while (cursor.hasNext()) {
            apiCollection = cursor.next();
            break;
        }
        if(apiCollection == null) {
            addActionError("API collection not found");
            return Action.ERROR.toUpperCase();
        }

        try {
            loggerMaker.infoAndAddToDb("uploadCrawlerData() - Pushing crawler data to kafka");
            Utils.pushDataToKafka(apiCollection.getId(), topic, Arrays.asList(crawlerData), new ArrayList<>(), true, true, true);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Exception while inserting crawler data");
            e.printStackTrace();
        }

        return Action.SUCCESS.toUpperCase();
    }

    public String saveCrawlerUrl() {
        try {
            loggerMaker.infoAndAddToDb("Saving crawler URL");

            if (StringUtils.isEmpty(url) || StringUtils.isEmpty(crawlId)) {
                addActionError("URL and crawl ID are required");
                return Action.ERROR.toUpperCase();
            }

            CrawlerUrl crawlerUrl = new CrawlerUrl(url, accepted, timestamp, crawlId, sourceUrl, sourceXpath, buttonText);
            CrawlerUrlDao.instance.insertOne(crawlerUrl);

            loggerMaker.infoAndAddToDb("Crawler URL saved successfully");
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error while saving crawler URL: " + e.getMessage());
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }
    }


    private List<Map<String, Object>> availableModules;

    public String fetchAvailableDastModules() {
        try {
            int currentTime = Context.now();
            int cutoffTime = currentTime - 300;

            List<ModuleInfo> activeModules = ModuleInfoDao.instance.findAll(
                    Filters.and(
                            Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.DAST.name()),
                            Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, cutoffTime)
                    )
            );

            availableModules = new ArrayList<>();

            if (activeModules == null || activeModules.isEmpty()) {
                addActionError("No DAST modules available");
                return ERROR.toUpperCase();
            } else {
                for (ModuleInfo module : activeModules) {
                    Map<String, Object> moduleMap = new HashMap<>();
                    moduleMap.put("name", module.getName());
                    moduleMap.put("displayName", module.getName());
                    moduleMap.put("lastHeartbeat", module.getLastHeartbeatReceived());
                    moduleMap.put("isDefault", false);
                    availableModules.add(moduleMap);
                }
            }

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching DAST modules: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCrawlerData() {
        return crawlerData;
    }

    public void setCrawlerData(String crawlerData) {
        this.crawlerData = crawlerData;
    }

    public String getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(String apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDashboardUrl() {
        return dashboardUrl;
    }

    public void setDashboardUrl(String dashboardUrl) {
        this.dashboardUrl = dashboardUrl;
    }

    public String getTestRoleHaxId() {
        return testRoleHaxId;
    }

    public void setTestRoleHaxId(String testRoleHaxId) {
        this.testRoleHaxId = testRoleHaxId;
    }

    public String getOutscopeUrls() {
        return outscopeUrls;
    }

    public void setOutscopeUrls(String outscopeUrls) {
        this.outscopeUrls = outscopeUrls;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getCrawlId() {
        return crawlId;
    }

    public void setCrawlId(String crawlId) {
        this.crawlId = crawlId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getSourceXpath() {
        return sourceXpath;
    }

    public void setSourceXpath(String sourceXpath) {
        this.sourceXpath = sourceXpath;
    }

    public String getButtonText() {
        return buttonText;
    }

    public void setButtonText(String buttonText) {
        this.buttonText = buttonText;
    }

    public int getCrawlingTime() {
        return crawlingTime;
    }

    public void setCrawlingTime(int crawlingTime) {
        this.crawlingTime = crawlingTime;
    }

    public String getSelectedModuleName() {
        return selectedModuleName;
    }

    public void setSelectedModuleName(String selectedModuleName) {
        this.selectedModuleName = selectedModuleName;
    }

    public List<Map<String, Object>> getAvailableModules() {
        return availableModules;
    }

    public void setAvailableModules(List<Map<String, Object>> availableModules) {
        this.availableModules = availableModules;
    }
}
