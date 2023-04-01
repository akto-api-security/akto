package com.akto.dto.test_editor.api_filters;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ApiSelectionFilters;
import com.akto.dto.test_editor.TestConfig;

public class MustContainHeaderFilter extends ApiFilter {

    @Override
    public boolean isValid(TestConfig testConfig, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {

        ApiSelectionFilters filters = testConfig.getApiSelectionFilters();
        if (filters == null) {
            return true;
        }
        List<String> mustContainHeaders = filters.getMustContainHeaders();
        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();
        
        if (mustContainHeaders == null) {
            return true;
        }

        Set<String> headersSet = new HashSet<>();
        for (String headerName: originalHttpResponse.getHeaders().keySet()) {
            headersSet.add(headerName);
        }

        for (String header: mustContainHeaders) {
            if (!headersSet.contains(header)) {
                return false;
            }
        }

        return true;
    }

}
