package com.akto.dto.testing;

import com.akto.dao.common.AuthPolicy;
import com.akto.dto.*;
import com.akto.dto.testing.sources.AuthWithCond;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class TestRolesTest {

    private TestRoles testRoles;
    private ObjectId objectId;
    private AuthWithCond defaultAuthWithCond;

    @Before
    public void setUp() {
        objectId = new ObjectId();
        testRoles = new TestRoles();
        testRoles.setId(objectId);
        testRoles.setName("ExampleRole");
        testRoles.setCreatedBy("admin");
        testRoles.setDefaultPresetAuth(false);

        defaultAuthWithCond = new AuthWithCond();
        defaultAuthWithCond.setHeaderKVPairs(new HashMap<>());

        AuthMechanism defaultMechanism = new AuthMechanism();
        defaultMechanism.setAuthParams(Collections.singletonList(
                new HardcodedAuthParam(AuthParam.Location.HEADER, "Authorization", "Bearer token", true)
        ));
        defaultAuthWithCond.setAuthMechanism(defaultMechanism);

        testRoles.setAuthWithCondList(Collections.singletonList(defaultAuthWithCond));
    }

    @After
    public void tearDown() {
        // Reset static fields in AuthPolicy after each test
        AuthPolicy.headersMap = new HashMap<>();
        AuthPolicy.authHeaders = new ArrayList<>();
    }

    @Test
    public void testFindDefaultAuthMechanism_returnsValidAuth() {
        AuthMechanism result = testRoles.findDefaultAuthMechanism();
        assertNotNull(result);
        assertEquals(1, result.getAuthParams().size());
        assertEquals("Authorization", result.getAuthParams().get(0).getKey());
    }

    @Test
    public void testFindMatchingAuthMechanism_withHeaderMatch() {
        AuthWithCond auth = new AuthWithCond();
        Map<String, String> condHeaders = new HashMap<>();
        condHeaders.put("x-api-key", "secret");
        auth.setHeaderKVPairs(condHeaders);

        AuthMechanism mechanism = new AuthMechanism();
        mechanism.setAuthParams(Collections.singletonList(
                new HardcodedAuthParam(AuthParam.Location.HEADER, "x-api-key", "secret", true)
        ));
        auth.setAuthMechanism(mechanism);

        testRoles.setAuthWithCondList(Arrays.asList(auth, defaultAuthWithCond));
        String method = "POST";
        String url = "https://example.com/api";
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", Arrays.asList("application/json"));
        headers.put("Authorization", Arrays.asList("Bearer token123"));
        String body = "{\"foo\":\"bar\"}";

        OriginalHttpRequest req = new OriginalHttpRequest();
        req.setMethod(method);
        req.setUrl(url);
        req.setHeaders(headers);
        req.setBody(body);

        RawApi rawApi = new RawApi();
        rawApi.setRequest(req);
        Map<String, List<String>> rawHeaders = new HashMap<>();
        rawHeaders.put("x-api-key", Collections.singletonList("secret"));
        req.setHeaders(rawHeaders);
        rawApi.setRequest(req);

        AuthMechanism result = testRoles.findMatchingAuthMechanism(rawApi);
        assertNotNull(result);
        assertEquals("x-api-key", result.getAuthParams().get(0).getKey());
    }

    @Test
    public void testFindMatchingAuthMechanism_noMatch_fallsBackToDefault() {
        AuthWithCond auth = new AuthWithCond();
        Map<String, String> condHeaders = new HashMap<>();
        condHeaders.put("x-api-key", "secret");
        auth.setHeaderKVPairs(condHeaders);

        AuthMechanism mechanism = new AuthMechanism();
        mechanism.setAuthParams(Collections.singletonList(
                new HardcodedAuthParam(AuthParam.Location.HEADER, "x-api-key", "secret", true)
        ));
        auth.setAuthMechanism(mechanism);

        testRoles.setAuthWithCondList(Arrays.asList(auth, defaultAuthWithCond));

        String method = "POST";
        String url = "https://example.com/api";
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", Arrays.asList("application/json"));
        headers.put("Authorization", Arrays.asList("Bearer token123"));
        String body = "{\"foo\":\"bar\"}";

        OriginalHttpRequest req = new OriginalHttpRequest();
        req.setMethod(method);
        req.setUrl(url);
        req.setHeaders(headers);
        req.setBody(body);

        RawApi rawApi = new RawApi();
        rawApi.setRequest(req);
        Map<String, List<String>> rawHeaders = new HashMap<>();
        rawHeaders.put("x-api-key", Collections.singletonList("secret"));
        req.setHeaders(rawHeaders);
        rawApi.setRequest(req);

        rawHeaders.put("x-api-key", Collections.singletonList("wrong")); // mismatch
        req.setHeaders(rawHeaders);
        rawApi.setRequest(req);

        AuthMechanism result = testRoles.findMatchingAuthMechanism(rawApi);
        assertNotNull(result);
        assertEquals("Authorization", result.getAuthParams().get(0).getKey()); // fallback
    }


    @Test
    public void testGettersAndSetters() {
        testRoles.setLastUpdatedBy("dev");
        testRoles.setCreatedTs(101);
        testRoles.setLastUpdatedTs(202);
        testRoles.setApiCollectionIds(Arrays.asList(1, 2));
        testRoles.setScopeRoles(Arrays.asList("admin", "qa"));

        assertEquals("dev", testRoles.getLastUpdatedBy());
        assertEquals(101, testRoles.getCreatedTs());
        assertEquals(202, testRoles.getLastUpdatedTs());
        assertEquals(Arrays.asList(1, 2), testRoles.getApiCollectionIds());
        assertEquals(Arrays.asList("admin", "qa"), testRoles.getScopeRoles());
    }

    @Test
    public void testFindMatchingAuthMechanism_withDefaultPresetAuth_true() {
        // --- Step 1: prepare static headers from AuthPolicy ---
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put("authorization", "Bearer example");
        AuthPolicy.headersMap = headersMap;

        List<String> authHeaders = new ArrayList<>();
        authHeaders.add("authorization");
        AuthPolicy.authHeaders = authHeaders;

        // --- Step 2: build rawApi with header ---
        RawApi rawApi = new RawApi();

        // request
        String method = "POST";
        String url = "https://example.com/api";
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Content-Type", Arrays.asList("application/json"));
        headers.put("Authorization", Arrays.asList("Bearer token123"));
        String body = "{\"foo\":\"bar\"}";

        OriginalHttpRequest req = new OriginalHttpRequest();
        req.setMethod(method);
        req.setUrl(url);
        req.setHeaders(headers);
        req.setBody(body);

        rawApi.setRequest(req);
        Map<String, List<String>> rawHeaders = new HashMap<>();
        rawHeaders.put("x-api-key", Collections.singletonList("secret"));
        req.setHeaders(rawHeaders);
        rawApi.setRequest(req);

        // --- Step 3: call method under test ---
        AuthMechanism result = testRoles.findMatchingAuthMechanism(rawApi);

        // --- Step 4: verify ---
        assertNotNull(result);
        assertEquals(1, result.getAuthParams().size());

        AuthParam param = result.getAuthParams().get(0);
        assertEquals("Authorization", param.getKey());
        assertTrue(param instanceof HardcodedAuthParam);

        // verify flag reset
        assertFalse("defaultPresetAuth should be reset to false", testRoles.isDefaultPresetAuth());
    }

}