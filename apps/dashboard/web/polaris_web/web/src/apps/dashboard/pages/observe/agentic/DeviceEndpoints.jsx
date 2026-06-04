import React, { useState, useMemo, useCallback, useRef, useEffect, useReducer } from "react";
import { useNavigate } from "react-router-dom";
import { produce } from "immer";
import Highcharts from "highcharts";
import { HighchartsReact } from "highcharts-react-official";
import { Card, Box, HorizontalStack, VerticalStack, Text, Icon, Divider, Checkbox } from "@shopify/polaris";
import { CustomersMajor } from "@shopify/polaris-icons";
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { LicenseManager, AllEnterpriseModule } from "ag-grid-enterprise";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import AgGridRow from "@/apps/dashboard/components/tables/rows/AgGridRow";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import DeviceFlyout from "./DeviceFlyout";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import { TYPE_STYLES, SEVERITY_COLORS, getRiskColor } from "./agenticStyles";
import agenticObserveApi, { aggregateViolationsByCollectionId } from "./agenticObserveApi";
import { buildDeviceEndpointsPageData } from "./agenticPageBuilders";
import { fetchEndpointShieldUserMetadata } from "../api_collections/endpointShieldHelper";
import { groupCollectionsByUser } from "./constants";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import values from "@/util/values";
import func from "@/util/func";
import api from "../api";

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);

LicenseManager.setLicenseKey(
    "[TRIAL]_this_{AG_Charts_and_AG_Grid}_Enterprise_key_{AG-129492}_is_granted_for_evaluation_only___Use_in_production_is_not_permitted___Please_report_misuse_to_legal@ag-grid.com___For_help_with_purchasing_a_production_key_please_contact_info@ag-grid.com___You_are_granted_a_{Single_Application}_Developer_License_for_one_application_only___All_Front-End_JavaScript_developers_working_on_the_application_would_need_to_be_licensed___This_key_will_deactivate_on_{18 June 2026}____[v3]_[0102]_MTc4MTczNzIwMDAwMA==d27c8a4487e577f42d9980e95824f43c"
);

// ─── Chart data ──────────────────────────────────────────────────────────────


// ─── Chart config helpers ─────────────────────────────────────────────────────

function makeSparklineConfig(data, color, monthLabels) {
    const safeData = data && data.length ? data : [0];
    const min = Math.min(...safeData), max = Math.max(...safeData);
    const pad = (max - min) * 0.2 || 1;
    const labels = monthLabels || [];
    return {
        chart: { type:"area", height:50, width:140, backgroundColor:"transparent", margin:[2,0,2,0], spacing:[0,0,0,0], animation:false },
        title:null, subtitle:{text:null}, credits:{enabled:false}, exporting:{enabled:false},
        xAxis:{visible:false}, yAxis:{visible:false, min:min-pad, max:max+pad},
        legend:{enabled:false},
        tooltip:{
            enabled:true, outside:true, backgroundColor:"white", borderColor:"#DFE3E8", borderRadius:6, style:{fontSize:"11px"},
            formatter: function() {
                const month = labels[this.point.index] || "";
                return month ? `<b>${month}:</b> ${this.y}` : `<b>${this.y}</b>`;
            },
        },
        plotOptions:{ area:{ fillColor:{ linearGradient:{x1:0,y1:0,x2:0,y2:1}, stops:[[0,Highcharts.color(color).setOpacity(0.25).get("rgba")],[1,Highcharts.color(color).setOpacity(0).get("rgba")]] }, lineWidth:2, marker:{enabled:false}, states:{hover:{enabled:true, lineWidth:2}}, enableMouseTracking:true } },
        series:[{data:safeData,color}],
    };
}

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
            type:"areaspline", height:220, backgroundColor:"transparent",
            style:{fontFamily:"Inter, -apple-system, sans-serif"},
            margin:[8,8,72,44],
        },
        title:null, credits:{enabled:false}, exporting:{enabled:false},
        xAxis:{
            categories,
            labels:{ style:{fontSize:"11px",color:"#8C9196"}, step: tickInterval },
            lineColor:"#DFE3E8", tickColor:"transparent",
        },
        yAxis:{title:null,labels:{style:{fontSize:"11px",color:"#8C9196"}},gridLineColor:"#F1F2F3",allowDecimals:false,min:0},
        legend:{align:"left",verticalAlign:"bottom",layout:"horizontal",itemStyle:{fontSize:"12px",fontWeight:"400",color:"#6D7175"},symbolRadius:4,margin:8,y:4},
        tooltip:{shared:true,backgroundColor:"white",borderColor:"#DFE3E8",borderRadius:8,style:{fontSize:"12px"}},
        plotOptions:{ areaspline:{ marker:{enabled:false}, lineWidth:2, fillOpacity:0.08 }, series:{ connectNulls:true } },
        series,
    };
}

function makeViolationsDonutConfig(violationsBySeverity) {
    return {
        chart:{type:"pie",height:200,backgroundColor:"transparent",style:{fontFamily:"Inter, -apple-system, sans-serif"},margin:[4,0,48,0]},
        title:null, credits:{enabled:false}, exporting:{enabled:false},
        tooltip:{pointFormat:"<b>{point.y}</b> ({point.percentage:.0f}%)",backgroundColor:"white",borderColor:"#DFE3E8",borderRadius:8,style:{fontSize:"12px"}},
        plotOptions:{pie:{innerSize:"55%",size:"85%",center:["50%","45%"],borderWidth:2,borderColor:"white",dataLabels:{enabled:false},showInLegend:true}},
        legend:{align:"center",verticalAlign:"bottom",itemStyle:{fontSize:"12px",fontWeight:"400",color:"#6D7175"},symbolRadius:4,margin:10},
        series:[{name:"Violations",data:violationsBySeverity || []}],
    };
}

// ─── Stat + chart cards ───────────────────────────────────────────────────────

function StatRow({ label, value, delta, sparklineData, color, valueColor, monthLabels }) {
    const opts = useMemo(() => makeSparklineConfig(sparklineData, color, monthLabels), [sparklineData, color, monthLabels]);
    return (
        <Box
            paddingInlineStart="5"
            paddingInlineEnd="5"
            paddingBlockStart="4"
            paddingBlockEnd="4"
        >
            <HorizontalStack align="space-between" blockAlign="center" gap="3">
                <VerticalStack gap="2">
                    <Text variant="headingSm" fontWeight="semibold">{label}</Text>
                    <Text variant="headingXl" fontWeight="bold" color={valueColor}>
                        {value.toLocaleString()}
                        {delta > 0 && <span style={{ fontSize:12, fontWeight:600, color:"#008060", marginLeft:6, verticalAlign:"middle" }}>+{delta}</span>}
                        {delta < 0 && <span style={{ fontSize:12, fontWeight:600, color:"#D72C0D", marginLeft:6, verticalAlign:"middle" }}>{delta}</span>}
                    </Text>
                </VerticalStack>
                <HighchartsReact highcharts={Highcharts} options={opts} />
            </HorizontalStack>
        </Box>
    );
}

function ChartPanel({ title, children }) {
    return (
        <Box padding="4">
            <VerticalStack gap="2">
                <Text variant="headingMd" fontWeight="semibold">{title}</Text>
                {children}
            </VerticalStack>
        </Box>
    );
}

function TopSection({ summary }) {
    const sparklines = summary?.statSparklines || {};
    const osTrend = summary?.osTrend || {};
    const osTrendOpts = useMemo(
        () => makeOsTrendConfig(osTrend, summary?.monthLabels),
        [summary?.osTrend, summary?.monthLabels]
    );
    const violationsDonutOpts = useMemo(
        () => makeViolationsDonutConfig(summary?.violationsBySeverity || []),
        [summary?.violationsBySeverity]
    );

    return (
        <HorizontalStack gap="4" align="start" blockAlign="stretch" wrap={false}>
            <div style={{ width: 320, flexShrink: 0, display: "flex", flexDirection: "column" }}>
                <Card padding="0">
                    <VerticalStack>
                        <StatRow label="Total Endpoints"  value={summary?.deviceCount ?? 0} delta={summary?.deltaEndpoints ?? 0} sparklineData={sparklines.endpoints || []}  color="#7C3AED" monthLabels={summary?.monthLabels} />
                        <Divider />
                        <StatRow label="Users"            value={summary?.totalUsers ?? 0} delta={summary?.deltaUsers ?? 0} sparklineData={sparklines.users || []}      color="#2563EB" monthLabels={summary?.monthLabels} />
                        <Divider />
                        <StatRow label="Total Violations" value={summary?.totalViolations ?? 0} delta={summary?.deltaViolations ?? 0} sparklineData={sparklines.violations || []} color="#DC2626" valueColor="critical" monthLabels={summary?.monthLabels} />
                    </VerticalStack>
                </Card>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
                <Card padding="0">
                    <ChartPanel title="Endpoints Over Time by OS Type">
                        <HighchartsReact highcharts={Highcharts} options={osTrendOpts} />
                    </ChartPanel>
                </Card>
            </div>
            <div style={{ width: 298, flexShrink: 0 }}>
                <Card padding="0">
                    <ChartPanel title="Violations by Severity">
                        <HighchartsReact highcharts={Highcharts} options={violationsDonutOpts} />
                    </ChartPanel>
                </Card>
            </div>
        </HorizontalStack>
    );
}

// ─── OS icon helpers ──────────────────────────────────────────────────────────
// SVGs live in public/ per CLAUDE.md — no inline SVG in component code.

function OsIcon({ os }) {
    if (os === "mac")     return <img src="/public/os-mac.svg"     width={15} height={15} alt="macOS"   style={{ flexShrink: 0 }} />;
    if (os === "windows") return <img src="/public/os-windows.svg" width={15} height={15} alt="Windows" style={{ flexShrink: 0 }} />;
    return                       <img src="/public/os-linux.svg"   width={15} height={15} alt="Linux"   style={{ flexShrink: 0 }} />;
}

// ─── Cell renderers ───────────────────────────────────────────────────────────

function TypeBadge({ type }) {
    if (!type) return null;
    const s = TYPE_STYLES[type] || { bg: "#F3F4F6", color: "#374151", border: "#E5E7EB" };
    return (
        <span style={{
            display: "inline-flex", alignItems: "center",
            padding: "1px 7px", borderRadius: 12,
            fontSize: 11, fontWeight: 500, lineHeight: "18px",
            background: s.bg, color: s.color,
            border: `1px solid ${s.border}`,
            whiteSpace: "nowrap",
        }}>
            {type}
        </span>
    );
}

function SkillBadge({ count }) {
    if (!count) return null;
    return (
        <span style={{
            display: "inline-flex", alignItems: "center",
            padding: "1px 7px", borderRadius: 12,
            fontSize: 11, fontWeight: 500, lineHeight: "18px",
            background: "#F3F4F6", color: "#374151",
            border: "1px solid #E5E7EB",
            whiteSpace: "nowrap",
        }}>
            {count} {count === 1 ? "skill" : "skills"}
        </span>
    );
}

function RiskScoreCellRenderer({ value }) {
    if (value == null) return null;
    const { bg, color } = getRiskColor(value);
    return (
        <HorizontalStack blockAlign="center">
            <span style={{
                display: "inline-flex", alignItems: "center", justifyContent: "center",
                width: 44, height: 24, borderRadius: 12,
                fontSize: 12, fontWeight: 600,
                background: bg, color,
            }}>
                {value.toFixed(1)}
            </span>
        </HorizontalStack>
    );
}

function ViolationsCellRenderer({ value }) {
    if (!value) return null;
    const parts = [
        { key: "critical", count: value.critical },
        { key: "high",     count: value.high     },
        { key: "medium",   count: value.medium   },
        { key: "low",      count: value.low      },
    ].filter(p => p.count > 0);
    if (!parts.length) return null;
    return (
        <HorizontalStack gap="1" blockAlign="center">
            {parts.map(p => (
                <span key={p.key} style={{
                    display: "inline-flex", alignItems: "center", justifyContent: "center",
                    minWidth: 22, height: 22, padding: "0 5px", borderRadius: 11,
                    fontSize: 11, fontWeight: 700,
                    background: SEVERITY_COLORS[p.key].bg,
                    color: SEVERITY_COLORS[p.key].text,
                }}>
                    {p.count}
                </span>
            ))}
        </HorizontalStack>
    );
}

// ─── Endpoint cell — uses AgGridRow as shared inner renderer ──────────────────

// Username cell renderer — used as innerRenderer of the auto-group (expand) column.
function UsernameCellInner({ data, node }) {
    if (!data) return null;
    const isLeaf = node.level > 0;
    if (isLeaf) {
        return (
            <AgGridRow
                label={data.endpoint}
                typeBadge={data.type}
                warning={data.skillCount ? <SkillBadge count={data.skillCount} /> : null}
            />
        );
    }
    const username = data.endpoint && data.endpoint !== "-" ? data.endpoint : null;
    return (
        <AgGridRow
            icon={<OsIcon os={data.os} />}
            label={username || "-"}
            isBold={!!username}
            warning={data.hasPersonalAccount ? <Icon source={CustomersMajor} color="critical" /> : null}
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
        width: 160,
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

// ─── Table section ────────────────────────────────────────────────────────────

function TableSection({ deviceFlatData, agentRiskData }) {
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
        setDeviceFlyout({ device, agents: findAgentsForDevice(deviceId) });
    }, [deviceFlatData, findDeviceRow, findAgentsForDevice]);

    const handleDeviceClickFromFlyout = useCallback((device) => {
        const deviceId = device?.path?.[0];
        if (!deviceId) return;
        setDeviceFlyout({ device, agents: findAgentsForDevice(deviceId) });
    }, [findAgentsForDevice]);

    const handleRowClick = useCallback((e) => {
        const { data, node } = e;
        if (!data) return;
        if (node.level === 0) {
            const deviceId = data.path[0];
            setDeviceFlyout({ device: data, agents: findAgentsForDevice(deviceId) });
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
                height={800}
                searchPlaceholder="Search..."
                searchOffset={400}
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
                sideBar={{ toolPanels: ["columns", "filters"] }}
            />

            <DeviceFlyout
                device={deviceFlyout?.device}
                agents={deviceFlyout?.agents}
                show={deviceFlyout !== null}
                onClose={closeAll}
                agentRiskData={agentRiskData}
            />
        </VerticalStack>
    );
}

// ─── Main component ───────────────────────────────────────────────────────────

const LAYOUT_KEY = "akto_agentic_new_ui";

export default function DeviceEndpoints() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [deviceFlatData, setDeviceFlatData] = useState([]);
    const [agentRiskData, setAgentRiskData] = useState({});
    const [summary, setSummary] = useState({});
    const [newLayout, setNewLayout] = useState(() => {
        const stored = localStorage.getItem(LAYOUT_KEY);
        return stored === null ? true : stored === "true";
    });

    const [currDateRange, dispatchCurrDateRange] = useReducer(
        produce((draft, action) => func.dateRangeReducer(draft, action)),
        values.ranges[4],
    );
    const startTimestamp = Math.floor(Date.parse(currDateRange.period.since) / 1000);
    const endTimestamp = Math.floor(Date.parse(currDateRange.period.until) / 1000);

    useEffect(() => {
        if (localStorage.getItem(LAYOUT_KEY) === "false") {
            navigate("/dashboard/observe/users-and-devices", { replace: true });
        }
    }, [navigate]);

    const handleLayoutToggle = useCallback((val) => {
        const checked = val === true;
        localStorage.setItem(LAYOUT_KEY, String(checked));
        setNewLayout(checked);
        if (!checked) navigate("/dashboard/observe/users-and-devices");
    }, [navigate]);

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
                    violationRows,
                ] = await Promise.all([
                    api.getAllCollectionsBasic(),
                    api.getLastTrafficSeen(),
                    api.getRiskScoreInfo(),
                    fetchEndpointShieldUserMetadata(),
                    agenticObserveApi.fetchAgenticViolations({ startTimestamp, endTimestamp }),
                ]);
                if (!isMountedRef.current) return;
                const { usernameMap = {}, userMetadataMap = {} } = shieldResult || {};
                const collections = apiCollectionsResp?.apiCollections || [];
                const pageData = buildDeviceEndpointsPageData(
                    collections,
                    trafficInfoResp || {},
                    riskScoreResp?.riskScoreOfCollectionsMap || {},
                    {
                        usernameMap,
                        violationsByCollectionId: aggregateViolationsByCollectionId(violationRows),
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
            } catch {
                if (isMountedRef.current) {
                    setDeviceFlatData([]);
                    setAgentRiskData({});
                    setSummary({});
                }
            } finally {
                if (isMountedRef.current) setLoading(false);
            }
        })();
        return () => { isMountedRef.current = false; };
    }, [startTimestamp, endTimestamp]);

    const headerActions = (
        <HorizontalStack gap="3" blockAlign="center">
            <Checkbox
                label="New UI"
                checked={newLayout}
                onChange={handleLayoutToggle}
            />
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
                />,
            ]}
        />
    );
}
