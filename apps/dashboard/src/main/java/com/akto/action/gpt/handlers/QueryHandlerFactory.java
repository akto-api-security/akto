package com.akto.action.gpt.handlers;

import com.akto.action.gpt.data_extractors.ListApisEndpointNames;
import com.akto.action.gpt.data_extractors.filters.FilterJunkEndpoints;
import com.akto.action.gpt.result_fetchers.AsyncResultFetcherStrategy;
import com.akto.action.gpt.result_fetchers.SimpleResultFetcherStrategy;
import com.akto.action.gpt.validators.ApiCollectionAllowedValidation;
import com.akto.action.gpt.validators.ValidateQuery;

import java.util.Collections;
import java.util.List;

public class QueryHandlerFactory {
    private static List<ValidateQuery> validators = Collections.singletonList(new ApiCollectionAllowedValidation());
    public static QueryHandler getQueryHandler(GptQuery query){
        if (query.equals(GptQuery.LIST_APIS_BY_TYPE)) {
            return new ListApisByType(new ListApisEndpointNames(Collections.singletonList(new FilterJunkEndpoints())), new SimpleResultFetcherStrategy(), validators);
        }
        if (query.equals(GptQuery.GROUP_APIS_BY_FUNCTIONALITY)) {
            return new GroupApisByFunctionality(new ListApisEndpointNames(Collections.singletonList(new FilterJunkEndpoints())), new SimpleResultFetcherStrategy(), validators);
        }
        if(query.equals(GptQuery.LIST_SENSITIVE_PARAMS)){
            return new ListSensitiveParameters(new AsyncResultFetcherStrategy());
        }
        if(query.equals(GptQuery.GENERATE_CURL_FOR_TEST)){
            return new ListSensitiveParameters(new SimpleResultFetcherStrategy(), validators);
        }
        throw new IllegalArgumentException("No such query handler");
    }
}
