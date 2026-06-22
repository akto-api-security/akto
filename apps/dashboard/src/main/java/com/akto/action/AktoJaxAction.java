package com.akto.action;

import com.akto.ApiRequest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.CrawlerRunDao;
import com.akto.dao.CrawlerUrlDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dao.testing.*;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.test_editor.YamlTemplate;
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
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.json.JSONObject;
import com.akto.websocket.CrawlerFrameCache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.net.URL;
import java.util.*;

public class AktoJaxAction extends UserAction {

    private String hostname;
    private String username;
    private String password;
    private String apiKey;
    private String dashboardUrl;
    @Getter
    @Setter
    private String testRoleHexId;

    private String outscopeUrls;
    @Getter
    @Setter
    private String urlTemplatePatterns;
    @Getter
    @Setter
    private String applicationPages;

    private String crawlerData;
    @Getter @Setter
    private List<String> crawlerDataList;

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
    private Map<String, String> customHeaders;

    private boolean runTestAfterCrawling;
    private String selectedMiniTestingService;
    private String collectionName;
    @Getter @Setter
    private boolean enableAiJsDiscovery;
    @Getter @Setter
    private String crawlUrlsBatch;
    @Getter @Setter
    private String userPrompt;
    @Getter @Setter
    private String crawlMode;
    @Getter @Setter
    private Boolean respectRobots;
    @Getter @Setter
    private Boolean aiPrioritize;
    @Getter @Setter
    private Boolean screencastEnabled;
    @Getter @Setter
    private Boolean strictHostScope;

    // Fields for live browser view
    private String frameData;
    private String currentUrl;
    @Getter @Setter
    private String latestFrameJson;

    // Fields for the navigation graph (Mermaid) shared by the crawler at the end.
    @Getter @Setter
    private String graph;
    @Getter @Setter
    private String navigationGraph;

    private static final LoggerMaker loggerMaker = new LoggerMaker(AktoJaxAction.class, LogDb.DASHBOARD);

    public String initiateCrawler() {
        try {
            if(crawlingTime < 600 || crawlingTime > 345600) { // crawlerTime cannot be < 10 minutes OR crawlerTime cannot be greater than 4 days
                addActionError("Invalid crawling time");
                return ERROR.toUpperCase();
            }

            loggerMaker.infoAndAddToDb("Initializing Crawler");

            URL parsedUrl = new URL(hostname);
            String host = parsedUrl.getHost();
            loggerMaker.infoAndAddToDb("DAST initiateCrawler: hostname=" + hostname + " parsed host=" + host);

            // Use custom collection name if provided, otherwise use hostname
            String finalCollectionName = (collectionName != null && !collectionName.trim().isEmpty())
                ? collectionName.trim()
                : host;
            loggerMaker.infoAndAddToDb("DAST initiateCrawler: finalCollectionName=" + finalCollectionName + " (customName=" + collectionName + ")");

            ApiCollectionsAction collectionsAction = new ApiCollectionsAction();
            collectionsAction.setCollectionName(finalCollectionName);
            String collectionStatus = collectionsAction.createCollection();
            loggerMaker.infoAndAddToDb("DAST initiateCrawler: createCollection status=" + collectionStatus + " actionErrors=" + collectionsAction.getActionErrors());
            int collectionId = 0;
            ApiCollection apiCollection = null;
            if(collectionStatus.equalsIgnoreCase(Action.SUCCESS)) {
                List<ApiCollection> apiCollections = collectionsAction.getApiCollections();
                if (apiCollections != null && !apiCollections.isEmpty()) {
                    apiCollection = apiCollections.get(0);
                    collectionId = apiCollection.getId();
                    loggerMaker.infoAndAddToDb("DAST initiateCrawler: new collection created id=" + collectionId + " name=" + apiCollection.getName());
                } else {
                    loggerMaker.infoAndAddToDb("DAST initiateCrawler: createCollection SUCCESS but returned empty list, falling back to lookup");
                    apiCollection = findCollectionByNameOrHost(finalCollectionName);
                    if (apiCollection != null) {
                        collectionId = apiCollection.getId();
                    }
                }
            } else {
                loggerMaker.infoAndAddToDb("DAST initiateCrawler: createCollection failed, falling back to lookup for name=" + finalCollectionName);
                apiCollection = findCollectionByNameOrHost(finalCollectionName);
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

            String cookies = null;
            // When a test role is configured but authentication fails, we no longer abort the crawl.
            // Instead we record the failure (flag + message), surface it on the UI and Slack, and let the
            // crawl run unauthenticated so the user still gets partial coverage instead of a silent error.
            boolean testRoleFailed = false;
            String testRoleError = null;
            if(!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
            } else if(testRoleHexId != null && !testRoleHexId.isEmpty()) {
                try {
                    TestRoles testRole = TestRolesDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(testRoleHexId)));
                    if (testRole == null) {
                        throw new Exception("Test role not found for id: " + testRoleHexId);
                    }
                    AuthMechanism authMechanismForRole = testRole.findDefaultAuthMechanism();
                    if (authMechanismForRole == null) {
                        throw new Exception("No default auth mechanism configured for test role: " + testRole.getName());
                    }

                    if (!testRole.getAuthWithCondList().isEmpty() && testRole.getAuthWithCondList().get(0).getRecordedLoginFlowInput() != null) {
                        RecordedLoginFlowInput recordedLoginFlowInput = authMechanismForRole.getRecordedLoginFlowInput();
                        String payload = recordedLoginFlowInput.getContent().toString();
                        File tmpOutputFile = File.createTempFile("output", ".json");
                        File tmpErrorFile = File.createTempFile("recordedFlowOutput", ".txt");
                        RecordedLoginFlowUtil.triggerFlow(recordedLoginFlowInput.getTokenFetchCommand(), payload, tmpOutputFile.getPath(), tmpErrorFile.getPath(), getSUser().getId());

                        String token = RecordedLoginFlowUtil.fetchToken(tmpOutputFile.getPath(), tmpErrorFile.getPath());
                        if (token == null) {
                            throw new Exception("Could not fetch token from recorded login flow (timed out or returned an error).");
                        }
                        BasicDBObject parseToken = BasicDBObject.parse(token);
                        BasicDBList allCookies = parseToken != null ? (BasicDBList) parseToken.get("all_cookies") : null;
                        if (allCookies == null) {
                            throw new Exception("Recorded login flow returned no cookies.");
                        }
                        loggerMaker.infoAndAddToDb("Got the cookies from test role for crawler");
                        cookies = allCookies.toString();
                    } else {
                        TestExecutor testExecutor = new TestExecutor();
                        LoginFlowParams loginFlowParams = new LoginFlowParams(getSUser().getId(), true, "x1");
                        LoginFlowResponse loginFlowResponse = testExecutor.executeLoginFlow(authMechanismForRole, loginFlowParams, testRole.getName());

                        if (loginFlowResponse == null || loginFlowResponse.getSuccess() == null || !loginFlowResponse.getSuccess()) {
                            String reason = (loginFlowResponse != null && loginFlowResponse.getError() != null && !loginFlowResponse.getError().isEmpty())
                                ? loginFlowResponse.getError() : "login flow did not succeed.";
                            throw new Exception("Could not fetch access token: " + reason);
                        }

                        List<AuthParam> authParamsToUse = authMechanismForRole.getAuthParamsFromAuthMechanism();
                        if (authParamsToUse == null || authParamsToUse.isEmpty()) {
                            throw new Exception("No auth params found in test role auth mechanism.");
                        }
                        AuthParam authParam = authParamsToUse.get(0);

                        if (authParam.getValue() != null && !authParam.getValue().isEmpty() && !authParam.getValue().toLowerCase().startsWith("bearer")) {
                            cookies = "Bearer " + authParam.getValue();
                        } else {
                            cookies = authParam.getValue();
                        }
                    }
                } catch (Exception e) {
                    testRoleFailed = true;
                    testRoleError = (e.getMessage() != null && !e.getMessage().isEmpty()) ? e.getMessage() : e.getClass().getSimpleName();
                    cookies = null;
                    loggerMaker.errorAndAddToDb(e, "Test role authentication failed for crawler; proceeding unauthenticated. crawlId=" + crawlId + " error: " + testRoleError);
                    this.errorMessage = "Test role authentication failed; the scan is running without authentication. Reason: " + testRoleError;
                }
            }

            // Check if DAST module is available
            boolean useModuleBasedDast = false;
            if (selectedModuleName != null && !selectedModuleName.isEmpty()) {
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

                useModuleBasedDast = true;
            }

            int currentTimestamp = Context.now();

            if (useModuleBasedDast) {
                CrawlerRun crawlerRun = new CrawlerRun(
                        getSUser().getLogin(),
                        currentTimestamp,
                        0,
                        crawlId,
                        hostname,
                        outscopeUrls,
                        runTestAfterCrawling
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
                crawlerRun.setCustomHeaders(customHeaders);
                crawlerRun.setUrlTemplatePatterns(urlTemplatePatterns);
                crawlerRun.setApplicationPages(applicationPages);
                crawlerRun.setEnableAiJsDiscovery(enableAiJsDiscovery);
                crawlerRun.setUserPrompt(userPrompt);
                crawlerRun.setCrawlMode(crawlMode);
                crawlerRun.setTestRoleFailed(testRoleFailed);
                crawlerRun.setTestRoleError(testRoleError);
                crawlerRun.setRespectRobots(respectRobots);
                crawlerRun.setAiPrioritize(aiPrioritize);
                crawlerRun.setScreencastEnabled(screencastEnabled);
                crawlerRun.setStrictHostScope(strictHostScope);

                if(runTestAfterCrawling && selectedMiniTestingService != null && !selectedMiniTestingService.isEmpty()) {
                    crawlerRun.setSelectedMiniTestingService(selectedMiniTestingService);
                }
                if (testRoleHexId != null && !testRoleHexId.isEmpty()) {
                    crawlerRun.setTestRoleHexId(testRoleHexId);
                }

                CrawlerRunDao.instance.insertOne(crawlerRun);
            } else {
                // Fallback to internal DAST API
                initiateInternalCrawl(crawlId, hostname, username, password, apiKey,
                    dashboardUrl, collectionId, cookies, crawlingTime, outscopeUrls, runTestAfterCrawling,
                    urlTemplatePatterns, applicationPages, testRoleHexId, enableAiJsDiscovery, userPrompt, crawlMode,
                    testRoleFailed, testRoleError,
                    respectRobots, aiPrioritize, screencastEnabled, strictHostScope);
            }

            // Send Slack alert for crawler initiation
            try {
                Config config = ConfigsDao.instance.findOne(
                    Filters.eq("configType", Config.ConfigType.SLACK_ALERT_INTERNAL.name())
                );

                if (config != null) {
                    Config.SlackAlertInternalConfig slackConfig = (Config.SlackAlertInternalConfig) config;
                    String slackWebhookUrl = slackConfig.getDastSlackWebhookUrl();

                    if (slackWebhookUrl != null && !slackWebhookUrl.isEmpty()) {
                        String collectionName = null;
                        if (collectionId != 0 && apiCollection != null) {
                            collectionName = apiCollection.getName();
                        }

                        String moduleNameForAlert = useModuleBasedDast ? selectedModuleName : INTERNAL_DAST_MODULE_NAME;

                        SlackAlerts crawlerAlert = new CrawlerInitiationAlert(
                            getSUser().getLogin(),
                            hostname,
                            moduleNameForAlert,
                            collectionId,
                            collectionName,
                            crawlingTime,
                            outscopeUrls,
                            crawlId,
                            Context.now(),
                            username,
                            (username != null && !username.isEmpty()) ||
                            (password != null && !password.isEmpty()) ||
                            (cookies != null),
                            testRoleError
                        );

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
            loggerMaker.infoAndAddToDb("DAST initiateCrawler: exception " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            addActionError("Error while initiating the Akto crawler. Error: " + e.getMessage());
            return Action.ERROR.toUpperCase();
        }
    }

    public String stopCrawler() {
        try {
            if (StringUtils.isEmpty(crawlId)) {
                addActionError("Crawl ID is required");
                return ERROR.toUpperCase();
            }

            // Look up the crawl to determine how it's being run.
            CrawlerRun crawlerRun = CrawlerRunDao.instance.findOne(
                    Filters.eq(CrawlerRun.CRAWL_ID, crawlId));
            if (crawlerRun == null) {
                addActionError("Crawl not found for the given crawlId");
                return ERROR.toUpperCase();
            }

            // Internal (web-server / push) DAST runs in the bundled AktoJax service
            // and is stopped via its HTTP API. A module DAST runs as a remote poller
            // and is stopped by setting STOP_REQUESTED in the DB — the module polls
            // checkCrawlStatus, sees it, stops the crawl, and reports STOPPED.
            String moduleName = crawlerRun.getModuleName();
            boolean isInternal = StringUtils.isEmpty(moduleName)
                    || INTERNAL_DAST_MODULE_NAME.equalsIgnoreCase(moduleName);

            // Always record the stop intent in the DB. For a polling module this IS
            // the stop signal; for the internal DAST it also persists intent in case
            // the service is slow to report back.
            CrawlerRunDao.instance.updateOne(
                    Filters.eq(CrawlerRun.CRAWL_ID, crawlId),
                    Updates.set(CrawlerRun.STATUS, CrawlerRunStatus.STOP_REQUESTED.name())
            );

            if (isInternal) {
                String serviceUrl = System.getenv("AKTOJAX_SERVICE_URL");
                if (StringUtils.isEmpty(serviceUrl)) {
                    loggerMaker.errorAndAddToDb("AKTOJAX_SERVICE_URL not set; cannot reach internal DAST service to stop crawlId=" + crawlId + " (marked STOP_REQUESTED only)");
                    addActionError("Internal DAST service URL is not configured.");
                    return Action.ERROR.toUpperCase();
                }
                String url = serviceUrl + "/stopCrawler";
                loggerMaker.infoAndAddToDb("Stopping internal DAST crawler: " + url);

                JSONObject requestBody = new JSONObject();
                requestBody.put("crawlId", crawlId);
                JsonNode node = ApiRequest.postRequest(new HashMap<>(), url, requestBody.toString());
                String status = node != null && node.get("status") != null ? node.get("status").textValue() : null;

                if (status == null || !status.equalsIgnoreCase("success")) {
                    loggerMaker.errorAndAddToDb("Failed to stop internal DAST crawler. Status: " + status);
                    addActionError("Failed to stop crawler. Please try again later.");
                    return Action.ERROR.toUpperCase();
                }
                loggerMaker.infoAndAddToDb("Internal DAST crawler stop requested successfully for crawlId=" + crawlId);
            } else {
                // Module/poller DAST — STOP_REQUESTED is enough; the module will stop
                // on its next status check (via cyborg checkCrawlStatus).
                loggerMaker.infoAndAddToDb("Stop requested for module DAST '" + moduleName
                        + "' (crawlId=" + crawlId + "); the module will stop on its next status check.");
            }

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error stopping crawler: " + e.getMessage());
            addActionError("Error stopping crawler. Please try again later.");
            return Action.ERROR.toUpperCase();
        }
    }

    // Module name used for the bundled web-server DAST (set in initiateInternalCrawl).
    private static final String INTERNAL_DAST_MODULE_NAME = "Internal DAST (Akto)";

    private ApiCollection findCollectionByNameOrHost(String name) {
        ApiCollection collection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.NAME, name));
        loggerMaker.infoAndAddToDb("DAST findCollectionByNameOrHost: name lookup for '" + name + "' returned " +
            (collection == null ? "null" : "id=" + collection.getId() + " hostName=" + collection.getHostName()));
        if (collection == null) {
            collection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.HOST_NAME, name));
            loggerMaker.infoAndAddToDb("DAST findCollectionByNameOrHost: hostName lookup for '" + name + "' returned " +
                (collection == null ? "null" : "id=" + collection.getId() + " name=" + collection.getName()));
        }
        return collection;
    }

    private void initiateInternalCrawl(String crawlId, String hostname, String username,
                                     String password, String apiKey, String dashboardUrl,
                                     int collectionId, String cookies, int crawlingTime,
                                     String outscopeUrls, boolean runTestAfterCrawling,
                                     String urlTemplatePatterns, String applicationPages,
                                     String testRoleHexId, boolean enableAiJsDiscovery,
                                     String userPrompt, String crawlMode,
                                     boolean testRoleFailed, String testRoleError,
                                     Boolean respectRobots, Boolean aiPrioritize,
                                     Boolean screencastEnabled, Boolean strictHostScope) throws Exception {
        String url = System.getenv("AKTOJAX_SERVICE_URL") + "/triggerCrawler";
        loggerMaker.infoAndAddToDb("Using internal DAST crawler service: " + url);

        JSONObject requestBody = new JSONObject();
        requestBody.put("hostname", hostname);
        requestBody.put("apiKey", apiKey);
        requestBody.put("dashboardUrl", dashboardUrl);
        requestBody.put("collectionId", collectionId);
        requestBody.put("accountId", Context.accountId.get());
        requestBody.put("outscopeUrls", outscopeUrls);
        requestBody.put("userPrompt", userPrompt);
        requestBody.put("crawlId", crawlId);
        requestBody.put("crawlingTime", crawlingTime);

        if (!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
            requestBody.put("username", username);
            requestBody.put("password", password);
        }

        if (cookies != null) {
            requestBody.put("cookies", cookies);
        }

        if (customHeaders != null && !customHeaders.isEmpty()) {
            requestBody.put("customHeaders", customHeaders);
        }

        if (!StringUtils.isEmpty(urlTemplatePatterns)) {
            requestBody.put("urlTemplatePatterns", urlTemplatePatterns);
        }

        if (!StringUtils.isEmpty(applicationPages)) {
            requestBody.put("applicationPages", applicationPages);
        }

        requestBody.put("enableAiJsDiscovery", enableAiJsDiscovery);
        if (!StringUtils.isEmpty(crawlMode)) {
            requestBody.put("crawlMode", crawlMode);
        }
        if (respectRobots != null) {
            requestBody.put("respectRobots", respectRobots);
        }
        if (aiPrioritize != null) {
            requestBody.put("aiPrioritize", aiPrioritize);
        }
        if (screencastEnabled != null) {
            requestBody.put("screencastEnabled", screencastEnabled);
        }
        if (strictHostScope != null) {
            requestBody.put("strictHostScope", strictHostScope);
        }

        String reqData = requestBody.toString();
        loggerMaker.infoAndAddToDb("Internal DAST crawler request data: " + reqData);

        JsonNode node = ApiRequest.postRequest(new HashMap<>(), url, reqData);
        String status = (node != null && node.get("status") != null) ? node.get("status").textValue() : null;

        // A non-success (or empty/invalid) response means the crawl never started. Throw so the caller
        // reports a clear error to the user instead of silently returning SUCCESS with no CrawlerRun created.
        if (status == null || !status.equalsIgnoreCase("success")) {
            loggerMaker.errorAndAddToDb("Internal DAST crawler did not start for crawlId=" + crawlId
                    + ". Status: " + status + ", response: " + (node != null ? node.toString() : "null"));
            throw new Exception("Internal DAST crawler service failed to start the crawl"
                    + (status != null ? " (status: " + status + ")" : " (no/invalid response from service)") + ".");
        }

        {
            int currentTimestamp = Context.now();
            CrawlerRun crawlerRun = new CrawlerRun(
                    getSUser().getLogin(),
                    currentTimestamp,
                    0,
                    crawlId,
                    hostname,
                    outscopeUrls,
                    runTestAfterCrawling
            );

            crawlerRun.setStatus(CrawlerRunStatus.PENDING);
            crawlerRun.setModuleName(INTERNAL_DAST_MODULE_NAME);
            crawlerRun.setUsername(username);
            crawlerRun.setPassword(password);
            crawlerRun.setApiKey(apiKey);
            crawlerRun.setDashboardUrl(dashboardUrl);
            crawlerRun.setCollectionId(collectionId);
            crawlerRun.setAccountId(Context.accountId.get());
            crawlerRun.setCookies(cookies);
            crawlerRun.setCrawlingTime(crawlingTime);
            crawlerRun.setCustomHeaders(customHeaders);
            crawlerRun.setUrlTemplatePatterns(urlTemplatePatterns);
            crawlerRun.setApplicationPages(applicationPages);
            crawlerRun.setEnableAiJsDiscovery(enableAiJsDiscovery);
            crawlerRun.setUserPrompt(userPrompt);
            crawlerRun.setCrawlMode(crawlMode);
            crawlerRun.setTestRoleFailed(testRoleFailed);
            crawlerRun.setTestRoleError(testRoleError);
            crawlerRun.setRespectRobots(respectRobots);
            crawlerRun.setAiPrioritize(aiPrioritize);
            crawlerRun.setScreencastEnabled(screencastEnabled);
            crawlerRun.setStrictHostScope(strictHostScope);

            if(runTestAfterCrawling && selectedMiniTestingService != null && !selectedMiniTestingService.isEmpty()) {
                crawlerRun.setSelectedMiniTestingService(selectedMiniTestingService);
            }
            if (testRoleHexId != null && !testRoleHexId.isEmpty()) {
                crawlerRun.setTestRoleHexId(testRoleHexId);
            }

            CrawlerRunDao.instance.insertOne(crawlerRun);
        }

        loggerMaker.infoAndAddToDb("Internal DAST crawler status: " + status);
    }

    private TestingRun triggerTestsAfterCrawling(CrawlerRun crawlerRun) {
        if (crawlerRun == null || !crawlerRun.isRunTestAfterCrawling()) {
            return null;
        }

        try {
            Integer collectionId = crawlerRun.getCollectionId();
            if (collectionId == null || collectionId == 0) {
                loggerMaker.errorAndAddToDb("Cannot trigger tests: No collection ID found in crawler run");
                return null;
            }

            // Fetch auth mechanism
            AuthMechanism authMechanism = TestRolesDao.instance.fetchAttackerToken(null);
            if (authMechanism == null) {
                loggerMaker.errorAndAddToDb("Cannot trigger tests: No authentication mechanism found");
                return null;
            }

            Bson filter = Filters.or(
                Filters.exists(YamlTemplate.INACTIVE, false),
                Filters.eq(YamlTemplate.INACTIVE, false)
            );
            List<YamlTemplate> yamlTemplates = YamlTemplateDao.instance.findAll(
                filter,
                Projections.include("_id")
            );

            if (yamlTemplates == null || yamlTemplates.isEmpty()) {
                loggerMaker.errorAndAddToDb("Cannot trigger tests: No test templates found");
                return null;
            }

            List<String> selectedTests = new ArrayList<>();
            for (YamlTemplate template : yamlTemplates) {
                selectedTests.add(template.getId());
            }

            // Create testing run config with all tests
            int testConfigId = UUID.randomUUID().hashCode() & 0xfffffff;
            TestingRunConfig testingRunConfig = new TestingRunConfig(
                testConfigId,
                null,                              // testingEndpoints (null for collection-wise)
                selectedTests,                     // selectedTests (all available tests)
                null,             // authMechanismId
                null,                              // overriddenTestAppUrl
                ""
            );
            testingRunConfig.setTestSuiteIds(new ArrayList<>());
            TestingRunConfigDao.instance.insertOne(testingRunConfig);

            // Create testing endpoints for the collection
            CollectionWiseTestingEndpoints testingEndpoints = new CollectionWiseTestingEndpoints(collectionId);

            // Determine mini testing service name (null if not specified)
            String miniTestingServiceName = null;
            if (crawlerRun.getSelectedMiniTestingService() != null
                && !crawlerRun.getSelectedMiniTestingService().isEmpty()) {
                miniTestingServiceName = crawlerRun.getSelectedMiniTestingService();
            }

            // Create testing run with the config ID
            String testName = "Auto-test after crawl: " + crawlerRun.getHostname();
            // Get dashboard context from Context.contextSource
            CONTEXT_SOURCE dashboardContext = Context.contextSource.get();
            
            TestingRun testingRun = new TestingRun(
                Context.now(),                  // scheduleTimestamp
                crawlerRun.getStartedBy(),      // userEmail
                testingEndpoints,               // testingEndpoints
                testConfigId,                   // testIdConfig (from TestingRunConfig)
                TestingRun.State.SCHEDULED,     // state
                0,                              // periodInSeconds (0 = one-time)
                testName,                       // name
                -1,                             // testRunTime (-1 = default)
                -1,                             // maxConcurrentRequests (-1 = default)
                false,                          // sendSlackAlert
                false,                          // sendMsTeamsAlert
                miniTestingServiceName,         // miniTestingServiceName (can be null)
                0,                              // selectedSlackChannelId
                dashboardContext               // dashboardContext (can be null if unknown)
            );

            testingRun.setTriggeredBy("DAST_CRAWLER_AUTO_TEST");

            // Insert testing run
            TestingRunDao.instance.insertOne(testingRun);

            loggerMaker.infoAndAddToDb(
                "Successfully triggered tests after crawling. " +
                "CrawlId: " + crawlerRun.getCrawlId() +
                ", CollectionId: " + collectionId +
                ", TestingRunId: " + testingRun.getId().toHexString() +
                ", TestCount: " + selectedTests.size() +
                (miniTestingServiceName != null ? ", MiniTestingService: " + miniTestingServiceName : "")
            );

            return testingRun;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error triggering tests after crawling for crawlId: " + crawlerRun.getCrawlId());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private ApiCollection findApiCollection() {
        try (MongoCursor<ApiCollection> cursor = ApiCollectionsDao.instance.getMCollection()
                .find(Filters.eq("_id", Integer.valueOf(apiCollectionId))).cursor()) {
            return cursor.hasNext() ? cursor.next() : null;
        }
    }

    private String pushCrawlerDataToKafka(List<String> records) {
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topic == null) topic = "akto.api.logs";

        ApiCollection apiCollection = findApiCollection();
        if (apiCollection == null) {
            addActionError("API collection not found");
            return Action.ERROR.toUpperCase();
        }
        try {
            Utils.pushDataToKafka(apiCollection.getId(), topic, records, new ArrayList<>(), true, true, true);
            loggerMaker.infoAndAddToDb("pushCrawlerDataToKafka: pushed " + records.size() + " record(s)");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Exception pushing crawler data to Kafka");
        }
        return Action.SUCCESS.toUpperCase();
    }

    private String insertCrawlerUrls(List<CrawlerUrl> urls) {
        try {
            for (CrawlerUrl u : urls) CrawlerUrlDao.instance.insertOne(u);
            loggerMaker.infoAndAddToDb("insertCrawlerUrls: saved " + urls.size() + " URL(s)");
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Exception saving crawler URL(s)");
            return Action.ERROR.toUpperCase();
        }
    }

    // -------------------------------------------------------------------------
    // Single-item endpoints (used by older crawler versions)
    // -------------------------------------------------------------------------

    public String uploadCrawlerData() {
        loggerMaker.infoAndAddToDb("uploadCrawlerData() called");
        return pushCrawlerDataToKafka(Arrays.asList(crawlerData));
    }

    public String saveCrawlerUrl() {
        if (StringUtils.isEmpty(url) || StringUtils.isEmpty(crawlId)) {
            addActionError("URL and crawl ID are required");
            return Action.ERROR.toUpperCase();
        }
        return insertCrawlerUrls(Arrays.asList(
            new CrawlerUrl(url, accepted, timestamp, crawlId, sourceUrl, sourceXpath, buttonText)));
    }

    // -------------------------------------------------------------------------
    // Batch endpoints (used by current crawler version)
    // -------------------------------------------------------------------------

    public String uploadCrawlerDataBatch() {
        if (crawlerDataList == null || crawlerDataList.isEmpty()) {
            addActionError("crawlerDataList is required");
            return Action.ERROR.toUpperCase();
        }
        return pushCrawlerDataToKafka(crawlerDataList);
    }

    public String saveCrawlerUrlsBatch() {
        if (crawlUrlsBatch == null || crawlUrlsBatch.isEmpty()) {
            addActionError("crawlUrlsBatch is required");
            return Action.ERROR.toUpperCase();
        }
        try {
            List<Map<String, Object>> items = new ObjectMapper().readValue(
                crawlUrlsBatch, new TypeReference<List<Map<String, Object>>>() {});
            List<CrawlerUrl> urls = new ArrayList<>();
            for (Map<String, Object> item : items) {
                String u        = (String) item.get("url");
                boolean acc     = Boolean.TRUE.equals(item.get("accepted"));
                int ts          = item.containsKey("timestamp")
                                  ? ((Number) item.get("timestamp")).intValue() : Context.now();
                String cid      = item.containsKey("crawlId") ? (String) item.get("crawlId") : crawlId;
                urls.add(new CrawlerUrl(u, acc, ts, cid,
                    (String) item.get("sourceUrl"), (String) item.get("sourceXpath"), (String) item.get("buttonText")));
            }
            return insertCrawlerUrls(urls);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Exception in saveCrawlerUrlsBatch");
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
                loggerMaker.infoAndAddToDb("Available DAST modules not found");
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

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching DAST modules: " + e.getMessage());
        }
        return Action.SUCCESS.toUpperCase();
    }

    private String status;
    private String errorMessage;
    public String updateCrawlerStatus() {
        try {
            if (status == null || status.isEmpty()) {
                loggerMaker.errorAndAddToDb("updateCrawlerStatus: missing status for crawlId=" + crawlId);
                return Action.ERROR.toUpperCase();
            }
            Bson updates = null;
            if (status.equals(CrawlerRun.CrawlerRunStatus.RUNNING.name())) {
                updates = Updates.combine(
                        Updates.set(CrawlerRun.STATUS, status),
                        Updates.set(CrawlerRun.START_TIMESTAMP, Context.now())
                );
            } else if (status.equals(CrawlerRun.CrawlerRunStatus.COMPLETED.name())) {
                updates = Updates.combine(
                        Updates.set(CrawlerRun.STATUS, status),
                        Updates.set(CrawlerRun.END_TIMESTAMP, Context.now())
                );
                // Clear frame from memory when crawl completes
                CrawlerFrameCache.instance.clearFrame(crawlId);
            } else if (status.equals(CrawlerRun.CrawlerRunStatus.FAILED.name())) {
                updates = Updates.combine(
                        Updates.set(CrawlerRun.STATUS, status),
                        Updates.set(CrawlerRun.END_TIMESTAMP, Context.now()),
                        Updates.set(CrawlerRun.ERROR_MESSAGE, errorMessage)
                );
                // Clear frame from memory when crawl fails
                CrawlerFrameCache.instance.clearFrame(crawlId);
            } else if (status.equals(CrawlerRun.CrawlerRunStatus.STOPPED.name())) {
                updates = Updates.combine(
                        Updates.set(CrawlerRun.STATUS, status),
                        Updates.set(CrawlerRun.END_TIMESTAMP, Context.now())
                );
                // Clear frame from memory when crawl is stopped
                CrawlerFrameCache.instance.clearFrame(crawlId);
            } else {
                loggerMaker.errorAndAddToDb("updateCrawlerStatus: unrecognized status=" + status + " for crawlId=" + crawlId);
                return Action.ERROR.toUpperCase();
            }

            CrawlerRunDao.instance.updateOne(
                    Filters.eq(CrawlerRun.CRAWL_ID, crawlId),
                    updates
            );

            // Trigger tests after successful crawling completion
            if (status.equals(CrawlerRun.CrawlerRunStatus.COMPLETED.name())) {
                CrawlerRun crawlerRun = CrawlerRunDao.instance.findOne(
                        Filters.eq(CrawlerRun.CRAWL_ID, crawlId)
                );
                triggerTestsAfterCrawling(crawlerRun);
            }

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "error in updateCrawlerStatus");
            return Action.ERROR.toUpperCase();
        }
    }

    /**
     * Upload crawler frame from DAST module
     * Stores latest frame in memory for live browser view
     */
    public String uploadCrawlerFrame() {
        try {
            if (crawlId == null || frameData == null) {
                addActionError("crawlId and frameData are required");
                return ERROR.toUpperCase();
            }

            // Create frame JSON
            JSONObject frame = new JSONObject();
            frame.put("crawlId", crawlId);
            frame.put("frameData", frameData);
            frame.put("currentUrl", currentUrl);
            frame.put("timestamp", timestamp > 0 ? timestamp : Context.now());

            // Store in memory
            CrawlerFrameCache.instance.storeFrame(crawlId, frame.toString());

            loggerMaker.infoAndAddToDb("Frame uploaded for crawl: " + crawlId, LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error uploading frame: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to upload frame");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Get latest crawler frame for frontend
     * Returns latest frame for given crawlId
     */
    public String getLatestCrawlerFrame() {
        try {
            if (crawlId == null) {
                addActionError("crawlId is required");
                return ERROR.toUpperCase();
            }

            latestFrameJson = CrawlerFrameCache.instance.getLatestFrame(crawlId);

            if (latestFrameJson == null) {
                latestFrameJson = "{}"; // Return empty object if no frame yet
            }

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching frame: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to fetch frame");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Upload the navigation graph (Mermaid flowchart source) from the DAST module.
     * Persisted on the CrawlerRun so it can be displayed after the crawl finishes.
     */
    public String uploadCrawlerGraph() {
        try {
            if (crawlId == null || graph == null) {
                addActionError("crawlId and graph are required");
                return ERROR.toUpperCase();
            }

            CrawlerRunDao.instance.updateOne(
                    Filters.eq(CrawlerRun.CRAWL_ID, crawlId),
                    Updates.set(CrawlerRun.NAVIGATION_GRAPH, graph)
            );

            loggerMaker.infoAndAddToDb("Navigation graph uploaded for crawl: " + crawlId, LogDb.DASHBOARD);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error uploading navigation graph: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to upload navigation graph");
            return ERROR.toUpperCase();
        }
    }

    /**
     * Return the stored navigation graph (Mermaid source) for a crawlId, or empty.
     */
    public String getCrawlerGraph() {
        try {
            if (crawlId == null) {
                addActionError("crawlId is required");
                return ERROR.toUpperCase();
            }

            CrawlerRun crawlerRun = CrawlerRunDao.instance.findOne(
                    Filters.eq(CrawlerRun.CRAWL_ID, crawlId)
            );
            navigationGraph = (crawlerRun != null && crawlerRun.getNavigationGraph() != null)
                    ? crawlerRun.getNavigationGraph() : "";

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching navigation graph: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to fetch navigation graph");
            return ERROR.toUpperCase();
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

    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders;
    }

    public boolean isRunTestAfterCrawling() {
        return runTestAfterCrawling;
    }

    public void setRunTestAfterCrawling(boolean runTestAfterCrawling) {
        this.runTestAfterCrawling = runTestAfterCrawling;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getSelectedMiniTestingService() {
        return selectedMiniTestingService;
    }

    public void setSelectedMiniTestingService(String selectedMiniTestingService) {
        this.selectedMiniTestingService = selectedMiniTestingService;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public String getFrameData() {
        return frameData;
    }

    public void setFrameData(String frameData) {
        this.frameData = frameData;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public void setCurrentUrl(String currentUrl) {
        this.currentUrl = currentUrl;
    }

    public String getUserPrompt() {
        return userPrompt;
    }

    public void setUserPrompt(String userPrompt) {
        this.userPrompt = userPrompt;
    }
}
