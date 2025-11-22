import { Text, HorizontalStack, VerticalStack, Box, Badge, Button, Icon, Tooltip, Avatar } from "@shopify/polaris"
import { useEffect, useReducer, useState, useRef, useMemo, useCallback } from "react"
import { useNavigate } from "react-router-dom"
import { motion, AnimatePresence } from 'framer-motion'
import { CaretDownMinor, CodeMinor, DynamicSourceMinor, ClockMinor, CalendarMinor } from '@shopify/polaris-icons'
import InlineEditableText from "../../components/shared/InlineEditableText"
import values from "@/util/values";
import {produce} from "immer"
import func from "@/util/func"
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "../../components/tables/GithubServerTable";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import { getAgentLogs } from "./dummyData";
import EndpointShieldMetadataDemo from "./EndpointShieldMetadataDemo";
import settingRequests from "../settings/api";
import PersistStore from "../../../main/PersistStore";
import { mapLabel } from "../../../main/labelHelper";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";

// Helper function to create table heading configuration
const createHeading = (text, value = null, sortKey = null) => ({
    text,
    value: value || text.toLowerCase().replace(/ /g, ''),
    title: text,
    type: CellType.TEXT,
    sortActive: true,
    sortKey: sortKey || (value || text.toLowerCase().replace(/ /g, ''))
});

const headings = [
    createHeading("Agent ID", "agentId"),
    createHeading("Device ID", "deviceId"),
    createHeading("Username", "username"),
    createHeading("Last Heartbeat", "lastHeartbeatComp", "lastHeartbeat"),
    createHeading("Last Deployed", "lastDeployedComp", "lastDeployed")
]

// Helper function to create sort options for a column
const createSortOptions = (label, sortKey, columnIndex, isTimeField = false) => {
    const descLabel = isTimeField ? 'Newest' : 'Z-A';
    const ascLabel = isTimeField ? 'Oldest' : 'A-Z';
    return [
        { label, value: `${sortKey} desc`, directionLabel: descLabel, sortKey, columnIndex },
        { label, value: `${sortKey} asc`, directionLabel: ascLabel, sortKey, columnIndex }
    ];
};

const sortOptions = [
    ...createSortOptions('Agent ID', 'agentId', 1),
    ...createSortOptions('Device ID', 'deviceId', 2),
    ...createSortOptions('Username', 'username', 3),
    ...createSortOptions('Last Heartbeat', 'lastHeartbeat', 4, true),
    ...createSortOptions('Last Deployed', 'lastDeployed', 5, true)
];

// Helper function to create filter configuration
const createFilter = (key, label) => ({
    key,
    label,
    title: label,
    choices: []
});

const resourceName = {
    singular: 'agent',
    plural: 'agents',
};

// Constants for better maintainability
const MODULE_TYPE = {
    MCP_ENDPOINT_SHIELD: 'MCP_ENDPOINT_SHIELD'
};
const LOG_STREAMING_DELAY_MS = 500;
const ANIMATION_DURATION = 0.2;
const LOG_LEVEL_TONES = {
    INFO: 'info',
    WARNING: 'warning',
    ERROR: 'critical'
};
const ICON_SIZE = { maxWidth: "1rem", maxHeight: "1rem" };
const LOG_TIMESTAMP_WIDTH = "180px";
const LOG_LEVEL_WIDTH = "60px";
const DEFAULT_VALUE = '-';

// Log fetching modes
const LOG_MODES = {
    CURRENT: 'CURRENT',
    HISTORICAL: 'HISTORICAL'
};

// Log limits to prevent performance issues
const LOG_LIMITS = {
    MAX_LOGS_DISPLAYED: 1000,   // Maximum logs to display in UI
    MAX_LOGS_FETCHED: 5000,     // Maximum logs to fetch from API
    LIVE_LOG_LIMIT: 500         // Maximum logs to keep in live mode
};

const convertDataIntoTableFormat = (agentData) => {
    return {
        ...agentData,
        id: agentData?.agentId, // Use agentId as the unique identifier for table selection
        lastHeartbeatComp: func.prettifyEpoch(agentData?.lastHeartbeat),
        lastDeployedComp: func.prettifyEpoch(agentData?.lastDeployed)
    };
}

// Reusable component for rendering metadata fields with icon and tooltip
const MetadataField = ({ icon, tooltip, value }) => {
    if (!value || value === DEFAULT_VALUE) {
        return null;
    }

    return (
        <HorizontalStack wrap={false} gap="1">
            <div style={ICON_SIZE}>
                <Tooltip content={tooltip} dismissOnMouseOut>
                    <Icon source={icon} color="subdued" />
                </Tooltip>
            </div>
            <Text variant="bodySm" color="subdued">
                {value}
            </Text>
        </HorizontalStack>
    );
};

// Metadata fields configuration
const getMetadataFields = (agent) => [
    { icon: CodeMinor, tooltip: "Agent ID", value: agent.agentId },
    { icon: DynamicSourceMinor, tooltip: "Device ID", value: agent.deviceId },
    { icon: ClockMinor, tooltip: "Last Heartbeat", value: func.prettifyEpoch(agent.lastHeartbeat) },
    { icon: CalendarMinor, tooltip: "Last Deployed", value: func.prettifyEpoch(agent.lastDeployed) }
];

function EndpointShieldMetadata() {
    const isDemoAccount = func.isDemoAccount();

    // Show demo version for demo accounts
    if (isDemoAccount) {
        return <EndpointShieldMetadataDemo />;
    }

    const navigate = useNavigate();
    const [loading, setLoading] = useState(false);
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5]);
    const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security";
    const allCollections = PersistStore((state) => state.allCollections) || [];
    const [selectedAgent, setSelectedAgent] = useState(null);
    const [showFlyout, setShowFlyout] = useState(false);
    const [mcpServers, setMcpServers] = useState([]);
    const [agentLogs, setAgentLogs] = useState([]);
    const [displayedLogs, setDisplayedLogs] = useState([]);
    const [isLogsExpanded, setIsLogsExpanded] = useState(true);
    const [logMode, setLogMode] = useState(LOG_MODES.HISTORICAL);
    const [isLiveFetching, setIsLiveFetching] = useState(false);
    const [liveInterval, setLiveInterval] = useState(null);
    const [description, setDescription] = useState("");
    const [isEditingDescription, setIsEditingDescription] = useState(false);
    const [editableDescription, setEditableDescription] = useState("");
    const [endpointShieldData, setEndpointShieldData] = useState(null);
    const [filters, setFilters] = useState([
        createFilter('username', 'Username'),
        createFilter('deviceId', 'Device ID')
    ]);
    const copyRef = useRef(null);

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, null, 2)
    }

    // Fetch module info from API
    const fetchModuleInfo = useCallback(async () => {
        try {
            // Fetch only MCP Endpoint Shield modules using filter
            const response = await settingRequests.fetchModuleInfo({
                moduleType: MODULE_TYPE.MCP_ENDPOINT_SHIELD
            });
            const endpointShieldModules = response.moduleInfos || [];

            // Transform module data to match the expected format
            const agents = endpointShieldModules.map(module => {
                const username = module.additionalData?.username || DEFAULT_VALUE;

                return {
                    agentId: module.id,
                    deviceId: module.name,
                    username,
                    lastHeartbeat: module.lastHeartbeatReceived || 0,
                    lastDeployed: module.startedTs || 0,
                    // Store full module data for flyout details
                    _moduleData: module
                };
            });

            setEndpointShieldData({ agents });
        } catch (error) {
            console.error("Error fetching module info:", error);
        }
    }, []);

    useEffect(() => {
        fetchModuleInfo();
    }, [fetchModuleInfo]);

    // Save description for the selected agent
    const handleSaveDescription = useCallback(() => {
        setDescription(editableDescription);
        setIsEditingDescription(false);
        func.setToast(true, false, "Description saved");
    }, [editableDescription]);

    // Fetch agent logs with optional time range
    const fetchAgentLogs = useCallback(async (agentId, startTime, endTime) => {
        try {
            const logsResponse = await settingRequests.getAgentLogs(agentId, startTime, endTime);
            // Transform API response to match expected format
            const transformedLogs = (logsResponse.agentLogs || []).map(log => ({
                timestamp: log.timestamp,
                level: log.level || 'INFO',
                message: log.log || log.message
            }));
            // Sort logs by timestamp (newest first, will be reversed later for display)
            let sortedLogs = transformedLogs.sort((a, b) => b.timestamp - a.timestamp);
            
            // Apply log limit cap
            if (sortedLogs.length > LOG_LIMITS.MAX_LOGS_FETCHED) {
                sortedLogs = sortedLogs.slice(0, LOG_LIMITS.MAX_LOGS_FETCHED);
                console.warn(`Log count exceeded limit. Showing latest ${LOG_LIMITS.MAX_LOGS_FETCHED} logs.`);
            }
            
            return sortedLogs;
        } catch (error) {
            console.error("Error fetching agent logs:", error);
            // Fallback to dummy data if API fails
            const logsData = getAgentLogs(agentId);
            return logsData.slice(0, LOG_LIMITS.MAX_LOGS_FETCHED);
        }
    }, []);

    // Start live log fetching
    const startLiveFetching = useCallback(async (agentId) => {
        if (liveInterval) {
            clearInterval(liveInterval);
        }
        
        setIsLiveFetching(true);
        
        // Track the last timestamp to fetch only new logs
        let lastFetchTimestamp = Math.floor(Date.now() / 1000);
        
        const interval = setInterval(async () => {
            try {
                // Fetch only new logs since last fetch
                const now = Math.floor(Date.now() / 1000);
                const newLogs = await fetchAgentLogs(agentId, lastFetchTimestamp, now);
                
                if (newLogs.length > 0) {
                    setAgentLogs(prevLogs => {
                        // Add new logs to the beginning (they're already sorted newest first)
                        const updatedLogs = [...newLogs, ...prevLogs];
                        

                        const uniqueLogs = updatedLogs.filter((log, index, arr) => 
                            arr.findIndex(l => l.timestamp === log.timestamp && l.message === log.message) === index
                        );
                        
                        // Keep only the latest logs within the limit
                        return uniqueLogs.slice(0, LOG_LIMITS.LIVE_LOG_LIMIT);
                    });
                    
                    // Update last fetch timestamp to the newest log's timestamp
                    lastFetchTimestamp = newLogs[0].timestamp;
                }
            } catch (error) {
                console.error("Error in live log fetching:", error);
            }
        }, 10000); // Fetch every 10 seconds (reduced frequency for better UX)
        
        setLiveInterval(interval);
        
        // Initial fetch for live mode
        const now = Math.floor(Date.now() / 1000);
        const oneHourAgo = now - (60 * 60); // Last hour for initial load
        const initialLogs = await fetchAgentLogs(agentId, oneHourAgo, now);
        setAgentLogs(initialLogs.slice(0, LOG_LIMITS.LIVE_LOG_LIMIT));
        
        if (initialLogs.length > 0) {
            lastFetchTimestamp = initialLogs[0].timestamp;
        }
    }, [liveInterval, fetchAgentLogs]);

    // Stop live log fetching
    const stopLiveFetching = useCallback(() => {
        if (liveInterval) {
            clearInterval(liveInterval);
            setLiveInterval(null);
        }
        setIsLiveFetching(false);
    }, [liveInterval]);

    // Handle log mode change
    const handleLogModeChange = useCallback(async (newMode) => {
        if (logMode === newMode) return; // Prevent unnecessary changes
        
        console.log(`Switching to ${newMode} mode for agent:`, selectedAgent?.agentId);
        setLogMode(newMode);
        if (!selectedAgent) return;
        
        // Keep the logs panel expanded during mode switch
        const wasExpanded = isLogsExpanded;
        
        // Clear existing logs first for immediate feedback
        setAgentLogs([]);
        setDisplayedLogs([]);
        
        if (newMode === LOG_MODES.CURRENT) {
            console.log('Starting live fetching...');
            await startLiveFetching(selectedAgent.agentId);
        } else {
            console.log('Fetching historical logs...', { startTimestamp, endTimestamp });
            stopLiveFetching();
            // Fetch historical logs using date range
            const historicalLogs = await fetchAgentLogs(selectedAgent.agentId, startTimestamp, endTimestamp);
            console.log('Historical logs fetched:', historicalLogs.length);
            setAgentLogs(historicalLogs);
        }
        
        // Ensure logs panel stays in the same state
        setIsLogsExpanded(wasExpanded);
    }, [logMode, selectedAgent, isLogsExpanded, startLiveFetching, stopLiveFetching, fetchAgentLogs, startTimestamp, endTimestamp]);

    // Handle agent row click to open flyout with details
    const handleRowClick = useCallback(async (agent) => {
        setSelectedAgent(agent);

        try {
            // Get MCP servers from API
            const serversResponse = await settingRequests.getMcpServersByAgent(agent.agentId, agent.deviceId);
            const servers = serversResponse.mcpServers || [];
            setMcpServers(servers);
            
            if (servers.length === 0) {
                console.log("No MCP servers found for agent:", agent.agentId);
            }
        } catch (error) {
            console.error("Error fetching MCP servers:", error);
            setMcpServers([]);
        }

        // Fetch logs based on current mode
        if (logMode === LOG_MODES.CURRENT) {
            await startLiveFetching(agent.agentId);
        } else {
            const historicalLogs = await fetchAgentLogs(agent.agentId, startTimestamp, endTimestamp);
            setAgentLogs(historicalLogs);
        }
        setDisplayedLogs([]);
        setDescription("");
        setEditableDescription("");
        setIsEditingDescription(false);
        setShowFlyout(true);
    }, [logMode, startLiveFetching, fetchAgentLogs, startTimestamp, endTimestamp]);

    // Cleanup interval when component unmounts or flyout closes
    useEffect(() => {
        return () => {
            if (liveInterval) {
                clearInterval(liveInterval);
            }
        };
    }, [liveInterval]);

    useEffect(() => {
        if (!showFlyout) {
            stopLiveFetching();
        }
    }, [showFlyout, stopLiveFetching]);

    // Handle log display based on mode
    useEffect(() => {
        if (agentLogs.length === 0 || !showFlyout) {
            setDisplayedLogs([]);
            return;
        }

        if (logMode === LOG_MODES.HISTORICAL) {
            // For historical mode, show all logs immediately (newest first) with display limit
            const limitedLogs = agentLogs.slice(0, LOG_LIMITS.MAX_LOGS_DISPLAYED);
            if (agentLogs.length > LOG_LIMITS.MAX_LOGS_DISPLAYED) {
                console.warn(`Display limit reached. Showing ${LOG_LIMITS.MAX_LOGS_DISPLAYED} out of ${agentLogs.length} logs.`);
            }
            setDisplayedLogs(limitedLogs);
        } else {
            // For current mode, show logs immediately (newest first) - no streaming animation
            const limitedLogs = agentLogs.slice(0, LOG_LIMITS.MAX_LOGS_DISPLAYED);
            if (agentLogs.length > LOG_LIMITS.MAX_LOGS_DISPLAYED) {
                console.warn(`Display limit reached. Showing ${LOG_LIMITS.MAX_LOGS_DISPLAYED} out of ${agentLogs.length} logs.`);
            }
            setDisplayedLogs(limitedLogs);
        }
    }, [agentLogs, showFlyout, logMode]);

    const renderLogs = () => {
        // If no logs were fetched at all, show "No logs found"
        if (!agentLogs || agentLogs.length === 0) {
            return (
                <Box padding="4" background="bg-surface">
                    <Text variant="bodyMd" color="subdued" alignment="center">No logs found</Text>
                </Box>
            );
        }
        
        // If logs exist but none are displayed yet (still streaming), show loading
        if (!displayedLogs || displayedLogs.length === 0) {
            return (
                <Box padding="4" background="bg-surface">
                    <Text variant="bodyMd" color="subdued">Loading logs...</Text>
                </Box>
            );
        }

        return (
            <div
                className={`rounded-lg overflow-hidden border border-[#C9CCCF] bg-[#F6F6F7] p-2 flex flex-col ${isLogsExpanded ? "gap-1" : "gap-0"}`}
            >
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
                            onClick={(e) => {
                                e.stopPropagation();
                                handleLogModeChange(LOG_MODES.CURRENT);
                            }}
                        >
                            Live
                        </Button>
                        <Button 
                            size="micro" 
                            pressed={logMode === LOG_MODES.HISTORICAL}
                            onClick={(e) => {
                                e.stopPropagation();
                                handleLogModeChange(LOG_MODES.HISTORICAL);
                            }}
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
                        <div
                            className="bg-[#F6F6F7] max-h-[45vh] overflow-auto ml-2.5 pt-0 space-y-1 border-l border-[#D2D5D8]"
                        >
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
                                                <Badge
                                                    size="small"
                                                    tone={LOG_LEVEL_TONES[log.level] || 'info'}
                                                >
                                                    {log.level}
                                                </Badge>
                                            </Box>
                                            <Box>
                                                <Text variant="bodySm" as="p">
                                                    {log.message}
                                                </Text>
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

    // Helper function to create simple table header
    const createSimpleHeader = (text, value = null) => ({
        text,
        title: text,
        value: value || text.toLowerCase().replace(/ /g, '')
    });

    const mcpServersHeaders = [
        createSimpleHeader("Server Name", "serverName"),
        createSimpleHeader("Server URL", "serverUrl"),
        createSimpleHeader("Last Updated", "lastSeenFormatted")
    ];

    // Memoize table data transformation to avoid recalculation on every render
    const mcpServersTableData = useMemo(() =>
        mcpServers.map(server => ({
            serverName: server.serverName,
            serverUrl: server.serverUrl,
            lastSeenFormatted: func.prettifyEpoch(server.lastSeen),
            lastSeen: server.lastSeen,
            collectionName: server.collectionName
        })), [mcpServers]);

    // Navigate to MCP collection page when server is clicked
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

    const McpServersTab = {
        id: 'mcp-servers',
        content: 'MCP Servers',
        component: (
            <Box paddingBlockStart={"4"}>
                {mcpServersTableData.length === 0 ? (
                    <Box padding="4" background="bg-surface">
                        <Text variant="bodyMd" color="subdued" alignment="center">No servers found</Text>
                    </Box>
                ) : (
                    <GithubSimpleTable
                        key="mcp-servers-table"
                        data={mcpServersTableData}
                        resourceName={{ singular: "server", plural: "servers" }}
                        headers={mcpServersHeaders}
                        headings={mcpServersHeaders}
                        useNewRow={true}
                        condensedHeight={true}
                        hideQueryField={true}
                        loading={false}
                        pageLimit={10}
                        showFooter={false}
                        onRowClick={handleServerClick}
                        rowClickable={true}
                    />
                )}
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

    const fetchData = useCallback(async (sortKey, sortOrder, skip, limit, filters, _filterOperators, queryValue) => {
        setLoading(true);
        let ret = [];
        let total = 0;

        try {
            // Get agents data from fetched module info
            const agentsData = endpointShieldData?.agents || [];

            // Apply filters
            const filteredData = agentsData.filter(agent => {
                // Date range filter
                if (agent.lastHeartbeat < startTimestamp || agent.lastHeartbeat > endTimestamp) {
                    return false;
                }

                // Username filter
                if (filters.username && filters.username.length > 0 && !filters.username.includes(agent.username)) {
                    return false;
                }

                // Device ID filter
                if (filters.deviceId && filters.deviceId.length > 0 && !filters.deviceId.includes(agent.deviceId)) {
                    return false;
                }

                // Search query
                if (queryValue) {
                    const searchLower = queryValue.toLowerCase();
                    const matchesSearch =
                        agent.agentId?.toLowerCase().includes(searchLower) ||
                        agent.deviceId?.toLowerCase().includes(searchLower) ||
                        agent.username?.toLowerCase().includes(searchLower);
                    if (!matchesSearch) return false;
                }

                return true;
            });

            // Apply sorting
            if (sortKey) {
                filteredData.sort((a, b) => {
                    let aVal = a[sortKey];
                    let bVal = b[sortKey];

                    // Handle null/undefined values - push them to the end
                    if (aVal == null && bVal == null) return 0;
                    if (aVal == null) return 1;
                    if (bVal == null) return -1;

                    if (typeof aVal === 'string' && typeof bVal === 'string') {
                        aVal = aVal.toLowerCase();
                        bVal = bVal.toLowerCase();
                    }

                    return sortOrder === 'asc'
                        ? (aVal > bVal ? 1 : -1)
                        : (aVal < bVal ? 1 : -1);
                });
            }

            total = filteredData.length;

            // Apply pagination
            const paginatedData = filteredData.slice(skip, skip + limit);

            ret = paginatedData.map(agent => convertDataIntoTableFormat(agent));

        } catch (error) {
            console.error("Error fetching MCP Endpoint Shield metadata:", error);
        } finally {
            setLoading(false);
        }

        return { value: ret, total };
    }, [endpointShieldData, startTimestamp, endTimestamp]);

    useEffect(() => {
        // Populate filter choices with unique values from fetched data
        if (endpointShieldData?.agents) {
            const agentsData = endpointShieldData.agents;
            const uniqueUsernames = [...new Set(agentsData.map(a => a.username).filter(Boolean))];
            const uniqueDeviceIds = [...new Set(agentsData.map(a => a.deviceId).filter(Boolean))];

            setFilters([
                {
                    ...createFilter('username', 'Username'),
                    choices: uniqueUsernames.map(username => ({ label: username, value: username }))
                },
                {
                    ...createFilter('deviceId', 'Device ID'),
                    choices: uniqueDeviceIds.map(deviceId => ({ label: deviceId, value: deviceId }))
                }
            ]);
        }
    }, [endpointShieldData])

    const allowBulkActions = window.USER_NAME && window.USER_NAME.endsWith("@akto.io");

    const promotedBulkActions = (selectedAgents) => {
        const actions = [];

        if (allowBulkActions) {
            actions.push({
                content: `Delete ${selectedAgents.length} agent info entr${selectedAgents.length > 1 ? "ies" : "y"}`,
                onAction: async () => {
                    const deleteConfirmationMessage = `Are you sure you want to delete ${selectedAgents.length} agent info entr${selectedAgents.length > 1 ? "ies" : "y"}?`;
                    func.showConfirmationModal(deleteConfirmationMessage, "Delete", async () => {
                        try {
                            await settingRequests.deleteModuleInfo(selectedAgents);
                            func.setToast(true, false, `${selectedAgents.length} agent info entr${selectedAgents.length > 1 ? "ies" : "y"} deleted successfully`);
                            window.location.reload();
                        } catch (error) {
                            console.error("Error deleting agent info:", error);
                            func.setToast(true, true, "Failed to delete agent info");
                        }
                    });
                },
            });
        }

        return actions;
    };

    const primaryActions = (
        <HorizontalStack gap={"2"}>
            <DateRangeFilter
                initialDispatch={currDateRange}
                dispatch={(dateObj) => dispatchCurrDateRange({
                    type: "update",
                    period: dateObj.period,
                    title: dateObj.title,
                    alias: dateObj.alias
                })}
            />
        </HorizontalStack>
    )

    return (
        <>
            <PageWithMultipleCards
                title={
                    <Text as="div" variant="headingLg">
                        {mapLabel("Endpoint Shield", dashboardCategory)}
                    </Text>
                }
                backUrl="/dashboard/observe"
                primaryAction={primaryActions}
                components = {[
                    <GithubServerTable
                        key={startTimestamp + endTimestamp + (endpointShieldData ? "loaded" : "loading") + filters[0]?.choices?.length}
                        headers={headings}
                        resourceName={resourceName}
                        appliedFilters={[]}
                        sortOptions={sortOptions}
                        disambiguateLabel={disambiguateLabel}
                        loading={loading}
                        fetchData={fetchData}
                        filters={filters}
                        hideQueryField={false}
                        useNewRow={true}
                        condensedHeight={true}
                        pageLimit={20}
                        headings={headings}
                        onRowClick={handleRowClick}
                        rowClickable={true}
                        selectable={allowBulkActions}
                        promotedBulkActions={promotedBulkActions}
                    />
                ]}
            />
            {selectedAgent && (
                <FlyLayout
                    show={showFlyout}
                    setShow={setShowFlyout}
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
                            tabs={[McpServersTab, AgentLogsTab]}
                        />
                    ]}
                />
            )}
        </>
    )
}

export default EndpointShieldMetadata
