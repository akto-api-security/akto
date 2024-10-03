import { Text, Button, HorizontalStack, Badge, Box } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useReducer, useState } from "react"
import values from "@/util/values";
import {produce} from "immer"
import api from "../api"
import func from "@/util/func"
import { useNavigate, useParams } from "react-router-dom"
import PersistStore from "../../../../main/PersistStore"
import DateRangeFilter from "../../../components/layouts/DateRangeFilter"
import GetPrettifyEndpoint from "../GetPrettifyEndpoint";
import TooltipText from "../../../components/shared/TooltipText";

const headers = [
    {
        text: "Endpoint",
        value: "endpointComp",
        title: "Api endpoints",
        textValue: "endpoint",
    },
    {
        title: 'Key & Value',
        value: 'keyValueComp',
        text: 'Key & Value',
        textValue: 'keyValue',
    },
    {
        title: 'Detected in',
        text: "Location",
        value: "location",
    },
    {
        text: "Collection",
        value: "collection",
        title: 'Collection',
    },
    {
        title: 'Discovered',
        text: "Discovered",
        value: "detected_timestamp",
        sortActive: true,
        sortKey: 'timestamp'
    }
]

const sortOptions = [
    { label: 'Discovered time', value: 'timestamp asc', directionLabel: 'Newest', sortKey: 'timestamp', columnIndex:5 },
    { label: 'Discovered time', value: 'timestamp desc', directionLabel: 'Oldest', sortKey: 'timestamp', columnIndex:5 },
    
];

let filters = [
    {
    key: 'apiCollectionId',
    label: 'Collection',
    title: 'Collection',
    choices: [],
  },
  {
    key: 'isRequest',
    label: 'API call',
    title: 'API call',
    choices: [
        {
            label:"In request",
            value:true
        },
        {
            label:"In response",
            value:false
        }],
    singleSelect:true
  },
  {
    key:'location',
    label:'Location',
    title:'Location',
    choices:[
        {label:"Header", value:"header"},
        {label:"Payload", value:"payload"},
        {label:"URL param", value:"urlParam"}
    ],
  },
  {
    key: 'collectionIds',
    label: 'API groups',
    title: 'API groups',
    choices: [],
  }
]

const appliedFilters = [
    {
        key: 'isRequest',
        label: 'In response',
        value: [false],
        onRemove: () => {}
    }
]

const resourceName = {
    singular: 'endpoint with sensitive data',
    plural: 'endpoints with sensitive data',
};

const convertDataIntoTableFormat = (endpoint, apiCollectionMap) => {
    let temp = {}
    const key = func.findLastParamField(endpoint.param)
    const value = endpoint?.values?.elements?.length > 0 ? endpoint.values.elements[0] : ""
    const id = endpoint.method + " " + endpoint.url
    temp['id'] = id
    temp['endpoint'] = id;
    temp['url'] = endpoint.url
    temp['method'] = endpoint.method
    temp['collection'] = apiCollectionMap[endpoint.apiCollectionId]
    temp['apiCollectionId'] = endpoint.apiCollectionId
    temp['detected_timestamp'] = func.prettifyEpoch(endpoint.timestamp)
    temp['timestamp'] = endpoint.timestamp
    temp['location'] = (endpoint.isHeader ? "Header" : (endpoint.isUrlParam ? "URL param" : "Payload"))
    temp['isHeader'] = endpoint.isHeader
    temp["paramLocation"] = endpoint.responseCode < 0 ? "Request" : "Response"
    temp['keyValue'] = key + ": " + value
    temp['endpointComp'] = <GetPrettifyEndpoint key={id} method={endpoint.method} url={endpoint.url} />
    temp['keyValueComp'] = (
        <Badge key={id} status="critical" size="slim">
            <Box maxWidth="270px">
                <HorizontalStack gap={"1"} wrap={false}>
                    <Box as="span" maxWidth="100px">
                        <TooltipText tooltip={key} text={key} />
                    </Box>
                    :
                    <Box maxWidth="150px">
                        <TooltipText tooltip={value} text={value}/>
                    </Box>
                </HorizontalStack>
            </Box>
        </Badge>
    )
    temp['nextUrl'] = "/dashboard/observe/sensitive/"+endpoint?.subTypeString +"/"+temp['apiCollectionId'] + "/" + btoa(endpoint.url + " " + endpoint.method);
    return temp;
}

function SensitiveDataExposure() {
    const [loading, setLoading] = useState(true);
    const params = useParams()
    const subType = params.subType;
    const apiCollectionMap = PersistStore(state => state.collectionsMap)

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[3]);
    const getTimeEpoch = (key) => {
        return Math.floor(Date.parse(currDateRange.period[key]) / 1000)
    }

    const startTimestamp = getTimeEpoch("since")
    const endTimestamp = getTimeEpoch("until")

    function disambiguateLabel(key, value) {
        switch (key) {
            case "location":
                return func.convertToDisambiguateLabelObj(value, null, 2)
            case "collectionIds": 
            case "apiCollectionId": 
            return func.convertToDisambiguateLabelObj(value, apiCollectionMap, 2)
            case "isRequest":
                return value[0] ? "In request" : "In response"
            default:
                return value;
        }
    }

    filters = func.getCollectionFilters(filters)

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);
        let isRequest = filters['isRequest'][0] || false;
        delete filters['isRequest']
        filters['subType'] = [subType]
        filterOperators['subType']="OR"
        let ret = []
        let total = 0; 
        await api.fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, startTimestamp, endTimestamp, true,isRequest).then((res)=> {
            res.endpoints.forEach((endpoint) => {
                const dataObj = convertDataIntoTableFormat(endpoint, apiCollectionMap)
                ret.push(dataObj);
            })
            total = res.total;
            setLoading(false);
        })
        return {value:ret , total:total};
    }

const navigate = useNavigate();

const handleRedirect = () => {
    navigate("/dashboard/observe/data-types")
}

const handleReset = async () => {
    await api.resetDataTypeRetro(subType)
    func.setToast(true, false, "Resetting data types")
}

const primaryActions = (
    <HorizontalStack gap={"2"}>
        <Button id={"reset-data-type"} onClick={handleReset}>Reset</Button>
        <DateRangeFilter initialDispatch = {currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})}/>
        <Button id={"all-data-types"} primary onClick={handleRedirect}>Create custom data types</Button>
    </HorizontalStack>
)

    return (
        <PageWithMultipleCards
        title={
            <Text as="div" variant="headingLg">
            {`Endpoints with ${subType}` }
          </Text>
        }
        backUrl="/dashboard/observe/sensitive"
        primaryAction={primaryActions}
        components = {[
            <GithubServerTable
                key={startTimestamp + endTimestamp}
                headers={headers}
                resourceName={resourceName} 
                appliedFilters={appliedFilters}
                sortOptions={sortOptions}
                disambiguateLabel={disambiguateLabel}
                loading={loading}
                fetchData={fetchData}
                filters={filters}
                hideQueryField={true}
                getStatus={func.getTestResultStatus}
                useNewRow={true}
                condensedHeight={true}
                pageLimit={20}
                headings={headers}
            />
        ]}
        />
    )
}

export default SensitiveDataExposure