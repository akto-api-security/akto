package com.akto.utils.crons;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.conversions.Bson;

import com.akto.action.observe.InventoryAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ActivitiesDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.AccountSettings;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods;
import com.akto.gpt.handlers.gpt_prompts.McpToolMaliciousnessAnalyzer;
import com.akto.dto.HttpResponseParams;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.parsers.HttpCallParser;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.akto.utils.RiskScoreOfCollections;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.akto.dao.billing.OrganizationsDao;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SyncCron {
    private static final LoggerMaker loggerMaker = new LoggerMaker(SyncCron.class, LogDb.DASHBOARD);
    private static final String AKTO_API_INFO_CRONS = "AKTO_API_INFO_CRONS";
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    ScheduledExecutorService mcpMaliciousnessScheduler = Executors.newScheduledThreadPool(1);

    public void setUpUpdateCronScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        if (!OrganizationsDao.instance.checkFeatureAccess(t.getId(), AKTO_API_INFO_CRONS)) {
                            loggerMaker.debugAndAddToDb("Skipping risk info mapping for account: " + t.getId(), LogDb.DASHBOARD);
                            return;
                        }
                        loggerMaker.warnAndAddToDb("Cron for mapping risk info picked up by " + t.getId(), LogDb.DASHBOARD);
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();
                        loggerMaker.debugAndAddToDb("Cron for updating new parameters, new endpoints and severity score picked up " + accountSettings.getId(), LogDb.DASHBOARD);
                        try {
                            int endTs = Context.now();
                            int startTs = endTs - 600 ;
                            int startTsSeverity = 0;
                            int resetTs = 0;
                            if(lastRunTimerInfo != null){
                                startTs = lastRunTimerInfo.getLastSyncedCron();
                                startTsSeverity = lastRunTimerInfo.getLastUpdatedSeverity();
                                resetTs = lastRunTimerInfo.getLastInfoResetted();
                            }
                            
                            // synced new parameters from STI and then inserted into activities
                            Bson filter = Filters.and(Filters.gte(SingleTypeInfo._TIMESTAMP, startTs),Filters.lte(SingleTypeInfo._TIMESTAMP, endTs));
                            long newParams = SingleTypeInfoDao.instance.getMCollection().countDocuments(filter);
                            
                            if(newParams > 0){
                                ActivitiesDao.instance.insertActivity("Parameters detected",newParams + " new parameters detected");
                            }

                             // synced new endpoints from APIinfo and then inserted into activities
                            long newEndpoints  = new InventoryAction().fetchRecentEndpoints(startTs,endTs).size();
                            if(newEndpoints > 0){
                                ActivitiesDao.instance.insertActivity("Endpoints detected",newEndpoints + " new endpoints detected");
                            }

                            // updated {Severity score field in APIinfo}
                            RiskScoreOfCollections updateRiskScore = new RiskScoreOfCollections();

                            Bson update = Updates.combine(
                                Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_SYNCED_CRON), endTs),
                                Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_UPDATED_SEVERITY), endTs)
                            );

                            // invoke reset once everyday
                            int resetTime = 24 * 60 * 60;

                            if((endTs - resetTs) >= resetTime){
                                // if reset is called, calculate riskScore for each api whose last updated is here while resetting isSensitive false and severityScore 0
                                updateRiskScore.calculateRiskScoreForAllApis();

                                // update account settings docs
                                update = Updates.combine(update,  
                                        Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_INFO_RESETTED), endTs)
                                    );

                                if (t.getId() == 1763355072) {
                                    String message = String.format("Full risk score recalculation completed for all APIs (endTs: %d, resetTs: %d, timeSinceReset: %d hours)",
                                        endTs, resetTs, (endTs - resetTs) / 3600);
                                    ActivitiesDao.instance.insertActivity("Risk score reset", message);
                                }
                            }else{
                                updateRiskScore.updateSeverityScoreInApiInfo(startTsSeverity);
                                if (t.getId() == 1763355072) {
                                    String message = String.format("Incremental severity score update completed (startTs: %d, endTs: %d, duration: %d seconds)",
                                        startTsSeverity, endTs, (endTs - startTsSeverity));
                                    ActivitiesDao.instance.insertActivity("Severity score update", message);
                                }
                            }


                            AccountSettingsDao.instance.getMCollection().updateOne(
                                AccountSettingsDao.generateFilter(),
                                update,
                                new UpdateOptions().upsert(true)
                            );
                        } catch (Exception e) {
                           e.printStackTrace();
                        }
                    }
                }, "sync-cron-info");
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    public void setUpMcpMaliciousnessCronScheduler() {
        mcpMaliciousnessScheduler.scheduleWithFixedDelay(new Runnable() {
            public void run() {
//                Context.accountId.set(1000_000);
//                boolean dibs = callDibs(Cluster.MCP_MALICIOUSNESS_CRON_INFO, 300, 60);
//                if(!dibs){
//                    loggerMaker.debugAndAddToDb("Cron for updating malicious MCP server tags dibs not acquired, thus skipping cron", LogDb.DASHBOARD);
//                    return;
//                }
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        loggerMaker.debugAndAddToDb("Cron for updating malicious MCP server tags picked up for account " + t.getId(), LogDb.DASHBOARD);
                        try {
                            // Update malicious-mcp-server tags based on tool analysis (malicious names, descriptions, name-description mismatches)
                            updateMaliciousMcpServerTags();
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb("Error in updateMaliciousMcpServerTags: " + e.getMessage(), LogDb.DASHBOARD);
                        }
                    }
                }, "mcp-maliciousness-cron-info");
            }
        }, 0, 30, TimeUnit.MINUTES);
    }

    private void updateMaliciousMcpServerTags() {
        try {
            // Get all MCP collections with only required fields (ID and tagsList), excluding deactivated ones
            Bson mcpTagFilter = Filters.elemMatch(ApiCollection.TAGS_STRING,
                Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_MCP_SERVER_TAG));
            Bson activeFilter = Filters.or(
                Filters.exists(ApiCollection._DEACTIVATED, false),
                Filters.eq(ApiCollection._DEACTIVATED, false)
            );
            List<ApiCollection> allCollections = ApiCollectionsDao.instance.findAll(
                Filters.and(mcpTagFilter, activeFilter),
                Projections.include(ApiCollection.ID, ApiCollection.TAGS_STRING, ApiCollection.MCP_MALICIOUSNESS_LAST_CHECK)
            );

            if (allCollections == null || allCollections.isEmpty()) {
                return;
            }

            // Get all collection IDs and create collection map using streams
            Set<Integer> mcpCollectionIds = allCollections.stream()
                .filter(ApiCollection::isMcpCollection)
                .map(ApiCollection::getId)
                .collect(Collectors.toSet());

            Map<Integer, ApiCollection> collectionMap = allCollections.stream()
                .filter(ApiCollection::isMcpCollection)
                .collect(Collectors.toMap(
                    ApiCollection::getId,
                    collection -> collection,
                    (existing, replacement) -> existing
                ));

            // Analyze tools for each MCP collection (only if tools have changed)
            Set<Integer> maliciousCollections = new HashSet<>();
            Set<Integer> analyzedCollections = new HashSet<>(); // Track which collections were analyzed
            McpToolMaliciousnessAnalyzer analyzer = new McpToolMaliciousnessAnalyzer();
            int currentTime = Context.now();

            for (Integer collectionId : mcpCollectionIds) {
                try {
                    ApiCollection collection = collectionMap.get(collectionId);

                    // Find the tools/list endpoint once for this collection
                    ApiInfo toolsListApi = ApiInfoDao.instance.findOne(
                        Filters.and(
                            Filters.eq(ApiInfo.ID_API_COLLECTION_ID, collectionId),
                            Filters.regex(ApiInfo.ID_URL, "tools/list", "i"),
                            Filters.eq(ApiInfo.ID_METHOD, URLMethods.Method.POST.name())
                        )
                    );

                    // Check if we need to analyze (tools may have changed)
                    if (!shouldAnalyzeCollection(collection, toolsListApi)) {
                        loggerMaker.debugAndAddToDb(String.format("Skipping collection %d - tools haven't changed since last check", collectionId), LogDb.DASHBOARD);
                        // Keep existing malicious tag status (don't update tags for skipped collections)
                        continue;
                    }

                    // Analyze tools and update last check timestamp only if analysis completes
                    boolean isMalicious = analyzeMcpCollectionTools(collectionId, toolsListApi, analyzer);
                    analyzedCollections.add(collectionId); // Mark as analyzed

                    if (isMalicious) {
                        maliciousCollections.add(collectionId);
                    }

                    // Update last check timestamp after successful analysis
                    updateLastCheckTimestamp(collectionId, currentTime);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(String.format("Error analyzing tools for collection %d: %s", collectionId, e.getMessage()), LogDb.DASHBOARD);
                    // Don't update timestamp on error - will retry next time
                }
            }

            // Update tags only for collections that were analyzed
            for (Integer collectionId : analyzedCollections) {
                try {
                    ApiCollection collection = collectionMap.get(collectionId);
                    if (collection == null) {
                        continue;
                    }

                    // Get current tags list
                    List<CollectionTags> currentTags = collection.getTagsList();
                    if (currentTags == null) {
                        currentTags = new ArrayList<>();
                    }

                    // Create a new list without the malicious-mcp-server tag
                    List<CollectionTags> updatedTags = new ArrayList<>();
                    for (CollectionTags tag : currentTags) {
                        if (!Constants.AKTO_MALICIOUS_MCP_SERVER_TAG.equals(tag.getKeyName())) {
                            updatedTags.add(tag);
                        }
                    }

                    // Add the malicious tag if collection is malicious
                    if (maliciousCollections.contains(collectionId)) {
                        CollectionTags maliciousTag = new CollectionTags(
                            Context.now(),
                            Constants.AKTO_MALICIOUS_MCP_SERVER_TAG,
                            "true",
                            CollectionTags.TagSource.USER
                        );
                        updatedTags.add(maliciousTag);
                        loggerMaker.debugAndAddToDb(String.format("Added malicious-mcp-server tag to collection %d", collectionId), LogDb.DASHBOARD);
                    } else {
                        loggerMaker.debugAndAddToDb(String.format("Removed malicious-mcp-server tag from collection %d", collectionId), LogDb.DASHBOARD);
                    }

                    // Replace entire tags array with a single update operation
                    Bson filter = Filters.eq(ApiCollection.ID, collectionId);
                    Bson update = Updates.set(ApiCollection.TAGS_STRING, updatedTags);
                    ApiCollectionsDao.instance.updateOne(filter, update);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(String.format("Error updating malicious-mcp-server tag for collection %d: %s", collectionId, e.getMessage()), LogDb.DASHBOARD);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in updateMaliciousMcpServerTags: " + e.getMessage(), LogDb.DASHBOARD);
        }
    }

    private boolean shouldAnalyzeCollection(ApiCollection collection, ApiInfo toolsListApi) {
        try {
            if (toolsListApi == null) {
                // If tools/list doesn't exist, we should check (first time)
                return true;
            }

            int toolsListLastSeen = toolsListApi.getLastSeen();
            if (toolsListLastSeen == 0) {
                // If lastSeen is 0, we should check
                return true;
            }

            // Get last check timestamp from ApiCollection field
            int lastCheckTimestamp = collection.getMcpMaliciousnessLastCheck();

            // If we haven't checked before, or if tools/list has been seen after our last check, analyze
            if (lastCheckTimestamp == 0 || toolsListLastSeen > lastCheckTimestamp) {
                return true;
            }

            return false;
        } catch (Exception e) {
            loggerMaker.debugAndAddToDb(String.format("Error checking if collection %d should be analyzed: %s", collection.getId(), e.getMessage()), LogDb.DASHBOARD);
            // On error, default to checking
            return true;
        }
    }

    private void updateLastCheckTimestamp(int collectionId, int timestamp) {
        try {
            Bson filter = Filters.eq(ApiCollection.ID, collectionId);
            Bson update = Updates.set(ApiCollection.MCP_MALICIOUSNESS_LAST_CHECK, timestamp);

            ApiCollectionsDao.instance.updateOne(filter, update);
        } catch (Exception e) {
            loggerMaker.debugAndAddToDb(String.format("Error updating last check timestamp for collection %d: %s", collectionId, e.getMessage()), LogDb.DASHBOARD);
        }
    }

    private boolean analyzeMcpCollectionTools(int collectionId, ApiInfo toolsListApi, McpToolMaliciousnessAnalyzer analyzer) {
        try {
            if (toolsListApi == null) {
                loggerMaker.debugAndAddToDb(String.format("tools/list endpoint not found for collection %d", collectionId), LogDb.DASHBOARD);
                return false;
            }

            // Get sample data for tools/list endpoint
            SampleData sampleData = SampleDataDao.instance.fetchSampleDataForApi(
                collectionId,
                toolsListApi.getId().getUrl(),
                toolsListApi.getId().getMethod()
            );

            if (sampleData == null || sampleData.getSamples() == null || sampleData.getSamples().isEmpty()) {
                loggerMaker.debugAndAddToDb(String.format("No sample data found for tools/list in collection %d", collectionId), LogDb.DASHBOARD);
                return false;
            }

            // Parse the first sample to get tools/list response
            String sampleJson = sampleData.getSamples().get(0);

            // Use parseKafkaMessage to extract request/response data
            HttpResponseParams httpResponseParams;
            try {
                httpResponseParams = HttpCallParser.parseKafkaMessage(sampleJson);
            } catch (Exception e) {
                loggerMaker.debugAndAddToDb(String.format("Error parsing sample data for collection %d: %s", collectionId, e.getMessage()), LogDb.DASHBOARD);
                return false;
            }

            // Extract response payload and content type
            String responsePayload = httpResponseParams.getPayload();
            String contentType = HttpRequestResponseUtils.getHeaderValue(httpResponseParams.getHeaders(), HttpRequestResponseUtils.CONTENT_TYPE);

            if (responsePayload == null || responsePayload.isEmpty()) {
                loggerMaker.debugAndAddToDb(String.format("No response payload found in sample data for collection %d", collectionId), LogDb.DASHBOARD);
                return false;
            }

            // Use McpRequestResponseUtils to parse response (handles SSE responses properly)
            String parsedResponse = McpRequestResponseUtils.parseResponse(contentType, responsePayload);

            // If it's an SSE response, extract the first event's data
            String jsonrpcData = parsedResponse;
            if (contentType != null && contentType.toLowerCase().contains(HttpRequestResponseUtils.TEXT_EVENT_STREAM_CONTENT_TYPE)) {
                try {
                    BasicDBObject parsed = BasicDBObject.parse(parsedResponse);
                    Object eventsObj = parsed.get("events");
                    if (eventsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<BasicDBObject> events = (List<BasicDBObject>) eventsObj;
                        if (events != null && !events.isEmpty()) {
                            jsonrpcData = events.get(0).getString("data");
                            if (jsonrpcData == null || jsonrpcData.isEmpty()) {
                                loggerMaker.debugAndAddToDb(String.format("No data found in SSE events for collection %d", collectionId), LogDb.DASHBOARD);
                                return false;
                            }
                        }
                    }
                } catch (Exception e) {
                    loggerMaker.debugAndAddToDb(String.format("Error parsing SSE response for collection %d: %s", collectionId, e.getMessage()), LogDb.DASHBOARD);
                    return false;
                }
            }

            // Parse JSON-RPC response to get tools
            JSONObject jsonrpcResponse = new JSONObject(jsonrpcData);
            if (!jsonrpcResponse.has("result")) {
                loggerMaker.debugAndAddToDb(String.format("Invalid JSON-RPC response format for collection %d", collectionId), LogDb.DASHBOARD);
                return false;
            }

            JSONObject result = jsonrpcResponse.getJSONObject("result");
            if (!result.has("tools")) {
                loggerMaker.debugAndAddToDb(String.format("No tools found in response for collection %d", collectionId), LogDb.DASHBOARD);
                return false;
            }

            JSONArray toolsArray = result.getJSONArray("tools");

            // Analyze each tool
            for (int i = 0; i < toolsArray.length(); i++) {
                JSONObject tool = toolsArray.getJSONObject(i);
                String toolName = tool.optString("name", "");
                String toolDescription = tool.optString("description", "");

                if (toolName.isEmpty()) {
                    continue;
                }

                // Analyze tool for maliciousness using LLM
                BasicDBObject queryData = new BasicDBObject();
                queryData.put(McpToolMaliciousnessAnalyzer.TOOL_NAME, toolName);
                queryData.put(McpToolMaliciousnessAnalyzer.TOOL_DESCRIPTION, toolDescription);

                BasicDBObject analysisResult = analyzer.handle(queryData);

                if (analysisResult != null && analysisResult.getBoolean("isMalicious", false)) {
                    loggerMaker.infoAndAddToDb(String.format(
                        "Found malicious tool in collection %d: %s. Reason: %s",
                        collectionId, toolName, analysisResult.getString("reason")
                    ), LogDb.DASHBOARD);
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(String.format("Error analyzing MCP tools for collection %d: %s", collectionId, e.getMessage()), LogDb.DASHBOARD);
            return false;
        }
    }
}
