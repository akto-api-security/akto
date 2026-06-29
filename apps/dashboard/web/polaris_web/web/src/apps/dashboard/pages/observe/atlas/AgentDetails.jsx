import { Text, HorizontalStack, VerticalStack, Box, Badge, Button, Icon, Tooltip, Avatar, List, Spinner } from "@shopify/polaris"
import { useRef, useMemo, useCallback, useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { motion, AnimatePresence } from 'framer-motion'
import { CaretDownMinor, CodeMinor, DynamicSourceMinor, ClockMinor, CalendarMinor, ExportMinor, RefreshMinor, ChevronLeftMinor, ChevronRightMinor } from '@shopify/polaris-icons'
import InlineEditableText from "../../../components/shared/InlineEditableText"
import func from "@/util/func"
import FlyLayout from "../../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { DEFAULT_VALUE } from "../api_collections/endpointShieldHelper";
import ModuleEnvConfigComponent from "../../settings/health_logs/ModuleEnvConfig";
import settingRequests from "../../settings/api";

const ANIMATION_DURATION = 0.2;
const LOG_LEVEL_TONES = {
    INFO: 'info',
    WARNING: 'warning',
    ERROR: 'critical'
};
const ICON_SIZE = { maxWidth: "1rem", maxHeight: "1rem" };
const LOG_TIMESTAMP_WIDTH = "140px";
const LOG_LEVEL_WIDTH = "50px";

const PAGE_SIZE = 500; // logs displayed per page

export const LOG_MODES = {
    HISTORICAL: 'HISTORICAL'
};

const MetadataField = ({ icon, tooltip, value }) => {
    if (!value || value === DEFAULT_VALUE) return null;
    return (
        <HorizontalStack wrap={false} gap="1">
            <Box maxWidth={ICON_SIZE.maxWidth} minHeight={ICON_SIZE.maxHeight}>
                <Tooltip content={tooltip} dismissOnMouseOut>
                    <Icon source={icon} color="subdued" />
                </Tooltip>
            </Box>
            <Text variant="bodySm" color="subdued">{value}</Text>
        </HorizontalStack>
    );
};

const getMetadataFields = (agent) => [
    { icon: CodeMinor, tooltip: "Agent ID", value: agent.agentId },
    { icon: DynamicSourceMinor, tooltip: "Device ID", value: agent.deviceId },
    { icon: ClockMinor, tooltip: "Last Heartbeat", value: func.prettifyEpoch(agent.lastHeartbeat) },
    { icon: CalendarMinor, tooltip: "Last Deployed", value: func.prettifyEpoch(agent.lastDeployed) },
];

const DEVICE_INFO_SECTIONS = [
    {
        title: "Hardware",
        fields: [
            { label: "CPU Model",      key: "cpuModel" },
            { label: "Mac Model",      key: "macModel" },
            { label: "Architecture",   key: "arch" },
            { label: "RAM",            key: "totalRamGB",    format: (v) => (v != null && v !== DEFAULT_VALUE) ? `${v} GB` : null },
            { label: "Total Disk",     key: "totalDiskGB",   format: (v) => (v != null && v !== DEFAULT_VALUE) ? `${v} GB` : null },
            { label: "Free Disk",      key: "availableDiskGB", format: (v) => (v != null && v !== DEFAULT_VALUE) ? `${v} GB` : null },
            { label: "CPU Cores",      key: "cpuCount",      format: (v) => (v != null && v !== DEFAULT_VALUE) ? String(v) : null },
            { label: "Virtual Machine",key: "isVM",          isVM: true },
        ],
    },
    {
        title: "Operating System",
        fields: [
            { label: "OS",             key: "osDisplayName", format: (v, agent) => v || agent?.os || null },
            { label: "OS Version",     key: "osVersion" },
            { label: "Kernel",         key: "kernelVersion" },
            { label: "Last Boot",      key: "bootTime",      format: (v) => (v && v !== DEFAULT_VALUE) ? func.prettifyEpoch(v) : null },
            { label: "Locale",         key: "locale" },
        ],
    },
    {
        title: "Network",
        fields: [
            { label: "Public IP",      key: "publicIP" },
            { label: "Local IP",       key: "localIP" },
            { label: "Hostname",       key: "localHostname" },
        ],
    },
    {
        title: "User",
        fields: [
            { label: "Full Name",      key: "userFullName" },
            { label: "Username",       key: "username" },
            { label: "Shell",          key: "userShell" },
        ],
    },
    {
        title: "Agent",
        fields: [
            { label: "Shield Version", key: "agentVersion" },
        ],
    },
];

const DeviceSectionGrid = ({ fields, agent }) => {
    const rows = fields.map(field => {
        if (field.isVM) {
            const val = agent.isVM;
            if (val == null) return null;
            return { label: field.label, value: val ? 'Yes' : 'No' };
        }
        const raw = field.format ? field.format(agent[field.key], agent) : agent[field.key];
        if (!raw || raw === DEFAULT_VALUE) return null;
        return { label: field.label, value: raw };
    }).filter(Boolean);

    if (rows.length === 0) return null;

    return (
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px 24px' }}>
            {rows.map(row => (
                <VerticalStack gap="1" key={row.label}>
                    <Text variant="bodySm" color="subdued">{row.label}</Text>
                    <Text variant="bodyMd">{'vmVal' in row ? (row.vmVal ? 'Yes' : 'No') : row.value}</Text>
                </VerticalStack>
            ))}
        </div>
    );
};

const DeviceInfoGrid = ({ agent }) => {
    if (!agent) return null;

    const hasAnyField = DEVICE_INFO_SECTIONS.some(section =>
        section.fields.some(field => {
            if (field.isVM) return agent.isVM != null && agent.isVM !== DEFAULT_VALUE;
            const raw = field.format ? field.format(agent[field.key], agent) : agent[field.key];
            return raw && raw !== DEFAULT_VALUE;
        })
    );

    if (!hasAnyField) return (
        <Box padding="4">
            <Text variant="bodyMd" color="subdued" alignment="center">No device info available yet</Text>
        </Box>
    );

    const visibleSections = DEVICE_INFO_SECTIONS.filter(section =>
        section.fields.some(field => {
            if (field.isVM) return agent.isVM != null && agent.isVM !== DEFAULT_VALUE;
            const raw = field.format ? field.format(agent[field.key], agent) : agent[field.key];
            return raw && raw !== DEFAULT_VALUE;
        })
    );

    return (
        <VerticalStack gap="0">
            {visibleSections.map((section, idx) => (
                <div key={section.title}>
                    {idx > 0 && (
                        <div style={{ borderTop: '2px solid #C9CCCF', margin: '16px 0' }} />
                    )}
                    <VerticalStack gap="3">
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                            <div style={{ width: '3px', height: '14px', background: '#8C9196', borderRadius: '2px', flexShrink: 0 }} />
                            <Text variant="headingSm">{section.title}</Text>
                        </div>
                        <DeviceSectionGrid fields={section.fields} agent={agent} />
                    </VerticalStack>
                </div>
            ))}
        </VerticalStack>
    );
};

const InstalledAppsSection = ({ apps }) => {
    const [expanded, setExpanded] = useState(false);
    if (!apps || apps.length === 0) return null;
    const sorted = [...apps].sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    const shown = expanded ? sorted : sorted.slice(0, 8);
    return (
        <VerticalStack gap="3">
            <HorizontalStack align="space-between" blockAlign="center">
                <Text variant="headingSm">Installed Applications</Text>
                <Badge>{String(apps.length)}</Badge>
            </HorizontalStack>
                <div>
                    {shown.map((app, idx) => (
                        <div
                            key={app.bundleId || app.name || idx}
                            style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', borderBottom: '1px solid #F6F6F7' }}
                        >
                            <Text variant="bodySm">{app.name}</Text>
                            <Text variant="bodySm" color="subdued">{app.version || '—'}</Text>
                        </div>
                    ))}
                </div>
                {apps.length > 8 && (
                    <Button plain onClick={() => setExpanded(e => !e)}>
                        {expanded ? 'Show less' : `Show all ${apps.length} apps`}
                    </Button>
                )}
        </VerticalStack>
    );
};

const createSimpleHeader = (text, value = null) => ({
    text,
    title: text,
    value: value || text.toLowerCase().replace(/ /g, '')
});

const mcpServersHeaders = [
    createSimpleHeader("Server Name", "serverName"),
    createSimpleHeader("Endpoint / Command", "serverUrl"),
    createSimpleHeader("Last Updated", "lastSeenFormatted")
];

function AgentDetails({
    show,
    setShow,
    selectedAgent,
    allCollections,
    allowedEnvFields,
    onSaveEnv,
    startTimestamp,
    endTimestamp,
}) {
    const navigate = useNavigate();
    const copyRef = useRef(null);

    const [loading, setLoading] = useState(false);
    const [tabLoading, setTabLoading] = useState(false);
    const [mcpServers, setMcpServers] = useState([]);
    const [userAnalysis, setUserAnalysis] = useState(null);
    const [currentPageLogs, setCurrentPageLogs] = useState([]);
    const [logsLoading, setLogsLoading] = useState(false);
    const [exportLoading, setExportLoading] = useState(false);
    const [pageStack, setPageStack] = useState([null]); // [null, afterId_p0, afterId_p1, ...]
    const [pageIndex, setPageIndex] = useState(0);
    const [hasMore, setHasMore] = useState(false);
    const [totalCount, setTotalCount] = useState(0);
    const [logMode, setLogMode] = useState(LOG_MODES.HISTORICAL);
    const [selectedLogSource, setSelectedLogSource] = useState('installation-logs');
    const logSourceRef = useRef('installation-logs');
    const [description, setDescription] = useState("");
    const [isEditingDescription, setIsEditingDescription] = useState(false);
    const [editableDescription, setEditableDescription] = useState("");

    const fetchPage = useCallback(async (afterId, updateTotal = false) => {
        if (!selectedAgent) return;
        setLogsLoading(true);
        try {
            const res = await settingRequests.getAgentLogs(
                selectedAgent.agentId, startTimestamp, endTimestamp,
                logSourceRef.current,
                afterId,
                PAGE_SIZE
            );
            const transformed = (res.agentLogs || []).map(log => ({
                hexId: log.hexId,
                timestamp: log.timestamp,
                level: log.level || 'INFO',
                message: log.log || log.message
            }));
            setCurrentPageLogs(transformed);
            setHasMore(res.hasMore || false);
            if (updateTotal) setTotalCount(res.totalCount || 0);
        } catch (error) {
            console.error("Error fetching agent logs:", error);
            setCurrentPageLogs([]);
        } finally {
            setLogsLoading(false);
        }
    }, [selectedAgent, startTimestamp, endTimestamp]);

    const handleSourceChange = useCallback(async (source) => {
        if (logSourceRef.current === source) return;
        logSourceRef.current = source;
        setSelectedLogSource(source);
        if (!selectedAgent) return;
        setPageStack([null]);
        setPageIndex(0);
        setCurrentPageLogs([]);
        await fetchPage(null, true);
    }, [selectedAgent, fetchPage]);

    const handleRefresh = useCallback(async () => {
        setPageStack([null]);
        setPageIndex(0);
        await fetchPage(null, true);
    }, [fetchPage]);

    const handlePrevPage = useCallback(async () => {
        const newIndex = pageIndex - 1;
        setPageIndex(newIndex);
        await fetchPage(pageStack[newIndex]);
    }, [pageIndex, pageStack, fetchPage]);

    const handleNextPage = useCallback(async () => {
        const lastId = currentPageLogs[currentPageLogs.length - 1]?.hexId;
        const newIndex = pageIndex + 1;
        if (newIndex >= pageStack.length) {
            setPageStack(prev => [...prev, lastId]);
        }
        setPageIndex(newIndex);
        await fetchPage(lastId);
    }, [currentPageLogs, pageIndex, pageStack, fetchPage]);

    const handleExportLogs = useCallback(async () => {
        if (!selectedAgent) return;
        setExportLoading(true);
        try {
            const res = await settingRequests.exportAgentLogs(
                selectedAgent.agentId, startTimestamp, endTimestamp,
                logSourceRef.current
            );
            const logs = res.agentLogs || [];
            if (logs.length === 0) return;
            const txt = logs
                .map(l => `[${func.epochToDateTime(l.timestamp)}] [${(l.level || 'INFO').padEnd(7)}] ${l.log || l.message || ''}`)
                .join('\n');
            const blob = new Blob([txt], { type: 'text/plain' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${logSourceRef.current}-${selectedAgent.agentId}.txt`;
            a.click();
            URL.revokeObjectURL(url);
        } catch (error) {
            console.error("Error exporting logs:", error);
        } finally {
            setExportLoading(false);
        }
    }, [selectedAgent, startTimestamp, endTimestamp]);

    const handleSaveDescription = useCallback(() => {
        setDescription(editableDescription);
        setIsEditingDescription(false);
        func.setToast(true, false, "Description saved");
    }, [editableDescription]);

    // Reset state when agent changes and fetch data for the default first tab (MCP Servers).
    useEffect(() => {
        if (!selectedAgent || !show) return;

        setMcpServers([]);
        setUserAnalysis(null);
        setCurrentPageLogs([]);
        setLogsLoading(false);
        setPageStack([null]);
        setPageIndex(0);
        setHasMore(false);
        setTotalCount(0);
        setLogMode(LOG_MODES.HISTORICAL);
        setSelectedLogSource('installation-logs');
        logSourceRef.current = 'installation-logs';
        setDescription("");
        setEditableDescription("");
        setIsEditingDescription(false);

        setLoading(true);
        settingRequests.getMcpServersByAgent(selectedAgent.agentId, selectedAgent.hostname)
            .then(res => setMcpServers(res.mcpServers || []))
            .catch(() => setMcpServers([]))
            .finally(() => setLoading(false));
    }, [selectedAgent, show]);

    const handleTabChange = useCallback(async (tab) => {
        if (!selectedAgent) return;
        switch (tab.id) {
            case 'mcp-servers':
                setTabLoading(true);
                try {
                    const res = await settingRequests.getMcpServersByAgent(selectedAgent.agentId, selectedAgent.hostname);
                    setMcpServers(res.mcpServers || []);
                } catch {
                    setMcpServers([]);
                } finally {
                    setTabLoading(false);
                }
                break;
            case 'user-analysis':
                setTabLoading(true);
                try {
                    const res = await settingRequests.getUserAnalysis(selectedAgent.hostname);
                    setUserAnalysis(res || null);
                } catch {
                    setUserAnalysis(null);
                } finally {
                    setTabLoading(false);
                }
                break;
            case 'agent-logs': {
                setCurrentPageLogs([]);
                setPageStack([null]);
                setPageIndex(0);
                setHasMore(false);
                setTotalCount(0);
                await fetchPage(null, true);
                break;
            }
            default:
                break;
        }
    }, [selectedAgent, fetchPage]);

    const mcpServersTableData = useMemo(() =>
        mcpServers.map(server => ({
            serverName: server.serverName,
            serverUrl: server.serverUrl,
            lastSeenFormatted: func.prettifyEpoch(server.lastSeen),
            lastSeen: server.lastSeen,
            collectionName: server.collectionName
        })), [mcpServers]);

    const handleServerClick = useCallback((server) => {
        const collection = allCollections.find(col =>
            col.name === server.collectionName || col.displayName === server.collectionName
        );
        if (collection) {
            navigate(`/dashboard/observe/inventory/${collection.id}`);
        } else {
            func.setToast(true, true, `Collection "${server.collectionName}" not found`);
        }
    }, [allCollections, navigate]);

    const renderLogs = () => {
        const logSources = [
            // { label: 'All', value: 'all' }, // removed — use specific sources
            { label: 'Install', value: 'installation-logs' },
            { label: 'Agent', value: 'agent-logs' },
            { label: 'Proxy', value: 'proxy-logs' },
        ];

        const controls = (
            <HorizontalStack gap="2" align="space-between" wrap={false}>
                <HorizontalStack gap="1">
                    {logSources.map(({ label, value }) => (
                        <Button
                            key={value}
                            size="micro"
                            pressed={selectedLogSource === value}
                            onClick={() => handleSourceChange(value)}
                        >
                            {label}
                        </Button>
                    ))}
                </HorizontalStack>
                <HorizontalStack gap="1">
                    <Button
                        size="micro"
                        icon={RefreshMinor}
                        disabled={logsLoading}
                        onClick={handleRefresh}
                        accessibilityLabel="Refresh logs"
                    />
                    <Button
                        size="micro"
                        icon={ExportMinor}
                        disabled={totalCount === 0 || logsLoading || exportLoading}
                        loading={exportLoading}
                        onClick={handleExportLogs}
                    >
                        Export
                    </Button>
                </HorizontalStack>
            </HorizontalStack>
        );

        const pageStart = pageIndex * PAGE_SIZE + 1;
        const pageEnd = !hasMore ? totalCount : pageIndex * PAGE_SIZE + currentPageLogs.length;
        const canGoPrev = pageIndex > 0;
        const canGoNext = hasMore;

        let logBody;
        if (logsLoading) {
            logBody = (
                <Box padding="8">
                    <VerticalStack gap="3" inlineAlign="center">
                        <Spinner size="small" accessibilityLabel="Loading logs" />
                        <Text variant="bodySm" color="subdued" alignment="center">Loading logs…</Text>
                    </VerticalStack>
                </Box>
            );
        } else if (currentPageLogs.length === 0) {
            logBody = (
                <Box padding="8">
                    <Text variant="bodyMd" color="subdued" alignment="center">No logs found for this time window.</Text>
                </Box>
            );
        } else {
            logBody = (
                <div className="max-h-[55vh] overflow-auto">
                    <AnimatePresence initial={false}>
                        {currentPageLogs.map((log, index) => (
                            <motion.div
                                key={log.hexId || `${log.timestamp}-${index}`}
                                initial={false}
                                animate={{ opacity: 1, y: 0 }}
                                transition={{ duration: ANIMATION_DURATION, ease: "easeOut" }}
                                className="px-3 py-1 hover:bg-[var(--background-selected)] border-b border-[#E4E5E7] last:border-0"
                            >
                                <HorizontalStack gap="3" align="start" wrap={false}>
                                    <Box minWidth={LOG_TIMESTAMP_WIDTH}>
                                        <Text variant="bodySm" fontWeight="medium" tone="subdued">
                                            {func.epochToDateTime(log.timestamp)}
                                        </Text>
                                    </Box>
                                    <Box minWidth={LOG_LEVEL_WIDTH}>
                                        <Badge size="small" tone={LOG_LEVEL_TONES[log.level] || 'info'}>
                                            {log.level}
                                        </Badge>
                                    </Box>
                                    <Box overflow="hidden">
                                        <Text variant="bodySm" as="p" breakWord>{log.message}</Text>
                                    </Box>
                                </HorizontalStack>
                            </motion.div>
                        ))}
                    </AnimatePresence>
                </div>
            );
        }

        const footer = (
            <HorizontalStack align="center" wrap={false} gap="2">
                <Button
                    plain
                    icon={ChevronLeftMinor}
                    disabled={!canGoPrev || logsLoading}
                    onClick={handlePrevPage}
                    accessibilityLabel="Previous page"
                />
                {totalCount > 0 && (
                    <Text variant="bodySm" color="subdued" alignment="center">
                        {`${pageStart}-${pageEnd} of ${totalCount}`}
                    </Text>
                )}
                <Button
                    plain
                    icon={ChevronRightMinor}
                    disabled={!canGoNext || logsLoading}
                    onClick={handleNextPage}
                    accessibilityLabel="Next page"
                />
            </HorizontalStack>
        );

        return (
            <VerticalStack gap="2">
                {controls}
                <div className="rounded-lg overflow-hidden border border-[#C9CCCF] bg-[#F6F6F7]">
                    {logBody}
                </div>
                {footer}
            </VerticalStack>
        );
    };

    const McpServersTab = {
        id: 'mcp-servers',
        content: 'MCP Servers',
        component: (
            <Box paddingBlockStart={"4"}>
                <GithubSimpleTable
                    key="mcp-servers-table"
                    data={mcpServersTableData}
                    resourceName={{ singular: "server", plural: "servers" }}
                    headers={mcpServersHeaders}
                    headings={mcpServersHeaders}
                    useNewRow={true}
                    condensedHeight={true}
                    hideQueryField={true}
                    loading={tabLoading}
                    pageLimit={10}
                    showFooter={false}
                    onRowClick={handleServerClick}
                    rowClickable={true}
                />
            </Box>
        ),
        panelID: 'mcp-servers-panel',
    };

    const AgentLogsTab = {
        id: 'agent-logs',
        content: 'Logs',
        component: (
            <Box paddingBlockStart={"4"}>
                <VerticalStack gap="2">
                    {renderLogs()}
                </VerticalStack>
            </Box>
        ),
        panelID: 'agent-logs-panel',
    };

    const ConfigureTab = {
        id: 'configure',
        content: 'Configure',
        component: (
            <Box paddingBlockStart={"4"}>
                <ModuleEnvConfigComponent
                    title="Environment Variables"
                    description="Configure environment variables for this agent. Changes will be picked up on the next poll cycle."
                    module={selectedAgent?._moduleData}
                    allowedEnvFields={allowedEnvFields}
                    onSaveEnv={onSaveEnv}
                />
            </Box>
        ),
        panelID: 'configure-panel',
    };

    const DeviceTab = {
        id: 'device-info',
        content: 'Device',
        component: (
            <Box paddingBlockStart="4">
                <DeviceInfoGrid agent={selectedAgent} />
            </Box>
        ),
        panelID: 'device-info-panel',
    };

    const AppsTab = {
        id: 'installed-apps',
        content: 'Apps',
        component: (
            <Box paddingBlockStart="4">
                <InstalledAppsSection apps={selectedAgent?.installedApps} />
            </Box>
        ),
        panelID: 'installed-apps-panel',
    };

    const getInputTokenLabel = (tokens) => {
        if (tokens < 10000) return { label: "Light user", tone: "success" };
        if (tokens < 100000) return { label: "Moderate user", tone: "attention" };
        return { label: "Heavy user", tone: "critical" };
    };

    const getOutputTokenLabel = (inputTokens, outputTokens) => {
        if (outputTokens > inputTokens * 3) return { label: "High output amplifier", tone: "critical" };
        if (outputTokens > inputTokens * 1.5) return { label: "Verbose responder", tone: "attention" };
        return { label: "Balanced output", tone: "success" };
    };

    const humanizeTopicKey = (key) =>
        key.replace(/_/g, " ").replace(/\b\w/g, c => c.toUpperCase());

    const titleComp = <TitleWithInfo
                titleText="Queries Flagged"
                textProps={{ variant: "headingMd" }}
                tooltipContent="Queries identified as potentially harmful or policy-violating."
            />

    const dominantTopics = useMemo(() => {
        if (!userAnalysis?.topicCounts) return [];
        return Object.entries(userAnalysis.topicCounts)
            .sort(([, a], [, b]) => b - a)
            .slice(0, 5)
            .map(([topic]) => topic);
    }, [userAnalysis]);

    const UserAnalysisTab = {
        id: 'user-analysis',
        content: 'User Analysis',
        component: (
            <Box paddingBlockStart={"4"}>
                {tabLoading ? (
                    <Box padding="4" background="bg-surface">
                        <Text variant="bodyMd" color="subdued" alignment="center">Loading...</Text>
                    </Box>
                ) : !userAnalysis ? (
                    <Box padding="4" background="bg-surface">
                        <Text variant="bodyMd" color="subdued" alignment="center">No analysis data found</Text>
                    </Box>
                ) : (
                    <VerticalStack gap="4">
                        {userAnalysis.aiSummary && (
                            <VerticalStack gap="1">
                                <TitleWithInfo
                                    titleText="User Analysis Summary"
                                    textProps={{ variant: "headingMd" }}
                                    tooltipContent="An overview summary of the user using the current agent."
                                />
                                <Text variant="bodyMd">{userAnalysis.aiSummary}</Text>
                            </VerticalStack>
                        )}
                        <HorizontalStack gap="6">
                            <VerticalStack gap="1">
                                <TitleWithInfo
                                    titleText="Input Tokens"
                                    textProps={{ variant: "headingMd" }}
                                    tooltipContent={`Total input tokens. (${getInputTokenLabel(userAnalysis.totalInputTokens ?? 0).label})`}
                                />
                                <Box>
                                    <Badge status={getInputTokenLabel(userAnalysis.totalInputTokens ?? 0).tone}>
                                        {transform.formatNumberWithCommas(userAnalysis.totalInputTokens ?? 0)}
                                    </Badge>
                                </Box>
                            </VerticalStack>
                            <VerticalStack gap="1">
                                <TitleWithInfo
                                    titleText="Output Tokens"
                                    textProps={{ variant: "headingMd" }}
                                    tooltipContent={`Total output tokens. (${getOutputTokenLabel(userAnalysis.totalInputTokens ?? 0, userAnalysis.totalOutputTokens ?? 0).label})`}
                                />
                                <Box>
                                    <Badge status={getOutputTokenLabel(userAnalysis.totalInputTokens ?? 0, userAnalysis.totalOutputTokens ?? 0).tone}>
                                        {transform.formatNumberWithCommas(userAnalysis.totalOutputTokens ?? 0)}
                                    </Badge>
                                </Box>
                            </VerticalStack>
                        </HorizontalStack>
                        {dominantTopics.length > 0 && (
                            <VerticalStack gap="2">
                                <TitleWithInfo
                                    titleText="Dominant Topics"
                                    textProps={{ variant: "headingMd" }}
                                    tooltipContent="The topics that the user mostly queries."
                                />
                                <List type="bullet" gap="extraTight">
                                    {dominantTopics.map((topic) => (
                                        <List.Item key={topic}>{humanizeTopicKey(topic)}</List.Item>
                                    ))}
                                </List>
                            </VerticalStack>
                        )}
                        {userAnalysis.harmfulTopics && Object.keys(userAnalysis.harmfulTopics).length > 0 ? (
                            <VerticalStack gap="2">
                                {titleComp}
                                <List type="bullet" gap="extraTight">
                                    {Object.entries(userAnalysis.harmfulTopics).map(([topic, data]) => (
                                        <List.Item key={topic}>
                                            <VerticalStack gap="1">
                                                <HorizontalStack gap="2" blockAlign="center">
                                                    <Text variant="bodyMd" fontWeight="bold" color="critical">
                                                        {humanizeTopicKey(topic)}
                                                    </Text>
                                                    {data.lastSeenAt && (
                                                        <Text variant="bodySm" color="subdued">
                                                            {func.prettifyEpoch(
                                                                typeof data.lastSeenAt === "object" && data.lastSeenAt.$numberLong
                                                                    ? Math.floor(parseInt(data.lastSeenAt.$numberLong) / 1000)
                                                                    : Math.floor(data.lastSeenAt / 1000)
                                                            )}
                                                        </Text>
                                                    )}
                                                    {data.count != null && (
                                                        <Badge size="small" tone="critical">{`${data.count}x`}</Badge>
                                                    )}
                                                </HorizontalStack>
                                                {data.lastReason && (
                                                    <Text variant="bodySm" fontWeight="regular" color="subdued" as="p">
                                                        {data.lastReason}
                                                    </Text>
                                                )}
                                            </VerticalStack>
                                        </List.Item>
                                    ))}
                                </List>
                            </VerticalStack>
                        ) : (
                            <Box padding="4" background="bg-surface">
                                <Text variant="bodyMd" color="subdued" alignment="center">No harmful topics found</Text>
                            </Box>
                        )}
                    </VerticalStack>
                )}
            </Box>
        ),
        panelID: 'user-analysis-panel',
    };

    if (!selectedAgent) return null;

    return (
        <FlyLayout
            show={show}
            setShow={setShow}
            loading={loading}
            title="Agent Details"
            components={[
                <HorizontalStack align="space-between" wrap={false} key="agent-heading">
                    <VerticalStack gap="2">
                        <HorizontalStack gap="2" wrap={false}>
                            <Text variant="headingMd" as="h2">
                                {selectedAgent.username}
                            </Text>
                            <Box paddingBlockStart={"05"}>
                                <Button plain onClick={() => func.copyToClipboard(selectedAgent.agentId, copyRef, "Agent ID copied")}>
                                    <Tooltip content="Copy Agent ID" dismissOnMouseOut>
                                        <div className="reduce-size">
                                            <Avatar size="extraSmall" source="/public/copy_icon.svg" />
                                        </div>
                                    </Tooltip>
                                    <Box ref={copyRef} />
                                </Button>
                            </Box>
                        </HorizontalStack>
                        <Box maxWidth="32vw">
                            {isEditingDescription ? (
                                <InlineEditableText
                                    textValue={editableDescription}
                                    setTextValue={setEditableDescription}
                                    handleSaveClick={handleSaveDescription}
                                    setIsEditing={setIsEditingDescription}
                                    placeholder="Add a brief description"
                                    maxLength={64}
                                />
                            ) : (
                                <Button plain removeUnderline onClick={() => {
                                    setEditableDescription(description);
                                    setIsEditingDescription(true);
                                }} textAlign="left">
                                    <Text as="span" variant="bodyMd" color={description ? "subdued" : undefined} alignment="start">
                                        {description || "Add description"}
                                    </Text>
                                </Button>
                            )}
                        </Box>
                        <Box>
                            <HorizontalStack gap="2" align="start">
                                {getMetadataFields(selectedAgent).map((field, index) => (
                                    <MetadataField
                                        key={index}
                                        icon={field.icon}
                                        tooltip={field.tooltip}
                                        value={field.value}
                                    />
                                ))}
                            </HorizontalStack>
                        </Box>
                    </VerticalStack>
                </HorizontalStack>,
                <LayoutWithTabs
                    key="tabs"
                    tabs={[McpServersTab, DeviceTab, AppsTab, UserAnalysisTab, AgentLogsTab, ConfigureTab]}
                    currTab={handleTabChange}
                />
            ]}
        />
    );
}

export default AgentDetails;
