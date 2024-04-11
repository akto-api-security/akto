import { CellType } from "../../../../components/tables/rows/GithubRow";

const endpointHeadings = [
    {
        text: "Endpoint",
        value: "endpointComp",
        title: "Api endpoints",
        sortActive: true
    },
    {
        text: "Risk score",
        title: "Risk score",
        value: "riskScoreComp",
    },
    {
        text: "Hostname",
        value: 'hostName',
        title: "Hostname",
        maxWidth: '100px',
        type: CellType.TEXT,
    },
    {
        text: "Collection",
        value: 'apiCollectionName',
        title: "Collection",
        maxWidth: '95px',
        type: CellType.TEXT,
    },
    {
        text: 'Access Type',
        value: 'access_type',
        title: 'Access type',
        showFilter: true,
        type: CellType.TEXT,
        sortActive: true
    },
    {
        text: 'Auth Type',
        title: 'Auth type',
        value: 'auth_type',
        showFilter: true,
        type: CellType.TEXT,
        sortActive: true
    },
    {
        text: 'Sensitive Params',
        title: 'Sensitive params',
        value: 'sensitiveTagsComp',
        filterKey: 'sensitiveTags',
    },
    {
        text: 'Last Seen',
        title: 'Last seen',
        value: 'last_seen',
        type: CellType.TEXT,
        sortActive: true
    },
]

const newParametersHeaders = [
    {
        text: 'Name',
        value: 'name',
        title: 'Parameter Name',
    },
    {
        text: 'Type',
        value: 'subType',
        title: 'Parameter Type',
        type: CellType.TEXT,
        maxWidth: '100px',
    },
    {
        text: "Discovered",
        title: 'Discovered',
        value: 'added',
        sortKey: 'timestamp',
        showFilterMenu: true,
        type: CellType.TEXT,
        maxWidth: '120px',
        sortActive: true
    },
    {
        text: "Endpoint",
        value: "endpointComp",
        title: "Api endpoints",
        sortKey: 'url',
        filterKey: 'url',
        showFilterMenu: true
    },
    {
        text: 'Collection',
        title: 'Collection',
        value: 'apiCollectionName',
        maxWidth: '100px',
        sortKey: 'apiCollectionId',
        showFilterMenu: true,
        type: CellType.TEXT,
    },
    {
        text: 'Location',
        title: 'Location',
        value: 'location',
        sortKey: 'isHeader',
        showFilterMenu: true,
        type: CellType.TEXT,
        maxWidth: '120px'
    },
    {
        text: 'Values',
        title: 'Values',
        value: 'domain',
        maxWidth: '150px',
        showFilterMenu: true,
        type: CellType.TEXT,
    }
]

const parameterResourceName = {
    singular: 'API parameter',
    plural: 'API parameters',
};

const endpointResourceName = {
    singular: 'API endpoint',
    plural: 'API endpoints',
};

const methodObj = [{
    text: 'Method',
    value: 'method',
    filterKey: 'method',
    showFilter: true,
    textValue: 'method',
    sortActive: true
}]

const endpointSortOptions = [
    { label: 'Last seen', value: 'lastSeenTs asc', directionLabel: 'Recent first', sortKey: 'lastSeenTs', columnIndex: 8 },
    { label: 'Last seen', value: 'lastSeenTs desc', directionLabel: 'Oldest first', sortKey: 'lastSeenTs', columnIndex: 8 },
    { label: 'Method', value: 'method asc', directionLabel: 'A-Z', sortKey: 'method', columnIndex: 9 },
    { label: 'Method', value: 'method desc', directionLabel: 'Z-A', sortKey: 'method', columnIndex: 9 },
    { label: 'Endpoint', value: 'endpoint asc', directionLabel: 'A-Z', sortKey: 'url', columnIndex: 1 },
    { label: 'Endpoint', value: 'endpoint desc', directionLabel: 'Z-A', sortKey: 'url', columnIndex: 1 },
    { label: 'Auth Type', value: 'auth_type asc', directionLabel: 'A-Z', sortKey: 'auth_type', columnIndex: 6 },
    { label: 'Auth Type', value: 'auth_type desc', directionLabel: 'Z-A', sortKey: 'auth_type', columnIndex: 6 },
    { label: 'Access Type', value: 'access_type asc', directionLabel: 'A-Z', sortKey: 'access_type', columnIndex: 5 },
    { label: 'Access Type', value: 'access_type desc', directionLabel: 'Z-A', sortKey: 'access_type', columnIndex: 5 },
];

const parameterSortOptions = [
    { label: 'Discovered time', value: 'timestamp asc', directionLabel: 'Newest', sortKey: 'timestamp', columnIndex: 3},
    { label: 'Discovered time', value: 'timestamp desc', directionLabel: 'Oldest', sortKey: 'timestamp', columnIndex: 3 },
];

let paramFilters = [
    {
        key: 'apiCollectionId',
        label: 'Collection',
        title: 'Collection',
        choices: [],
    },
    {
        key: 'method',
        label: 'Method',
        title: 'Method',
        choices: [
            { label: "GET",value: "GET"},
            { label: "POST",value: "POST"},
            { label: "PUT",value: "PUT"},
            { label: "PATCH",value: "PATCH"},
            { label: "DELETE",value: "DELETE"},
            { label: "OPTIONS",value: "OPTIONS"},
            { label: "HEAD",value: "HEAD"},
        ]
    },
    {
        key: 'subType',
        label: 'Type',
        title: 'Type',
        choices: []
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
    }
]

const apiChangesData = {
    getData(key){
        if(key === 'param'){
            const obj = {
                headers: [...newParametersHeaders, ...methodObj],
                headings: newParametersHeaders,
                resourceName: parameterResourceName,
                sortOptions: parameterSortOptions,
            }
            return obj;
        }else{
            const obj = {
                headings: endpointHeadings,
                headers: endpointHeadings,
                resourceName: endpointResourceName,
                sortOptions: endpointSortOptions,
            }
            return obj;
        }
    },
    getParamFilters(){
        return paramFilters;
    }
}

export default apiChangesData;