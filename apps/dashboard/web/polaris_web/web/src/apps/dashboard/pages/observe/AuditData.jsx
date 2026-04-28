
import { Text, HorizontalStack, VerticalStack, Box } from "@shopify/polaris"
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
import settingRequests from "../settings/api";
import PersistStore from "../../../main/PersistStore";
import ConditionalApprovalModal from "../../components/modals/ConditionalApprovalModal";
import RegistryBadge from "../../components/shared/RegistryBadge";
import ComponentRiskAnalysisBadges from "./components/ComponentRiskAnalysisBadges";
import AuditDataDrawer from "./AuditDataDrawer";

const headings = [
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
    },
    {
        title: 'AI Agent',
        text: 'AI Agent',
        value: 'aiAgentName',
        type: CellType.TEXT,
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
]

const sortOptions = [
    { label: 'Last Detected', value: 'lastDetected asc', directionLabel: 'Oldest', sortKey: 'lastDetected', columnIndex: 3 },
    { label: 'Last Detected', value: 'lastDetected desc', directionLabel: 'Newest', sortKey: 'lastDetected', columnIndex: 3 },
    { label: 'Updated', value: 'updatedTimestamp asc', directionLabel: 'Oldest', sortKey: 'updatedTimestamp', columnIndex: 4 },
    { label: 'Updated', value: 'updatedTimestamp desc', directionLabel: 'Newest', sortKey: 'updatedTimestamp', columnIndex: 4 },
   
];

let filters = [
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
    const [showDrawer, setShowDrawer] = useState(false);
    // Scope of the in-flight conditional-approval modal: 'server' | 'agent' | 'children'.
    const [conditionalScope, setConditionalScope] = useState('server');
    // For the 'children' scope, the actual child records the user selected in the drawer.
    const [conditionalChildren, setConditionalChildren] = useState(null);

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")
    const collectionsMap = PersistStore(state => state.collectionsMap)
    const collectionsRegistryStatusMap = PersistStore(state => state.collectionsRegistryStatusMap)

    function disambiguateLabel(key, value) {
        switch (key) {
            case "markedBy":
            case "apiAccessTypes":
            case "aiAgent":
            case "mcpServer":
                return func.convertToDisambiguateLabelObj(value, null, 2)
            default:
                return value;
        }
    }

    const cascadeIdsForItem = (item) => (
        item?.type === 'mcp-server' && Array.isArray(item?.groupedHostCollectionIds)
            ? item.groupedHostCollectionIds
            : null
    );

    const handleRowClick = (rowData) => {
        setSelectedAuditItem(rowData);
        setShowDrawer(true);
    }

    const handleAfterDrawerUpdate = (scope) => {
        // Server-scope mutations affect rows beyond the drawer; reload to resync the
        // parent table. Children mutations stay inside the drawer, which refetches
        // its own list — no reload needed.
        if (scope === 'server') {
            window.location.reload();
        }
    }

    const handleRequestConditional = (scope, item, selectedChildren) => {
        setSelectedAuditItem(item);
        setConditionalScope(scope);
        setConditionalChildren(scope === 'children' ? selectedChildren : null);
        setModalOpen(true);
    }

    const updateAuditDataWithConditions = async (_hexId, approvalData, _hexIds, _item) => {
        try {
            if (conditionalScope === 'children') {
                if (!Array.isArray(conditionalChildren) || conditionalChildren.length === 0) return
                await Promise.all(conditionalChildren.map((child) =>
                    api.updateAuditData(child.hexId, null, approvalData, child.groupedHexIds, null, null)
                ))
                window.location.reload()
            } else {
                const item = selectedAuditItem
                await api.updateAuditData(
                    item?.hexId,
                    null,
                    approvalData,
                    item?.groupedHexIds,
                    cascadeIdsForItem(item),
                    null
                )
                window.location.reload()
            }
        } catch (e) {
            func.setToast(true, true, 'Failed to apply conditional approval')
        }
    }

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);
        let ret = []
        let total = 0;
        let finalFilters = {...filters}
        finalFilters['lastDetected'] = [startTimestamp, endTimestamp]
        // Parent rows are MCP servers; tools/resources/prompts surface via expansion.
        finalFilters['type'] = ['mcp-server']

        try {
            // Backend dedupes by (agent, server) and returns one canonical record per
            // group, with `groupedHostCollectionIds` listing every member collection.
            const res = await api.fetchAuditData(sortKey, sortOrder, skip, limit, finalFilters, filterOperators, queryValue, true)
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
        const usersResponse = await settingRequests.getTeamData()
        if (usersResponse) {
            filters[0].choices = usersResponse.map((user) => ({label: user.login, value: user.login}))
        }

        // Pull the deduped server list to seed AI Agent / MCP Server choices.
        try {
            const serversRes = await api.fetchAuditData(
                'lastDetected', -1, 0, 1000,
                { type: ['mcp-server'], lastDetected: [startTimestamp, endTimestamp] },
                {}, '', true
            )
            const allCollections = PersistStore.getState().allCollections;
            const agents = new Set()
            const servers = new Set()
            if (serversRes && Array.isArray(serversRes.auditData)) {
                serversRes.auditData.forEach((rec) => {
                    const stripped = stripDeviceIdFromName(rec?.resourceName, allCollections, rec?.hostCollectionId)
                    const { agent, server } = splitAgentAndServer(stripped)
                    if (agent && agent !== '-') agents.add(agent)
                    if (server && server !== '-') servers.add(server)
                })
            }
            filters[2].choices = Array.from(agents).sort().map(a => ({ label: a, value: a }))
            filters[3].choices = Array.from(servers).sort().map(s => ({ label: s, value: s }))
        } catch (e) {
            // leave choices empty on failure; user can still search/filter elsewhere
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
                    key={startTimestamp + endTimestamp + filters[0].choices.length}
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
                    onRowClick={handleRowClick}
                    rowClickable={true}
                />
            ]}
            />

            <AuditDataDrawer
                auditItem={selectedAuditItem}
                show={showDrawer}
                setShow={setShowDrawer}
                startTimestamp={startTimestamp}
                endTimestamp={endTimestamp}
                onRequestConditional={handleRequestConditional}
                onAfterUpdate={handleAfterDrawerUpdate}
            />

            <ConditionalApprovalModal
                isOpen={modalOpen}
                onClose={() => {
                    setModalOpen(false);
                    setConditionalChildren(null);
                }}
                onApprove={updateAuditDataWithConditions}
                auditItem={selectedAuditItem}
            />
        </>
    )
}

export default AuditData
