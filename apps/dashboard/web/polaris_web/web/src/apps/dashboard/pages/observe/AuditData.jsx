
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
import { CircleTickMajor, CircleCancelMajor, SettingsMajor } from "@shopify/polaris-icons";
import { Icon } from "@shopify/polaris";
import settingRequests from "../settings/api";
import PersistStore from "../../../main/PersistStore";
import ConditionalApprovalModal from "../../components/modals/ConditionalApprovalModal";
import RegistryBadge from "../../components/shared/RegistryBadge";

const headings = [
    {
        title: 'Type',
        value: 'typeComp',
        text: 'Type',
    },
    {
        text: "MCP component name",
        value: "resourceName",
        title: "MCP component name"
    },
    {
        text: "Collection name",
        value: "collectionName",
        title: "Collection name",
        type: CellType.TEXT
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

let filters = [
    {
        key: 'type',
        label: 'Type',
        title: 'Type',
        choices: [
            { label: "Tool", value: "TOOL" },
            { label: "Resource", value: "RESOURCE" },
            { label: "Prompt", value: "PROMPT" },
            { label: "Server", value: "SERVER" }
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

const convertDataIntoTableFormat = (auditRecord, collectionName, collectionRegistry) => {
    const allCollections = PersistStore.getState().allCollections;
    let temp = {...auditRecord}
    temp['typeComp'] = (
        <MethodBox method={""} url={auditRecord?.type.toLowerCase() || "TOOL"}/>
    )
    
    temp['apiAccessTypesComp'] = temp?.apiAccessTypes && temp?.apiAccessTypes.length > 0 && temp?.apiAccessTypes.join(', ') ;
    temp['resourceName'] = stripDeviceIdFromName(temp?.resourceName, allCollections, temp?.hostCollectionId);
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
    return temp;
}

function AuditData() {
    const [loading, setLoading] = useState(true);
    const [modalOpen, setModalOpen] = useState(false);
    const [selectedAuditItem, setSelectedAuditItem] = useState(null);

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
            case "type":
            case "markedBy":
            case "apiAccessTypes":
                return func.convertToDisambiguateLabelObj(value, null, 2)
            case "collectionName":
                return func.convertToDisambiguateLabelObj(value, collectionsMap, 1)
            default:
                return value;
        }
    }

    const updateAuditData = async (hexId, remarks) => {
        await api.updateAuditData(hexId, remarks)
        window.location.reload();
    }

    const updateAuditDataWithConditions = async (hexId, approvalData) => {
        await api.updateAuditData(hexId, null, approvalData)
        window.location.reload();
    }

    // Custom colored icons
    const GreenTickIcon = () => <Icon source={CircleTickMajor} tone="success" />;
    const GreenSettingsIcon = () => <Icon source={SettingsMajor} tone="success" />;
    const RedCancelIcon = () => <Icon source={CircleCancelMajor} tone="critical" />;

    const getActionsList = (item) => {
        return [{title: 'Actions', items: [
            {
                content: <span style={{ color: '#008060' }}>Conditional Approval</span>,
                icon: GreenSettingsIcon,
                onAction: () => {
                    setSelectedAuditItem(item);
                    setModalOpen(true);
                },
            },
            {
                content: <span style={{ color: '#008060' }}>Mark as resolved</span>,
                icon: GreenTickIcon,
                onAction: () => {updateAuditData(item.hexId, "Approved")},
            },
            {
                content: <span style={{ color: '#D72C0D' }}>Disapprove</span>,
                icon: RedCancelIcon,
                onAction: () => {updateAuditData(item.hexId, "Rejected")},
                destructive: true
            }
        ]}]
    }

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);
        let ret = []
        let total = 0;
        let finalFilters = {...filters}
        finalFilters['lastDetected'] = [startTimestamp, endTimestamp]
        finalFilters['hostCollectionId'] = filters['collectionName'].map(id => parseInt(id)) || Object.keys(collectionsMap).map(id => parseInt(id))
        delete finalFilters['collectionName']

        try {
            const res = await api.fetchAuditData(sortKey, sortOrder, skip, limit, finalFilters, filterOperators)
            if (res && res.auditData) {
                res.auditData.forEach((auditRecord) => {
                    // Get collection name and registry status from separate maps
                    const collectionName = collectionsMap[auditRecord?.hostCollectionId] || "Unknown Collection";
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
            console.error("Error fetching audit data:", error)
        }
        
        setLoading(false);
        return {value: ret, total: total};
    }

    const fillFilters = async () => {
        const usersResponse = await settingRequests.getTeamData()
        if (usersResponse) {
            filters[1].choices = usersResponse.map((user) => ({label: user.login, value: user.login}))
        }
        filters[3].choices = Object.entries(collectionsMap).map(([id, name]) => ({ label: name, value: id }));
    }

    useEffect(() => {
        fillFilters()
    }, [collectionsMap])

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
            backUrl="/dashboard/observe"
            primaryAction={primaryActions}
            components = {[
                <GithubServerTable
                    key={startTimestamp + endTimestamp + filters[1].choices.length}
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
