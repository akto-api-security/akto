import React, { useState, useMemo, useCallback, useEffect } from "react";
import { Tabs, Box, VerticalStack, Text, Divider } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import AgenticFlyoutShell from "./AgenticFlyoutShell";
import AiChatSection from "./AiChatSection";
import { buildAgentInlineTopologyComponents, buildAgentBuiltinToolsFromStis, countAgentComponentsTab } from "./agenticPageBuilders";
import { RiskScoreCellRenderer } from "./AgenticCellRenderers";
import agenticObserveApi, { buildAgenticObserveChatMetadata, selectConfigViolationRows, summarizeViolations } from "./agenticObserveApi";
import OverviewTab from "./OverviewTab";
import ViolationsTab from "./ViolationsTab";
import McpComponentsView from "./McpComponentsView";
import AgentComponentsView from "./AgentComponentsView";
import SkillComponentsView from "./SkillComponentsView";
import "../../../components/layouts/style.css";

// ─── Devices tab (small, kept inline) ────────────────────────────────────────

const DEVICES_COL_DEFS = [
    { field: "username", headerName: "User",       flex: 1,  minWidth: 120, cellStyle: { display: "flex", alignItems: "center" }, valueFormatter: p => p.value || "-" },
    { field: "riskScore", headerName: "Risk Score", width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: RiskScoreCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "lastSeen", headerName: "Last Seen",  width: 130, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", color: "#6D7175" }, valueFormatter: p => p.value || "-", comparator: (a, b, nodeA, nodeB) => (nodeA?.data?.lastSeenEpoch || 0) - (nodeB?.data?.lastSeenEpoch || 0) },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

function DevicesTab({ asset, assetDevices = {} }) {
    const devices = useMemo(() => assetDevices[asset.id] || [], [asset.id, assetDevices]);

    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        const deviceId = e.data.deviceId || e.data.endpoint;
        window.open(`/dashboard/observe/endpoints?device=${encodeURIComponent(deviceId)}`, "_blank");
    }, []);

    if (devices.length === 0) {
        return (
            <Box padding="8">
                <VerticalStack gap="1" inlineAlign="center">
                    <Text variant="bodySm" fontWeight="semibold">No devices found</Text>
                    <Text variant="bodySm" color="subdued">This asset hasn't been observed on any device.</Text>
                </VerticalStack>
            </Box>
        );
    }

    return (
        <AgGridTable
            rowData={devices}
            columnDefs={DEVICES_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={handleRowClick}
            getRowStyle={() => ({ cursor: "pointer" })}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search devices..."
            pagination
            paginationPageSize={20}
            sideBar={{ toolPanels: ["columns", "filters"], defaultToolPanel: null }}
            domLayout="normal"
        />
    );
}

// ─── Components tab router ────────────────────────────────────────────────────

function AgenticComponentsTab({ asset, onNavChange, onNavigateToAsset, agenticFlatData = [], configViolations, configRows }) {
    if (asset.type === "MCP Server") return <McpComponentsView asset={asset} onNavChange={onNavChange} />;
    if (asset.type === "AI Agent")   return <AgentComponentsView asset={asset} onNavChange={onNavChange} onNavigateToAsset={onNavigateToAsset} agenticFlatData={agenticFlatData} configViolations={configViolations} configRows={configRows} />;
    // Skills: fetch from parent collections then show the skill's own traffic
    if (asset.type === "Skill") return <SkillComponentsView asset={asset} />;
    // LLMs: their collectionIds are their own collections — show actual LLM API endpoints
    if (asset.type === "LLM") return <McpComponentsView asset={asset} onNavChange={onNavChange} />;
    return <Box padding="4"><Text variant="bodySm" color="subdued">No component data available for this asset type.</Text></Box>;
}

// ─── Main export ──────────────────────────────────────────────────────────────

export default function AgenticAssetFlyout({
    asset,
    show,
    onClose,
    onNavigateToAsset,
    agenticTreeData = [],
    agenticFlatData = [],
    assetDevices = {},
    collections = [],
    agenticViolationRows = [],
    startTimestamp,
    endTimestamp,
}) {
    const [selectedTab,    setSelectedTab]    = useState(0);
    const [topNav,         setTopNav]         = useState(null);
    const [topNavPicker,   setTopNavPicker]   = useState(null);
    const [mcpComponentCount, setMcpComponentCount] = useState(0);
    const [inlineTopology, setInlineTopology] = useState([]);

    useEffect(() => { setSelectedTab(0); setTopNav(null); setTopNavPicker(null); }, [asset?.id]);

    useEffect(() => {
        const collectionIds = asset?.collectionIds;
        const type = asset?.type;
        if (!collectionIds?.length || type !== "AI Agent") { setInlineTopology([]); return; }
        let cancelled = false;
        (async () => {
            try {
                const bundles = await Promise.all(collectionIds.map(id => agenticObserveApi.fetchCollectionStiBundle(id)));
                const sti = bundles.flatMap(b => b.stiEndpoints || []);
                const builtinTools = bundles.flatMap(b =>
                    buildAgentBuiltinToolsFromStis(b.stiEndpoints, b.apiInfoList, b.id, b.auditRows)
                );
                if (!cancelled) setInlineTopology(buildAgentInlineTopologyComponents(sti, builtinTools, asset));
            } catch {
                if (!cancelled) setInlineTopology([]);
            }
        })();
        return () => { cancelled = true; };
    }, [asset?.id, asset?.type, asset?.collectionIds, asset?.assetTagValue, asset?.name]);

    useEffect(() => {
        const collectionIds = asset?.collectionIds;
        const type = asset?.type;
        if (!collectionIds?.length || (type !== "MCP Server" && type !== "LLM")) { setMcpComponentCount(0); return; }
        let cancelled = false;
        (async () => {
            try {
                const results = await Promise.all(collectionIds.map(id => agenticObserveApi.fetchMcpComponentsData(id)));
                if (cancelled) return;
                const seen = new Set();
                let count = 0;
                results.forEach(data => {
                    const categories = [
                        { items: data.tools,     prefix: "tool:" },
                        { items: data.resources, prefix: "resource:" },
                        { items: data.prompts,   prefix: "prompt:" },
                    ];
                    categories.forEach(({ items, prefix }) => {
                        (items || []).forEach(item => {
                            const k = `${prefix}${item.name}`;
                            if (!seen.has(k)) { seen.add(k); count++; }
                        });
                    });
                });
                setMcpComponentCount(count);
            } catch {
                if (!cancelled) setMcpComponentCount(0);
            }
        })();
        return () => { cancelled = true; };
    }, [asset?.id, asset?.type, asset?.collectionIds]);

    const chatMetadata = useMemo(() => {
        if (!asset) return null;
        return buildAgenticObserveChatMetadata("asset", {
            assetName:    asset.name,
            assetType:    asset.type,
            collectionId: asset.collectionIds || [],
            assetTagValue: asset.assetTagValue,
        });
    }, [asset]);

    // Claude config/settings violations attributed to this asset's devices (host-matched, not the
    // agent total). Drives the accurate "Claude Settings" row count + its threat-activity deep link.
    const configRows = useMemo(
        () => (asset?.type === "AI Agent" ? selectConfigViolationRows(agenticViolationRows, asset, collections) : []),
        [asset, agenticViolationRows, collections],
    );
    const configViolations = useMemo(() => {
        const summary = summarizeViolations(configRows);
        return summary.total > 0 ? summary : null;
    }, [configRows]);

    const handleTabSelect = useCallback((tab) => {
        setSelectedTab(tab);
        setTopNav(null);
        setTopNavPicker(null);
    }, []);

    const handleNavChange = useCallback((items, picker = null) => {
        setTopNav(items);
        setTopNavPicker(picker || null);
    }, []);

    const tabs = useMemo(() => {
        if (!asset) return [];
        const totalV   = (asset.violations?.critical || 0) + (asset.violations?.high || 0) + (asset.violations?.medium || 0) + (asset.violations?.low || 0);
        const devCount = (assetDevices[asset.id] || []).length;
        let componentCount = 0;
        if (asset.type === "AI Agent") {
            componentCount = countAgentComponentsTab(asset, {
                inlineComponents: inlineTopology,
                configViolations,
            });
        } else if (asset.type === "MCP Server") {
            componentCount = mcpComponentCount;
        }
        return [
            { id: "overview",   content: "Overview" },
            { id: "components", content: (componentCount > 0 && asset.type !== "Skill") ? `Components (${componentCount})` : "Components" },
            { id: "violations", content: `Violations (${totalV})` },
            { id: "devices",    content: `Devices (${devCount})` },
        ];
    }, [asset, assetDevices, inlineTopology, mcpComponentCount, configViolations]);

    if (!asset) return null;

    return (
        <AgenticFlyoutShell
            show={show}
            width={800}
            header={
                <>
                    <FlyoutBreadcrumb
                        items={topNav
                            ? [{ label: asset.name, badge: asset.riskScore, onClick: topNav[0]?.onClick }, ...topNav.slice(1)]
                            : [{ label: asset.name, badge: asset.riskScore }]
                        }
                        onClose={onClose}
                    >
                        {topNavPicker && (
                            <>
                                <Text variant="bodySm" color="subdued">/</Text>
                                {topNavPicker}
                            </>
                        )}
                    </FlyoutBreadcrumb>
                    {!topNav && (
                        <>
                            <Box paddingInlineStart="1" paddingInlineEnd="1">
                                <Tabs tabs={tabs} selected={selectedTab} onSelect={handleTabSelect} />
                            </Box>
                            <Divider />
                        </>
                    )}
                </>
            }
            footer={
                <AiChatSection
                    placeholder="Ask anything about this agentic asset..."
                    resetKey={asset?.id}
                    conversationType="AGENTIC_OBSERVE"
                    chatMetadata={chatMetadata}
                />
            }
        >
            <Box padding="2" style={{ flex: 1, minHeight: 0, overflowY: "auto", display: "flex", flexDirection: "column" }}>
                {selectedTab === 0 && (
                    <OverviewTab
                        asset={asset}
                        onTabChange={handleTabSelect}
                        assetDevices={assetDevices}
                        agenticTreeData={agenticTreeData}
                        agenticFlatData={agenticFlatData}
                        mcpComponentCount={mcpComponentCount}
                        inlineComponents={inlineTopology}
                    />
                )}
                {selectedTab === 1 && (
                    <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                        <AgenticComponentsTab
                            asset={asset}
                            onNavChange={handleNavChange}
                            onNavigateToAsset={onNavigateToAsset}
                            agenticFlatData={agenticFlatData}
                            configViolations={configViolations}
                            configRows={configRows}
                        />
                    </div>
                )}
                {selectedTab === 2 && <ViolationsTab asset={asset} collections={collections} startTimestamp={startTimestamp} endTimestamp={endTimestamp} onViolationClick={asset?.type === "Skill" ? () => handleTabSelect(1) : undefined} />}
                {selectedTab === 3 && <DevicesTab asset={asset} assetDevices={assetDevices} />}
            </Box>
        </AgenticFlyoutShell>
    );
}
