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

    private void validateBodyAuthOperations(OriginalHttpRequest request, String key, String modifiedValue, String removeExpectedValue, Boolean modified, Boolean hardcoded) {
        String value = "Value";
        String finalKey = key.toLowerCase().trim();
        AuthMechanism authMechanism = new AuthMechanism();
        if (hardcoded) {
            authMechanism.setAuthParams(Collections.singletonList(new HardcodedAuthParam(AuthParam.Location.BODY, key, value)));
        } else {
            authMechanism.setAuthParams(Collections.singletonList(new LoginRequestAuthParam(AuthParam.Location.BODY, key, value)));
        }

        boolean result = authMechanism.addAuthToRequest(request);
        assertEquals(result, modified);
        
        String body = request.getBody();
        assertEquals(request.getBody(), modifiedValue);

        result = authMechanism.removeAuthFromRequest(request);
        assertEquals(result, modified);
        if (modifiedValue == null) return;

        body = request.getBody();
        assertEquals(body, removeExpectedValue);
    }

    @Test
    public void testAddAuthToRequestHardcoded() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("header1", Arrays.asList("1","2"));
        headers.put("header2", null);
        headers.put("header3", Collections.emptyList());
        headers.put("header4", Collections.singletonList("1"));

        OriginalHttpRequest request = new OriginalHttpRequest("", "", "GET", "",headers, "HTTP/1.1");

        validate(request, "Header1", Collections.singletonList("Value"));
        validate(request, " Header2  ", Collections.singletonList("Value"));
        validate(request, "InvalidHeader", null);


    }

    @Test
    public void testAddAuthToRequestBodyHardcoded() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("header1", Arrays.asList("1","2"));
        headers.put("header2", null);
        headers.put("header3", Collections.emptyList());
        headers.put("header4", Collections.singletonList("1"));

        String requestPayload = "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}";

        OriginalHttpRequest request = new OriginalHttpRequest("", "", "GET", requestPayload,headers, "HTTP/1.1");

        validateBodyAuthOperations(request, "initials.xy", "{\"initials\":{\"initials\":\"AH\",\"xy\":\"Value\"},\"xy\":\"ab\"}", "{\"initials\":{\"initials\":\"AH\",\"xy\":null},\"xy\":\"ab\"}", true, true);
        request.setBody(requestPayload);
        validateBodyAuthOperations(request, "xy", "{\"initials\":{\"initials\":\"AH\",\"xy\":\"ab\"},\"xy\":\"Value\"}", "{\"initials\":{\"initials\":\"AH\",\"xy\":\"ab\"},\"xy\":null}", true, true);
        request.setBody(requestPayload);
        validateBodyAuthOperations(request, "initials.yz", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", false, true);
        request.setBody(requestPayload);
        validateBodyAuthOperations(request, "yz", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", false, true);
        request.setBody(requestPayload);
        validateBodyAuthOperations(request, "initials.xy.yz", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", false, true);


    }

    @Test
    public void testAddAuthToRequestBodyLoginFlow() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("header1", Arrays.asList("1","2"));
        headers.put("header2", null);
        headers.put("header3", Collections.emptyList());
        headers.put("header4", Collections.singletonList("1"));

        String requestPayload = "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}";

        OriginalHttpRequest request = new OriginalHttpRequest("", "", "GET", requestPayload,headers, "HTTP/1.1");

        validateBodyAuthOperations(request, "initials.xy", "{\"initials\":{\"initials\":\"AH\",\"xy\":\"Value\"},\"xy\":\"ab\"}", "{\"initials\":{\"initials\":\"AH\",\"xy\":null},\"xy\":\"ab\"}", true, false);
        request.setBody(requestPayload);
        validateBodyAuthOperations(request, "xy", "{\"initials\":{\"initials\":\"AH\",\"xy\":\"ab\"},\"xy\":\"Value\"}", "{\"initials\":{\"initials\":\"AH\",\"xy\":\"ab\"},\"xy\":null}", true, false);
        request.setBody(requestPayload);
        validateBodyAuthOperations(request, "initials.yz", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", false, false);
        request.setBody(requestPayload);
        validateBodyAuthOperations(request, "yz", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", false, false);
        request.setBody(requestPayload);
        validateBodyAuthOperations(request, "initials.xy.yz", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", "{\"initials\": {\"initials\": \"AH\", \"xy\": \"ab\"}, \"xy\": \"ab\"}", false, false);


    }

}
