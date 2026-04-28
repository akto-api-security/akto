
import { Text, HorizontalStack, VerticalStack, Box, Spinner, Popover, Button, ActionList, DataTable } from "@shopify/polaris"
import { useEffect, useReducer, useState } from "react"
import values from "@/util/values";
import {produce} from "immer"
import api from "./api"
import func from "@/util/func"
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "../../components/tables/GithubServerTable";
import { MethodBox } from "./GetPrettifyEndpoint";
import { CellType } from "../../components/tables/rows/GithubRow";
import { CircleTickMajor, CircleCancelMajor, SettingsMajor, HorizontalDotsMinor } from "@shopify/polaris-icons";
import { Icon } from "@shopify/polaris";
import settingRequests from "../settings/api";
import PersistStore from "../../../main/PersistStore";
import ConditionalApprovalModal from "../../components/modals/ConditionalApprovalModal";
import RegistryBadge from "../../components/shared/RegistryBadge";
import ComponentRiskAnalysisBadges from "./components/ComponentRiskAnalysisBadges";
import { isEndpointSecurityCategory } from "../../../main/labelHelper";

const headings = [
    {
        title: '',
        value: 'expanderSpacer',
        text: '',
        type: CellType.COLLAPSIBLE,
    },
    {
        title: 'Risk Analysis',
        value: 'riskAnalysisComp',
        text: 'Risk Analysis',
    },
    {
        title: 'MCP Server',
        text: 'MCP Server',
        value: 'mcpServerName',
        type: CellType.TEXT,
        filterKey: 'mcpServer',
    },
    {
        title: 'AI Agent',
        text: 'AI Agent',
        value: 'aiAgentName',
        type: CellType.TEXT,
        filterKey: 'aiAgent',
    },
    {
        title: 'Last Detected',
        text: "Last Detected",
        value: "lastDetectedComp",
        sortActive: true,
        sortKey: 'lastDetected',
        type: CellType.TEXT
    },
    {
        title: 'Updated',
        text: "Updated",
        value: "updatedTimestampComp",
        sortKey: 'updatedTimestamp',
        sortActive: true,
        type: CellType.TEXT
    },
    {
        title: 'Access Types',
        text: "Access Types",
        value: "apiAccessTypesComp",
    },
    {
        title: 'Remarks',
        text: "Remarks",
        value: "remarksComp"
    },
    {
        title: 'Marked By',
        text: "Marked By",
        value: "markedBy",
        type: CellType.TEXT
    },
    {
        title: '',
        type: CellType.ACTION,
    }
]

const sortOptions = [
    { label: 'Last Detected', value: 'lastDetected asc', directionLabel: 'Oldest', sortKey: 'lastDetected', columnIndex: 3 },
    { label: 'Last Detected', value: 'lastDetected desc', directionLabel: 'Newest', sortKey: 'lastDetected', columnIndex: 3 },
    { label: 'Updated', value: 'updatedTimestamp asc', directionLabel: 'Oldest', sortKey: 'updatedTimestamp', columnIndex: 4 },
    { label: 'Updated', value: 'updatedTimestamp desc', directionLabel: 'Newest', sortKey: 'updatedTimestamp', columnIndex: 4 },
   
];

let filtersDefault = [
    {
        key: 'type',
        label: 'Type',
        title: 'Type',
        choices: [
            { label: "Tool", value: "mcp-tool" },
            { label: "Resource", value: "mcp-resource" },
            { label: "Prompt", value: "mcp-prompt" },
            { label: "Server", value: "mcp-server" }
        ],
    },
    {
        key: 'markedBy',
        label: 'Marked By',
        title: 'Marked By',
        choices: [],
    },
    {
        key: 'apiAccessTypes',
        label: 'Access Types',
        title: 'Access Types',
        choices: [
            { label: "Public", value: "PUBLIC" },
            { label: "Private", value: "PRIVATE" },
            { label: "Partner", value: "PARTNER" },
            { label: "Third Party", value: "THIRD_PARTY" }
        ],
    },
    {
        key: 'collectionName',
        label: 'Collection Name',
        title: 'Collection Name',
        choices: [],
    }
]

let filtersEndpointSecurity = [
    {
        key: 'markedBy',
        label: 'Marked By',
        title: 'Marked By',
        choices: [],
    },
    {
        key: 'apiAccessTypes',
        label: 'Access Types',
        title: 'Access Types',
        choices: [
            { label: "Public", value: "PUBLIC" },
            { label: "Private", value: "PRIVATE" },
            { label: "Partner", value: "PARTNER" },
            { label: "Third Party", value: "THIRD_PARTY" }
        ],
    },
    {
        key: 'aiAgent',
        label: 'AI Agent',
        title: 'AI Agent',
        choices: [],
    },
    {
        key: 'mcpServer',
        label: 'MCP Server',
        title: 'MCP Server',
        choices: [],
    }
]

const resourceName = {
    singular: 'audit record',
    plural: 'audit records',
};

const stripDeviceIdFromName = (name, allCollections, collectionId) => {
    if (!name || !allCollections || !collectionId) {
        return name;
    }
    
    // Find the collection by ID
    const collection = allCollections.find(col => col.id === collectionId);
    if (!collection || !collection.envType || !Array.isArray(collection.envType)) {
        return name;
    }
    
    // Check if any envType has source "ENDPOINT" (case insensitive)
    const hasEndpointSource = collection.envType.some(env => 
        env.value && env.value.toLowerCase() === 'endpoint'
    );
    
    if (!hasEndpointSource) {
        return name;
    }

    const dotIndex = name.indexOf('.');
    if (dotIndex > 0 && dotIndex < name.length - 1) {
        // Return everything after the first dot
        return name.substring(dotIndex + 1);
    }
    
    return name;
};

const childTypeLabel = (type) => {
    switch (type) {
        case 'mcp-tool': return 'Tool';
        case 'mcp-resource': return 'Resource';
        case 'mcp-prompt': return 'Prompt';
        default: return type || '-';
    }
};

function ChildActionMenu({ child, getActionsList }) {
    const [active, setActive] = useState(false);
    return (
        <Popover
            active={active}
            activator={
                <Button plain icon={HorizontalDotsMinor} onClick={() => setActive((p) => !p)} />
            }
            autofocusTarget="first-node"
            onClose={() => setActive(false)}
        >
            <ActionList actionRole="menuitem" sections={getActionsList(child)} />
        </Popover>
    );
}

function MCPChildren({ parent, dateRange, getActionsList }) {
    const [loadingChildren, setLoadingChildren] = useState(true);
    const [children, setChildren] = useState([]);

    useEffect(() => {
        let cancelled = false;
        async function load() {
            setLoadingChildren(true);
            try {
                // A parent row may represent multiple device-specific mcp-server records
                // grouped by (agent, server). Fetch tools/resources/prompts across ALL of
                // those member hostCollectionIds.
                const collectionIds = Array.isArray(parent.groupedHostCollectionIds) && parent.groupedHostCollectionIds.length > 0
                    ? parent.groupedHostCollectionIds
                    : [parent.hostCollectionId];
                const res = await api.fetchAuditData(
                    'lastDetected', -1, 0, 500,
                    {
                        type: ['mcp-tool', 'mcp-resource', 'mcp-prompt'],
                        hostCollectionId: collectionIds,
                        lastDetected: dateRange,
                    },
                    {}, '', false, true
                );
                if (!cancelled) setChildren(res?.auditData || []);
            } catch (e) {
                if (!cancelled) setChildren([]);
            } finally {
                if (!cancelled) setLoadingChildren(false);
            }
        }
        load();
        return () => { cancelled = true; };
    }, [JSON.stringify(parent.groupedHostCollectionIds || [parent.hostCollectionId]), dateRange[0], dateRange[1]]);

    const renderRemarks = (child) => {
        if (!child?.remarks) {
            return <Text variant="bodySm" color="critical" fontWeight="bold">Pending...</Text>;
        }
        return <Text variant="bodySm">{child.remarks}</Text>;
    };

    const rows = children.map((child) => [
        <Text key={`type-${child.hexId}`} variant="bodySm">{childTypeLabel(child.type)}</Text>,
        <ComponentRiskAnalysisBadges key={`risk-${child.hexId}`} componentRiskAnalysis={child?.componentRiskAnalysis} />,
        <Text key={`name-${child.hexId}`} variant="bodySm">{child.resourceName}</Text>,
        <Text key={`access-${child.hexId}`} variant="bodySm">{(child.apiAccessTypes || []).join(', ') || '-'}</Text>,
        renderRemarks(child),
        <Text key={`mb-${child.hexId}`} variant="bodySm">{child.markedBy || '-'}</Text>,
        <ChildActionMenu key={`act-${child.hexId}`} child={child} getActionsList={getActionsList} />,
    ]);

    return (
        <tr>
            <td colSpan="100%" style={{ padding: 0 }}>
                <Box padding="3" background="bg-subdued">
                    {loadingChildren ? (
                        <HorizontalStack align="center" gap="2"><Spinner size="small" /><Text>Loading components...</Text></HorizontalStack>
                    ) : children.length === 0 ? (
                        <Text color="subdued">No tools found for this MCP server.</Text>
                    ) : (
                        <DataTable
                            columnContentTypes={['text', 'text', 'text', 'text', 'text', 'text', 'text']}
                            headings={['Type', 'Risk Analysis', 'Name', 'Access Types', 'Remarks', 'Marked By', '']}
                            rows={rows}
                        />
                    )}
                </Box>
            </td>
        </tr>
    );
}

const splitAgentAndServer = (name) => {
    if (!name) return { agent: '-', server: '-' };
    const dot = name.indexOf('.');
    if (dot <= 0 || dot >= name.length - 1) return { agent: '-', server: name };
    return { agent: name.substring(0, dot), server: name.substring(dot + 1) };
};

const convertDataIntoTableFormat = (auditRecord, collectionName, collectionRegistry) => {
    const allCollections = PersistStore.getState().allCollections;
    let temp = {...auditRecord}
    temp['typeComp'] = (
        <MethodBox method={""} url={auditRecord?.type.toLowerCase() || "TOOL"}/>
    )

    temp['riskAnalysisComp'] = <ComponentRiskAnalysisBadges componentRiskAnalysis={auditRecord?.componentRiskAnalysis} />;

    temp['apiAccessTypesComp'] = temp?.apiAccessTypes && temp?.apiAccessTypes.length > 0 && temp?.apiAccessTypes.join(', ') ;
    // Preserve the unstripped hostname for child lookups (children store mcpHost = original parent hostname)
    temp['originalResourceName'] = temp?.resourceName;
    temp['resourceName'] = stripDeviceIdFromName(temp?.resourceName, allCollections, temp?.hostCollectionId);
    const { agent, server } = splitAgentAndServer(temp.resourceName);
    temp['aiAgentName'] = agent;
    temp['mcpServerName'] = server;
    temp['lastDetectedComp'] = func.prettifyEpoch(temp?.lastDetected)
    temp['updatedTimestampComp'] = func.prettifyEpoch(temp?.updatedTimestamp)
    temp['approvedAtComp'] = func.prettifyEpoch(temp?.approvedAt)
    temp['expiresAtComp'] = temp?.approvalConditions?.expiresAt ? (() => {
        const expirationDate = new Date(temp.approvalConditions.expiresAt * 1000);
        return expirationDate.toLocaleString('en-US', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            hour12: true,
            timeZone: window.TIME_ZONE === 'Us/Pacific' ? 'America/Los_Angeles' : window.TIME_ZONE
        });
    })() : null
    temp['remarksComp'] = (
        (temp?.remarks === null || temp?.remarks === "" || !temp?.remarks) ? 
            <Text variant="headingSm" color="critical" fontWeight="bold">Pending...</Text> : 
            <VerticalStack gap="1">
                <Text variant="bodyMd">{temp?.remarks}</Text>
                {temp?.approvalConditions && (
                    <Box paddingBlockStart="1">
                        <VerticalStack gap="0">
                            {(() => {
                                const approvalDetails = [
                                    { condition: temp?.approvalConditions?.justification, label: 'Justification', value: temp.approvalConditions.justification },
                                    { condition: temp?.approvedAt, label: 'Approved at', value: temp.approvedAtComp },
                                    { condition: temp?.expiresAtComp, label: 'Expires At', value: temp.expiresAtComp },
                                    { condition: temp?.approvalConditions?.allowedIps, label: 'Allowed IPs', value: temp.approvalConditions.allowedIps?.join(', ') },
                                    { condition: temp?.approvalConditions?.allowedIpRange, label: 'Allowed IP Ranges', value: temp.approvalConditions.allowedIpRange },
                                    { condition: temp?.approvalConditions?.allowedEndpoints, label: 'Allowed Endpoints', value: temp.approvalConditions.allowedEndpoints?.map(ep => ep.name).join(', ') }
                                ];
                                
                                const elements = [];
                                for (let i = 0; i < approvalDetails.length; i++) {
                                    const detail = approvalDetails[i];
                                    if (detail.condition) {
                                        elements.push(
                                            <Text key={i} variant="bodySm" color="subdued">
                                                <Text as="span" fontWeight="medium">{detail.label}:</Text> {detail.value}
                                            </Text>
                                        );
                                    }
                                }
                                return elements;
                            })()}
                        </VerticalStack>
                    </Box>
                )}
            </VerticalStack>
    )
    temp['collectionName'] = (
        <HorizontalStack gap="2" align="center">
            <Text>{stripDeviceIdFromName(collectionName, allCollections, temp?.hostCollectionId)}</Text>
            {collectionRegistry === "available" && <RegistryBadge />}
        </HorizontalStack>
    );
    // Required by GithubRow for tree-style expand/collapse
    temp['id'] = temp.hexId;
    temp['name'] = temp.hexId;
    temp['isTerminal'] = false;
    return temp;
}

function AuditData() {
    const [loading, setLoading] = useState(true);
    const [modalOpen, setModalOpen] = useState(false);
    const [selectedAuditItem, setSelectedAuditItem] = useState(null);
    const [filterVersion, setFilterVersion] = useState(0);

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const collectionsRegistryStatusMap = PersistStore(state => state.collectionsRegistryStatusMap)

    const isEndpointSecurity = isEndpointSecurityCategory();
    const filters = isEndpointSecurity ? filtersEndpointSecurity : filtersDefault;

    function disambiguateLabel(key, value) {
        switch (key) {
            case "type":
            case "markedBy":
            case "apiAccessTypes":
            case "aiAgent":
            case "mcpServer":
                return func.convertToDisambiguateLabelObj(value, null, 2)
            case "collectionName":
                return func.convertToDisambiguateLabelObj(value, collectionsMap, 1)
            default:
                return value;
        }
    }

    // Server-level decisions cascade to every tool/resource/prompt under the same
    // hostCollectionIds. The user can still override an individual tool afterwards
    // by changing that tool's state from the child action menu.
    const cascadeIdsForItem = (item) => (
        item?.type === 'mcp-server' && Array.isArray(item?.groupedHostCollectionIds)
            ? item.groupedHostCollectionIds
            : null
    );

    const updateAuditData = async (item, remarks, allAgents = false) => {
        const allAgentsServer = allAgents ? item?.mcpServerName : null
        await api.updateAuditData(item.hexId, remarks, null, item?.groupedHexIds, cascadeIdsForItem(item), allAgentsServer)
        window.location.reload();
    }

    const updateAuditDataWithConditions = async (hexId, approvalData, hexIds = null, item = null) => {
        const allAgentsServer = item?._allAgents ? item?.mcpServerName : null
        await api.updateAuditData(hexId, null, approvalData, hexIds, cascadeIdsForItem(item), allAgentsServer)
        window.location.reload();
    }

    // Custom colored icons
    const GreenTickIcon = () => <Icon source={CircleTickMajor} tone="success" />;
    const GreenSettingsIcon = () => <Icon source={SettingsMajor} tone="success" />;
    const RedCancelIcon = () => <Icon source={CircleCancelMajor} tone="critical" />;

    const getActionsList = (item) => {
        const sections = [{title: 'Actions', items: [
            {
                content: <span style={{ color: '#008060' }}>Conditionally Approve</span>,
                icon: GreenSettingsIcon,
                onAction: () => {
                    setSelectedAuditItem({ ...item, _allAgents: false });
                    setModalOpen(true);
                },
            },
            {
                content: <span style={{ color: '#008060' }}>Approve</span>,
                icon: GreenTickIcon,
                onAction: () => {updateAuditData(item, "Approved")},
            },
            {
                content: <span style={{ color: '#D72C0D' }}>Block</span>,
                icon: RedCancelIcon,
                onAction: () => {updateAuditData(item, "Rejected")},
                destructive: true
            }
        ]}]

        // Server rows can fan an action across every AI agent using the same MCP
        // server. Tools/resources/prompts stay scoped to their agent variant.
        if (item?.type === 'mcp-server' && item?.mcpServerName) {
            sections.push({ title: 'For all AI agents', items: [
                {
                    content: <span style={{ color: '#008060' }}>Conditionally Approve</span>,
                    icon: GreenSettingsIcon,
                    onAction: () => {
                        setSelectedAuditItem({ ...item, _allAgents: true });
                        setModalOpen(true);
                    },
                },
                {
                    content: <span style={{ color: '#008060' }}>Approve</span>,
                    icon: GreenTickIcon,
                    onAction: () => {updateAuditData(item, "Approved", true)},
                },
                {
                    content: <span style={{ color: '#D72C0D' }}>Block</span>,
                    icon: RedCancelIcon,
                    onAction: () => {updateAuditData(item, "Rejected", true)},
                    destructive: true
                }
            ]})
        }
        return sections
    }

    async function fetchData(sortKey, sortOrder, skip, limit, filterParams, filterOperators, queryValue){
        setLoading(true);
        let ret = []
        let total = 0;
        let finalFilters = {...filterParams}
        finalFilters['lastDetected'] = [startTimestamp, endTimestamp]

        if (isEndpointSecurity) {
            finalFilters['type'] = ['mcp-server']
        } else {
            finalFilters['hostCollectionId'] = (filterParams['collectionName'] || []).map(id => parseInt(id))
            if (finalFilters['hostCollectionId'].length === 0) {
                finalFilters['hostCollectionId'] = Object.keys(collectionsMap).map(id => parseInt(id))
            }
            delete finalFilters['collectionName']
        }

        try {
            const res = await api.fetchAuditData(sortKey, sortOrder, skip, limit, finalFilters, filterOperators, queryValue, isEndpointSecurity)
            if (res && res.auditData) {
                res.auditData.forEach((auditRecord) => {
                    let collectionName = "-";
                    if(collectionsMap[auditRecord?.hostCollectionId]){
                        collectionName = collectionsMap[auditRecord?.hostCollectionId];
                    } else if(auditRecord?.mcpHost !== null && auditRecord?.mcpHost !== ""){
                        collectionName = auditRecord?.mcpHost;
                    }
                    const collectionRegistryStatus = collectionsRegistryStatusMap[auditRecord?.hostCollectionId];
                    const dataObj = convertDataIntoTableFormat(
                        auditRecord,
                        collectionName,
                        collectionRegistryStatus
                    )
                    if (isEndpointSecurity) {
                        dataObj.collapsibleRow = (
                            <MCPChildren
                                parent={dataObj}
                                dateRange={[startTimestamp, endTimestamp]}
                                getActionsList={getActionsList}
                            />
                        );
                    }
                    ret.push(dataObj);
                })
                total = res.total || 0;
            }
        } catch (error) {
        }

        setLoading(false);
        return {value: ret, total: total};
    }

    const fillFilters = async () => {
        if (isEndpointSecurity) {
            try {
                const serversRes = await api.fetchAuditData(
                    'lastDetected', -1, 0, 1000,
                    { type: ['mcp-server'], lastDetected: [startTimestamp, endTimestamp] },
                    {}, '', true
                )
                const allCollections = PersistStore.getState().allCollections;
                const agents = new Set()
                const servers = new Set()
                const markedByUsers = new Set()
                if (serversRes && Array.isArray(serversRes.auditData)) {
                    serversRes.auditData.forEach((rec) => {
                        const stripped = stripDeviceIdFromName(rec?.resourceName, allCollections, rec?.hostCollectionId)
                        const { agent, server } = splitAgentAndServer(stripped)
                        if (agent && agent !== '-') agents.add(agent)
                        if (server && server !== '-') servers.add(server)
                        if (rec?.markedBy) markedByUsers.add(rec.markedBy)
                    })
                }
                filtersEndpointSecurity[0].choices = Array.from(markedByUsers).sort().map(u => ({ label: u, value: u }))
                filtersEndpointSecurity[2].choices = Array.from(agents).sort().map(a => ({ label: a, value: a }))
                filtersEndpointSecurity[3].choices = Array.from(servers).sort().map(s => ({ label: s, value: s }))
            } catch (e) {}
            setFilterVersion(v => v + 1)
        } else {
            const usersResponse = await settingRequests.getTeamData()
            if (usersResponse) {
                filtersDefault[1].choices = usersResponse.map((user) => ({label: user.login, value: user.login}))
            }
            filtersDefault[3].choices = Object.entries(collectionsMap).map(([id, name]) => ({ label: name, value: id }));
        }
    }

    useEffect(() => {
        fillFilters()
    }, [collectionsMap, startTimestamp, endTimestamp])

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
                Audit Data
              </Text>
            }
            isFirstPage={true}
            primaryAction={primaryActions}
            components = {[
                <GithubServerTable
                    key={startTimestamp + endTimestamp + (isEndpointSecurity ? filterVersion : filtersDefault[1].choices.length) + String(isEndpointSecurity)}
                    headers={headings}
                    resourceName={resourceName}
                    appliedFilters={[]}
                    sortOptions={sortOptions}
                    disambiguateLabel={disambiguateLabel}
                    loading={loading}
                    fetchData={fetchData}
                    filters={filters}
                    hideQueryField={false}
                    getStatus={func.getTestResultStatus}
                    useNewRow={true}
                    condensedHeight={true}
                    pageLimit={20}
                    headings={headings}
                    getActions = {(item) => getActionsList(item)}
                    hasRowActions={true}
                />
            ]}
            />
            
            <ConditionalApprovalModal
                isOpen={modalOpen}
                onClose={() => {
                    setModalOpen(false);
                    setSelectedAuditItem(null);
                }}
                onApprove={updateAuditDataWithConditions}
                auditItem={selectedAuditItem}
            />
        </>
    )
}

export default AuditData
