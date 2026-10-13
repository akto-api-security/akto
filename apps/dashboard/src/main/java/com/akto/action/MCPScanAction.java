package com.akto.action;

import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dto.McpAuditInfo;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.APIConfig;
import com.akto.dto.ApiCollection;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.AuthParam;
import com.akto.dto.testing.LoginFlowResponse;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.CollectionTags.TagSource;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.McpToolsSyncJobExecutor;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;
import com.akto.util.enums.LoginFlowEnums;
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
    private String testRoleHexId;
    private String apiCollectionId;
    private ApiCollection createdCollection;

    private static final LoggerMaker loggerMaker = new LoggerMaker(MCPScanAction.class, LoggerMaker.LogDb.DASHBOARD);
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public String initiateMCPScan() {
        try {

            // Resolve auth from the test role upfront so an invalid role or failed login flow
            // (e.g. OAuth token fetch) is reported to the user instead of failing silently in the sync job
            Map<String, String> testRoleAuthHeaders = null;
            if (testRoleHexId != null && !testRoleHexId.isEmpty()) {
                try {
                    testRoleAuthHeaders = resolveAuthHeadersFromTestRole(testRoleHexId);
                } catch (Exception e) {
                    String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    loggerMaker.errorAndAddToDb(e, "Error resolving auth from test role for MCP scan: " + reason);
                    addActionError("Test role authentication failed: " + reason);
                    return Action.ERROR.toUpperCase();
                }
            }

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
                            collectionId,
                            hostName
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
                Map<String, String> authHeader = new HashMap<>();
                if(testRoleAuthHeaders != null) {
                    authHeader.putAll(testRoleAuthHeaders);
                } else if(authKey != null && !authKey.isEmpty() && authValue != null && !authValue.isEmpty()) {
                    authHeader.put(authKey, authValue);
                } else {
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

    private Map<String, String> resolveAuthHeadersFromTestRole(String roleHexId) throws Exception {
        TestRoles testRole = TestRolesDao.instance.findOne(Filters.eq(Constants.ID, new ObjectId(roleHexId)));
        if (testRole == null) {
            throw new Exception("Test role not found.");
        }

        AuthMechanism authMechanism = testRole.findDefaultAuthMechanism();
        if (authMechanism == null) {
            throw new Exception("No default auth mechanism configured for test role: " + testRole.getName());
        }

        String authType = authMechanism.getType();
        if (LoginFlowEnums.AuthMechanismTypes.LOGIN_REQUEST.toString().equalsIgnoreCase(authType)) {
            // Runs the configured login steps (e.g. OAuth token request or recorded login) and caches the resolved tokens
            LoginFlowResponse loginFlowResponse = TestExecutor.executeLoginFlow(authMechanism, null, testRole.getName());
            if (loginFlowResponse == null || loginFlowResponse.getSuccess() == null || !loginFlowResponse.getSuccess()) {
                String reason = (loginFlowResponse != null && loginFlowResponse.getError() != null && !loginFlowResponse.getError().isEmpty())
                    ? loginFlowResponse.getError() : "login flow did not succeed.";
                throw new Exception("Could not fetch access token: " + reason);
            }
        } else if (!LoginFlowEnums.AuthMechanismTypes.HARDCODED.toString().equalsIgnoreCase(authType)) {
            throw new Exception("Auth type " + authType + " is not supported for MCP import. Use a hardcoded or login request based test role.");
        }

        List<AuthParam> authParams = authMechanism.getAuthParamsFromAuthMechanism();
        Map<String, String> authHeaders = new HashMap<>();
        if (authParams != null) {
            for (AuthParam authParam : authParams) {
                if (authParam.getKey() == null || authParam.getKey().isEmpty() || authParam.getValue() == null) continue;
                if (authParam.getWhere() != null && authParam.getWhere() != AuthParam.Location.HEADER) {
                    loggerMaker.info("Skipping non-header auth param " + authParam.getKey() + " for MCP scan", LogDb.DASHBOARD);
                    continue;
                }
                authHeaders.put(authParam.getKey(), authParam.getValue());
            }
        }

        if (authHeaders.isEmpty()) {
            throw new Exception("No header auth params found in test role: " + testRole.getName());
        }
        return authHeaders;
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

    public String getTestRoleHexId() {
        return testRoleHexId;
    }

    public void setTestRoleHexId(String testRoleHexId) {
        this.testRoleHexId = testRoleHexId;
    }
}
