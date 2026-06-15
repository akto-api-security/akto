import React, { useMemo } from "react";
import { Box, HorizontalGrid, VerticalStack, Text } from "@shopify/polaris";
import { getAgentLinkedComponents } from "./agenticPageBuilders";
import { findParentAgents } from "./AssetTopologyGraph";
import AssetTopologyGraph from "./AssetTopologyGraph";
import { RiskFactorRow } from "./RiskFactorRow";
import DetailGrid from "./DetailGrid";
import "../../../components/layouts/style.css";

const SEV_ORDER = { critical: 0, high: 1, medium: 2, low: 3 };

function computeAssetRiskFactors(asset) {
    const factors = [];
    const sevLabels = { critical: "Critical", high: "High", medium: "Medium", low: "Low" };

    for (const [sev, label] of Object.entries(sevLabels)) {
        const count = asset.violations?.[sev] || 0;
        if (count > 0) {
            factors.push({
                severity: sev,
                title: `${count} ${label} Violation${count > 1 ? "s" : ""}`,
                description: `Contains ${label.toLowerCase()} violations`,
                type: "violation",
            });
        }
    }

    if (asset.hasPersonalAccount) {
        factors.push({
            severity: "high",
            title: "Personal Account",
            description: "Contains personal account",
            type: "personal_account",
        });
    }

    if (asset.isMalicious) {
        factors.push({
            severity: "critical",
            title: "Malicious Skill",
            description: "Contains malicious skill",
            type: "malicious_skill",
        });
    }

    if (factors.length === 0) {
        factors.push({
            severity: "low",
            title: "Standard Risk Profile",
            description: "No elevated risk factors detected.",
            type: "normal",
        });
    }
    return factors.filter(f => f.type !== "normal");
}

export default function OverviewTab({ asset, onTabChange, assetDevices = {}, agenticTreeData = [], agenticFlatData = [], mcpComponentCount = 0 }) {
    const totalV = useMemo(() =>
        (asset.violations?.critical || 0) + (asset.violations?.high || 0) + (asset.violations?.medium || 0) + (asset.violations?.low || 0),
        [asset.violations]
    );

    const rawFactors = useMemo(() => computeAssetRiskFactors(asset), [asset]);
    const factors    = useMemo(() => [...rawFactors].sort((a, b) => (SEV_ORDER[a.severity] ?? 99) - (SEV_ORDER[b.severity] ?? 99)), [rawFactors]);

    const stats = useMemo(() => {
        const devices  = assetDevices[asset.id] || [];
        const devCount = devices.length;
        const children = asset.type === "AI Agent"
            ? getAgentLinkedComponents(asset, agenticTreeData, agenticFlatData)
            : [];
        const mcpCount = children.filter((c) => c.type === "MCP Server").length;

        if (asset.type === "AI Agent") return [
            { label: devCount  === 1 ? "Device"     : "Devices",     value: devCount },
            { label: mcpCount  === 1 ? "MCP Server" : "MCP Servers", value: mcpCount },
            { label: (asset.skillCount || 0) === 1 ? "Skill" : "Skills", value: asset.skillCount || 0 },
            { label: totalV    === 1 ? "Violation"  : "Violations",  value: totalV },
        ];
        if (asset.type === "MCP Server") {
            const agentCount = findParentAgents(asset, agenticFlatData).length;
            return [
                { label: devCount    === 1 ? "Device"     : "Devices",    value: devCount },
                { label: agentCount  === 1 ? "AI Agent"   : "AI Agents",  value: agentCount },
                { label: mcpComponentCount === 1 ? "Component" : "Components", value: mcpComponentCount },
                { label: totalV      === 1 ? "Violation"  : "Violations", value: totalV },
            ];
        }
        if (asset.type === "Skill") {
            const agentCount = findParentAgents(asset, agenticFlatData).length;
            return [
                { label: devCount   === 1 ? "Device"    : "Devices",    value: devCount },
                { label: agentCount === 1 ? "AI Agent"  : "AI Agents",  value: agentCount },
                { label: totalV     === 1 ? "Violation" : "Violations", value: totalV },
            ];
        }
        return [
            { label: devCount === 1 ? "Device"    : "Devices",    value: devCount },
            { label: totalV   === 1 ? "Violation" : "Violations", value: totalV },
        ];
    }, [asset, totalV, assetDevices, agenticTreeData, agenticFlatData, mcpComponentCount]);

    const assetDetails = useMemo(() => [
        { label: "AI Interactions",   value: asset.aiInteractions != null ? Number(asset.aiInteractions).toLocaleString("en-US") : "-" },
        { label: "Last Traffic Seen", value: asset.lastSeen || "-" },
        { label: "Group",             value: asset.groups?.[0]?.name || "-" },
    ], [asset]);

    return (
        <Box padding="4">
            <VerticalStack gap="5">
                <HorizontalGrid columns={stats.length} gap="3">
                    {stats.map(s => (
                        <VerticalStack gap="1" key={s.label}>
                            <Text variant="heading2xl" as="p">{s.value}</Text>
                            <Text variant="bodySm" color="subdued">{s.label}</Text>
                        </VerticalStack>
                    ))}
                </HorizontalGrid>

                <AssetTopologyGraph asset={asset} assetDevices={assetDevices} agenticTreeData={agenticTreeData} agenticFlatData={agenticFlatData} />

                {factors.length > 0 && (
                    <VerticalStack gap="3">
                        <Text variant="headingXs" color="subdued">Risk Analysis</Text>
                        <VerticalStack gap="2">
                            {factors.map((f, i) => {
                                let handleClick;
                                if (f.type === "violation") {
                                    handleClick = () => onTabChange?.(2);
                                } else if (f.type === "personal_account") {
                                    const devices = assetDevices[asset.id] || [];
                                    const firstDevice = devices[0];
                                    handleClick = firstDevice
                                        ? () => window.open(`/dashboard/observe/endpoints?device=${encodeURIComponent(firstDevice.deviceId)}`, "_blank")
                                        : () => window.open("/dashboard/observe/endpoints", "_blank");
                                } else if (f.type === "malicious_skill") {
                                    handleClick = () => window.open(`/dashboard/observe/agentic-assets?asset=${encodeURIComponent(asset.name || asset.id)}`, "_blank");
                                } else {
                                    handleClick = undefined;
                                }
                                return <RiskFactorRow key={i} factor={f} onClick={handleClick} />;
                            })}
                        </VerticalStack>
                    </VerticalStack>
                )}

                <DetailGrid heading="Asset Details" items={assetDetails} columns={3} />
            </VerticalStack>
        </Box>
    );
}
