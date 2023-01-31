package com.akto.rules;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.type.URLMethods;

import java.util.Collections;
import java.util.List;

public class AddMethodOverrideHeadersTest extends ChangeMethodPlugin {

    @Override
    public void modifyRequest(OriginalHttpRequest originalHttpRequest, URLMethods.Method method) {
        List<String> value = Collections.singletonList(method.name());
        originalHttpRequest.getHeaders().put("x-http-method", value);
        originalHttpRequest.getHeaders().put("x-http-method-override", value);
        originalHttpRequest.getHeaders().put("x-method-override", value);
    }

    @Override
    public boolean isVulnerable(double percentageBodyMatch, int statusCode) {
        return isStatusGood(statusCode) && percentageBodyMatch < 30;
    }

    @Override
    public String subTestName() {
        return "ADD_METHOD_OVERRIDE_HEADERS";
    }

}
