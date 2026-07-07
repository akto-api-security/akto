package com.akto.action.monitoring;

import com.akto.action.UserAction;
import com.akto.dao.agentic_sessions.UserAnalysisDataDao;
import com.akto.dao.monitoring.EndpointShieldLogsDao;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.dto.monitoring.EndpointShieldServer;
import com.akto.dto.monitoring.EndpointShieldLog;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.dto.Log;
import com.akto.dao.context.Context;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EndpointShieldAgentAction extends UserAction {
    private static final LoggerMaker loggerMaker = new LoggerMaker(EndpointShieldAgentAction.class, LogDb.DASHBOARD);
    private static final int MAX_LOGS_LIMIT = 10000;
    private static final int MAX_EXPORT_LIMIT = 100_000;

    private String agentId;
    private String deviceId;
    private List<EndpointShieldServer> mcpServers;
    private List<Log> agentLogs;
    private int startTime;
    private int endTime;
    private String logKey; // optional filter: "agent-logs", "proxy-logs", "installation-logs", etc.
    private String afterId;  // ObjectId hex cursor — fetch logs older than this document
    private int pageSize;    // number of logs per page; defaults to DEFAULT_PAGE_SIZE
    private long totalCount; // total matching logs (without cursor), for display
    private boolean hasMore; // whether older logs exist beyond the current page

    @Getter
    private UserAnalysisData userAnalysis;

    @Getter
    private List<UserAnalysisData> userAnalysisList = new ArrayList<>();

    public String fetchUserAnalysisList() {
        userAnalysisList = UserAnalysisDataDao.instance.findAll(Filters.empty(),
                Projections.include(UserAnalysisData.USER_NAME, UserAnalysisData.LAST_UPDATED_AT,
                        UserAnalysisData.TOTAL_INPUT_TOKENS,
                        UserAnalysisData.TOTAL_OUTPUT_TOKENS, UserAnalysisData.AI_SUMMARY,
                        UserAnalysisData.HARMFUL_TOPICS));
        return SUCCESS.toUpperCase();
    }

    public String getMcpServersByAgent() {
        mcpServers = new ArrayList<>();
        
        List<ModuleInfo> moduleInfoList;
        if (agentId != null) {
            // agentId corresponds to _id in module_info
            moduleInfoList = ModuleInfoDao.instance.findAll(Filters.and(
                Filters.eq("_id", agentId),
                Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD)
            ));
        } else if (deviceId != null) {
            // deviceId corresponds to name in module_info
            moduleInfoList = ModuleInfoDao.instance.findAll(Filters.and(
                Filters.eq("name", deviceId),
                Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD)
            ));
        } else {
            // Get all MCP_ENDPOINT_SHIELD modules
            moduleInfoList = ModuleInfoDao.instance.findAll(Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD));
        }
        
        for (ModuleInfo moduleInfo : moduleInfoList) {
            if (moduleInfo.getAdditionalData() != null) {
                Map<String, Object> additionalData = moduleInfo.getAdditionalData();
                Object mcpServersObj = additionalData.get("mcpServers");
                
                if (mcpServersObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mcpServersMap = (Map<String, Object>) mcpServersObj;
                    
                    for (Map.Entry<String, Object> entry : mcpServersMap.entrySet()) {
                        String serverName = entry.getKey();
                        Object serverDataObj = entry.getValue();
                        
                        if (serverDataObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> serverData = (Map<String, Object>) serverDataObj;
                            
                            EndpointShieldServer server = new EndpointShieldServer();
                            server.setAgentId(moduleInfo.getId());
                            server.setDeviceId(moduleInfo.getName());
                            server.setServerName(serverName);
                            
                            // Set lastSeen from updatedTs
                            Object updatedTsObj = serverData.get("updatedTs");
                            if (updatedTsObj instanceof Number) {
                                server.setLastSeen(((Number) updatedTsObj).intValue());
                            }
                            
                            // Set serverUrl - either from url field or construct from command+args
                            Object urlObj = serverData.get("url");
                            if (urlObj instanceof String) {
                                server.setServerUrl((String) urlObj);
                            } else {
                                Object commandObj = serverData.get("command");
                                Object argsObj = serverData.get("args");
                                
                                if (commandObj instanceof String && argsObj instanceof List) {
                                    @SuppressWarnings("unchecked")
                                    List<String> args = (List<String>) argsObj;
                                    StringBuilder urlBuilder = new StringBuilder((String) commandObj);
                                    for (String arg : args) {
                                        urlBuilder.append(" ").append(arg);
                                    }
                                    server.setServerUrl(urlBuilder.toString());
                                }
                            }
                            
                            // Set collectionName - priority order:
                            // 1. Check if collectionName exists in serverData (additionalInfo)
                            // 2. Fallback to constructed name based on server type
                            Object collectionNameObj = serverData.get("collectionName");
                            String collectionName;

                            if (collectionNameObj instanceof String && !((String) collectionNameObj).isEmpty()) {
                                // Priority 1: Use collection name from additionalInfo
                                collectionName = (String) collectionNameObj;
                            } else {
                                // Priority 2: Construct collection name based on server type
                                String deviceId = moduleInfo.getName();
                                String serverUrl = server.getServerUrl();

                                // Check if it's a streamable case (contains URL pattern)
                                if (serverUrl != null && (serverUrl.startsWith("http://") || serverUrl.startsWith("https://"))) {
                                    // Streamable case: extract hostname from URL
                                    try {
                                        java.net.URL url = new java.net.URL(serverUrl);
                                        String hostname = url.getHost();
                                        collectionName = deviceId + "." + hostname;
                                    } catch (Exception e) {
                                        // If URL parsing fails, fallback to serverName
                                        collectionName = deviceId + "." + serverName;
                                    }
                                } else {
                                    // Stdio case: use deviceID.serverName
                                    collectionName = deviceId + "." + serverName;
                                }
                            }

                            server.setCollectionName(collectionName.toLowerCase());
                            
                            // Set detected as true since server exists
                            server.setDetected(true);
                            
                            mcpServers.add(server);
                        }
                    }
                }
            }
        }
        
        return SUCCESS.toUpperCase();
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public List<EndpointShieldServer> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(List<EndpointShieldServer> mcpServers) {
        this.mcpServers = mcpServers;
    }

    public String fetchAgentLogs() {
        try {
            if (agentId == null || agentId.trim().isEmpty()) {
                addActionError("Agent ID is required");
                return ERROR.toUpperCase();
            }

            // If startTime and endTime are not provided, default to last 24 hours
            if (startTime == 0 && endTime == 0) {
                int now = (int) (System.currentTimeMillis() / 1000);
                endTime = now;
                startTime = now - (24 * 60 * 60); // 24 hours ago
            }

            // pageSize==0 means caller didn't send the field (Java int default); fall back to MAX_LOGS_LIMIT
            // to preserve the pre-pagination behaviour for callers that don't paginate.
            int effectivePageSize = (pageSize > 0) ? Math.min(pageSize, MAX_LOGS_LIMIT) : MAX_LOGS_LIMIT;

            // Base filter (no cursor) — used for totalCount
            Bson baseFilter = Filters.and(
                Filters.eq(EndpointShieldLog.AGENT_ID, agentId),
                Filters.gte(Log.TIMESTAMP, startTime),
                Filters.lte(Log.TIMESTAMP, endTime)
            );
            if (logKey != null && !logKey.isEmpty()) {
                baseFilter = Filters.and(baseFilter, Filters.eq("key", logKey));
            }

            // Add ObjectId cursor for paged requests — _id < afterId fetches older logs.
            // Using _id instead of timestamp avoids skipping logs that share the same second.
            Bson pagedFilter = baseFilter;
            if (afterId != null && !afterId.isEmpty()) {
                pagedFilter = Filters.and(baseFilter, Filters.lt("_id", new ObjectId(afterId)));
            }

            // Sort descending by _id (newest ObjectId = newest log)
            Bson sort = Sorts.descending("_id");
            Bson projection = Projections.include("log", "timestamp", "key", "agentId", "deviceId", "level");

            // Force agentId index to avoid collection scans on sparse keys (proxy-logs, installation-logs).
            // The agentId single-field index narrows the scan to this agent's documents; key/timestamp
            // filtering is then applied as residual predicates, which is fast enough for per-agent volumes.
            org.bson.Document hint = new org.bson.Document("agentId", -1);

            List<EndpointShieldLog> fetched = new ArrayList<>();
            EndpointShieldLogsDao.instance.getMCollection()
                .find(pagedFilter)
                .projection(projection)
                .sort(sort)
                .hint(hint)
                .limit(effectivePageSize + 1)
                .maxTime(30, java.util.concurrent.TimeUnit.SECONDS)
                .into(fetched);

            hasMore = fetched.size() > effectivePageSize;
            if (hasMore) {
                fetched = fetched.subList(0, effectivePageSize);
            }

            agentLogs = new ArrayList<>(fetched);
            // Only count on the first page — subsequent pages discard the value anyway.
            // Count with the same agentId hint as the find so it narrows to this agent's
            // documents (key/timestamp applied as residual) instead of scanning the timestamp
            // range index across every agent — that scan is why the count previously had to be
            // skipped for wide (e.g. all-time) ranges. Display-only and non-fatal: on timeout
            // totalCount = -1, which the UI renders as "unknown" while pagination still works.
            if (afterId == null || afterId.isEmpty()) {
                try {
                    totalCount = EndpointShieldLogsDao.instance.getMCollection()
                        .countDocuments(baseFilter, new com.mongodb.client.model.CountOptions()
                            .hint(hint)
                            .maxTime(10, java.util.concurrent.TimeUnit.SECONDS));
                } catch (Exception countErr) {
                    loggerMaker.errorAndAddToDb("Error counting endpoint shield logs: " + countErr.getMessage());
                    totalCount = -1;
                }
            }

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError("Error fetching agent logs: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    public String exportAgentLogs() {
        try {
            if (agentId == null || agentId.trim().isEmpty()) {
                addActionError("Agent ID is required");
                return ERROR.toUpperCase();
            }

            if (startTime == 0 && endTime == 0) {
                endTime = Context.now();
                startTime = endTime - (24 * 60 * 60);
            }

            Bson filter = Filters.and(
                Filters.eq(EndpointShieldLog.AGENT_ID, agentId),
                Filters.gte(Log.TIMESTAMP, startTime),
                Filters.lte(Log.TIMESTAMP, endTime)
            );
            if (logKey != null && !logKey.isEmpty()) {
                filter = Filters.and(filter, Filters.eq("key", logKey));
            }

            Bson sort = Sorts.descending("_id");
            Bson projection = Projections.include("log", "timestamp", "key", "agentId", "deviceId", "level");

            agentLogs = new ArrayList<>(EndpointShieldLogsDao.instance.findAll(
                filter, 0, MAX_EXPORT_LIMIT, sort, projection
            ));

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError("Error exporting agent logs: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    // Additional getters and setters
    public List<Log> getAgentLogs() {
        return agentLogs;
    }

    public void setAgentLogs(List<Log> agentLogs) {
        this.agentLogs = agentLogs;
    }

    public int getStartTime() {
        return startTime;
    }

    public void setStartTime(int startTime) {
        this.startTime = startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public void setEndTime(int endTime) {
        this.endTime = endTime;
    }

    public String getLogKey() {
        return logKey;
    }

    public void setLogKey(String logKey) {
        this.logKey = logKey;
    }

    public String getAfterId() {
        return afterId;
    }

    public void setAfterId(String afterId) {
        this.afterId = afterId;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    @Setter
    String username;

    public String fetchUserAnalysis() {
        if (this.username == null || this.username.isEmpty()) {
            addActionError("Username is required");
            return ERROR.toUpperCase();
        }

        userAnalysis = UserAnalysisDataDao.instance.findOne(
            Filters.and(
                Filters.eq(UserAnalysisData.USER_NAME, this.username)
            )
        );

        return SUCCESS.toUpperCase();
    }

}