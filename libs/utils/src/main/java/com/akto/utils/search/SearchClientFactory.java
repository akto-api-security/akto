package com.akto.utils.search;

import com.akto.utils.elasticsearch.ElasticSearchClient;

public class SearchClientFactory {

    public static SearchClient instance() {
        SearchBackend backend = SearchBackend.getSearchBackend();
        if (backend == SearchBackend.AZURE_ADX) {
            return AzureDataExplorerClient.instance();
        }
        return ElasticSearchClient.instance();
    }
}
