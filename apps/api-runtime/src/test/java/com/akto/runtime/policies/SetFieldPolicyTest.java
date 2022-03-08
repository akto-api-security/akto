package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.runtime_filters.RuntimeFilter;
import com.akto.dto.type.URLMethods.Method;

import org.junit.jupiter.api.Assertions;
import org.junit.Test;

public class SetFieldPolicyTest {

    @Test
    public void happy() {
        ApiInfo apiInfo = new ApiInfo();
        RuntimeFilter runtimeFilter = new RuntimeFilter();
        runtimeFilter.setCustomFieldName("field");
        SetFieldPolicy.setField(null,apiInfo, runtimeFilter);
        Assertions.assertEquals(apiInfo.getViolations().size(), 1);
        Assertions.assertNotNull(apiInfo.getViolations().get("field"));
    }

    @Test
    public void happyExisting() {
        ApiInfo apiInfo = new ApiInfo(0,"",Method.DELETE);
        apiInfo.getViolations().put("field", 0);
        RuntimeFilter runtimeFilter = new RuntimeFilter();
        runtimeFilter.setCustomFieldName("field");
        SetFieldPolicy.setField(null,apiInfo, runtimeFilter);
        Assertions.assertEquals(apiInfo.getViolations().size(), 1);
        Assertions.assertNotNull(apiInfo.getViolations().get("field"));
    }
}
