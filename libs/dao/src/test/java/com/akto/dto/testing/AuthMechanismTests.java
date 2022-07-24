package com.akto.dto.testing;

import com.akto.dto.HttpRequestParams;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class AuthMechanismTests {

    private void validate(HttpRequestParams httpRequestParams, String key, List<String> modifiedValue) {
        String value = "Value";
        String finalKey = key.toLowerCase().trim();
        AuthMechanism authMechanism = new AuthMechanism();
        authMechanism.setAuthParams(Collections.singletonList(new HardcodedAuthParam(AuthParam.Location.HEADER, key, value)));

        boolean result = authMechanism.addAuthToRequest(httpRequestParams);
        assertEquals(result, modifiedValue!=null);
        List<String> modifiedHeader = httpRequestParams.getHeaders().get(finalKey);
        assertEquals(modifiedHeader, modifiedValue);

        result = authMechanism.removeAuthFromRequest(httpRequestParams);
        assertEquals(result, modifiedValue!=null);
        if (modifiedValue == null) return;
        modifiedHeader = httpRequestParams.getHeaders().get(finalKey);
        assertEquals(modifiedHeader, Collections.singletonList(null));
    }

    @Test
    public void testAddAuthToRequestHardcoded() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("header1", Arrays.asList("1","2"));
        headers.put("header2", null);
        headers.put("header3", Collections.emptyList());
        headers.put("header4", Collections.singletonList("1"));
        HttpRequestParams httpRequestParams = new HttpRequestParams("", "", "",headers ,"", 0);

        validate(httpRequestParams, "Header1", Collections.singletonList("Value"));
        validate(httpRequestParams, " Header2  ", Collections.singletonList("Value"));
        validate(httpRequestParams, "InvalidHeader", null);


    }

}
