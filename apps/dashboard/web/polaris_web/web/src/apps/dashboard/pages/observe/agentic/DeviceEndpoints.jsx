import React, { useState, useMemo, useCallback, useRef, useEffect, useReducer } from "react";
import { useNavigate } from "react-router-dom";
import { produce } from "immer";
import { Card, Box, HorizontalStack, HorizontalGrid, VerticalStack, Text, Divider, Badge, Tooltip } from "@shopify/polaris";
import LaptopIcon from "@/assets/Laptop.svg";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import AgGridRow from "@/apps/dashboard/components/tables/rows/AgGridRow";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import DeviceFlyout from "./DeviceFlyout";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import { SeverityBadge, RiskPill } from "./AgenticCellRenderers";
import MisconfiguredBadge from "./MisconfiguredBadge";
import DonutChart from "../../../components/shared/DonutChart";
import AgenticStatsCard from "./AgenticStatsCard";
import { EndpointBrowserTrendChart } from "./TrendCharts";
import { aggregateViolationCountsByCollectionId, fetchAgenticViolationCountsByHost } from "./agenticObserveApi";
import { buildModuleDeviceMap } from "./agenticPageBuilders";
import { fetchEndpointShieldUserMetadata } from "../api_collections/endpointShieldHelper";
import { fetchAndCacheAgenticCollectionsBundle, settledValue, logRejected } from "./constants";
import NewLayoutTooltip from "./NewLayoutTooltip";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import values from "@/util/values";
import func from "@/util/func";
import api from "../api";
import LocalStore from "../../../../main/LocalStorageStore";
import PersistStore from "../../../../main/PersistStore";

// AG Grid colId -> backend sortKey (see AgenticObserveAction.buildHostGroupComparator).
const SORT_FIELD_MAP = { deviceId: "name", riskScore: "riskScore", lastTraffic: "lastSeenEpoch" };

// ─── Stat + chart cards ───────────────────────────────────────────────────────
function TopSection({ stats, violationsBySeverity, totalViolations }) {
    const sparklines = stats?.sparklines || {};
    const violationsChartData = useMemo(() => {
        const obj = {};
        (violationsBySeverity || []).forEach(({ name, y, color }) => { obj[name] = { text: y, color }; });
        return obj;
    }, [violationsBySeverity]);

    const violationsTitleColor = useMemo(() => {
        const order = ["Critical", "High", "Medium", "Low"];
        for (const sev of order) {
            if (violationsChartData[sev]?.text > 0) return violationsChartData[sev].color;
        }
        return undefined;
    }, [violationsChartData]);

    return (
        <HorizontalGrid columns="280px 1fr 260px" gap="4">
            <Card padding="0">
                <VerticalStack>
                    <AgenticStatsCard
                        title="Total Endpoints"
                        total={stats?.deviceCount ?? 0}
                        delta={stats?.deltaEndpoints ?? 0}
                        sparklineCounts={sparklines.endpoints}
                        sparklineColor="#7C3AED"
                        sparklineLabels={stats?.monthLabels}
                        noCard
                    />
                    <Divider />
                    <AgenticStatsCard
                        title="Browsers"
                        total={stats?.browserDeviceCount ?? 0}
                        delta={stats?.deltaBrowsers ?? 0}
                        sparklineCounts={sparklines.browsers}
                        sparklineColor="#4285F4"
                        sparklineLabels={stats?.monthLabels}
                        noCard
                    />
                    <Divider />
                    <AgenticStatsCard
                        title="Users"
                        total={stats?.totalUsers ?? 0}
                        delta={stats?.deltaUsers ?? 0}
                        sparklineCounts={sparklines.users}
                        sparklineColor="#2563EB"
                        sparklineLabels={stats?.monthLabels}
                        noCard
                    />
                    <Divider />
                    <AgenticStatsCard
                        title="Total Violations"
                        total={totalViolations ?? 0}
                        totalColor="critical"
                        delta={stats?.deltaViolations ?? 0}
                        sparklineCounts={sparklines.violations}
                        sparklineColor="#DC2626"
                        sparklineLabels={stats?.monthLabels}
                        noCard
                    />
                </VerticalStack>
            </Card>
            <Card padding="0">
                <EndpointBrowserTrendChart
                    osTrend={stats?.osTrend || {}}
                    browserTrend={stats?.browserTrend || {}}
                    monthLabels={stats?.monthLabels || []}
                />
            </Card>
            <Card padding="4">
                <VerticalStack gap="2">
                    <Text variant="headingMd" fontWeight="semibold">Violations by Severity</Text>
                    <HorizontalStack align="center">
                        <DonutChart
                            data={violationsChartData}
                            title={totalViolations ?? 0}
                            subtitle="Violations"
                            size={180}
                            pieInnerSize="55%"
                            titleColor={violationsTitleColor}
                        />
                    </HorizontalStack>
                    {Object.keys(violationsChartData).length > 0 && (
                        <HorizontalStack gap="3" wrap align="start">
                            {Object.entries(violationsChartData).map(([key, { text, color }]) => (
                                <HorizontalStack key={key} gap="1" blockAlign="center">
                                    <Box className="agentic-dot" style={{ "--dot-color": color }} />
                                    <Text variant="bodySm" color="subdued">{key} ({text})</Text>
                                </HorizontalStack>
                            ))}
                        </HorizontalStack>
                    )}
                </VerticalStack>
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

// ─── Cell renderers ───────────────────────────────────────────────────────────

function SkillBadge({ count }) {
    if (!count) return null;
    return <Badge>{`${count} ${count === 1 ? "skill" : "skills"}`}</Badge>;
}

// Agent rows only — how many plugins (their own sibling child rows, not embedded in the agent's
// own collection) belong to this agent. Gives the same "parent knows about its children" continuity
// the skills badge above gives, since a plugin's own row has no children of its own to show it in.
function PluginBadge({ count }) {
    if (!count) return null;
    return <Badge status="info">{`${count} ${count === 1 ? "plugin" : "plugins"}`}</Badge>;
}

function RiskScoreCellRenderer({ value }) {
    if (value == null) return null;
    return <RiskPill score={value} />;
}

// Compact "key=value" badge — just the first tag inline, rest collapsed behind a "+N"
// badge with a tooltip, same idiom as the classic Users & Devices page's Tags column.
const MAX_VISIBLE_TAGS = 1;
function TagsCellRenderer({ value }) {
    if (!value || value.length === 0) return null;
    const shown = value.slice(0, MAX_VISIBLE_TAGS);
    const rest = value.slice(MAX_VISIBLE_TAGS);
    return (
        <HorizontalStack gap="1" wrap={false} blockAlign="center">
            {shown.map((t, i) => (
                <Badge key={`${t.key}-${i}`} size="small" status="info">{`${t.key}=${t.value}`}</Badge>
            ))}
            {rest.length > 0 && (
                <Tooltip content={rest.map((t) => `${t.key}=${t.value}`).join(", ")} dismissOnMouseOut>
                    <Badge size="small" status="info">{`+${rest.length}`}</Badge>
                </Tooltip>
            )}
        </HorizontalStack>
    );
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

export const TYPE_CLASS_MAP = {
    "AI Agent": "agentic-type-AGENT",
    "MCP Server": "agentic-type-MCP",
    "LLM": "agentic-type-LLM",
    "Skill": "agentic-type-SKILL",
    "Plugin": "agentic-type-PLUGIN",
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
                        {data.pluginCount ? <PluginBadge count={data.pluginCount} /> : null}
                    </HorizontalStack>
                }
            />
        );
    }
    const username = data.username && data.username !== "-" ? data.username : null;
    return (
        <AgGridRow
            icon={<OsIcon os={data.os} />}
            label={username || "-"}
            isBold={!!username}
            warning={
                (data.hasPersonalAccount || data.hasMisconfiguredConfig) ? (
                    <HorizontalStack gap="1" blockAlign="center" wrap={false}>
                        {data.hasPersonalAccount && <Badge size="small" status="warning">Contains personal account</Badge>}
                        {data.hasMisconfiguredConfig && <MisconfiguredBadge />}
                    </HorizontalStack>
                ) : null
            }
        />
    );
}

// ─── Column definitions ───────────────────────────────────────────────────────

const DASH_FORMATTER = (params) => (params.value && params.value !== "-" ? params.value : "-");

// Set Filter values must be supplied up front under SSRM — AG Grid can't auto-derive them from a
// partial/paginated dataset the way it does in client-side row model. deviceId/os/group/role are
// all enumerable (known, server-computed lists — see filterOptions below), so they get real
// filters; lastTraffic doesn't (a timestamp, no sensible Set Filter shape).
function buildDeviceColDefs(filterOptions) {
    return [
        { field: "riskScore", headerName: "Risk score", width: 110, sort: "desc", filter: false, cellRenderer: RiskScoreCellRenderer },
        {
            field: "deviceId", headerName: "Endpoint", flex: 1.6, minWidth: 240, sortable: true,
            filter: "agSetColumnFilter", filterParams: { values: filterOptions.deviceId },
            valueFormatter: (params) => params.value || "-",
        },
        {
            field: "os", headerName: "OS", width: 100, hide: true, sortable: false,
            filter: "agSetColumnFilter", filterParams: { values: filterOptions.os },
        },
        {
            field: "tags", headerName: "Tags", flex: 1.2, minWidth: 160, sortable: false,
            // Server-computed (AgenticObserveAction.HostGroupSummary.toRow) — filterable by tag KEY
            // only, matching constants.js's buildTagFilterValues semantics ("group" matches any row
            // with a "group" tag, regardless of value).
            filter: "agSetColumnFilter", filterParams: { values: filterOptions.tags },
            filterValueGetter: (params) => params.data?.tagFilterValues || [],
            cellRenderer: TagsCellRenderer,
            valueGetter: (p) => p.data?.tags || [],
        },
        { field: "violations", headerName: "Violations", width: 200, sortable: false, filter: false, cellRenderer: ViolationsCellRenderer },
        { field: "lastTraffic", headerName: "Last Traffic", width: 130, filter: false, valueFormatter: DASH_FORMATTER },
    ];
}

const DEFAULT_COL_DEF = {
    sortable: true,
    resizable: true,
    filter: true,
    cellStyle: { display: "flex", alignItems: "center", fontSize: 13, color: "#202223" },
};

// Backend row field names don't match the grid's column defs directly — device (parent) rows carry
// lastSeenEpoch (AgenticObserveAction.HostGroupSummary.toRow), service (child) rows carry
// lastTrafficEpoch (buildDeviceChildren). Reshape both into the lastTraffic the columns actually read.
function shapeRow(row) {
    if (!row) return row;
    const epoch = row.lastSeenEpoch ?? row.lastTrafficEpoch;
    return {
        ...row,
        lastTraffic: epoch > 0 ? func.prettifyEpoch(epoch) : "",
    };
}

function getHostNamesForDevice(deviceId, collections) {
    if (!deviceId || !collections?.length) return [];
    const prefix = deviceId + ".";
    return collections.filter(c => c.hostName && c.hostName.startsWith(prefix)).map(c => c.hostName);
}

// ─── Table section ────────────────────────────────────────────────────────────

function TableSection({ onServerFetch, fetchDeviceChildren, collections, startTimestamp, endTimestamp, refreshKey, filterOptions }) {
    const [selectedCount, setSelectedCount] = useState(0);
    const deviceColDefs = useMemo(() => buildDeviceColDefs(filterOptions), [filterOptions]);
    const [deviceFlyout, setDeviceFlyout] = useState(null);
    const gridRef = useRef(null);

    const closeAll = useCallback(() => setDeviceFlyout(null), []);

    const openDeviceFlyout = useCallback(async (deviceData) => {
        const deviceId = deviceData.deviceId;
        // Full, unpaginated — needed for the Overview tab's topology graph/counts. The flyout's own
        // "Agentic Assets" tab fetches its own paginated page independently (AgenticsTab's
        // onServerFetch in DeviceFlyout.jsx), same as the main grid's tree-expand rows.
        const children = await fetchDeviceChildren(deviceId);
        setDeviceFlyout({
            device: deviceData,
            agents: children,
            hostNames: getHostNamesForDevice(deviceId, collections),
        });
    }, [fetchDeviceChildren, collections]);

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const deviceId = params.get("device");
        if (!deviceId) return;
        // Deep link (?device=...): fetch the device's own row the same way the grid does -
        // {deviceId, path:[deviceId]} alone has no username/os/lastSeen/violations, which used
        // to render the flyout with every field blank. deviceId has its own exact-match column
        // filter in the grid (buildDeviceColDefs), so it's a real, already-supported filter key.
        onServerFetch({ skip: 0, limit: 1, filters: { deviceId: [deviceId] } }).then((res) => {
            openDeviceFlyout(res?.value?.[0] || { deviceId, path: [deviceId] });
        });
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const handleRowClick = useCallback((e) => {
        const { data, node } = e;
        if (!data) return;
        if (node.level === 0) {
            openDeviceFlyout(data);
            return;
        }
        const assetId = data.rawServiceName || data.endpoint;
        const params = new URLSearchParams({ asset: assetId, type: data.type || "" });
        window.location.href = `/dashboard/observe/agentic-assets?${params}`;
    }, [openDeviceFlyout]);

    const isServerSideGroup = useCallback((data) => (data.path || []).length === 1, []);
    const getServerSideGroupKey = useCallback((data) => data.path[0], []);

    const autoGroupColumnDef = useMemo(() => ({
        headerName: "Username",
        width: 320,
        minWidth: 180,
        pinned: "left",
        checkboxSelection: true,
        headerCheckboxSelection: true,
        filter: false,
        cellRendererParams: {
            suppressCount: true,
            innerRenderer: UsernameCellInner,
        },
        cellStyle: { display: "flex", alignItems: "center" },
    }), []);

    return (
        <VerticalStack gap="0">
            <AgGridTable
                key={`device-endpoints-grid-${startTimestamp}-${endTimestamp}-${refreshKey}`}
                gridRef={gridRef}
                columnDefs={deviceColDefs}
                defaultColDef={DEFAULT_COL_DEF}
                autoGroupColumnDef={autoGroupColumnDef}
                treeData
                isServerSideGroup={isServerSideGroup}
                getServerSideGroupKey={getServerSideGroupKey}
                groupDefaultExpanded={0}
                onServerFetch={onServerFetch}
                onFetchError={() => func.setToast(true, true, "Failed to load endpoints")}
                serverSideRowModel
                getRowId={(params) => params.data.id}
                height={500}
                domLayout="normal"
                searchPlaceholder="Search..."
                bulkActionCount={selectedCount}
                bulkActions={[]}
                onClearBulk={() => { gridRef.current?.api?.deselectAll(); setSelectedCount(0); }}
                onRowClicked={handleRowClick}
                getRowStyle={() => ({ cursor: "pointer" })}
                onSelectionChanged={e => setSelectedCount(e.api.getSelectedRows().length)}
                rowSelection="multiple"
                suppressRowClickSelection
                paginationPageSize={20}
                paginationPageSizeSelector={[20, 50, 100]}
            />

            <DeviceFlyout
                device={deviceFlyout?.device}
                agents={deviceFlyout?.agents}
                show={deviceFlyout !== null}
                onClose={closeAll}
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
    const [collections, setCollections] = useState([]);
    const [stats, setStats] = useState({ deviceCount: 0, browserDeviceCount: 0, totalUsers: 0, deviceIds: [], monthLabels: [], osTrend: {}, browserTrend: {}, sparklines: {} });
    const [violationsBySeverity, setViolationsBySeverity] = useState([]);
    const totalViolations = useMemo(
        () => violationsBySeverity.reduce((sum, s) => sum + (s.y || 0), 0),
        [violationsBySeverity],
    );
    const [refreshKey, setRefreshKey] = useState(0);
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

    const handleLayoutToggle = useCallback((val) => {
        const checked = val === true;
        setAgenticNewLayout(checked);
        if (!checked) navigate("/dashboard/observe/users-and-devices");
    }, [navigate, setAgenticNewLayout]);

    const enrichRef = useRef({
        trafficMap: {}, riskScoreMap: {}, usernameMap: {}, userMetadataMap: {},
        deviceMetadataMap: {}, violationsByCollectionId: {},
    });

    useEffect(() => {
        const isMountedRef = { current: true };
        (async () => {
            try {
                // allSettled, not all: every state setter below sits after this await, so one rejected
                // member used to blank the whole grid instead of just its own column.
                const [collectionsSettled, shieldSettled, hostCountsSettled] = await Promise.allSettled([
                    fetchAndCacheAgenticCollectionsBundle({ api, PersistStore }),
                    fetchEndpointShieldUserMetadata(),
                    // Server-aggregated {host: {critical,high,medium,low}} — same aggregate Agentic
                    // Assets uses; no raw-event fetch needed for either the column or the total.
                    fetchAgenticViolationCountsByHost({ startTimestamp, endTimestamp }),
                ]);
                if (!isMountedRef.current) return;
                logRejected("DeviceEndpoints mount", { collections: collectionsSettled, shield: shieldSettled, violations: hostCountsSettled });
                const collectionsBundle = settledValue(collectionsSettled, {});
                const shieldResult = settledValue(shieldSettled, {});
                const hostCounts = settledValue(hostCountsSettled, {});
                const { collections = [], trafficMap = {}, riskScoreMap = {} } = collectionsBundle || {};
                const { usernameMap = {}, userMetadataMap = {}, moduleInfos = [] } = shieldResult || {};
                const violationsByCollectionId = aggregateViolationCountsByCollectionId(hostCounts, collections);
                const deviceMetadataMap = buildModuleDeviceMap(moduleInfos);
                const severitySums = { critical: 0, high: 0, medium: 0, low: 0 };
                Object.values(hostCounts).forEach((c) => {
                    severitySums.critical += c.critical || 0;
                    severitySums.high += c.high || 0;
                    severitySums.medium += c.medium || 0;
                    severitySums.low += c.low || 0;
                });
                enrichRef.current = {
                    trafficMap, riskScoreMap, usernameMap, userMetadataMap, deviceMetadataMap, violationsByCollectionId,
                };
                setCollections(collections);
                setViolationsBySeverity([
                    { name: "Critical", y: severitySums.critical, color: "#DC2626" },
                    { name: "High", y: severitySums.high, color: "#F97316" },
                    { name: "Medium", y: severitySums.medium, color: "#EAB308" },
                    { name: "Low", y: severitySums.low, color: "#D1D5DB" },
                ]);
                setLoading(false);
                setRefreshKey((k) => k + 1);
            } catch (e) {
                // eslint-disable-next-line no-console
                console.error("DeviceEndpoints mount fetch failed:", e);
                if (isMountedRef.current) {
                    setCollections([]);
                    setLoading(false);
                }
            }
        })();
        return () => { isMountedRef.current = false; };
    }, [startTimestamp, endTimestamp]);

    const loadStats = useCallback(async () => {
        try {
            const { usernameMap, deviceMetadataMap } = enrichRef.current;
            const result = await api.fetchDeviceEndpointsStats({ usernameMap, deviceMetadataMap, startTimestamp, endTimestamp });
            setStats(result);
        } catch (e) {
            // eslint-disable-next-line no-console
            console.error("fetchDeviceEndpointsStats failed:", e);
        }
    }, [startTimestamp, endTimestamp]);

    useEffect(() => {
        if (refreshKey === 0) return;
        loadStats();
    }, [loadStats, refreshKey]);

    // Distinct deviceId/os values for the grid's Set Filters. os comes from deviceMetadataMap —
    // already the full, unpaginated account-wide map (fetched once at mount via
    // fetchEndpointShieldUserMetadata) — so no extra network call. deviceId comes from
    // fetchDeviceEndpointsStats's own deviceIds list (arrives slightly later, once loadStats
    // resolves) rather than being derived client-side, since it must match the grid's own
    // extractEndpointId(hostName) grouping key exactly — safer to reuse the server's computation
    // than re-derive it in the browser and risk drift.
    const filterOptions = useMemo(() => {
        const meta = Object.values(enrichRef.current.deviceMetadataMap || {});
        const os = new Set();
        meta.forEach((m) => {
            if (m.os) os.add(m.os);
        });
        // Distinct device-tag keys — same source (userMetadataMap, full/unpaginated) as os above,
        // just keyed by username instead of deviceId.
        const tags = new Set();
        Object.values(enrichRef.current.userMetadataMap || {}).forEach((u) => {
            (u.tags || []).forEach((t) => { if (t?.key) tags.add(t.key); });
        });
        return {
            deviceId: stats.deviceIds || [],
            os: Array.from(os).sort(),
            tags: Array.from(tags).sort(),
        };
    }, [refreshKey, stats.deviceIds]);

    const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString, groupKeys, filters }) => {
        const { trafficMap, riskScoreMap, usernameMap, userMetadataMap, deviceMetadataMap, violationsByCollectionId } = enrichRef.current;
        const mappedSortKey = SORT_FIELD_MAP[sortKey] || "riskScore";
        const mongoSortOrder = sortOrder === -1 ? 1 : -1; // AG Grid asc=-1/desc=1 is inverted vs Mongo
        const parentDeviceId = groupKeys && groupKeys.length === 1 ? groupKeys[0] : undefined;
        const tagsByUsername = Object.fromEntries(
            Object.entries(userMetadataMap || {}).map(([u, m]) => [u, m.tags || []])
        );
        return api.fetchDeviceEndpointsSummary({
            parentDeviceId, skip, limit, sortKey: mappedSortKey, sortOrder: mongoSortOrder, queryValue: searchString,
            trafficMap, riskScoreMap, usernameMap, deviceMetadataMap, violationsByCollectionId, filters, tagsByUsername,
        }).then((res) => ({ value: (res.rows || []).map(shapeRow), total: res.total || 0 }));
    }, []);

    const fetchDeviceChildren = useCallback(async (deviceId) => {
        const { trafficMap, riskScoreMap, violationsByCollectionId } = enrichRef.current;
        // This caller wants the device's WHOLE children list (Overview tab's topology graph/counts,
        // and the flyout's own tab-count label) — explicitly ask for the server's max page size
        // (fetchDeviceEndpointsSummary's parentDeviceId branch now paginates; a device's own
        // accessible assets are bounded well under 500 in practice, unlike AgenticsTab's own grid
        // fetch, which intentionally pages 20 at a time).
        const res = await api.fetchDeviceEndpointsSummary({ parentDeviceId: deviceId, limit: 500, trafficMap, riskScoreMap, violationsByCollectionId });
        return (res.rows || []).map(shapeRow);
    }, []);

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
                <TopSection key="top" stats={stats} violationsBySeverity={violationsBySeverity} totalViolations={totalViolations} />,
                <TableSection
                    key="table"
                    onServerFetch={onServerFetch}
                    fetchDeviceChildren={fetchDeviceChildren}
                    collections={collections}
                    startTimestamp={startTimestamp}
                    endTimestamp={endTimestamp}
                    refreshKey={refreshKey}
                    filterOptions={filterOptions}
                />,
            ]}
        />
    );
}
