import React, {
  useState,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useReducer,
} from "react";
import { produce } from "immer";
import { useNavigate } from "react-router-dom";
import { Box, Card, Divider, HorizontalGrid, HorizontalStack, Text } from "@shopify/polaris";
import AskBar from "@/apps/dashboard/components/shared/AskBar";
import useGridColumnFilter from "@/apps/dashboard/components/tables/useGridColumnFilter";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import AgenticInventoryTable, { SORT_FIELD_MAP, shapeRow } from "./AgenticInventoryTable";
import InsightsFlyout from "./insights/InsightsFlyout";
import InsightsEntryButton from "./insights/InsightsEntryButton";
import useInsightsEntryPoint from "./insights/useInsightsEntryPoint";
import { ATLAS_INVENTORY_CHAT_SCOPE, ASK_ATLAS_PLACEHOLDER, ASK_ATLAS_SUGGESTIONS } from "../atlas/devices_employees/constants";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import "../../../components/layouts/style.css";
import NewLayoutTooltip from "./NewLayoutTooltip";
import api from "../api";
import {
  fetchAgenticViolationCountsByHost,
  fetchViolationCountsByCollection,
  fetchAgenticSkillViolationCounts,
} from "./agenticObserveApi";
import {
  fetchAndCacheSkillApiData,
  settledValue,
  logRejected,
} from "./constants";
import PersistStore from "../../../../main/PersistStore";
import LocalStore from "../../../../main/LocalStorageStore";
import DateRangeFilter from "@/apps/dashboard/components/layouts/DateRangeFilter";
import values from "@/util/values";
import func from "@/util/func";
import AgenticStatsCard from "./AgenticStatsCard";
import AgenticTopListCard from "./AgenticTopListCard";
import SmoothAreaChart from "@/apps/dashboard/pages/dashboard/new_components/SmoothChart";

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function AgenticAssetsPage() {
  const navigate = useNavigate();
  const [flyout, setFlyout] = useState(null);
  const insights = useInsightsEntryPoint();
  const [loading, setLoading] = useState(true);
  const [hostSeverityCounts, setHostSeverityCounts] = useState({});
  const [stats, setStats] = useState({ totalAssets: 0, countsByType: {}, countsByCategory: {} });
  const [refreshKey, setRefreshKey] = useState(0);
  const newLayout = LocalStore((state) => state.agenticNewLayout);
  const setAgenticNewLayout = LocalStore((state) => state.setAgenticNewLayout);

  // Shared with AgenticInventoryTable, which drives its filters through it; the stats card chips
  // drive the grid's "type" and hidden "severity" filters.
  const gridRef = useRef(null);
  const typeFilter = useGridColumnFilter(gridRef, "type");
  const severityFilter = useGridColumnFilter(gridRef, "severity");

  // Everything shapeRow needs to enrich a server-returned page — populated once at mount, read
  // (not reacted to) by onServerFetch, which AG Grid SSRM calls directly rather than through React
  // re-renders.
  const enrichRef = useRef({
    violationsByCollectionId: {},
    skillViolationsByName: {},
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
      const { violationsByCollectionId, skillViolationsByName } = enrichRef.current;
      const result = await api.fetchAgenticAssetsStats({
        startTimestamp, endTimestamp, violationsByCollectionId, skillViolationsByName,
      });
      setStats(result);
    } catch (e) {
      // eslint-disable-next-line no-console
      console.error("fetchAgenticAssetsStats failed:", e);
      setStats({ totalAssets: 0, countsByType: {}, countsByCategory: {} });
    }
  }, [startTimestamp, endTimestamp]);

  // Tier 1 (fast) mounts the grid; Tier 2 (slow, non-blocking) patches in violations/AI-interaction data after.
  useEffect(() => {
    // Skip on a cold mount that's about to redirect to legacy Endpoints.jsx.
    if (!newLayout) return;
    const isMountedRef = { current: true };

    (async () => {
      try {
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
        Promise.allSettled([
          fetchAgenticViolationCountsByHost({ startTimestamp, endTimestamp }),
          fetchAgenticSkillViolationCounts({ startTimestamp, endTimestamp }),
        ])
          .then(async ([hostCountsSettled, skillViolationsSettled]) => {
            if (!isMountedRef.current) return;
            logRejected("AgenticAssetsPage tier-2", { violations: hostCountsSettled, skills: skillViolationsSettled });
            const hostCounts = settledValue(hostCountsSettled, {});
            const skillViolationsByName = settledValue(skillViolationsSettled, {});
            // Host -> collection-id attribution now happens server-side (no raw collection list
            // needed client-side for this — see attributeViolationCountsToCollections). Skills
            // aren't attributable by host at all (see skillViolationsByName's own comment), so
            // fetchAgenticSkillViolationCounts above already returns them keyed by skill name.
            const violationsByCollectionId = await fetchViolationCountsByCollection(hostCounts);
            if (!isMountedRef.current) return;
            enrichRef.current = {
              ...enrichRef.current,
              violationsByCollectionId,
              skillViolationsByName,
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
    const mappedSortKey = SORT_FIELD_MAP[sortKey] || sortKey || "riskScore";
    // AG Grid SSRM sends sortOrder: -1 for asc, 1 for desc — opposite of the backend's Mongo
    // convention (1 asc / -1 desc, matching NhiGovernanceViolationsAction's own onServerFetch).
    const mongoSortOrder = sortOrder ? -sortOrder : -1;
    const { violationsByCollectionId, skillViolationsByName } = enrichRef.current;

    // trafficMap/riskScoreMap omitted — backend computes both server-side now.
    return api.fetchAgenticAssetsSummary({
      skip,
      limit: pageSize,
      sortKey: mappedSortKey,
      sortOrder: mongoSortOrder,
      queryValue: searchString || undefined,
      startTimestamp,
      endTimestamp,
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
    }).then((res) => ({
      value: (res.rows || []).map((row) => shapeRow(row)),
      total: res.total || 0,
    }));
  }, [startTimestamp, endTimestamp]);

  // ─── Summary cards (kept from the earlier new layout) ──────────────────────
  const assetTypeBreakdown = useMemo(() => [
    { label: "Agents",      count: stats.countsByType?.["AI Agent"] || 0,   color: "#9642FC", key: "AI Agent" },
    { label: "SaaS Agents", count: stats.countsByType?.["SaaS Agent"] || 0, color: "#DB2777", key: "SaaS Agent" },
    { label: "MCP Servers", count: stats.countsByType?.["MCP Server"] || 0, color: "#4cbebb", key: "MCP Server" },
    { label: "LLMs",        count: stats.countsByType?.["LLM"] || 0,        color: "#EAB308", key: "LLM" },
    { label: "Skills",      count: stats.countsByType?.["Skill"] || 0,      color: "#D1D5DB", key: "Skill" },
    { label: "Plugins",     count: stats.countsByType?.["Plugin"] || 0,     color: "#4F46E5", key: "Plugin" },
  ], [stats]);

  // Summed from the server-aggregated per-host counts — independent of the paginated table.
  const violationTotals = useMemo(() => {
    const t = { critical: 0, high: 0, medium: 0, low: 0 };
    Object.values(hostSeverityCounts).forEach((c) => {
      Object.keys(t).forEach((k) => { t[k] += c[k] || 0; });
    });
    return { ...t, total: t.critical + t.high + t.medium + t.low };
  }, [hostSeverityCounts]);

  const violBreakdown = useMemo(() => [
    { label: "Critical", key: "critical", count: violationTotals.critical, color: "#DC2626" },
    { label: "High",     key: "high",     count: violationTotals.high,     color: "#F97316" },
    { label: "Medium",   key: "medium",   count: violationTotals.medium,   color: "#EAB308" },
    { label: "Low",      key: "low",      count: violationTotals.low,      color: "#D1D5DB" },
  ], [violationTotals]);

  const toggleFilter = (filter) => (key) => filter.apply(
    filter.values.includes(key) ? filter.values.filter((v) => v !== key) : [...filter.values, key],
  );

  const openAssetByName = useCallback((name, type) => {
    onServerFetch({ skip: 0, limit: 20, searchString: name, filters: type ? { type: [type] } : undefined })
      .then((res) => {
        const rows = res?.value || [];
        const found = rows.find((r) => (r.name || "").toLowerCase() === (name || "").toLowerCase()) || rows[0];
        if (found) setFlyout(found);
      });
  }, [onServerFetch]);

  const topAppsRows = useMemo(() =>
    (stats.topUsedApplications || []).map((row) => ({
      ...row,
      assetTagValue: row.groupKey,
      onClick: (r) => openAssetByName(r.name, r.type),
      renderValue: (r) => (
        <HorizontalStack align="end" blockAlign="center" wrap={false} gap="0">
          <Box minHeight="28px">
            <Text variant="bodyMd" alignment="end">{func.prettifyShort(r.aiInteractions)}</Text>
          </Box>
        </HorizontalStack>
      ),
    })), [stats.topUsedApplications, openAssetByName]);

  const topViolRows = useMemo(() =>
    (stats.topAssetsWithViolations || []).map((row) => ({
      ...row,
      onClick: (r) => openAssetByName(r.name, r.type),
      renderValue: (r) => (
        <HorizontalStack align="end" blockAlign="center" gap="3" wrap={false}>
          <Text variant="bodyMd">{func.prettifyShort(r.violations)}</Text>
          <SmoothAreaChart tickPositions={r.sparkline} color="#EF4444" height={28} width={100} labels={stats.monthLabels} enableHover />
        </HorizontalStack>
      ),
    })), [stats.topAssetsWithViolations, stats.monthLabels, openAssetByName]);

  const summaryCards = (
    <HorizontalGrid key="summary-cards" columns={3} gap="4">
      <Card padding="0">
        <Box className="agentic-stats-card-fill">
          <Box className="agentic-stats-card-item">
            <AgenticStatsCard
              title="Agentic Assets"
              total={stats.totalAssets}
              delta={stats.assetDelta}
              sparklineCounts={stats.assetSparkline}
              sparklineLabels={stats.monthLabels}
              breakdown={assetTypeBreakdown}
              onFilterClick={toggleFilter(typeFilter)}
              activeFilter={new Set(typeFilter.values)}
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
              onFilterClick={toggleFilter(severityFilter)}
              activeFilter={new Set(severityFilter.values)}
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
  );

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
      <InsightsEntryButton granted={insights.granted} onClick={insights.handleOpen} label="Atlas Insights" />
    </HorizontalStack>
  );

  return (
    <>
      <PageWithMultipleCards
        title="AI inventory"
        subtitle="All discovered AI tools across your devices. Approve, block or review each one."
        isFirstPage={true}
        secondaryActions={headerActions}
        components={loading ? [<SpinnerCentered key="loading" />] : [
          <AskBar
            key="ask-atlas"
            label="Ask Atlas"
            placeholder={ASK_ATLAS_PLACEHOLDER}
            suggestions={ASK_ATLAS_SUGGESTIONS}
            chatScope={ATLAS_INVENTORY_CHAT_SCOPE}
          />,
          summaryCards,
          <AgenticInventoryTable
            key="table"
            onServerFetch={onServerFetch}
            flyout={flyout}
            setFlyout={setFlyout}
            startTimestamp={startTimestamp}
            endTimestamp={endTimestamp}
            refreshKey={refreshKey}
            enrichMaps={enrichRef.current}
            gridRef={gridRef}
            categoryCounts={stats.countsByCategory}
            totalCount={stats.totalAssets}
          />,
        ]}
      />
      {insights.granted && (
        <InsightsFlyout
          show={insights.open}
          onClose={insights.handleClose}
          startTimestamp={startTimestamp}
          endTimestamp={endTimestamp}
          initialInsightId={insights.initialInsightId}
        />
      )}
    </>
  );
}
