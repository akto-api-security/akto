import { Text, Button, InlineStack, Badge, Box } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useReducer, useState } from "react"
import values from "@/util/values";
import {produce} from "immer"
import api from "../api"
import func from "@/util/func"
import { useParams, useSearchParams } from "react-router-dom"
import PersistStore from "../../../../main/PersistStore"
import DateRangeFilter from "../../../components/layouts/DateRangeFilter"
import GetPrettifyEndpoint from "../GetPrettifyEndpoint";
import TooltipText from "../../../components/shared/TooltipText";
import SaveAsCollectionModal from "../api_collections/api_query_component/SaveAsCollectionModal";

const headings = [
    {
        text: "Endpoint",
        value: "endpointComp",
        title: "API endpoints",
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

const resourceName = {
    singular: 'endpoint with sensitive data',
    plural: 'endpoints with sensitive data',
};

const convertDataIntoTableFormat = (endpoint, apiCollectionMap) => {
    let temp = {}
    const key = func.findLastParamField(endpoint.param)
    // const value = endpoint?.values?.elements?.length > 0 ? endpoint.values.elements[0] : ""
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
    temp['keyValue'] = key
    temp['endpointComp'] = <GetPrettifyEndpoint key={id} maxWidth="300px" method={endpoint.method} url={endpoint.url} />
    temp["call"] = endpoint.responseCode < 0 ? "Request" : "Response"
    temp['keyValueComp'] = (
        <Badge key={id} tone="critical" size="slim">
            <Box maxWidth="270px">
                <InlineStack gap={"100"} wrap={false}>
                    <Box as="span" maxWidth="180px">
                        <TooltipText tooltip={key} text={key} />
                    </Box>
                    {/* :
                    <Box maxWidth="150px">
                        <TooltipText tooltip={value} text={value}/>
                    </Box> */}
                </InlineStack>
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

    const [searchParams] = useSearchParams();
    const filterParams = searchParams.get('filters')
    let initialValForResponseFilter = false
    if(filterParams && filterParams !== undefined &&filterParams.split('isRequest').length > 1){
        let isRequestVal =  filterParams.split("isRequest__")[1].split('&')[0]
        if(isRequestVal.length > 0){
            initialValForResponseFilter = (isRequestVal === 'true' || isRequestVal.includes('true'))
        }
    }

    const appliedFilters = [
        {
            key: 'isRequest',
            label: 'In response',
            value: [initialValForResponseFilter],
            onRemove: () => {}
        }
    ]

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
        await api.fetchChanges('timestamp', -1, 0, 100000, [], filterOperators, startTimestamp, endTimestamp, true, false).then((res) => {
            
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
                return value[0] ? "In request" : "In response"
            default:
                return value;
        }
    }

    filters = func.getCollectionFilters(filters)
    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);
        let isRequest = (filters && filters['isRequest'] !== undefined && filters['isRequest'][0]) || initialValForResponseFilter;
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

const handleReset = async () => {
    await api.resetDataTypeRetro(subType)
    func.setToast(true, false, "Resetting data types")
}

const primaryActions = (
    <InlineStack gap={"200"}>
        <Button id={"reset-data-type"} onClick={handleReset}>Reset</Button>
        <DateRangeFilter initialDispatch = {currDateRange} dispatch={(dateObj) => dispatchCurrDateRange({type: "update", period: dateObj.period, title: dateObj.title, alias: dateObj.alias})}/>
        <Button id={"all-data-types"}  onClick={() => setModal(!modal)} variant="primary">Create API group</Button>
    </InlineStack>
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