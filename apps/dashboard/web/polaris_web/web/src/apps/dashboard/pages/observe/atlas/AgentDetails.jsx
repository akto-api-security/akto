import { Text, HorizontalStack, VerticalStack, Box, Badge, Button, Icon, Tooltip, Avatar, List, Spinner } from "@shopify/polaris"
import { useRef, useMemo, useCallback, useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { motion, AnimatePresence } from 'framer-motion'
import { CaretDownMinor, CodeMinor, DynamicSourceMinor, ClockMinor, CalendarMinor, ExportMinor } from '@shopify/polaris-icons'
import InlineEditableText from "../../../components/shared/InlineEditableText"
import func from "@/util/func"
import FlyLayout from "../../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../../components/layouts/LayoutWithTabs";
import GithubSimpleTable from "../../../components/tables/GithubSimpleTable";
import { DEFAULT_VALUE } from "../api_collections/endpointShieldHelper";
import ModuleEnvConfigComponent from "../../settings/health_logs/ModuleEnvConfig";
import TitleWithInfo from "../../../components/shared/TitleWithInfo";
import settingRequests from "../../settings/api";
import transform from "../transform"

const ANIMATION_DURATION = 0.2;
const LOG_LEVEL_TONES = {
    INFO: 'info',
    WARNING: 'warning',
    ERROR: 'critical'
};
const ICON_SIZE = { maxWidth: "1rem", maxHeight: "1rem" };
const LOG_TIMESTAMP_WIDTH = "180px";
const LOG_LEVEL_WIDTH = "60px";
// const MAX_LOGS_DISPLAYED = 1000;
const MAX_LOGS_FETCHED = 5000;
// const LIVE_LOG_LIMIT = 500; // Live mode disabled

// const INITIAL_WINDOW_HOURS = 6; // time-window approach replaced by count-based pagination
// const WINDOW_STEP_HOURS = 6;
const PAGE_SIZE = 500; // logs displayed per page

export const LOG_MODES = {
    // CURRENT: 'CURRENT', // Live mode disabled
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
    { icon: CalendarMinor, tooltip: "Last Deployed", value: func.prettifyEpoch(agent.lastDeployed) }
];

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
    // const liveIntervalRef = useRef(null); // Live mode disabled

    const [loading, setLoading] = useState(false);
    const [tabLoading, setTabLoading] = useState(false);
    const [mcpServers, setMcpServers] = useState([]);
    const [userAnalysis, setUserAnalysis] = useState(null);
    const [agentLogs, setAgentLogs] = useState([]);
    // const [displayedLogs, setDisplayedLogs] = useState([]); // replaced by agentLogs directly
    // const [isLogsExpanded, setIsLogsExpanded] = useState(true); // collapse removed
    const [logsLoading, setLogsLoading] = useState(false);
    const [displayedCount, setDisplayedCount] = useState(PAGE_SIZE);
    const [lastFetchEndTime, setLastFetchEndTime] = useState(null);
    const [noNewLogsFound, setNoNewLogsFound] = useState(false);
    const [logMode, setLogMode] = useState(LOG_MODES.HISTORICAL);
    const [selectedLogSource, setSelectedLogSource] = useState('all');
    const logSourceRef = useRef('all');
    // const [windowStartTime, setWindowStartTime] = useState(null); // time-window replaced by count-based
    // const [windowEndTime, setWindowEndTime] = useState(null);
    const [description, setDescription] = useState("");
    const [isEditingDescription, setIsEditingDescription] = useState(false);
    const [editableDescription, setEditableDescription] = useState("");

    // Live mode disabled — stopLiveFetching kept for reference
    // const stopLiveFetching = useCallback(() => {
    //     if (liveIntervalRef.current) {
    //         clearInterval(liveIntervalRef.current);
    //         liveIntervalRef.current = null;
    //     }
    // }, []);

    const fetchAgentLogs = useCallback(async (agentId, startTime, endTime) => {
        try {
            const logKey = logSourceRef.current !== 'all' ? logSourceRef.current : null;
            const logsResponse = await settingRequests.getAgentLogs(agentId, startTime, endTime, logKey);
            const transformedLogs = (logsResponse.agentLogs || []).map(log => ({
                timestamp: log.timestamp,
                level: log.level || 'INFO',
                message: log.log || log.message
            }));
            let sortedLogs = transformedLogs.sort((a, b) => b.timestamp - a.timestamp);
            if (sortedLogs.length > MAX_LOGS_FETCHED) {
                sortedLogs = sortedLogs.slice(0, MAX_LOGS_FETCHED);
            }
            return sortedLogs;
        } catch (error) {
            console.error("Error fetching agent logs:", error);
            return [];
        }
    }, []);

    // Live mode disabled
    // const startLiveFetching = useCallback(async (agentId) => {
    //     stopLiveFetching();
    //     let lastFetchTimestamp = Math.floor(Date.now() / 1000);
    //     liveIntervalRef.current = setInterval(async () => {
    //         try {
    //             const now = Math.floor(Date.now() / 1000);
    //             const newLogs = await fetchAgentLogs(agentId, lastFetchTimestamp, now);
    //             if (newLogs.length > 0) {
    //                 setAgentLogs(prevLogs => {
    //                     const updatedLogs = [...newLogs, ...prevLogs];
    //                     const uniqueLogs = updatedLogs.filter((log, index, arr) =>
    //                         arr.findIndex(l => l.timestamp === log.timestamp && l.message === log.message) === index
    //                     );
    //                     return uniqueLogs.slice(0, LIVE_LOG_LIMIT);
    //                 });
    //                 lastFetchTimestamp = newLogs[0].timestamp;
    //             }
    //         } catch (error) {
    //             console.error("Error in live log fetching:", error);
    //         }
    //     }, 10000);
    //     const now = Math.floor(Date.now() / 1000);
    //     const oneHourAgo = now - 3600;
    //     const initialLogs = await fetchAgentLogs(agentId, oneHourAgo, now);
    //     setAgentLogs(initialLogs.slice(0, LIVE_LOG_LIMIT));
    //     if (initialLogs.length > 0) {
    //         lastFetchTimestamp = initialLogs[0].timestamp;
    //     }
    // }, [stopLiveFetching, fetchAgentLogs]);

    // Live mode disabled — handleLogModeChange kept for reference
    // const handleLogModeChange = useCallback(async (newMode) => {
    //     if (logMode === newMode) return;
    //     setLogMode(newMode);
    //     if (!selectedAgent) return;
    //     const wasExpanded = isLogsExpanded;
    //     setAgentLogs([]);
    //     setDisplayedLogs([]);
    //     if (newMode === LOG_MODES.CURRENT) {
    //         await startLiveFetching(selectedAgent.agentId);
    //     } else {
    //         stopLiveFetching();
    //         const historicalLogs = await fetchAgentLogs(selectedAgent.agentId, startTimestamp, endTimestamp);
    //         setAgentLogs(historicalLogs);
    //     }
    //     setIsLogsExpanded(wasExpanded);
    // }, [logMode, selectedAgent, isLogsExpanded, startLiveFetching, stopLiveFetching, fetchAgentLogs, startTimestamp, endTimestamp]);

    const handleSourceChange = useCallback(async (source) => {
        if (logSourceRef.current === source) return;
        setSelectedLogSource(source);
        logSourceRef.current = source;
        if (!selectedAgent) return;
        setAgentLogs([]);
        setDisplayedCount(PAGE_SIZE);
        setNoNewLogsFound(false);
        // Live mode disabled — always historical
        // if (logMode === LOG_MODES.CURRENT) {
        //     await startLiveFetching(selectedAgent.agentId);
        // } else {
        setLogsLoading(true);
        try {
            const logs = await fetchAgentLogs(selectedAgent.agentId, startTimestamp, endTimestamp);
            setAgentLogs(logs);
            setNoNewLogsFound(true); // just loaded everything — nothing newer right now
        } finally {
            setLogsLoading(false);
        }
        // }
    }, [selectedAgent, startTimestamp, endTimestamp, fetchAgentLogs]);

    // handleLoadOlder: purely frontend — shows the next page from already-fetched agentLogs
    const handleLoadOlder = useCallback(() => {
        setDisplayedCount(prev => Math.min(prev + PAGE_SIZE, agentLogs.length));
    }, [agentLogs.length]);

    // handleLoadNewer: API call to fetch logs newer than the most recent one we have
    const handleLoadNewer = useCallback(async () => {
        if (!selectedAgent) return;
        const nowEpoch = Math.floor(Date.now() / 1000);
        // +1 to exclude the boundary log we already have, so we only get genuinely newer entries
        const newerStart = agentLogs.length > 0 ? agentLogs[0].timestamp + 1 : lastFetchEndTime;
        if (!newerStart) return;
        setLogsLoading(true);
        try {
            const newerLogs = await fetchAgentLogs(selectedAgent.agentId, newerStart, nowEpoch);
            if (newerLogs.length > 0) {
                setAgentLogs(prev => {
                    const combined = [...newerLogs, ...prev];
                    const unique = combined.filter((log, idx, arr) =>
                        arr.findIndex(l => l.timestamp === log.timestamp && l.message === log.message) === idx
                    );
                    return unique.sort((a, b) => b.timestamp - a.timestamp);
                });
                setNoNewLogsFound(false); // new logs found — keep button enabled
            } else {
                setNoNewLogsFound(true); // nothing newer right now — disable button
            }
            setLastFetchEndTime(nowEpoch);
        } finally {
            setLogsLoading(false);
        }
    }, [selectedAgent, agentLogs, lastFetchEndTime, fetchAgentLogs]);

    const handleExportLogs = useCallback(() => {
        if (!agentLogs || agentLogs.length === 0) return;
        const logsToExport = agentLogs; // export all fetched logs, not just the currently displayed page
        const txt = logsToExport
            .map(l => `[${func.epochToDateTime(l.timestamp)}] [${(l.level || 'INFO').padEnd(7)}] ${l.message || ''}`)
            .join('\n');
        const blob = new Blob([txt], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `agent-logs-${selectedAgent?.agentId || 'export'}.txt`;
        a.click();
        URL.revokeObjectURL(url);
    }, [agentLogs, displayedCount, selectedAgent]);

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
        setAgentLogs([]);
        // setDisplayedLogs([]); // replaced
        setLogsLoading(false);
        setDisplayedCount(PAGE_SIZE);
        setLastFetchEndTime(null);
        setNoNewLogsFound(false);
        setLogMode(LOG_MODES.HISTORICAL);
        setSelectedLogSource('all');
        logSourceRef.current = 'all';
        // setWindowStartTime(null); // time-window replaced by count-based
        // setWindowEndTime(null);
        setDescription("");
        setEditableDescription("");
        setIsEditingDescription(false);
        // stopLiveFetching(); // Live mode disabled

        setLoading(true);
        settingRequests.getMcpServersByAgent(selectedAgent.agentId, selectedAgent.hostname)
            .then(res => setMcpServers(res.mcpServers || []))
            .catch(() => setMcpServers([]))
            .finally(() => setLoading(false));
    }, [selectedAgent, show]);

    // Live mode disabled — stop-on-close and unmount cleanup no longer needed
    // useEffect(() => {
    //     if (!show) stopLiveFetching();
    // }, [show, stopLiveFetching]);
    // useEffect(() => {
    //     return () => stopLiveFetching();
    // }, [stopLiveFetching]);

    // displayedLogs sync removed — renderLogs() now reads agentLogs directly
    // useEffect(() => {
    //     if (agentLogs.length === 0 || !show) {
    //         setDisplayedLogs([]);
    //         return;
    //     }
    //     setDisplayedLogs(agentLogs.slice(0, MAX_LOGS_DISPLAYED));
    // }, [agentLogs, show]);

    // Re-enable "Load Newer" 30 seconds after it was disabled (so user can check again without switching filters)
    useEffect(() => {
        if (!noNewLogsFound) return;
        const timer = setTimeout(() => setNoNewLogsFound(false), 30000);
        return () => clearTimeout(timer);
    }, [noNewLogsFound]);

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
                // Live mode disabled
                // stopLiveFetching();
                setAgentLogs([]);
                setDisplayedCount(PAGE_SIZE);
                setLastFetchEndTime(endTimestamp);
                setNoNewLogsFound(false);
                // setDisplayedLogs([]); // replaced
                // if (logMode === LOG_MODES.CURRENT) {
                //     await startLiveFetching(selectedAgent.agentId);
                // } else {
                setLogsLoading(true);
                try {
                    const logs = await fetchAgentLogs(selectedAgent.agentId, startTimestamp, endTimestamp);
                    setAgentLogs(logs);
                    setNoNewLogsFound(true); // just loaded everything — nothing newer right now
                } finally {
                    setLogsLoading(false);
                }
                // }
                break;
            }
            default:
                break;
        }
    }, [selectedAgent, fetchAgentLogs, startTimestamp, endTimestamp]);

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

    // Old renderLogs — replaced by new version below
    // const renderLogs_OLD = () => {
    //     if (!agentLogs || agentLogs.length === 0) {
    //         return (
    //             <Box padding="4" background="bg-surface">
    //                 <Text variant="bodyMd" color="subdued" alignment="center">No logs found</Text>
    //             </Box>
    //         );
    //     }
    //     if (!displayedLogs || displayedLogs.length === 0) {
    //         return (
    //             <Box padding="4" background="bg-surface">
    //                 <Text variant="bodyMd" color="subdued">Loading logs...</Text>
    //             </Box>
    //         );
    //     }
    //     return (
    //         <div className={`rounded-lg overflow-hidden border border-[#C9CCCF] bg-[#F6F6F7] p-2 flex flex-col ${isLogsExpanded ? "gap-1" : "gap-0"}`}>
    //             <HorizontalStack gap="2" align="space-between" wrap={false}>
    //                 <Button variant="plain" onClick={() => setIsLogsExpanded(!isLogsExpanded)} textAlign="left" style={{ backgroundColor: '#F6F6F7' }}>
    //                     <HorizontalStack gap="2" align="start">
    //                         <motion.div animate={{ rotate: isLogsExpanded ? 0 : 270 }} transition={{ duration: ANIMATION_DURATION }}>
    //                             <CaretDownMinor height={20} width={20} />
    //                         </motion.div>
    //                         <Text as="dd">Agent</Text>
    //                     </HorizontalStack>
    //                 </Button>
    //                 <HorizontalStack gap="3" wrap={false}>
    //                     <HorizontalStack gap="1">
    //                         <Button size="micro" pressed={logMode === LOG_MODES.CURRENT} onClick={(e) => { e.stopPropagation(); handleLogModeChange(LOG_MODES.CURRENT); }}>Live</Button>
    //                         <Button size="micro" pressed={logMode === LOG_MODES.HISTORICAL} onClick={(e) => { e.stopPropagation(); handleLogModeChange(LOG_MODES.HISTORICAL); }}>All Time</Button>
    //                     </HorizontalStack>
    //                     <HorizontalStack gap="1">
    //                         {[{ label: 'All', value: 'all' }, { label: 'Agent', value: 'agent-logs' }, { label: 'Proxy', value: 'proxy-logs' }, { label: 'Install', value: 'installation-logs' }].map(({ label, value }) => (
    //                             <Button key={value} size="micro" pressed={selectedLogSource === value} onClick={(e) => { e.stopPropagation(); handleSourceChange(value); }}>{label}</Button>
    //                         ))}
    //                     </HorizontalStack>
    //                 </HorizontalStack>
    //             </HorizontalStack>
    //             <AnimatePresence>
    //                 <motion.div animate={isLogsExpanded ? "open" : "closed"} variants={{ open: { height: "auto", opacity: 1 }, closed: { height: 0, opacity: 0 } }} transition={{ duration: ANIMATION_DURATION }} className="overflow-hidden">
    //                     <div className="bg-[#F6F6F7] max-h-[45vh] overflow-auto ml-2.5 pt-0 space-y-1 border-l border-[#D2D5D8]">
    //                         <AnimatePresence initial={false}>
    //                             {displayedLogs.map((log, index) => (
    //                                 <motion.div key={`${log.timestamp}-${log.message}-${index}`} initial={logMode === LOG_MODES.CURRENT ? { opacity: 0, y: -5 } : false} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.3, ease: "easeOut" }} className="ml-3 p-0.5 hover:bg-[var(--background-selected)]">
    //                                     <HorizontalStack gap="3" align="start">
    //                                         <Box minWidth={LOG_TIMESTAMP_WIDTH}><Text variant="bodySm" fontWeight="medium" tone="subdued">{func.epochToDateTime(log.timestamp)}</Text></Box>
    //                                         <Box minWidth={LOG_LEVEL_WIDTH}><Badge size="small" tone={LOG_LEVEL_TONES[log.level] || 'info'}>{log.level}</Badge></Box>
    //                                         <Box><Text variant="bodySm" as="p">{log.message}</Text></Box>
    //                                     </HorizontalStack>
    //                                 </motion.div>
    //                             ))}
    //                         </AnimatePresence>
    //                     </div>
    //                 </motion.div>
    //             </AnimatePresence>
    //         </div>
    //     );
    // };

    // count-based pagination — no time-window tracking needed
    const canLoadOlder = displayedCount < agentLogs.length;
    const canLoadNewer = agentLogs.length > 0 && !logsLoading && !noNewLogsFound;

    const renderLogs = () => {
        const logSources = [
            { label: 'All', value: 'all' },
            { label: 'Agent', value: 'agent-logs' },
            { label: 'Proxy', value: 'proxy-logs' },
            { label: 'Install', value: 'installation-logs' },
        ];

        // Live mode toggle — commented out, ready to re-enable
        // const liveModeToggle = (
        //     <HorizontalStack gap="1">
        //         <Button size="micro" pressed={logMode === LOG_MODES.CURRENT}
        //             onClick={(e) => { e.stopPropagation(); handleLogModeChange(LOG_MODES.CURRENT); }}>Live</Button>
        //         <Button size="micro" pressed={logMode === LOG_MODES.HISTORICAL}
        //             onClick={(e) => { e.stopPropagation(); handleLogModeChange(LOG_MODES.HISTORICAL); }}>All Time</Button>
        //     </HorizontalStack>
        // );

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
                <Button
                    size="micro"
                    icon={ExportMinor}
                    disabled={agentLogs.length === 0 || logsLoading}
                    onClick={handleExportLogs}
                >
                    Export
                </Button>
            </HorizontalStack>
        );

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
        } else if (agentLogs.length === 0) {
            logBody = (
                <Box padding="8">
                    <Text variant="bodyMd" color="subdued" alignment="center">No logs found for this time window.</Text>
                </Box>
            );
        } else {
            logBody = (
                <div className="max-h-[55vh] overflow-auto">
                    <AnimatePresence initial={false}>
                        {agentLogs.slice(0, displayedCount).map((log, index) => (
                            <motion.div
                                key={`${log.timestamp}-${log.message}-${index}`}
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
                                    <Box>
                                        <Text variant="bodySm" as="p">{log.message}</Text>
                                    </Box>
                                </HorizontalStack>
                            </motion.div>
                        ))}
                    </AnimatePresence>
                </div>
            );
        }

        const footer = (
            <HorizontalStack align="space-between" wrap={false} gap="2">
                <Button
                    plain
                    disabled={!canLoadOlder || logsLoading}
                    onClick={handleLoadOlder}
                >
                    ← Load Older
                </Button>
                <VerticalStack gap="0" align="center">
                    {agentLogs.length > 0 && (
                        <Text variant="bodySm" color="subdued" alignment="center">
                            {canLoadOlder
                                ? `Showing ${displayedCount} of ${agentLogs.length} logs`
                                : `${agentLogs.length} log${agentLogs.length !== 1 ? 's' : ''}`}
                        </Text>
                    )}
                </VerticalStack>
                <Button
                    plain
                    disabled={!canLoadNewer || logsLoading}
                    onClick={handleLoadNewer}
                >
                    Load Newer →
                </Button>
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
        content: 'Agent Logs',
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
                    tabs={[McpServersTab, UserAnalysisTab, AgentLogsTab, ConfigureTab]}
                    currTab={handleTabChange}
                />
            ]}
        />
    );
}

export default AgentDetails;
