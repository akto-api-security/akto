import React, { useState, useMemo, useCallback, useEffect } from "react";
import { Tabs, Button, Popover, ActionList, LegacyCard, Icon, TextField, Badge, Box, HorizontalStack, VerticalStack, Text, Divider } from "@shopify/polaris";
import { ChevronDownMinor } from "@shopify/polaris-icons";
import AgGridTable from "@/apps/dashboard/components/tables/AgGridTable";
import AiChatSection from "./AiChatSection";
import SampleDataComponent from "../../../components/shared/SampleDataComponent";
import FlyoutBreadcrumb from "./FlyoutBreadcrumb";
import { ParamNameCellRenderer, ParamTypeCellRenderer, ParamDescCellRenderer } from "./agenticCellRenderers";
import { generateResourceSample, generatePromptSample, generateToolSample } from "./agenticSampleHelpers";
import agenticObserveApi, { buildAgenticObserveChatMetadata } from "./agenticObserveApi";
import observeApi from "../api";
import "../../../components/layouts/style.css";

// ─── Cell renderers ───────────────────────────────────────────────────────────
// Exception: AG Grid cell renderers use inline styles (Polaris tokens don't reach into the grid sandbox)

function ToolNameCellRenderer({ data }) {
    if (!data) return null;
    return (
        <Box style={{ display: "flex", alignItems: "center", gap: 8, width: "100%", overflow: "hidden" }}>
            <Box as="span" style={{ fontSize: 13, color: "#202223", fontWeight: 600, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                {data.name}
            </Box>
        </Box>
    );
}

function ToolViolationsCellRenderer({ data }) {
    if (!data) return null;
    const count = data.violationCount || 0;
    if (!count) return <Box style={{ display: "flex", alignItems: "center", height: "100%" }}><Box as="span" style={{ color: "#C4C7CB" }}>-</Box></Box>;
    return (
        <Box style={{ display: "flex", alignItems: "center", height: "100%" }}>
            <Box as="span" style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", minWidth: 22, height: 20, padding: "0 6px", borderRadius: 10, fontSize: 11, fontWeight: 700, background: "#DF2909", color: "#FFFBFB" }}>
                {count}
            </Box>
        </Box>
    );
}

function ToolParamsCellRenderer({ data }) {
    if (!data) return null;
    return <Box style={{ display: "flex", alignItems: "center", height: "100%" }}><Box as="span" style={{ fontSize: 12, color: "#6D7175" }}>{data.params?.length || 0}</Box></Box>;
}

function ResourceNameCellRenderer({ data }) {
    if (!data) return null;
    return <Box style={{ display: "flex", alignItems: "center", height: "100%" }}><Box as="span" style={{ fontSize: 13, fontWeight: 600, color: "#202223" }}>{data.name}</Box></Box>;
}

function ResourceUriCellRenderer({ data }) {
    if (!data) return null;
    return <Box style={{ display: "flex", alignItems: "center", height: "100%" }}><Box as="span" style={{ fontSize: 12, color: "#8C9196", fontFamily: "ui-monospace, 'Cascadia Mono', Consolas, monospace" }}>{data.uri}</Box></Box>;
}

function PromptNameCellRenderer({ data }) {
    if (!data) return null;
    return <Box style={{ display: "flex", alignItems: "center", height: "100%" }}><Box as="span" style={{ fontSize: 13, fontWeight: 600, color: "#202223" }}>{data.name}</Box></Box>;
}

function PromptDescCellRenderer({ data }) {
    if (!data) return null;
    return <Box style={{ display: "flex", alignItems: "center", height: "100%" }}><Box as="span" style={{ fontSize: 12, color: "#6D7175" }}>{data.description}</Box></Box>;
}

// ─── Column definitions ───────────────────────────────────────────────────────

const TOOLS_COL_DEFS = [
    { field: "name",       headerName: "Tool",       flex: 1,    minWidth: 160, filter: "agTextColumnFilter", cellRenderer: ToolNameCellRenderer,       cellStyle: { display: "flex", alignItems: "center" } },
    { field: "violations", headerName: "Violations", width: 110, sort: "desc", suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ToolViolationsCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, valueGetter: p => p.data?.violationCount || 0 },
    { field: "params",     headerName: "Params",     width: 80,  suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ToolParamsCellRenderer, cellStyle: { display: "flex", alignItems: "center" }, valueGetter: p => p.data?.params?.length ?? 0 },
];

const RESOURCES_COL_DEFS = [
    { field: "name", headerName: "Name", flex: 1, minWidth: 120, cellRenderer: ResourceNameCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "uri",  headerName: "URI",  flex: 1, minWidth: 160, cellRenderer: ResourceUriCellRenderer,  cellStyle: { display: "flex", alignItems: "center" } },
];

const PROMPTS_COL_DEFS = [
    { field: "name",        headerName: "Session Title", flex: 1, minWidth: 140, cellRenderer: PromptNameCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "description", headerName: "Prompt",        flex: 2, minWidth: 200, cellRenderer: PromptDescCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const SCHEMA_COL_DEFS = [
    { field: "name", headerName: "Name",        flex: 1,    minWidth: 140, cellRenderer: ParamNameCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "type", headerName: "Type",        width: 100, suppressHeaderMenuButton: true, suppressHeaderFilterButton: true, cellRenderer: ParamTypeCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
    { field: "desc", headerName: "Description", flex: 2,    minWidth: 160, cellRenderer: ParamDescCellRenderer, cellStyle: { display: "flex", alignItems: "center" } },
];

const GRID_DEFAULT_COL = { sortable: true, resizable: true, filter: false };

// ─── Tool detail view ─────────────────────────────────────────────────────────

const TOOL_TABS = [
    { id: "value",  content: "Value" },
    { id: "schema", content: "Schema" },
    { id: "traces", content: "Traces" },
];

function ToolDetailView({ tool, device, agent, allTools, onBack, onClose, onToolChange, onDeviceClick }) {
    const [selectedTab, setSelectedTab] = useState(0);
    const [pickerOpen, setPickerOpen]   = useState(false);
    const [pickerSearch, setPickerSearch] = useState("");
    const [sampleData, setSampleData] = useState(() => generateToolSample(tool));
    const collectionId = agent?.collectionIds?.[0];

    useEffect(() => {
        let cancelled = false;
        const loadSample = async () => {
            if (!collectionId || !tool?.name) {
                setSampleData(generateToolSample(tool));
                return;
            }
            try {
                const resp = await observeApi.fetchSampleData(`tools/call/${tool.name}`, collectionId, "POST");
                const samples = resp?.sampleDataList || resp?.samples || [];
                if (!cancelled && samples.length > 0 && samples[0]?.message) {
                    setSampleData({ message: samples[0].message });
                    return;
                }
            } catch {
                // fall through to generated sample
            }
            if (!cancelled) setSampleData(generateToolSample(tool));
        };
        loadSample();
        return () => { cancelled = true; };
    }, [tool?.id, tool?.name, collectionId]);

    const tabs = [
        ...TOOL_TABS,
        { id: "violations", content: `Violations (0)` },
    ];

    const otherTools    = useMemo(() => allTools.filter(t => t.id !== tool.id), [allTools, tool.id]);
    const filteredTools = useMemo(() =>
        pickerSearch
            ? otherTools.filter(t => t.name.toLowerCase().includes(pickerSearch.toLowerCase()))
            : otherTools,
        [otherTools, pickerSearch]
    );
    const toolActions   = useMemo(() =>
        filteredTools.map(t => ({
            content: t.name,
            onAction: () => { onToolChange(t); setPickerOpen(false); setPickerSearch(""); setSelectedTab(0); },
        })),
        [filteredTools, onToolChange]
    );

    const showChevron = otherTools.length > 0;
    const showSearch  = otherTools.length > 5;

    const pickerActivator = showChevron ? (
        <Button plain disclosure onClick={() => setPickerOpen(s => !s)}>
            {tool.name}
        </Button>
    ) : (
        <Text variant="bodySm" fontWeight="semibold">{tool.name}</Text>
    );

    return (
        <Box style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
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

            <Box paddingInlineStart="1" paddingInlineEnd="1">
                <Tabs tabs={tabs} selected={selectedTab} onSelect={setSelectedTab} />
            </Box>
            <Divider />

            <Box style={{ flex: 1, minHeight: 0, display: "flex", flexDirection: "column" }}>
                {selectedTab === 0 && (
                    <Box style={{ flex: 1, overflowY: "auto" }}>
                        <Box padding="4">
                            <VerticalStack gap="4">
                                <LegacyCard>
                                    <SampleDataComponent type="request" sampleData={sampleData} readOnly={true} />
                                </LegacyCard>
                                <LegacyCard>
                                    <SampleDataComponent type="response" sampleData={sampleData} readOnly={true} />
                                </LegacyCard>
                            </VerticalStack>
                        </Box>
                    </Box>
                )}
                {selectedTab === 1 && (
                    tool.params && tool.params.length > 0 ? (
                        <AgGridTable
                            rowData={tool.params}
                            columnDefs={SCHEMA_COL_DEFS}
                            defaultColDef={{ sortable: false, resizable: true }}
                            fillHeight
                            noOuterBorder
                            pagination={false}
                            sideBar={false}
                        />
                    ) : (
                        <Box padding="4">
                            <Text variant="bodySm" color="subdued">No parameters.</Text>
                        </Box>
                    )
                )}
                {selectedTab === 2 && (
                    <Box padding="4">
                        <Text variant="bodySm" color="subdued">No traces recorded yet.</Text>
                    </Box>
                )}
                {selectedTab === 3 && (
                    <Box padding="4">
                        <Text variant="bodySm" color="subdued">No violations found.</Text>
                    </Box>
                )}
            </Box>
        </Box>
    );
}

// ─── Resource detail view ─────────────────────────────────────────────────────

function ResourceDetailView({ resource, agent, device, onBack, onClose, onDeviceClick }) {
    const sampleData = useMemo(() => generateResourceSample(resource), [resource.id]);

    return (
        <Box style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: () => onDeviceClick ? onDeviceClick(device) : onBack() },
                    { label: agent?.endpoint, onClick: onBack },
                    { label: resource.name },
                ]}
                onClose={onClose}
            />
            <Box style={{ flex: 1, overflowY: "auto" }}>
                <Box padding="4">
                    <VerticalStack gap="4">
                        <LegacyCard>
                            <SampleDataComponent type="request" sampleData={sampleData} readOnly={true} />
                        </LegacyCard>
                        <LegacyCard>
                            <SampleDataComponent type="response" sampleData={sampleData} readOnly={true} />
                        </LegacyCard>
                    </VerticalStack>
                </Box>
            </Box>
        </Box>
    );
}

// ─── Prompt detail view ───────────────────────────────────────────────────────

function PromptDetailView({ prompt, agent, device, onBack, onClose, onDeviceClick }) {
    const sampleData = useMemo(() => generatePromptSample(prompt), [prompt.id]);

    return (
        <Box style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: () => onDeviceClick ? onDeviceClick(device) : onBack() },
                    { label: agent?.endpoint, onClick: onBack },
                    { label: prompt.name },
                ]}
                onClose={onClose}
            />
            <Box style={{ flex: 1, overflowY: "auto" }}>
                <Box padding="4">
                    <VerticalStack gap="4">
                        <LegacyCard>
                            <SampleDataComponent type="request" sampleData={sampleData} readOnly={true} />
                        </LegacyCard>
                        <LegacyCard>
                            <SampleDataComponent type="response" sampleData={sampleData} readOnly={true} />
                        </LegacyCard>
                    </VerticalStack>
                </Box>
            </Box>
        </Box>
    );
}

// ─── Resources & Prompts list views ──────────────────────────────────────────

function ResourcesView({ resources, onResourceClick }) {
    if (resources.length === 0) {
        return (
            <Box paddingBlockStart="12">
                <VerticalStack gap="2" align="center">
                    <Text variant="bodySm" fontWeight="semibold">No resources exposed</Text>
                    <Text variant="bodySm" color="subdued">This MCP server does not expose any resources.</Text>
                </VerticalStack>
            </Box>
        );
    }
    return (
        <AgGridTable
            rowData={resources}
            columnDefs={RESOURCES_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={e => { if (e.data) onResourceClick(e.data); }}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search resources..."
            pagination={false}
            sideBar={false}
        />
    );
}

function PromptsView({ prompts, onPromptClick }) {
    if (prompts.length === 0) {
        return (
            <Box paddingBlockStart="12">
                <VerticalStack gap="2" align="center">
                    <Text variant="bodySm" fontWeight="semibold">No prompts defined</Text>
                    <Text variant="bodySm" color="subdued">This MCP server does not expose any prompt templates.</Text>
                </VerticalStack>
            </Box>
        );
    }
    return (
        <AgGridTable
            rowData={prompts}
            columnDefs={PROMPTS_COL_DEFS}
            defaultColDef={GRID_DEFAULT_COL}
            onRowClicked={e => { if (e.data) onPromptClick(e.data); }}
            fillHeight
            noOuterBorder
            searchPlaceholder="Search prompts..."
            pagination={false}
            sideBar={false}
        />
    );
}

// ─── Tools list view ──────────────────────────────────────────────────────────

function ToolsListView({ agent, device, tools, resources, prompts, onToolClick, onClose, onDeviceClick, onResourceClick, onPromptClick }) {
    const [selectedTab, setSelectedTab] = useState(0);

    const mcpTabs = useMemo(() => [
        { id: "tools",     content: `Tools (${tools.length})` },
        { id: "resources", content: `Resources (${resources.length})` },
        { id: "prompts",   content: `Prompts (${prompts.length})` },
    ], [tools.length, resources.length, prompts.length]);

    const handleTabChange = useCallback((i) => { setSelectedTab(i); }, []);

    return (
        <Box style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
            <FlyoutBreadcrumb
                items={[
                    { label: device?.endpoint, badge: device?.riskScore, onClick: onDeviceClick ? () => onDeviceClick(device) : undefined },
                    { label: agent?.endpoint },
                ]}
                onClose={onClose}
            />

            <Box paddingInlineStart="1" paddingInlineEnd="1">
                <Tabs tabs={mcpTabs} selected={selectedTab} onSelect={handleTabChange} />
            </Box>
            <Divider />

            {selectedTab === 0 && (
                tools.length === 0 ? (
                    <Box paddingBlockStart="12">
                        <VerticalStack gap="2" align="center">
                            <Text variant="bodySm" fontWeight="semibold">No tools captured</Text>
                            <Text variant="bodySm" color="subdued">
                                Tool schema for {agent?.endpoint} has not been captured yet.
                            </Text>
                        </VerticalStack>
                    </Box>
                ) : (
                    <AgGridTable
                        rowData={tools}
                        columnDefs={TOOLS_COL_DEFS}
                        defaultColDef={GRID_DEFAULT_COL}
                        onRowClicked={e => { if (e.data) onToolClick(e.data); }}
                        fillHeight
                        noOuterBorder
                        searchPlaceholder="Search tools..."
                        sideBar={false}
                    />
                )
            )}

            {selectedTab === 1 && <ResourcesView resources={resources} onResourceClick={onResourceClick} />}
            {selectedTab === 2 && <PromptsView prompts={prompts} onPromptClick={onPromptClick} />}
        </Box>
    );
}

// ─── Main McpFlyout ───────────────────────────────────────────────────────────

export default function McpFlyout({ agent, device, show, onClose, onDeviceClick }) {
    const [selectedTool,     setSelectedTool]     = useState(null);
    const [selectedResource, setSelectedResource] = useState(null);
    const [selectedPrompt,   setSelectedPrompt]   = useState(null);
    const [allTools, setAllTools] = useState([]);
    const [allResources, setAllResources] = useState([]);
    const [allPrompts, setAllPrompts] = useState([]);
    const collectionId = agent?.collectionIds?.[0];

    useEffect(() => {
        if (!show || !collectionId) {
            setAllTools([]);
            setAllResources([]);
            setAllPrompts([]);
            return;
        }
        let cancelled = false;
        (async () => {
            try {
                const data = await agenticObserveApi.fetchMcpFlyoutData(collectionId);
                if (cancelled) return;
                const toolViolations = data.toolViolations || {};
                const tools = (data.tools || []).map((t) => ({
                    ...t,
                    violationCount: toolViolations[t.name] || 0,
                }));
                setAllTools(tools);
                setAllResources(data.resources || []);
                setAllPrompts(data.prompts || []);
            } catch {
                if (!cancelled) {
                    setAllTools([]);
                    setAllResources([]);
                    setAllPrompts([]);
                }
            }
        })();
        return () => { cancelled = true; };
    }, [show, collectionId, agent?.endpoint]);

    React.useEffect(() => {
        if (!show) { setSelectedTool(null); setSelectedResource(null); setSelectedPrompt(null); }
    }, [show]);
    React.useEffect(() => {
        setSelectedTool(null); setSelectedResource(null); setSelectedPrompt(null);
    }, [agent?.endpoint]);

    const chatMetadata = useMemo(() => buildAgenticObserveChatMetadata("mcp", {
        deviceEndpoint: device?.endpoint,
        deviceId: device?.path?.[0],
        agentEndpoint: agent?.endpoint,
        riskScore: agent?.riskScore ?? device?.riskScore,
        toolName: selectedTool?.name,
        resourceName: selectedResource?.name,
        promptName: selectedPrompt?.name,
        counts: {
            tools: allTools.length,
            resources: allResources.length,
            prompts: allPrompts.length,
        },
    }), [device, agent, selectedTool, selectedResource, selectedPrompt, allTools.length, allResources.length, allPrompts.length]);

    const lockScroll   = useCallback(() => { document.body.style.overflow = "hidden"; }, []);
    const unlockScroll = useCallback(() => { document.body.style.overflow = "";       }, []);

    React.useEffect(() => { if (!show) document.body.style.overflow = ""; }, [show]);

    if (!agent) return null;

    return (
        <Box className={"flyLayout " + (show ? "show" : "")} style={{ width: 720 }}>
            <Box
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

                <AiChatSection
                    placeholder="Ask about this MCP server's tools and risks..."
                    resetKey={`${agent?.endpoint}-${selectedTool?.name || ""}-${selectedResource?.name || ""}-${selectedPrompt?.name || ""}`}
                    conversationType="ASK_AKTO"
                    chatMetadata={chatMetadata}
                />
            </Box>
        </Box>
    );
}
