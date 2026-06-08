import React, { useMemo } from "react";
import { Box, HorizontalGrid, VerticalStack, Text } from "@shopify/polaris";
import { getAgentLinkedComponents, getRiskLabel } from "./agenticPageBuilders";
import AssetTopologyGraph from "./AssetTopologyGraph";
import { RiskFactorRow } from "./RiskFactorRow";
import DetailGrid from "./DetailGrid";
import "../../../components/layouts/style.css";

const SEV_ORDER = { critical: 0, high: 1, medium: 2, low: 3 };

function computeAssetRiskFactors(asset) {
    const factors = [];
    if ((asset.violations?.critical || 0) > 0) {
        factors.push({ severity: "critical", title: `${asset.violations.critical} Critical Violation${asset.violations.critical > 1 ? "s" : ""}`, description: "Active critical policy violations indicate potential data exfiltration or unauthorized system access." });
    }
    if ((asset.violations?.high || 0) > 0) {
        factors.push({ severity: "high", title: `${asset.violations.high} High-Severity Violation${asset.violations.high > 1 ? "s" : ""}`, description: "High-severity violations require investigation and may indicate significant policy breaches." });
    }
    if ((asset.mcpServers || []).length >= 3) {
        factors.push({ severity: "medium", title: `High Integration Complexity (${asset.mcpServers.length} MCP servers)`, description: `${asset.mcpServers.length} external system integrations expand the blast radius of any compromised agent session.` });
    }
    if (factors.length === 0) {
        factors.push({ severity: "low", title: "Standard Risk Profile", description: "No elevated risk factors detected. Score reflects baseline activity levels." });
    }
    return factors;
}

function getAssetNarrative(asset) {
    const score = asset.riskScore != null ? Math.round(asset.riskScore * 10) / 10 : null;
    const label = getRiskLabel(asset.riskScore)?.toLowerCase();
    const parts = [];

    if ((asset.violations?.critical || 0) > 0)
        parts.push(`${asset.violations.critical} critical violation${asset.violations.critical > 1 ? "s" : ""} indicate unauthorized data transmission or credential exposure`);
    if ((asset.violations?.high || 0) > 0)
        parts.push(`${asset.violations.high} high-severity violation${asset.violations.high > 1 ? "s" : ""} require immediate investigation`);
    if ((asset.skillCount || 0) > 80)
        parts.push(`${asset.skillCount} exposed skills significantly expand the attack surface`);

    if (parts.length === 0)
        return `${asset.name} shows a standard activity profile with no elevated signals. Score reflects baseline activity patterns.`;

    return `${asset.name} carries a risk score of ${score}/5 (${label}) because ${parts.join(", and ")}. ${(asset.violations?.critical || 0) > 0 ? "Immediate action is recommended." : "Monitor closely and review permissions."}`;
}

export default function OverviewTab({ asset, onTabChange, assetDevices = {}, agenticTreeData = [], agenticFlatData = [], mcpComponentCount = 0 }) {
    const totalV = useMemo(() =>
        (asset.violations?.critical || 0) + (asset.violations?.high || 0) + (asset.violations?.medium || 0) + (asset.violations?.low || 0),
        [asset.violations]
    );

    const rawFactors = useMemo(() => computeAssetRiskFactors(asset), [asset]);
    const factors    = useMemo(() => [...rawFactors].sort((a, b) => (SEV_ORDER[a.severity] ?? 99) - (SEV_ORDER[b.severity] ?? 99)), [rawFactors]);
    const narrative  = useMemo(() => getAssetNarrative(asset), [asset]);

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
        if (asset.type === "MCP Server") return [
            { label: devCount          === 1 ? "Device"     : "Devices",    value: devCount },
            { label: mcpComponentCount === 1 ? "Component"  : "Components", value: mcpComponentCount },
            { label: totalV            === 1 ? "Violation"  : "Violations", value: totalV },
        ];
        if (asset.type === "Skill") return [
            { label: devCount === 1 ? "Device" : "Devices",       value: devCount },
            { label: totalV   === 1 ? "Violation" : "Violations", value: totalV },
        ];
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

                <VerticalStack gap="3">
                    <Text variant="headingXs" color="subdued">Risk Analysis</Text>
                    <Text variant="bodySm">{narrative}</Text>
                    <VerticalStack gap="2">
                        {factors.map((f, i) => {
                            const targetTab = f.title.toLowerCase().includes("violation") ? 2 : 1;
                            return <RiskFactorRow key={i} factor={f} onClick={() => onTabChange?.(targetTab)} />;
                        })}
                    </VerticalStack>
                </VerticalStack>

                <DetailGrid heading="Asset Details" items={assetDetails} columns={3} />
            </VerticalStack>
        </Box>
    );
}
