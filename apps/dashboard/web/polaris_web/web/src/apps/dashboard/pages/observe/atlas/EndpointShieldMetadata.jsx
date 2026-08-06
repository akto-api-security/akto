import { Text, HorizontalStack, Icon, Tooltip } from "@shopify/polaris"
import { StatusActiveMajor, DiamondAlertMinor, RefreshMinor } from "@shopify/polaris-icons"
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
import AgentDetails from "./AgentDetails";
import { DEFAULT_VALUE } from "../api_collections/endpointShieldHelper";

const createHeading = (text, value = null, sortKey = null) => ({
    text,
    value: value || text.toLowerCase().replace(/ /g, ''),
    title: text,
    type: CellType.TEXT,
    sortActive: true,
    sortKey: sortKey || (value || text.toLowerCase().replace(/ /g, ''))
});

const headings = [
    { ...createHeading("Status", "statusComp"), sortActive: false },
    createHeading("Hostname", "hostname"),
    createHeading("Device ID", "deviceId"),
    createHeading("Agent Version", "agentVersion"),
    createHeading("OS", "osComp", "os"),
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

// columnIndex must equal (heading index + 1) — GithubServerTable.handleSort matches
// the Polaris heading index `col` against `columnIndex === col + 1`. The leading
// non-sortable "Status" column occupies heading index 0, so sortable columns start at 2.
const sortOptions = [
    ...createSortOptions('Hostname', 'hostname', 2),
    ...createSortOptions('Device ID', 'deviceId', 3),
    ...createSortOptions('Agent Version', 'agentVersion', 4),
    ...createSortOptions('OS', 'os', 5),
    ...createSortOptions('Username', 'username', 6),
    ...createSortOptions('Last Heartbeat', 'lastHeartbeat', 7, true),
    ...createSortOptions('Last Deployed', 'lastDeployed', 8, true)
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

const OS_ICON_MAP = { darwin: '/public/os-mac.svg', mac: '/public/os-mac.svg', windows: '/public/os-windows.svg', linux: '/public/os-linux.svg' };

const getOsIcon = (os) => {
    if (!os || os === DEFAULT_VALUE) return null;
    const key = os.toLowerCase();
    for (const [prefix, icon] of Object.entries(OS_ICON_MAP)) {
        if (key.includes(prefix)) return icon;
    }
    return null;
};

const getStatusComp = (installStatus, lastHeartbeat) => {
    if (installStatus === 'installing') {
        return (
            <Tooltip content="Installation in progress" dismissOnMouseOut>
                <Icon source={RefreshMinor} color="warning" />
            </Tooltip>
        );
    }
    if (installStatus === 'failed') {
        return (
            <Tooltip content="Installation failed" dismissOnMouseOut>
                <Icon source={DiamondAlertMinor} color="critical" />
            </Tooltip>
        );
    }
    if (lastHeartbeat > 0) {
        return (
            <Tooltip content="Running" dismissOnMouseOut>
                <Icon source={StatusActiveMajor} color="success" />
            </Tooltip>
        );
    }
    return null;
};

const convertDataIntoTableFormat = (agentData) => {
    const os = agentData?.os;
    const osDisplayName = agentData?.osDisplayName;
    const displayOs = (osDisplayName && osDisplayName !== DEFAULT_VALUE) ? osDisplayName : (os && os !== DEFAULT_VALUE ? os : null);
    const osIcon = getOsIcon(os);
    const osComp = displayOs ? (
        <HorizontalStack gap="1" wrap={false} blockAlign="center">
            {osIcon && <img src={osIcon} alt={os} style={{ width: '16px', height: '16px', flexShrink: 0 }} />}
            <Text variant="bodySm">{displayOs}</Text>
        </HorizontalStack>
    ) : DEFAULT_VALUE;

    return {
        ...agentData,
        id: agentData?.agentId,
        lastHeartbeatComp: func.prettifyEpoch(agentData?.lastHeartbeat),
        lastDeployedComp: func.prettifyEpoch(agentData?.lastDeployed),
        osComp,
        statusComp: getStatusComp(agentData?.installStatus, agentData?.lastHeartbeat),
    };
};

const mapModuleToAgent = (module) => ({
    agentId: module.id,
    hostname: module.name,
    deviceId: module.additionalData?.deviceId || DEFAULT_VALUE,
    agentVersion: module.currentVersion || DEFAULT_VALUE,
    username: module.additionalData?.username || DEFAULT_VALUE,
    lastHeartbeat: module.lastHeartbeatReceived || 0,
    lastDeployed: module.startedTs || 0,
    os: module.additionalData?.os || DEFAULT_VALUE,
    osDisplayName: module.additionalData?.osDisplayName || DEFAULT_VALUE,
    osVersion: module.additionalData?.osVersion || DEFAULT_VALUE,
    arch: module.additionalData?.arch || DEFAULT_VALUE,
    kernelVersion: module.additionalData?.kernelVersion || DEFAULT_VALUE,
    totalRamGB: module.additionalData?.totalRamGB ?? DEFAULT_VALUE,
    cpuCount: module.additionalData?.cpuCount ?? DEFAULT_VALUE,
    isVM: module.additionalData?.isVM ?? null,
    locale: module.additionalData?.locale || DEFAULT_VALUE,
    timezone: module.additionalData?.timezone || DEFAULT_VALUE,
    publicIP: module.additionalData?.publicIP || DEFAULT_VALUE,
    cpuModel: module.additionalData?.cpuModel || DEFAULT_VALUE,
    macModel: module.additionalData?.macModel || DEFAULT_VALUE,
    totalDiskGB: module.additionalData?.totalDiskGB ?? DEFAULT_VALUE,
    availableDiskGB: module.additionalData?.availableDiskGB ?? DEFAULT_VALUE,
    localIP: module.additionalData?.localIP || DEFAULT_VALUE,
    localHostname: module.additionalData?.localHostname || DEFAULT_VALUE,
    userFullName: module.additionalData?.userFullName || DEFAULT_VALUE,
    userShell: module.additionalData?.userShell || DEFAULT_VALUE,
    bootTime: module.additionalData?.bootTime || null,
    installedApps: module.additionalData?.installedApps || [],
    installStatus: module.additionalData?.installStatus || null,
    _moduleData: module
});

function EndpointShieldMetadata() {

    const [loading, setLoading] = useState(false);
    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5]);
    const dashboardCategory = PersistStore((state) => state.dashboardCategory) || "API Security";
    const allCollections = PersistStore((state) => state.allCollections) || [];
    const [selectedAgent, setSelectedAgent] = useState(null);
    const [showFlyout, setShowFlyout] = useState(false);
    const [refreshKey, setRefreshKey] = useState(0);
    const [allowedEnvFields, setAllowedEnvFields] = useState([]);
    const [filters, setFilters] = useState([
        createFilter('username', 'Username'),
        createFilter('hostname', 'Hostname'),
        createFilter('deviceId', 'Device ID'),
        createFilter('os', 'OS')
    ]);

    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    function disambiguateLabel(key, value) {
        return func.convertToDisambiguateLabelObj(value, null, 2);
    }

    // Filter dropdown options (distinct values across ALL agents) — fetched ONCE, server-side.
    useEffect(() => {
        (async () => {
            try {
                const resp = await settingRequests.fetchEndpointShieldFilterOptions();
                const opts = resp?.filterOptions || {};
                setFilters([
                    { ...createFilter('username', 'Username'), choices: (opts.usernames || []).map(u => ({ label: u, value: u })) },
                    { ...createFilter('hostname', 'Hostname'), choices: (opts.hostnames || []).map(h => ({ label: h, value: h })) },
                    { ...createFilter('deviceId', 'Device ID'), choices: (opts.deviceIds || []).map(d => ({ label: d, value: d })) },
                    { ...createFilter('os', 'OS'), choices: (opts.oses || []).map(o => ({ label: o, value: o })) }
                ]);
            } catch (e) { /* ignore */ }
        })();
    }, []);

    const handleSaveEnv = useCallback(async (moduleId, moduleName, envData) => {
        await settingRequests.updateModuleEnvAndReboot(moduleId, moduleName, envData);
        func.setToast(true, false, "Configuration saved. Agent will pick up changes shortly.");
        // reflect the saved env in the open flyout optimistically, and refresh the current table page
        setSelectedAgent(prev => {
            if (!prev || !prev._moduleData) return prev;
            const ad = prev._moduleData.additionalData || {};
            return { ...prev, _moduleData: { ...prev._moduleData, additionalData: { ...ad, env: { ...(ad.env || {}), ...(envData || {}) } } } };
        });
        setRefreshKey(k => k + 1);
    }, []);

    // Server-side paginated fetch — one page (skip/limit) with filters/sort/query pushed to the backend.
    const fetchData = useCallback(async (sortKey, sortOrder, skip, limit, filters, _filterOperators, queryValue) => {
        setLoading(true);
        let ret = [];
        let total = 0;
        try {
            const resp = await settingRequests.fetchEndpointShieldAgents({
                skip, limit,
                sortKey: sortKey || "lastHeartbeat",
                sortOrder: sortOrder === 1 ? 1 : -1,
                usernames: filters?.username || [],
                hostnames: filters?.hostname || [],
                deviceIds: filters?.deviceId || [],
                oses: filters?.os || [],
                queryValue: queryValue || "",
                startTimestamp, endTimestamp,
            });
            setAllowedEnvFields(resp?.allowedEnvFields || []);
            const agents = (resp?.moduleInfos || []).map(mapModuleToAgent);
            total = resp?.total || 0;
            ret = agents.map(convertDataIntoTableFormat);
        } catch (error) {
            console.error("Error fetching MCP Endpoint Shield metadata:", error);
        } finally {
            setLoading(false);
        }
        return { value: ret, total };
    }, [startTimestamp, endTimestamp]);

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

    const handleRowClick = useCallback((agent) => {
        // the row already carries the full module (_moduleData) from the paginated fetch
        setSelectedAgent(agent);
        setShowFlyout(true);
    }, []);

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
                        key={startTimestamp + endTimestamp + "-" + refreshKey + "-" + (filters[0]?.choices?.length || 0)}
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
                allCollections={allCollections}
                allowedEnvFields={allowedEnvFields}
                onSaveEnv={handleSaveEnv}
                startTimestamp={startTimestamp}
                endTimestamp={endTimestamp}
            />
        </>
    );
}

export default EndpointShieldMetadata;
