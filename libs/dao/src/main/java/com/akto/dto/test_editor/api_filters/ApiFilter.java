package com.akto.dto.test_editor.api_filters;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.TestConfig;

public abstract class ApiFilter {
    
    public abstract boolean isValid(TestConfig testConfig, RawApi rawApi, ApiInfo.ApiInfoKey apiInfoKey);
    
}
