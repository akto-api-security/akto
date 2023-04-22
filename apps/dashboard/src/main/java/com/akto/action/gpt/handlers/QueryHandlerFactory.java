package com.akto.action.gpt.handlers;

import com.akto.action.gpt.data_extractors.ListApisEndpointNames;
import com.akto.action.gpt.data_extractors.filters.FilterJunkEndpoints;
import com.akto.action.gpt.result_fetchers.AsyncResultFetcherStrategy;
import com.akto.action.gpt.result_fetchers.SimpleResultFetcherStrategy;

import java.util.Collections;

public class QueryHandlerFactory {

    public static QueryHandler getQueryHandler(GptQuery query){
        if (query.equals(GptQuery.LIST_APIS_BY_TYPE)) {
            return new ListApisByType(new ListApisEndpointNames(Collections.singletonList(new FilterJunkEndpoints())), new AsyncResultFetcherStrategy());
        }
        if (query.equals(GptQuery.GROUP_APIS_BY_FUNCTIONALITY)) {
            return new GroupApisByFunctionality(new ListApisEndpointNames(Collections.singletonList(new FilterJunkEndpoints())), new AsyncResultFetcherStrategy());
        }
        if(query.equals(GptQuery.LIST_SENSITIVE_PARAMS)){
            return new ListSensitiveParameters(new AsyncResultFetcherStrategy());
        }
        if(query.equals(GptQuery.GENERATE_CURL_FOR_TEST)){
            return new GenerateCurlForTest(new AsyncResultFetcherStrategy());
        }
        throw new IllegalArgumentException("No such query handler");
    }
}
