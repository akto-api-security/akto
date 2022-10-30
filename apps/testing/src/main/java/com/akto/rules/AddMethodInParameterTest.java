package com.akto.rules;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.type.URLMethods;

public class AddMethodInParameterTest extends ChangeMethodPlugin {
    @Override
    public void modifyRequest(OriginalHttpRequest originalHttpRequest, URLMethods.Method method) {
        String queryParams = originalHttpRequest.getQueryParams();
        if (queryParams == null) {
            queryParams = "method="+method.name();
        } else {
            queryParams = "&method="+method.name();
        }

        originalHttpRequest.setQueryParams(queryParams);
    }

    @Override
    public boolean isVulnerable(double percentageBodyMatch, int statusCode) {
        return isStatusGood(statusCode) && percentageBodyMatch < 30;
    }

    @Override
    public String subTestName() {
        return "ADD_METHOD_IN_PARAMETER";
    }
}
