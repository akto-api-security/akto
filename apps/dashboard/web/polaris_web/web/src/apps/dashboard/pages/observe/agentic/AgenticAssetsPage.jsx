import React, {
  useState,
  useCallback,
  useEffect,
  useRef,
  useMemo,
  useReducer,
} from "react";
import { produce } from "immer";
import { useNavigate } from "react-router-dom";
import { Box, Card, Divider, HorizontalGrid, HorizontalStack, Text } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import AgenticAssetFlyout from "./AgenticAssetFlyout";
import {
  AssetNameCellRenderer,
  TypeBadgeCellRenderer,
  RiskScoreCellRenderer,
  ViolationsCellRenderer,
  InteractionsCellRenderer,
} from "./AgenticCellRenderers";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import "../../../components/layouts/style.css";
import NewLayoutTooltip from "./NewLayoutTooltip";
import api from "../api";
import agenticObserveApi, {
  fetchAgenticViolationCountsByHost,
  fetchViolationCountsByCollection,
  fetchAgenticSkillViolationCounts,
} from "./agenticObserveApi";
import {
  buildUserAnalysisLookup,
  buildUserAnalysisFlatMap,
  fetchAndCacheSkillApiData,
} from "./constants";
import PersistStore from "../../../../main/PersistStore";
import LocalStore from "../../../../main/LocalStorageStore";
import { fetchEndpointShieldUserMetadata } from "../api_collections/endpointShieldHelper";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import values from "@/util/values";
import func from "@/util/func";
import AgenticStatsCard from "./AgenticStatsCard";
import AgenticTopListCard from "./AgenticTopListCard";
import SmoothAreaChart from "@/apps/dashboard/pages/dashboard/new_components/SmoothChart";

// ─── Column definitions ───────────────────────────────────────────────────────

// AG Grid colId -> backend sortKey (see AgenticObserveAction.buildSummaryComparator)
const SORT_FIELD_MAP = {
  name: "name",
  riskScore: "riskScore",
  endpointCount: "endpointsCount",
  violations: "violations",
  lastSeen: "lastSeenEpoch",
};

const COL_DEFS = [
  {
    field: "name",
    headerName: "Agentic Assets",
    width: 460,
    minWidth: 200,
    pinned: "left",
    filter: false,
    cellRenderer: AssetNameCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "type",
    headerName: "Type",
    width: 140,
    // Fixed, small enum (AgenticObserveUtil.CLIENT_TYPE_*) — no need to derive values from data.
    filter: "agSetColumnFilter",
    filterParams: { values: ["AI Agent", "MCP Server", "LLM", "Skill"] },
    sortable: false,
    cellRenderer: TypeBadgeCellRenderer,
    cellClass: (p) => ({ "AI Agent": "agentic-type-AGENT", "MCP Server": "agentic-type-MCP", "LLM": "agentic-type-LLM", "Skill": "agentic-type-SKILL" })[p.value] || "agentic-type-DEFAULT",
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "riskScore",
    headerName: "Risk score",
    width: 110,
    filter: false,
    cellRenderer: RiskScoreCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "endpointCount",
    headerName: "Endpoints",
    width: 110,
    filter: false,
    cellStyle: {
      display: "flex",
      alignItems: "center",
      fontSize: 12,
      color: "#202223",
    },
    valueFormatter: (p) => (p.value != null ? p.value : ""),
  },
  {
    field: "aiInteractions",
    headerName: "AI Interactions",
    headerTooltip:
      "Total tokens from UserAnalysisData (input + output) across devices using this asset.",
    width: 150,
    filter: false,
    sortable: false,
    cellRenderer: InteractionsCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "violations",
    headerName: "Violations",
    width: 200,
    // Server-side sort via AgenticObserveAction.violationsTotalForGroup — not a stored field, so
    // it's computed from the already-fetched violationsByCollectionId/skillViolationsByName maps
    // rather than a real Mongo sort. Default sort (was riskScore, which ties ~770 skills at the
    // same score and buried every other asset type under them) — violations is a more actionable
    // "what needs attention first" ordering and doesn't have that tie problem.
    sort: "desc",
    sortIndex: 0,
    filter: false,
    cellRenderer: ViolationsCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "lastSeen",
    headerName: "Last Traffic Seen",
    width: 150,
    filter: false,
    // Backend already supports sorting by lastSeenEpoch
    cellStyle: {
      display: "flex",
      alignItems: "center",
      fontSize: 12,
      color: "#6D7175",
    },
  },
  {
    field: "tags",
    headerName: "Tags",
    hide: true,
    // Fixed set — matches shapeRow's own tag derivation below exactly. Values are pre-computed
    // string arrays on each row (array-valued field: a row matches if ANY selected tag is present).
    filter: "agSetColumnFilter",
    filterParams: { values: ["Contains personal account", "Local MCP Server", "Misconfigured", "Malicious Skill"] },
    sortable: false,
  },
  {
    field: "severity",
    headerName: "Severity",
    hide: true,
    // Filter-only, same pattern as "tags" above — drives the Violations card's Critical/High/
    // Medium/Low chips (handleSeverityClick), matched server-side against each group's own
    // violation counts (AgenticObserveAction.fetchAgenticAssetsSummary's "severity" filter branch).
    filter: "agSetColumnFilter",
    filterParams: { values: ["critical", "high", "medium", "low"] },
    sortable: false,
  },
];

const DEFAULT_COL_DEF = {
  sortable: true,
  resizable: true,
  filter: false,
  cellStyle: { display: "flex", alignItems: "center" },
};

// ─── Row shaping ──────────────────────────────────────────────────────────────
// Turns one server-computed row (AgenticObserveAction.fetchAgenticAssetsSummary) into the shape
// COL_DEFS/AgenticCellRenderers/AgenticAssetFlyout expect. `violations`/`isMalicious`/`groups`
// (team breakdown)/`aiInteractions` all come precomputed from the server now — a row's raw
// per-device list (up to hundreds of entries for a big group) used to be sent just so the browser
// could derive these few small values from it; a single 50-row page measured at 16MB. The full
// device list itself now comes from fetchAgenticAssetDetail (see AgenticAssetFlyout.jsx), fetched
// lazily only for the one asset a user actually opens. See
// AgenticObserveAction.GroupSummary.toSummaryResponse()'s and fetchAgenticAssetsSummary's row-loop
// comments.
function shapeRow(row) {
  const isSkill = row.rowType === "skill";
  const tags = [];
  if (row.hasPersonalAccount && !isSkill) tags.push("Contains personal account");
  if (row.hasLocalMcpServer && !isSkill) tags.push("Local MCP Server");
  if (row.hasMisconfiguredConfig && !isSkill) tags.push("Misconfigured");
  if (row.isMalicious) tags.push("Malicious Skill");

  return {
    ...row,
    type: row.clientType,
    endpointCount: row.endpointsCount,
    lastSeen: row.lastSeenEpoch > 0 ? func.prettifyEpoch(row.lastSeenEpoch) : "",
    assetTagValue: row.groupKey,
    tags: tags.length ? tags : undefined,
  };
}

// ─── Table section ────────────────────────────────────────────────────────────

function TableSection({
  onServerFetch,
  flyout,
  setFlyout,
  startTimestamp,
  endTimestamp,
  refreshKey,
  enrichMaps,
  gridRef,
}) {
  const didAutoOpenRef = useRef(false);

  // ?asset= deep link — best-effort: matches against whatever the grid has already fetched (the
  // first page, at minimum). A row further down the sorted list that hasn't loaded yet won't be
  // found; this only regressed the corner case (a very old exact link to a low-ranked asset), not
  // the common one (a link to something high-risk/recent, which sorts near the top by default).
  useEffect(() => {
    if (didAutoOpenRef.current) return;
    const params = new URLSearchParams(window.location.search);
    const assetName = params.get("asset");
    if (!assetName) return;
    const api2 = gridRef.current?.api;
    if (!api2) return;
    didAutoOpenRef.current = true;
    const decoded = decodeURIComponent(assetName.replace(/\+/g, " ")).toLowerCase();
    let found = null;
    api2.forEachNode((node) => {
      if (found || !node.data) return;
      const n = (node.data.name || "").toLowerCase();
      if (n === decoded || node.data.id === assetName) found = node.data;
    });
    if (found) setFlyout(found);
  }, [setFlyout]);

  const handleRowClick = useCallback(
    (e) => {
      if (!e.data) return;
      setFlyout(e.data);
    },
    [setFlyout],
  );

  const handleClose = useCallback(() => setFlyout(null), [setFlyout]);
  const handleNavigateToAsset = useCallback(
    (assetData) => setFlyout(assetData),
    [setFlyout],
  );
  const getRowStyle = useCallback(() => ({ cursor: "pointer" }), []);

  return (
    <>
      <AgGridTable
        key={`agentic-assets-grid-${startTimestamp}-${endTimestamp}-${refreshKey}`}
        gridRef={gridRef}
        columnDefs={COL_DEFS}
        defaultColDef={DEFAULT_COL_DEF}
        height={500}
        domLayout="normal"
        rowHeight={44}
        headerHeight={40}
        searchPlaceholder="Search agentic assets..."
        onRowClicked={handleRowClick}
        getRowStyle={getRowStyle}
        animateRows
        suppressCellFocus
        paginationPageSize={50}
        paginationPageSizeSelector={[20, 50, 100]}
        onServerFetch={onServerFetch}
        serverSideRowModel
        getRowId={(params) => params.data.id}
      />

      <AgenticAssetFlyout
        asset={flyout}
        show={flyout !== null}
        onClose={handleClose}
        onNavigateToAsset={handleNavigateToAsset}
        agenticTreeData={[]}
        agenticFlatData={[]}
        enrichMaps={enrichMaps}
        agenticViolationRows={undefined}
        startTimestamp={startTimestamp}
        endTimestamp={endTimestamp}
      />
    </>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function AgenticAssetsPage() {
  const navigate = useNavigate();
  const [flyout, setFlyout] = useState(null);
  const [loading, setLoading] = useState(true);
  const [hostSeverityCounts, setHostSeverityCounts] = useState({});
  const [stats, setStats] = useState({ totalAssets: 0, countsByType: {} });
  const [refreshKey, setRefreshKey] = useState(0);
  const newLayout = LocalStore((state) => state.agenticNewLayout);
  const setAgenticNewLayout = LocalStore((state) => state.setAgenticNewLayout);

  // Lifted from TableSection so the breakdown chips below can drive the grid's "type" filter.
  const gridRef = useRef(null);
  const [activeTypeFilter, setActiveTypeFilter] = useState(new Set());

  const handleAssetTypeClick = useCallback((key) => {
    setActiveTypeFilter((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      const gridApi = gridRef.current?.api;
      if (gridApi) {
        const values = [...next];
        const model = values.length > 0 ? { filterType: "set", values } : null;
        gridApi.setColumnFilterModel("type", model).then(() => gridApi.onFilterChanged());
      }
      return next;
    });
  }, []);

  // Same toggle pattern as handleAssetTypeClick above, driving the hidden "severity" column
  // filter instead of "type" — wires up the Violations card's Critical/High/Medium/Low chips.
  const [activeSeverityFilter, setActiveSeverityFilter] = useState(new Set());
  const handleSeverityClick = useCallback((key) => {
    setActiveSeverityFilter((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      const gridApi = gridRef.current?.api;
      if (gridApi) {
        const values = [...next];
        const model = values.length > 0 ? { filterType: "set", values } : null;
        gridApi.setColumnFilterModel("severity", model).then(() => gridApi.onFilterChanged());
      }
      return next;
    });
  }, []);

  // Everything shapeRow needs to enrich a server-returned page — populated once at mount, read
  // (not reacted to) by onServerFetch, which AG Grid SSRM calls directly rather than through React
  // re-renders.
  const enrichRef = useRef({
    violationsByCollectionId: {},
    skillViolationsByName: {},
    usernameMap: {},
    userMetadataMap: {},
    analysisByKey: new Map(),
    userAnalysisFlatMap: {},
  });

  useEffect(() => {
    if (!newLayout) {
      navigate("/dashboard/observe/agentic-assets-legacy", { replace: true });
    }
  }, [navigate]);

  // Date range — scopes inventory (last-seen), violations, and charts page-wide
  // Defaults to "All time" so this matches the legacy (unfiltered) Agentic assets page on
  // first load; MCP servers/LLMs are often detected via tags without directly-attributed
  // traffic, so a narrower default (e.g. Last 1 year) would silently drop them.
  const [currDateRange, dispatchCurrDateRange] = useReducer(
    produce((draft, action) => func.dateRangeReducer(draft, action)),
    values.ranges[5],
  );
  const rawStart = Math.floor(Date.parse(currDateRange.period.since) / 1000);
  const startTimestamp = rawStart <= 1 ? 0 : rawStart;
  const endTimestamp = Math.floor(
    Date.parse(currDateRange.period.until) / 1000,
  );

  const handleLayoutToggle = useCallback(
    (checked) => {
      setAgenticNewLayout(checked);
      if (!checked) navigate("/dashboard/observe/agentic-assets-legacy");
    },
    [navigate, setAgenticNewLayout],
  );

  const loadStats = useCallback(async () => {
    try {
      // trafficMap/riskScoreMap no longer sent — backend computes both itself when omitted.
      const { violationsByCollectionId, skillViolationsByName, userAnalysisFlatMap } = enrichRef.current;
      const result = await api.fetchAgenticAssetsStats({
        startTimestamp, endTimestamp, violationsByCollectionId, skillViolationsByName, userAnalysisFlatMap,
      });
      setStats(result);
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error("fetchAgenticAssetsStats failed:", e);
      setStats({ totalAssets: 0, countsByType: {} });
    }
  }, [startTimestamp, endTimestamp]);

  // Tier 1 (fast) mounts the grid; Tier 2 (slow, non-blocking) patches in violations/AI-interaction data after.
  useEffect(() => {
    // Skip on a cold mount that's about to redirect to legacy Endpoints.jsx.
    if (!newLayout) return;
    const isMountedRef = { current: true };

    (async () => {
      try {
        const shieldResult = await fetchEndpointShieldUserMetadata();
        if (!isMountedRef.current) return;

        const { usernameMap = {}, userMetadataMap = {} } = shieldResult || {};

        enrichRef.current = {
          ...enrichRef.current,
          usernameMap,
          userMetadataMap,
        };

        // The only grid remount — Tier 2 used to also bump this, causing an unwanted second refetch.
        setRefreshKey((k) => k + 1);
        setLoading(false);
        loadStats(); // fast pass; refined again after Tier 2

        // Warms the shared skillRiskScoreCache (PersistStore) that AgentEndpointTreeTable.jsx
        // (Inventory's Agent tree view) reads from — this page no longer needs the result itself:
        // "Malicious Skill" (row.isMalicious) and skill risk scores are now computed server-side in
        // fetchAgenticAssetsSummary (AgenticObserveAction.getOrBuildSkillData's own account-wide
        // cache), so there's nothing left to patch into enrichRef/onServerFetch here.
        fetchAndCacheSkillApiData([], { api, PersistStore }).catch(() => {});

        // Tier 2 — slow, runs after first paint, patches in without a grid remount.
        Promise.all([
          fetchAgenticViolationCountsByHost({ startTimestamp, endTimestamp }),
          fetchAgenticSkillViolationCounts({ startTimestamp, endTimestamp }),
          agenticObserveApi.listUserAnalysis().catch(() => []),
        ])
          .then(async ([hostCounts, skillViolationsByName, userAnalysisList]) => {
            if (!isMountedRef.current) return;
            // Host -> collection-id attribution now happens server-side (no raw collection list
            // needed client-side for this — see attributeViolationCountsToCollections). Skills
            // aren't attributable by host at all (see skillViolationsByName's own comment), so
            // fetchAgenticSkillViolationCounts above already returns them keyed by skill name.
            const violationsByCollectionId = await fetchViolationCountsByCollection(hostCounts);
            if (!isMountedRef.current) return;
            const analysisByKey = buildUserAnalysisLookup(userAnalysisList);
            enrichRef.current = {
              ...enrichRef.current,
              violationsByCollectionId,
              skillViolationsByName,
              analysisByKey,
              userAnalysisFlatMap: buildUserAnalysisFlatMap(analysisByKey),
            };
            setHostSeverityCounts(hostCounts);
            loadStats(); // refined pass — no setRefreshKey, so this never remounts the grid
          })
          .catch((e) => {
            // eslint-disable-next-line no-console
            console.error("AgenticAssetsPage tier-2 enrichment fetch failed:", e);
          });
      } catch (e) {
        // eslint-disable-next-line no-console
        console.error("AgenticAssetsPage mount fetch failed:", e);
        if (isMountedRef.current) {
          setHostSeverityCounts({});
          setLoading(false);
        }
      }
    })();
    return () => {
      isMountedRef.current = false;
    };
  }, [startTimestamp, endTimestamp, newLayout, loadStats]);

  // ─── Server-side data fetch for AG Grid ─────────────────────────────────────
  const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString, filters }) => {
    const pageSize = limit || 50;
    const mappedSortKey = SORT_FIELD_MAP[sortKey] || sortKey || "violations";
    // AG Grid SSRM sends sortOrder: -1 for asc, 1 for desc — opposite of the backend's Mongo
    // convention (1 asc / -1 desc, matching NhiGovernanceViolationsAction's own onServerFetch).
    const mongoSortOrder = sortOrder ? -sortOrder : -1;
    const { userAnalysisFlatMap, violationsByCollectionId, skillViolationsByName, usernameMap, userMetadataMap } = enrichRef.current;

    // trafficMap/riskScoreMap omitted — backend computes both server-side now.
    return api.fetchAgenticAssetsSummary({
      skip,
      limit: pageSize,
      sortKey: mappedSortKey,
      sortOrder: mongoSortOrder,
      queryValue: searchString || undefined,
      startTimestamp,
      endTimestamp,
      userAnalysisFlatMap,
      filters,
      // Precomputed account-wide already (attributeViolationCountsToCollections, via
      // fetchViolationCountsByCollection in the Tier-2 mount effect below) — passed straight through
      // so the server can compute each row's own violations total in-memory instead of the browser
      // needing every row's raw collectionIds list to do that sum itself.
      violationsByCollectionId,
      // Skill rows use this instead of violationsByCollectionId — a skill's declaring collection
      // is shared with the agent/device that invoked it, so collection-based attribution can't
      // give a skill its own count (see fetchAgenticSkillViolationCounts's own comment).
      skillViolationsByName,
      // Endpoint Shield maps, so the server can precompute each row's own Teams breakdown/AI
      // interactions total from its own per-device list, instead of sending that raw list (up to
      // hundreds of entries per row) just for the browser to derive these few small values.
      usernameMap,
      userMetadataMap,
    }).then((res) => ({
      value: (res.rows || []).map((row) => shapeRow(row)),
      total: res.total || 0,
    }));
  }, [startTimestamp, endTimestamp]);

  const headerActions = (
    <HorizontalStack gap="3" blockAlign="center">
      <NewLayoutTooltip checked={newLayout} onChange={handleLayoutToggle} />
      <DateRangeFilter
        initialDispatch={currDateRange}
        dispatch={(dateObj) =>
          dispatchCurrDateRange({
            type: "update",
            period: dateObj.period,
            title: dateObj.title,
            alias: dateObj.alias,
          })
        }
      />
    </HorizontalStack>
  );

  const pageTitle = (
    <TitleWithInfo
      tooltipContent="All agentic assets observed across your environment — AI Agents, MCP Servers, LLMs, and Skills."
      titleText="Agentic assets"
    />
  );

  const totalAssets = stats.totalAssets;

  const assetTypeBreakdown = useMemo(() => [
    { label: "Agents",      count: stats.countsByType["AI Agent"] || 0,   color: "#9642FC",  key: "AI Agent" },
    { label: "MCP Servers", count: stats.countsByType["MCP Server"] || 0, color: "#4cbebb",  key: "MCP Server" },
    { label: "LLMs",        count: stats.countsByType["LLM"] || 0,        color: "#EAB308",  key: "LLM" },
    { label: "Skills",      count: stats.countsByType["Skill"] || 0,      color: "#D1D5DB",  key: "Skill" },
  ], [stats]);

  // Summed from the server-aggregated per-host counts (available at first paint) — independent of
  // the paginated table, so these totals are exact regardless of which page is currently loaded.
  const violationTotals = useMemo(() => {
    const t = { crit: 0, high: 0, med: 0, low: 0 };
    Object.values(hostSeverityCounts).forEach((c) => {
      t.crit += c.critical || 0;
      t.high += c.high || 0;
      t.med  += c.medium || 0;
      t.low  += c.low || 0;
    });
    return { ...t, total: t.crit + t.high + t.med + t.low };
  }, [hostSeverityCounts]);

  const violBreakdown = useMemo(() => [
    { label: "Critical", key: "critical", count: violationTotals.crit, color: "#DC2626" },
    { label: "High",     key: "high",     count: violationTotals.high, color: "#F97316" },
    { label: "Medium",   key: "medium",   count: violationTotals.med,  color: "#EAB308" },
    { label: "Low",      key: "low",      count: violationTotals.low,  color: "#D1D5DB" },
  ], [violationTotals]);

  const topAppsRows = useMemo(() =>
    (stats.topUsedApplications || []).map((row) => ({
      ...row,
      renderValue: (r) => (
        <HorizontalStack align="end" blockAlign="center" wrap={false} gap="0">
          <Box minHeight="28px">
            <Text variant="bodyMd" alignment="end">{func.prettifyShort(r.aiInteractions)}</Text>
          </Box>
        </HorizontalStack>
      ),
    })), [stats.topUsedApplications]);

  const topViolRows = useMemo(() =>
    (stats.topAssetsWithViolations || []).map((row) => ({
      ...row,
      renderValue: (r) => (
        <HorizontalStack align="end" blockAlign="center" gap="3" wrap={false}>
          <Text variant="bodyMd">{func.prettifyShort(r.violations)}</Text>
          <SmoothAreaChart
            tickPositions={r.sparkline}
            color="#EF4444"
            height={28}
            width={100}
            labels={stats.monthLabels}
            enableHover
          />
        </HorizontalStack>
      ),
    })), [stats.topAssetsWithViolations, stats.monthLabels]);

  const topCards = useMemo(() => (
    <HorizontalGrid key="top-row" columns={3} gap="4">
      <Card padding="0">
        <Box className="agentic-stats-card-fill">
          <Box className="agentic-stats-card-item">
            <AgenticStatsCard
              title="Agentic Assets"
              total={totalAssets}
              delta={stats.assetDelta}
              sparklineCounts={stats.assetSparkline}
              sparklineLabels={stats.monthLabels}
              breakdown={assetTypeBreakdown}
              onFilterClick={handleAssetTypeClick}
              activeFilter={activeTypeFilter}
              noCard
            />
          </Box>
          <Divider />
          <Box className="agentic-stats-card-item">
            <AgenticStatsCard
              title="Violations"
              total={violationTotals.total}
              totalColor="critical"
              delta={stats.violationsDelta}
              sparklineCounts={stats.violationsSparkline}
              sparklineColor="#DC2626"
              sparklineLabels={stats.monthLabels}
              breakdown={violBreakdown}
              onFilterClick={handleSeverityClick}
              activeFilter={activeSeverityFilter}
              noCard
            />
          </Box>
        </Box>
      </Card>
      <AgenticTopListCard
        title="Top Used Applications"
        columns={[{ label: "Agentic Asset" }, { label: "AI Interactions" }]}
        rows={topAppsRows}
        emptyStateText="No AI interaction data yet."
      />
      <AgenticTopListCard
        title="Top Assets with Violations"
        columns={[{ label: "Agentic Asset" }, { label: "Violations" }]}
        rows={topViolRows}
        emptyStateText="No violations"
      />
    </HorizontalGrid>
  ), [totalAssets, assetTypeBreakdown, violationTotals, violBreakdown, stats, topAppsRows, topViolRows, handleAssetTypeClick, activeTypeFilter, handleSeverityClick, activeSeverityFilter]);

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
        topCards,
        <TableSection
          key="table"
          onServerFetch={onServerFetch}
          flyout={flyout}
          setFlyout={setFlyout}
          startTimestamp={startTimestamp}
          endTimestamp={endTimestamp}
          refreshKey={refreshKey}
          enrichMaps={enrichRef.current}
          gridRef={gridRef}
        />,
      ]}
    />
  );
}
