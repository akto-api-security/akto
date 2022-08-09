package com.akto.dto.testing;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.OriginalHttpRequest;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class AuthMechanismTests {

    private void validate(OriginalHttpRequest request, String key, List<String> modifiedValue) {
        String value = "Value";
        String finalKey = key.toLowerCase().trim();
        AuthMechanism authMechanism = new AuthMechanism();
        authMechanism.setAuthParams(Collections.singletonList(new HardcodedAuthParam(AuthParam.Location.HEADER, key, value)));

        boolean result = authMechanism.addAuthToRequest(request);
        assertEquals(result, modifiedValue!=null);
        List<String> modifiedHeader = request.getHeaders().get(finalKey);
        assertEquals(modifiedHeader, modifiedValue);

        result = authMechanism.removeAuthFromRequest(request);
        assertEquals(result, modifiedValue!=null);
        if (modifiedValue == null) return;
        modifiedHeader = request.getHeaders().get(finalKey);
        assertEquals(modifiedHeader, Collections.singletonList(null));
    }

    @Test
    public void testAddAuthToRequestHardcoded() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("header1", Arrays.asList("1","2"));
        headers.put("header2", null);
        headers.put("header3", Collections.emptyList());
        headers.put("header4", Collections.singletonList("1"));

        OriginalHttpRequest request = new OriginalHttpRequest("", "", "GET", "",headers);

        validate(request, "Header1", Collections.singletonList("Value"));
        validate(request, " Header2  ", Collections.singletonList("Value"));
        validate(request, "InvalidHeader", null);


    }

}
