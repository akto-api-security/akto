package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.McpReconRequest;

/**
 * DAO for MCP Recon Requests
 * Handles database operations for MCP reconnaissance scan requests
 */
public class McpReconRequestDao extends AccountsContextDao<McpReconRequest> {

    public static final McpReconRequestDao instance = new McpReconRequestDao();

    private McpReconRequestDao() {}

    @Override
    public String getCollName() {
        return "mcp_recon_requests";
    }

    @Override
    public Class<McpReconRequest> getClassT() {
        return McpReconRequest.class;
    }


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


        MCollection.createIndexIfAbsent(getDBName(), getCollName(),new String[] {  McpReconRequest.STATUS, McpReconRequest.ACCOUNT_ID }, false);

        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
                new String[] { McpReconRequest.ACCOUNT_ID, McpReconRequest.STATUS, McpReconRequest.CREATED_AT }, false);
    }
}