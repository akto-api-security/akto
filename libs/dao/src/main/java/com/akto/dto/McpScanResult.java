package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * DTO representing the result of an MCP reconnaissance scan
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class McpScanResult {

    private String scanCompleted;
    private String ipRange;
    private int ipsScanned;
    private int portsChecked;
    private int serversFound;
    private List<McpServer> servers;
    private double scanTimeSeconds;
    private Map<String, Object> performance;

    /**
     * Builder pattern for creating McpScanResult instances
     */
    public static class Builder {
        private McpScanResult result = new McpScanResult();

        public Builder scanCompleted(String scanCompleted) {
            result.scanCompleted = scanCompleted;
            return this;
        }

        public Builder ipRange(String ipRange) {
            result.ipRange = ipRange;
            return this;
        }

        public Builder ipsScanned(int ipsScanned) {
            result.ipsScanned = ipsScanned;
            return this;
        }

        public Builder portsChecked(int portsChecked) {
            result.portsChecked = portsChecked;
            return this;
        }

        public Builder serversFound(int serversFound) {
            result.serversFound = serversFound;
            return this;
        }

        public Builder servers(List<McpServer> servers) {
            result.servers = servers;
            return this;
        }

        public Builder scanTimeSeconds(double scanTimeSeconds) {
            result.scanTimeSeconds = scanTimeSeconds;
            return this;
        }

        public Builder performance(Map<String, Object> performance) {
            result.performance = performance;
            return this;
        }

        public McpScanResult build() {
            return result;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}