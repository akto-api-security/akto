package com.akto.dao;

import com.mongodb.BasicDBObject;
import java.util.List;
import java.util.Map;

/**
 * DAO for MCP Recon Results
 * Handles database operations for storing MCP server discovery results
 */
public class McpReconResultDao extends AccountsContextDao<BasicDBObject> {

    public static final McpReconResultDao instance = new McpReconResultDao();
    
    private McpReconResultDao() {}
    
    @Override
    public String getCollName() {
        return "mcp_recon_results";
    }
    
    @Override
    public Class<BasicDBObject> getClassT() {
        return BasicDBObject.class;
    }
    
    /**
     * Create indexes for the collection
     * Indexes on: mcp_recon_request_id, ip, port, url, verified, detection_method, discovered_at
     */
    public void createIndicesIfAbsent() {
        // Single field indexes
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), 
            new String[] { Fields.MCP_RECON_REQUEST_ID }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), 
            new String[] { Fields.IP }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), 
            new String[] { Fields.PORT }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), 
            new String[] { Fields.URL }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), 
            new String[] { Fields.VERIFIED }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), 
            new String[] { Fields.DETECTION_METHOD }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), 
            new String[] { Fields.DISCOVERED_AT }, false);
        
        // Compound indexes for common queries
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), 
            new String[] { Fields.MCP_RECON_REQUEST_ID, Fields.VERIFIED }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), 
            new String[] { Fields.IP, Fields.PORT }, true);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), 
            new String[] { Fields.MCP_RECON_REQUEST_ID, Fields.DISCOVERED_AT }, false);
    }
    
    /**
     * Insert a server discovery result
     */
    public void insertOne(Map<String, Object> serverData) {
        BasicDBObject document = new BasicDBObject(serverData);
        super.insertOne(document);
    }
    
    /**
     * Batch insert multiple server discovery results
     */
    public void insertManyServerResults(List<BasicDBObject> serverDataList) {
        if (serverDataList == null || serverDataList.isEmpty()) {
            return;
        }
        super.insertMany(serverDataList);
    }
    
    /**
     * Field names for the collection
     */
    public static class Fields {
        public static final String ID = "_id";
        public static final String MCP_RECON_REQUEST_ID = "mcp_recon_request_id";
        public static final String IP = "ip";
        public static final String PORT = "port";
        public static final String URL = "url";
        public static final String VERIFIED = "verified";
        public static final String DETECTION_METHOD = "detection_method";
        public static final String TIMESTAMP = "timestamp";
        public static final String TYPE = "type";
        public static final String ENDPOINT = "endpoint";
        public static final String PROTOCOL_VERSION = "protocol_version";
        public static final String SERVER_INFO = "server_info";
        public static final String CAPABILITIES = "capabilities";
        public static final String TOOLS = "tools";
        public static final String RESOURCES = "resources";
        public static final String PROMPTS = "prompts";
        public static final String DISCOVERED_AT = "discovered_at";
    }
}