import React, { useState, useCallback, useEffect, useRef, useMemo } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import Highcharts from "highcharts";
import HighchartsMore from "highcharts/highcharts-more";
import { HighchartsReact } from "highcharts-react-official";
import { LegacyCard, Box, HorizontalStack, HorizontalGrid, VerticalStack, Text, Divider, Checkbox } from "@shopify/polaris";
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { LicenseManager, AllEnterpriseModule } from "ag-grid-enterprise";
import MCPIcon from "@/assets/MCP_Icon.svg";
import LaptopIcon from "@/assets/Laptop.svg";
import PersonLockIcon from "@/assets/PersonLockIcon.svg";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import AgenticAssetFlyout from "./AgenticAssetFlyout";
import { TYPE_STYLES, SEVERITY_COLORS, getRiskColor } from "./agenticStyles";
import { getDomainForFavicon } from "./mcpClientHelper";
import SpinnerCentered from "@/apps/dashboard/components/progress/SpinnerCentered";
import api from "../api";
import agenticObserveApi, { aggregateViolationsByCollectionId } from "./agenticObserveApi";
import { buildAgenticAssetsPageData, buildUserAnalysisLookup, fetchAndCacheSkillApiData } from "./constants";
import PersistStore from "../../../../main/PersistStore";
import { fetchEndpointShieldUserMetadata } from "../api_collections/endpointShieldHelper";

HighchartsMore(Highcharts);

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);

LicenseManager.setLicenseKey(
    "[TRIAL]_this_{AG_Charts_and_AG_Grid}_Enterprise_key_{AG-129492}_is_granted_for_evaluation_only___Use_in_production_is_not_permitted___Please_report_misuse_to_legal@ag-grid.com___For_help_with_purchasing_a_production_key_please_contact_info@ag-grid.com___You_are_granted_a_{Single_Application}_Developer_License_for_one_application_only___All_Front-End_JavaScript_developers_working_on_the_application_would_need_to_be_licensed___This_key_will_deactivate_on_{18 June 2026}____[v3]_[0102]_MTc4MTczNzIwMDAwMA==d27c8a4487e577f42d9980e95824f43c"
);

// ─── Icon helpers ─────────────────────────────────────────────────────────────

function AgentIconImg({ data }) {
    if (!data) return null;
    if (data.type === "MCP Server") {
        return <img src={MCPIcon} width={20} height={20} alt="" style={{ flexShrink: 0, borderRadius: 3 }} />;
    }
    if (data.type === "Skill") {
        return <img src={LaptopIcon} width={18} height={18} alt="" style={{ flexShrink: 0, opacity: 0.7 }} />;
    }
    const domain = getDomainForFavicon(data.assetTagValue);
    if (domain) {
        return <img src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`} width={18} height={18} alt="" style={{ flexShrink: 0, borderRadius: 3 }} />;
    }
    return <img src={LaptopIcon} width={18} height={18} alt="" style={{ flexShrink: 0 }} />;
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
        <div style={{ display: "flex", alignItems: "center", gap: 8, width: "100%", overflow: "hidden" }}>
            <AgentIconImg data={data} />
            <span style={{ fontSize: 13, color: "#202223", fontWeight: 500, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {data.name}
            </span>
            {showLocalMcp && (
                <span title="Local MCP Server" style={{ flexShrink: 0, display: "inline-flex", alignItems: "center" }}>
                    <img src={MCPIcon} width={16} height={16} alt="Local MCP Server" style={{ pointerEvents: "none" }} />
                </span>
            )}
            {showPersonal && (
                <span title="Contains personal account" style={{ flexShrink: 0, display: "inline-flex", alignItems: "center" }}>
                    <img src={PersonLockIcon} width={16} height={16} alt="Contains personal account" style={{ pointerEvents: "none" }} />
                </span>
            )}
            {showMalicious && (
                <span title="Malicious skill" style={{ flexShrink: 0, display: "inline-flex", alignItems: "center", padding: "1px 6px", borderRadius: 10, fontSize: 10, fontWeight: 600, background: "#FBEAE5", color: "#C4320A" }}>
                    Malicious
                </span>
            )}
        </div>
    );
}

// type badge shown in its own column — used as both renderer and Set Filter display
function TypeBadgeCellRenderer({ value }) {
    if (!value) return null;
    const s = TYPE_STYLES[value] || { bg: "#F3F4F6", color: "#374151", border: "#E5E7EB" };
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", height: 20, padding: "0 8px", borderRadius: 12, fontSize: 11, fontWeight: 500, background: s.bg, color: s.color, border: `1px solid ${s.border}`, whiteSpace: "nowrap" }}>
                {value}
            </span>
        </div>
    );
}

function RiskScoreCellRenderer({ value }) {
    if (value == null) return null;
    const { bg, color } = getRiskColor(value);
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", width: 44, height: 24, borderRadius: 12, fontSize: 12, fontWeight: 600, background: bg, color }}>
                {value.toFixed(1)}
            </span>
        </div>
    );
}

function ViolationsCellRenderer({ value }) {
    if (!value) return <span style={{ color: "#C4C7CB" }}>—</span>;
    const parts = ["critical", "high", "medium", "low"]
        .map(k => ({ k, c: value[k], ...SEVERITY_COLORS[k] }))
        .filter(p => p.c > 0);
    if (!parts.length) return <span style={{ color: "#C4C7CB" }}>—</span>;
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 3 }}>
            {parts.map(p => (
                <span key={p.k} style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", minWidth: 22, height: 22, padding: "0 5px", borderRadius: 11, fontSize: 11, fontWeight: 700, background: p.bg, color: p.text }}>
                    {p.c}
                </span>
            ))}
        </div>
    );
}

function InteractionsCellRenderer({ value, data }) {
    if (value == null) return <span style={{ color: "#C4C7CB" }}>—</span>;
    const detail = data?.aiInteractionsDetail;
    const title = detail
        ? `Input: ${Number(detail.totalInputTokens || 0).toLocaleString("en-IN")} · Output: ${Number(detail.totalOutputTokens || 0).toLocaleString("en-IN")}`
        : undefined;
    return (
        <span style={{ fontSize: 12, color: "#202223" }} title={title}>
            {Number(value).toLocaleString("en-IN")}
        </span>
    );
}

function GroupCellRenderer({ data }) {
    const [tipPos, setTipPos] = useState(null);
    if (!data?.groups?.length) return null;

    const primary = data.groups[0];
    const rest    = data.groups.slice(1);

    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", gap: 5 }}>
            <span style={{ fontSize: 12, color: "#202223" }}>
                {primary.name} [{primary.count}]
            </span>
            {rest.length > 0 && (
                <>
                    <span
                        style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", height: 20, padding: "0 7px", borderRadius: 10, fontSize: 11, fontWeight: 600, background: "#F1F2F3", color: "#6D7175", cursor: "default", userSelect: "none" }}
                        onMouseEnter={e => {
                            const r = e.currentTarget.getBoundingClientRect();
                            setTipPos({ top: r.bottom + 6, left: r.left });
                        }}
                        onMouseLeave={() => setTipPos(null)}
                    >
                        +{rest.length}
                    </span>
                    {/* createPortal renders into document.body — escapes AG Grid's overflow:hidden */}
                    {tipPos && createPortal(
                        <div style={{ position: "fixed", top: tipPos.top, left: tipPos.left, background: "white", border: "1px solid #E1E3E5", borderRadius: 8, padding: "8px 12px", zIndex: 9999, whiteSpace: "nowrap", boxShadow: "0 4px 16px rgba(0,0,0,0.12)", pointerEvents: "none" }}>
                            {rest.map(g => (
                                <div key={g.name} style={{ fontSize: 12, color: "#202223", padding: "2px 0" }}>
                                    {g.name} [{g.count}]
                                </div>
                            ))}
                        </div>,
                        document.body
                    )}
                </>
            )}
        </div>
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
        cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#202223" },
        valueFormatter: p => p.value != null ? p.value : "",
    },
    {
        field: "aiInteractions",
        headerName: "AI Interactions",
        headerTooltip: "Total tokens from UserAnalysisData (input + output) across devices using this asset.",
        width: 150,
        filter: false,
        cellRenderer: InteractionsCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
    },
    {
        field: "violations",
        headerName: "Violations",
        width: 160,
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
        cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" },
    },
];

const DEFAULT_COL_DEF = {
    sortable: true,
    resizable: true,
    filter: false,
    cellStyle: { display: "flex", alignItems: "center" },
};

// ─── Top section helpers ──────────────────────────────────────────────────────

const ASSET_TREND     = [18,19,20,21,20,22,23,22,25,26,27,29];
const VIOLATION_TREND = [900,920,950,980,1000,1020,1050,1090,1120,1150,1180,1203];

function sparklineFromTemplate(template, intensity = 1) {
    const tMax = Math.max(...template, 1);
    const scale = Math.min(1, Math.max(0.15, intensity));
    return template.map((v) => Math.max(1, Math.round((v / tMax) * 100 * scale)));
}

// areaspline smooth curves; omit width for full-container auto-width, or pass explicit width
function makeAreasplineConfig(data, color, height = 50, width = undefined, margin = [2, 0, 2, 0]) {
    const min = Math.min(...data), max = Math.max(...data);
    const pad = (max - min) * 0.2 || 1;
    return {
        chart: { type: "areaspline", height, ...(width ? { width } : {}), backgroundColor: "transparent", margin, spacing: [0,0,0,0], animation: false },
        title: null, credits: { enabled: false }, exporting: { enabled: false },
        xAxis: { visible: false }, yAxis: { visible: false, min: min - pad, max: max + pad },
        legend: { enabled: false }, tooltip: { enabled: false },
        plotOptions: { areaspline: {
            fillColor: { linearGradient: { x1:0, y1:0, x2:0, y2:1 }, stops: [[0, Highcharts.color(color).setOpacity(0.25).get("rgba")], [1, Highcharts.color(color).setOpacity(0).get("rgba")]] },
            lineWidth: 2, marker: { enabled: false }, states: { hover: { enabled: false } }, enableMouseTracking: false,
        }},
        series: [{ data, color }],
    };
}

function TopSectionIcon({ row }) {
    if (row.type === "MCP Server") return <img src={MCPIcon} width={18} height={18} alt="" style={{ flexShrink: 0, borderRadius: 3 }} />;
    const domain = getDomainForFavicon(row.assetTagValue);
    if (domain) return <img src={`https://www.google.com/s2/favicons?domain=${domain}&sz=64`} width={18} height={18} alt="" style={{ flexShrink: 0, borderRadius: 3 }} />;
    return <img src={LaptopIcon} width={18} height={18} alt="" style={{ flexShrink: 0, borderRadius: 3, opacity: 0.7 }} />;
}

// ─── Top section ──────────────────────────────────────────────────────────────

function violationTotal(row) {
    const v = row?.violations;
    if (!v) return 0;
    return (v.critical || 0) + (v.high || 0) + (v.medium || 0) + (v.low || 0);
}

function TopSection({ agenticFlatData = [], onTypeFilter, activeTypeFilter, onAssetClick }) {
    const aiCount    = agenticFlatData.filter(r => r.type === "AI Agent").length;
    const mcpCount   = agenticFlatData.filter(r => r.type === "MCP Server").length;
    const llmCount   = agenticFlatData.filter(r => r.type === "LLM").length;
    const skillCount = agenticFlatData.filter(r => r.type === "Skill").length;
    const total      = agenticFlatData.length;

    const critV  = agenticFlatData.reduce((s, r) => s + (r.violations?.critical || 0), 0);
    const highV  = agenticFlatData.reduce((s, r) => s + (r.violations?.high    || 0), 0);
    const medV   = agenticFlatData.reduce((s, r) => s + (r.violations?.medium  || 0), 0);
    const lowV   = agenticFlatData.reduce((s, r) => s + (r.violations?.low     || 0), 0);
    const totalV = critV + highV + medV + lowV;

    const topApps = useMemo(() =>
        [...agenticFlatData].filter(r => r.aiInteractions > 0)
            .sort((a, b) => b.aiInteractions - a.aiInteractions).slice(0, 5), [agenticFlatData]);

    const topViolations = useMemo(() =>
        [...agenticFlatData]
            .map(r => ({ ...r, totalV: violationTotal(r) }))
            .filter(r => r.totalV > 0).sort((a, b) => b.totalV - a.totalV).slice(0, 5), [agenticFlatData]);

    // 1 — areaspline configs for the stat cards (full-width, height 80)
    // stat charts: width=140, height=50 — to the right of the number, same as DeviceEndpoints StatRow
    const assetChartOpts = useMemo(() => makeAreasplineConfig(ASSET_TREND,     "#9642FC", 40, 140), []);
    const violChartOpts  = useMemo(() => makeAreasplineConfig(VIOLATION_TREND, "#DC2626", 40, 140), []);

    const maxInteractions = useMemo(
        () => Math.max(...topApps.map((r) => r.aiInteractions || 0), 1),
        [topApps],
    );

    // mini charts for list rows: explicit width=80, height=36
    const topAppOpts = useMemo(() => topApps.map((row) => {
        const intensity = (row.aiInteractions || 0) / maxInteractions;
        return makeAreasplineConfig(sparklineFromTemplate(ASSET_TREND, intensity), "#9642FC", 36, 80);
    }), [topApps, maxInteractions]);

    const maxViolationCount = useMemo(
        () => Math.max(...topViolations.map((r) => r.totalV || 0), 1),
        [topViolations],
    );

    const topViolOpts = useMemo(() => topViolations.map((row) => {
        const intensity = (row.totalV || 0) / maxViolationCount;
        return makeAreasplineConfig(sparklineFromTemplate(VIOLATION_TREND, intensity), "#EF4444", 36, 80);
    }), [topViolations, maxViolationCount]);

    const typeBreakdown = [
        { label: "Agents",      count: aiCount,    color: "#9642FC", typeKey: "AI Agent"   },
        { label: "MCP Servers", count: mcpCount,   color: "#4cbebb", typeKey: "MCP Server" },
        { label: "LLMs",        count: llmCount,   color: "#EAB308", typeKey: "LLM"        },
        { label: "Skills",      count: skillCount, color: "#D1D5DB", typeKey: "Skill"      },
    ];
    const violBreakdown = [
        { label: "Critical", count: critV, color: "#DC2626" },
        { label: "High",     count: highV, color: "#F97316" },
        { label: "Medium",   count: medV,  color: "#EAB308" },
        { label: "Low",      count: lowV,  color: "#D1D5DB" },
    ];

    return (
        <HorizontalGrid columns={3} gap="4">

            {/* ── Card 1: Stats — two sections, each half of the 300px card height ── */}
            <LegacyCard>
                <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="4" paddingBlockEnd="3" minHeight="150px">
                    <VerticalStack gap="2">
                        <Text variant="headingSm" fontWeight="semibold">Agentic Assets</Text>
                        <HorizontalStack align="space-between" blockAlign="center" gap="3">
                            <Text variant="heading2xl" as="p">{total}</Text>
                            <HighchartsReact highcharts={Highcharts} options={assetChartOpts} immutable />
                        </HorizontalStack>
                        <VerticalStack gap="2">
                            <div style={{ display: "flex", height: 5, borderRadius: 3, overflow: "hidden", gap: 1 }}>
                                {typeBreakdown.map(b => total > 0 && <div key={b.label} title={`${b.label}: ${b.count}`} style={{ flex: b.count, background: b.color, minWidth: b.count > 0 ? 2 : 0 }} />)}
                            </div>
                            <HorizontalStack gap="2" wrap>
                                {typeBreakdown.map(b => {
                                    const active = activeTypeFilter?.has(b.typeKey);
                                    return (
                                        <div
                                            key={b.label}
                                            onClick={() => onTypeFilter?.(b.typeKey)}
                                            title={`${b.typeKey}: ${b.count}`}
                                            style={{ cursor: "pointer", display: "flex", alignItems: "center", gap: 4, padding: "2px 8px", borderRadius: 12, background: active ? b.color + "22" : "transparent", border: active ? `1px solid ${b.color}` : "1px solid transparent", transition: "all 0.15s" }}
                                        >
                                            <div style={{ width: 7, height: 7, borderRadius: "50%", background: b.color, flexShrink: 0 }} />
                                            <Text variant="bodySm" color="subdued">{b.label} ({b.count})</Text>
                                        </div>
                                    );
                                })}
                            </HorizontalStack>
                        </VerticalStack>
                    </VerticalStack>
                </Box>
                <Divider />
                <Box paddingInlineStart="5" paddingInlineEnd="5" paddingBlockStart="3" paddingBlockEnd="4" minHeight="150px">
                    <VerticalStack gap="2">
                        <Text variant="headingSm" fontWeight="semibold">Violations</Text>
                        <HorizontalStack align="space-between" blockAlign="center" gap="3">
                            <Text variant="heading2xl" as="p">{totalV}</Text>
                            <HighchartsReact highcharts={Highcharts} options={violChartOpts} immutable />
                        </HorizontalStack>
                        <VerticalStack gap="2">
                            <div style={{ display: "flex", height: 5, borderRadius: 3, overflow: "hidden", gap: 1 }}>
                                {violBreakdown.map(b => totalV > 0 && <div key={b.label} style={{ flex: b.count, background: b.color, minWidth: b.count > 0 ? 2 : 0 }} />)}
                            </div>
                            <HorizontalStack gap="3" wrap>
                                {violBreakdown.map(b => (
                                    <HorizontalStack key={b.label} gap="1" blockAlign="center">
                                        <div style={{ width: 7, height: 7, borderRadius: "50%", background: b.color, flexShrink: 0 }} />
                                        <Text variant="bodySm" color="subdued">{b.label}</Text>
                                    </HorizontalStack>
                                ))}
                            </HorizontalStack>
                        </VerticalStack>
                    </VerticalStack>
                </Box>
            </LegacyCard>

            {/* ── Card 2: Top Used Applications — each row opens the asset flyout ── */}
            <LegacyCard>
                <Box padding="4" minHeight="300px" overflowX="hidden" overflowY="hidden">
                    <VerticalStack gap="0">
                        <Box paddingBlockEnd="3">
                            <Text variant="headingSm">Top Used Applications</Text>
                        </Box>
                        {topApps.length === 0 && (
                            <Text variant="bodySm" color="subdued">No AI interaction data yet.</Text>
                        )}
                        {topApps.map((row, i) => (
                                <React.Fragment key={row.id}>
                                    {i > 0 && <Divider />}
                                    <Box paddingBlockStart="3" paddingBlockEnd="3">
                                        <div onClick={() => onAssetClick?.(row)} style={{ cursor: "pointer" }}>
                                            <HorizontalStack blockAlign="center" gap="2" wrap={false}>
                                                <TopSectionIcon row={row} />
                                                <Text variant="bodySm" as="span" truncate>{row.name}</Text>
                                                <div style={{ flex: 1 }} />
                                                <Text variant="bodySm" color="subdued">{row.aiInteractions.toLocaleString("en-IN")}</Text>
                                                <div style={{ width: 80, height: 36, flexShrink: 0 }}>
                                                    <HighchartsReact
                                                        key={`app-chart-${row.id}`}
                                                        highcharts={Highcharts}
                                                        options={topAppOpts[i]}
                                                        immutable
                                                    />
                                                </div>
                                            </HorizontalStack>
                                        </div>
                                    </Box>
                                </React.Fragment>
                        ))}
                    </VerticalStack>
                </Box>
            </LegacyCard>

            {/* ── Card 3: Top Agentic Asset with Violations — each row opens the asset flyout ── */}
            <LegacyCard>
                <Box padding="4" minHeight="300px" overflowX="hidden" overflowY="hidden">
                    <VerticalStack gap="0">
                        <Box paddingBlockEnd="3">
                            <Text variant="headingSm">Top Agentic Asset with Violations</Text>
                        </Box>
                        {topViolations.map((row, i) => (
                                <React.Fragment key={row.id}>
                                    {i > 0 && <Divider />}
                                    <Box paddingBlockStart="3" paddingBlockEnd="3">
                                        <div onClick={() => onAssetClick?.(row)} style={{ cursor: "pointer" }}>
                                            <HorizontalStack blockAlign="center" gap="2" wrap={false}>
                                                <TopSectionIcon row={row} />
                                                <Text variant="bodySm" as="span" truncate>{row.name}</Text>
                                                <div style={{ flex: 1 }} />
                                                <Text variant="bodySm" color="subdued">{row.totalV}</Text>
                                                <div style={{ width: 80, height: 36, flexShrink: 0 }}>
                                                    <HighchartsReact
                                                        key={`viol-chart-${row.id}`}
                                                        highcharts={Highcharts}
                                                        options={topViolOpts[i]}
                                                        immutable
                                                    />
                                                </div>
                                            </HorizontalStack>
                                        </div>
                                    </Box>
                                </React.Fragment>
                        ))}
                    </VerticalStack>
                </Box>
            </LegacyCard>

        </HorizontalGrid>
    );
}

// ─── Table section ────────────────────────────────────────────────────────────

function TableSection({ agenticTreeData, agenticFlatData, assetDevices, typeFilter, flyout, setFlyout }) {
    const gridRef = useRef(null);

    const flatRowData = useMemo(() => {
        const rows = agenticTreeData.filter((r) => r.path?.length === 1);
        if (!typeFilter || typeFilter.size === 0) return rows;
        return rows.filter((r) => typeFilter.has(r.type));
    }, [agenticTreeData, typeFilter]);

    useEffect(() => {
        const params    = new URLSearchParams(window.location.search);
        const assetName = params.get("asset");
        if (!assetName) return;
        const row  = flatRowData.find((r) => r.name === assetName || r.path[0] === assetName);
        const flat = row
            ? (agenticFlatData.find((a) => a.id === row.path[0]) || { ...row, id: row.path[0] })
            : agenticFlatData.find((a) => a.name === assetName || a.id === assetName);
        if (flat) setFlyout(flat);
    }, [flatRowData, agenticFlatData, setFlyout]);

    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        const flat = agenticFlatData.find((a) => a.id === e.data.path?.[0]) || { ...e.data, id: e.data.path?.[0] };
        setFlyout(flat);
    }, [agenticFlatData, setFlyout]);

    const handleClose           = useCallback(() => setFlyout(null), [setFlyout]);
    const handleNavigateToAsset = useCallback((assetData) => setFlyout(assetData), [setFlyout]);
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
    const [flyout,     setFlyout]     = useState(null);
    const [loading, setLoading] = useState(true);
    const [agenticTreeData, setAgenticTreeData] = useState([]);
    const [agenticFlatData, setAgenticFlatData] = useState([]);
    const [assetDevices, setAssetDevices] = useState({});
    const [newLayout, setNewLayout] = useState(() => {
        const stored = localStorage.getItem(LAYOUT_KEY);
        return stored === null ? true : stored === "true";
    });

    const handleLayoutToggle = useCallback((checked) => {
        localStorage.setItem(LAYOUT_KEY, String(checked));
        setNewLayout(checked);
        if (!checked) navigate("/dashboard/observe/agentic-assets-legacy");
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
                    agenticObserveApi.fetchAgenticViolations({}),
                    agenticObserveApi.listUserAnalysis(),
                ]);

                if (!isMountedRef.current) return;

                const collections = apiCollectionsResp?.apiCollections || [];
                const trafficMap = trafficInfoResp || {};
                const riskScoreMap = riskScoreResp?.riskScoreOfCollectionsMap || {};
                const sensitiveMap = sensitiveInfoResp?.sensitiveSubtypesInCollection || {};
                const {
                    usernameMap = {},
                    userMetadataMap = {},
                    userAnalysisKeysByDeviceId = new Map(),
                } = shieldResult || {};
                const violationsByCollectionId = aggregateViolationsByCollectionId(violationRows);
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

                // Enrich Skill rows with malicious flag (same source as old UI) — async, non-blocking
                const skillCollectionIds = [];
                collections.forEach((c) => {
                    if (!skillCollectionIds.includes(c.id)) skillCollectionIds.push(c.id);
                });
                if (skillCollectionIds.length) {
                    fetchAndCacheSkillApiData(skillCollectionIds, { api, PersistStore })
                        .then(({ maliciousSkills }) => {
                            if (!isMountedRef.current || !maliciousSkills?.size) return;
                            const markMalicious = (rows) => rows.map((r) =>
                                r.type === "Skill" && maliciousSkills.has(r.name)
                                    ? { ...r, isMalicious: true }
                                    : r
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
                }
            } finally {
                if (isMountedRef.current) setLoading(false);
            }
        })();
        return () => { isMountedRef.current = false; };
    }, []);

    const layoutToggle = (
        <Checkbox
            label="New layout"
            checked={newLayout}
            onChange={handleLayoutToggle}
        />
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
                secondaryActions={layoutToggle}
                components={[<SpinnerCentered key="loading" />]}
            />
        );
    }

    return (
        <PageWithMultipleCards
            title={pageTitle}
            isFirstPage={true}
            secondaryActions={layoutToggle}
            components={[
                <TopSection
                    key="top"
                    agenticFlatData={agenticFlatData}
                    onTypeFilter={t => setTypeFilter(prev => {
                        const next = new Set(prev);
                        if (next.has(t)) next.delete(t); else next.add(t);
                        return next;
                    })}
                    activeTypeFilter={typeFilter}
                    onAssetClick={setFlyout}
                />,
                <TableSection
                    key="table"
                    agenticTreeData={agenticTreeData}
                    agenticFlatData={agenticFlatData}
                    assetDevices={assetDevices}
                    typeFilter={typeFilter}
                    flyout={flyout}
                    setFlyout={setFlyout}
                />,
            ]}
        />
    );
}
