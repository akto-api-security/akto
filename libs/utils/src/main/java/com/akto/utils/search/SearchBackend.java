package com.akto.utils.search;

public enum SearchBackend {

    ELASTICSEARCH, AZURE_ADX;

    public static SearchBackend getSearchBackend() {
        String backend = System.getenv("SEARCH_BACKEND");
        if ("AZURE_ADX".equalsIgnoreCase(backend)) {
            return AZURE_ADX;
        }
        return ELASTICSEARCH;
    }
}
