import React from 'react'
import HeadingWithTooltip from "../../../components/shared/HeadingWithTooltip"
import { CellType } from '../../../components/tables/rows/GithubRow'

const headings = [
    {
        text: "Endpoint",
        value: "endpointComp",
        title: "Api endpoints",
        textValue: "endpoint",
        sortActive: true
    },
    {
        text: "Risk score",
        title: <HeadingWithTooltip
            title={"Risk score"}
            content={"Risk score is calculated based on the amount of sensitive information the API shares and its current status regarding security issues."}
        />,
        value: "riskScoreComp",
        textValue: "riskScore",
        sortActive: true,
        
    },
    {
        text: "Hostname",
        value: 'hostName',
        title: "Hostname",
        maxWidth: '100px',
        type: CellType.TEXT,
    },
    {
        text: 'Access Type',
        value: 'access_type', 
        title:"Access Type",
        showFilter: true,
        type: CellType.TEXT,
        tooltipContent: "Access type of the API. It can be public, private, partner"
    },
    {
        text: 'Auth Type',
        title: 'Auth type',
        value: 'auth_type',
        showFilter: true,
        textValue: 'authTypeTag',
        tooltipContent: "Authentication type of the API."
    },
    {
        text: 'Sensitive params in response',
        title: 'Sensitive params',
        value: 'sensitiveTagsComp',
        filterKey: 'sensitiveInResp',
        showFilter: true,
        textValue: "sensitiveDataTags"
    },
    {
        text: 'Last Seen',
        title: <HeadingWithTooltip 
                title={"Last Seen"}
                content={"Time when API was last detected in traffic."}
            />,
        value: 'last_seen',
        isText: true,
        type: CellType.TEXT,
        sortActive: true,
        
    },
    {
        text: "Source location",
        value: "sourceLocationComp",
        textValue: "sourceLocation",
        title: "Source location",
        tooltipContent: "Exact location of the URL in case detected from the source code."    
    },
    {
        text: "Collection",
        value: "apiCollectionName",
        title: "Collection",
        showFilter: true,
        filterKey: "apiCollectionName",
    }
]

let headers = JSON.parse(JSON.stringify(headings))
headers.push({
    text: 'Method',
    filterKey: 'method',
    showFilter: true,
    textValue: 'method',
    sortActive: true
})

headers.push({
    text: 'Sensitive params in request',
    value: 'sensitiveInReq',
    filterKey: 'sensitiveInReq',
    showFilter: true,
})

headers.push({
    text: 'Response codes',
    value: 'responseCodes',
    filterKey: 'responseCodes',
    showFilter: true,
})


const sortOptions = [
    { label: 'Risk Score', value: 'riskScore asc', directionLabel: 'Highest', sortKey: 'riskScore', columnIndex: 2},
    { label: 'Risk Score', value: 'riskScore desc', directionLabel: 'Lowest', sortKey: 'riskScore', columnIndex: 2},
    { label: 'Method', value: 'method asc', directionLabel: 'A-Z', sortKey: 'method', columnIndex: 8 },
    { label: 'Method', value: 'method desc', directionLabel: 'Z-A', sortKey: 'method', columnIndex: 8 },
    { label: 'Endpoint', value: 'endpoint asc', directionLabel: 'A-Z', sortKey: 'endpoint', columnIndex: 1 },
    { label: 'Endpoint', value: 'endpoint desc', directionLabel: 'Z-A', sortKey: 'endpoint', columnIndex: 1 },
    { label: 'Last seen', value: 'lastSeenTs asc', directionLabel: 'Newest', sortKey: 'lastSeenTs', columnIndex: 7 },
    { label: 'Last seen', value: 'lastSeenTs desc', directionLabel: 'Oldest', sortKey: 'lastSeenTs', columnIndex: 7 }
]

const ActiveTestingCollection = () => {
  return (
    <div>ActiveTestingCollection</div>
  )
}

export default ActiveTestingCollection