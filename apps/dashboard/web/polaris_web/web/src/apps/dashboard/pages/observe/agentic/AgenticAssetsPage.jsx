import React, { useState, useCallback, useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";
import { LicenseManager, AllEnterpriseModule } from "ag-grid-enterprise";
import MCPIcon from "@/assets/MCP_Icon.svg";
import LaptopIcon from "@/assets/Laptop.svg";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import TitleWithInfo from "@/apps/dashboard/components/shared/TitleWithInfo";
import PageWithMultipleCards from "@/apps/dashboard/components/layouts/PageWithMultipleCards";
import AgenticAssetFlyout from "./AgenticAssetFlyout";
import { TYPE_STYLES, SEVERITY_COLORS, getRiskColor } from "./agenticStyles";
import { getDomainForFavicon } from "./mcpClientHelper";
import { AGENTIC_TREE_DATA, AGENTIC_FLAT_DATA } from "./agenticDummyData";

ModuleRegistry.registerModules([AllCommunityModule, AllEnterpriseModule]);

LicenseManager.setLicenseKey(
    "[TRIAL]_this_{AG_Charts_and_AG_Grid}_Enterprise_key_{AG-129492}_is_granted_for_evaluation_only___Use_in_production_is_not_permitted___Please_report_misuse_to_legal@ag-grid.com___For_help_with_purchasing_a_production_key_please_contact_info@ag-grid.com___You_are_granted_a_{Single_Application}_Developer_License_for_one_application_only___All_Front-End_JavaScript_developers_working_on_the_application_would_need_to_be_licensed___This_key_will_deactivate_on_{18 June 2026}____[v3]_[0102]_MTc4MTczNzIwMDAwMA==d27c8a4487e577f42d9980e95824f43c"
);

// parent-only rows (path.length === 1)
const FLAT_ROW_DATA = AGENTIC_TREE_DATA.filter(r => r.path.length === 1);

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
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 8, width: "100%", overflow: "hidden" }}>
            <AgentIconImg data={data} />
            <span style={{ fontSize: 13, color: "#202223", fontWeight: 500, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {data.name}
            </span>
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

function InteractionsCellRenderer({ value }) {
    if (value == null) return <span style={{ color: "#C4C7CB" }}>—</span>;
    return <span style={{ fontSize: 12, color: "#202223" }}>{value.toLocaleString("en-IN")}</span>;
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
        flex: 2,
        minWidth: 220,
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

// ─── Table section ────────────────────────────────────────────────────────────

function TableSection() {
    const [flyout, setFlyout] = useState(null);
    const gridRef = useRef(null);

    // Auto-open flyout when arriving from DeviceFlyout via ?asset= param
    useEffect(() => {
        const params    = new URLSearchParams(window.location.search);
        const assetName = params.get("asset");
        if (!assetName) return;
        // Check parent rows in table first
        const row  = FLAT_ROW_DATA.find(r => r.name === assetName || r.path[0] === assetName);
        // Also check AGENTIC_FLAT_DATA directly — covers child assets (e.g. kubernetes-mcp)
        const flat = row
            ? (AGENTIC_FLAT_DATA.find(a => a.id === row.path[0]) || { ...row, id: row.path[0] })
            : AGENTIC_FLAT_DATA.find(a => a.name === assetName || a.id === assetName);
        if (flat) setFlyout(flat);
    }, []);

    const handleRowClick = useCallback((e) => {
        if (!e.data) return;
        const flat = AGENTIC_FLAT_DATA.find(a => a.id === e.data.path?.[0]) || { ...e.data, id: e.data.path?.[0] };
        setFlyout(flat);
    }, []);

    const handleClose             = useCallback(() => setFlyout(null), []);
    // Fix 3: when a component inside the flyout navigates to another asset, update the active flyout
    const handleNavigateToAsset   = useCallback((assetData) => setFlyout(assetData), []);
    const getRowStyle = useCallback(() => ({ cursor: "pointer" }), []);

    return (
        <>
            <AgGridTable
                gridRef={gridRef}
                rowData={FLAT_ROW_DATA}
                columnDefs={COL_DEFS}
                defaultColDef={DEFAULT_COL_DEF}
                height={800}
                rowHeight={44}
                headerHeight={40}
                searchPlaceholder="Search agentic assets..."
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
            />
        </>
    );
}

// ─── Page ─────────────────────────────────────────────────────────────────────

export default function AgenticAssetsPage() {
    return (
        <PageWithMultipleCards
            title={
                <TitleWithInfo
                    tooltipContent="All agentic assets observed across your environment — AI Agents, MCP Servers, LLMs, and Skills."
                    titleText="Agentic assets"
                />
            }
            isFirstPage={true}
            components={[<TableSection key="table" />]}
        />
    );
}
