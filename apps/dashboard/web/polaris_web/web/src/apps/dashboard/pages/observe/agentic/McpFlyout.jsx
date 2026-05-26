import React, { useState, useMemo, useCallback, useRef } from "react";
import { AgGridReact } from "ag-grid-react";
import { themeQuartz } from "ag-grid-enterprise";
import { Tabs, Popover, ActionList, LegacyCard, Link, Icon, TextField, Badge } from "@shopify/polaris";
import { ChevronDownMinor } from "@shopify/polaris-icons";
import AiChatSection from "./AiChatSection";
import SampleDataComponent from "../../../components/shared/SampleDataComponent";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import { MCP_TOOLS, MCP_RESOURCES, MCP_PROMPTS, TOOL_VIOLATIONS, generateResourceSample, generatePromptSample, generateToolSample } from "./agenticDummyData";
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

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getRiskColors(score) {
    if (score >= 4.5) return { bg: "#FEE2E2", color: "#DC2626" };
    if (score >= 4.0) return { bg: "#FFEDD5", color: "#EA580C" };
    if (score >= 3.5) return { bg: "#FEF9C3", color: "#CA8A04" };
    return { bg: "#F0FDF4", color: "#16A34A" };
}

function RiskBadge({ score }) {
    if (score == null) return null;
    const { bg, color } = getRiskColors(score);
    return (
        <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", padding: "2px 8px", borderRadius: 12, fontSize: 12, fontWeight: 600, background: bg, color, flexShrink: 0 }}>
            {score.toFixed(1)}
        </span>
    );
}

const RISK_LEVEL_STYLES = {
    critical: { bg: "#DF2909", color: "#FFFBFB" },
    high:     { bg: "#FED3D1", color: "#202223" },
    medium:   { bg: "#FFD79D", color: "#202223" },
    low:      { bg: "#E4E5E7", color: "#202223" },
};

// ─── Server lookup helpers ────────────────────────────────────────────────────

function getToolsForServer(endpoint) {
    if (!endpoint) return [];
    const normalised = endpoint.toLowerCase();
    const key = Object.keys(MCP_TOOLS).find(k => normalised.includes(k.replace("-mcp","").replace("-stdio","")) || normalised === k);
    return key ? MCP_TOOLS[key] : [];
}

function getResourcesForServer(endpoint) {
    if (!endpoint) return [];
    const n = endpoint.toLowerCase();
    const key = Object.keys(MCP_RESOURCES).find(k => n.includes(k.replace("-mcp","").replace("-stdio","")) || n === k);
    return key ? MCP_RESOURCES[key] : [];
}

function getPromptsForServer(endpoint) {
    if (!endpoint) return [];
    const n = endpoint.toLowerCase();
    const key = Object.keys(MCP_PROMPTS).find(k => n.includes(k.replace("-mcp","").replace("-stdio","")) || n === k);
    return key ? MCP_PROMPTS[key] : [];
}

// ─── Cell renderers ───────────────────────────────────────────────────────────

function ToolNameCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 8, width: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 13, color: "#202223", fontWeight: 600, fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {data.name}
            </span>
        </div>
    );
}

function ToolViolationsCell({ data }) {
    if (!data) return null;
    const count = TOOL_VIOLATIONS[data.name] || 0;
    if (!count) return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ color: "#C4C7CB" }}>—</span></div>;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <span style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", minWidth: 22, height: 20, padding: "0 6px", borderRadius: 10, fontSize: 11, fontWeight: 700, background: "#DF2909", color: "#FFFBFB" }}>
                {count}
            </span>
        </div>
    );
}

function ToolParamsCell({ data }) {
    if (!data) return null;
    const count = data.params?.length || 0;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 12, color: "#6D7175" }}>{count}</span></div>;
}

const TOOLS_COL_DEFS = [
    { field: "name",       headerName: "Tool",       flex: 1,   minWidth: 160, cellRenderer: ToolNameCell,       cellStyle: { display: "flex", alignItems: "center" } },
    { field: "violations", headerName: "Violations", width: 110, sort: "desc", suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ToolViolationsCell, cellStyle: { display: "flex", alignItems: "center" }, valueGetter: p => TOOL_VIOLATIONS[p.data?.name] || 0 },
    { field: "params",     headerName: "Params",     width: 80,  suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ToolParamsCell,     cellStyle: { display: "flex", alignItems: "center" }, valueGetter: p => p.data?.params?.length ?? 0 },
];

// ─── Resources cell renderers ─────────────────────────────────────────────────

function ResourceNameCell({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 13, fontWeight: 600, color: "#202223" }}>{data.name}</span></div>;
}

function ResourceUriCell({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 12, color: "#8C9196", fontFamily: "ui-monospace, 'Cascadia Mono', Consolas, monospace" }}>{data.uri}</span></div>;
}

const RESOURCES_COL_DEFS = [
    { field: "name", headerName: "Name", flex: 1,   minWidth: 120, cellRenderer: ResourceNameCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "uri",  headerName: "URI",  flex: 1,   minWidth: 160, cellRenderer: ResourceUriCell,  cellStyle: { display: "flex", alignItems: "center" } },
];

// ─── Prompts cell renderers ───────────────────────────────────────────────────

function PromptNameCell({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 13, fontWeight: 600, color: "#202223" }}>{data.name}</span></div>;
}

function PromptDescCell({ data }) {
    if (!data) return null;
    return <div style={{ display: "flex", alignItems: "center", height: "100%" }}><span style={{ fontSize: 12, color: "#6D7175" }}>{data.description}</span></div>;
}

const PROMPTS_COL_DEFS = [
    { field: "name",        headerName: "Session Title", flex: 1,   minWidth: 140, cellRenderer: PromptNameCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "description", headerName: "Prompt",   flex: 2,   minWidth: 200, cellRenderer: PromptDescCell, cellStyle: { display: "flex", alignItems: "center" } },
];

// ─── Schema param cell renderers (shared with SkillsFlyout pattern) ──────────

function ParamNameCell({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 500, color: "#202223" }}>{data.name}</span>
            {data.required ? <Badge status="critical">required</Badge> : <Badge>optional</Badge>}
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

const SCHEMA_COL_DEFS = [
    { field: "name", headerName: "Name",        flex: 1,   minWidth: 140, cellRenderer: ParamNameCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "type", headerName: "Type",        width: 100, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ParamTypeCell, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "desc", headerName: "Description", flex: 2,   minWidth: 160, cellRenderer: ParamDescCell, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// ─── Tool detail view (mirrors SkillDetailView) ───────────────────────────────

const TOOL_TABS = [
    { id: "value",  content: "Value" },
    { id: "schema", content: "Schema" },
    { id: "traces", content: "Traces" },
];

function ToolDetailView({ tool, device, agent, allTools, onBack, onClose, onToolChange, onDeviceClick }) {
    const [selectedTab, setSelectedTab] = useState(0);
    const [pickerOpen, setPickerOpen]   = useState(false);
    const [pickerSearch, setPickerSearch] = useState("");

    const sampleData = useMemo(() => generateToolSample(tool), [tool.id]);

    const tabs = [
        ...TOOL_TABS,
        { id: "violations", content: `Violations (0)` },
    ];

    const otherTools = useMemo(() => allTools.filter(t => t.id !== tool.id), [allTools, tool.id]);

    const filteredTools = useMemo(() =>
        pickerSearch
            ? otherTools.filter(t => t.name.toLowerCase().includes(pickerSearch.toLowerCase()))
            : otherTools,
        [otherTools, pickerSearch]
    );

    const toolActions = useMemo(() =>
        filteredTools.map(t => ({
            content: t.name,
            onAction: () => { onToolChange(t); setPickerOpen(false); setPickerSearch(""); setSelectedTab(0); },
        })),
        [filteredTools, onToolChange]
    );

    const showChevron = otherTools.length > 0;
    const showSearch  = otherTools.length > 5;

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
                                fontSize: 13, fontWeight: 600, color: "#202223",
                                fontFamily: "Inter, sans-serif",
                                display: "flex", alignItems: "center", gap: 2,
                            }}
                        >
                            {tool.name}
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
                                    placeholder="Search tools…"
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
                        <ActionList items={toolActions} />
                    </Popover.Pane>
                </Popover>
            </FlyoutBreadcrumb>

            {/* Tabs bar */}
            <div style={{ borderBottom: "1px solid #E1E3E5", padding: "0 4px", flexShrink: 0 }}>
                <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
            </div>

            {/* Tab content */}
            <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                {selectedTab === 0 && (
                    <div style={{ flex: 1, overflowY: "auto", padding: 16, display: "flex", flexDirection: "column", gap: 16 }}>
                        <LegacyCard>
                            <SampleDataComponent type="request" sampleData={sampleData} readOnly={true} />
                        </LegacyCard>
                        <LegacyCard>
                            <SampleDataComponent type="response" sampleData={sampleData} readOnly={true} />
                        </LegacyCard>
                    </div>
                )}
                {selectedTab === 1 && (
                    tool.params && tool.params.length > 0 ? (
                        <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
                            <div style={{ position: "absolute", inset: 0 }}>
                                <AgGridReact
                                    theme={gridTheme}
                                    rowData={tool.params}
                                    columnDefs={SCHEMA_COL_DEFS}
                                    defaultColDef={{ sortable: false, resizable: true }}
                                    rowHeight={44}
                                    headerHeight={40}
                                    suppressCellFocus
                                />
                            </div>
                        </div>
                    ) : (
                        <div style={{ padding: 16, color: "#8C9196", fontSize: 13 }}>No parameters.</div>
                    )
                )}
                {selectedTab === 2 && <div style={{ padding: 16, color: "#8C9196", fontSize: 13 }}>No traces recorded yet.</div>}
                {selectedTab === 3 && <div style={{ padding: 16, color: "#8C9196", fontSize: 13 }}>No violations found.</div>}
            </div>
        </div>
    );
}

// ─── Resource & Prompt detail views ──────────────────────────────────────────

function ResourceDetailView({ resource, agent, device, onBack, onClose, onDeviceClick }) {
    const sampleData = useMemo(() => generateResourceSample(resource), [resource.id]);

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: () => onDeviceClick ? onDeviceClick(device) : onBack() },
                    { label: agent?.endpoint, onClick: onBack },
                    { label: resource.name },
                ]}
                onClose={onClose}
            />
            <div style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: 16, display: "flex", flexDirection: "column", gap: 16 }}>
                <LegacyCard>
                    <SampleDataComponent type="request" sampleData={sampleData} readOnly={true} />
                </LegacyCard>
                <LegacyCard>
                    <SampleDataComponent type="response" sampleData={sampleData} readOnly={true} />
                </LegacyCard>
            </div>
        </div>
    );
}

function PromptDetailView({ prompt, agent, device, onBack, onClose, onDeviceClick }) {
    const sampleData = useMemo(() => generatePromptSample(prompt), [prompt.id]);

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: () => onDeviceClick ? onDeviceClick(device) : onBack() },
                    { label: agent?.endpoint, onClick: onBack },
                    { label: prompt.name },
                ]}
                onClose={onClose}
            />
            <div style={{ flex: 1, minHeight: 0, overflowY: "auto", padding: 16, display: "flex", flexDirection: "column", gap: 16 }}>
                <LegacyCard>
                    <SampleDataComponent type="request" sampleData={sampleData} readOnly={true} />
                </LegacyCard>
                <LegacyCard>
                    <SampleDataComponent type="response" sampleData={sampleData} readOnly={true} />
                </LegacyCard>
            </div>
        </div>
    );
}

// ─── Resources & Prompts views ────────────────────────────────────────────────

function ResourcesView({ resources, onResourceClick }) {
    if (resources.length === 0) {
        return (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, paddingTop: 48 }}>
                <span style={{ fontSize: 14, fontWeight: 600, color: "#202223" }}>No resources exposed</span>
                <span style={{ fontSize: 12, color: "#6D7175" }}>This MCP server does not expose any resources.</span>
            </div>
        );
    }
    return (
        <div style={{ position: "absolute", inset: 0 }}>
            <AgGridReact
                theme={gridTheme}
                rowData={resources}
                columnDefs={RESOURCES_COL_DEFS}
                defaultColDef={GRID_DEFAULT_COL}
                rowHeight={44}
                headerHeight={40}
                suppressCellFocus
                onRowClicked={e => { if (e.data) onResourceClick(e.data); }}
            />
        </div>
    );
}

function PromptsView({ prompts, onPromptClick }) {
    if (prompts.length === 0) {
        return (
            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, paddingTop: 48 }}>
                <span style={{ fontSize: 14, fontWeight: 600, color: "#202223" }}>No prompts defined</span>
                <span style={{ fontSize: 12, color: "#6D7175" }}>This MCP server does not expose any prompt templates.</span>
            </div>
        );
    }
    return (
        <div style={{ position: "absolute", inset: 0 }}>
            <AgGridReact
                theme={gridTheme}
                rowData={prompts}
                columnDefs={PROMPTS_COL_DEFS}
                defaultColDef={GRID_DEFAULT_COL}
                rowHeight={44}
                headerHeight={40}
                suppressCellFocus
                onRowClicked={e => { if (e.data) onPromptClick(e.data); }}
            />
        </div>
    );
}

// ─── Tools list view ──────────────────────────────────────────────────────────

function ToolsListView({ agent, device, tools, resources, prompts, onToolClick, onClose, onDeviceClick, onResourceClick, onPromptClick }) {
    const [selectedTab, setSelectedTab] = useState(0);
    const [quickFilter, setQuickFilter] = useState("");
    const gridRef = useRef(null);

    const tabs = useMemo(() => [
        { id: "tools",     content: `Tools (${tools.length})` },
        { id: "resources", content: `Resources (${resources.length})` },
        { id: "prompts",   content: `Prompts (${prompts.length})` },
    ], [tools.length, resources.length, prompts.length]);

    return (
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            {/* Header / breadcrumb */}
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: onDeviceClick ? () => onDeviceClick(device) : undefined },
                    { label: agent?.endpoint },
                ]}
                onClose={onClose}
            />

            {/* Tabs */}
            <div style={{ borderBottom: "1px solid #E1E3E5", padding: "0 4px", flexShrink: 0 }}>
                <Tabs tabs={tabs} selected={selectedTab} onSelect={i => { setSelectedTab(i); setQuickFilter(""); }} />
            </div>

            {/* Search toolbar — always visible for Tools tab */}
            {selectedTab === 0 && (
                <div style={{ display: "flex", alignItems: "center", padding: "6px 12px", borderBottom: "1px solid #E1E3E5", flexShrink: 0, gap: 8 }}>
                    <svg width="13" height="13" viewBox="0 0 20 20" fill="#8C9196" style={{ flexShrink: 0 }}>
                        <path fillRule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clipRule="evenodd"/>
                    </svg>
                    <input
                        type="text" placeholder="Search tools…" value={quickFilter}
                        onChange={e => setQuickFilter(e.target.value)}
                        style={{ flex: 1, border: "none", outline: "none", fontSize: 13, color: "#202223", background: "transparent", fontFamily: "Inter, sans-serif" }}
                    />
                    {quickFilter && <button onClick={() => setQuickFilter("")} style={{ background: "none", border: "none", cursor: "pointer", color: "#8C9196", fontSize: 16, lineHeight: 1, padding: 0 }}>×</button>}
                </div>
            )}

            {/* Tools tab */}
            {selectedTab === 0 && (
                <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
                    {tools.length === 0 ? (
                        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 8, paddingTop: 48 }}>
                            <span style={{ fontSize: 14, fontWeight: 600, color: "#202223" }}>No tools captured</span>
                            <span style={{ fontSize: 12, color: "#6D7175" }}>Tool schema for <strong>{agent?.endpoint}</strong> has not been captured yet.</span>
                        </div>
                    ) : (
                        <div style={{ position: "absolute", inset: 0 }}>
                            <AgGridReact
                                ref={gridRef}
                                theme={gridTheme}
                                rowData={tools}
                                columnDefs={TOOLS_COL_DEFS}
                                defaultColDef={GRID_DEFAULT_COL}
                                rowHeight={44}
                                headerHeight={40}
                                suppressCellFocus
                                quickFilterText={quickFilter}
                                onRowClicked={e => { if (e.data) onToolClick(e.data); }}
                                pagination
                                paginationPageSize={20}
                                paginationPageSizeSelector={[20, 50, 100]}
                            />
                        </div>
                    )}
                </div>
            )}

            {/* Resources tab */}
            {selectedTab === 1 && (
                <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
                    <ResourcesView resources={resources} onResourceClick={onResourceClick} />
                </div>
            )}

            {/* Prompts tab */}
            {selectedTab === 2 && (
                <div style={{ flex: 1, minHeight: 0, position: "relative" }}>
                    <PromptsView prompts={prompts} onPromptClick={onPromptClick} />
                </div>
            )}
        </div>
    );
}

// ─── Main McpFlyout ───────────────────────────────────────────────────────────

export default function McpFlyout({ agent, device, show, onClose, onDeviceClick }) {
    const [selectedTool,     setSelectedTool]     = useState(null);
    const [selectedResource, setSelectedResource] = useState(null);
    const [selectedPrompt,   setSelectedPrompt]   = useState(null);

    const allTools     = useMemo(() => getToolsForServer(agent?.endpoint),     [agent?.endpoint]);
    const allResources = useMemo(() => getResourcesForServer(agent?.endpoint), [agent?.endpoint]);
    const allPrompts   = useMemo(() => getPromptsForServer(agent?.endpoint),   [agent?.endpoint]);

    // Reset when flyout closes or agent changes
    React.useEffect(() => { if (!show) { setSelectedTool(null); setSelectedResource(null); setSelectedPrompt(null); } }, [show]);
    React.useEffect(() => { setSelectedTool(null); setSelectedResource(null); setSelectedPrompt(null); }, [agent?.endpoint]);

    const lockScroll   = useCallback(() => { document.body.style.overflow = "hidden"; }, []);
    const unlockScroll = useCallback(() => { document.body.style.overflow = "";       }, []);

    React.useEffect(() => { if (!show) document.body.style.overflow = ""; }, [show]);

    if (!agent) return null;

    return (
        <div className={"flyLayout " + (show ? "show" : "")} style={{ width: 720 }}>
            <div
                className="innerFlyLayout"
                onMouseEnter={lockScroll}
                onMouseLeave={unlockScroll}
                style={{
                    width: 720, top: "3.5rem",
                    height: "calc(100vh - 3.5rem)",
                    overflowY: "hidden",
                    display: "flex", flexDirection: "column",
                    background: "white",
                    borderLeft: "1px solid #E1E3E5",
                    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
                }}
            >
                {selectedTool ? (
                    <ToolDetailView
                        key={selectedTool.id}
                        tool={selectedTool}
                        device={device}
                        agent={agent}
                        allTools={allTools}
                        onBack={() => setSelectedTool(null)}
                        onClose={onClose}
                        onToolChange={setSelectedTool}
                        onDeviceClick={onDeviceClick}
                    />
                ) : selectedResource ? (
                    <ResourceDetailView
                        resource={selectedResource}
                        agent={agent}
                        device={device}
                        onBack={() => setSelectedResource(null)}
                        onClose={onClose}
                        onDeviceClick={onDeviceClick}
                    />
                ) : selectedPrompt ? (
                    <PromptDetailView
                        prompt={selectedPrompt}
                        agent={agent}
                        device={device}
                        onBack={() => setSelectedPrompt(null)}
                        onClose={onClose}
                        onDeviceClick={onDeviceClick}
                    />
                ) : (
                    <ToolsListView
                        agent={agent}
                        device={device}
                        tools={allTools}
                        resources={allResources}
                        prompts={allPrompts}
                        onToolClick={setSelectedTool}
                        onClose={onClose}
                        onDeviceClick={onDeviceClick}
                        onResourceClick={setSelectedResource}
                        onPromptClick={setSelectedPrompt}
                    />
                )}

                {/* Ask Akto — expands to half-screen as user types */}
                <AiChatSection
                    placeholder="Ask about this MCP server's tools and risks..."
                    resetKey={agent?.endpoint}
                />
            </div>
        </div>
    );
}
