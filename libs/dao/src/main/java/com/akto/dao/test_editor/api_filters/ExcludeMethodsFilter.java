package com.akto.dao.test_editor.api_filters;

import java.util.List;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ApiSelectionFilters;
import com.akto.dto.test_editor.TestConfig;

public class ExcludeMethodsFilter extends ApiFilter{
    
    @Override
    public boolean isValid(TestConfig testConfig, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey) {

        ApiSelectionFilters filters = testConfig.getApiSelectionFilters();
        if (filters == null) {
            return true;
        }
        List<String> excludeMethods = filters.getExcludeMethods();
        
        if (excludeMethods == null) {
            return true;
        }

        for (String method: excludeMethods) {
            if (method.equals(apiInfoKey.getMethod().toString())) {
                return false;
            }
        }
        return true;
    }

}
