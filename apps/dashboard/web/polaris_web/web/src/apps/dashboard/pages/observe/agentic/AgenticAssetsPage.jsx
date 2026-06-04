import React, {
  useState,
  useCallback,
  useEffect,
  useRef,
  useMemo,
  useReducer,
} from "react";
import { createPortal } from "react-dom";
import { produce } from "immer";
import { useNavigate } from "react-router-dom";
import Highcharts from "highcharts";
import HighchartsMore from "highcharts/highcharts-more";
import { HighchartsReact } from "highcharts-react-official";
import {
  Box,
  Card,
  HorizontalGrid,
  HorizontalStack,
  VerticalStack,
  Text,
  Divider,
  Tooltip,
  Checkbox,
} from "@shopify/polaris";
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { LicenseManager, AllEnterpriseModule } from "ag-grid-enterprise";
import MCPIcon from "@/assets/MCP_Icon.svg";
import McpRedIcon from "@/assets/McpRedIcon.svg";
import PersonLockIcon from "@/assets/PersonLockIcon.svg";
import LaptopIcon from "@/assets/Laptop.svg";
import SkillIcon from "@/assets/Skill.svg";
import MaliciousSkillIcon from "@/assets/MaliciousSkill.svg";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import AgenticAssetFlyout from "./AgenticAssetFlyout";
import { TYPE_STYLES, SEVERITY_COLORS, getRiskColor } from "./agenticStyles";
import { getDomainForFavicon } from "./mcpClientHelper";
import { cumulativeByMonth } from "./agenticPageBuilders";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import "../../../components/layouts/style.css";
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

HighchartsMore(Highcharts);

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);

LicenseManager.setLicenseKey(
  "[TRIAL]_this_{AG_Charts_and_AG_Grid}_Enterprise_key_{AG-129492}_is_granted_for_evaluation_only___Use_in_production_is_not_permitted___Please_report_misuse_to_legal@ag-grid.com___For_help_with_purchasing_a_production_key_please_contact_info@ag-grid.com___You_are_granted_a_{Single_Application}_Developer_License_for_one_application_only___All_Front-End_JavaScript_developers_working_on_the_application_would_need_to_be_licensed___This_key_will_deactivate_on_{18 June 2026}____[v3]_[0102]_MTc4MTczNzIwMDAwMA==d27c8a4487e577f42d9980e95824f43c",
);

// ─── Icon helpers ─────────────────────────────────────────────────────────────

function AgentIconImg({ data }) {
  if (!data) return null;
  if (data.type === "MCP Server") {
    return (
      <img
        src={MCPIcon}
        width={20}
        height={20}
        alt=""
        style={{ flexShrink: 0, borderRadius: 3 }}
      />
    );
  }
  if (data.type === "Skill") {
    return (
      <img
        src={SkillIcon}
        width={18}
        height={18}
        alt=""
        style={{ flexShrink: 0 }}
      />
    );
  }
  const domain = getDomainForFavicon(data.assetTagValue);
  if (domain) {
    return (
      <img
        src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`}
        width={18}
        height={18}
        alt=""
        style={{ flexShrink: 0, borderRadius: 3 }}
      />
    );
  }
  return (
    <img
      src={LaptopIcon}
      width={18}
      height={18}
      alt=""
      style={{ flexShrink: 0 }}
    />
  );
}

// ─── Cell renderers ───────────────────────────────────────────────────────────
// Exception: AG Grid cell renderers use inline styles (Polaris tokens don't reach into the grid sandbox)

function AssetNameCellRenderer({ data }) {
  if (!data) return null;
  // Match old UI: personal-account + local-MCP markers for non-Skill rows; malicious marker for Skills
  const isSkill = data.type === "Skill";
  const showLocalMcp = data.hasLocalMcpServer && !isSkill;
  const showPersonal = data.hasPersonalAccount && !isSkill;
  const showMalicious = data.isMalicious && isSkill;
  return (
    <Box
      style={{
        display: "flex",
        alignItems: "center",
        gap: 8,
        width: "100%",
        overflow: "hidden",
      }}
    >
      <AgentIconImg data={data} />
      <Box
        as="span"
        style={{
          fontSize: 13,
          color: "#202223",
          fontWeight: 500,
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis",
        }}
      >
        {data.name}
      </Box>
      {showLocalMcp && (
        <Tooltip content="Local MCP Server" dismissOnMouseOut>
          <Box
            as="span"
            style={{
              flexShrink: 0,
              display: "inline-flex",
              alignItems: "center",
            }}
          >
            <img
              src={McpRedIcon}
              width={16}
              height={16}
              alt="Local MCP Server"
              style={{ pointerEvents: "none" }}
            />
          </Box>
        </Tooltip>
      )}
      {showPersonal && (
        <Tooltip content="Contains personal account" dismissOnMouseOut>
          <Box
            as="span"
            style={{
              flexShrink: 0,
              display: "inline-flex",
              alignItems: "center",
            }}
          >
            <img
              src={PersonLockIcon}
              width={16}
              height={16}
              alt="Contains personal account"
              style={{ pointerEvents: "none" }}
            />
          </Box>
        </Tooltip>
      )}
      {showMalicious && (
        <Tooltip content="Malicious skill" dismissOnMouseOut>
          <Box
            as="span"
            style={{
              flexShrink: 0,
              display: "inline-flex",
              alignItems: "center",
            }}
          >
            <img
              src={MaliciousSkillIcon}
              width={20}
              height={20}
              alt="Malicious skill"
              style={{ pointerEvents: "none" }}
            />
          </Box>
        </Tooltip>
      )}
    </Box>
  );
}

// type badge shown in its own column — used as both renderer and Set Filter display
function TypeBadgeCellRenderer({ value }) {
  if (!value) return null;
  const s = TYPE_STYLES[value] || {
    bg: "#F3F4F6",
    color: "#374151",
    border: "#E5E7EB",
  };
  return (
    <Box style={{ display: "flex", alignItems: "center", height: "100%" }}>
      <Box
        as="span"
        style={{
          display: "inline-flex",
          alignItems: "center",
          height: 20,
          padding: "0 8px",
          borderRadius: 12,
          fontSize: 11,
          fontWeight: 500,
          background: s.bg,
          color: s.color,
          border: `1px solid ${s.border}`,
          whiteSpace: "nowrap",
        }}
      >
        {value}
      </Box>
    </Box>
  );
}

function RiskScoreCellRenderer({ value }) {
  if (value == null) return null;
  const { bg, color } = getRiskColor(value);
  return (
    <Box style={{ display: "flex", alignItems: "center", height: "100%" }}>
      <Box
        as="span"
        style={{
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          width: 44,
          height: 24,
          borderRadius: 12,
          fontSize: 12,
          fontWeight: 600,
          background: bg,
          color,
        }}
      >
        {value.toFixed(1)}
      </Box>
    </Box>
  );
}

function ViolationsCellRenderer({ value }) {
  if (!value) return <Box as="span" style={{ color: "#C4C7CB" }}>-</Box>;
  const parts = ["critical", "high", "medium", "low"]
    .map((k) => ({ k, c: value[k], ...SEVERITY_COLORS[k] }))
    .filter((p) => p.c > 0);
  if (!parts.length) return <Box as="span" style={{ color: "#C4C7CB" }}>-</Box>;
  return (
    <Box style={{ display: "flex", alignItems: "center", gap: 3 }}>
      {parts.map((p) => (
        <Box
          as="span"
          key={p.k}
          style={{
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            minWidth: 22,
            height: 22,
            padding: "0 5px",
            borderRadius: 11,
            fontSize: 11,
            fontWeight: 700,
            background: p.bg,
            color: p.text,
          }}
        >
          {p.c}
        </Box>
      ))}
    </Box>
  );
}

function InteractionsCellRenderer({ value, data }) {
  if (value == null) return <Box as="span" style={{ color: "#C4C7CB" }}>-</Box>;
  const detail = data?.aiInteractionsDetail;
  const title = detail
    ? `Input: ${Number(detail.totalInputTokens || 0).toLocaleString("en-US")} · Output: ${Number(detail.totalOutputTokens || 0).toLocaleString("en-US")}`
    : undefined;
  return (
    <Box as="span" style={{ fontSize: 12, color: "#202223" }} title={title}>
      {Number(value).toLocaleString("en-US")}
    </Box>
  );
}

function GroupCellRenderer({ data }) {
  const [tipPos, setTipPos] = useState(null);
  if (!data?.groups?.length) return null;

  const primary = data.groups[0];
  const rest = data.groups.slice(1);

  return (
    <Box
      style={{ display: "flex", alignItems: "center", height: "100%", gap: 5 }}
    >
      <Box as="span" style={{ fontSize: 12, color: "#202223" }}>
        {primary.name} [{primary.count}]
      </Box>
      {rest.length > 0 && (
        <>
          <Box
            as="span"
            style={{
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              height: 20,
              padding: "0 7px",
              borderRadius: 10,
              fontSize: 11,
              fontWeight: 600,
              background: "#F1F2F3",
              color: "#6D7175",
              cursor: "default",
              userSelect: "none",
            }}
            onMouseEnter={(e) => {
              const r = e.currentTarget.getBoundingClientRect();
              setTipPos({ top: r.bottom + 6, left: r.left });
            }}
            onMouseLeave={() => setTipPos(null)}
          >
            +{rest.length}
          </Box>
          {/* createPortal renders into document.body — escapes AG Grid's overflow:hidden */}
          {tipPos &&
            createPortal(
              <Box
                style={{
                  position: "fixed",
                  top: tipPos.top,
                  left: tipPos.left,
                  background: "white",
                  border: "1px solid #E1E3E5",
                  borderRadius: 8,
                  padding: "8px 12px",
                  zIndex: 9999,
                  whiteSpace: "nowrap",
                  boxShadow: "0 4px 16px rgba(0,0,0,0.12)",
                  pointerEvents: "none",
                }}
              >
                {rest.map((g) => (
                  <Box
                    key={g.name}
                    style={{ fontSize: 12, color: "#202223", padding: "2px 0" }}
                  >
                    {g.name} [{g.count}]
                  </Box>
                ))}
              </Box>,
              document.body,
            )}
        </>
      )}
    </Box>
  );
}

// ─── Column definitions ───────────────────────────────────────────────────────

const COL_DEFS = [
  {
    field: "name",
    headerName: "Agentic Assets",
    width: 460,
    minWidth: 200,
    pinned: "left",
    filter: "agTextColumnFilter",
    checkboxSelection: true,
    headerCheckboxSelection: true,
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

// ─── Top section helpers ──────────────────────────────────────────────────────

// Areaspline sparkline config from a real numeric series. `labels` (month names) drive the
// hover tooltip; pass `enableHover=false` to disable interaction.
function makeAreasplineConfig(
  data,
  color,
  height,
  width,
  labels = [],
  enableHover = true,
) {
  const safe = data && data.length ? data : [0];
  const min = Math.min(...safe),
    max = Math.max(...safe);
  const pad = (max - min) * 0.2 || 1;
  return {
    chart: {
      type: "areaspline",
      height,
      ...(width ? { width } : {}),
      backgroundColor: "transparent",
      margin: [4, 0, 2, 0],
      spacing: [0, 0, 0, 0],
      animation: false,
    },
    title: null,
    credits: { enabled: false },
    exporting: { enabled: false },
    xAxis: { visible: false },
    yAxis: { visible: false, min: min - pad, max: max + pad },
    legend: { enabled: false },
    tooltip: enableHover
      ? {
          enabled: true,
          outside: true,
          backgroundColor: "white",
          borderColor: "#DFE3E8",
          borderRadius: 6,
          style: { fontSize: "11px" },
          formatter: function () {
            const label = labels[this.point.index];
            return label ? `<b>${label}:</b> ${this.y}` : `<b>${this.y}</b>`;
          },
        }
      : { enabled: false },
    plotOptions: {
      areaspline: {
        fillColor: {
          linearGradient: { x1: 0, y1: 0, x2: 0, y2: 1 },
          stops: [
            [0, Highcharts.color(color).setOpacity(0.25).get("rgba")],
            [1, Highcharts.color(color).setOpacity(0).get("rgba")],
          ],
        },
        lineWidth: 2,
        marker: { enabled: false },
        states: { hover: { enabled: enableHover, lineWidth: 2 } },
        enableMouseTracking: enableHover,
      },
    },
    series: [{ data: safe, color }],
  };
}

function TopSectionIcon({ row }) {
  if (row.type === "MCP Server")
    return (
      <img
        src={MCPIcon}
        width={20}
        height={20}
        alt=""
        style={{ flexShrink: 0, borderRadius: 3 }}
      />
    );
  const domain = getDomainForFavicon(row.assetTagValue);
  if (domain)
    return (
      <img
        src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`}
        width={20}
        height={20}
        alt=""
        style={{ flexShrink: 0, borderRadius: 3 }}
      />
    );
  return (
    <img
      src={LaptopIcon}
      width={20}
      height={20}
      alt=""
      style={{ flexShrink: 0, borderRadius: 3, opacity: 0.7 }}
    />
  );
}

// Full-width clickable list row — grows to fill an equal share of the card height (flex: 1).
// No borderRadius so the hover highlight is a clean edge-to-edge rectangle.
// Vertical centering and border-bottom divider are handled in CSS (.agentic-top-list-row).
// padding-inline and padding-block are fully owned by .agentic-top-list-row CSS
// so that hover background and content padding come from the same rule.
function TopListRow({ onClick, children }) {
  return (
    <Box onClick={onClick} className="agentic-top-list-row">
      {children}
    </Box>
  );
}

// Proportion bar — each segment width ∝ count.
// Dynamic colour/width are data-driven and cannot use Polaris tokens, so minimal inline style is used.
function SegmentBar({ segments }) {
  return (
    <Box className="agentic-seg-bar">
      {segments.map(
        (s) =>
          s.count > 0 && (
            <Box
              key={s.label}
              className="agentic-seg"
              title={`${s.label}: ${s.count}`}
              style={{ flexGrow: s.count, background: s.color }}
            />
          ),
      )}
    </Box>
  );
}

// Small legend dot — colour is data-driven; CSS custom property bridges CSS class ↔ dynamic value.
function LegendDot({ color, active }) {
  return (
    <Box
      className={active ? "agentic-dot agentic-dot--active" : "agentic-dot"}
      style={{ "--dot-color": color }}
    />
  );
}

// Shift a cumulative series so its final point equals `total` (the headline source of truth),
// preserving the month-over-month shape. Guarantees hovering the latest month shows `total`.
function anchorSeriesToTotal(series, total) {
  const counts = series.counts || [];
  if (!counts.length || total == null) return series;
  const diff = total - counts[counts.length - 1];
  if (diff === 0) return series;
  return { ...series, counts: counts.map((c) => Math.max(0, c + diff)) };
}

// ─── Top section ──────────────────────────────────────────────────────────────

function violationTotal(row) {
  const v = row?.violations;
  if (!v) return 0;
  return (v.critical || 0) + (v.high || 0) + (v.medium || 0) + (v.low || 0);
}

function TopSection({
  agenticFlatData = [],
  violationRows = [],
  startTimestamp = 0,
  endTimestamp = 0,
  onTypeFilter,
  activeTypeFilter,
  onAssetClick,
  onViolSevFilter,
  activeViolSevFilter,
}) {
  const aiCount = agenticFlatData.filter((r) => r.type === "AI Agent").length;
  const mcpCount = agenticFlatData.filter(
    (r) => r.type === "MCP Server",
  ).length;
  const llmCount = agenticFlatData.filter((r) => r.type === "LLM").length;
  const skillCount = agenticFlatData.filter((r) => r.type === "Skill").length;
  const total = agenticFlatData.length;

  // Source-of-truth violation totals: summed from per-asset aggregated violation objects.
  const critV = agenticFlatData.reduce(
    (s, r) => s + (r.violations?.critical || 0),
    0,
  );
  const highV = agenticFlatData.reduce(
    (s, r) => s + (r.violations?.high || 0),
    0,
  );
  const medV = agenticFlatData.reduce(
    (s, r) => s + (r.violations?.medium || 0),
    0,
  );
  const lowV = agenticFlatData.reduce(
    (s, r) => s + (r.violations?.low || 0),
    0,
  );
  const totalV = critV + highV + medV + lowV;

  // Agentic violation events (rows whose collection belongs to an agentic asset) — drives the
  // time-bucketed shape of the violations chart.
  const agenticViolationRows = useMemo(() => {
    const collectionIds = new Set();
    agenticFlatData.forEach((r) =>
      (r.collectionIds || []).forEach((id) => collectionIds.add(Number(id))),
    );
    return violationRows.filter((v) =>
      collectionIds.has(Number(v.apiCollectionId)),
    );
  }, [agenticFlatData, violationRows]);

  // Card 1 sparklines = cumulative monthly running totals. The LAST point of each series is
  // anchored to the headline number so hovering the latest month shows exactly that total.
  // Assets: cumulative count of assets by last-traffic-seen → ends at `total` (127).
  const assetSeries = useMemo(
    () =>
      anchorSeriesToTotal(
        cumulativeByMonth(
          agenticFlatData,
          (r) => r.lastSeenEpoch || 0,
          startTimestamp,
          endTimestamp,
        ),
        total,
      ),
    [agenticFlatData, startTimestamp, endTimestamp, total],
  );
  // Violations: cumulative violation events → ends at `totalV` (91).
  const violSeries = useMemo(
    () =>
      anchorSeriesToTotal(
        cumulativeByMonth(
          agenticViolationRows,
          (v) => v.timeEpoch || 0,
          startTimestamp,
          endTimestamp,
        ),
        totalV,
      ),
    [agenticViolationRows, startTimestamp, endTimestamp, totalV],
  );

  // +N = net growth across the selected window = last cumulative point − first.
  const windowDelta = (counts) =>
    counts && counts.length >= 2
      ? Math.max(0, counts[counts.length - 1] - counts[0])
      : 0;
  const assetDelta = windowDelta(assetSeries.counts);
  const violDelta = windowDelta(violSeries.counts);

  const assetChartOpts = useMemo(
    () =>
      makeAreasplineConfig(
        assetSeries.counts,
        "#9642FC",
        40,
        140,
        assetSeries.labels,
      ),
    [assetSeries],
  );
  const violChartOpts = useMemo(
    () =>
      makeAreasplineConfig(
        violSeries.counts,
        "#DC2626",
        40,
        140,
        violSeries.labels,
      ),
    [violSeries],
  );

  const topApps = useMemo(
    () =>
      [...agenticFlatData]
        .filter((r) => r.aiInteractions > 0)
        .sort((a, b) => b.aiInteractions - a.aiInteractions)
        .slice(0, 5),
    [agenticFlatData],
  );

  const topViolations = useMemo(
    () =>
      [...agenticFlatData]
        .map((r) => ({ ...r, totalV: violationTotal(r) }))
        .filter((r) => r.totalV > 0)
        .sort((a, b) => b.totalV - a.totalV)
        .slice(0, 5),
    [agenticFlatData],
  );

  // Card 3 row sparklines: per-asset cumulative violation events over months (same source as
  // Card 1 violations — agentic violation rows filtered to each asset's collections).
  const topViolOpts = useMemo(
    () =>
      topViolations.map((row) => {
        const collectionIdSet = new Set((row.collectionIds || []).map(Number));
        const assetViolRows = agenticViolationRows.filter((v) =>
          collectionIdSet.has(Number(v.apiCollectionId)),
        );
        const series = cumulativeByMonth(
          assetViolRows,
          (v) => v.timeEpoch || 0,
          startTimestamp,
          endTimestamp,
        );
        return makeAreasplineConfig(
          series.counts,
          "#EF4444",
          28,
          80,
          series.labels,
          true,
        );
      }),
    [topViolations, agenticViolationRows, startTimestamp, endTimestamp],
  );

  const typeBreakdown = [
    { label: "Agents", count: aiCount, color: "#9642FC", typeKey: "AI Agent" },
    {
      label: "MCP Servers",
      count: mcpCount,
      color: "#4cbebb",
      typeKey: "MCP Server",
    },
    { label: "LLMs", count: llmCount, color: "#EAB308", typeKey: "LLM" },
    { label: "Skills", count: skillCount, color: "#D1D5DB", typeKey: "Skill" },
  ];
  const violBreakdown = [
    { label: "Critical", key: "critical", count: critV, color: "#DC2626" },
    { label: "High", key: "high", count: highV, color: "#F97316" },
    { label: "Medium", key: "medium", count: medV, color: "#EAB308" },
    { label: "Low", key: "low", count: lowV, color: "#D1D5DB" },
  ];

  return (
    <HorizontalGrid columns={3} gap="4">
      {/* ── Card 1: Stats ── */}
      <Card padding="0">
        <Box
          paddingInlineStart="5"
          paddingInlineEnd="5"
          paddingBlockStart="4"
          paddingBlockEnd="3"
        >
          <VerticalStack gap="2">
            <Text variant="headingSm" fontWeight="semibold">
              Agentic Assets
            </Text>
            <HorizontalStack align="space-between" blockAlign="center" gap="3">
              <HorizontalStack gap="2" blockAlign="center">
                <Text variant="heading2xl" as="p">
                  {total}
                </Text>
                {assetDelta > 0 && (
                  <Text variant="bodySm" color="success">
                    +{assetDelta}
                  </Text>
                )}
              </HorizontalStack>
              <HighchartsReact
                highcharts={Highcharts}
                options={assetChartOpts}
              />
            </HorizontalStack>
            <VerticalStack gap="2">
              {total > 0 && <SegmentBar segments={typeBreakdown} />}
              <HorizontalStack gap="3" wrap>
                {typeBreakdown.map((b) => {
                  const active = activeTypeFilter?.has(b.typeKey);
                  return (
                    <Box
                      key={b.label}
                      onClick={() => onTypeFilter?.(b.typeKey)}
                      paddingInlineStart="2"
                      paddingInlineEnd="2"
                      paddingBlockStart="1"
                      paddingBlockEnd="1"
                      className="agentic-chip"
                    >
                      <HorizontalStack gap="1" blockAlign="center">
                        <LegendDot color={b.color} active={active} />
                        <Text variant="bodySm" color="subdued">
                          {b.label} ({b.count})
                        </Text>
                      </HorizontalStack>
                    </Box>
                  );
                })}
              </HorizontalStack>
            </VerticalStack>
          </VerticalStack>
        </Box>
        <Box paddingBlockStart="4" paddingBlockEnd="4">
          <Divider />
        </Box>
        <Box
          paddingInlineStart="5"
          paddingInlineEnd="5"
          paddingBlockStart="3"
          paddingBlockEnd="4"
        >
          <VerticalStack gap="2">
            <Text variant="headingSm" fontWeight="semibold">
              Violations
            </Text>
            <HorizontalStack align="space-between" blockAlign="center" gap="3">
              <HorizontalStack gap="2" blockAlign="center">
                <Text variant="heading2xl" as="p">
                  {totalV}
                </Text>
                {violDelta > 0 && (
                  <Text variant="bodySm" color="critical">
                    +{violDelta}
                  </Text>
                )}
              </HorizontalStack>
              <HighchartsReact
                highcharts={Highcharts}
                options={violChartOpts}
              />
            </HorizontalStack>
            <VerticalStack gap="2">
              {totalV > 0 && <SegmentBar segments={violBreakdown} />}
              <HorizontalStack gap="3" wrap>
                {violBreakdown.map((b) => {
                  const active = activeViolSevFilter?.has(b.key);
                  return (
                    <Box
                      key={b.label}
                      onClick={() => onViolSevFilter?.(b.key)}
                      paddingInlineStart="2"
                      paddingInlineEnd="2"
                      paddingBlockStart="1"
                      paddingBlockEnd="1"
                      className="agentic-chip"
                    >
                      <HorizontalStack gap="1" blockAlign="center">
                        <LegendDot color={b.color} active={active} />
                        <Text variant="bodySm" color="subdued">
                          {b.label}
                        </Text>
                      </HorizontalStack>
                    </Box>
                  );
                })}
              </HorizontalStack>
            </VerticalStack>
          </VerticalStack>
        </Box>
      </Card>

      {/* ── Card 2: Top Used Applications ── */}
      <Card padding="0">
        <Box className="agentic-card">
          <Box
            paddingInlineStart="5"
            paddingInlineEnd="5"
            paddingBlockStart="4"
            paddingBlockEnd="3"
          >
            <Text variant="headingSm">Top Used Applications</Text>
          </Box>
          <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockEnd="2">
            <HorizontalStack align="space-between" blockAlign="center">
              <Text variant="bodySm" color="subdued">
                Agentic Asset
              </Text>
              <Box minWidth="80px">
                <Text variant="bodySm" color="subdued" alignment="end">
                  AI Interactions
                </Text>
              </Box>
            </HorizontalStack>
          </Box>
          <Divider />
          {topApps.length === 0 ? (
            <Box padding="4">
              <Text variant="bodySm" color="subdued">
                No AI interaction data yet.
              </Text>
            </Box>
          ) : (
            <Box className="agentic-row-list">
              {topApps.map((row) => (
                <TopListRow key={row.id} onClick={() => onAssetClick?.(row)}>
                  <HorizontalStack
                    align="space-between"
                    blockAlign="center"
                    gap="2"
                    wrap={false}
                  >
                    <HorizontalStack blockAlign="center" gap="2" wrap={false}>
                      <TopSectionIcon row={row} />
                      <Text variant="bodySm" as="span" truncate>
                        {row.name}
                      </Text>
                    </HorizontalStack>
                    <Box minWidth="80px">
                      <Text variant="bodySm" color="subdued" alignment="end">
                        {func.prettifyShort(row.aiInteractions)}
                      </Text>
                    </Box>
                  </HorizontalStack>
                </TopListRow>
              ))}
            </Box>
          )}
        </Box>
      </Card>

      {/* ── Card 3: Top Assets with Violations ── */}
      <Card padding="0">
        <Box className="agentic-card">
          <Box
            paddingInlineStart="5"
            paddingInlineEnd="5"
            paddingBlockStart="4"
            paddingBlockEnd="3"
          >
            <Text variant="headingSm">Top Assets with Violations</Text>
          </Box>
          <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockEnd="2">
            <HorizontalStack align="space-between" blockAlign="center">
              <Text variant="bodySm" color="subdued">
                Agentic Asset
              </Text>
              <Box minWidth="120px">
                <Text variant="bodySm" color="subdued" alignment="end">
                  Violations
                </Text>
              </Box>
            </HorizontalStack>
          </Box>
          <Divider />
          {topViolations.length === 0 ? (
            <Box padding="4">
              <Text variant="bodySm" color="subdued">
                No violations yet.
              </Text>
            </Box>
          ) : (
            <Box className="agentic-row-list">
              {topViolations.map((row, i) => (
                <TopListRow key={row.id} onClick={() => onAssetClick?.(row)}>
                  <HorizontalStack
                    align="space-between"
                    blockAlign="center"
                    gap="2"
                    wrap={false}
                  >
                    <HorizontalStack blockAlign="center" gap="2" wrap={false}>
                      <TopSectionIcon row={row} />
                      <Text variant="bodySm" as="span" truncate>
                        {row.name}
                      </Text>
                    </HorizontalStack>
                    <Box minWidth="120px">
                      <HorizontalStack
                        align="end"
                        blockAlign="center"
                        gap="3"
                        wrap={false}
                      >
                        <Text variant="bodySm" color="subdued">
                          {row.totalV}
                        </Text>
                        <Box width="80px" minHeight="28px">
                          <HighchartsReact
                            key={`viol-chart-${row.id}`}
                            highcharts={Highcharts}
                            options={topViolOpts[i]}
                          />
                        </Box>
                      </HorizontalStack>
                    </Box>
                  </HorizontalStack>
                </TopListRow>
              ))}
            </Box>
          )}
        </Box>
      </Card>
    </HorizontalGrid>
  );
}

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
    if (!flatRowData.length && !agenticFlatData.length) return;
    const params = new URLSearchParams(window.location.search);
    const assetName = params.get("asset");
    if (!assetName) return;
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
    const row = flatRowData.find(
      (r) => nameMatch(r.name) || idMatch(r.path?.[0]),
    );
    const flat = row
      ? agenticFlatData.find((a) => a.id === row.path[0]) || {
          ...row,
          id: row.path[0],
        }
      : agenticFlatData.find((a) => nameMatch(a.name) || idMatch(a.id));
    if (flat) setFlyout(flat);
  }, [flatRowData, agenticFlatData, setFlyout]);

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
        rowSelection="multiple"
        suppressRowClickSelection
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
    return stored === null ? false : stored === "true";
  });

  useEffect(() => {
    if (localStorage.getItem(LAYOUT_KEY) === "false") {
      navigate("/dashboard/observe/agentic-assets-legacy", { replace: true });
    }
  }, [navigate]);

  // Date range — only data with real time fields is filtered (violations + last-seen)
  const [currDateRange, dispatchCurrDateRange] = useReducer(
    produce((draft, action) => func.dateRangeReducer(draft, action)),
    values.ranges[4],
  );
  const startTimestamp = Math.floor(
    Date.parse(currDateRange.period.since) / 1000,
  );
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
      <Checkbox
        label="New UI"
        checked={newLayout}
        onChange={handleLayoutToggle}
      />
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
        <TopSection
          key="top"
          agenticFlatData={agenticFlatData}
          violationRows={agenticViolationRows}
          startTimestamp={startTimestamp}
          endTimestamp={endTimestamp}
          onTypeFilter={(t) =>
            setTypeFilter((prev) => {
              const next = new Set(prev);
              if (next.has(t)) next.delete(t);
              else next.add(t);
              return next;
            })
          }
          activeTypeFilter={typeFilter}
          onViolSevFilter={(sev) =>
            setViolSevFilter((prev) => {
              const next = new Set(prev);
              if (next.has(sev)) next.delete(sev);
              else next.add(sev);
              return next;
            })
          }
          activeViolSevFilter={violSevFilter}
          onAssetClick={setFlyout}
        />,
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
