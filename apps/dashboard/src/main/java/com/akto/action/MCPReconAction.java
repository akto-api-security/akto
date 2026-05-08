package com.akto.action;

import com.akto.dao.McpReconRequestDao;
import com.akto.dao.context.Context;
import com.akto.dto.McpReconRequest;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.opensymphony.xwork2.Action;

public class MCPReconAction extends UserAction {

    private String ipRange;
    private String requestId;

    private static final LoggerMaker loggerMaker = new LoggerMaker(MCPReconAction.class, LoggerMaker.LogDb.DASHBOARD);

    public String initiateMCPRecon() {
        try {
            if (ipRange == null || ipRange.isEmpty()) {
                loggerMaker.error("IP ranges are empty or null", LogDb.DASHBOARD);
                return Action.ERROR.toUpperCase();
            }

            loggerMaker.info("Initiating MCP Recon for IP ranges: " + ipRange, LogDb.DASHBOARD);

            // Create new McpReconRequest with pending status
            McpReconRequest reconRequest = new McpReconRequest(
                Context.accountId.get(),
                ipRange,
                Constants.STATUS_PENDING,
                Context.now()
            );

            // Insert the request into the mcp_recon_requests collection
            McpReconRequestDao.instance.insertOne(reconRequest);
            return Action.SUCCESS.toUpperCase();

        } catch (Exception e) {
            loggerMaker.error("Error while initiating MCP Recon. Error: " + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
            return Action.ERROR.toUpperCase();
        }
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getIpRange() {
        return ipRange;
    }

    public void setIpRange(String ipRange) {
        this.ipRange = ipRange;
    }
}