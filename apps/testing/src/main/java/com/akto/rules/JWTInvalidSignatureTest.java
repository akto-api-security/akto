package com.akto.rules;

import com.akto.util.JSONUtils;
import com.akto.util.modifier.InvalidSignatureJWTModifier;

import java.util.List;
import java.util.Map;

public class JWTInvalidSignatureTest extends ModifyAuthTokenTestPlugin {

    public Map<String, List<String>> modifyHeaders(Map<String, List<String>> headers) {
        return JSONUtils.modifyHeaderValues(headers, new InvalidSignatureJWTModifier());
    }


    @Override
    public String superTestName() {
        return "NO_AUTH";
    }

    @Override
    public String subTestName() {
        return "JWT_INVALID_SIGNATURE";
    }
}
