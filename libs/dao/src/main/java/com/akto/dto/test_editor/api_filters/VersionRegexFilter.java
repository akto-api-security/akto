package com.akto.dto.test_editor.api_filters;


import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ApiSelectionFilters;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.test_editor.Utils;

public class VersionRegexFilter extends ApiFilter {
 
    @Override
    public boolean isValid(TestConfig testConfig, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {

        ApiSelectionFilters filters = testConfig.getApiSelectionFilters();
        if (filters == null) {
            return true;
        }
        String versionMatchRegexFilter = filters.getVersionMatchRegex();
        OriginalHttpRequest originalHttpRequest = rawApi.getRequest().copy();
        String url = originalHttpRequest.getUrl();

        if (versionMatchRegexFilter == null) {
            return true;
        }
        return Utils.checkIfContainsMatch(url, versionMatchRegexFilter);
    }
}
