import React, { useState, useMemo, useEffect, useRef, useCallback } from "react";
import { Tabs, Button, Popover, ActionList, LegacyCard, Icon, TextField, Badge, Box, HorizontalStack, VerticalStack, Text, Divider } from "@shopify/polaris";
import { ChevronDownMinor } from "@shopify/polaris-icons";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import AiChatSection from "./AiChatSection";
import SampleDataComponent from "../../../components/shared/SampleDataComponent";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import { ParamNameCellRenderer, ParamTypeCellRenderer, ParamDescCellRenderer } from "./agenticCellRenderers";
import { generateSkills, DUMMY_SKILL_SAMPLE, SKILL_SCHEMA_PARAMS, SKILL_VIOLATION_ROWS } from "./agenticDummyData";
import "../../../components/layouts/style.css";

// ─── Cell renderers ───────────────────────────────────────────────────────────
// Exception: AG Grid cell renderers use inline styles (Polaris tokens don't reach into the grid sandbox)

function SkillNameCellRenderer({ data }) {
    return (
        <div style={{ display: "flex", alignItems: "center", gap: 6, width: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 13, color: "#202223", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {data.name}
            </span>
            {data.isNew && (
                <span style={{
                    flexShrink: 0, fontSize: 11, fontWeight: 500,
                    padding: "2px 8px", borderRadius: 12,
                    background: "#F1F2F3", color: "#6D7175",
                    border: "1px solid #E1E3E5", lineHeight: "16px",
                    display: "inline-flex", alignItems: "center",
                }}>New</span>
            )}
        </div>
    );
}

function ViolationCellRenderer({ data }) {
    if (!data.violations) return <span style={{ color: "#8C9196", fontSize: 13 }}>–</span>;
    return (
        // SEVERITY_COLORS.critical bg — no matching Polaris Badge status
        <span style={{
            display: "inline-flex", alignItems: "center", justifyContent: "center",
            minWidth: 22, height: 22, padding: "0 5px", borderRadius: 11,
            fontSize: 11, fontWeight: 700, background: "#DF2909", color: "#FFFBFB",
        }}>
            {data.violations}
        </span>
    );
}

function SevBadgeCellRenderer({ data }) {
    if (!data) return null;
    const STATUS_MAP = { critical: "critical", high: "warning", medium: "attention", low: undefined };
    const label = data.severity.charAt(0).toUpperCase() + data.severity.slice(1);
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <Badge status={STATUS_MAP[data.severity]}>{label}</Badge>
        </div>
    );
}

function ViolTitleCellRenderer({ data }) {
    if (!data) return null;
    return (
        <div style={{ display: "flex", alignItems: "center", height: "100%", overflow: "hidden" }}>
            <span style={{ fontSize: 13, color: "#202223", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{data.title}</span>
        </div>
    );
}

// ─── Column definitions ───────────────────────────────────────────────────────

const SKILL_SCHEMA_COL_DEFS = [
    { field: "name", headerName: "Name",        flex: 1,    minWidth: 140, cellRenderer: ParamNameCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "type", headerName: "Type",        width: 100, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ParamTypeCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "desc", headerName: "Description", flex: 2,    minWidth: 160, cellRenderer: ParamDescCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const SKILL_VIOLATION_COL_DEFS = [
    { field: "severity", headerName: "Severity", width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: SevBadgeCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "title",    headerName: "Violation", flex: 1, minWidth: 200, cellRenderer: ViolTitleCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "time",     headerName: "Time",      width: 110, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellStyle: { display: "flex", alignItems: "center", fontSize: 12, color: "#6D7175" } },
];

const COL_DEFS = [
    {
        field: "name", headerName: "Skill", flex: 1,
        checkboxSelection: true, headerCheckboxSelection: true,
        cellRenderer: SkillNameCellRenderer,
        cellStyle: { display: "flex", alignItems: "center", overflow: "hidden" },
        filter: "agTextColumnFilter",
    },
    {
        field: "violations", headerName: "Violations", width: 120,
        suppressHeaderMenuButton: true, suppressHeaderFilterButton: true,
        cellRenderer: ViolationCellRenderer,
        cellStyle: { display: "flex", alignItems: "center" },
    },
];

const DEFAULT_COL_DEF = { sortable: true, resizable: true, cellStyle: { display: "flex", alignItems: "center" } };

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

    const otherSkills    = useMemo(() => skills.filter(s => s.id !== skill.id), [skills, skill.id]);
    const filteredSkills = useMemo(() =>
        pickerSearch
            ? otherSkills.filter(s => s.name.toLowerCase().includes(pickerSearch.toLowerCase()))
            : otherSkills,
        [otherSkills, pickerSearch]
    );
    const skillActions   = useMemo(() =>
        filteredSkills.map(s => ({
            content: s.name,
            onAction: () => { onSkillChange(s); setPickerOpen(false); setPickerSearch(""); setSelectedTab(0); },
        })),
        [filteredSkills, onSkillChange]
    );

    const showChevron = otherSkills.length > 0;
    const showSearch  = otherSkills.length > 5;

    const pickerActivator = showChevron ? (
        <Button plain disclosure onClick={() => setPickerOpen(s => !s)}>
            {skill.name}
        </Button>
    ) : (
        <Text variant="bodySm" fontWeight="semibold">{skill.name}</Text>
    );

    return (
        // flex:1, minHeight:0 needed for flyout layout — Box props insufficient
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: () => onDeviceClick ? onDeviceClick(device) : onBack() },
                    { label: agent?.endpoint, onClick: onBack },
                ]}
                onClose={onClose}
            >
                <Text variant="bodySm" color="subdued">/</Text>
                <Popover
                    active={pickerOpen}
                    onClose={() => { setPickerOpen(false); setPickerSearch(""); }}
                    preferredAlignment="left"
                    activator={pickerActivator}
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

            <Box paddingInlineStart="1" paddingInlineEnd="1">
                <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
            </Box>
            <Divider />

            {/* flex:1, minHeight:0 needed to fill remaining flyout space — Box props insufficient */}
            <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                {selectedTab === 0 && (
                    // overflowY:auto on flex child requires flex:1 — Box doesn't support flex child props
                    <div style={{ flex: 1, overflowY: "auto" }}>
                        <Box padding="4">
                            <VerticalStack gap="4">
                                <LegacyCard>
                                    <SampleDataComponent type="request" sampleData={DUMMY_SKILL_SAMPLE} readOnly={true} />
                                </LegacyCard>
                                <LegacyCard>
                                    <SampleDataComponent type="response" sampleData={DUMMY_SKILL_SAMPLE} readOnly={true} />
                                </LegacyCard>
                            </VerticalStack>
                        </Box>
                    </div>
                )}
                {selectedTab === 1 && (
                    <AgGridTable
                        rowData={SKILL_SCHEMA_PARAMS}
                        columnDefs={SKILL_SCHEMA_COL_DEFS}
                        defaultColDef={{ sortable: false, resizable: true }}
                        fillHeight
                        noOuterBorder
                        pagination={false}
                        sideBar={false}
                    />
                )}
                {selectedTab === 2 && (
                    <Box padding="4">
                        <Text variant="bodySm" color="subdued">No traces recorded yet.</Text>
                    </Box>
                )}
                {selectedTab === 3 && (
                    skill.violations > 0 ? (
                        <AgGridTable
                            rowData={SKILL_VIOLATION_ROWS.slice(0, skill.violations)}
                            columnDefs={SKILL_VIOLATION_COL_DEFS}
                            defaultColDef={{ sortable: false, resizable: true }}
                            fillHeight
                            noOuterBorder
                            pagination={false}
                            sideBar={false}
                        />
                    ) : (
                        <Box padding="4">
                            <Text variant="bodySm" color="subdued">No violations found.</Text>
                        </Box>
                    )
                )}
            </div>
        </div>
    );
}

// ─── Skills list view ─────────────────────────────────────────────────────────

function SkillsListView({ agent, device, allSkills, onSkillClick, onClose, onDeviceClick }) {
    const [activeTabIndex, setActiveTabIndex] = useState(0);
    const [selectedCount, setSelectedCount]   = useState(0);
    const gridRef = useRef(null);

    const blockedSkills = useMemo(() => allSkills.filter(s => s.blocked), [allSkills]);
    const rowData       = activeTabIndex === 0 ? allSkills : blockedSkills;

    const clearSel = useCallback(() => { gridRef.current?.api?.deselectAll(); setSelectedCount(0); }, []);

    const skillTabs = useMemo(() => [
        { id: "all",     content: `All (${allSkills.length})` },
        { id: "blocked", content: `Blocked Skills (${blockedSkills.length})` },
    ], [allSkills.length, blockedSkills.length]);

    const bulkActions = useMemo(() => [
        { label: "Export as CSV",                  onAction: () => {} },
        { label: "Add to Agentic Component group", onAction: () => {} },
        { label: "De-merge Agentic Components",    onAction: () => {} },
        { label: "Block Skills",                   onAction: () => {} },
        { label: "Delete Agentic Components",      onAction: () => {} },
    ], []);

    return (
        // flex:1, minHeight:0 needed for flyout layout — Box props insufficient
        <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: onDeviceClick ? () => onDeviceClick(device) : undefined },
                    { label: `${agent?.endpoint} Skills` },
                ]}
                onClose={onClose}
            />

            <Box paddingInlineStart="1" paddingInlineEnd="1">
                <Tabs tabs={skillTabs} selected={activeTabIndex} onSelect={setActiveTabIndex} />
            </Box>
            <Divider />

            <AgGridTable
                gridRef={gridRef}
                rowData={rowData}
                columnDefs={COL_DEFS}
                defaultColDef={DEFAULT_COL_DEF}
                bulkActionCount={selectedCount}
                bulkActions={bulkActions}
                onClearBulk={clearSel}
                onSelectionChanged={e => setSelectedCount(e.api.getSelectedRows().length)}
                onRowClicked={e => { if (e.data) onSkillClick(e.data); }}
                fillHeight
                noOuterBorder
                searchPlaceholder="Search skills..."
                sideBar={false}
            />
        </div>
    );
}

// ─── Main export ──────────────────────────────────────────────────────────────

export default function SkillsFlyout({ agent, device, show, onClose, onDeviceClick }) {
    const [selectedSkill, setSelectedSkill] = useState(null);

    const allSkills = useMemo(() => generateSkills(agent?.skillCount || 0), [agent?.skillCount]);

    useEffect(() => { if (!show) setSelectedSkill(null); }, [show]);
    useEffect(() => { setSelectedSkill(null); }, [agent?.endpoint]);

    const lockScroll   = useCallback(() => { document.body.style.overflow = "hidden"; }, []);
    const unlockScroll = useCallback(() => { document.body.style.overflow = "";       }, []);

    useEffect(() => { if (!show) document.body.style.overflow = ""; }, [show]);

    return (
        <div className={"flyLayout " + (show ? "show" : "")} style={{ width: 720 }}>
            {/* onMouseEnter/onMouseLeave not available on Box; flyout positioning requires CSS not supported by Box */}
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
                {/* flex:1, minHeight:0 needed for flyout content area — Box props insufficient */}
                <div style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
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
                </div>

                <AiChatSection
                    placeholder="Ask anything related to your skills..."
                    resetKey={agent?.endpoint}
                />
            </div>
        </div>
    );
}
