import React, { useState, useMemo, useCallback, useRef, useEffect, useReducer } from "react";
import { useNavigate } from "react-router-dom";
import { produce } from "immer";
import Highcharts from "highcharts";
import HighchartsReact from "highcharts-react-official";
import { Card, Box, HorizontalStack, HorizontalGrid, VerticalStack, Text, Divider, Badge, Tooltip } from "@shopify/polaris";
import MisconfiguredConfigIcon from "@/assets/MisconfiguredConfigIcon.svg";
import PersonLockIcon from "@/assets/PersonLockIcon.svg";
import LaptopIcon from "@/assets/Laptop.svg";
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { AllEnterpriseModule } from "ag-grid-enterprise";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import AgGridRow from "@/apps/dashboard/components/tables/rows/AgGridRow";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import DeviceFlyout from "./DeviceFlyout";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import { SeverityBadge, RiskPill } from "./AgenticCellRenderers";
import DonutChart from "../../../components/shared/DonutChart";
import AgenticStatsCard from "./AgenticStatsCard";
import { aggregateViolationsByCollectionId, fetchAgenticViolations } from "./agenticObserveApi";
import { buildDeviceEndpointsPageData } from "./agenticPageBuilders";
import { fetchEndpointShieldUserMetadata } from "../api_collections/endpointShieldHelper";
import { groupCollectionsByUser } from "./constants";
import NewLayoutTooltip from "./NewLayoutTooltip";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import values from "@/util/values";
import func from "@/util/func";
import api from "../api";
import LocalStore from "../../../../main/LocalStorageStore";
import { fetchGuardrailPolicyNamesCached } from "../../guardrails/topicGuardrailUtils";

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);

// ─── Chart config helpers ─────────────────────────────────────────────────────

function makeOsTrendConfig(osTrend, monthLabels) {
    const categories = monthLabels || [];
    const n = categories.length;
    // Spread x labels for long windows so they don't overlap (all-time can be 12+ months)
    const tickInterval = n > 14 ? Math.ceil(n / 12) : 1;
    const series = [
        {name:"Mac",     data:osTrend.mac     || new Array(n).fill(0), color:"#7C3AED"},
        {name:"Windows", data:osTrend.windows || new Array(n).fill(0), color:"#10B981"},
        {name:"Linux",   data:osTrend.linux   || new Array(n).fill(0), color:"#F59E0B"},
    ];
    return {
        chart:{
            type:"areaspline", height:240, backgroundColor:"transparent",
            style:{fontFamily:"Inter, -apple-system, sans-serif"},
            margin:[8,8,88,44],
        },
        title:null, credits:{enabled:false}, exporting:{enabled:false},
        xAxis:{
            categories,
            labels:{ style:{fontSize:"11px",color:"#8C9196"}, step: tickInterval },
            lineColor:"#DFE3E8", tickColor:"transparent",
        },
        yAxis:{title:null,labels:{style:{fontSize:"11px",color:"#8C9196"}},gridLineColor:"#F1F2F3",allowDecimals:false,min:0},
        legend:{enabled:true,align:"left",verticalAlign:"bottom",layout:"horizontal",itemStyle:{fontSize:"12px",fontWeight:"400",color:"#6D7175"},symbolRadius:4,margin:8,y:8},
        tooltip:{shared:true,backgroundColor:"white",borderColor:"#DFE3E8",borderRadius:8,style:{fontSize:"12px"}},
        plotOptions:{ areaspline:{ marker:{enabled:false}, lineWidth:2, fillOpacity:0.08 }, series:{ connectNulls:true } },
        series,
    };
}

// ─── Stat + chart cards ───────────────────────────────────────────────────────

function TopSection({ summary }) {
    const sparklines = summary?.statSparklines || {};
    const osTrend = summary?.osTrend || {};
    const osTrendOpts = useMemo(
        () => makeOsTrendConfig(osTrend, summary?.monthLabels),
        [summary?.osTrend, summary?.monthLabels]
    );
    const violationsChartData = useMemo(() => {
        const arr = summary?.violationsBySeverity || [];
        const obj = {};
        arr.forEach(({ name, y, color }) => { obj[name] = { text: y, color }; });
        return obj;
    }, [summary?.violationsBySeverity]);

    const violationsTitleColor = useMemo(() => {
        const order = ["Critical", "High", "Medium", "Low"];
        for (const sev of order) {
            if (violationsChartData[sev]?.text > 0) return violationsChartData[sev].color;
        }
        return undefined;
    }, [violationsChartData]);

    return (
        <HorizontalGrid columns="320px 1fr 298px" gap="4">
            <Card padding="0">
                <VerticalStack>
                    <AgenticStatsCard
                        title="Total Endpoints"
                        total={summary?.deviceCount ?? 0}
                        delta={summary?.deltaEndpoints ?? 0}
                        sparklineCounts={sparklines.endpoints}
                        sparklineColor="#7C3AED"
                        sparklineLabels={summary?.monthLabels}
                        noCard
                    />
                    <Divider />
                    <AgenticStatsCard
                        title="Users"
                        total={summary?.totalUsers ?? 0}
                        delta={summary?.deltaUsers ?? 0}
                        sparklineCounts={sparklines.users}
                        sparklineColor="#2563EB"
                        sparklineLabels={summary?.monthLabels}
                        noCard
                    />
                    <Divider />
                    <AgenticStatsCard
                        title="Total Violations"
                        total={summary?.totalViolations ?? 0}
                        totalColor="critical"
                        delta={summary?.deltaViolations ?? 0}
                        sparklineCounts={sparklines.violations}
                        sparklineColor="#DC2626"
                        sparklineLabels={summary?.monthLabels}
                        noCard
                    />
                </VerticalStack>
            </Card>
            <Card padding="0">
                <Box padding="4">
                    <VerticalStack gap="2">
                        <Text variant="headingMd" fontWeight="semibold">Endpoints Over Time by OS Type</Text>
                        <HighchartsReact highcharts={Highcharts} options={osTrendOpts} />
                    </VerticalStack>
                </Box>
            </Card>
            <Card padding="0">
                <Box padding="4">
                    <VerticalStack gap="2">
                        <Text variant="headingMd" fontWeight="semibold" alignment="center">Violations by Severity</Text>
                        <HorizontalStack align="center">
                            <DonutChart
                                data={violationsChartData}
                                title={summary?.totalViolations ?? 0}
                                subtitle="Violations"
                                size={180}
                                pieInnerSize="55%"
                                titleColor={violationsTitleColor}
                            />
                        </HorizontalStack>
                        {Object.keys(violationsChartData).length > 0 && (
                            <HorizontalStack gap="3" wrap align="center">
                                {Object.entries(violationsChartData).map(([key, { text, color }]) => (
                                    <HorizontalStack key={key} gap="1" blockAlign="center">
                                        <Box
                                            className="agentic-dot"
                                            style={{ "--dot-color": color }}
                                        />
                                        <Text variant="bodySm" color="subdued">{key} ({text})</Text>
                                    </HorizontalStack>
                                ))}
                            </HorizontalStack>
                        )}
                    </VerticalStack>
                </Box>
            </Card>
        </HorizontalGrid>
    );
}

// ─── OS icon helpers ──────────────────────────────────────────────────────────
export function OsIcon({ os, size = 16 }) {
    if (os === "mac")     return <img src="/public/os-mac.svg"     width={size} height={size} alt="macOS"   style={{ flexShrink: 0 }} />;
    if (os === "windows") return <img src="/public/os-windows.svg" width={size} height={size} alt="Windows" style={{ flexShrink: 0 }} />;
    if (os === "linux")   return <img src="/public/os-linux.svg"   width={size} height={size} alt="Linux"   style={{ flexShrink: 0 }} />;
    return                       <img src={LaptopIcon}             width={size} height={size} alt="Device"  style={{ flexShrink: 0 }} />;
}

// Marker icon shown next to a row label (matches the personal-account marker pattern in AgenticCellRenderers).
function MarkerIcon({ src, label, size = 16 }) {
    return (
        <Tooltip content={label} dismissOnMouseOut activatorWrapper="div">
            <img src={src} width={size} height={size} alt={label} style={{ flexShrink: 0, display: "block" }} />
        </Tooltip>
    );
}

// ─── Cell renderers ───────────────────────────────────────────────────────────

function SkillBadge({ count }) {
    if (!count) return null;
    return <Badge>{`${count} ${count === 1 ? "skill" : "skills"}`}</Badge>;
}

function RiskScoreCellRenderer({ value }) {
    if (value == null) return null;
    return <RiskPill score={value} />;
}

function ViolationsCellRenderer({ value }) {
    if (!value) return null;
    const parts = ["critical", "high", "medium", "low"].filter(k => value[k] > 0);
    if (!parts.length) return null;
    return (
        <HorizontalStack gap="1" blockAlign="center" wrap={false}>
            {parts.map(k => <SeverityBadge key={k} severity={k}>{value[k]}</SeverityBadge>)}
        </HorizontalStack>
    );
}

// ─── Endpoint cell — uses AgGridRow as shared inner renderer ──────────────────

// Username cell renderer — used as innerRenderer of the auto-group (expand) column.
export const TYPE_CLASS_MAP = {
    "AI Agent": "agentic-type-AGENT",
    "MCP Server": "agentic-type-MCP",
    "LLM": "agentic-type-LLM",
    "Skill": "agentic-type-SKILL",
    "Tool": "agentic-type-TOOL",
    "Tool Call": "agentic-type-TOOL",
    "Resource": "agentic-type-RESOURCE",
    "Prompt": "agentic-type-PROMPT",
    "Config": "agentic-type-CONFIG",
};

function UsernameCellInner({ data, node }) {
    if (!data) return null;
    const isLeaf = node.level > 0;
    if (isLeaf) {
        const coloredBadge = data.type ? (
            <span className={TYPE_CLASS_MAP[data.type] || "agentic-type-DEFAULT"}>
                <Badge>{data.type}</Badge>
            </span>
        ) : null;
        return (
            <AgGridRow
                label={data.endpoint}
                warning={
                    <HorizontalStack gap="2" blockAlign="center" wrap={false}>
                        {coloredBadge}
                        {data.skillCount ? <SkillBadge count={data.skillCount} /> : null}
                    </HorizontalStack>
                }
            />
        );
    }
    const username = data.endpoint && data.endpoint !== "-" ? data.endpoint : null;
    return (
        <AgGridRow
            icon={<OsIcon os={data.os} />}
            label={username || "-"}
            isBold={!!username}
            warning={
                (data.hasPersonalAccount || data.hasMisconfiguredConfig) ? (
                    <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                        {data.hasPersonalAccount && <MarkerIcon src={PersonLockIcon} label="Contains personal account" size={24} />}
                        {data.hasMisconfiguredConfig && <MarkerIcon src={MisconfiguredConfigIcon} label="Misconfigured config" size={24} />}
                    </HorizontalStack>
                ) : null
            }
        />
    );
}


// ─── Column definitions ───────────────────────────────────────────────────────

const DASH_FORMATTER = (params) => (params.value && params.value !== "-" ? params.value : "-");

function buildDeviceColDefs(agentRiskData) {
    return [
    {
        field: "riskScore",
        headerName: "Risk score",
        width: 110,
        sort: "desc",
        filter: false,
        cellRenderer: RiskScoreCellRenderer,
        valueGetter: (params) => {
            if (!params.data) return null;
            if (!params.data.path || params.data.path.length <= 1) return params.data.riskScore ?? null;
            const key = params.data.path.join("/");
            return agentRiskData[key]?.riskScore ?? null;
        },
    },
    {
        field: "deviceId",
        headerName: "Endpoint",
        flex: 1.6,
        minWidth: 240,
        sortable: true,
        valueGetter: (params) => {
            if (!params.data) return null;
            return params.data.deviceId || null;
        },
        valueFormatter: (params) => params.value || "-",
    },
    {
        field: "os",
        headerName: "OS",
        width: 100,
        hide: true,
        filter: "agSetColumnFilter",
    },
    {
        field: "group", headerName: "Group", flex: 1, minWidth: 120,
        valueGetter: (p) => p.data?.group || "",
        valueFormatter: (p) => p.value || "-",
    },
    {
        field: "role", headerName: "Role", flex: 1.2, minWidth: 150,
        valueGetter: (p) => p.data?.role || "",
        valueFormatter: (p) => p.value || "-",
    },
    {
        field: "violations",
        headerName: "Violations",
        width: 200,
        sortable: true,
        filter: false,
        cellRenderer: ViolationsCellRenderer,
        valueGetter: (params) => {
            if (!params.data) return null;
            if (!params.data.path || params.data.path.length <= 1) return params.data.violations ?? null;
            const key = params.data.path.join("/");
            return agentRiskData[key]?.violations ?? null;
        },
        comparator: (a, b) => {
            const total = (v) => v ? (v.critical || 0) * 1000 + (v.high || 0) * 100 + (v.medium || 0) * 10 + (v.low || 0) : 0;
            return total(a) - total(b);
        },
    },
    {
        field: "lastTraffic",
        headerName: "Last Traffic",
        width: 130,
        valueFormatter: DASH_FORMATTER,
        // Sort on raw epoch so numeric ordering works correctly
        comparator: (a, b, nodeA, nodeB) => {
            const ea = nodeA?.data?.lastTrafficEpoch || 0;
            const eb = nodeB?.data?.lastTrafficEpoch || 0;
            return ea - eb;
        },
    },
];
}


const DEFAULT_COL_DEF = {
    sortable: true,
    resizable: true,
    filter: true,
    cellStyle: { display: "flex", alignItems: "center", fontSize: 13, color: "#202223" },
};

function getCollectionIdsForDevice(deviceId, collections) {
    if (!deviceId || !collections?.length) return [];
    const prefix = deviceId + ".";
    return collections.filter(c => c.hostName && c.hostName.startsWith(prefix)).map(c => c.id);
}

function getHostNamesForDevice(deviceId, collections) {
    if (!deviceId || !collections?.length) return [];
    const prefix = deviceId + ".";
    return collections.filter(c => c.hostName && c.hostName.startsWith(prefix)).map(c => c.hostName);
}

// ─── Table section ────────────────────────────────────────────────────────────

function TableSection({ deviceFlatData, agentRiskData, collections, startTimestamp, endTimestamp }) {
    const [selectedCount, setSelectedCount] = useState(0);
    const [deviceFlyout, setDeviceFlyout] = useState(null);
    const gridRef = useRef(null);

    const deviceColDefs = useMemo(() => buildDeviceColDefs(agentRiskData), [agentRiskData]);

    const findDeviceRow = useCallback((deviceId) =>
        deviceFlatData.find((r) => r.path?.length === 1 && r.path[0] === deviceId),
    [deviceFlatData]);

    const findAgentsForDevice = useCallback((deviceId) =>
        deviceFlatData.filter((r) => r.path?.length === 2 && r.path[0] === deviceId),
    [deviceFlatData]);

    const closeAll = useCallback(() => setDeviceFlyout(null), []);

    useEffect(() => {
        const params   = new URLSearchParams(window.location.search);
        const deviceId = params.get("device");
        if (!deviceId) return;
        const device = findDeviceRow(deviceId);
        if (!device) return;
        setDeviceFlyout({ device, agents: findAgentsForDevice(deviceId), hostNames: getHostNamesForDevice(deviceId, collections) });
    }, [deviceFlatData, findDeviceRow, findAgentsForDevice, collections]);


    const handleRowClick = useCallback((e) => {
        const { data, node } = e;
        if (!data) return;
        if (node.level === 0) {
            const deviceId = data.path[0];
            setDeviceFlyout({ device: data, agents: findAgentsForDevice(deviceId), hostNames: getHostNamesForDevice(deviceId, collections) });
            return;
        }
        // Child (agentic asset) rows — open on the Agentic Assets page with flyout pre-selected
        const assetId = data.rawServiceName || data.endpoint;
        const params = new URLSearchParams({ asset: assetId, type: data.type || "" });
        window.open(`/dashboard/observe/agentic-assets?${params}`, "_blank");
    }, [findAgentsForDevice]);

    const getDataPath = useCallback((data) => data.path, []);

    const autoGroupColumnDef = useMemo(() => ({
        headerName: "Username",
        width: 320,
        minWidth: 180,
        pinned: "left",
        checkboxSelection: true,
        headerCheckboxSelection: true,
        filter: "agTextColumnFilter",
        // Sort by displayed username (endpoint field), not by the internal path key
        comparator: (a, b, nodeA, nodeB) => {
            const va = (nodeA?.data?.endpoint || nodeA?.data?.deviceId || "").toLowerCase();
            const vb = (nodeB?.data?.endpoint || nodeB?.data?.deviceId || "").toLowerCase();
            return va < vb ? -1 : va > vb ? 1 : 0;
        },
        cellRendererParams: {
            suppressCount: true,
            innerRenderer: UsernameCellInner,
        },
        cellStyle: { display: "flex", alignItems: "center" },
        getQuickFilterText: (params) => {
            if (!params.data) return "";
            return params.data.endpoint || params.data.deviceId || "";
        },
    }), []);

    const bulkActions = useMemo(() => [
        { label: "Edit Team", onAction: () => {} },
        { label: "Edit Role", onAction: () => {} },
    ], []);

    return (
        <VerticalStack gap="0">
            <AgGridTable
                gridRef={gridRef}
                rowData={deviceFlatData}
                columnDefs={deviceColDefs}
                defaultColDef={DEFAULT_COL_DEF}
                autoGroupColumnDef={autoGroupColumnDef}
                treeData
                getDataPath={getDataPath}
                groupDefaultExpanded={0}
                height={500}
                domLayout="normal"
                searchPlaceholder="Search..."
                bulkActionCount={selectedCount}
                bulkActions={bulkActions}
                onClearBulk={() => { gridRef.current?.api?.deselectAll(); setSelectedCount(0); }}
                onRowClicked={handleRowClick}
                getRowStyle={() => ({ cursor: "pointer" })}
                onSelectionChanged={e => setSelectedCount(e.api.getSelectedRows().length)}
                rowSelection="multiple"
                suppressRowClickSelection
                pagination
                paginationPageSize={20}
                paginationPageSizeSelector={[20, 50, 100]}
                sideBar={{ toolPanels: ["columns", "filters"], defaultToolPanel: null }}
            />

            <DeviceFlyout
                device={deviceFlyout?.device}
                agents={deviceFlyout?.agents}
                show={deviceFlyout !== null}
                onClose={closeAll}
                agentRiskData={agentRiskData}
                deviceHostNames={deviceFlyout?.hostNames || []}
                collections={collections}
                startTimestamp={startTimestamp}
                endTimestamp={endTimestamp}
            />
        </VerticalStack>
    );
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function DeviceEndpoints() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [deviceFlatData, setDeviceFlatData] = useState([]);
    const [agentRiskData, setAgentRiskData] = useState({});
    const [summary, setSummary] = useState({});
    const [collections, setCollections] = useState([]);
    const newLayout = LocalStore((state) => state.agenticNewLayout);
    const setAgenticNewLayout = LocalStore((state) => state.setAgenticNewLayout);

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[4],
    );
    const startTimestamp = Math.floor(Date.parse(currDateRange.period.since) / 1000);
    const endTimestamp = Math.floor(Date.parse(currDateRange.period.until) / 1000);

    useEffect(() => {
        if (!newLayout) {
            navigate("/dashboard/observe/users-and-devices", { replace: true });
        }
    }, [navigate, newLayout]);

    useEffect(() => {
        fetchGuardrailPolicyNamesCached();
    }, []);

    const handleLayoutToggle = useCallback((val) => {
        const checked = val === true;
        setAgenticNewLayout(checked);
        if (!checked) navigate("/dashboard/observe/users-and-devices");
    }, [navigate, setAgenticNewLayout]);

    useEffect(() => {
        const isMountedRef = { current: true };
        (async () => {
            try {
                setLoading(true);
                const [
                    apiCollectionsResp,
                    trafficInfoResp,
                    riskScoreResp,
                    shieldResult,
                    violationsResp,
                ] = await Promise.all([
                    api.getAllCollectionsBasic(),
                    api.getLastTrafficSeen(),
                    api.getRiskScoreInfo(),
                    fetchEndpointShieldUserMetadata(),
                    fetchAgenticViolations({ startTimestamp, endTimestamp }),
                ]);
                if (!isMountedRef.current) return;
                const violationRows = violationsResp || [];
                const { usernameMap = {}, userMetadataMap = {}, moduleInfos = [] } = shieldResult || {};
                const collections = apiCollectionsResp?.apiCollections || [];
                const pageData = buildDeviceEndpointsPageData(
                    collections,
                    trafficInfoResp || {},
                    riskScoreResp?.riskScoreOfCollectionsMap || {},
                    {
                        moduleInfos,
                        usernameMap,
                        violationsByCollectionId: aggregateViolationsByCollectionId(violationRows, collections),
                        violationRows,
                        startTimestamp,
                        endTimestamp,
                    },
                );
                const userRows = groupCollectionsByUser(collections, trafficInfoResp || {}, {}, riskScoreResp?.riskScoreOfCollectionsMap || {}, usernameMap, userMetadataMap);
                pageData.summary.totalUsers = userRows.length;
                setDeviceFlatData(pageData.deviceFlatData);
                setAgentRiskData(pageData.agentRiskData);
                setSummary(pageData.summary);
                setCollections(collections);
            } catch {
                if (isMountedRef.current) {
                    setDeviceFlatData([]);
                    setAgentRiskData({});
                    setSummary({});
                    setCollections([]);
                }
            } finally {
                if (isMountedRef.current) setLoading(false);
            }
        })();
        return () => { isMountedRef.current = false; };
    }, [startTimestamp, endTimestamp]);

    const headerActions = (
        <HorizontalStack gap="3" blockAlign="center">
            <NewLayoutTooltip checked={newLayout} onChange={handleLayoutToggle} />
            <DateRangeFilter
                initialDispatch={currDateRange}
                dispatch={(dateObj) => dispatchCurrDateRange({ type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias })}
            />
        </HorizontalStack>
    );

    const pageTitle = (
        <TitleWithInfo
            tooltipContent="View all endpoints by device and user — track AI agent activity, risk scores, and violations."
            titleText="Endpoints"
            docsUrl="https://ai-security-docs.akto.io/agentic-ai-discovery/get-started"
        />
    );

    if (loading) {
        return (
            <PageWithMultipleCards
                title={pageTitle}
                isFirstPage={true}
                secondaryActions={headerActions}
                components={[<SpinnerCentered key="loading" />]}
            />
        );
    }

    return (
        <PageWithMultipleCards
            title={pageTitle}
            isFirstPage={true}
            secondaryActions={headerActions}
            components={[
                <TopSection key="top" summary={summary} />,
                <TableSection
                    key="table"
                    deviceFlatData={deviceFlatData}
                    agentRiskData={agentRiskData}
                    collections={collections}
                    startTimestamp={startTimestamp}
                    endTimestamp={endTimestamp}
                />,
            ]}
        />
    );
}
