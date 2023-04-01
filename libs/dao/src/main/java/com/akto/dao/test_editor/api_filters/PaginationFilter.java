package com.akto.dao.test_editor.api_filters;

import java.util.List;

import com.akto.dao.test_editor.Utils;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ApiSelectionFilters;
import com.akto.dto.test_editor.TestConfig;
import com.akto.util.HttpRequestResponseUtils;

public class PaginationFilter extends ApiFilter{
    
    @Override
    public boolean isValid(TestConfig testConfig, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {

        ApiSelectionFilters filters = testConfig.getApiSelectionFilters();
        if (filters == null) {
            return true;
        }
        List<String> paginationFilter = filters.getPaginationKeyWords();
        OriginalHttpRequest originalHttpRequest = rawApi.getRequest().copy();
        Boolean found;

        String queryJson = HttpRequestResponseUtils.convertFormUrlEncodedToJson(originalHttpRequest.getQueryParams());
        if (queryJson == null) {
            return false;
        }

        if (paginationFilter == null) {
            return true;
        }

        for (String paginationKeyword: paginationFilter) {
            found = Utils.checkIfContainsMatch(queryJson, paginationKeyword);
            if (found) {
                return true;
            }
        }

        return false;
    }
}
