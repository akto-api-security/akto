import { Text, HorizontalStack, VerticalStack, Box, Badge, Button, Icon, Tooltip, Avatar, Spinner } from "@shopify/polaris"
import { useRef, useMemo, useCallback, useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { motion, AnimatePresence } from 'framer-motion'
import { CodeMinor, DynamicSourceMinor, ClockMinor, CalendarMinor, ExportMinor, RefreshMinor, ChevronLeftMinor, ChevronRightMinor } from '@shopify/polaris-icons'
import InlineEditableText from "../../../components/shared/InlineEditableText"
import func from "@/util/func"
import FlyLayout from "../../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { DEFAULT_VALUE } from "../api_collections/endpointShieldHelper";
import ModuleEnvConfigComponent from "../../settings/health_logs/ModuleEnvConfig";
import settingRequests from "../../settings/api";
import DetailGrid from "../agentic/DetailGrid";

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

const DeviceInfoGrid = ({ agent }) => {
    if (!agent) return null;

    const visibleSections = DEVICE_INFO_SECTIONS.map(section => {
        const items = section.fields.map(field => {
            if (field.isVM) {
                const val = agent.isVM;
                if (val == null) return null;
                return { label: field.label, value: val ? 'Yes' : 'No' };
            }
            const raw = field.format ? field.format(agent[field.key], agent) : agent[field.key];
            if (!raw || raw === DEFAULT_VALUE) return null;
            return { label: field.label, value: String(raw) };
        }).filter(Boolean);
        return { title: section.title, items };
    }).filter(s => s.items.length > 0);

    if (visibleSections.length === 0) return (
        <Box padding="4">
            <Text variant="bodyMd" color="subdued" alignment="center">No device info available yet</Text>
        </Box>
    );

    return (
        <VerticalStack gap="5">
            {visibleSections.map(section => (
                <DetailGrid key={section.title} heading={section.title} items={section.items} columns={2} />
            ))}
        </VerticalStack>
    );
};

// Decodes literal \uXXXX sequences (double-escaped by some agents) then strips
// invisible Unicode formatting chars (LTR/RTL marks, zero-width spaces, etc.)
const sanitizeAppText = (str) => {
    if (!str) return str;
    const decoded = str.replace(/\\u([0-9a-fA-F]{4})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
    return decoded.replace(/[\u200b-\u200f\u202a-\u202e\u2060\ufeff]/g, '').trim();
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

const installedAppsHeaders = [
    createSimpleHeader("App Name", "name"),
    createSimpleHeader("Version", "version"),
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
    const [currentPageLogs, setCurrentPageLogs] = useState([]);
    const [logsLoading, setLogsLoading] = useState(false);
    const [exportLoading, setExportLoading] = useState(false);
    const [pageStack, setPageStack] = useState([null]); // [null, afterId_p0, afterId_p1, ...]
    const [pageIndex, setPageIndex] = useState(0);
    const [hasMore, setHasMore] = useState(false);
    const [totalCount, setTotalCount] = useState(0);
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
        if (!lastId) return; // no cursor — can't advance safely
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
            const txt = [...logs].reverse()
                .map(l => `[${func.epochToDateTime(l.timestamp)}] [${(l.level || 'INFO').toUpperCase()}] ${l.log || l.message || ''}`)
                .join('\n');
            const blob = new Blob([txt], { type: 'text/plain' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `${logSourceRef.current}-${selectedAgent.agentId}.log`;
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
        setCurrentPageLogs([]);
        setLogsLoading(false);
        setPageStack([null]);
        setPageIndex(0);
        setHasMore(false);
        setTotalCount(0);
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

    const installedAppsTableData = useMemo(() =>
        [...(selectedAgent?.installedApps || [])]
            .sort((a, b) => (a.name || '').localeCompare(b.name || ''))
            .map(app => ({
                name: sanitizeAppText(app.name) || '\u2014',
                version: app.version ? sanitizeAppText(app.version) : '\u2014',
            })),
    [selectedAgent]);

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
                        disabled={logsLoading || exportLoading}
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
            <Box paddingBlockStart={"4"}>
                <GithubSimpleTable
                    key="installed-apps-table"
                    data={installedAppsTableData}
                    resourceName={{ singular: "app", plural: "apps" }}
                    headers={installedAppsHeaders}
                    headings={installedAppsHeaders}
                    useNewRow={true}
                    condensedHeight={true}
                    hideQueryField={true}
                    loading={false}
                    pageLimit={20}
                />
            </Box>
        ),
        panelID: 'installed-apps-panel',
    };

   
    if (!selectedAgent) return null;

    return (
        <FlyLayout
            show={show}
            setShow={setShow}
            loading={loading}
            title="Endpoint Details"
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
                    tabs={[McpServersTab, DeviceTab, AppsTab, AgentLogsTab, ConfigureTab]}
                    currTab={handleTabChange}
                />
            ]}
        />
    );
}

export default AgentDetails;
