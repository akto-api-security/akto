package com.akto.dao.test_editor.api_filters;

import java.util.List;

import com.akto.dao.test_editor.Utils;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ApiSelectionFilters;
import com.akto.dto.test_editor.TestConfig;

public class UrlContainsFilter extends ApiFilter {
    
    @Override
    public boolean isValid(TestConfig testConfig, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {

        OriginalHttpRequest originalHttpRequest = rawApi.getRequest().copy();
        String url = originalHttpRequest.getUrl();
        ApiSelectionFilters filters = testConfig.getApiSelectionFilters();
        if (filters == null) {
            return true;
        }
        List<String> urlContainsFilter = filters.getUrlContains();
        Boolean found = false;
        if (urlContainsFilter == null || urlContainsFilter.size() == 0) {
            return true;
        }

        for (String keyword: urlContainsFilter) {
            found = Utils.checkIfContainsMatch(url, keyword);
            if (found) {
                return true;
            }
        }
        return false;
    }
}
