package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.McpReconResult;

/**
 * DAO for MCP Recon Results
 * Handles database operations for storing MCP server discovery results
 */
public class McpReconResultDao extends AccountsContextDao<McpReconResult> {

    public static final McpReconResultDao instance = new McpReconResultDao();

    private McpReconResultDao() {}

    @Override
    public String getCollName() {
        return "mcp_recon_results";
    }

    @Override
    public Class<McpReconResult> getClassT() {
        return McpReconResult.class;
    }

    /**
     * Create indexes for the collection
     * Indexes on: mcp_recon_request_id, ip, port, url, verified, detection_method, discovered_at
     */
    public void createIndicesIfAbsent() {


        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        }

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { McpReconResult.MCP_RECON_REQUEST_ID }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { McpReconResult.VERIFIED, McpReconResult.IP }, false);
    }


}