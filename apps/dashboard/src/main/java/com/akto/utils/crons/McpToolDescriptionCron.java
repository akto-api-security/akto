package com.akto.utils.crons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mcp.McpRequestResponseUtils;
import com.akto.util.Constants;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

/**
 * Copies each MCP tool's description from the server's tools/list response onto the tool's
 * tools/call/<name> endpoint (ApiInfo.description), so the inventory shows what a tool does.
 * Only fills empty descriptions, so a description typed in by a user is never overwritten.
 */
public class McpToolDescriptionCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(McpToolDescriptionCron.class, LogDb.DASHBOARD);

    // Rolled out one account at a time; add accounts here once their descriptions look right in the UI.
    private static final List<Integer> ENABLED_ACCOUNTS = Collections.singletonList(1718042191);

    private static final String TOOLS_LIST = "tools/list";
    private static final String TOOLS_LIST_URL_REGEX = TOOLS_LIST + "$";
    private static final String TOOLS_CALL = "tools/call/";

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpMcpToolDescriptionScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                for (int accountId : ENABLED_ACCOUNTS) {
                    try {
                        Context.accountId.set(accountId);
                        fillToolDescriptions();
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "AccountId: " + accountId
                                + " error while filling MCP tool descriptions: " + e.getMessage(), LogDb.DASHBOARD);
                    }
                }
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    public static void fillToolDescriptions() {
        List<ApiCollection> mcpCollections = ApiCollectionsDao.instance.findAll(
                Filters.elemMatch(ApiCollection.TAGS_STRING,
                        Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_MCP_SERVER_TAG)),
                Projections.include(Constants.ID));

        int filled = 0;
        for (ApiCollection collection : mcpCollections) {
            try {
                filled += fillForCollection(collection.getId());
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "AccountId: " + Context.accountId.get() + " collectionId: "
                        + collection.getId() + " error while filling MCP tool descriptions: " + e.getMessage(),
                        LogDb.DASHBOARD);
            }
        }
        loggerMaker.infoAndAddToDb("AccountId: " + Context.accountId.get() + " filled " + filled
                + " MCP tool descriptions across " + mcpCollections.size() + " MCP collections", LogDb.DASHBOARD);
    }

    static int fillForCollection(int apiCollectionId) {
        // One host can serve several MCP servers (/fintech/mcp, /common/mcp, ...), each with its own
        // tools/list, so descriptions are kept per server path as well as collection-wide.
        List<SampleData> toolsListSamples = SampleDataDao.instance.findAll(Filters.and(
                Filters.eq("_id.apiCollectionId", apiCollectionId),
                Filters.regex("_id.url", TOOLS_LIST_URL_REGEX)));

        Map<String, Map<String, String>> descriptionsByServer = new HashMap<>();
        Map<String, String> descriptions = new HashMap<>();
        for (SampleData sampleData : toolsListSamples) {
            if (sampleData.getSamples() == null) continue;
            Map<String, String> serverDescriptions = descriptionsByServer.computeIfAbsent(
                    serverPath(sampleData.getId().getUrl(), TOOLS_LIST), k -> new HashMap<>());
            // samples are oldest first, so a newer tools/list wins on a name clash
            for (String sample : sampleData.getSamples()) {
                Map<String, String> parsed = parseToolDescriptions(sample);
                serverDescriptions.putAll(parsed);
                descriptions.putAll(parsed);
            }
        }
        if (descriptions.isEmpty()) {
            return 0;
        }

        List<ApiInfo> tools = ApiInfoDao.instance.findAll(Filters.and(
                Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollectionId),
                Filters.regex(ApiInfo.ID_URL, TOOLS_CALL)),
                Projections.include(Constants.ID, ApiInfo.DESCRIPTION));

        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        for (ApiInfo tool : tools) {
            if (StringUtils.isNotBlank(tool.getDescription())) continue;
            String url = tool.getId().getUrl();
            String toolName = toolNameFromUrl(url);
            // Prefer the tools/list of the tool's own server; fall back to any server in the collection
            // (covers a tools/list recorded under a merged path like /STRING/mcp/tools/list).
            Map<String, String> ownServer = descriptionsByServer.get(serverPath(url, TOOLS_CALL));
            String description = ownServer != null && ownServer.containsKey(toolName)
                    ? ownServer.get(toolName) : descriptions.get(toolName);
            if (StringUtils.isBlank(description)) continue;
            updates.add(new UpdateOneModel<>(ApiInfoDao.getFilter(tool.getId()),
                    Updates.set(ApiInfo.DESCRIPTION, description.trim())));
        }

        if (!updates.isEmpty()) {
            ApiInfoDao.instance.bulkWrite(updates, new BulkWriteOptions().ordered(false));
        }
        return updates.size();
    }

    /** Tool name -> description from one stored tools/list sample. Empty when the sample isn't one. */
    static Map<String, String> parseToolDescriptions(String sample) {
        Map<String, String> descriptions = new HashMap<>();
        try {
            JSONObject sampleJson = new JSONObject(sample);
            String responsePayload = sampleJson.optString("responsePayload", "");
            String contentType = findContentType(sampleJson.optString("responseHeaders", ""));
            JSONObject parsed = new JSONObject(McpRequestResponseUtils.parseResponse(contentType, responsePayload));

            // An SSE response comes back as {"events": [{"data": "<json-rpc message>"}, ...]}
            JSONArray events = parsed.optJSONArray("events");
            if (events == null) {
                addTools(parsed, descriptions);
                return descriptions;
            }
            for (int i = 0; i < events.length(); i++) {
                String data = events.getJSONObject(i).optString("data", "");
                if (data.startsWith("{")) {
                    addTools(new JSONObject(data), descriptions);
                }
            }
        } catch (Exception e) {
            loggerMaker.debugAndAddToDb("Could not parse tools/list sample: " + e.getMessage(), LogDb.DASHBOARD);
        }
        return descriptions;
    }

    private static void addTools(JSONObject jsonRpcResponse, Map<String, String> descriptions) {
        JSONObject result = jsonRpcResponse.optJSONObject("result");
        JSONArray tools = result == null ? null : result.optJSONArray("tools");
        if (tools == null) return;
        for (int i = 0; i < tools.length(); i++) {
            JSONObject tool = tools.optJSONObject(i);
            if (tool == null) continue;
            String name = tool.optString("name", "");
            String description = tool.optString("description", "");
            if (!name.isEmpty() && !description.trim().isEmpty()) {
                descriptions.put(name, description);
            }
        }
    }

    private static String findContentType(String responseHeaders) {
        try {
            JSONObject headers = new JSONObject(responseHeaders);
            for (String key : headers.keySet()) {
                if ("content-type".equalsIgnoreCase(key)) {
                    return headers.optString(key, null);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** "/fintech/mcp/tools/call/x" or "/fintech/mcp/tools/list" -> "/fintech/mcp/" */
    static String serverPath(String url, String marker) {
        int idx = url.lastIndexOf(marker);
        return idx >= 0 ? url.substring(0, idx) : url;
    }

    /** "/mcp/tools/call/search_hotels" -> "search_hotels" */
    static String toolNameFromUrl(String url) {
        int idx = url.lastIndexOf(TOOLS_CALL);
        return idx >= 0 ? url.substring(idx + TOOLS_CALL.length()) : url;
    }
}
