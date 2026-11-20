import React, { useState, useCallback, useEffect, useRef, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { Card, Tabs } from "@shopify/polaris";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import useGridColumnFilter from "@/apps/dashboard/components/tables/useGridColumnFilter";
import FilterToggleButtons from "@/apps/dashboard/components/shared/FilterToggleButtons";
import { pageRequires } from "@/util/permissions";
import AgenticAssetFlyout from "./AgenticAssetFlyout";
import {
  AssetNameCellRenderer,
  ApprovalStatusCellRenderer,
  ToolsCellRenderer,
  PluginAgentCellRenderer,
  RiskLevelCellRenderer,
  ViolationsCellRenderer,
  InteractionsCellRenderer,
} from "./AgenticCellRenderers";
import {
  APPROVAL_ACTIONS,
  approvalActionApplies,
  approvalActionRequires,
  buildGuardrailPrefillForAssets,
  canTargetGuardrail,
  canUpdateApproval,
  updateAssetApproval,
} from "./agenticObserveApi";
import { GUARDRAIL_POLICIES_PATH } from "../../guardrails/topicGuardrailUtils";
import { CATEGORY_LABELS, CATEGORY_TABS, typeLabelOf } from "./inventoryCategories";
import func from "@/util/func";

// ─── Column definitions ───────────────────────────────────────────────────────

// AG Grid colId -> backend sortKey (see AgenticObserveAction.buildSummaryComparator)
export const SORT_FIELD_MAP = {
  name: "name",
  riskScore: "riskScore",
  endpointCount: "endpointsCount",
  pluginCount: "pluginCount",
  violations: "violations",
  lastSeen: "lastSeenEpoch",
};

const APPROVAL_STATUSES = ["Approved", "Unapproved", "Under review", "Blocked"];


const COL_DEFS = [
  {
    field: "name",
    headerName: "Name",
    width: 360,
    minWidth: 200,
    pinned: "left",
    filter: false,
    cellRenderer: AssetNameCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "type",
    headerName: "Type",
    width: 160,
    // Filters on the fixed AgenticObserveUtil.CLIENT_TYPE_* enum; shows the inventory's plain-text type.
    filter: "agSetColumnFilter",
    filterParams: { values: ["AI Agent", "SaaS Agent", "MCP Server", "LLM", "Skill", "Plugin"] },
    sortable: false,
    valueFormatter: (p) => p.data?.typeLabel || p.value,
  },
  {
    // Approve/Block state of MCP server, LLM and AI agent rows, from the Audit Data records
    // (AgenticObserveAction.fetchApprovalStates). Other rows can't be approved and show "-" —
    // e.g. a plugin row rolls up one collection per device, so a single blended value would
    // misrepresent devices that disagree; see the flyout's Devices tab for those instead.
    field: "approvalStatus",
    headerName: "Status",
    width: 140,
    filter: "agSetColumnFilter",
    filterParams: { values: APPROVAL_STATUSES },
    sortable: false,
    cellRenderer: ApprovalStatusCellRenderer,
    cellStyle: { display: "flex", alignItems: "center" },
  },
  {
    field: "endpointCount",
    headerName: "Devices",
    width: 110,
    filter: false,
    valueFormatter: (p) => (p.value != null ? p.value : ""),
  },
  {
    field: "employeesCount",
    headerName: "Employees",
    width: 120,
    filter: false,
    sortable: false,
  },
  {
    field: "tools",
    headerName: "Tool capabilities",
    width: 280,
    filter: false,
    sortable: false,
    cellRenderer: ToolsCellRenderer,
  },
  {
    field: "riskScore",
    headerName: "Risk",
    width: 120,
    filter: false,
    cellRenderer: RiskLevelCellRenderer,
  },
  {
    field: "lastSeen",
    headerName: "Last seen",
    width: 150,
    filter: false,
    // Backend already supports sorting by lastSeenEpoch
    cellStyle: { display: "flex", alignItems: "center", color: "#6D7175" },
  },
  // Hidden by default (not in the AI inventory design); still available from the Columns panel.
  {
    field: "pluginParentAgent",
    headerName: "AI Agent",
    width: 160,
    hide: true,
    filter: false,
    sortable: false,
    cellRenderer: PluginAgentCellRenderer,
  },
  {
    field: "aiInteractions",
    headerName: "AI Interactions",
    headerTooltip:
      "Total tokens from UserAnalysisData (input + output) across devices using this asset.",
    width: 150,
    hide: true,
    filter: false,
    sortable: false,
    cellRenderer: InteractionsCellRenderer,
  },
  {
    field: "violations",
    headerName: "Violations",
    width: 200,
    hide: true,
    // Server-side sort via AgenticObserveAction.violationsTotalForGroup — not a stored field, so
    // it's computed from the already-fetched violationsByCollectionId/skillViolationsByName maps
    // rather than a real Mongo sort.
    filter: false,
    cellRenderer: ViolationsCellRenderer,
  },
  {
    // Filter-only, driven by the category tabs above the grid.
    colId: "category",
    field: "inventoryCategory",
    headerName: "Category",
    hide: true,
    filter: "agSetColumnFilter",
    filterParams: { values: CATEGORY_TABS.map((t) => t.id), valueFormatter: (p) => CATEGORY_LABELS[p.value] || p.value },
    sortable: false,
  },
  {
    field: "tags",
    headerName: "Tags",
    hide: true,
    // Fixed set — matches shapeRow's own tag derivation below exactly. Values are pre-computed
    // string arrays on each row (array-valued field: a row matches if ANY selected tag is present).
    filter: "agSetColumnFilter",
    filterParams: { values: ["Contains personal account", "Local MCP Server", "Misconfigured", "Malicious Skill", "Malicious MCP Server"] },
    sortable: false,
  },
  { field: "pluginVersion", headerName: "Plugin Version", width: 130, hide: true, filter: false, sortable: false },
  { field: "pluginMarketplace", headerName: "Marketplace", width: 170, hide: true, filter: false, sortable: false },
  {
    // Filter-only: "High risk" is matched server-side on each group's risk score.
    field: "riskBand",
    headerName: "Risk band",
    hide: true,
    filter: "agSetColumnFilter",
    filterParams: { values: ["High risk"] },
    sortable: false,
  },
  {
    field: "severity",
    headerName: "Severity",
    hide: true,
    // Filter-only, same pattern as "tags" above, matched server-side against each group's own
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

// Same SSRM selection setup as guardrails' ViolationsPage; only rows with an action get a checkbox.
const ROW_SELECTION = {
  mode: "multiRow",
  checkboxes: true,
  headerCheckbox: true,
  enableClickSelection: false,
  isRowSelectable: (node) => canUpdateApproval(node.data) || canTargetGuardrail(node.data),
  hideDisabledCheckboxes: true,
};
const SELECTION_COLUMN_DEF = { pinned: "left" };
// ─── Row shaping ──────────────────────────────────────────────────────────────

// Second line under the name, only where we have it: "Local" for local MCP servers, and a
// plugin's marketplace/version.
function subtitleOf(row) {
  if (row.rowType === "service") return row.hasLocalMcpServer ? "Local" : undefined;
  if (row.rowType === "plugin") return [row.pluginMarketplace, row.pluginVersion].filter(Boolean).join(" · ") || undefined;
  return undefined;
}

// Turns one server-computed row (AgenticObserveAction.fetchAgenticAssetsSummary) into the shape
// COL_DEFS/AgenticCellRenderers/AgenticAssetFlyout expect. `violations`/`isMalicious`/`groups`
// (team breakdown)/`aiInteractions` all come precomputed from the server now — a row's raw
// per-device list (up to hundreds of entries for a big group) used to be sent just so the browser
// could derive these few small values from it; a single 50-row page measured at 16MB. The full
// device list itself now comes from fetchAgenticAssetDetail (see AgenticAssetFlyout.jsx), fetched
// lazily only for the one asset a user actually opens. See
// AgenticObserveAction.GroupSummary.toSummaryResponse()'s and fetchAgenticAssetsSummary's row-loop
// comments.
export function shapeRow(row) {
  // Fan-out rows borrow their parent agent collection's flags, so suppress them on both.
  const isFanout = row.rowType === "skill" || row.rowType === "plugin";
  const tags = [];
  if (row.hasPersonalAccount && !isFanout) tags.push("Contains personal account");
  if (row.hasLocalMcpServer && !isFanout) tags.push("Local MCP Server");
  if (row.hasMisconfiguredConfig && !isFanout) tags.push("Misconfigured");
  if (row.isMalicious) tags.push(row.rowType === "service" ? "Malicious MCP Server" : "Malicious Skill");

  return {
    ...row,
    type: row.clientType,
    typeLabel: typeLabelOf(row),
    subtitle: subtitleOf(row),
    endpointCount: row.endpointsCount,
    lastSeen: row.lastSeenEpoch > 0 ? func.prettifyEpoch(row.lastSeenEpoch) : "",
    assetTagValue: row.groupKey,
    tags: tags.length ? tags : undefined,
  };
}

// ─── Table ────────────────────────────────────────────────────────────────────
// AI inventory card: category tabs, status filters, grid and the asset flyout. A page can open it
// pre-filtered by status with ?status=Unapproved. categoryCounts/totalCount come from
// fetchAgenticAssetsStats (countsByCategory/totalAssets).

export default function AgenticInventoryTable({
  onServerFetch,
  flyout,
  setFlyout,
  startTimestamp,
  endTimestamp,
  refreshKey,
  enrichMaps,
  gridRef,
  categoryCounts = {},
  totalCount = 0,
}) {
  const navigate = useNavigate();
  const didAutoOpenRef = useRef(false);
  const didApplyStatusRef = useRef(false);
  const [selectedRows, setSelectedRows] = useState([]);
  const category = useGridColumnFilter(gridRef, "category");
  const status = useGridColumnFilter(gridRef, "approvalStatus");

  useEffect(() => {
    if (didAutoOpenRef.current) return;
    const params = new URLSearchParams(window.location.search);
    const assetName = params.get("asset");
    if (!assetName) return;
    didAutoOpenRef.current = true;
    const decoded = decodeURIComponent(assetName.replace(/\+/g, " ")).toLowerCase();
    const normalize = (s) => (s || "").toLowerCase().replace(/[^a-z0-9]/g, "");
    const normDecoded = normalize(decoded);
    const assetType = params.get("type");

    onServerFetch({
      skip: 0,
      limit: 20,
      searchString: assetName,
      filters: assetType ? { type: [assetType] } : undefined,
    }).then((res) => {
      const rows = res?.value || [];
      const found = rows.find((r) =>
        (r.name || "").toLowerCase() === decoded || r.id === assetName || normalize(r.name) === normDecoded);
      if (found) setFlyout(found);
    });
  }, [setFlyout, onServerFetch]);

  // Runs for every grid instance (the grid remounts on date-range change): keeps the tabs and
  // status buttons in step with filters set from the header menu, Filters panel or persisted state.
  const { sync: syncCategory } = category;
  const { sync: syncStatus } = status;
  const handleFirstDataRendered = useCallback((e) => {
    const syncAll = () => { syncCategory(e.api); syncStatus(e.api); };
    e.api.addEventListener("filterChanged", syncAll);
    syncAll();
    if (didApplyStatusRef.current) return;
    didApplyStatusRef.current = true;
    const urlStatus = new URLSearchParams(window.location.search).get("status");
    if (!APPROVAL_STATUSES.includes(urlStatus)) return;
    e.api.setColumnFilterModel("approvalStatus", { filterType: "set", values: [urlStatus] })
      .then(() => e.api.onFilterChanged());
  }, [syncCategory, syncStatus]);

  const handleRowClick = useCallback(
    (e) => {
      if (!e.data) return;
      setFlyout(e.data);
    },
    [setFlyout],
  );

  const handleClose = useCallback(() => setFlyout(null), [setFlyout]);
  const getRowStyle = useCallback(() => ({ cursor: "pointer" }), []);

  // Under SSRM, header "select all" doesn't populate getSelectedRows(), so read loaded nodes instead.
  const handleSelectionChanged = useCallback((e) => {
    const rows = [];
    e.api.forEachNode((node) => {
      if (!node.stub && node.isSelected() && node.data) rows.push(node.data);
    });
    setSelectedRows(rows);
  }, []);

  const clearSelection = useCallback(() => {
    gridRef.current?.api?.deselectAll();
    setSelectedRows([]);
  }, [gridRef]);

  // Shared by the bulk bar and the flyout header; action is an APPROVAL_ACTIONS entry.
  const handleApproval = useCallback(async (rows, action) => {
    const changed = rows.filter((r) => approvalActionApplies(action, r));
    try {
      const count = await updateAssetApproval(rows, action);
      if (!count) return;
      func.setToast(true, false, `${count > 1 ? `${count} tools` : changed[0].name} marked ${action.status.toLowerCase()}`);
      clearSelection();
      setFlyout((prev) => (prev && changed.some((r) => r.id === prev.id) ? { ...prev, approvalStatus: action.status } : prev));
      gridRef.current?.api?.refreshServerSide({ purge: false });
    } catch (e) {
      func.setToast(true, true, "Unable to update status. Please try again.");
    }
  }, [clearSelection, setFlyout, gridRef]);

  // Opens the existing Create Guardrail flow pre-scoped to these assets (same prefill as Insights).
  const createGuardrailPolicy = useCallback((rows) => {
    navigate(GUARDRAIL_POLICIES_PATH, { state: { topicGuardrailPrefill: buildGuardrailPrefillForAssets(rows) } });
  }, [navigate]);

  const bulkActions = useMemo(() => {
    const requires = approvalActionRequires(selectedRows);
    const actions = Object.values(APPROVAL_ACTIONS)
      .filter((action) => selectedRows.some((r) => approvalActionApplies(action, r)))
      .map((action) => ({ label: action.label, destructive: action.destructive, requires, onAction: () => handleApproval(selectedRows, action) }));
    if (selectedRows.some(canTargetGuardrail)) {
      actions.push({ label: "Create guardrail policy", requires: pageRequires(GUARDRAIL_POLICIES_PATH), onAction: () => createGuardrailPolicy(selectedRows) });
    }
    return actions;
  }, [selectedRows, handleApproval, createGuardrailPolicy]);

  const tabs = useMemo(() => [
    { id: "all", content: "All", badge: String(totalCount) },
    ...CATEGORY_TABS
      .filter((t) => !t.hideWhenEmpty || categoryCounts[t.id] > 0)
      .map((t) => ({ id: t.id, content: t.label, badge: String(categoryCounts[t.id] || 0) })),
  ], [categoryCounts, totalCount]);
  // A multi-value category filter (set from the Filters panel) has no single tab, so "All" stays selected.
  const selectedTab = Math.max(0, category.values.length === 1 ? tabs.findIndex((t) => t.id === category.values[0]) : 0);
  const handleTabSelect = useCallback((index) => {
    const id = tabs[index]?.id;
    category.apply(!id || id === "all" ? [] : [id]);
  }, [tabs, category]);

  const filterRow = <FilterToggleButtons options={APPROVAL_STATUSES} selected={status.values} onChange={status.apply} single />;

  return (
    <>
      <Card padding="0">
        <Tabs tabs={tabs} selected={selectedTab} onSelect={handleTabSelect} />
        <AgGridTable
          key={`agentic-assets-grid-${startTimestamp}-${endTimestamp}-${refreshKey}`}
          gridRef={gridRef}
          columnDefs={COL_DEFS}
          defaultColDef={DEFAULT_COL_DEF}
          noOuterBorder
          height={560}
          domLayout="normal"
          rowHeight={48}
          headerHeight={40}
          searchPlaceholder="Filter by name or tool"
          searchBelow={filterRow}
          onRowClicked={handleRowClick}
          getRowStyle={getRowStyle}
          rowSelection={ROW_SELECTION}
          selectionColumnDef={SELECTION_COLUMN_DEF}
          onSelectionChanged={handleSelectionChanged}
          bulkActionCount={selectedRows.length}
          bulkBarVariant="inline"
          bulkActions={bulkActions}
          onClearBulk={clearSelection}
          onFirstDataRendered={handleFirstDataRendered}
          animateRows
          suppressCellFocus
          paginationPageSize={50}
          paginationPageSizeSelector={[20, 50, 100]}
          onServerFetch={onServerFetch}
          onFetchError={() => func.setToast(true, true, "Failed to load agentic assets")}
          serverSideRowModel
          getRowId={(params) => params.data.id}
        />
      </Card>

      <AgenticAssetFlyout
        asset={flyout}
        show={flyout !== null}
        onClose={handleClose}
        onNavigateToAsset={setFlyout}
        onApprovalChange={handleApproval}
        onCreateGuardrail={createGuardrailPolicy}
        enrichMaps={enrichMaps}
        startTimestamp={startTimestamp}
        endTimestamp={endTimestamp}
      />
    </>
  );
}
