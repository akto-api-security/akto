package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

/**
 * DTO representing the result of an MCP reconnaissance scan
 */
@Getter
@Setter
@AllArgsConstructor
public class McpReconResult {

    public static final String ID = "_id";
    private ObjectId _id;

    public static final String MCP_RECON_REQUEST_ID = "mcpReconRequestId";
    private ObjectId mcpReconRequestId;

    // McpServer fields
    public static final String IP = "ip";
    private String ip;

    public static final String PORT = "port";
    private int port;

    public static final String URL = "url";
    private String url;

    public static final String VERIFIED = "verified";
    private boolean verified;

    public static final String DETECTION_METHOD = "detectionMethod";
    private String detectionMethod;

    public static final String TIMESTAMP = "timestamp";
    private String timestamp;

    public static final String TYPE = "type";
    private String type;

    public static final String ENDPOINT = "endpoint";
    private String endpoint;

    public static final String PROTOCOL_VERSION = "protocolVersion";
    private String protocolVersion;

    public static final String SERVER_INFO = "serverInfo";
    private Map<String, Object> serverInfo;

    public static final String CAPABILITIES = "capabilities";
    private Map<String, Object> capabilities;

    public static final String TOOLS = "tools";
    private List<Map<String, Object>> tools;

    public static final String RESOURCES = "resources";
    private List<Map<String, Object>> resources;

    public static final String PROMPTS = "prompts";
    private List<Map<String, Object>> prompts;

    public McpReconResult() {
    }

    public McpReconResult(ObjectId mcpReconRequestId, String ip, int port, String url, boolean verified, String detectionMethod, String timestamp, String type, String endpoint, String protocolVersion, Map<String, Object> serverInfo, Map<String, Object> capabilities, List<Map<String, Object>> tools, List<Map<String, Object>> resources, List<Map<String, Object>> prompts) {
        this.mcpReconRequestId = mcpReconRequestId;
        this.ip = ip;
        this.port = port;
        this.url = url;
        this.verified = verified;
        this.detectionMethod = detectionMethod;
        this.timestamp = timestamp;
        this.type = type;
        this.endpoint = endpoint;
        this.protocolVersion = protocolVersion;
        this.serverInfo = serverInfo;
        this.capabilities = capabilities;
        this.tools = tools;
        this.resources = resources;
        this.prompts = prompts;
    }
}