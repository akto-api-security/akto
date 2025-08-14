
import { Text, HorizontalStack, Badge, Box } from "@shopify/polaris"
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
import { CircleTickMajor, CircleCancelMajor } from "@shopify/polaris-icons";
import settingRequests from "../settings/api";

const headings = [
    {
        title: 'Type',
        value: 'typeComp',
        text: 'Type',
    },
    {
        text: "Resource Name",
        value: "resourceName",
        title: "Resource Name",
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
    { label: 'Last Detected', value: 'lastDetected desc', directionLabel: 'Newest', sortKey: 'lastDetected', columnIndex: 3 },
    { label: 'Last Detected', value: 'lastDetected asc', directionLabel: 'Oldest', sortKey: 'lastDetected', columnIndex: 3 },
    { label: 'Updated', value: 'updatedTimestamp desc', directionLabel: 'Newest', sortKey: 'updatedTimestamp', columnIndex: 4 },
    { label: 'Updated', value: 'updatedTimestamp asc', directionLabel: 'Oldest', sortKey: 'updatedTimestamp', columnIndex: 4 },
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
    }
]

const resourceName = {
    singular: 'audit record',
    plural: 'audit records',
};

const convertDataIntoTableFormat = (auditRecord) => {
    let temp = {...auditRecord}
    temp['typeComp'] = (
        <MethodBox method={""} url={auditRecord?.type || "TOOL"}/>
    )
    
    temp['apiAccessTypesComp'] = temp?.apiAccessTypes && temp?.apiAccessTypes.length > 0 && (
        <Box maxWidth="200px">
            <HorizontalStack gap="1" wrap>
                {temp?.apiAccessTypes && temp?.apiAccessTypes.map((accessType, index) => (
                    <Badge key={index} status="success" size="slim">
                        {accessType.toUpperCase()}
                    </Badge>
                ))}
            </HorizontalStack>
        </Box>
    )
    temp['lastDetectedComp'] = func.prettifyEpoch(temp?.lastDetected)
    temp['updatedTimestampComp'] = func.prettifyEpoch(temp?.updatedTimestamp)
    temp['remarksComp'] = (
        temp?.remarks === undefined ? <Text variant="headingSm" color="critical">Pending...</Text> : <Text variant="bodyMd">{temp?.remarks}</Text>
    )
    
    return temp;
}

function AuditData() {
    const [loading, setLoading] = useState(true);

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    function disambiguateLabel(key, value) {
        switch (key) {
            case "type":
            case "markedBy":
            case "apiAccessTypes":
                return func.convertToDisambiguateLabelObj(value, null, 2)
            default:
                return value;
        }
    }

    const updateAuditData = async (hexId, remarks) => {
        const res = await api.updateAuditData(hexId, remarks)
        if (res && res.success) {
            func.setToast(true, true, "Audit data updated successfully")
        }
    }

    const getActionsList = (item) => {
        return [{title: 'Actions', items: [
            {
                content: 'Mark as resolved',
                icon: CircleTickMajor,
                onAction: () => {updateAuditData(item.hexId, "Approved")},
            },
            {
                content: 'Disapprove',
                icon: CircleCancelMajor,
                onAction: () => {updateAuditData(item.hexId, "Rejected")},
            }
        ]}]
    }

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);
        let ret = []
        let total = 0;
        let finalFilters = {...filters}
        finalFilters['lastDetected'] = [startTimestamp, endTimestamp]
        
        try {
            const res = await api.fetchAuditData(sortKey, sortOrder, skip, limit, finalFilters, filterOperators)
            if (res && res.auditData) {
                res.auditData.forEach((auditRecord) => {
                    const dataObj = convertDataIntoTableFormat(auditRecord)
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
    }

    useEffect(() => {
        fillFilters()
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
    )
}

export default AuditData
