
const keywordSnippets = {
    modify_url: [
        "modify_url: <NEW_URL>",
    ],
    modify_method: [
        "modify_method: <NEW_METHOD>",
    ],
    add_body_param: [
        "add_body_param:",
        "\t<PAYLOAD_KEY>: <PAYLOAD_VALUE>"
    ],
    add_header: [
        "add_header:",
        "\t<HEADER_KEY>: <HEADER_VALUE>"
    ],
    add_query_param: [
        "add_query_param:",
        "\t<QUERY_KEY>: <QUERY_VALUE>"
    ],
    modify_body_param: [
        "modify_body_param:",
        "\t<PAYLOAD_KEY>: <NEW_PAYLOAD_VALUE>"
    ],
    modify_header: [
        "modify_header:",
        "\t<HEADER_KEY>: <NEW_HEADER_VALUE>"
    ],
    modify_query_param: [
        "modify_query_param:",
        "\t<QUERY_KEY>: <NEW_QUERY_VALUE>"
    ],
    delete_body_param: [
        "delete_body_param: <PAYLOAD_KEY>"
    ],
    delete_header: [
        "delete_header: <HEADER_KEY>"
    ],
    delete_query_param: [
        "delete_query_param: <QUERY_KEY>"
    ],
    remove_auth_header: [
        "remove_auth_header: true"
    ],
    follow_redirect: [
        "follow_redirect: true"
    ],
    replace_auth_header: [
        "replace_auth_header: true"
    ],
    

}

export default keywordSnippets;