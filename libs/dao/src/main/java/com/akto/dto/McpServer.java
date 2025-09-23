package com.akto.dto;

import com.akto.mcp.McpSchema.Resource;
import com.akto.mcp.McpSchema.Tool;
import com.akto.mcp.McpSchema.Prompt;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * DTO representing an MCP (Model Context Protocol) server
 */
@Getter
@Setter
@AllArgsConstructor
public class McpServer {
    
    private String ip;
    private int port;
    private String url;
    private boolean verified;
    private String detectionMethod;
    private String timestamp;
    private String type;
    private String endpoint;
    private String protocolVersion;
    private Map<String, Object> serverInfo;
    private Map<String, Object> capabilities;
    private List<Tool> tools;
    private List<Resource> resources;
    private List<Prompt> prompts;

    public McpServer() {}

}