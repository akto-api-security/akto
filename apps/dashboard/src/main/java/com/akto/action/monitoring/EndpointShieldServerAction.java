package com.akto.action.monitoring;

import com.akto.action.UserAction;
import com.akto.dao.monitoring.EndpointShieldServerDao;
import com.akto.dto.monitoring.EndpointShieldServer;
import com.akto.dto.monitoring.EndpointShieldLog;
import com.akto.util.EndpointShieldServerSampleDataUtil;
import com.akto.util.EndpointShieldLogsSampleDataUtil;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.dto.Log;
import com.mongodb.client.model.Filters;

import java.util.List;

public class EndpointShieldServerAction extends UserAction {
    private String agentId;
    private String deviceId;
    private List<EndpointShieldServer> mcpServers;
    private List<Log> agentLogs;
    private int startTime;
    private int endTime;

    @Override
    public String execute() {
        return SUCCESS;
    }

    public String getMcpServersByAgent() {
        if (agentId != null && deviceId != null) {
            mcpServers = EndpointShieldServerDao.instance.findByAgentIdAndDeviceId(agentId, deviceId);
        } else if (agentId != null) {
            mcpServers = EndpointShieldServerDao.instance.findByAgentId(agentId);
        } else if (deviceId != null) {
            mcpServers = EndpointShieldServerDao.instance.findByDeviceId(deviceId);
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
            
            LoggerMaker loggerMaker = new LoggerMaker(EndpointShieldServerAction.class, LogDb.ENDPOINT_SHIELD);
            agentLogs = loggerMaker.fetchLogRecords(startTime, endTime, LogDb.ENDPOINT_SHIELD);
            
            // Filter logs by agentId if needed (since the DAO query might return all logs)
            agentLogs = agentLogs.stream()
                .filter(log -> {
                    if (log instanceof EndpointShieldLog) {
                        EndpointShieldLog esLog = (EndpointShieldLog) log;
                        return agentId.equals(esLog.getAgentId());
                    }
                    return false;
                })
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