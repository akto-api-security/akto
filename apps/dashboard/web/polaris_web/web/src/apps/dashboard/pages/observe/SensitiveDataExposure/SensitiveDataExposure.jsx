import { Text, Button, HorizontalStack, Badge, Box } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useReducer, useState } from "react"
import values from "@/util/values";
import {produce} from "immer"
import api from "../api"
import func from "@/util/func"
import { useParams } from "react-router-dom"
import PersistStore from "../../../../main/PersistStore"
import DateRangeFilter from "../../../components/layouts/DateRangeFilter"
import GetPrettifyEndpoint from "../GetPrettifyEndpoint";
import TooltipText from "../../../components/shared/TooltipText";
import SaveAsCollectionModal from "../api_collections/api_query_component/SaveAsCollectionModal";
import { getDashboardCategory, mapLabel } from "../../../../main/labelHelper";

const headings = [
    {
        text: "Endpoint",
        value: "endpointComp",
        title: mapLabel("API endpoints", getDashboardCategory()),
        textValue: "endpoint",
        filterKey: 'endpoint'
    },
    {
        title: 'Key',
        value: 'keyValueComp',
        text: 'Key',
        textValue: 'keyValue',
        filterKey: 'keyValue'
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
        filterKey: 'apiCollectionId'
    },
    {
        title: 'Discovered',
        text: "Discovered",
        value: "detected_timestamp",
        sortActive: true,
        sortKey: 'timestamp'
    }
]

let headers = JSON.parse(JSON.stringify(headings))
headers.push({
    text: 'collectionIds',
    filterKey: 'collectionIds',
})

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
    label: mapLabel('API', getDashboardCategory()) + ' call',
    title: mapLabel('API', getDashboardCategory()) + ' call',
    choices: [
        {
            label:"In request",
            value:"request"
        },
        {
            label:"In response",
            value:"response"
        }],
  },
  {
    key:'location',
    label:'Location',
    title:'Location',
    choices:[
        {label:"Header", value:"header"},
        {label:"Payload", value:"payload"},
        {label:"URL param", value:"urlParam"},
        {label:"Query param", value:"queryParam"}
    ],
  },
  {
    key: 'collectionIds',
    label: mapLabel('API', getDashboardCategory()) + ' groups',
    title: mapLabel('API', getDashboardCategory()) + ' groups',
    choices: [],
  }
]

const resourceName = {
    singular: mapLabel('endpoint', getDashboardCategory()) + ' with sensitive data',
    plural: mapLabel('endpoints', getDashboardCategory()) + ' with sensitive data',
};

const convertDataIntoTableFormat = (endpoint, apiCollectionMap) => {
    let temp = {}
    const key = func.findLastParamField(endpoint.param)
    // const value = endpoint?.values?.elements?.length > 0 ? endpoint.values.elements[0] : ""
    const id = endpoint.method + "_" + endpoint.url + "_" + endpoint.param
    const uniqueKey = `${id}_${endpoint.timestamp}_${endpoint.responseCode}_${endpoint.isHeader}_${endpoint.isUrlParam}`
    temp['id'] = uniqueKey
    temp['endpoint'] = id;
    temp['url'] = endpoint.url
    temp['method'] = endpoint.method
    temp['collection'] = apiCollectionMap[endpoint.apiCollectionId]
    temp['apiCollectionId'] = endpoint.apiCollectionId
    temp['detected_timestamp'] = func.prettifyEpoch(endpoint.timestamp)
    temp['timestamp'] = endpoint.timestamp
    temp['location'] = (endpoint.isHeader ? "Header" : (endpoint.isUrlParam ? "URL param" : (endpoint.isQueryParam ? "Query param" : "Payload")))
    temp['isHeader'] = endpoint.isHeader
    temp["paramLocation"] = endpoint.responseCode < 0 ? "Request" : "Response"
    temp['keyValue'] = key
    temp['endpointComp'] = <GetPrettifyEndpoint key={uniqueKey} maxWidth="300px" method={endpoint.method} url={endpoint.url} />
    temp["call"] = endpoint.responseCode < 0 ? "Request" : "Response"
    temp['keyValueComp'] = (
        <Badge key={uniqueKey} status="critical" size="slim">
            <Box maxWidth="270px">
                <HorizontalStack gap={"1"} wrap={false}>
                    <Box as="span" maxWidth="180px">
                        <TooltipText tooltip={key} text={key} />
                    </Box>
                    {/* :
                    <Box maxWidth="150px">
                        <TooltipText tooltip={value} text={value}/>
                    </Box> */}
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
    const [modal, setModal] = useState(false)

    const filtersMap = PersistStore(state => state.filtersMap)

    const handleCreateCollection = async(collectionName) => {
        const filterOperators = {
            "endpoint": "OR",
            "keyValue": "OR",
            "location": "OR",
            "apiCollectionId": "OR",
            "timestamp": "OR",
            "collectionIds": "OR",
            "subType": "OR"
        }
        setLoading(true)
        const apiInfosSet = new Set()
        let apisList = []
        let filters = {
            "subType": [subType],
            "apiCollectionId": [],
            "location": [],
            "collectionIds": []
        }
        filters['subType'] = [subType]
        await api.fetchChanges('timestamp', -1, 0, 100000, filters, filterOperators, startTimestamp, endTimestamp, true, []).then((res) => {
            
            res.endpoints.forEach(x => {
                let stringId = x.apiCollectionId + "####" + x.method + "###" + x.url
                if(apiInfosSet.has(stringId)){
                    return;
                }
                let apiInfo = {
                    apiCollectionId: x.apiCollectionId,
                    method: x.method,
                    url: x.url,
                }
                apisList.push(apiInfo)
                apiInfosSet.add(stringId)
            })
        })

        api.addApisToCustomCollection(apisList,collectionName).then((resp) => {
            try {
                func.setToast(true, false, `Saved ${apiInfosSet.size} urls in group`)
            } catch (error) {
                func.setToast(true, true, error)
            }
        })

        setLoading(false)
        setModal(false)
        
    }

    const [currDateRange, dispatchCurrDateRange] = useReducer(produce((draft, action) => func.dateRangeReducer(draft, action)), values.ranges[5]);
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
                return func.convertToDisambiguateLabelObj(value, null, 2)
            default:
                return value;
        }
    }

    filters = func.getCollectionFilters(filters)
    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);
        const isRequestValues = filters?.hasOwnProperty('isRequest') ? (filters.isRequest || []) : [];
        delete filters['isRequest']
        filters['subType'] = [subType]
        filterOperators['subType']="OR"
        let ret = []
        let total = 0;
        await api.fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, startTimestamp, endTimestamp, true, isRequestValues, queryValue).then((res)=>{
            res.endpoints.forEach((endpoint) => {
                const dataObj = convertDataIntoTableFormat(endpoint, apiCollectionMap)
                ret.push(dataObj);
            })
            total += res.total;
            setLoading(false);
        })

        setLoading(false);
        return {value: ret, total: total};
    }

const handleReset = async () => {
    await api.resetDataTypeRetro(subType)
    func.setToast(true, false, "Resetting data types")
}

const primaryActions = (
    <HorizontalStack gap={"2"}>
        <Button id={"reset-data-type"} onClick={handleReset}>Reset</Button>
        <DateRangeFilter initialDispatch = {currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})}/>
        <Button id={"all-data-types"} primary onClick={() => setModal(!modal)}>Create API group</Button>
    </HorizontalStack>
)

    return (
        <PageWithMultipleCards
        title={
            <Text as="div" variant="headingLg">
            {`${mapLabel('API endpoints', getDashboardCategory())} with ${subType}` }
          </Text>
        }
        backUrl="/dashboard/observe/sensitive"
        primaryAction={primaryActions}
        components = {[
            <GithubServerTable
                key={startTimestamp + endTimestamp}
                headers={headers}
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
            />,
            <SaveAsCollectionModal
                key="modal"
                active={modal}
                setActive={setModal}
                createNewCollection={handleCreateCollection}
                initialCollectionName={'APIs with ' + subType}
                loading={loading}
            />
        ]}
        />
    )
}

export default SensitiveDataExposure