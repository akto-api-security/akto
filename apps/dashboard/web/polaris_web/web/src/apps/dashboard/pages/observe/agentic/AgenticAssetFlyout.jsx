import React, { useState, useMemo, useCallback, useEffect } from "react";
import { Badge, Box, Divider, HorizontalStack, Button, Spinner, Tabs, Text } from "@shopify/polaris";
import AllowedAction from "@/apps/dashboard/components/shared/AllowedAction";
import DetailPanelHeader from "@/apps/dashboard/components/shared/DetailPanelHeader";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import { usePermissions } from "@/util/permissions";
import func from "@/util/func";
import AgenticFlyoutShell from "./AgenticFlyoutShell";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import AssetOverview from "./AssetOverview";
import AssetIcon from "./AssetIcon";
import ViolationsTab from "./ViolationsTab";
import McpComponentsView from "./McpComponentsView";
import AgentComponentsView from "./AgentComponentsView";
import SkillComponentsView from "./SkillComponentsView";
import PluginComponentsView from "./PluginComponentsView";
import { ApprovalStatusBadge, RiskScoreCellRenderer } from "./AgenticCellRenderers";
import { buildAgentInlineTopologyComponents, countAgentComponentsTab } from "./agenticPageBuilders";
import { APPROVAL_ACTIONS, approvalActionApplies, approvalActionRequires, canTargetGuardrail } from "./agenticObserveApi";
import { typeLabelOf } from "./inventoryCategories";
import api from "../api";
import "../../../components/layouts/style.css";

// ─── Devices tab (small, kept inline) ────────────────────────────────────────

const DEVICES_BASE_COL_DEFS = [
    { field: "username", headerName: "User",       flex: 1,  minWidth: 120, cellStyle: { display: "flex", alignItems: "center" }, valueFormatter: p => p.value || "-" },
    { field: "riskScore", headerName: "Risk Score", width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: RiskScoreCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "lastSeen", headerName: "Last Seen",  width: 130, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", color: "#6D7175" }, valueFormatter: p => p.value || "-", comparator: (a, b, nodeA, nodeB) => (nodeA?.data?.lastSeenEpoch || 0) - (nodeB?.data?.lastSeenEpoch || 0) },
];

// "unknown"/unset reads as disabled — an unreported status isn't proof a plugin is active.
const isPluginEnabled = (status) => String(status).toLowerCase() === "enabled";

const PLUGIN_STATUS_COL_DEF = {
    field: "pluginStatus", headerName: "Plugin Status", width: 130,
    suppressHeaderMenuButton: true, suppressHeaderFilterButton: true,
    cellStyle: { display: "flex", alignItems: "center" },
    cellRenderer: (p) => (
        <Badge size="small" status={isPluginEnabled(p.value) ? "success" : "warning"}>
            {isPluginEnabled(p.value) ? "enabled" : "disabled"}
        </Badge>
    ),
};

const PLUGIN_SCOPE_COL_DEF = {
    field: "pluginScope", headerName: "Plugin Scope", width: 130,
    suppressHeaderMenuButton: true, suppressHeaderFilterButton: true,
    cellStyle: { display: "flex", alignItems: "center" },
    valueFormatter: p => p.value || "-",
};

const getDevicesColDefs = (assetType) =>
    assetType === "Plugin" ? [...DEVICES_BASE_COL_DEFS, PLUGIN_STATUS_COL_DEF, PLUGIN_SCOPE_COL_DEF] : DEVICES_BASE_COL_DEFS;

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

function DevicesTab({ asset, enrichMaps = {}, startTimestamp, endTimestamp }) {
    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        const deviceId = e.data.deviceId || e.data.endpoint;
        window.location.href = `/dashboard/observe/endpoints?device=${encodeURIComponent(deviceId)}`;
    }, []);

    // Server-side paginated — scoped to this one asset's own apiCollectionIds (cheap), never the
    // whole account. See AgenticObserveAction.fetchAgenticAssetDevicesPage.
    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
        const { trafficMap, riskScoreMap } = enrichMaps;
        return api.fetchAgenticAssetDevicesPage({
            apiCollectionIds: asset.collectionIds || [],
            skip,
            limit: limit || 20,
            sortKey,
            sortOrder: sortOrder ? -sortOrder : -1,
            queryValue: searchString || undefined,
            trafficMap, riskScoreMap, startTimestamp, endTimestamp,
        }).then((res) => ({
            value: (res.devices || []).map((d) => ({
                ...d,
                lastSeen: d.lastSeenEpoch > 0 ? func.prettifyEpoch(d.lastSeenEpoch) : "-",
            })),
            total: res.total || 0,
        }));
    }, [asset.collectionIds, enrichMaps, startTimestamp, endTimestamp]);

    return (
        <AgGridTable
            key={asset.id}
            columnDefs={getDevicesColDefs(asset.type)}
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

function AgenticComponentsTab({ asset, onNavChange, onNavigateToAsset }) {
    if (asset.type === "MCP Server") return <McpComponentsView asset={asset} onNavChange={onNavChange} />;
    if (asset.type === "AI Agent")   return <AgentComponentsView asset={asset} onNavChange={onNavChange} onNavigateToAsset={onNavigateToAsset} />;
    // Skills: fetch from parent collections then show the skill's own traffic
    if (asset.type === "Skill") return <SkillComponentsView asset={asset} />;
    // Plugins: discovery-only — lists the bundled MCP servers/skills (same list/drill-down idiom as an AI Agent's).
    if (asset.type === "Plugin") return <PluginComponentsView asset={asset} onNavChange={onNavChange} />;
    // LLMs: their collectionIds are their own collections — show actual LLM API endpoints
    if (asset.type === "LLM") return <McpComponentsView asset={asset} onNavChange={onNavChange} />;
    return <Box padding="4"><Text variant="bodySm" color="subdued">No component data available for this asset type.</Text></Box>;
}

// ─── Main export ──────────────────────────────────────────────────────────────
// Tabs: Overview (AI inventory panel design — AssetOverview), Components, Violations (page date
// range) and Devices. Each tab loads its own data only when selected; the tab counts come from the
// grid row and the asset detail already fetched below.

export default function AgenticAssetFlyout({
    asset: rawAsset,
    show,
    onClose,
    // (assetRow) => void — opens another asset (e.g. an MCP server from an agent's components).
    onNavigateToAsset,
    enrichMaps = {},
    startTimestamp,
    endTimestamp,
    // (rows, APPROVAL_ACTIONS entry) => Promise — status actions in the header; omitted = no buttons.
    onApprovalChange,
    // (rows) => void — "Create guardrail policy" once the asset is approved; omitted = not offered.
    onCreateGuardrail,
}) {
    const { canCall } = usePermissions();
    const [approvalUpdating, setApprovalUpdating] = useState(null);
    const [selectedTab, setSelectedTab] = useState(0);
    // Components drill-down breadcrumb (set by the components views); replaces the header and tabs.
    const [topNav, setTopNav] = useState(null);
    const [topNavPicker, setTopNavPicker] = useState(null);
    // The row's violation count is an exact-hostName join and can undercount vs. ViolationsTab's own
    // query; once the tab is opened and reports its real total, prefer that.
    const [violationsTotal, setViolationsTotal] = useState(null);

    // hostNames/collectionIds/skillCount/mcpServers/mcpServerCollectionIds/devices no longer come
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
                const { trafficMap, riskScoreMap } = enrichMaps;
                const detail = await api.fetchAgenticAssetDetail({
                    groupKey: rawAsset.groupKey, rowType: rawAsset.rowType,
                    trafficMap, riskScoreMap, startTimestamp, endTimestamp,
                });
                if (!cancelled) setAssetDetail(detail);
            } catch {
                if (!cancelled) setAssetDetail({ hostNames: [], collectionIds: [], skillCount: 0, mcpServers: [], mcpServerCollectionIds: {}, deviceCount: 0, deviceSample: [] });
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

    // True only between opening an asset and its lazy detail landing — the header renders from the
    // grid row immediately; the body needs asset.collectionIds/hostNames for its own fetches.
    const detailLoading = !!rawAsset && !assetDetail;

    useEffect(() => { setSelectedTab(0); setTopNav(null); setTopNavPicker(null); setViolationsTotal(null); }, [rawAsset?.id]);

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
        const v = asset.violations || {};
        const totalV = violationsTotal ?? ((v.critical || 0) + (v.high || 0) + (v.medium || 0) + (v.low || 0));
        let componentCount = 0;
        if (asset.type === "AI Agent") {
            const inlineComponents = buildAgentInlineTopologyComponents(asset.hasInlineLlm, asset.inlineToolNames, asset);
            componentCount = countAgentComponentsTab(asset, { inlineComponents });
        } else if (asset.type === "MCP Server") {
            componentCount = asset.mcpComponentCount || 0;
        } else if (asset.type === "Plugin") {
            componentCount = (asset.pluginMcpServers || []).length + (asset.pluginSkills || []).length;
        }
        return [
            { id: "overview",   content: "Overview" },
            { id: "components", content: componentCount > 0 && asset.type !== "Skill" ? `Components (${componentCount})` : "Components" },
            { id: "violations", content: `Violations (${totalV})` },
            { id: "devices",    content: `Devices (${asset.endpointsCount || 0})` },
        ];
    }, [asset, violationsTotal]);


    const handleApproval = useCallback(async (action) => {
        setApprovalUpdating(action.label);
        try {
            await onApprovalChange([rawAsset], action);
        } finally {
            setApprovalUpdating(null);
        }
    }, [onApprovalChange, rawAsset]);

    if (!asset) return null;

    // Mockup order: primary first (Create guardrail policy once approved, else Approve), then the
    // remaining status actions.
    const approvalActions = onApprovalChange ? Object.values(APPROVAL_ACTIONS).filter((action) => approvalActionApplies(action, rawAsset)) : [];
    const showCreateGuardrail = onCreateGuardrail && rawAsset.approvalStatus === "Approved" && canTargetGuardrail(rawAsset);
    const approvalAllowed = canCall(approvalActionRequires([rawAsset]));
    const headerActions = (
        <HorizontalStack gap="2">
            {showCreateGuardrail && <Button primary onClick={() => onCreateGuardrail([rawAsset])}>Create guardrail policy</Button>}
            {approvalActions.map((action) => (
                <AllowedAction key={action.label} allowed={approvalAllowed}>
                    <Button
                        primary={action.primary && !showCreateGuardrail}
                        loading={approvalUpdating === action.label}
                        disabled={!!approvalUpdating}
                        onClick={() => handleApproval(action)}
                    >
                        {action.label}
                    </Button>
                </AllowedAction>
            ))}
        </HorizontalStack>
    );

    const header = topNav ? (
        <FlyoutBreadcrumb
            items={[{ label: asset.name, badge: asset.riskScore, onClick: topNav[0]?.onClick }, ...topNav.slice(1)]}
            onClose={onClose}
        >
            {topNavPicker && (
                <>
                    <Text variant="bodySm" color="subdued">/</Text>
                    {topNavPicker}
                </>
            )}
        </FlyoutBreadcrumb>
    ) : (
        <>
            <DetailPanelHeader
                icon={<AssetIcon type={asset.type} assetTagValue={asset.assetTagValue} size={36} />}
                title={asset.name}
                badge={asset.approvalStatus ? <ApprovalStatusBadge status={asset.approvalStatus} /> : null}
                subtitle={[typeLabelOf(asset) || asset.type, asset.subtitle]}
                actions={headerActions}
                onClose={onClose}
            />
            <Box paddingInlineStart="1" paddingInlineEnd="1">
                <Tabs tabs={tabs} selected={selectedTab} onSelect={handleTabSelect} />
            </Box>
            <Divider />
        </>
    );

    return (
        <AgenticFlyoutShell show={show} width={800} header={header}>
            <Box style={{ flex: 1, minHeight: 0, overflowY: "auto", display: "flex", flexDirection: "column" }}>
                {detailLoading ? (
                    <Box padding="8"><Spinner accessibilityLabel="Loading asset details" size="large" /></Box>
                ) : (
                    <>
                        {selectedTab === 0 && <AssetOverview asset={asset} enrichMaps={enrichMaps} startTimestamp={startTimestamp} endTimestamp={endTimestamp} />}
                        {selectedTab === 1 && (
                            <Box padding="2" style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                                <AgenticComponentsTab asset={asset} onNavChange={handleNavChange} onNavigateToAsset={onNavigateToAsset} />
                            </Box>
                        )}
                        {selectedTab === 2 && (
                            <Box padding="2" style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                                <ViolationsTab asset={asset} startTimestamp={startTimestamp} endTimestamp={endTimestamp} onViolationClick={asset.type === "Skill" ? () => handleTabSelect(1) : undefined} onTotalChange={setViolationsTotal} />
                            </Box>
                        )}
                        {selectedTab === 3 && (
                            <Box padding="2" style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                                <DevicesTab asset={asset} enrichMaps={enrichMaps} startTimestamp={startTimestamp} endTimestamp={endTimestamp} />
                            </Box>
                        )}
                    </>
                )}
            </Box>
        </AgenticFlyoutShell>
    );
}
