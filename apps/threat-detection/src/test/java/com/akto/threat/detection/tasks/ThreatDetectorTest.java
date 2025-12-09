package com.akto.threat.detection.tasks;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpRequestParams;
import com.akto.threat.detection.utils.ThreatDetector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;


public class ThreatDetectorTest {

    private ThreatDetector threatDetector;

    @Before
    public void setUp() throws Exception {
        threatDetector = new ThreatDetector();
    }

    @Test
    public void testIsSqliThreat_url() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/login?user=admin' OR '1'='1", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSqliThreat(params));
    }

    @Test
    public void testIsSqliThreat_headers() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Forwarded-For", Arrays.asList("127.0.0.1' OR '1'='1"));
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSqliThreat(params));
    }

    @Test
    public void testIsSqliThreat_payload() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "username=admin&password=' OR '1'='1";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSqliThreat(params));
    }

    @Test
    public void testIsSqliThreat_none() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Forwarded-For", Arrays.asList("127.0.0.1"));
        String payload = "username=admin&password=1234";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertFalse(threatDetector.isSqliThreat(params));
    }

    @Test
    public void testIsLfiThreat_url() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/index.php?page=../../etc/passwd", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isLFiThreat(params));
    }

    @Test
    public void testIsLfiThreat_headers() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Referer", Arrays.asList("../../etc/passwd"));
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isLFiThreat(params));
    }

    @Test
    public void testIsLfiThreat_payload() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "file=../../etc/passwd";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isLFiThreat(params));
    }

    @Test
    public void testIsLfiThreat_none() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Referer", Arrays.asList("/home"));
        String payload = "file=about.html";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertFalse(threatDetector.isLFiThreat(params));
    }

    @Test
    public void testIsOsCommandInjectionThreat_url() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/run?cmd=cat /etc/passwd", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isOsCommandInjectionThreat(params));
    }

    @Test
    public void testIsOsCommandInjectionThreat_headers() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Command", Arrays.asList("cat /etc/passwd"));
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isOsCommandInjectionThreat(params));
    }

    @Test
    public void testIsOsCommandInjectionThreat_payload() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "cmd=cat /etc/passwd";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isOsCommandInjectionThreat(params));
    }

    @Test
    public void testIsOsCommandInjectionThreat_none() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Command", Arrays.asList("ls"));
        String payload = "cmd=ls";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertFalse(threatDetector.isOsCommandInjectionThreat(params));
    }

    @Test
    public void testIsSSRFThreat_url() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/fetch?url=http://169.254.169.254/latest/meta-data/", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSSRFThreat(params));
    }

    @Test
    public void testIsSSRFThreat_headers() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-URL", Arrays.asList("http://169.254.169.254/latest/meta-data/"));
        String payload = "";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSSRFThreat(params));
    }

    @Test
    public void testIsSSRFThreat_payload() {
        Map<String, List<String>> headers = new HashMap<>();
        String payload = "url=http://169.254.169.254/latest/meta-data/";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertTrue(threatDetector.isSSRFThreat(params));
    }

    @Test
    public void testIsSSRFThreat_none() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-URL", Arrays.asList("http://example.com"));
        String payload = "url=http://example.com";
        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("POST", "/home", "HTTP/1.1", headers, payload, 0);
        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        assertFalse(threatDetector.isSSRFThreat(params));
    }

    private static final int AUTH_MISMATCH_ACCOUNT_ID = 1758858035;
    private static final String USER_ID_1 = "43017713-831f-458c-ae42-918215550c16";
    private static final String USER_ID_2 = "8b2e4f9a-1c3d-4e5f-a6b7-c8d9e0f1a2b3";
    // JWT with sub=USER_ID_1
    private static final String JWT_WITH_SUB_USER_1 = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJzdWIiOiAiNDMwMTc3MTMtODMxZi00NThjLWFlNDItOTE4MjE1NTUwYzE2IiwgImlhdCI6IDE3MDAwMDAwMDB9.fdxj-TMrYg2da5PicziWV8SxjWINi_pOXo8Fz_1Drg8";
    // JWT with sub=USER_ID_2
    private static final String JWT_WITH_SUB_USER_2 = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJzdWIiOiAiOGIyZTRmOWEtMWMzZC00ZTVmLWE2YjctYzhkOWUwZjFhMmIzIiwgImlhdCI6IDE3MDAwMDAwMDB9.LFW458NUkTGH3N0f0TyIytzuirF-bfcCuNQ9h-YVUM4";
    // JWT without sub claim
    private static final String JWT_WITHOUT_SUB = "eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJpYXQiOiAxNzAwMDAwMDAwLCAibmFtZSI6ICJ0ZXN0In0.mgK0xD5-Ir10g-YVHdFFVfMbE0hIuMWNu7plZxnybEg";

    @After
    public void tearDown() {
        Context.accountId.remove();
    }

    @Test
    public void testUserAuthMismatchThreat_mismatch() {
        Context.accountId.set(AUTH_MISMATCH_ACCOUNT_ID);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Arrays.asList("Bearer " + JWT_WITH_SUB_USER_2));

        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("GET", "/api/users/" + USER_ID_1 + "/profile", "HTTP/1.1", headers, "", 0);

        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        params.setStatusCode(200);

        // sub=USER_ID_2 but URL has USER_ID_1 → threat
        assertTrue(threatDetector.isUserAuthMismatchThreat(params, null));
    }

    @Test
    public void testUserAuthMismatchThreat_match() {
        Context.accountId.set(AUTH_MISMATCH_ACCOUNT_ID);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Arrays.asList("Bearer " + JWT_WITH_SUB_USER_1));

        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("GET", "/api/users/" + USER_ID_1 + "/profile", "HTTP/1.1", headers, "", 0);

        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        params.setStatusCode(200);

        // sub=USER_ID_1 matches URL USER_ID_1 → no threat
        assertFalse(threatDetector.isUserAuthMismatchThreat(params, null));
    }

    @Test
    public void testUserAuthMismatchThreat_non200Status() {
        Context.accountId.set(AUTH_MISMATCH_ACCOUNT_ID);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Arrays.asList("Bearer " + JWT_WITH_SUB_USER_2));

        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("GET", "/api/users/" + USER_ID_1 + "/profile", "HTTP/1.1", headers, "", 0);

        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        params.setStatusCode(403);

        // Non-200 status → no threat
        assertFalse(threatDetector.isUserAuthMismatchThreat(params, null));
    }

    @Test
    public void testUserAuthMismatchThreat_noUserIdInUrl() {
        Context.accountId.set(AUTH_MISMATCH_ACCOUNT_ID);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Arrays.asList("Bearer " + JWT_WITH_SUB_USER_1));

        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("GET", "/api/products/123", "HTTP/1.1", headers, "", 0);

        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        params.setStatusCode(200);

        // URL doesn't match /users/{id} pattern → no threat
        assertFalse(threatDetector.isUserAuthMismatchThreat(params, null));
    }

    @Test
    public void testUserAuthMismatchThreat_noJwtInHeaders() {
        Context.accountId.set(AUTH_MISMATCH_ACCOUNT_ID);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Arrays.asList("Basic dXNlcjpwYXNz"));

        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("GET", "/api/users/" + USER_ID_1 + "/profile", "HTTP/1.1", headers, "", 0);

        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        params.setStatusCode(200);

        // No JWT in headers → no threat
        assertFalse(threatDetector.isUserAuthMismatchThreat(params, null));
    }

    @Test
    public void testUserAuthMismatchThreat_noSubInJwt() {
        Context.accountId.set(AUTH_MISMATCH_ACCOUNT_ID);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Arrays.asList("Bearer " + JWT_WITHOUT_SUB));

        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("GET", "/api/users/" + USER_ID_1 + "/profile", "HTTP/1.1", headers, "", 0);

        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        params.setStatusCode(200);

        // JWT without sub claim → no threat
        assertFalse(threatDetector.isUserAuthMismatchThreat(params, null));
    }
}