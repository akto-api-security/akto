
const successResponseCode = [
    "\tresponse_code:",
    "\t\tgte: 200",
    "\t\tlt: 300"
]

const snippets = [
    {
        label: "auth-block",
        desc: "makes sure that only authenticated api's get considered for a test",
        text: [
            "auth:",
            "\tauthenticated: true"
        ]
    },
    {
        label: "filter-success-response",
        desc: "API filter for success response code",
        text: [
            "api_selection_filter:",
            ...successResponseCode
        ]
    },
    {
        label: "validate-success-response",
        desc: "Validate success response code",
        text: [
            "validate:",
            ...successResponseCode
        ]
    }

]

export default snippets;
