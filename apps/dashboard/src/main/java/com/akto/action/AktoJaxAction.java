package com.akto.action;

import com.akto.ApiRequest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestRoles;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.akto.util.RecordedLoginFlowUtil;
import com.akto.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
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

    private static final LoggerMaker loggerMaker = new LoggerMaker(AktoJaxAction.class, LoggerMaker.LogDb.DASHBOARD);

    public String initiateCrawler() {
        try {
            loggerMaker.infoAndAddToDb("Initializing Crawler", LoggerMaker.LogDb.DASHBOARD);
            String url = System.getenv("AKTOJAX_SERVICE_URL") + "/triggerCrawler";
            loggerMaker.infoAndAddToDb("Crawler service url: " + url, LoggerMaker.LogDb.DASHBOARD);

            URL parsedUrl = new URL(hostname);
            String host = parsedUrl.getHost();

            ApiCollectionsAction collectionsAction = new ApiCollectionsAction();
            collectionsAction.setCollectionName(host);
            String collectionStatus = collectionsAction.createCollection();
            int collectionId = 0;

            if(collectionStatus.equalsIgnoreCase(Action.SUCCESS)) {
                List<ApiCollection> apiCollections = collectionsAction.getApiCollections();
                if (apiCollections != null && !apiCollections.isEmpty()) {
                    collectionId = apiCollections.get(0).getId();
                } else {
                    ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.NAME, host));
                    if (apiCollection != null) {
                        collectionId = apiCollection.getId();
                    }
                }
            } else {
                ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.NAME, host));
                if (apiCollection != null) {
                    collectionId = apiCollection.getId();
                }
            }

            loggerMaker.infoAndAddToDb("Crawler collection id: " + collectionId, LoggerMaker.LogDb.DASHBOARD);

            JSONObject requestBody = new JSONObject();
            requestBody.put("hostname", hostname);
            requestBody.put("apiKey", apiKey);
            requestBody.put("dashboardUrl", dashboardUrl);
            requestBody.put("collectionId", collectionId);
            requestBody.put("accountId", Context.accountId.get());
            requestBody.put("outscopeUrls", outscopeUrls);

            if(!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
                requestBody.put("username", username);
                requestBody.put("password", password);
            } else if(testRoleHaxId != null && !testRoleHaxId.isEmpty()) {
                TestRoles testRole = TestRolesDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(testRoleHaxId)));
                AuthMechanism authMechanismForRole = testRole.findDefaultAuthMechanism();

                RecordedLoginFlowInput recordedLoginFlowInput = authMechanismForRole.getRecordedLoginFlowInput();
                String payload = recordedLoginFlowInput.getContent().toString();
                File tmpOutputFile;
                File tmpErrorFile;
                try {
                    tmpOutputFile = File.createTempFile("output", ".json");
                    tmpErrorFile = File.createTempFile("recordedFlowOutput", ".txt");
                    RecordedLoginFlowUtil.triggerFlow(recordedLoginFlowInput.getTokenFetchCommand(), payload, tmpOutputFile.getPath(), tmpErrorFile.getPath(), getSUser().getId());

                    String token = RecordedLoginFlowUtil.fetchToken(tmpOutputFile.getPath(), tmpErrorFile.getPath());
                    BasicDBObject parseToken = BasicDBObject.parse(token);
                    if(parseToken != null) {
                        loggerMaker.infoAndAddToDb("Got the cookies from test role for crawler");
                        BasicDBList allCookies = (BasicDBList) parseToken.get("all_cookies");
                        requestBody.put("cookies", allCookies);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb("Error while fetching cookies from test role: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
                }
            }

            String reqData = requestBody.toString();

            loggerMaker.infoAndAddToDb("Crawler request data: " + reqData, LoggerMaker.LogDb.DASHBOARD);

            JsonNode node = ApiRequest.postRequest(new HashMap<>(), url, reqData);
            String status = node.get("status").textValue();

            loggerMaker.infoAndAddToDb("Crawler status: " + status, LoggerMaker.LogDb.DASHBOARD);

            if(status.equalsIgnoreCase("success")) {
                return Action.SUCCESS.toUpperCase();
            } else {
                return Action.ERROR.toUpperCase();
            }
        } catch (Exception e) {
            loggerMaker.error("Error while initiating the Akto crawler. Error: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }
    }

    public String uploadCrawlerData() {
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topic == null) topic = "akto.api.logs";

        loggerMaker.infoAndAddToDb("uploadCrawlerData() - Crawler topic: " + topic, LoggerMaker.LogDb.DASHBOARD);

        // fetch collection id
        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq("_id", Integer.valueOf(apiCollectionId)));
        if(apiCollection == null) {
            addActionError("API collection not found");
            return Action.ERROR.toUpperCase();
        }

        try {
            loggerMaker.infoAndAddToDb("uploadCrawlerData() - Pushing crawler data to kafka", LoggerMaker.LogDb.DASHBOARD);
            Utils.pushDataToKafka(apiCollection.getId(), topic, Arrays.asList(crawlerData), new ArrayList<>(), true, true, true);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Exception while inserting crawler data", LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
        }

        return Action.SUCCESS.toUpperCase();
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
}
