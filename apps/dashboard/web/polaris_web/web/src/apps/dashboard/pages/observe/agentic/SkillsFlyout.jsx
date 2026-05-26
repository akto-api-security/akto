import React, { useState, useMemo, useEffect, useRef, useCallback } from "react";
import { AgGridReact } from "ag-grid-react";
import { themeQuartz } from "ag-grid-enterprise";
import { Tabs, Popover, ActionList, LegacyCard, Link, Icon, TextField, Badge } from "@shopify/polaris";
import { ChevronDownMinor } from "@shopify/polaris-icons";
import AgenticSearchInput from "../../agentic/components/AgenticSearchInput";
import SampleDataComponent from "../../../components/shared/SampleDataComponent";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import "../../../components/layouts/style.css";

// ─── Theme ────────────────────────────────────────────────────────────────────

const gridTheme = themeQuartz.withParams({
    accentColor: "#9642FC",
    borderColor: "#E1E3E5",
    borderRadius: 4,
    browserColorScheme: "light",
    cellTextColor: "#202223",
    columnBorder: false,
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    fontSize: 13,
    foregroundColor: "#202223",
    headerFontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
    headerRowBorder: true,
    headerTextColor: "#6D7175",
    rowBorder: true,
    spacing: 8,
    wrapperBorder: false,
    headerFontSize: 12,
    headerFontWeight: 500,
    checkboxBorderRadius: 4,
});

// ─── Dummy skill data ─────────────────────────────────────────────────────────

const BASE_SKILL_NAMES = [
    "Generate Snapshot", "Design a Framework", "Construct a Prototype",
    "Craft a Snapshot", "Collect Insights", "Compile an Overview",
    "Generate an Analysis Report", "Summarize Findings", "Draft a Proposal",
    "Examine Data", "Prepare Documentation", "Master Testing Techniques",
    "Run Diagnostic", "Deploy Service", "Query Database", "Execute Script",
    "Fetch Credentials", "Parse Config", "Validate Schema", "Export Report",
    "Sync Repository", "Trigger Pipeline", "Scan Endpoints", "Audit Logs",
    "Monitor Resources", "Rotate Secrets", "Invoke Lambda", "List Buckets",
];

function generateSkills(total) {
    return Array.from({ length: total }, (_, i) => ({
        id: i,
        name: BASE_SKILL_NAMES[i % BASE_SKILL_NAMES.length] +
              (i >= BASE_SKILL_NAMES.length ? ` v${Math.floor(i / BASE_SKILL_NAMES.length) + 1}` : ""),
        isNew: i < 6,
        violations: i === 0 ? 1 : 0,
        blocked: false,
    }));
}

const DUMMY_SKILL_SAMPLE = {
    message: JSON.stringify({
        method: "POST",
        path: "/mcp/tools/call",
        requestHeaders: JSON.stringify({
            "content-type": "application/json",
            "authorization": "Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyXzAwMSJ9",
            "x-mcp-session": "sess_8f2a91b4c3d1",
        }),
        requestPayload: JSON.stringify({
            name: "generate_snapshot",
            arguments: {
                prompt: "Generate a complete state snapshot",
                context: { sessionId: "sess_8f2a91b4c3d1", depth: 2 },
            },
        }),
        statusCode: 200,
        responseHeaders: JSON.stringify({
            "content-type": "application/json",
            "x-mcp-request-id": "req_7c3d12e9a4b5",
        }),
        responsePayload: JSON.stringify({
            content: [{
                type: "text",
                text: JSON.stringify({
                    snapshot: { id: "snap_20260522_001", timestamp: "2026-05-22T10:30:00Z", status: "complete" },
                    usage: { promptTokens: 142, completionTokens: 89 },
                }),
            }],
        }),
    }),
};

// ─── Cell renderers ───────────────────────────────────────────────────────────

function SkillNameCell({ data }) {
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6, width: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 13, color: "#202223", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {data.name}
            </span>
            {data.isNew && (
                <span style={{
                    flexShrink: 0,
                    fontSize: 11, fontWeight: 500,
                    padding: "2px 8px", borderRadius: 12,
                    background: "#F1F2F3", color: "#6D7175",
                    border: "1px solid #E1E3E5",
                    lineHeight: "16px",
                    display: "inline-flex", alignItems: "center",
                }}>New</span>
            )}
        </div>
    );
}

function ViolationCell({ data }) {
    if (!data.violations) {
        return <span style={{ color: "#8C9196", fontSize: 13 }}>–</span>;
    }
    return (
        <span style={{
            display: "inline-flex", alignItems: "center", justifyContent: "center",
            minWidth: 22, height: 22, padding: "0 5px", borderRadius: 11,
            fontSize: 11, fontWeight: 700,
            background: "#DF2909", color: "#FFFBFB",
        }}>
            {data.violations}
        </span>
    );
}

// ─── Skill schema params ──────────────────────────────────────────────────────

const SKILL_SCHEMA_PARAMS = [
    { name: "prompt",     type: "string", required: true,  desc: "The instruction or prompt for the skill to execute" },
    { name: "context",    type: "object", required: false, desc: "Optional session context to scope the skill execution" },
    { name: "timeout_ms", type: "number", required: false, desc: "Maximum execution time in milliseconds" },
];

function ParamNameCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 500, color: "#202223" }}>{data.name}</span>
            {data.required
                ? <Badge status="critical">required</Badge>
                : <Badge>optional</Badge>
            }
        </div>
    );
}

function ParamTypeCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <Badge status="info">{data.type}</Badge>
        </div>
    );
}

function ParamDescCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 12, color: "#6D7175", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{data.desc}</span>
        </div>
    );
}

const SKILL_SCHEMA_COL_DEFS = [
    { field: "name", headerName: "Name",        flex: 1,   minWidth: 140, cellRenderer: ParamNameCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "type", headerName: "Type",        width: 100, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ParamTypeCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "desc", headerName: "Description", flex: 2,   minWidth: 160, cellRenderer: ParamDescCell, cellStyle: { display: "flex", alignItems: "center" } },
];

// ─── Skill violation data ─────────────────────────────────────────────────────

const SKILL_VIOLATION_ROWS = [
    { id: 0, severity: "critical", title: "PII Sent to External LLM", desc: "Skill transmitted user PII in the prompt payload to a third-party LLM without redaction or consent.", time: "2m ago" },
    { id: 1, severity: "high",     title: "Excessive Token Consumption", desc: "Skill consumed 52k tokens in a single invocation, exceeding the 10k policy limit.", time: "17m ago" },
    { id: 2, severity: "medium",   title: "Unusual Invocation Rate", desc: "Skill invoked 47 times within 2 minutes, consistent with automated enumeration.", time: "1h ago" },
];

function SevBadgeCell({ data }) {
    if (!data) return null;
    const MAP = {
        critical: { bg: "#DF2909", color: "#FFFBFB" },
        high:     { bg: "#FED3D1", color: "#202223" },
        medium:   { bg: "#FFD79D", color: "#202223" },
        low:      { bg: "#E4E5E7", color: "#202223" },
    };
    const s = MAP[data.severity] || MAP.low;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", padding: "2px 8px", borderRadius: 10, fontSize: 11, fontWeight: 600, background: s.bg, color: s.color, textTransform: "capitalize" }}>{data.severity}</span>
        </div>
    );
}

function ViolTitleCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 13, color: "#202223", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{data.title}</span>
        </div>
    );
}

const SKILL_VIOLATION_COL_DEFS = [
    { field: "severity", headerName: "Severity", width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: SevBadgeCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "title",    headerName: "Violation", flex: 1, minWidth: 200, cellRenderer: ViolTitleCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "time",     headerName: "Time",      width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" } },
];

const COL_DEFS = [
    {
        field: "name",
        headerName: "Skill",
        flex: 1,
        checkboxSelection: true,
        headerCheckboxSelection: true,
        cellRenderer: SkillNameCell,
        cellStyle: { display: "flex", alignItems: "center", overflow: "hidden" },
        filter: true,
    },
    {
        field: "violations",
        headerName: "Violations",
        width: 120,
        suppressHeaderMenuButton: true,
        suppressHeaderFilterButton: true,
        cellRenderer: ViolationCell,
        cellStyle: { display: "flex", alignItems: "center" },
    },
];

const DEFAULT_COL_DEF = {
    sortable: true,
    resizable: true,
    cellStyle: { display: "flex", alignItems: "center" },
};

// ─── Risk badge ───────────────────────────────────────────────────────────────

function RiskBadge({ score }) {
    if (score == null) return null;
    let bg = "#F0FDF4", color = "#16A34A";
    if (score >= 4.5) { bg = "#FEE2E2"; color = "#DC2626"; }
    else if (score >= 4.0) { bg = "#FFEDD5"; color = "#EA580C"; }
    else if (score >= 3.5) { bg = "#FEF9C3"; color = "#CA8A04"; }
    return (
        <span style={{
            display: "inline-flex", alignItems: "center", justifyContent: "center",
            padding: "2px 8px", borderRadius: 12,
            fontSize: 12, fontWeight: 600,
            background: bg, color, flexShrink: 0,
        }}>
            {score.toFixed(1)}
        </span>
    );
}

// ─── Icon buttons ─────────────────────────────────────────────────────────────

const ICON_BTN = {
    width: 30, height: 30, borderRadius: 6,
    border: "1px solid #E1E3E5", background: "white",
    cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center",
    flexShrink: 0,
};

const BREADCRUMB_BTN = {
    background: "none", border: "none", padding: 0,
    cursor: "pointer", fontSize: 12, color: "#6D7175",
    fontFamily: "Inter, sans-serif",
};

// ─── Skill detail view ────────────────────────────────────────────────────────

const DETAIL_TABS = [
    { id: "value",      content: "Value" },
    { id: "schema",     content: "Schema" },
    { id: "traces",     content: "Traces" },
];

function SkillDetailView({ skill, device, agent, skills, onBack, onClose, onSkillChange, onDeviceClick }) {
    const [selectedTab, setSelectedTab] = useState(0);
    const [pickerOpen, setPickerOpen]   = useState(false);
    const [pickerSearch, setPickerSearch] = useState("");

    const tabs = [
        ...DETAIL_TABS,
        { id: "violations", content: `Violations (${skill.violations || 0})` },
    ];

    const otherSkills = useMemo(() => skills.filter(s => s.id !== skill.id), [skills, skill.id]);

    const filteredSkills = useMemo(() =>
        pickerSearch
            ? otherSkills.filter(s => s.name.toLowerCase().includes(pickerSearch.toLowerCase()))
            : otherSkills,
        [otherSkills, pickerSearch]
    );

    const skillActions = useMemo(() =>
        filteredSkills.map(s => ({
            content: s.name,
            onAction: () => { onSkillChange(s); setPickerOpen(false); setPickerSearch(""); setSelectedTab(0); },
        })),
        [filteredSkills, onSkillChange]
    );

    const showChevron = otherSkills.length > 0;
    const showSearch  = otherSkills.length > 5;

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            {/* Breadcrumb header */}
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: () => onDeviceClick ? onDeviceClick(device) : onBack() },
                    { label: agent?.endpoint, onClick: onBack },
                ]}
                onClose={onClose}
            >
                <span style={{ color: "#8C9196" }}>/</span>
                <Popover
                    active={pickerOpen}
                    onClose={() => { setPickerOpen(false); setPickerSearch(""); }}
                    preferredAlignment="left"
                    activator={
                        <button
                            onClick={() => showChevron && setPickerOpen(s => !s)}
                            style={{
                                background: "none", border: "none", padding: 0,
                                cursor: showChevron ? "pointer" : "default",
                                fontSize: 13, fontWeight: 600,
                                color: "#202223", fontFamily: "Inter, sans-serif",
                                display: "flex", alignItems: "center", gap: 2,
                            }}
                        >
                            {skill.name}
                            {showChevron && (
                                <span style={{ display: "flex", alignItems: "center", color: "#6D7175" }}>
                                    <Icon source={ChevronDownMinor} />
                                </span>
                            )}
                        </button>
                    }
                >
                    {showSearch && (
                        <Popover.Pane fixed>
                            <Popover.Section>
                                <TextField
                                    autoFocus
                                    type="search"
                                    placeholder="Search skills…"
                                    value={pickerSearch}
                                    onChange={setPickerSearch}
                                    autoComplete="off"
                                    clearButton
                                    onClearButtonClick={() => setPickerSearch("")}
                                />
                            </Popover.Section>
                        </Popover.Pane>
                    )}
                    <Popover.Pane>
                        <ActionList items={skillActions} />
                    </Popover.Pane>
                </Popover>
            </FlyoutBreadcrumb>

            {/* Tabs bar */}
            <div style={{ borderBottom: "1px solid #E1E3E5", padding: "0 4px", flexShrink: 0 }}>
                <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
            </div>

            {/* Tab content */}
            <div style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: 16 }}>
                {selectedTab === 0 && (
                    <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
                        <LegacyCard>
                            <SampleDataComponent
                                type="request"
                                sampleData={DUMMY_SKILL_SAMPLE}
                                readOnly={true}
                            />
                        </LegacyCard>
                        <LegacyCard>
                            <SampleDataComponent
                                type="response"
                                sampleData={DUMMY_SKILL_SAMPLE}
                                readOnly={true}
                            />
                        </LegacyCard>
                    </div>
                )}
                {selectedTab === 1 && (
                    <div style={{ position: "relative", height: 3 * 44 + 40 }}>
                        <AgGridReact
                            theme={gridTheme}
                            rowData={SKILL_SCHEMA_PARAMS}
                            columnDefs={SKILL_SCHEMA_COL_DEFS}
                            defaultColDef={{ sortable: false, resizable: true }}
                            rowHeight={44}
                            headerHeight={40}
                            suppressCellFocus
                        />
                    </div>
                )}
                {selectedTab === 2 && (
                    <div style={{ color: "#6D7175", fontSize: 13 }}>No traces recorded yet.</div>
                )}
                {selectedTab === 3 && (
                    skill.violations > 0 ? (
                        <div style={{ position: "relative", height: skill.violations * 44 + 40 }}>
                            <AgGridReact
                                theme={gridTheme}
                                rowData={SKILL_VIOLATION_ROWS.slice(0, skill.violations)}
                                columnDefs={SKILL_VIOLATION_COL_DEFS}
                                defaultColDef={{ sortable: false, resizable: true }}
                                rowHeight={44}
                                headerHeight={40}
                                suppressCellFocus
                            />
                        </div>
                    ) : (
                        <div style={{ color: "#6D7175", fontSize: 13 }}>No violations found.</div>
                    )
                )}
            </div>
        </div>
    );
}

// ─── Skills list view ─────────────────────────────────────────────────────────

function SkillsListView({ agent, device, allSkills, onSkillClick, onClose, onDeviceClick }) {
    const [activeTab, setActiveTab]         = useState("all");
    const [showSearch, setShowSearch]       = useState(false);
    const [quickFilter, setQuickFilter]     = useState("");
    const [selectedCount, setSelectedCount] = useState(0);
    const gridRef = useRef(null);

    const blockedSkills = useMemo(() => allSkills.filter(s => s.blocked), [allSkills]);
    const rowData       = activeTab === "all" ? allSkills : blockedSkills;

    const clearSel = useCallback(() => { gridRef.current?.api?.deselectAll(); setSelectedCount(0); }, []);

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            {/* Header */}
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: onDeviceClick ? () => onDeviceClick(device) : undefined },
                    { label: `${agent?.endpoint} Skills` },
                ]}
                onClose={onClose}
            />

            {/* Tabs + actions */}
            <div style={{
                display: "flex", alignItems: "center", justifyContent: "space-between",
                padding: "10px 16px",
                borderBottom: "1px solid #E1E3E5",
                flexShrink: 0,
            }}>
                <div style={{ display: "flex", gap: 4 }}>
                    {[
                        { key: "all",     label: "All",            count: allSkills.length },
                        { key: "blocked", label: "Blocked Skills",  count: blockedSkills.length },
                    ].map(tab => (
                        <button
                            key={tab.key}
                            onClick={() => setActiveTab(tab.key)}
                            style={{
                                display: "flex", alignItems: "center", gap: 6,
                                padding: "5px 12px", borderRadius: 6, border: "none",
                                cursor: "pointer", fontFamily: "Inter, sans-serif",
                                fontSize: 13, fontWeight: activeTab === tab.key ? 600 : 400,
                                color: activeTab === tab.key ? "#202223" : "#6D7175",
                                background: activeTab === tab.key ? "#F1F2F3" : "transparent",
                            }}
                        >
                            {tab.label}
                            <span style={{ fontSize: 12, fontWeight: 600, color: activeTab === tab.key ? "#6D7175" : "#9CA3AF" }}>
                                {tab.count}
                            </span>
                        </button>
                    ))}
                </div>
                <div style={{ display: "flex", gap: 8 }}>
                    <button
                        onClick={() => { setShowSearch(s => !s); if (showSearch) setQuickFilter(""); }}
                        style={{ ...ICON_BTN, border: showSearch ? "1.5px solid #9642FC" : "1px solid #E1E3E5" }}
                    >
                        <svg width="14" height="14" viewBox="0 0 20 20" fill={showSearch ? "#9642FC" : "#6D7175"}>
                            <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd"/>
                        </svg>
                    </button>
                </div>
            </div>

            {/* Search */}
            {showSearch && (
                <div style={{
                    display: "flex", alignItems: "center", gap: 8,
                    padding: "7px 12px",
                    borderBottom: "1px solid #E1E3E5",
                    flexShrink: 0, background: "white",
                }}>
                    <svg width="13" height="13" viewBox="0 0 20 20" fill="#8C9196" style={{ flexShrink: 0 }}>
                        <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd"/>
                    </svg>
                    <input
                        autoFocus
                        type="text"
                        placeholder="Search skills…"
                        value={quickFilter}
                        onChange={e => setQuickFilter(e.target.value)}
                        style={{
                            flex: 1, border: "none", outline: "none",
                            fontSize: 13, color: "#202223",
                            background: "transparent",
                            fontFamily: "Inter, sans-serif",
                        }}
                    />
                    {quickFilter && (
                        <button onClick={() => setQuickFilter("")} style={{ background: "none", border: "none", cursor: "pointer", color: "#8C9196", fontSize: 16, lineHeight: 1, padding: 0 }}>×</button>
                    )}
                </div>
            )}

            {/* Bulk actions */}
            {selectedCount > 0 && (
                <div style={{
                    display: "flex", alignItems: "center", gap: 8,
                    padding: "8px 16px",
                    background: "#F9F9FB",
                    borderBottom: "1px solid #E1E3E5",
                    flexShrink: 0,
                    flexWrap: "wrap",
                }}>
                    <span style={{
                        display: "inline-flex", alignItems: "center", justifyContent: "center",
                        minWidth: 22, height: 22, padding: "0 7px", borderRadius: 11,
                        fontSize: 11, fontWeight: 700,
                        background: "#9642FC", color: "white", flexShrink: 0,
                    }}>{selectedCount}</span>
                    <span style={{ fontSize: 12, color: "#6D7175", marginRight: 4 }}>selected</span>
                    {[
                        "Export as CSV",
                        "Add to Agentic Component group",
                        "De-merge Agentic Components",
                        "Block Skills",
                        "Delete Agentic Components",
                    ].map(label => (
                        <button key={label} style={{
                            padding: "4px 12px", borderRadius: 6,
                            border: "1px solid #D1D5DB", background: "white",
                            fontSize: 12, fontWeight: 500, color: "#202223",
                            cursor: "pointer", whiteSpace: "nowrap",
                            fontFamily: "Inter, sans-serif",
                            boxShadow: "0 1px 2px rgba(0,0,0,0.05)",
                        }}>
                            {label}
                        </button>
                    ))}
                    <button onClick={clearSel} style={{
                        marginLeft: "auto", background: "none", border: "none",
                        cursor: "pointer", color: "#6D7175", fontSize: 18,
                        lineHeight: 1, padding: "0 2px", flexShrink: 0,
                    }}>×</button>
                </div>
            )}

            {/* Grid */}
            <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
                <div style={{ position: "absolute", inset: 0 }}>
                    <AgGridReact
                        ref={gridRef}
                        theme={gridTheme}
                        rowData={rowData}
                        columnDefs={COL_DEFS}
                        defaultColDef={DEFAULT_COL_DEF}
                        rowHeight={44}
                        headerHeight={40}
                        rowSelection="multiple"
                        suppressRowClickSelection
                        suppressCellFocus
                        quickFilterText={quickFilter}
                        onSelectionChanged={e => setSelectedCount(e.api.getSelectedRows().length)}
                        onRowClicked={e => { if (e.data) onSkillClick(e.data); }}
                        pagination
                        paginationPageSize={20}
                        paginationPageSizeSelector={[20, 50, 100]}
                    />
                </div>
            </div>
        </div>
    );
}

// ─── Main export ──────────────────────────────────────────────────────────────

/**
 * SkillsFlyout — reusable flyout for viewing skills of an AI agent.
 *
 * Props:
 *   agent   — { endpoint, skillCount, ... }
 *   device  — { endpoint, riskScore, ... }
 *   show    — boolean
 *   onClose — () => void
 */
export default function SkillsFlyout({ agent, device, show, onClose, onDeviceClick }) {
    const [selectedSkill, setSelectedSkill] = useState(null);

    const allSkills = useMemo(() => generateSkills(agent?.skillCount || 0), [agent?.skillCount]);

    // Reset state when flyout closes or agent changes
    useEffect(() => {
        if (!show) setSelectedSkill(null);
    }, [show]);

    useEffect(() => {
        setSelectedSkill(null);
    }, [agent?.endpoint]);

    const lockScroll   = useCallback(() => { document.body.style.overflow = "hidden"; }, []);
    const unlockScroll = useCallback(() => { document.body.style.overflow = "";       }, []);

    return (
        <div className={"flyLayout " + (show ? "show" : "")} style={{ width: 720 }}>
            <div
                className="innerFlyLayout"
                onMouseEnter={lockScroll}
                onMouseLeave={unlockScroll}
                style={{
                    width: 720,
                    top: "3.5rem",
                    height: "calc(100vh - 3.5rem)",
                    overflowY: "hidden",
                    display: "flex",
                    flexDirection: "column",
                    background: "white",
                    borderLeft: "1px solid #E1E3E5",
                    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
                }}
            >
                {selectedSkill ? (
                    <SkillDetailView
                        key={selectedSkill.id}
                        skill={selectedSkill}
                        device={device}
                        agent={agent}
                        skills={allSkills}
                        onBack={() => setSelectedSkill(null)}
                        onClose={onClose}
                        onSkillChange={setSelectedSkill}
                        onDeviceClick={onDeviceClick}
                    />
                ) : (
                    <SkillsListView
                        agent={agent}
                        device={device}
                        allSkills={allSkills}
                        onSkillClick={setSelectedSkill}
                        onClose={onClose}
                        onDeviceClick={onDeviceClick}
                    />
                )}

                {/* Ask Akto — always pinned at bottom */}
                <div style={{
                    borderTop: "1px solid #E1E3E5",
                    padding: "12px 16px",
                    flexShrink: 0,
                    background: "white",
                }}>
                    <AgenticSearchInput
                        placeholder="Ask anything related to your endpoints..."
                        isFixed={false}
                        inputWidth="100%"
                        containerStyle={{ display: "block" }}
                    />
                </div>
            </div>
        </div>
    );
}
