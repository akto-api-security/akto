package com.akto.rules;

import com.akto.util.JSONUtils;
import com.akto.util.modifier.AddJkuJWTModifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AddJkuToJwtTest extends ModifyAuthTokenTestPlugin {
    @Override
    public List<Map<String, List<String>>> modifyHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> modifiedHeaders = JSONUtils.modifyHeaderValues(headers, new AddJkuJWTModifier());
        return Collections.singletonList(modifiedHeaders);
    }

    @Override
    public String superTestName() {
        return "NO_AUTH";
    }

    @Override
    public String subTestName() {
        return "ADD_JKU_TO_JWT";
    }
}
