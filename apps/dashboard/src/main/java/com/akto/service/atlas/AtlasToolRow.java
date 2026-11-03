package com.akto.service.atlas;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One discovered AI tool (agent, MCP server, browser AI app, skill or plugin) rolled up across devices. */
@Getter
@Setter
public class AtlasToolRow {
    private String groupKey;
    private String rowType;
    private String name;
    private String clientType;
    // Canonical vendor used by the vendor allowlist; null when the tool has no vendor host.
    private String vendorName;
    private String riskReason;
    private double riskScore;
    private int firstSeen;
    private int lastSeen;
    private boolean personal;
    private boolean localMcp;
    private boolean misconfigured;
    private boolean malicious;
    private int misconfiguredDevices;
    private int personalDevices;
    // The devices behind the two counts, so a device scope can narrow them.
    private Set<String> misconfiguredDeviceIds = new HashSet<>();
    private Set<String> personalDeviceIds = new HashSet<>();
    // Names guardrails matches an AI agent on; empty for every other tool type.
    private List<String> rawValues = new ArrayList<>();
    private Set<String> deviceIds = new HashSet<>();
    // MCP server name -> devices where this agent has it configured.
    private Map<String, Set<String>> mcpDevices = new HashMap<>();
    // A few collections whose observed tools describe this tool's capabilities; empty for tools without any.
    private List<Integer> capabilityCollectionIds = new ArrayList<>();
}
