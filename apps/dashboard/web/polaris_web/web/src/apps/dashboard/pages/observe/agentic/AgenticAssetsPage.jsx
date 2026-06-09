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
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { LicenseManager, AllEnterpriseModule } from "ag-grid-enterprise";
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
import { cumulativeByMonth } from "./agenticPageBuilders";
import SmoothAreaChart from "@/apps/dashboard/pages/dashboard/new_components/SmoothChart";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import "../../../components/layouts/style.css";
import NewLayoutTooltip from "./NewLayoutTooltip";
import api from "../api";
import agenticObserveApi, {
  aggregateViolationsByCollectionId,
} from "./agenticObserveApi";
import {
  buildAgenticAssetsPageData,
  buildUserAnalysisLookup,
  fetchAndCacheSkillApiData,
} from "./constants";
import PersistStore from "../../../../main/PersistStore";
import { fetchEndpointShieldUserMetadata } from "../api_collections/endpointShieldHelper";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import values from "@/util/values";
import func from "@/util/func";
import AgenticStatsCard from "./AgenticStatsCard";
import AgenticTopListCard from "./AgenticTopListCard";

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);

// ─── Column definitions ───────────────────────────────────────────────────────

const COL_DEFS = [
  {
    field: "name",
    headerName: "Agentic Assets",
    width: 460,
    minWidth: 200,
    pinned: "left",
    filter: "agTextColumnFilter",
    cellRenderer: AssetNameCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "type",
    headerName: "Type",
    width: 140,
    // agSetColumnFilter gives the checkbox list matching the 3rd screenshot
    filter: "agSetColumnFilter",
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
    cellRenderer: InteractionsCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "violations",
    headerName: "Violations",
    width: 160,
    sortable: true,
    filter: false,
    cellRenderer: ViolationsCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
    comparator: (a, b) => {
      const sum = (v) =>
        v
          ? (v.critical || 0) + (v.high || 0) + (v.medium || 0) + (v.low || 0)
          : 0;
      return sum(a) - sum(b);
    },
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
    // Sort chronologically on the raw epoch, not the prettified string ("8 days ago" etc.)
    comparator: (valA, valB, nodeA, nodeB) =>
      (nodeA?.data?.lastSeenEpoch || 0) - (nodeB?.data?.lastSeenEpoch || 0),
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


// ─── Table section ────────────────────────────────────────────────────────────

function TableSection({
  agenticTreeData,
  agenticFlatData,
  assetDevices,
  typeFilter,
  violSevFilter,
  flyout,
  setFlyout,
}) {
  const gridRef = useRef(null);
  // Auto-open from a ?asset= deep link must run ONCE on load — not every time the
  // type/severity chips change the filtered rows (that re-opened the flyout on each click).
  const didAutoOpenRef = useRef(false);

  const flatRowData = useMemo(() => {
    const rows = agenticTreeData.filter((r) => r.path?.length === 1);
    let filtered =
      typeFilter && typeFilter.size > 0
        ? rows.filter((r) => typeFilter.has(r.type))
        : rows;
    if (violSevFilter && violSevFilter.size > 0) {
      filtered = filtered.filter((r) =>
        [...violSevFilter].some((sev) => (r.violations?.[sev] || 0) > 0),
      );
    }
    return filtered;
  }, [agenticTreeData, typeFilter, violSevFilter]);

  useEffect(() => {
    if (didAutoOpenRef.current) return;
    const baseRows = agenticTreeData.filter((r) => r.path?.length === 1);
    if (!baseRows.length && !agenticFlatData.length) return;
    const params = new URLSearchParams(window.location.search);
    const assetName = params.get("asset");
    if (!assetName) return;
    didAutoOpenRef.current = true;
    const decoded = decodeURIComponent(assetName.replace(/\+/g, " "));
    const lc = decoded.toLowerCase();
    // Slug: match raw service names that may have been slug-encoded in toChildPathKey
    const toSlug = (s) =>
      s
        ? String(s)
            .toLowerCase()
            .replace(/[^a-z0-9]+/g, "-")
            .replace(/^-|-$/g, "")
        : "";
    const decodedSlug = toSlug(decoded);
    const nameMatch = (n) => {
      if (!n) return false;
      if (n === decoded || n === assetName || n.toLowerCase() === lc)
        return true;
      // also match if the slug form of the stored name equals the URL value
      if (toSlug(n) === decodedSlug || toSlug(n) === toSlug(assetName))
        return true;
      return false;
    };
    const idMatch = (id) =>
      id && (id === assetName || id.includes(decodedSlug));
    const row = baseRows.find(
      (r) => nameMatch(r.name) || idMatch(r.path?.[0]),
    );
    const flat = row
      ? agenticFlatData.find((a) => a.id === row.path[0]) || {
          ...row,
          id: row.path[0],
        }
      : agenticFlatData.find((a) => nameMatch(a.name) || idMatch(a.id));
    if (flat) setFlyout(flat);
  }, [agenticTreeData, agenticFlatData, setFlyout]);

  const handleRowClick = useCallback(
    (e) => {
      if (!e.data) return;
      const flat = agenticFlatData.find((a) => a.id === e.data.path?.[0]) || {
        ...e.data,
        id: e.data.path?.[0],
      };
      setFlyout(flat);
    },
    [agenticFlatData, setFlyout],
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
        gridRef={gridRef}
        rowData={flatRowData}
        columnDefs={COL_DEFS}
        defaultColDef={DEFAULT_COL_DEF}
        height={800}
        rowHeight={44}
        headerHeight={40}
        searchPlaceholder="Search agentic assets..."
        searchOffset={700}
        onRowClicked={handleRowClick}
        getRowStyle={getRowStyle}
        animateRows
        suppressCellFocus
        pagination
        paginationPageSize={20}
        paginationPageSizeSelector={[20, 50, 100]}
        sideBar={{ toolPanels: ["columns", "filters"] }}
      />

      <AgenticAssetFlyout
        asset={flyout}
        show={flyout !== null}
        onClose={handleClose}
        onNavigateToAsset={handleNavigateToAsset}
        agenticTreeData={agenticTreeData}
        agenticFlatData={agenticFlatData}
        assetDevices={assetDevices}
      />
    </>
  );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

const LAYOUT_KEY = "akto_agentic_new_ui";

export default function AgenticAssetsPage() {
  const navigate = useNavigate();
  const [typeFilter, setTypeFilter] = useState(new Set());
  const [violSevFilter, setViolSevFilter] = useState(new Set());
  const [flyout, setFlyout] = useState(null);
  const [loading, setLoading] = useState(true);
  const [agenticTreeData, setAgenticTreeData] = useState([]);
  const [agenticFlatData, setAgenticFlatData] = useState([]);
  const [assetDevices, setAssetDevices] = useState({});
  const [agenticViolationRows, setAgenticViolationRows] = useState([]);
  const [newLayout, setNewLayout] = useState(() => {
    const stored = localStorage.getItem(LAYOUT_KEY);
    return stored === "true";
  });

  useEffect(() => {
    if (localStorage.getItem(LAYOUT_KEY) !== "true") {
      navigate("/dashboard/observe/agentic-assets-legacy", { replace: true });
    }
  }, [navigate]);

  // Date range — only data with real time fields is filtered (violations + last-seen)
  const [currDateRange, dispatchCurrDateRange] = useReducer(
    produce((draft, action) => func.dateRangeReducer(draft, action)),
    values.ranges[4],
  );
  const rawStart = Math.floor(Date.parse(currDateRange.period.since) / 1000);
  // values.ranges "allTime" uses since=new Date(1000) → rawStart=1; treat as 0 (data-min mode)
  const startTimestamp = rawStart <= 1 ? 0 : rawStart;
  const endTimestamp = Math.floor(
    Date.parse(currDateRange.period.until) / 1000,
  );

  const handleLayoutToggle = useCallback(
    (checked) => {
      localStorage.setItem(LAYOUT_KEY, String(checked));
      setNewLayout(checked);
      if (!checked) navigate("/dashboard/observe/agentic-assets-legacy");
    },
    [navigate],
  );

  useEffect(() => {
    const isMountedRef = { current: true };
    (async () => {
      try {
        setLoading(true);
        const [
          apiCollectionsResp,
          trafficInfoResp,
          riskScoreResp,
          sensitiveInfoResp,
          shieldResult,
          violationRows,
          userAnalysisList,
        ] = await Promise.all([
          api.getAllCollectionsBasic(),
          api.getLastTrafficSeen(),
          api.getRiskScoreInfo(),
          api.getSensitiveInfoForCollections(),
          fetchEndpointShieldUserMetadata(),
          agenticObserveApi.fetchAgenticViolations({
            startTimestamp,
            endTimestamp,
          }),
          agenticObserveApi.listUserAnalysis(),
        ]);

        if (!isMountedRef.current) return;

        const collections = apiCollectionsResp?.apiCollections || [];
        const trafficMap = trafficInfoResp || {};
        const riskScoreMap = riskScoreResp?.riskScoreOfCollectionsMap || {};
        const sensitiveMap =
          sensitiveInfoResp?.sensitiveSubtypesInCollection || {};
        const {
          usernameMap = {},
          userMetadataMap = {},
          userAnalysisKeysByDeviceId = new Map(),
        } = shieldResult || {};
        const violationsByCollectionId =
          aggregateViolationsByCollectionId(violationRows);
        const analysisByKey = buildUserAnalysisLookup(userAnalysisList);

        const pageData = buildAgenticAssetsPageData(
          collections,
          trafficMap,
          riskScoreMap,
          sensitiveMap,
          {
            usernameMap,
            userMetadataMap,
            violationsByCollectionId,
            analysisByKey,
            userAnalysisKeysByDeviceId,
          },
        );

        setAgenticTreeData(pageData.agenticTreeData);
        setAgenticFlatData(pageData.agenticFlatData);
        setAssetDevices(pageData.assetDevices);
        setAgenticViolationRows(violationRows);

        // Enrich Skill rows with malicious flag (same source as old UI) — async, non-blocking
        const skillCollectionIds = [];
        collections.forEach((c) => {
          if (!skillCollectionIds.includes(c.id)) skillCollectionIds.push(c.id);
        });
        if (skillCollectionIds.length) {
          fetchAndCacheSkillApiData(skillCollectionIds, { api, PersistStore })
            .then(({ maliciousSkills }) => {
              if (!isMountedRef.current || !maliciousSkills?.size) return;
              const markMalicious = (rows) =>
                rows.map((r) =>
                  r.type === "Skill" && maliciousSkills.has(r.name)
                    ? { ...r, isMalicious: true }
                    : r,
                );
              setAgenticTreeData((prev) => markMalicious(prev));
              setAgenticFlatData((prev) => markMalicious(prev));
            })
            .catch(() => {});
        }
      } catch {
        if (isMountedRef.current) {
          setAgenticTreeData([]);
          setAgenticFlatData([]);
          setAssetDevices({});
          setAgenticViolationRows([]);
        }
      } finally {
        if (isMountedRef.current) setLoading(false);
      }
    })();
    return () => {
      isMountedRef.current = false;
    };
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

  const handleTypeFilter = useCallback(
    (t) =>
      setTypeFilter((prev) => {
        const next = new Set(prev);
        if (next.has(t)) next.delete(t);
        else next.add(t);
        return next;
      }),
    [],
  );

  const handleViolSevFilter = useCallback(
    (sev) =>
      setViolSevFilter((prev) => {
        const next = new Set(prev);
        if (next.has(sev)) next.delete(sev);
        else next.add(sev);
        return next;
      }),
    [],
  );

  const topAppsRows = useMemo(
    () =>
      [...agenticFlatData]
        .filter((r) => r.aiInteractions > 0)
        .sort((a, b) => b.aiInteractions - a.aiInteractions)
        .slice(0, 5)
        .map((row) => ({
          ...row,
          onClick: setFlyout,
          renderValue: (r) => (
            <HorizontalStack align="end" blockAlign="center" wrap={false} gap="0">
              <Box minHeight="28px">
                <Text variant="bodyMd" alignment="end">
                  {func.prettifyShort(r.aiInteractions)}
                </Text>
              </Box>
            </HorizontalStack>
          ),
        })),
    [agenticFlatData, setFlyout],
  );

  const agenticCollectionViolRows = useMemo(() => {
    const ids = new Set();
    agenticFlatData.forEach((r) =>
      (r.collectionIds || []).forEach((id) => ids.add(Number(id))),
    );
    return agenticViolationRows.filter((v) => ids.has(Number(v.apiCollectionId)));
  }, [agenticFlatData, agenticViolationRows]);

  const topViolRows = useMemo(
    () =>
      [...agenticFlatData]
        .map((r) => {
          const v = r.violations;
          const totalV = v
            ? (v.critical || 0) + (v.high || 0) + (v.medium || 0) + (v.low || 0)
            : 0;
          return { ...r, totalV };
        })
        .filter((r) => r.totalV > 0)
        .sort((a, b) => b.totalV - a.totalV)
        .slice(0, 5)
        .map((row) => {
          const collectionIdSet = new Set((row.collectionIds || []).map(Number));
          const assetViolRows = agenticCollectionViolRows.filter((v) =>
            collectionIdSet.has(Number(v.apiCollectionId)),
          );
          const series = cumulativeByMonth(
            assetViolRows,
            (v) => v.timeEpoch || 0,
            startTimestamp,
            endTimestamp,
          );
          return {
            ...row,
            onClick: setFlyout,
            renderValue: (r) => (
              <HorizontalStack align="end" blockAlign="center" gap="3" wrap={false}>
                <Text variant="bodyMd">{r.totalV}</Text>
                <SmoothAreaChart
                  tickPositions={series.counts}
                  color="#EF4444"
                  height={28}
                  width={100}
                  labels={series.labels}
                  enableHover
                />
              </HorizontalStack>
            ),
          };
        }),
    [agenticFlatData, agenticCollectionViolRows, startTimestamp, endTimestamp, setFlyout],
  );

  const anchorSeriesToTotal = useCallback((series, total) => {
    const counts = series.counts || [];
    if (!counts.length || total == null) return series;
    const diff = total - counts[counts.length - 1];
    if (diff === 0) return series;
    return { ...series, counts: counts.map(c => Math.max(0, c + diff)) };
  }, []);

  const windowDelta = useCallback((counts) =>
    counts && counts.length >= 2 ? Math.max(0, counts[counts.length - 1] - counts[0]) : 0,
  []);

  const totalAssets = agenticFlatData.length;

  const assetTypeBreakdown = useMemo(() => [
    { label: "Agents",     count: agenticFlatData.filter(r => r.type === "AI Agent").length,   color: "#9642FC",  key: "AI Agent" },
    { label: "MCP Servers",count: agenticFlatData.filter(r => r.type === "MCP Server").length,color: "#4cbebb",  key: "MCP Server" },
    { label: "LLMs",       count: agenticFlatData.filter(r => r.type === "LLM").length,        color: "#EAB308",  key: "LLM" },
    { label: "Skills",     count: agenticFlatData.filter(r => r.type === "Skill").length,      color: "#D1D5DB",  key: "Skill" },
  ], [agenticFlatData]);

  const violationTotals = useMemo(() => {
    const crit = agenticFlatData.reduce((s, r) => s + (r.violations?.critical || 0), 0);
    const high = agenticFlatData.reduce((s, r) => s + (r.violations?.high || 0), 0);
    const med  = agenticFlatData.reduce((s, r) => s + (r.violations?.medium || 0), 0);
    const low  = agenticFlatData.reduce((s, r) => s + (r.violations?.low || 0), 0);
    return { crit, high, med, low, total: crit + high + med + low };
  }, [agenticFlatData]);

  const violBreakdown = useMemo(() => [
    { label: "Critical", key: "critical", count: violationTotals.crit, color: "#DC2626" },
    { label: "High",     key: "high",     count: violationTotals.high, color: "#F97316" },
    { label: "Medium",   key: "medium",   count: violationTotals.med,  color: "#EAB308" },
    { label: "Low",      key: "low",      count: violationTotals.low,  color: "#D1D5DB" },
  ], [violationTotals]);

  const assetSeries = useMemo(() =>
    anchorSeriesToTotal(
      cumulativeByMonth(agenticFlatData, r => r.lastSeenEpoch || 0, startTimestamp, endTimestamp),
      totalAssets,
    ),
    [agenticFlatData, startTimestamp, endTimestamp, totalAssets, anchorSeriesToTotal],
  );

  const assetDelta = useMemo(() => windowDelta(assetSeries.counts), [assetSeries.counts, windowDelta]);

  const violSeries = useMemo(() =>
    anchorSeriesToTotal(
      cumulativeByMonth(agenticCollectionViolRows, v => v.timeEpoch || 0, startTimestamp, endTimestamp),
      violationTotals.total,
    ),
    [agenticCollectionViolRows, startTimestamp, endTimestamp, violationTotals.total, anchorSeriesToTotal],
  );

  const violDelta = useMemo(() => windowDelta(violSeries.counts), [violSeries.counts, windowDelta]);

  const topCards = useMemo(() => (
    <HorizontalGrid key="top-row" columns={3} gap="4">
      <Card padding="0">
        <Box className="agentic-stats-card-fill">
          <Box className="agentic-stats-card-item">
            <AgenticStatsCard
              title="Agentic Assets"
              total={totalAssets}
              delta={assetDelta}
              sparklineCounts={assetSeries.counts}
              sparklineLabels={assetSeries.labels}
              breakdown={assetTypeBreakdown}
              onFilterClick={handleTypeFilter}
              activeFilter={typeFilter}
              noCard
            />
          </Box>
          <Divider />
          <Box className="agentic-stats-card-item">
            <AgenticStatsCard
              title="Violations"
              total={violationTotals.total}
              totalColor="critical"
              delta={violDelta}
              sparklineCounts={violSeries.counts}
              sparklineColor="#DC2626"
              sparklineLabels={violSeries.labels}
              breakdown={violBreakdown}
              onFilterClick={handleViolSevFilter}
              activeFilter={violSevFilter}
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
  ), [
    totalAssets, assetDelta, assetSeries, assetTypeBreakdown,
    violationTotals, violBreakdown, violDelta, violSeries,
    topAppsRows, topViolRows,
    handleTypeFilter, handleViolSevFilter,
    typeFilter, violSevFilter,
  ]);

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
          agenticTreeData={agenticTreeData}
          agenticFlatData={agenticFlatData}
          assetDevices={assetDevices}
          typeFilter={typeFilter}
          violSevFilter={violSevFilter}
          flyout={flyout}
          setFlyout={setFlyout}
        />,
      ]}
    />
  );
}
