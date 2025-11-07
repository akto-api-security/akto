package com.akto.action;

import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dto.McpAuditInfo;
import org.bson.conversions.Bson;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.APIConfig;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.CollectionTags.TagSource;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.McpToolsSyncJobExecutor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

import static com.akto.mcp.McpRequestResponseUtils.setAuditData;
import static com.akto.util.Constants.AKTO_MCP_SERVER_TAG;

public class MCPScanAction extends UserAction {

    private String serverUrl;
    private String dashboardUrl;
    private String authKey;
    private String authValue;
    private String apiCollectionId;
    private ApiCollection createdCollection;

    private static final LoggerMaker loggerMaker = new LoggerMaker(MCPScanAction.class, LoggerMaker.LogDb.DASHBOARD);
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public String initiateMCPScan() {
        try {

            URL parsedUrl = new URL(serverUrl);
            // Store the full path with query params for MCP endpoint
            // This could be either an SSE endpoint or a standard HTTP endpoint
            String mcpEndpoint = parsedUrl.getPath() + (parsedUrl.getQuery() != null ? "?" + parsedUrl.getQuery() : "");
            String hostName = parsedUrl.getHost();
            hostName = hostName.toLowerCase();
            hostName = hostName.trim();
            int collectionId = hostName.hashCode();

            //create api collection using collectionId if it does not exist
            createdCollection = ApiCollectionsDao.instance.findOne(Filters.eq(ApiCollection.ID, collectionId));
            if (createdCollection != null) {
                loggerMaker.info("ApiCollection already exists for host: " + hostName, LogDb.DASHBOARD);
            } else {
                loggerMaker.info("Creating ApiCollection for host: " + hostName, LogDb.DASHBOARD);  
                createdCollection = new ApiCollection(collectionId, null, Context.now(), new HashSet<>(), hostName, 0, false, true, mcpEndpoint);
                ApiCollectionsDao.instance.insertOne(createdCollection);

                try {
                    //New MCP server detected, audit it
                    McpAuditInfo auditInfo = new McpAuditInfo(
                            Context.now(), "", AKTO_MCP_SERVER_TAG , 0,
                            hostName, "", null,
                            collectionId
                    );

                    setAuditData(auditInfo);
                } catch (Exception e) {
                    loggerMaker.error("Exception while inserting McpAuditInfo: " + e.getMessage(), LogDb.DASHBOARD);
                }
            }

            if(createdCollection == null) {
                loggerMaker.error("Unable to find or create ApiCollection for host: " + hostName, LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }

            //update api collection with tags and ensure sseCallbackUrl/mcpEndpoint is set
            Bson updates = Updates.combine(
                Updates.setOnInsert("_id", collectionId),
                Updates.setOnInsert("startTs", Context.now()),
                Updates.setOnInsert("urls", new HashSet<>()),
                Updates.set(ApiCollection.SSE_CALLBACK_URL, mcpEndpoint),
                Updates.set(ApiCollection.TAGS_STRING,
                Collections.singletonList(new CollectionTags(Context.now(), AKTO_MCP_SERVER_TAG, "MCP Server", TagSource.KUBERNETES)))
            );

            FindOneAndUpdateOptions updateOptions = new FindOneAndUpdateOptions();
            updateOptions.upsert(true);
            updateOptions.returnDocument(ReturnDocument.AFTER);

            createdCollection = ApiCollectionsDao.instance.getMCollection()
                .findOneAndUpdate(
                    Filters.eq(ApiCollection.ID, createdCollection.getId()), updates, updateOptions);


            // Create APIConfig for MCP tools sync job
            // Use the provided authKey and authValue for authentication
            APIConfig apiConfig = new APIConfig("userIdentifier","access-token", 1, 1, 1);
            try {
                loggerMaker.info("Starting MCP sync job..");
                String authHeader;
                if(authKey != null && !authKey.isEmpty() && authValue != null && !authValue.isEmpty()) {
                    authHeader = "\"" + authKey + "\": \"" + authValue + "\"";
                } else {
                    authHeader = "";
                    loggerMaker.info("No authentication credentials provided");
                }
                int accountId = Context.accountId.get();
                executorService.schedule(new Runnable() {
                    public void run() {
                        Context.accountId.set(accountId);
                        loggerMaker.info("Starting MCP sync job for collection: {} with host: {} and MCP endpoint: {}",
                            createdCollection.getId(), createdCollection.getHostName(), createdCollection.getSseCallbackUrl());
                        new McpToolsSyncJobExecutor().runJobforCollection(createdCollection, apiConfig, authHeader);
                    }
                }, 0, TimeUnit.SECONDS);

            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error in MCP tools sync job: " + e.getMessage());
                return Action.ERROR.toUpperCase();
            }

            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error while initiating the Akto MCP Scan. Error: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;

    }

    public String getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(String apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getDashboardUrl() {
        return dashboardUrl;
    }

    public void setDashboardUrl(String dashboardUrl) {
        this.dashboardUrl = dashboardUrl;
    }

    public String getAuthKey() {
        return authKey;
    }   

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public String getAuthValue() {
        return authValue;
    }   

    public void setAuthValue(String authValue) {
        this.authValue = authValue;
    }
}
