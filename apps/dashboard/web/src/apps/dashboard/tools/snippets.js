
const successResponseCode = [
    "\tresponse_code:",
    "\t\tgte: 200",
    "\t\tlt: 300"
]

const executeBasic = [
    "execute:",
    "\ttype: single",
    "\trequests:",
    "\t\t- req:"
]

const snippets = [
    {
        label: "authorization-SAMPLE",
        desc: "Makes sure that only authenticated APIs get considered for a test",
        text: [
            "auth:",
            "\tauthenticated: true"
        ]
    },
    {
        label: "filter-success-response-SAMPLE",
        desc: "API filter for success response code",
        text: [
            "api_selection_filters:",
            ...successResponseCode
        ]
    },
    {
        label: "validate-success-response-SAMPLE",
        desc: "Validate success response code",
        text: [
            "validate:",
            ...successResponseCode
        ]
    },
    {
        label: "execute-add-header-SAMPLE",
        desc: "Add header to request while executing the test",
        text: [
            ...executeBasic,
            "\t\t\t- add_header:",
            "\t\t\t\t<HEADER_KEY>: <HEADER_VALUE>"
        ]
    },
    {
        label: "execute-replace-body-SAMPLE",
        desc: "Replace body of the request while executing the test",
        text: [
            ...executeBasic,
            "\t\t\t- replace_body: <REPLACED_BODY>"
        ]
    },
    {
        label: "execute-modify-body-param-SAMPLE",
        desc: "Modify body param of the request while executing the test",
        text: [
            ...executeBasic,
            "\t\t\t- modify_body_param:",
            "\t\t\t\t<PAYLOAD_KEY>: <NEW_PAYLOAD_VALUE>"
        ]
    },
]

export default snippets;
