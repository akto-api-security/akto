package com.akto.action.monitoring;

import com.akto.action.UserAction;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.monitoring.EndpointShieldServer;
import com.akto.dto.monitoring.EndpointShieldLog;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.dto.Log;
import com.mongodb.client.model.Filters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

public class EndpointShieldAgentAction extends UserAction {
    private static final int MAX_LOGS_LIMIT = 10000; // Maximum logs to fetch from database
    
    private String agentId;
    private String deviceId;
    private List<EndpointShieldServer> mcpServers;
    private List<Log> agentLogs;
    private int startTime;
    private int endTime;


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
                            
                            // Set collectionName as name + "." + mcpServerName
                            server.setCollectionName(moduleInfo.getName() + "." + serverName);
                            
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
            
            LoggerMaker loggerMaker = new LoggerMaker(EndpointShieldAgentAction.class, LogDb.ENDPOINT_SHIELD);
            agentLogs = loggerMaker.fetchLogRecords(startTime, endTime, LogDb.ENDPOINT_SHIELD);
            
            // Filter logs by agentId and apply sorting with limit
            agentLogs = agentLogs.stream()
                .filter(log -> {
                    if (log instanceof EndpointShieldLog) {
                        EndpointShieldLog esLog = (EndpointShieldLog) log;
                        return agentId.equals(esLog.getAgentId());
                    }
                    return false;
                })
                .sorted(Comparator.comparing(Log::getTimestamp).reversed()) // Sort by timestamp descending (newest first)
                .limit(MAX_LOGS_LIMIT) // Apply upper cap
                .collect(java.util.stream.Collectors.toList());
                
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError("Error fetching agent logs: " + e.getMessage());
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
}