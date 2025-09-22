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

    public McpScanResult() {}

}