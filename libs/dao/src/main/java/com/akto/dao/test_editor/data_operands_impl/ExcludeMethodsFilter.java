package com.akto.dao.test_editor.data_operands_impl;

import java.util.List;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.ApiSelectionFilters;
import com.akto.dto.test_editor.DataOperandFilterRequest;
import com.akto.dto.test_editor.DataOperandsFilterResponse;
import com.akto.dto.test_editor.TestConfig;

public class ExcludeMethodsFilter extends DataOperandsImpl{
    
    @Override
    public Boolean isValid(DataOperandFilterRequest dataOperandFilterRequest) {

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
