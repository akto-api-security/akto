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
  GroupCellRenderer,
} from "./AgenticCellRenderers";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import "../../../components/layouts/style.css";
import NewLayoutTooltip from "./NewLayoutTooltip";
import api from "../api";
import agenticObserveApi, {
  aggregateViolationCountsByCollectionId,
  fetchAgenticViolationCountsByHost,
} from "./agenticObserveApi";
import {
  buildUserAnalysisLookup,
  getRowViolations,
  buildTeamGroupsFromDevices,
  computeAiInteractionsFromDevices,
  fetchAndCacheSkillApiData,
  skillCollectionKey,
  fetchAndCacheAgenticCollectionsBundle,
} from "./constants";
import PersistStore from "../../../../main/PersistStore";
import LocalStore from "../../../../main/LocalStorageStore";
import { fetchEndpointShieldUserMetadata } from "../api_collections/endpointShieldHelper";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import values from "@/util/values";
import func from "@/util/func";
import AgenticStatsCard from "./AgenticStatsCard";

// ─── Column definitions ───────────────────────────────────────────────────────

// AG Grid colId -> backend sortKey (see AgenticObserveAction.buildSummaryComparator)
const SORT_FIELD_MAP = {
  name: "name",
  riskScore: "riskScore",
  endpointCount: "endpointsCount",
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
    filter: false,
    sortable: false,
    cellRenderer: TypeBadgeCellRenderer,
    cellClass: (p) => ({ "AI Agent": "agentic-type-AGENT", "MCP Server": "agentic-type-MCP", "LLM": "agentic-type-LLM", "Skill": "agentic-type-SKILL" })[p.value] || "agentic-type-DEFAULT",
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "riskScore",
    headerName: "Risk score",
    width: 110,
    sort: "desc",
    sortIndex: 0,
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
    sortable: false,
    filter: false,
    cellRenderer: ViolationsCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "groups",
    headerName: "Group",
    flex: 1,
    minWidth: 160,
    sortable: false,
    filter: false,
    cellRenderer: GroupCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "lastSeen",
    headerName: "Last Traffic Seen",
    width: 150,
    filter: false,
    sortable: false,
    cellStyle: {
      display: "flex",
      alignItems: "center",
      fontSize: 12,
      color: "#6D7175",
    },
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
// COL_DEFS/AgenticCellRenderers/AgenticAssetFlyout expect. Team breakdown, AI-interaction totals and
// violations are computed here, client-side, scoped to just this row's device list — cheap, since
// it's bounded by however many rows are on the current page, not the account's full ~800 groups (see
// atlas-scale-test/DASHBOARD_OPTIMIZATION.md's "paginated server-side aggregation rebuild" entry for
// why that distinction is the whole point).
function shapeRow(row, { violationsByCollectionId, usernameMap, userMetadataMap, analysisByKey, userAnalysisKeysByDeviceId, maliciousSkillKeys }) {
  const devices = row.devices || [];
  const violations = getRowViolations(row.collectionIds, violationsByCollectionId);
  const groups = buildTeamGroupsFromDevices(devices, usernameMap, userMetadataMap);
  const aiInteractions = computeAiInteractionsFromDevices(devices, analysisByKey, userAnalysisKeysByDeviceId);
  const isSkill = row.rowType === "skill";
  const tags = [];
  if (row.hasPersonalAccount && !isSkill) tags.push("Contains personal account");
  if (row.hasLocalMcpServer && !isSkill) tags.push("Local MCP Server");
  if (row.hasMisconfiguredConfig && !isSkill) tags.push("Misconfigured");
  // Collection-scoped so a same-named skill belonging to a different user/agent doesn't mark this
  // one malicious too (see skillCollectionKey in constants.js).
  const isMalicious = isSkill && (row.collectionIds || []).some((cid) => maliciousSkillKeys?.has(skillCollectionKey(cid, row.name)));
  if (isMalicious) tags.push("Malicious Skill");

  return {
    ...row,
    type: row.clientType,
    endpointCount: row.endpointsCount,
    lastSeen: row.lastSeenEpoch > 0 ? func.prettifyEpoch(row.lastSeenEpoch) : "",
    assetTagValue: row.groupKey,
    isMalicious,
    violations,
    groups: groups.length ? groups : undefined,
    aiInteractions: aiInteractions?.total,
    aiInteractionsDetail: aiInteractions
      ? { totalInputTokens: aiInteractions.totalInputTokens, totalOutputTokens: aiInteractions.totalOutputTokens }
      : undefined,
    devices,
    tags: tags.length ? tags : undefined,
  };
}

// ─── Table section ────────────────────────────────────────────────────────────

function TableSection({
  onServerFetch,
  flyout,
  setFlyout,
  collections,
  startTimestamp,
  endTimestamp,
  refreshKey,
}) {
  const gridRef = useRef(null);
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
        assetDevices={flyout ? { [flyout.id]: flyout.devices || [] } : {}}
        collections={collections}
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
  const [collections, setCollections] = useState([]);
  const [hostSeverityCounts, setHostSeverityCounts] = useState({});
  const [stats, setStats] = useState({ totalAssets: 0, countsByType: {} });
  const [refreshKey, setRefreshKey] = useState(0);
  const newLayout = LocalStore((state) => state.agenticNewLayout);
  const setAgenticNewLayout = LocalStore((state) => state.setAgenticNewLayout);

  // Everything shapeRow needs to enrich a server-returned page — populated once at mount, read
  // (not reacted to) by onServerFetch, which AG Grid SSRM calls directly rather than through React
  // re-renders.
  const enrichRef = useRef({
    violationsByCollectionId: {},
    usernameMap: {},
    userMetadataMap: {},
    analysisByKey: new Map(),
    userAnalysisKeysByDeviceId: new Map(),
    maliciousSkillKeys: new Set(),
    trafficMap: {},
    riskScoreMap: {},
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
      const { trafficMap, riskScoreMap } = enrichRef.current;
      const result = await api.fetchAgenticAssetsStats({ trafficMap, riskScoreMap, startTimestamp, endTimestamp });
      setStats(result);
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error("fetchAgenticAssetsStats failed:", e);
      setStats({ totalAssets: 0, countsByType: {} });
    }
  }, [startTimestamp, endTimestamp]);

  // Mount-time fetch of everything shapeRow/stats need but the paginated table endpoint doesn't
  // carry itself, split into two tiers so the page's first paint isn't gated on the slowest call:
  //   Tier 1 (blocks first paint, both calls measured <500ms): the collections/traffic/risk bundle
  //     (also needed for trafficMap/riskScoreMap, which the date-range filter's maxTrafficTimestamp
  //     check depends on) and Endpoint Shield username/team data. As soon as these land, the grid
  //     mounts and fires its own (fast, paginated) fetch.
  //   Tier 2 (patches in after, non-blocking — measured up to ~6s, proxies through
  //     threat-detection-backend): server-aggregated violation counts and the account-wide
  //     AI-interaction list. Deliberately does NOT force a grid remount/re-fetch to patch these in
  //     (same reasoning as the malicious-skill flag below) — they apply on the next natural fetch
  //     (page change, sort, search, date-range change) instead of paying a second full round trip.
  useEffect(() => {
    const isMountedRef = { current: true };

    (async () => {
      try {
        const [collectionsBundle, shieldResult] = await Promise.all([
          fetchAndCacheAgenticCollectionsBundle({ api, PersistStore }),
          fetchEndpointShieldUserMetadata(),
        ]);
        if (!isMountedRef.current) return;

        const { collections = [], trafficMap = {}, riskScoreMap = {} } = collectionsBundle || {};
        const { usernameMap = {}, userMetadataMap = {}, userAnalysisKeysByDeviceId = new Map() } = shieldResult || {};

        enrichRef.current = {
          ...enrichRef.current,
          usernameMap,
          userMetadataMap,
          userAnalysisKeysByDeviceId,
          trafficMap,
          riskScoreMap,
        };

        setCollections(collections);
        setRefreshKey((k) => k + 1); // (re)mount the grid now that enrichRef is populated
        setLoading(false);

        // Malicious-skill flag — account-wide, single call, non-blocking, patched in on the next
        // natural fetch rather than forcing a grid remount (see comment above). Keyed by
        // maliciousSkillKeys (collection-scoped `<collectionId>|<skillName>`, see skillCollectionKey)
        // rather than skill name alone, so a same-named skill belonging to a different user/agent
        // doesn't mark this one malicious too.
        fetchAndCacheSkillApiData([], { api, PersistStore })
          .then(({ maliciousSkillKeys }) => {
            if (!isMountedRef.current || !maliciousSkillKeys?.size) return;
            enrichRef.current = { ...enrichRef.current, maliciousSkillKeys };
          })
          .catch(() => {});

        // Tier 2 — slow, runs after first paint, patches in without a grid remount.
        Promise.all([
          fetchAgenticViolationCountsByHost({ startTimestamp, endTimestamp }),
          agenticObserveApi.listUserAnalysis().catch(() => []),
        ])
          .then(([hostCounts, userAnalysisList]) => {
            if (!isMountedRef.current) return;
            const violationsByCollectionId = aggregateViolationCountsByCollectionId(hostCounts, collections);
            enrichRef.current = {
              ...enrichRef.current,
              violationsByCollectionId,
              analysisByKey: buildUserAnalysisLookup(userAnalysisList),
            };
            setHostSeverityCounts(hostCounts);
          })
          .catch((e) => {
            // eslint-disable-next-line no-console
            console.error("AgenticAssetsPage tier-2 enrichment fetch failed:", e);
          });
      } catch (e) {
        // eslint-disable-next-line no-console
        console.error("AgenticAssetsPage mount fetch failed:", e);
        if (isMountedRef.current) {
          setCollections([]);
          setHostSeverityCounts({});
          setLoading(false);
        }
      }
    })();
    return () => {
      isMountedRef.current = false;
    };
  }, [startTimestamp, endTimestamp]);

  useEffect(() => {
    // refreshKey starts at 0 and only becomes meaningful once the mount effect above has populated
    // enrichRef (trafficMap/riskScoreMap) and bumped it — skip the otherwise-automatic call this
    // effect would make on first render, which would run against still-empty enrichment data.
    if (refreshKey === 0) return;
    loadStats();
  }, [loadStats, refreshKey]);

  // ─── Server-side data fetch for AG Grid ─────────────────────────────────────
  const onServerFetch = useCallback(({ sortKey, sortOrder, skip, limit, searchString }) => {
    const pageSize = limit || 50;
    const mappedSortKey = SORT_FIELD_MAP[sortKey] || sortKey || "riskScore";
    // AG Grid SSRM sends sortOrder: -1 for asc, 1 for desc — opposite of the backend's Mongo
    // convention (1 asc / -1 desc, matching NhiGovernanceViolationsAction's own onServerFetch).
    const mongoSortOrder = sortOrder ? -sortOrder : -1;
    const { trafficMap, riskScoreMap } = enrichRef.current;

    return api.fetchAgenticAssetsSummary({
      skip,
      limit: pageSize,
      sortKey: mappedSortKey,
      sortOrder: mongoSortOrder,
      queryValue: searchString || undefined,
      trafficMap,
      riskScoreMap,
      startTimestamp,
      endTimestamp,
    }).then((res) => ({
      value: (res.rows || []).map((row) => shapeRow(row, enrichRef.current)),
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

  const topCards = useMemo(() => (
    <HorizontalGrid key="top-row" columns={2} gap="4">
      <Card padding="0">
        <Box className="agentic-stats-card-fill">
          <Box className="agentic-stats-card-item">
            <AgenticStatsCard
              title="Agentic Assets"
              total={totalAssets}
              breakdown={assetTypeBreakdown}
              noCard
            />
          </Box>
          <Divider />
          <Box className="agentic-stats-card-item">
            <AgenticStatsCard
              title="Violations"
              total={violationTotals.total}
              totalColor="critical"
              breakdown={violBreakdown}
              noCard
            />
          </Box>
        </Box>
      </Card>
    </HorizontalGrid>
  ), [totalAssets, assetTypeBreakdown, violationTotals, violBreakdown]);

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
          collections={collections}
          startTimestamp={startTimestamp}
          endTimestamp={endTimestamp}
          refreshKey={refreshKey}
        />,
      ]}
    />
  );
}
