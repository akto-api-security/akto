package com.akto.action;

import com.akto.ApiRequest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.log.LoggerMaker;
import com.akto.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import org.json.JSONObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class AktoJaxAction extends UserAction {

    private String hostname;
    private String username;
    private String password;
    private String apiKey;
    private String dashboardUrl;

    private String crawlerData;

    private String apiCollectionId;

    private static final LoggerMaker loggerMaker = new LoggerMaker(AktoJaxAction.class, LoggerMaker.LogDb.DASHBOARD);

    public String initiateCrawler() {
        try {
            String url = System.getenv("AKTOJAX_SERVICE_URL") + "/triggerCrawler";

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

            JSONObject requestBody = new JSONObject();
            requestBody.put("hostname", hostname);
            requestBody.put("username", username);
            requestBody.put("password", password);
            requestBody.put("apiKey", apiKey);
            requestBody.put("dashboardUrl", dashboardUrl);
            requestBody.put("collectionId", collectionId);
            requestBody.put("accountId", Context.accountId.get());

            String reqData = requestBody.toString();
            JsonNode node = ApiRequest.postRequest(new HashMap<>(), url, reqData);
            String status = node.get("status").textValue();

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

        // fetch collection id
        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne(Filters.eq("_id", Integer.valueOf(apiCollectionId)));
        if(apiCollection == null) {
            addActionError("API collection not found");
            return Action.ERROR.toUpperCase();
        }

        try {
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
}
