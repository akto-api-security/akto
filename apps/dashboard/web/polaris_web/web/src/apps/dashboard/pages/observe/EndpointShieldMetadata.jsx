import { Text, HorizontalStack, VerticalStack, Box, Badge, Button, Icon, Tooltip, Avatar } from "@shopify/polaris"
import { useEffect, useReducer, useState, useRef } from "react"
import { useNavigate } from "react-router-dom"
import { motion, AnimatePresence } from 'framer-motion'
import { CaretDownMinor, CodeMinor, DynamicSourceMinor, EmailMajor, ClockMinor, CalendarMinor } from '@shopify/polaris-icons'
import InlineEditableText from "../../components/shared/InlineEditableText"
import values from "@/util/values";
import {produce} from "immer"
import func from "@/util/func"
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "../../components/tables/GithubServerTable";
import GithubSimpleTable from "../../components/tables/GithubSimpleTable";
import { CellType } from "../../components/tables/rows/GithubRow";
import { getMcpEndpointShieldData, getMcpServersByAgent, getAgentLogs } from "./dummyData";
import PersistStore from "../../../main/PersistStore";
import { mapLabel } from "../../../main/labelHelper";
import FlyLayout from "../../components/layouts/FlyLayout";
import LayoutWithTabs from "../../components/layouts/LayoutWithTabs";

const headings = [
    {
        text: "Agent ID",
        value: "agentId",
        title: "Agent ID",
        type: CellType.TEXT,
        sortActive: true,
        sortKey: 'agentId'
    },
    {
        text: "Device ID",
        value: "deviceId",
        title: "Device ID",
        type: CellType.TEXT,
        sortActive: true,
        sortKey: 'deviceId'
    },
    {
        text: "Username",
        value: "username",
        title: "Username",
        type: CellType.TEXT,
        sortActive: true,
        sortKey: 'username'
    },
    {
        text: "Email",
        value: "email",
        title: "Email",
        type: CellType.TEXT,
        sortActive: true,
        sortKey: 'email'
    },
    {
        title: 'Last Heartbeat',
        text: "Last Heartbeat",
        value: "lastHeartbeatComp",
        sortActive: true,
        sortKey: 'lastHeartbeat',
        type: CellType.TEXT
    },
    {
        title: 'Last Deployed',
        text: "Last Deployed",
        value: "lastDeployedComp",
        sortKey: 'lastDeployed',
        sortActive: true,
        type: CellType.TEXT
    }
]

const sortOptions = [
    { label: 'Agent ID', value: 'agentId desc', directionLabel: 'Z-A', sortKey: 'agentId', columnIndex: 1 },
    { label: 'Agent ID', value: 'agentId asc', directionLabel: 'A-Z', sortKey: 'agentId', columnIndex: 1 },
    { label: 'Device ID', value: 'deviceId desc', directionLabel: 'Z-A', sortKey: 'deviceId', columnIndex: 2 },
    { label: 'Device ID', value: 'deviceId asc', directionLabel: 'A-Z', sortKey: 'deviceId', columnIndex: 2 },
    { label: 'Username', value: 'username desc', directionLabel: 'Z-A', sortKey: 'username', columnIndex: 3 },
    { label: 'Username', value: 'username asc', directionLabel: 'A-Z', sortKey: 'username', columnIndex: 3 },
    { label: 'Email', value: 'email desc', directionLabel: 'Z-A', sortKey: 'email', columnIndex: 4 },
    { label: 'Email', value: 'email asc', directionLabel: 'A-Z', sortKey: 'email', columnIndex: 4 },
    { label: 'Last Heartbeat', value: 'lastHeartbeat desc', directionLabel: 'Newest', sortKey: 'lastHeartbeat', columnIndex: 5 },
    { label: 'Last Heartbeat', value: 'lastHeartbeat asc', directionLabel: 'Oldest', sortKey: 'lastHeartbeat', columnIndex: 5 },
    { label: 'Last Deployed', value: 'lastDeployed desc', directionLabel: 'Newest', sortKey: 'lastDeployed', columnIndex: 6 },
    { label: 'Last Deployed', value: 'lastDeployed asc', directionLabel: 'Oldest', sortKey: 'lastDeployed', columnIndex: 6 },
];

const filters = [
    {
        key: 'username',
        label: 'Username',
        title: 'Username',
        choices: [],
    },
    {
        key: 'email',
        label: 'Email',
        title: 'Email',
        choices: [],
    }
]

const resourceName = {
    singular: 'agent',
    plural: 'agents',
};

const convertDataIntoTableFormat = (agentData) => {
    let temp = {...agentData}
    temp['lastHeartbeatComp'] = func.prettifyEpoch(temp?.lastHeartbeat)
    temp['lastDeployedComp'] = func.prettifyEpoch(temp?.lastDeployed)
    return temp;
}

// Use fixed dummy data from external file
const generateDummyData = () => {
    return getMcpEndpointShieldData();
}

// Reusable component for rendering metadata fields with icon and tooltip
const MetadataField = ({ icon, tooltip, value }) => (
    <HorizontalStack wrap={false} gap="1">
        <div style={{ maxWidth: "1rem", maxHeight: "1rem" }}>
            <Tooltip content={tooltip} dismissOnMouseOut>
                <Icon source={icon} color="subdued" />
            </Tooltip>
        </div>
        <Text variant="bodySm" color="subdued">
            {value}
        </Text>
    </HorizontalStack>
);

function EndpointShieldMetadata() {
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
    const [description, setDescription] = useState("");
    const [isEditingDescription, setIsEditingDescription] = useState(false);
    const [editableDescription, setEditableDescription] = useState("");
    const copyRef = useRef(null);

    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, null, 2)
    }

    // Save description for the selected agent
    const handleSaveDescription = () => {
        setDescription(editableDescription);
        setIsEditingDescription(false);
        func.setToast(true, false, "Description saved");
    };

    // Handle agent row click to open flyout with details
    const handleRowClick = (agent) => {
        setSelectedAgent(agent);
        const servers = getMcpServersByAgent(agent.agentId, agent.deviceId);
        const logs = getAgentLogs(agent.agentId);
        setMcpServers(servers);
        // Reverse logs array so oldest appears first in the UI
        const reversedLogs = [...logs].reverse();
        setAgentLogs(reversedLogs);
        setDisplayedLogs([]);
        setDescription("");
        setEditableDescription("");
        setIsEditingDescription(false);
        setShowFlyout(true);
    };

    // Simulate live log streaming
    useEffect(() => {
        if (agentLogs.length === 0 || !showFlyout) {
            return;
        }

        let currentIndex = 0;
        const interval = setInterval(() => {
            if (currentIndex < agentLogs.length) {
                setDisplayedLogs(prev => [...prev, agentLogs[currentIndex]]);
                currentIndex++;
            } else {
                clearInterval(interval);
            }
        }, 500); // Add one log every 500ms

        return () => clearInterval(interval);
    }, [agentLogs, showFlyout]);

    const renderLogs = () => {
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
                <Button
                    variant="plain"
                    fullWidth
                    onClick={() => setIsLogsExpanded(!isLogsExpanded)}
                    textAlign="left"
                    style={{ backgroundColor: '#F6F6F7' }}
                >
                    <HorizontalStack gap="2" align="start">
                        <motion.div animate={{ rotate: isLogsExpanded ? 0 : 270 }} transition={{ duration: 0.2 }}>
                            <CaretDownMinor height={20} width={20} />
                        </motion.div>
                        <HorizontalStack gap="2" align="start">
                            <Text as="dd">
                                Agent
                            </Text>
                            <Badge tone="info">Current</Badge>
                        </HorizontalStack>
                    </HorizontalStack>
                </Button>

                <AnimatePresence>
                    <motion.div
                        animate={isLogsExpanded ? "open" : "closed"}
                        variants={{
                            open: { height: "auto", opacity: 1 },
                            closed: { height: 0, opacity: 0 }
                        }}
                        transition={{ duration: 0.2 }}
                        className="overflow-hidden"
                    >
                        <div
                            className="bg-[#F6F6F7] max-h-[45vh] overflow-auto ml-2.5 pt-0 space-y-1 border-l border-[#D2D5D8]"
                        >
                            <AnimatePresence initial={false}>
                                {displayedLogs.map((log, index) => (
                                    <motion.div
                                        key={`${index}-${log.message}`}
                                        initial={{ opacity: 0, y: -10 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        transition={{ duration: 0.2 }}
                                        className="ml-3 p-0.5 hover:bg-[var(--background-selected)]"
                                    >
                                        <HorizontalStack gap="3" align="start">
                                            <Box minWidth="180px">
                                                <Text variant="bodySm" fontWeight="medium" tone="subdued">
                                                    {func.epochToDateTime(log.timestamp)}
                                                </Text>
                                            </Box>
                                            <Box minWidth="60px">
                                                <Badge
                                                    size="small"
                                                    tone={log.level === 'INFO' ? 'info' : log.level === 'WARNING' ? 'warning' : 'critical'}
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

    const mcpServersHeaders = [
        { text: "Server Name", title: "Server Name", value: "serverName" },
        { text: "Server URL", title: "Server URL", value: "serverUrl" },
        { text: "Last Seen", title: "Last Seen", value: "lastSeenFormatted" }
    ];

    const mcpServersTableData = mcpServers.map(server => ({
        serverName: server.serverName,
        serverUrl: server.serverUrl,
        lastSeenFormatted: func.prettifyEpoch(server.lastSeen),
        lastSeen: server.lastSeen,
        collectionName: server.collectionName
    }));

    // Navigate to MCP collection page when server is clicked
    const handleServerClick = (server) => {
        const collection = allCollections.find(col => col.name === server.collectionName);
        if (collection) {
            navigate(`/dashboard/observe/inventory/${collection.id}`);
        } else {
            func.setToast(true, true, `Collection "${server.collectionName}" not found`);
        }
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
                    loading={false}
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

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);
        let ret = []
        let total = 0;

        try {
            // Generate dummy data
            const allDummyData = generateDummyData();

            // Apply filters
            let filteredData = allDummyData.filter(agent => {
                // Date range filter
                if (agent.lastHeartbeat < startTimestamp || agent.lastHeartbeat > endTimestamp) {
                    return false;
                }

                // Username filter
                if (filters.username && filters.username.length > 0 && !filters.username.includes(agent.username)) {
                    return false;
                }

                // Email filter
                if (filters.email && filters.email.length > 0 && !filters.email.includes(agent.email)) {
                    return false;
                }

                // Search query
                if (queryValue) {
                    const searchLower = queryValue.toLowerCase();
                    const matchesSearch =
                        agent.agentId.toLowerCase().includes(searchLower) ||
                        agent.deviceId.toLowerCase().includes(searchLower) ||
                        agent.username.toLowerCase().includes(searchLower) ||
                        agent.email.toLowerCase().includes(searchLower);
                    if (!matchesSearch) return false;
                }

                return true;
            });

            // Apply sorting
            if (sortKey) {
                filteredData.sort((a, b) => {
                    let aVal = a[sortKey];
                    let bVal = b[sortKey];

                    if (typeof aVal === 'string') {
                        aVal = aVal.toLowerCase();
                        bVal = bVal.toLowerCase();
                    }

                    if (sortOrder === 'asc') {
                        return aVal > bVal ? 1 : -1;
                    } else {
                        return aVal < bVal ? 1 : -1;
                    }
                });
            }

            total = filteredData.length;

            // Apply pagination
            const paginatedData = filteredData.slice(skip, skip + limit);

            ret = paginatedData.map(agent => convertDataIntoTableFormat(agent));

        } catch (error) {
            console.error("Error fetching MCP Endpoint Shield metadata:", error)
        }

        setLoading(false);
        return {value: ret, total: total};
    }

    useEffect(() => {
        // Populate filter choices with unique values from dummy data
        const dummyData = generateDummyData();
        const uniqueUsernames = [...new Set(dummyData.map(a => a.username))];
        const uniqueEmails = [...new Set(dummyData.map(a => a.email))];

        filters[0].choices = uniqueUsernames.map(username => ({ label: username, value: username }));
        filters[1].choices = uniqueEmails.map(email => ({ label: email, value: email }));
    }, [])

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
                        key={startTimestamp + endTimestamp}
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
                                        <MetadataField
                                            icon={CodeMinor}
                                            tooltip="Agent ID"
                                            value={selectedAgent.agentId}
                                        />
                                        <MetadataField
                                            icon={DynamicSourceMinor}
                                            tooltip="Device ID"
                                            value={selectedAgent.deviceId}
                                        />
                                        <MetadataField
                                            icon={EmailMajor}
                                            tooltip="Email"
                                            value={selectedAgent.email}
                                        />
                                        <MetadataField
                                            icon={ClockMinor}
                                            tooltip="Last Heartbeat"
                                            value={func.prettifyEpoch(selectedAgent.lastHeartbeat)}
                                        />
                                        <MetadataField
                                            icon={CalendarMinor}
                                            tooltip="Last Deployed"
                                            value={func.prettifyEpoch(selectedAgent.lastDeployed)}
                                        />
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
