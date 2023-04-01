package com.akto.dao.test_editor.api_filters;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ApiSelectionFilters;
import com.akto.dto.test_editor.TestConfig;

public class ResponseStatusFilter extends ApiFilter {
 
    @Override
    public boolean isValid(TestConfig testConfig, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {

        OriginalHttpResponse originalHttpResponse = rawApi.getResponse().copy();
        ApiSelectionFilters filters = testConfig.getApiSelectionFilters();
        if (filters == null) {
            return true;
        }
        String respStatusFilter = filters.getRespStatus();

        if (respStatusFilter == null) {
            return true;
        }
    
        int statusCode = originalHttpResponse.getStatusCode();
        if (respStatusFilter.equals("2xx") && (statusCode >= 200 || statusCode <= 206)) {
            return true;
        }
        String statusCodeString = Integer.toString(statusCode);
        if (respStatusFilter.equals(statusCodeString)) {
            return true;
        }
        return false;
    }

}
