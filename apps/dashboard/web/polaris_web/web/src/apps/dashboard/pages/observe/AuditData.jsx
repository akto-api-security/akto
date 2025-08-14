
import { Text, HorizontalStack, Badge, Box } from "@shopify/polaris"
import { useReducer, useState } from "react"
import values from "@/util/values";
import {produce} from "immer"
import api from "./api"
import func from "@/util/func"
import TooltipText from "../../components/shared/TooltipText";
import DateRangeFilter from "../../components/layouts/DateRangeFilter";
import PageWithMultipleCards from "../../components/layouts/PageWithMultipleCards";
import GithubServerTable from "../../components/tables/GithubServerTable";

const headings = [
    {
        title: 'Type',
        value: 'typeComp',
        text: 'Type',
        textValue: 'type',
        filterKey: 'type'
    },
    {
        text: "Resource Name",
        value: "resourceName",
        title: "Resource Name",
    },
    {
        title: 'Last Detected',
        text: "Last Detected",
        value: "lastDetectedComp",
        sortActive: true,
        sortKey: 'lastDetected'
    },
    {
        title: 'Updated',
        text: "Updated",
        value: "updatedTimestampComp",
        sortKey: 'updatedTimestamp'
    },
    {
        title: 'Access Types',
        text: "Access Types",
        value: "apiAccessTypesComp",
        filterKey: 'apiAccessTypes'
    },
    {
        title: 'Remarks',
        text: "Remarks",
        value: "remarks"
    },
    {
        title: 'Marked By',
        text: "Marked By",
        value: "markedBy",
        filterKey: 'markedBy'
    },
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
    let temp = {}
    const id = auditRecord.id
    temp['id'] = id
    temp['resourceName'] = auditRecord.resourceName || "N/A"
    temp['type'] = auditRecord.type || "N/A"
    temp['markedBy'] = auditRecord.markedBy || "N/A"
    temp['lastDetected'] = auditRecord.lastDetected || "N/A"
    temp['updatedTimestamp'] = auditRecord.updatedTimestamp || "N/A"
    temp['remarks'] = auditRecord.remarks || "N/A"
    temp['apiAccessTypes'] = auditRecord.apiAccessTypes || []
    
    temp['typeComp'] = (
        
    )
    
    temp['apiAccessTypesComp'] = (
        <Box maxWidth="200px">
            <HorizontalStack gap="1" wrap>
                {temp['apiAccessTypes'].map((accessType, index) => (
                    <Badge key={index} status="success" size="slim">
                        {accessType}
                    </Badge>
                ))}
            </HorizontalStack>
        </Box>
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

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);
        let ret = []
        let total = 0;
        
        try {
            const res = await api.fetchAuditData(sortKey, sortOrder, skip, limit, filters, filterOperators)
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
                getStatus={func.getTestResultStatus}
                useNewRow={true}
                condensedHeight={true}
                pageLimit={20}
                headings={headings}
            />
        ]}
        />
    )
}

export default AuditData
