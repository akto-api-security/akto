package com.akto.rules;

import com.akto.util.JSONUtils;
import com.akto.util.modifier.NoneAlgoJWTModifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class JWTNoneAlgoTest extends ModifyAuthTokenTestPlugin {

    public List<Map<String, List<String>>> modifyHeaders(Map<String, List<String>> headers) {
        List<Map<String, List<String>>> result = new ArrayList<>();
        for (String noneString: Arrays.asList("None", "NONE", "none", "NoNe")) {
            result.add(JSONUtils.modifyHeaderValues(headers, new NoneAlgoJWTModifier(noneString)));
        }
        return result;
    }


    @Override
    public String superTestName() {
        return "NO_AUTH";
    }

    @Override
    public String subTestName() {
        return "JWT_NONE_ALGO";
    }
}
