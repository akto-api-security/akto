import { Text, HorizontalStack } from "@shopify/polaris"
import { useEffect, useReducer, useState, useCallback } from "react"
import values from "@/util/values";
import { produce } from "immer"
import func from "@/util/func"
import DateRangeFilter from "../../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "../../../components/tables/GithubServerTable";
import { CellType } from "../../../components/tables/rows/GithubRow";
import settingRequests from "../../settings/api";
import PersistStore from "../../../../main/PersistStore";
import { mapLabel } from "../../../../main/labelHelper";
import AgentDetails, { LOG_MODES } from "./AgentDetails";
import { MODULE_TYPE, DEFAULT_VALUE } from "../api_collections/endpointShieldHelper";

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
];

const createSortOptions = (label, sortKey, columnIndex, isTimeField = false) => {
    const descLabel = isTimeField ? 'Newest' : 'Z-A';
    const ascLabel = isTimeField ? 'Oldest' : 'A-Z';
    return [
        { label, value: `${sortKey} asc`, directionLabel: descLabel, sortKey, columnIndex },
        { label, value: `${sortKey} desc`, directionLabel: ascLabel, sortKey, columnIndex }
    ];
};

const sortOptions = [
    ...createSortOptions('Agent ID', 'agentId', 1),
    ...createSortOptions('Device ID', 'deviceId', 2),
    ...createSortOptions('Username', 'username', 3),
    ...createSortOptions('Last Heartbeat', 'lastHeartbeat', 4, true),
    ...createSortOptions('Last Deployed', 'lastDeployed', 5, true)
];

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

const LOG_LIMITS = {
    MAX_LOGS_DISPLAYED: 1000,
    MAX_LOGS_FETCHED: 5000,
    LIVE_LOG_LIMIT: 500
};

const convertDataIntoTableFormat = (agentData) => ({
    ...agentData,
    id: agentData?.agentId,
    lastHeartbeatComp: func.prettifyEpoch(agentData?.lastHeartbeat),
    lastDeployedComp: func.prettifyEpoch(agentData?.lastDeployed)
});

function EndpointShieldMetadata() {

    const [loading, setLoading] = useState(false);
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5]);
    const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security";
    const allCollections = PersistStore((state) => state.allCollections) || [];
    const [selectedAgent, setSelectedAgent] = useState(null);
    const [showFlyout, setShowFlyout] = useState(false);
    const [mcpServers, setMcpServers] = useState([]);
    const [userAnalysis, setUserAnalysis] = useState(null);
    const [agentLogs, setAgentLogs] = useState([]);
    const [displayedLogs, setDisplayedLogs] = useState([]);
    const [isLogsExpanded, setIsLogsExpanded] = useState(true);
    const [logMode, setLogMode] = useState(LOG_MODES.HISTORICAL);
    const [liveInterval, setLiveInterval] = useState(null);
    const [description, setDescription] = useState("");
    const [isEditingDescription, setIsEditingDescription] = useState(false);
    const [editableDescription, setEditableDescription] = useState("");
    const [endpointShieldData, setEndpointShieldData] = useState(null);
    const [filters, setFilters] = useState([
        createFilter('username', 'Username'),
        createFilter('deviceId', 'Device ID')
    ]);

    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }

    const fetchModuleInfo = useCallback(async () => {
        try {
            const response = await settingRequests.fetchModuleInfo({
                moduleType: MODULE_TYPE.MCP_ENDPOINT_SHIELD
            });
            const endpointShieldModules = response.moduleInfos || [];
            const agents = endpointShieldModules.map(module => ({
                agentId: module.id,
                deviceId: module.name,
                username: module.additionalData?.username || DEFAULT_VALUE,
                lastHeartbeat: module.lastHeartbeatReceived || 0,
                lastDeployed: module.startedTs || 0,
                _moduleData: module
            }));
            setEndpointShieldData({ agents });
        } catch (error) {
        }
    }, []);

    useEffect(() => {
        fetchModuleInfo();
    }, [fetchModuleInfo]);

    const handleSaveDescription = useCallback(() => {
        setDescription(editableDescription);
        setIsEditingDescription(false);
        func.setToast(true, false, "Description saved");
    }, [editableDescription]);

    const fetchAgentLogs = useCallback(async (agentId, startTime, endTime) => {
        try {
            const logsResponse = await settingRequests.getAgentLogs(agentId, startTime, endTime);
            const transformedLogs = (logsResponse.agentLogs || []).map(log => ({
                timestamp: log.timestamp,
                level: log.level || 'INFO',
                message: log.log || log.message
            }));
            let sortedLogs = transformedLogs.sort((a, b) => b.timestamp - a.timestamp);
            if (sortedLogs.length > LOG_LIMITS.MAX_LOGS_FETCHED) {
                sortedLogs = sortedLogs.slice(0, LOG_LIMITS.MAX_LOGS_FETCHED);
            }
            return sortedLogs;
        } catch (error) {
            console.error("Error fetching agent logs:", error);
        }
    }, []);

    const stopLiveFetching = useCallback(() => {
        if (liveInterval) {
            clearInterval(liveInterval);
            setLiveInterval(null);
        }
    }, [liveInterval]);

    const startLiveFetching = useCallback(async (agentId) => {
        if (liveInterval) clearInterval(liveInterval);

        let lastFetchTimestamp = Math.floor(Date.now() / 1000);

        const interval = setInterval(async () => {
            try {
                const now = Math.floor(Date.now() / 1000);
                const newLogs = await fetchAgentLogs(agentId, lastFetchTimestamp, now);
                if (newLogs.length > 0) {
                    setAgentLogs(prevLogs => {
                        const updatedLogs = [...newLogs, ...prevLogs];
                        const uniqueLogs = updatedLogs.filter((log, index, arr) =>
                            arr.findIndex(l => l.timestamp === log.timestamp && l.message === log.message) === index
                        );
                        return uniqueLogs.slice(0, LOG_LIMITS.LIVE_LOG_LIMIT);
                    });
                    lastFetchTimestamp = newLogs[0].timestamp;
                }
            } catch (error) {
                console.error("Error in live log fetching:", error);
            }
        }, 10000);

        setLiveInterval(interval);

        const now = Math.floor(Date.now() / 1000);
        const oneHourAgo = now - (60 * 60);
        const initialLogs = await fetchAgentLogs(agentId, oneHourAgo, now);
        setAgentLogs(initialLogs.slice(0, LOG_LIMITS.LIVE_LOG_LIMIT));
        if (initialLogs.length > 0) {
            lastFetchTimestamp = initialLogs[0].timestamp;
        }
    }, [liveInterval, fetchAgentLogs]);

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

    const handleRowClick = useCallback(async (agent) => {
        setSelectedAgent(agent);

        try {
            const serversResponse = await settingRequests.getMcpServersByAgent(agent.agentId, agent.deviceId);
            setMcpServers(serversResponse.mcpServers || []);
        } catch (error) {
            console.error("Error fetching MCP servers:", error);
            setMcpServers([]);
        }

        try {
            const analysisResponse = await settingRequests.getUserAnalysis(agent.agentId, agent.deviceId);
            setUserAnalysis(analysisResponse || null);
        } catch (error) {
            console.error("Error fetching user analysis:", error);
            setUserAnalysis(null);
        }

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

    useEffect(() => {
        return () => { if (liveInterval) clearInterval(liveInterval); };
    }, [liveInterval]);

    useEffect(() => {
        if (!showFlyout) stopLiveFetching();
    }, [showFlyout, stopLiveFetching]);

    useEffect(() => {
        if (agentLogs.length === 0 || !showFlyout) {
            setDisplayedLogs([]);
            return;
        }
        const limitedLogs = agentLogs.slice(0, LOG_LIMITS.MAX_LOGS_DISPLAYED);
        setDisplayedLogs(limitedLogs);
    }, [agentLogs, showFlyout, logMode]);

    useEffect(() => {
        if (endpointShieldData?.agents) {
            const agentsData = endpointShieldData.agents;
            const uniqueUsernames = [...new Set(agentsData.map(a => a.username).filter(Boolean))];
            const uniqueDeviceIds = [...new Set(agentsData.map(a => a.deviceId).filter(Boolean))];
            setFilters([
                { ...createFilter('username', 'Username'), choices: uniqueUsernames.map(u => ({ label: u, value: u })) },
                { ...createFilter('deviceId', 'Device ID'), choices: uniqueDeviceIds.map(d => ({ label: d, value: d })) }
            ]);
        }
    }, [endpointShieldData]);

    const fetchData = useCallback(async (sortKey, sortOrder, skip, limit, filters, _filterOperators, queryValue) => {
        setLoading(true);
        let ret = [];
        let total = 0;
        try {
            const agentsData = endpointShieldData?.agents || [];
            const filteredData = agentsData.filter(agent => {
                if (agent.lastHeartbeat < startTimestamp || agent.lastHeartbeat > endTimestamp) return false;
                if (filters.username?.length > 0 && !filters.username.includes(agent.username)) return false;
                if (filters.deviceId?.length > 0 && !filters.deviceId.includes(agent.deviceId)) return false;
                if (queryValue) {
                    const q = queryValue.toLowerCase();
                    if (!agent.agentId?.toLowerCase().includes(q) &&
                        !agent.deviceId?.toLowerCase().includes(q) &&
                        !agent.username?.toLowerCase().includes(q)) return false;
                }
                return true;
            });

            if (sortKey) {
                filteredData.sort((a, b) => {
                    const aVal = a[sortKey], bVal = b[sortKey];
                    if (aVal == null && bVal == null) return 0;
                    if (aVal == null) return 1;
                    if (bVal == null) return -1;
                    if (typeof aVal === 'number' && typeof bVal === 'number') return sortOrder * (aVal - bVal);
                    if (typeof aVal === 'string' && typeof bVal === 'string') return sortOrder * (bVal.toLowerCase().localeCompare(aVal.toLowerCase()));
                    return 0;
                });
            }

            total = filteredData.length;
            ret = filteredData.slice(skip, skip + limit).map(convertDataIntoTableFormat);
        } catch (error) {
            console.error("Error fetching MCP Endpoint Shield metadata:", error);
        } finally {
            setLoading(false);
        }
        return { value: ret, total };
    }, [endpointShieldData, startTimestamp, endTimestamp]);

    const allowBulkActions = window.USER_NAME && window.USER_NAME.endsWith("@akto.io");

    const promotedBulkActions = (selectedAgents) => {
        const actions = [];
        if (allowBulkActions) {
            actions.push({
                content: `Delete ${selectedAgents.length} agent info entr${selectedAgents.length > 1 ? "ies" : "y"}`,
                onAction: async () => {
                    const msg = `Are you sure you want to delete ${selectedAgents.length} agent info entr${selectedAgents.length > 1 ? "ies" : "y"}?`;
                    func.showConfirmationModal(msg, "Delete", async () => {
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
    );

    return (
        <>
            <PageWithMultipleCards
                title={
                    <Text as="div" variant="headingLg">
                        {mapLabel("Endpoint Shield", dashboardCategory)}
                    </Text>
                }
                isFirstPage={true}
                primaryAction={primaryActions}
                components={[
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
            <AgentDetails
                show={showFlyout}
                setShow={setShowFlyout}
                selectedAgent={selectedAgent}
                mcpServers={mcpServers}
                userAnalysis={userAnalysis}
                agentLogs={agentLogs}
                displayedLogs={displayedLogs}
                logMode={logMode}
                isLogsExpanded={isLogsExpanded}
                setIsLogsExpanded={setIsLogsExpanded}
                handleLogModeChange={handleLogModeChange}
                description={description}
                isEditingDescription={isEditingDescription}
                editableDescription={editableDescription}
                setEditableDescription={setEditableDescription}
                setIsEditingDescription={setIsEditingDescription}
                handleSaveDescription={handleSaveDescription}
                allCollections={allCollections}
            />
        </>
    );
}

export default EndpointShieldMetadata;
