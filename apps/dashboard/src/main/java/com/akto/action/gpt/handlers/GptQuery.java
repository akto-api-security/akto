package com.akto.action.gpt.handlers;

public enum GptQuery {
    LIST_APIS_BY_TYPE("list_apis_by_type"),
    GROUP_APIS_BY_FUNCTIONALITY("group_apis_by_functionality"),
    LIST_SENSITIVE_PARAMS("list_sensitive_params"),
    GENERATE_CURL_FOR_TEST("generate_curl_for_test"),
    GENERATE_REGEX("generate_regex"),
    SUGGEST_TESTS("suggest_tests"),
    FIND_AUTH_RELATED_TOKENS("find_auth_related_tokens"),
    ANALYZE_REQUEST_RESPONSE_HEADERS("analyze_request_response_headers"),
    ANALYZE_VULNERABILITY("analyze_vulnerability");

    private final String name;

    GptQuery(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static GptQuery getQuery(String name){
        for (GptQuery query : GptQuery.values()) {
            if (query.getName().equalsIgnoreCase(name)) {
                return query;
            }
        }
        throw new IllegalArgumentException("No such query supported");
    }
}
