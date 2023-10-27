const endpointHeadings = [
    {
        text: "Endpoint",
        value: "endpointComp",
        title: "Api endpoints",
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
        isText: true,
    },
    {
        text: 'Access Type',
        value: 'access_type',
        title: 'Access type',
        showFilter: true,
        isText: true,
    },
    {
        text: 'Auth Type',
        title: 'Auth type',
        value: 'auth_type',
        showFilter: true,
        isText: true,
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
        isText: true,
    },
    {
        text: "Method",
        filterKey: "method",
        showFilter: true
    }
]


const apiChangesData = {

}

export default apiChangesData;