package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * DTO representing an MCP (Model Context Protocol) server
 */
@Getter
@Setter
@NoArgsConstructor
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
    private List<Map<String, Object>> tools;
    private List<Map<String, Object>> resources;
    private List<Map<String, Object>> prompts;

    /**
     * Builder pattern for creating McpServer instances
     */
    public static class Builder {
        private McpServer server = new McpServer();

        public Builder ip(String ip) {
            server.ip = ip;
            return this;
        }

        public Builder port(int port) {
            server.port = port;
            return this;
        }

        public Builder url(String url) {
            server.url = url;
            return this;
        }

        public Builder verified(boolean verified) {
            server.verified = verified;
            return this;
        }

        public Builder detectionMethod(String detectionMethod) {
            server.detectionMethod = detectionMethod;
            return this;
        }

        public Builder timestamp(String timestamp) {
            server.timestamp = timestamp;
            return this;
        }

        public Builder type(String type) {
            server.type = type;
            return this;
        }

        public Builder endpoint(String endpoint) {
            server.endpoint = endpoint;
            return this;
        }

        public Builder protocolVersion(String protocolVersion) {
            server.protocolVersion = protocolVersion;
            return this;
        }

        public Builder serverInfo(Map<String, Object> serverInfo) {
            server.serverInfo = serverInfo;
            return this;
        }

        public Builder capabilities(Map<String, Object> capabilities) {
            server.capabilities = capabilities;
            return this;
        }

        public Builder tools(List<Map<String, Object>> tools) {
            server.tools = tools;
            return this;
        }

        public Builder resources(List<Map<String, Object>> resources) {
            server.resources = resources;
            return this;
        }

        public Builder prompts(List<Map<String, Object>> prompts) {
            server.prompts = prompts;
            return this;
        }

        public McpServer build() {
            return server;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}