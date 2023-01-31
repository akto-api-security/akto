package com.akto.rules;

import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.type.URLMethods;

public class ChangeHttpMethodTest extends ChangeMethodPlugin {

    @Override
    public void modifyRequest(OriginalHttpRequest originalHttpRequest, URLMethods.Method method) {
        originalHttpRequest.setMethod(method.name());
    }

    @Override
    public boolean isVulnerable(double percentageBodyMatch, int statusCode) {
        return isStatusGood(statusCode);
    }

    @Override
    public String subTestName() {
        return "CHANGE_METHOD";
    }

}
