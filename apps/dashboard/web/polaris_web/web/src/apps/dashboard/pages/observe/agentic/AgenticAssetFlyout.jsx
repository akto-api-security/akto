import React, { useState, useMemo, useCallback, useEffect } from "react";
import { Tabs, Box, VerticalStack, Text, Divider } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import AgenticFlyoutShell from "./AgenticFlyoutShell";
import AiChatSection from "./AiChatSection";
import { getAgentLinkedComponents } from "./agenticPageBuilders";
import agenticObserveApi, { buildAgenticObserveChatMetadata } from "./agenticObserveApi";
import OverviewTab from "./OverviewTab";
import ViolationsTab from "./ViolationsTab";
import McpComponentsView from "./McpComponentsView";
import AgentComponentsView from "./AgentComponentsView";
import SkillComponentsView from "./SkillComponentsView";
import "../../../components/layouts/style.css";

// ─── Devices tab (small, kept inline) ────────────────────────────────────────

const DEVICES_COL_DEFS = [
    { field: "username", headerName: "User",      flex: 1,   minWidth: 120, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#202223" }, valueFormatter: p => p.value || "-" },
    { field: "lastSeen", headerName: "Last Seen", width: 130, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" }, valueFormatter: p => p.value || "-", comparator: (a, b, nodeA, nodeB) => (nodeA?.data?.lastSeenEpoch || 0) - (nodeB?.data?.lastSeenEpoch || 0) },
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
            sideBar={false}
        />
    );
}

// ─── Components tab router ────────────────────────────────────────────────────

function AgenticComponentsTab({ asset, onNavChange, onNavigateToAsset, agenticFlatData = [] }) {
    if (asset.type === "MCP Server") return <McpComponentsView asset={asset} onNavChange={onNavChange} />;
    if (asset.type === "AI Agent")   return <AgentComponentsView asset={asset} onNavChange={onNavChange} onNavigateToAsset={onNavigateToAsset} agenticFlatData={agenticFlatData} />;
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
}) {
    const [selectedTab,    setSelectedTab]    = useState(0);
    const [topNav,         setTopNav]         = useState(null);
    const [topNavPicker,   setTopNavPicker]   = useState(null);
    const [mcpComponentCount, setMcpComponentCount] = useState(0);

    useEffect(() => { setSelectedTab(0); setTopNav(null); setTopNavPicker(null); }, [asset?.id]);

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
            const children = getAgentLinkedComponents(asset, agenticTreeData, agenticFlatData);
            componentCount = children.length + (asset.skillCount || 0);
        } else if (asset.type === "MCP Server") {
            componentCount = mcpComponentCount;
        }
        return [
            { id: "overview",   content: "Overview" },
            { id: "components", content: (componentCount > 0 && asset.type !== "Skill") ? `Components (${componentCount})` : "Components" },
            { id: "violations", content: `Violations (${totalV})` },
            { id: "devices",    content: `Devices (${devCount})` },
        ];
    }, [asset, assetDevices, agenticTreeData, agenticFlatData, mcpComponentCount]);

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
            <Box minHeight="0px" overflowY={selectedTab === 0 ? "auto" : "hidden"} className="agentic-flex-fill">
                {selectedTab === 0 && (
                    <OverviewTab
                        asset={asset}
                        onTabChange={handleTabSelect}
                        assetDevices={assetDevices}
                        agenticTreeData={agenticTreeData}
                        agenticFlatData={agenticFlatData}
                        mcpComponentCount={mcpComponentCount}
                    />
                )}
                {selectedTab === 1 && (
                    <AgenticComponentsTab
                        asset={asset}
                        onNavChange={handleNavChange}
                        onNavigateToAsset={onNavigateToAsset}
                        agenticFlatData={agenticFlatData}
                    />
                )}
                {selectedTab === 2 && <ViolationsTab asset={asset} />}
                {selectedTab === 3 && <DevicesTab asset={asset} assetDevices={assetDevices} />}
            </Box>
        </AgenticFlyoutShell>
    );
}
