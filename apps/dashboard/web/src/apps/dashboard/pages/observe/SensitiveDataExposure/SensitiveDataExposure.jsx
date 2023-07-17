import { Text, Button } from "@shopify/polaris"
import PageWithMultipleCards from "../../../components/layouts/PageWithMultipleCards"
import GithubServerTable from "../../../components/tables/GithubServerTable"
import { useState } from "react"
import api from "../api"
import Store from "../../../store"
import func from "@/util/func"
import { useNavigate, useParams } from "react-router-dom"
import {
    SearchMinor,
    FraudProtectMinor  } from '@shopify/polaris-icons';

const headers = [
    {
        text: "Endpoint",
        value: "url",
        itemOrder: 1,
    },
    {
        text: "Collection",
        value: "collection",
        itemOrder: 3,
        icon: FraudProtectMinor,
    },
    {
        text: "API Collection ID",
        value: "apiCollectionId",
    },
    {
        text: "Discovered",
        value: "detected_timestamp",
        itemOrder: 3,
        icon: SearchMinor,
    },
    {
        text: "Timestamp",
        value: "timestamp",
    },
    {
        text: "Location",
        value: "location",
        itemOrder: 3,
        icon: SearchMinor,
    },
    {
        text: "API call",
        value: "isRequest",
    },
]

const sortOptions = [
    { label: 'Discovered time', value: 'timestamp asc', directionLabel: 'Newest', sortKey: 'timestamp' },
    { label: 'Discovered time', value: 'timestamp desc', directionLabel: 'Oldest', sortKey: 'timestamp' },
    
];

const filters = [
    {
    key: 'apiCollectionId',
    label: 'Collection',
    title: 'Collection',
    choices: [],
    availableChoices: new Set()
  },
  {
    key: 'isRequest',
    label: 'API call',
    title: 'API call',
    choices: [],
    availableChoices: new Set(),
    singleSelect:true
  },
  {
    key:'location',
    label:'Location',
    title:'Location',
    choices:[
        {label:"Header", value:"header"},
        {label:"Payload", value:"payload"},
        {label:"Query string", value:"queryString"}
    ],
    availableChoices: new Set('Header', 'Payload', 'Query string')
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
    singular: 'Endpoint with sensitive data',
    plural: 'Endpoints with sensitive data',
  };

let promotedBulkActions = (selectedResources) => {
    return [
        {
            content: 'Ignore',
            onAction: () => console.log('Todo: implement function'),
          },
          {
            content: 'Mark as false positive',
            onAction: () => console.log('Todo: implement function'),
          },
          {
            content: 'Create issue',
            onAction: () => console.log('Todo: implement function'),
          },
          {
            content: 'Configure data types',
            onAction: () => console.log('Todo: implement function'),
          },
    ]
}


function SensitiveDataExposure() {
    const [loading, setLoading] = useState(true);
    const allCollections = Store(state => state.allCollections);
    const params = useParams()
    const subType = params.subType;
    const apiCollectionMap = allCollections.reduce(
        (map, e) => {map[e.id] = e.displayName; return map}, {}
    )

    function disambiguateLabel(key, value) {
        switch (key) {
            case "apiCollectionId": 
                return (value).map((val) => `${apiCollectionMap[val]}`).join(', ');
            case "isRequest":
                return value[0] ? "In request" : "In response"
            case "dateRange":
                return value.since.toDateString() + " - " + value.until.toDateString();
            default:
                return value;
        }
      }
    Object.keys(apiCollectionMap).forEach((key) => {
        filters[0].availableChoices.add(key)
    });
    filters[0].choices=[];
    filters[0].availableChoices.forEach((key) => {
        filters[0].choices.push({
            label:apiCollectionMap[key],
            value:Number(key)
        })
    })
    filters[1].choices=[{
        label:"In request",
        value:true
    },{
        label:"In response",
        value:false
    }]
    filters[1].availableChoices.add(true)
    filters[1].availableChoices.add(false)

    async function fetchData(sortKey, sortOrder, skip, limit, filters, filterOperators, queryValue){
        setLoading(true);
        let isRequest = filters['isRequest'][0] || false;
        delete filters['isRequest']
        filters['subType'] = [subType]
        filterOperators['subType']="OR"
        let ret = []
        let total = 0; 
        let dateRange = filters['dateRange'] || false;
        delete filters['dateRange']
        let startTimestamp = 0;
        let endTimestamp = func.timeNow()
        if(dateRange){
            startTimestamp = Math.floor(Date.parse(dateRange.since) / 1000);
            endTimestamp = Math.floor(Date.parse(dateRange.until) / 1000)
        }
        await api.fetchChanges(sortKey, sortOrder, skip, limit, filters, filterOperators, startTimestamp, endTimestamp, true,isRequest).then((res)=> {
            res.endpoints.forEach((endpoint) => {
                let temp = {}
                temp['collection'] = apiCollectionMap[endpoint.apiCollectionId]
                temp['apiCollectionId'] = endpoint.apiCollectionId
                temp['url'] = endpoint.method + " " + endpoint.url
                temp['detected_timestamp'] = "Detected " + func.prettifyEpoch(endpoint.timestamp)
                temp['timestamp'] = endpoint.timestamp
                temp['location'] = "Detected in " + (endpoint.isHeader ? "header" : (endpoint.isUrlParam ? "query param" : "payload"))
                temp['isHeader'] = endpoint.isHeader
                temp["call"] = endpoint.responseCode < 0 ? "Request" : "Response"
                temp["hexId"] = temp['collection'] + temp['url'] + temp['location'] + temp['call'] + endpoint.param + endpoint.subTypeString
                temp['nextUrl'] = "/dashboard/observe/sensitive/"+subType+"/"+temp['apiCollectionId'] + "/" + btoa(endpoint.url + " " + endpoint.method);
                ret.push(temp);
            })
            total = res.total;
            setLoading(false);
        })
        return {value:ret , total:total};
    }

const navigate = useNavigate();
function navigateBack(){
  navigate("/dashboard/observe/sensitive")
}

    return (
        <PageWithMultipleCards
        title={
            <Text as="div" variant="headingLg">
            {`Endpoints with ${subType}` }
          </Text>
        }
        backAction = {{onAction:navigateBack}}
        primaryAction={<Button primary>Create custom data types</Button>}
        components = {[
            <GithubServerTable
                key="table"
                headers={headers}
                resourceName={resourceName} 
                appliedFilters={appliedFilters}
                getActions = {() => {}}
                sortOptions={sortOptions}
                disambiguateLabel={disambiguateLabel}
                selectable = {true}
                loading={loading}
                fetchData={fetchData}
                filters={filters}
                promotedBulkActions={promotedBulkActions}
                hideQueryField={true}
                calenderFilter={true}
            />
        ]}
        />
    )
}

export default SensitiveDataExposure