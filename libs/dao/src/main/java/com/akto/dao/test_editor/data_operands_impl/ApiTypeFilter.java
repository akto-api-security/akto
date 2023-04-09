package com.akto.dao.test_editor.data_operands_impl;

import com.akto.dao.test_editor.Utils;
import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ApiSelectionFilters;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.TestConfig;
import com.akto.util.HttpRequestResponseUtils;

public class ApiTypeFilter extends DataOperandsImpl {
    
    @Override
    public DataOperandsFilterResponse isValid(DataOperandFilterRequest dataOperandFilterRequest) {

        // OriginalHttpRequest originalHttpRequest = rawApi.getRequest().copy();
        // ApiSelectionFilters filters = testConfig.getApiSelectionFilters();
        // String url = originalHttpRequest.getUrl();
        // if (filters == null) {
        //     return true;
        // }
        // String apiTypeFilter = filters.getApiType();

        // if (apiTypeFilter == null) {
        //     return true;
        // }
        // if (apiTypeFilter.toLowerCase().equals("graphql")) {
        //     return Utils.checkIfContainsMatch(url, apiTypeFilter);
        // } else if (apiTypeFilter.toLowerCase().equals("grpc")) {
        //     String contentType = HttpRequestResponseUtils.getAcceptableContentType(originalHttpRequest.getHeaders());
        //     if (contentType != null && contentType.equals("application/grpc")) {
        //         return true;
        //     }
        // } else {
        //     return true;
        // }
        return false;
    }        

}
