import React, { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Box, Text, VerticalStack } from "@shopify/polaris";
import AskBar from "@/apps/dashboard/components/shared/AskBar";
import SummaryCardInfo from "@/apps/dashboard/components/shared/SummaryCardInfo";
import CalloutStrip from "@/apps/dashboard/components/shared/CalloutStrip";
import ItemListCard from "@/apps/dashboard/components/shared/ItemListCard";
import PeopleList from "@/apps/dashboard/components/shared/PeopleList";
import DetailGrid from "./DetailGrid";
import { RiskFactorRow, computeAssetRiskFactors } from "./RiskFactorRow";
import { getRiskLevel } from "./agenticPageBuilders";
import { CATEGORY_LABELS, summarizeHostSegment } from "./inventoryCategories";
import formatDisplayName from "./mcpClientHelper";
import api from "../api";
import { fetchRecentViolations, guardrailActivityPath } from "./agenticObserveApi";
import FootprintGraph from "./FootprintGraph";
import { ASSET_FOOTPRINT_LEGEND, buildAssetFootprint } from "./assetFootprint";
import func from "@/util/func";

// ─── AssetOverview ────────────────────────────────────────────────────────────
// Main view of the AI inventory asset panel (AgenticAssetFlyout): Ask Atlas, key stats, violations,
// tool capabilities, risk factors, details and top employees. Details we don't collect are left out.
// Violation links open the Guardrail activity page for the matching time range.

const ASK_SUGGESTIONS = ["Who installed this first?", "What did it access this week?", "Is there an approved alternative?"];
const TOP_EMPLOYEES = 5;
const DEVICE_SAMPLE = 200;
const RISK_COLORS = { CRITICAL: "critical", HIGH: "critical", MEDIUM: "warning" };

const TOOLS_SECTION = {
    "AI Agent": { title: "Tool capabilities", emptyText: "No built-in tools discovered yet." },
    "MCP Server": { title: "Tools exposed", emptyText: "No tools discovered yet." },
};

const countText = (n) => (n > 0 ? String(n) : null);
const capitalize = (s) => s.charAt(0).toUpperCase() + s.slice(1);
// Only the details we have a value for; rows without one are dropped.
function detailsOf(asset) {
    const accounts = { label: "Accounts", value: asset.hasPersonalAccount ? "Personal accounts on some devices" : null };
    const byType = {
        "AI Agent": [
            { label: "MCP servers attached", value: countText((asset.mcpServers || []).length) },
            { label: "Skills installed", value: countText(asset.skillCount) },
            { label: "Plugins", value: countText((asset.pluginNames || []).length) },
            accounts,
        ],
        "MCP Server": [
            { label: "Transport", value: asset.hasLocalMcpServer ? "Local" : null },
            { label: "Attached to", value: summarizeHostSegment(asset.hostNames, formatDisplayName) },
        ],
        "LLM": [
            { label: "Browsers", value: summarizeHostSegment(asset.hostNames, capitalize) },
            accounts,
        ],
        "Plugin": [
            { label: "Version", value: asset.pluginVersion },
            { label: "Marketplace", value: asset.pluginMarketplace },
            { label: "AI agent", value: asset.pluginParentAgent },
        ],
        "Skill": [
            { label: "Bundled by plugin", value: asset.owningPluginName },
        ],
    };
    return [
        { label: "Category", value: CATEGORY_LABELS[asset.inventoryCategory] || asset.type },
        ...(byType[asset.type] || []),
        { label: "AI interactions", value: asset.aiInteractions != null ? Number(asset.aiInteractions).toLocaleString("en-US") : null },
        { label: "Description", value: asset.description },
        { label: "Last seen", value: asset.lastSeen },
    ].filter((d) => d.value);
}

// The asset's most recent devices (same paginated devices API the Devices page uses) — shared by
// the AI footprint graph and Top employees. null while loading.
function useAssetDevices(asset, enrichMaps, startTimestamp, endTimestamp) {
    const [result, setResult] = useState(null);
    useEffect(() => {
        if (!asset?.collectionIds?.length) { setResult({ devices: [], total: 0 }); return; }
        let cancelled = false;
        setResult(null);
        const { trafficMap, riskScoreMap } = enrichMaps || {};
        api.fetchAgenticAssetDevicesPage({
            apiCollectionIds: asset.collectionIds, skip: 0, limit: DEVICE_SAMPLE, sortOrder: -1,
            trafficMap, riskScoreMap, startTimestamp, endTimestamp,
        }).then(({ devices, total }) => {
            if (!cancelled) setResult({ devices: devices || [], total: total || 0 });
        }).catch(() => { if (!cancelled) setResult({ devices: [], total: 0 }); });
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [asset?.id, asset?.collectionIds]);
    return result;
}

function topEmployeesOf(devices) {
    const byUser = new Map();
    devices.forEach((d) => {
        if (!d.username || d.username === "-") return;
        const u = byUser.get(d.username) || { devices: 0, lastSeen: 0 };
        byUser.set(d.username, { devices: u.devices + 1, lastSeen: Math.max(u.lastSeen, d.lastSeenEpoch || 0) });
    });
    return [...byUser.entries()]
        .sort((a, b) => b[1].devices - a[1].devices || b[1].lastSeen - a[1].lastSeen)
        .slice(0, TOP_EMPLOYEES)
        .map(([name, u]) => ({
            name,
            secondary: `${u.devices} device${u.devices === 1 ? "" : "s"}`,
            trailing: u.lastSeen > 0 ? `Last used ${func.prettifyEpoch(u.lastSeen)}` : null,
            trailingTooltip: u.lastSeen > 0 ? func.epochToDateTime(u.lastSeen) : null,
        }));
}

// Guardrail violations in the last 7 days by severity, regardless of the page's date range. null while loading.
function useRecentViolations(asset) {
    const [violations, setViolations] = useState(null);
    useEffect(() => {
        let cancelled = false;
        setViolations(null);
        fetchRecentViolations(asset)
            .then((v) => { if (!cancelled) setViolations(v); })
            .catch(() => { if (!cancelled) setViolations({}); });
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [asset?.id, asset?.collectionIds]);
    return violations;
}

function Section({ title, children }) {
    return (
        <VerticalStack gap="3">
            <Text as="h3" variant="headingSm">{title}</Text>
            {children}
        </VerticalStack>
    );
}

export default function AssetOverview({ asset, enrichMaps, startTimestamp, endTimestamp }) {
    const navigate = useNavigate();
    const assetDevices = useAssetDevices(asset, enrichMaps, startTimestamp, endTimestamp);
    const violations = useRecentViolations(asset);
    const topEmployees = useMemo(() => (assetDevices ? topEmployeesOf(assetDevices.devices) : null), [assetDevices]);
    const recentViolations = violations ? Object.values(violations).reduce((sum, n) => sum + n, 0) : 0;
    const buildFootprint = useCallback(({ fullscreen, expanded }) => buildAssetFootprint({
        asset, devices: assetDevices.devices, deviceTotal: assetDevices.total, violations, fullscreen, expanded,
    }), [asset, assetDevices, violations]);
    const riskFactors = useMemo(() => computeAssetRiskFactors(asset), [asset]);
    const details = useMemo(() => detailsOf(asset), [asset]);
    const chatData = useMemo(() => ({
        assetName: asset.name, assetType: asset.type, collectionId: asset.collectionIds || [], assetTagValue: asset.assetTagValue,
    }), [asset.name, asset.type, asset.collectionIds, asset.assetTagValue]);

    const riskLevel = asset.riskScore ? getRiskLevel(asset.riskScore) : null;
    const stats = [
        { title: "Devices", data: asset.endpointsCount ?? 0 },
        { title: "Employees", data: asset.employeesCount ?? 0 },
        ...(riskLevel ? [{ title: "Risk", data: func.toSentenceCase(riskLevel), color: RISK_COLORS[riskLevel] }] : []),
        ...(asset.discoveredAt > 0 ? [{ title: "First seen", data: func.prettifyEpoch(asset.discoveredAt) }] : []),
    ];

    const toolsSection = TOOLS_SECTION[asset.type];
    const toolItems = (asset.tools || []).map((name) => ({ id: name, title: name }));

    return (
        <Box padding="5">
            <VerticalStack gap="5">
                <AskBar
                    key={asset.id}
                    label={`Ask Atlas about ${asset.name}`}
                    placeholder={`Ask anything about ${asset.name}`}
                    suggestions={ASK_SUGGESTIONS}
                    chatScope="asset"
                    chatData={chatData}
                />

                <SummaryCardInfo summaryItems={stats} />

                {recentViolations > 0 && (
                    <CalloutStrip tone="critical" action={{ content: "View in guardrail activity", onAction: () => navigate(guardrailActivityPath({ range: "last7days" })) }}>
                        <Text as="span" fontWeight="semibold">{recentViolations} guardrail violation{recentViolations === 1 ? "" : "s"}</Text> in the last 7 days
                    </CalloutStrip>
                )}

                {assetDevices && violations && (
                    <FootprintGraph key={asset.id} title="AI footprint" buildModel={buildFootprint} legend={ASSET_FOOTPRINT_LEGEND} />
                )}

                {toolsSection && (
                    <Section title={toolsSection.title}>
                        <ItemListCard
                            items={toolItems}
                            emptyText={toolsSection.emptyText}
                            limit={6}
                        />
                    </Section>
                )}

                {riskFactors.length > 0 && (
                    <Section title="Risk factors">
                        <VerticalStack gap="2">
                            {riskFactors.map((f) => (
                                <RiskFactorRow key={f.title} factor={f} onClick={f.type === "violation" ? () => navigate(guardrailActivityPath({ startTimestamp, endTimestamp })) : undefined} />
                            ))}
                        </VerticalStack>
                    </Section>
                )}

                <Section title="Details">
                    <DetailGrid items={details} labelWidth="160px" valueMaxWidth="460px" valueWeight="regular" />
                </Section>

                <Section title="Top employees">
                    {topEmployees === null
                        ? <Text variant="bodySm" color="subdued">Loading…</Text>
                        : <PeopleList people={topEmployees} emptyText="No employee data for this asset's devices." />}
                </Section>
            </VerticalStack>
        </Box>
    );
}
