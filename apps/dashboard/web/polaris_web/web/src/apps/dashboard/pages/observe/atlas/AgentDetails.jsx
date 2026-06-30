import { Text, HorizontalStack, VerticalStack, Box, Badge, Button, Icon, Tooltip, Avatar } from "@shopify/polaris"
import { useRef, useMemo, useCallback, useEffect, useState } from "react"
import { useNavigate } from "react-router-dom"
import { motion, AnimatePresence } from 'framer-motion'
import { CaretDownMinor, CodeMinor, DynamicSourceMinor, ClockMinor, CalendarMinor } from '@shopify/polaris-icons'
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
const LOG_TIMESTAMP_WIDTH = "180px";
const LOG_LEVEL_WIDTH = "60px";
const MAX_LOGS_DISPLAYED = 1000;
const MAX_LOGS_FETCHED = 5000;
const LIVE_LOG_LIMIT = 500;

export const LOG_MODES = {
    CURRENT: 'CURRENT',
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
    const liveIntervalRef = useRef(null);

    const [loading, setLoading] = useState(false);
    const [tabLoading, setTabLoading] = useState(false);
    const [mcpServers, setMcpServers] = useState([]);
    const [agentLogs, setAgentLogs] = useState([]);
    const [displayedLogs, setDisplayedLogs] = useState([]);
    const [isLogsExpanded, setIsLogsExpanded] = useState(true);
    const [logMode, setLogMode] = useState(LOG_MODES.HISTORICAL);
    const [description, setDescription] = useState("");
    const [isEditingDescription, setIsEditingDescription] = useState(false);
    const [editableDescription, setEditableDescription] = useState("");

    const stopLiveFetching = useCallback(() => {
        if (liveIntervalRef.current) {
            clearInterval(liveIntervalRef.current);
            liveIntervalRef.current = null;
        }
    }, []);

    const fetchAgentLogs = useCallback(async (agentId, startTime, endTime) => {
        try {
            const logsResponse = await settingRequests.getAgentLogs(agentId, startTime, endTime);
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

    const startLiveFetching = useCallback(async (agentId) => {
        stopLiveFetching();

        let lastFetchTimestamp = Math.floor(Date.now() / 1000);

        liveIntervalRef.current = setInterval(async () => {
            try {
                const now = Math.floor(Date.now() / 1000);
                const newLogs = await fetchAgentLogs(agentId, lastFetchTimestamp, now);
                if (newLogs.length > 0) {
                    setAgentLogs(prevLogs => {
                        const updatedLogs = [...newLogs, ...prevLogs];
                        const uniqueLogs = updatedLogs.filter((log, index, arr) =>
                            arr.findIndex(l => l.timestamp === log.timestamp && l.message === log.message) === index
                        );
                        return uniqueLogs.slice(0, LIVE_LOG_LIMIT);
                    });
                    lastFetchTimestamp = newLogs[0].timestamp;
                }
            } catch (error) {
                console.error("Error in live log fetching:", error);
            }
        }, 10000);

        const now = Math.floor(Date.now() / 1000);
        const oneHourAgo = now - 3600;
        const initialLogs = await fetchAgentLogs(agentId, oneHourAgo, now);
        setAgentLogs(initialLogs.slice(0, LIVE_LOG_LIMIT));
        if (initialLogs.length > 0) {
            lastFetchTimestamp = initialLogs[0].timestamp;
        }
    }, [stopLiveFetching, fetchAgentLogs]);

    const handleLogModeChange = useCallback(async (newMode) => {
        if (logMode === newMode) return;
        setLogMode(newMode);
        if (!selectedAgent) return;
        const wasExpanded = isLogsExpanded;
        setAgentLogs([]);
        setDisplayedLogs([]);
        if (newMode === LOG_MODES.CURRENT) {
            await startLiveFetching(selectedAgent.agentId);
        } else {
            stopLiveFetching();
            const historicalLogs = await fetchAgentLogs(selectedAgent.agentId, startTimestamp, endTimestamp);
            setAgentLogs(historicalLogs);
        }
        setIsLogsExpanded(wasExpanded);
    }, [logMode, selectedAgent, isLogsExpanded, startLiveFetching, stopLiveFetching, fetchAgentLogs, startTimestamp, endTimestamp]);

    const handleSaveDescription = useCallback(() => {
        setDescription(editableDescription);
        setIsEditingDescription(false);
        func.setToast(true, false, "Description saved");
    }, [editableDescription]);

    // Reset state when agent changes and fetch data for the default first tab (MCP Servers).
    useEffect(() => {
        if (!selectedAgent || !show) return;

        setMcpServers([]);
        setAgentLogs([]);
        setDisplayedLogs([]);
        setLogMode(LOG_MODES.HISTORICAL);
        setDescription("");
        setEditableDescription("");
        setIsEditingDescription(false);
        stopLiveFetching();

        setLoading(true);
        settingRequests.getMcpServersByAgent(selectedAgent.agentId, selectedAgent.hostname)
            .then(res => setMcpServers(res.mcpServers || []))
            .catch(() => setMcpServers([]))
            .finally(() => setLoading(false));
    }, [selectedAgent, show, stopLiveFetching]);

    // Stop live fetching when flyout closes.
    useEffect(() => {
        if (!show) stopLiveFetching();
    }, [show, stopLiveFetching]);

    // Cleanup on unmount.
    useEffect(() => {
        return () => stopLiveFetching();
    }, [stopLiveFetching]);

    // Sync displayedLogs from agentLogs.
    useEffect(() => {
        if (agentLogs.length === 0 || !show) {
            setDisplayedLogs([]);
            return;
        }
        setDisplayedLogs(agentLogs.slice(0, MAX_LOGS_DISPLAYED));
    }, [agentLogs, show]);

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
            case 'agent-logs':
                stopLiveFetching();
                setAgentLogs([]);
                setDisplayedLogs([]);
                if (logMode === LOG_MODES.CURRENT) {
                    await startLiveFetching(selectedAgent.agentId);
                } else {
                    const logs = await fetchAgentLogs(selectedAgent.agentId, startTimestamp, endTimestamp);
                    setAgentLogs(logs);
                }
                break;
            default:
                break;
        }
    }, [selectedAgent, logMode, startLiveFetching, stopLiveFetching, fetchAgentLogs, startTimestamp, endTimestamp]);

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
        if (!agentLogs || agentLogs.length === 0) {
            return (
                <Box padding="4" background="bg-surface">
                    <Text variant="bodyMd" color="subdued" alignment="center">No logs found</Text>
                </Box>
            );
        }

        if (!displayedLogs || displayedLogs.length === 0) {
            return (
                <Box padding="4" background="bg-surface">
                    <Text variant="bodyMd" color="subdued">Loading logs...</Text>
                </Box>
            );
        }

        return (
            <div className={`rounded-lg overflow-hidden border border-[#C9CCCF] bg-[#F6F6F7] p-2 flex flex-col ${isLogsExpanded ? "gap-1" : "gap-0"}`}>
                <HorizontalStack gap="2" align="space-between" wrap={false}>
                    <Button
                        variant="plain"
                        onClick={() => setIsLogsExpanded(!isLogsExpanded)}
                        textAlign="left"
                        style={{ backgroundColor: '#F6F6F7' }}
                    >
                        <HorizontalStack gap="2" align="start">
                            <motion.div animate={{ rotate: isLogsExpanded ? 0 : 270 }} transition={{ duration: ANIMATION_DURATION }}>
                                <CaretDownMinor height={20} width={20} />
                            </motion.div>
                            <Text as="dd">Agent</Text>
                        </HorizontalStack>
                    </Button>
                    <HorizontalStack gap="1">
                        <Button
                            size="micro"
                            pressed={logMode === LOG_MODES.CURRENT}
                            onClick={(e) => { e.stopPropagation(); handleLogModeChange(LOG_MODES.CURRENT); }}
                        >
                            Live
                        </Button>
                        <Button
                            size="micro"
                            pressed={logMode === LOG_MODES.HISTORICAL}
                            onClick={(e) => { e.stopPropagation(); handleLogModeChange(LOG_MODES.HISTORICAL); }}
                        >
                            All Time
                        </Button>
                    </HorizontalStack>
                </HorizontalStack>

                <AnimatePresence>
                    <motion.div
                        animate={isLogsExpanded ? "open" : "closed"}
                        variants={{
                            open: { height: "auto", opacity: 1 },
                            closed: { height: 0, opacity: 0 }
                        }}
                        transition={{ duration: ANIMATION_DURATION }}
                        className="overflow-hidden"
                    >
                        <div className="bg-[#F6F6F7] max-h-[45vh] overflow-auto ml-2.5 pt-0 space-y-1 border-l border-[#D2D5D8]">
                            <AnimatePresence initial={false}>
                                {displayedLogs.map((log, index) => (
                                    <motion.div
                                        key={`${log.timestamp}-${log.message}-${index}`}
                                        initial={logMode === LOG_MODES.CURRENT ? { opacity: 0, y: -5 } : false}
                                        animate={{ opacity: 1, y: 0 }}
                                        transition={{ duration: 0.3, ease: "easeOut" }}
                                        className="ml-3 p-0.5 hover:bg-[var(--background-selected)]"
                                    >
                                        <HorizontalStack gap="3" align="start">
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
                    </motion.div>
                </AnimatePresence>
            </div>
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
                    tabs={[McpServersTab, AgentLogsTab, ConfigureTab]}
                    currTab={handleTabChange}
                />
            ]}
        />
    );
}

export default AgentDetails;
