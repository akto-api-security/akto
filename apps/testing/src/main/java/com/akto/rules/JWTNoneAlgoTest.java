package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.util.JSONUtils;
import com.akto.util.modifier.NoneAlgoJWTModifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JWTNoneAlgoTest extends ModifyAuthTokenTestPlugin {

    public Map<String, List<String>> modifyHeaders(Map<String, List<String>> headers) {
        return JSONUtils.modifyHeaderValues(headers, new NoneAlgoJWTModifier());
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
