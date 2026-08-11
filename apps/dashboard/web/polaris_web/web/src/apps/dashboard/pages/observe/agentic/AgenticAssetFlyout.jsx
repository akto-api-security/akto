import React, { useState, useMemo, useCallback, useEffect } from "react";
import { Tabs, Box, VerticalStack, Text, Divider, Spinner } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import AgenticFlyoutShell from "./AgenticFlyoutShell";
import AiChatSection from "./AiChatSection";
import { buildAgentInlineTopologyComponents, buildAgentBuiltinToolsFromStis, buildMcpComponentsFromStis, countAgentComponentsTab } from "./agenticPageBuilders";
import { RiskScoreCellRenderer } from "./AgenticCellRenderers";
import agenticObserveApi, { buildAgenticObserveChatMetadata, selectConfigViolationRows, summarizeViolations } from "./agenticObserveApi";
import api from "../api";
import func from "@/util/func";
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

function DevicesTab({ asset, enrichMaps = {} }) {
    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        const deviceId = e.data.deviceId || e.data.endpoint;
        window.open(`/dashboard/observe/endpoints?device=${encodeURIComponent(deviceId)}`, "_blank");
    }, []);

    // Server-side paginated — scoped to this one asset's own apiCollectionIds (cheap), never the
    // whole account. See AgenticObserveAction.fetchAgenticAssetDevicesPage.
    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        const { trafficMap, riskScoreMap, userAnalysisFlatMap, usernameMap } = enrichMaps;
        return api.fetchAgenticAssetDevicesPage({
            apiCollectionIds: asset.collectionIds || [],
            skip,
            limit: limit || 20,
            sortKey,
            sortOrder: sortOrder ? -sortOrder : -1,
            queryValue: searchString || undefined,
            trafficMap, riskScoreMap, userAnalysisFlatMap, usernameMap,
        }).then((res) => ({
            value: (res.devices || []).map((d) => ({
                ...d,
                lastSeen: d.lastSeenEpoch > 0 ? func.prettifyEpoch(d.lastSeenEpoch) : "-",
            })),
            total: res.total || 0,
        }));
    }, [asset.collectionIds, enrichMaps]);

    return (
        <AgGridTable
            key={asset.id}
            columnDefs={DEVICES_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onServerFetch={onServerFetch}
            serverSideRowModel
            getRowId={(params) => params.data.deviceId}
            onRowClicked={handleRowClick}
            getRowStyle={() => ({ cursor: "pointer" })}
            noOuterBorder
            searchPlaceholder="Search devices..."
            paginationPageSize={20}
            sideBar={{ toolPanels: ["columns", "filters"], defaultToolPanel: null }}
            domLayout="normal"
        />
    );
}

// ─── Components tab router ────────────────────────────────────────────────────

function AgenticComponentsTab({ asset, onNavChange, onNavigateToAsset, configViolations, configRows }) {
    if (asset.type === "MCP Server") return <McpComponentsView asset={asset} onNavChange={onNavChange} />;
    if (asset.type === "AI Agent")   return <AgentComponentsView asset={asset} onNavChange={onNavChange} onNavigateToAsset={onNavigateToAsset} configViolations={configViolations} configRows={configRows} />;
    // Skills: fetch from parent collections then show the skill's own traffic
    if (asset.type === "Skill") return <SkillComponentsView asset={asset} />;
    // LLMs: their collectionIds are their own collections — show actual LLM API endpoints
    if (asset.type === "LLM") return <McpComponentsView asset={asset} onNavChange={onNavChange} />;
    return <Box padding="4"><Text variant="bodySm" color="subdued">No component data available for this asset type.</Text></Box>;
}

// ─── Main export ──────────────────────────────────────────────────────────────

export default function AgenticAssetFlyout({
    asset: rawAsset,
    show,
    onClose,
    onNavigateToAsset,
    agenticTreeData = [],
    agenticFlatData = [],
    enrichMaps = {},
    collections = [],
    // Left undefined (not defaulted to []) when the parent hasn't loaded raw violation rows yet, so
    // ViolationsTab can tell "not loaded" apart from "confirmed zero violations" via Array.isArray.
    // selectConfigViolationRows below has its own internal default and handles undefined safely.
    agenticViolationRows,
    startTimestamp,
    endTimestamp,
}) {
    const [selectedTab,    setSelectedTab]    = useState(0);
    const [topNav,         setTopNav]         = useState(null);
    const [topNavPicker,   setTopNavPicker]   = useState(null);
    const [mcpComponentCount, setMcpComponentCount] = useState(0);
    const [inlineTopology, setInlineTopology] = useState([]);

    // hostNames/collectionIds/skillNames/mcpServers/mcpServerCollectionIds/devices no longer come
    // with the grid row (see AgenticObserveAction.GroupSummary.toSummaryResponse()'s and
    // fetchAgenticAssetsSummary's row-loop comments — sending them for every row of every page used
    // to make a single 50-row page 16MB, mostly from raw per-device breakdowns on rows with hundreds
    // of devices). Fetched lazily here, once, only for the one asset actually opened.
    const [assetDetail, setAssetDetail] = useState(null);

    useEffect(() => {
        setAssetDetail(null);
        if (!rawAsset?.groupKey || !rawAsset?.rowType) return;
        let cancelled = false;
        (async () => {
            try {
                const { trafficMap, riskScoreMap, userAnalysisFlatMap } = enrichMaps;
                const detail = await api.fetchAgenticAssetDetail({
                    groupKey: rawAsset.groupKey, rowType: rawAsset.rowType,
                    trafficMap, riskScoreMap, userAnalysisFlatMap,
                });
                if (!cancelled) setAssetDetail(detail);
            } catch {
                if (!cancelled) setAssetDetail({ hostNames: [], collectionIds: [], skillNames: [], mcpServers: [], mcpServerCollectionIds: {}, devices: [] });
            }
        })();
        return () => { cancelled = true; };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [rawAsset?.groupKey, rawAsset?.rowType]);

    const asset = useMemo(() => {
        if (!rawAsset) return null;
        if (!assetDetail) return rawAsset;
        return { ...rawAsset, ...assetDetail };
    }, [rawAsset, assetDetail]);

    // Id-keyed map for OverviewTab's existing contract (built from the lazily-fetched asset.devices
    // instead of being threaded in as a prop from the grid — there's only ever one asset open at a
    // time in this flyout).
    const assetDevices = useMemo(() => (
        asset ? { [asset.id]: asset.devices || [] } : {}
    ), [asset]);

    // True only for the brief window between opening an asset and its lazy detail landing — the
    // shell/header (name, riskScore — cheap scalars, always present on the row) render immediately;
    // only tab content needs to wait, since every tab needs asset.collectionIds/hostNames for its
    // own data fetch.
    const detailLoading = !!rawAsset && !assetDetail;

    useEffect(() => { setSelectedTab(0); setTopNav(null); setTopNavPicker(null); }, [asset?.id]);

    useEffect(() => {
        const collectionIds = asset?.collectionIds;
        const type = asset?.type;
        if (!collectionIds?.length || type !== "AI Agent") { setInlineTopology([]); return; }
        let cancelled = false;
        (async () => {
            try {
                // Batched: 3 requests total regardless of collectionIds.length, not 3 PER id — an
                // agent group can span thousands of collections, which used to fire thousands of
                // concurrent requests here and choke the browser on opening this flyout.
                const bundleMap = await agenticObserveApi.fetchCollectionStiBundlesBatch(collectionIds);
                const bundles = Array.from(bundleMap.values());
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
                // Batched — see comment in the AI Agent effect above for why this matters.
                const bundleMap = await agenticObserveApi.fetchCollectionStiBundlesBatch(collectionIds);
                if (cancelled) return;
                const seen = new Set();
                let count = 0;
                bundleMap.forEach((b) => {
                    const data = buildMcpComponentsFromStis(b.stiEndpoints, b.apiInfoList, b.id, b.auditRows);
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
        // endpointsCount is a cheap scalar already present on the grid row (before the lazy detail
        // fetch lands), same number (asset.devices).length would give once loaded — avoids the tab
        // badge flashing 0 while assetDetail is still in flight.
        const devCount = asset.endpointsCount || 0;
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
                {detailLoading ? (
                    <Box padding="8"><Spinner accessibilityLabel="Loading asset details" size="large" /></Box>
                ) : (
                    <>
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
                                    configViolations={configViolations}
                                    configRows={configRows}
                                />
                            </div>
                        )}
                        {selectedTab === 2 && <ViolationsTab asset={asset} startTimestamp={startTimestamp} endTimestamp={endTimestamp} onViolationClick={asset?.type === "Skill" ? () => handleTabSelect(1) : undefined} />}
                        {selectedTab === 3 && <DevicesTab asset={asset} enrichMaps={enrichMaps} />}
                    </>
                )}
            </Box>
        </AgenticFlyoutShell>
    );
}
