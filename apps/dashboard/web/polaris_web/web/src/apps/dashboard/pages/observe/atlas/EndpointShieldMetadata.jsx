import { Text, HorizontalStack, Icon, Tooltip, Badge } from "@shopify/polaris"
import { StatusActiveMajor, DiamondAlertMinor, RefreshMinor, ClockMinor } from "@shopify/polaris-icons"
import { useEffect, useReducer, useState, useCallback, useRef } from "react"
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
import { DEFAULT_VALUE, isExtensionAgent, INSTALL_IN_PROGRESS_STATUSES, isInstallFailed, getDeploymentAttempt, DEPLOYMENT_OUTCOME_BADGE } from "../api_collections/endpointShieldHelper";

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
    createHeading("OS/Browser", "osComp", "os"),
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

const STATUS_LABELS = {
    active: 'Running',
    inactive: 'Inactive',
    error: 'Stale',
    failed: 'Never connected',
    installing: 'Installing',
    install_failed: 'Install failed'
};

const resourceName = {
    singular: 'agent',
    plural: 'agents',
};

const OS_LABELS = { mac: 'macOS', darwin: 'macOS', windows: 'Windows', linux: 'Linux' };
const OS_ICON_MAP = { darwin: '/public/os-mac.svg', mac: '/public/os-mac.svg', windows: '/public/os-windows.svg', linux: '/public/linux.svg' };
const BROWSER_ICON_MAP = { chrome: '/public/chrome.svg', firefox: '/public/firefox.svg', safari: '/public/safari.svg', brave: '/public/brave.svg', edge: '/public/edge.svg' };
const GENERIC_BROWSER_ICON = '/public/Globe_icon.svg';
const CLAUDE_ICON = '/public/claude.svg';
const CLAUDE_COMPLIANCE_LABEL = 'Claude Compliance';

const getIconFromMap = (value, map) => {
    if (!value || value === DEFAULT_VALUE) return null;
    const key = value.toLowerCase();
    for (const [prefix, icon] of Object.entries(map)) {
        if (key.includes(prefix)) return icon;
    }
    return null;
};

const getOsOrBrowserComp = (agentData) => {
    if (agentData?.provider === 'claude' && agentData?.orgName) {
        return (
            <HorizontalStack gap="1" wrap={false} blockAlign="center">
                <img src={CLAUDE_ICON} alt={CLAUDE_COMPLIANCE_LABEL} style={{ width: '16px', height: '16px', flexShrink: 0 }} />
                <Text variant="bodySm">{CLAUDE_COMPLIANCE_LABEL}</Text>
            </HorizontalStack>
        );
    }

    const reportedBrowser = agentData?.browserName;
    const hasReportedBrowser = reportedBrowser && reportedBrowser !== DEFAULT_VALUE && reportedBrowser.toLowerCase() !== 'unknown';
    const hasReportedOs = agentData?.os && agentData.os !== DEFAULT_VALUE;
    if (hasReportedBrowser || (!hasReportedOs && isExtensionAgent(agentData?.deviceId, agentData?.agentVersion))) {
        const browserName = agentData?.browserName;
        const browserVersion = agentData?.browserVersion;
        const hasBrowserName = browserName && browserName !== DEFAULT_VALUE && browserName.toLowerCase() !== 'unknown';
        const label = hasBrowserName
            ? `${browserName}${browserVersion && browserVersion !== DEFAULT_VALUE && browserVersion.toLowerCase() !== 'unknown' ? ` ${browserVersion}` : ''}`
            : 'Browser';
        const icon = (hasBrowserName && getIconFromMap(browserName, BROWSER_ICON_MAP)) || GENERIC_BROWSER_ICON;
        return (
            <HorizontalStack gap="1" wrap={false} blockAlign="center">
                <img src={icon} alt={label} style={{ width: '16px', height: '16px', flexShrink: 0 }} />
                <Text variant="bodySm">{label}</Text>
            </HorizontalStack>
        );
    }

    const os = agentData?.os;
    const osDisplayName = agentData?.osDisplayName;
    const displayOs = (osDisplayName && osDisplayName !== DEFAULT_VALUE) ? osDisplayName : (os && os !== DEFAULT_VALUE ? (OS_LABELS[os.toLowerCase()] || os) : null);
    if (!displayOs) return DEFAULT_VALUE;
    const osIcon = getIconFromMap(os, OS_ICON_MAP);
    return (
        <HorizontalStack gap="1" wrap={false} blockAlign="center">
            {osIcon && <img src={osIcon} alt={os} style={{ width: '16px', height: '16px', flexShrink: 0 }} />}
            <Text variant="bodySm">{displayOs}</Text>
        </HorizontalStack>
    );
};

// currentStatus is computed server-side (ModuleInfoAction.fetchEndpointShieldAgents) from
// lastHeartbeatReceived: active (<2h), inactive (<24h), error (stale >24h), failed (never heartbeat).
const CURRENT_STATUS_COMP_MAP = {
    active: { icon: StatusActiveMajor, color: "success", tooltip: "Running" },
    inactive: { icon: ClockMinor, color: "warning", tooltip: "No heartbeat in over 2 hours" },
    error: { icon: DiamondAlertMinor, color: "critical", tooltip: "No heartbeat in over 24 hours" },
    failed: { icon: DiamondAlertMinor, color: "critical", tooltip: "Never connected" },
};

const getStatusComp = (installStatus, currentStatus, installFailureReason) => {
    if (INSTALL_IN_PROGRESS_STATUSES.includes(installStatus)) {
        return (
            <Tooltip content="Installation in progress" dismissOnMouseOut>
                <Icon source={RefreshMinor} color="warning" />
            </Tooltip>
        );
    }
    if (isInstallFailed(installStatus)) {
        return (
            <Tooltip content={installFailureReason ? `Installation failed: ${installFailureReason}` : "Installation failed"} dismissOnMouseOut>
                <Icon source={DiamondAlertMinor} color="critical" />
            </Tooltip>
        );
    }
    const statusComp = CURRENT_STATUS_COMP_MAP[currentStatus];
    if (!statusComp) return null;
    return (
        <Tooltip content={statusComp.tooltip} dismissOnMouseOut>
            <Icon source={statusComp.icon} color={statusComp.color} />
        </Tooltip>
    );
};

// "Last deployed" cell: when the latest deployment attempt started, plus a badge
// when it did not end in a successful install. The tooltip has the details.
const getLastDeployedComp = (agentData) => {
    const text = func.prettifyEpoch(agentData?.lastDeployed);
    const attempt = getDeploymentAttempt(agentData);
    if (!attempt) return text;
    const badge = DEPLOYMENT_OUTCOME_BADGE[attempt.outcome];
    const details = [
        `Started ${func.epochToDateTime(attempt.startedTs)}`,
        attempt.finishedTs ? `finished ${func.epochToDateTime(attempt.finishedTs)}` : null,
        attempt.outcome ? `outcome: ${attempt.outcome}` : null,
        attempt.version ? `version ${attempt.version}` : null,
    ].filter(Boolean).join(' · ');
    return (
        <Tooltip content={details} dismissOnMouseOut>
            <HorizontalStack gap="1" wrap={false} blockAlign="center">
                <Text variant="bodyMd">{text}</Text>
                {badge ? <Badge size="small" status={badge.status}>{badge.label}</Badge> : null}
            </HorizontalStack>
        </Tooltip>
    );
};

const convertDataIntoTableFormat = (agentData) => ({
    ...agentData,
    id: agentData?.agentId,
    lastHeartbeatComp: func.prettifyEpoch(agentData?.lastHeartbeat),
    lastDeployedComp: getLastDeployedComp(agentData),
    osComp: getOsOrBrowserComp(agentData),
    statusComp: getStatusComp(agentData?.installStatus, agentData?.currentStatus, agentData?.installFailureReason),
});

const knownOrDefault = (value) => (value && String(value).toLowerCase() !== 'unknown') ? value : DEFAULT_VALUE;
const hideUnknownLabel = (text, fallback) => String(text).toLowerCase() === 'unknown' ? fallback : text;

const mapModuleToAgent = (module) => ({
    agentId: module.id,
    hostname: module.name,
    deviceId: module.additionalData?.deviceId || module?.id || DEFAULT_VALUE,
    agentVersion: module.currentVersion || DEFAULT_VALUE,
    username: module.additionalData?.username || module?.additionalData?.email || DEFAULT_VALUE,
    lastHeartbeat: module.lastHeartbeatReceived || 0,
    // Latest deployment attempt (computed server-side: installStartedTs, else startedTs).
    lastDeployed: module.additionalData?.lastDeployedTs || module.additionalData?.installStartedTs || module.startedTs || 0,
    firstSeen: module.startedTs || 0,
    currentStatus: module.additionalData?.currentStatus || null,
    provider: module.additionalData?.provider || null,
    orgName: module.additionalData?.orgName || null,
    os: knownOrDefault(module.additionalData?.os),
    osDisplayName: knownOrDefault(module.additionalData?.osDisplayName),
    browserName: knownOrDefault(module.additionalData?.browserName),
    browserVersion: knownOrDefault(module.additionalData?.browserVersion),
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
    // Failure summary sent by the installer with a failed status (cleared on success).
    installFailureReason: module.additionalData?.installFailureReason || null,
    installDiagnosis: module.additionalData?.installDiagnosis || null,
    installFindings: Array.isArray(module.additionalData?.installFindings) ? module.additionalData.installFindings : [],
    installExitCode: module.additionalData?.installExitCode ?? null,
    installId: module.additionalData?.installId || null,
    // Latest deployment attempt (installers only; see getDeploymentAttempt).
    installStartedTs: module.additionalData?.installStartedTs || 0,
    installFinishedTs: module.additionalData?.installFinishedTs || 0,
    installOutcome: module.additionalData?.installOutcome || null,
    installVersion: module.additionalData?.installVersion || null,
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
    const agentsCacheRef = useRef({});
    const [filters, setFilters] = useState([
        createFilter('username', 'Username'),
        createFilter('hostname', 'Hostname'),
        createFilter('deviceId', 'Device ID'),
        createFilter('osBrowser', 'OS/Browser'),
        createFilter('agentVersion', 'Agent Version'),
        createFilter('status', 'Status')
    ]);

    const getTimeEpoch = (key) => Math.floor(Date.parse(currDateRange.period[key]) / 1000);
    const startTimestamp = getTimeEpoch("since");
    const endTimestamp = getTimeEpoch("until");

    function disambiguateLabel(key, value) {
        const choices = filters.find(f => f.key === key)?.choices || [];
        const labels = Array.isArray(value) ? value.map(v => choices.find(c => c.value === v)?.label || v) : value;
        return func.convertToDisambiguateLabelObj(labels, null, 2);
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
                    { ...createFilter('osBrowser', 'OS/Browser'), choices: [
                        ...(opts.oses || []).map(o => ({ label: hideUnknownLabel(OS_LABELS[o.toLowerCase()] || o, 'OS'), value: `os:${o}` })),
                        ...(opts.browserNames || []).map(b => ({ label: hideUnknownLabel(b, 'Browser'), value: `browser:${b}` })),
                        ...((opts.providers || []).includes('claude') ? [{ label: CLAUDE_COMPLIANCE_LABEL, value: 'provider:claude' }] : [])
                    ] },
                    { ...createFilter('agentVersion', 'Agent Version'), choices: (opts.agentVersions || []).map(v => ({ label: hideUnknownLabel(v, 'Version'), value: v })) },
                    { ...createFilter('status', 'Status'), choices: (opts.statuses || []).map(st => ({ label: STATUS_LABELS[st] || st, value: st })) },
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
        const pickOsBrowser = (type) => (filters?.osBrowser || []).filter(v => v.startsWith(`${type}:`)).map(v => v.slice(type.length + 1));
        try {
            const resp = await settingRequests.fetchEndpointShieldAgents({
                skip, limit,
                sortKey: sortKey || "lastHeartbeat",
                sortOrder: sortOrder === 1 ? 1 : -1,
                usernames: filters?.username || [],
                hostnames: filters?.hostname || [],
                deviceIds: filters?.deviceId || [],
                oses: pickOsBrowser('os'),
                browserNames: pickOsBrowser('browser'),
                agentVersions: filters?.agentVersion || [],
                statuses: filters?.status || [],
                providers: pickOsBrowser('provider'),
                queryValue: queryValue || "",
                startTimestamp, endTimestamp,
            });
            setAllowedEnvFields(resp?.allowedEnvFields || []);
            const agents = (resp?.moduleInfos || []).map(mapModuleToAgent);
            total = resp?.total || 0;
            ret = agents.map(convertDataIntoTableFormat);
            ret.forEach((agent) => { agentsCacheRef.current[agent.agentId] = agent; });
        } catch (error) {
            console.error("Error fetching MCP Endpoint Shield metadata:", error);
        } finally {
            setLoading(false);
        }
        return { value: ret, total };
    }, [startTimestamp, endTimestamp]);

    // Delete stays restricted to internal akto.io users (destructive — removes agent info entries
    // outright). Enable/Disable System Proxy is reversible (just flips an env flag and reboots the
    // agent, same as the single-device Configure tab's toggle) and available to anyone who can
    // reach this page — the backend still enforces ADMIN_ACTIONS regardless.
    const allowBulkActions = window.USER_NAME && window.USER_NAME.endsWith("@akto.io");

    const promotedBulkActions = (selectedAgents) => {
        const actions = [];
        const agentCount = selectedAgents.length;
        const agentWord = `agent${agentCount > 1 ? "s" : ""}`;

        const selectedAgentsMeta = selectedAgents.map((id) => agentsCacheRef.current[id]).filter(Boolean);
        const allInstallers = selectedAgentsMeta.length === agentCount &&
            selectedAgentsMeta.every((a) => !isExtensionAgent(a.deviceId, a.agentVersion));

        const bulkToggleSystemProxy = (enable) => () => {
            const verb = enable ? "enable" : "disable";
            const msg = `Are you sure you want to ${verb} system proxy for ${agentCount} ${agentWord}? They will pick up the change on their next reboot.`;
            func.showConfirmationModal(msg, enable ? "Enable" : "Disable", async () => {
                try {
                    await settingRequests.bulkUpdateModuleEnvAndReboot(selectedAgents, { ENABLE_SYSTEM_PROXY: enable ? "true" : "false" });
                    func.setToast(true, false, `System proxy ${enable ? "enabled" : "disabled"} for ${agentCount} ${agentWord}. Agents will pick up changes shortly.`);
                    setRefreshKey(k => k + 1);
                } catch (error) {
                    console.error("Error updating system proxy:", error);
                    func.setToast(true, true, "Failed to update system proxy");
                }
            });
        };

        actions.push({
            content: `Enable system proxy for ${agentCount} ${agentWord}`,
            onAction: bulkToggleSystemProxy(true),
            requires: 'api/bulkUpdateModuleEnvAndReboot',
        });
        actions.push({
            content: `Disable system proxy for ${agentCount} ${agentWord}`,
            onAction: bulkToggleSystemProxy(false),
            requires: 'api/bulkUpdateModuleEnvAndReboot',
        });

        if (allInstallers) {
            const bulkToggleAutoUpdate = (enable) => () => {
                const verb = enable ? "enable" : "disable";
                const msg = `Are you sure you want to ${verb} auto update for ${agentCount} ${agentWord}? They will pick up the change on their next reboot.`;
                func.showConfirmationModal(msg, enable ? "Enable" : "Disable", async () => {
                    try {
                        await settingRequests.bulkUpdateModuleEnvAndReboot(selectedAgents, { ENABLE_AUTO_UPDATE: enable ? "true" : "false" });
                        func.setToast(true, false, `Auto update ${enable ? "enabled" : "disabled"} for ${agentCount} ${agentWord}. Agents will pick up changes shortly.`);
                        setRefreshKey(k => k + 1);
                    } catch (error) {
                        console.error("Error updating auto update flag:", error);
                        func.setToast(true, true, "Failed to update auto update setting");
                    }
                });
            };

            actions.push({
                content: `Enable auto update for ${agentCount} ${agentWord}`,
                onAction: bulkToggleAutoUpdate(true),
                requires: 'api/bulkUpdateModuleEnvAndReboot',
            });
            actions.push({
                content: `Disable auto update for ${agentCount} ${agentWord}`,
                onAction: bulkToggleAutoUpdate(false),
                requires: 'api/bulkUpdateModuleEnvAndReboot',
            });

            actions.push({
                content: `Update ${agentCount} ${agentWord} to latest version`,
                onAction: () => {
                    const msg = `Are you sure you want to update ${agentCount} ${agentWord} to the latest version? They will pick up the change on their next reboot.`;
                    func.showConfirmationModal(msg, "Update", async () => {
                        try {
                            await settingRequests.bulkUpdateModuleEnvAndReboot(selectedAgents, { UPDATE_TO_LATEST_VERSION: "true" });
                            func.setToast(true, false, `${agentCount} ${agentWord} queued to update to the latest version.`);
                            setRefreshKey(k => k + 1);
                        } catch (error) {
                            console.error("Error triggering update to latest version:", error);
                            func.setToast(true, true, "Failed to trigger update to latest version");
                        }
                    });
                },
                requires: 'api/bulkUpdateModuleEnvAndReboot',
            });
        }

        if (allowBulkActions) {
            actions.push({
                content: `Delete ${agentCount} agent info entr${agentCount > 1 ? "ies" : "y"}`,
                onAction: async () => {
                    const msg = `Are you sure you want to delete ${agentCount} agent info entr${agentCount > 1 ? "ies" : "y"}?`;
                    func.showConfirmationModal(msg, "Delete", async () => {
                        try {
                            await settingRequests.deleteModuleInfo(selectedAgents);
                            func.setToast(true, false, `${agentCount} agent info entr${agentCount > 1 ? "ies" : "y"} deleted successfully`);
                            window.location.reload();
                        } catch (error) {
                            console.error("Error deleting agent info:", error);
                            func.setToast(true, true, "Failed to delete agent info");
                        }
                    });
                },
                requires: 'api/deleteModuleInfo',
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
                        loadingText="Loading agents..."
                        fetchData={fetchData}
                        filters={filters}
                        hideQueryField={false}
                        useNewRow={true}
                        condensedHeight={true}
                        pageLimit={20}
                        headings={headings}
                        onRowClick={handleRowClick}
                        rowClickable={true}
                        selectable={true}
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
