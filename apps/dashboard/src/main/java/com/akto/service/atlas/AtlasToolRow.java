package com.akto.service.atlas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
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
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
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
    @Builder.Default
    private Set<String> misconfiguredDeviceIds = new HashSet<>();
    @Builder.Default
    private Set<String> personalDeviceIds = new HashSet<>();
    // Names guardrails matches an AI agent on; empty for every other tool type.
    @Builder.Default
    private List<String> rawValues = new ArrayList<>();
    @Builder.Default
    private Set<String> deviceIds = new HashSet<>();
    // MCP server name -> devices where this agent has it configured.
    @Builder.Default
    private Map<String, Set<String>> mcpDevices = new HashMap<>();
    // A few collections whose observed tools describe this tool's capabilities; empty for tools without any.
    @Builder.Default
    private List<Integer> capabilityCollectionIds = new ArrayList<>();
    // Malicious skills only: the collections they run in, so a decision reaches their api_info rows.
    @Builder.Default
    private List<Integer> collectionIds = new ArrayList<>();
    // Device -> last traffic of this tool on it, for the cards that look at recent use only.
    @Builder.Default
    private Map<String, Integer> deviceLastSeen = new HashMap<>();
}
