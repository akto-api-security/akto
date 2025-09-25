package com.akto.action.gpt.handlers;

import com.akto.action.gpt.data_extractors.ListApisEndpointNames;
import com.akto.action.gpt.data_extractors.filters.FilterJunkEndpoints;
import com.akto.action.gpt.result_fetchers.AsyncResultFetcherStrategy;
import com.akto.action.gpt.result_fetchers.SelfHostedLlmResultFetcherStrategy;
import com.akto.action.gpt.validators.ApiCollectionAllowedValidation;
import com.akto.action.gpt.validators.ValidateQuery;

import java.util.Collections;
import java.util.List;

public class QueryHandlerFactory {
    private static List<ValidateQuery> validators = Collections.singletonList(new ApiCollectionAllowedValidation());
    public static QueryHandler getQueryHandler(GptQuery query){
        switch (query) {
            case LIST_APIS_BY_TYPE:
                return new ListApisByType(new ListApisEndpointNames(Collections.singletonList(new FilterJunkEndpoints())), new AsyncResultFetcherStrategy(), validators);
            case GROUP_APIS_BY_FUNCTIONALITY:
                return new GroupApisByFunctionality(new ListApisEndpointNames(Collections.singletonList(new FilterJunkEndpoints())), new AsyncResultFetcherStrategy(), validators);
            case LIST_SENSITIVE_PARAMS:
                return new ListSensitiveParameters(new AsyncResultFetcherStrategy(), validators);
            case GENERATE_CURL_FOR_TEST:
                return new GenerateCurlForTest(new AsyncResultFetcherStrategy());
            case GENERATE_REGEX:
                return new GenerateRegex(new AsyncResultFetcherStrategy());
            case SUGGEST_TESTS:
                return new SuggestTests(new AsyncResultFetcherStrategy());
            case FIND_AUTH_RELATED_TOKENS:
                return new FindAuthRelatedTokens(new AsyncResultFetcherStrategy());
            case ANALYZE_REQUEST_RESPONSE_HEADERS:
                return new FindRequestResponseHeaders(new SelfHostedLlmResultFetcherStrategy());
            case ANALYZE_VULNERABILITY:
                return new AnalyzeVulnerability(new SelfHostedLlmResultFetcherStrategy());
            default:
                throw new IllegalArgumentException("Unexpected value: " + query);
        }
    }
}
