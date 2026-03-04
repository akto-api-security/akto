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
    private static final String TARGET_HOST = "api.stage.store.ignite.harman.com";
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
        headers.put("host", Arrays.asList(TARGET_HOST));

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
        headers.put("host", Arrays.asList(TARGET_HOST));

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
        headers.put("host", Arrays.asList(TARGET_HOST));

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
        headers.put("host", Arrays.asList(TARGET_HOST));

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
        headers.put("host", Arrays.asList(TARGET_HOST));

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
        headers.put("host", Arrays.asList(TARGET_HOST));

        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("GET", "/api/users/" + USER_ID_1 + "/profile", "HTTP/1.1", headers, "", 0);

        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        params.setStatusCode(200);

        // JWT without sub claim → no threat
        assertFalse(threatDetector.isUserAuthMismatchThreat(params, null));
    }

    @Test
    public void testUserAuthMismatchThreat_wrongHost() {
        Context.accountId.set(AUTH_MISMATCH_ACCOUNT_ID);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Arrays.asList("Bearer " + JWT_WITH_SUB_USER_2));
        headers.put("host", Arrays.asList("api.other.example.com"));

        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("GET", "/api/users/" + USER_ID_1 + "/profile", "HTTP/1.1", headers, "", 0);

        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        params.setStatusCode(200);

        // Host doesn't match target host → no threat
        assertFalse(threatDetector.isUserAuthMismatchThreat(params, null));
    }

    @Test
    public void testUserAuthMismatchThreat_noHostHeader() {
        Context.accountId.set(AUTH_MISMATCH_ACCOUNT_ID);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Arrays.asList("Bearer " + JWT_WITH_SUB_USER_2));

        HttpRequestParams reqParams = new HttpRequestParams();
        reqParams.resetValues("GET", "/api/users/" + USER_ID_1 + "/profile", "HTTP/1.1", headers, "", 0);

        HttpResponseParams params = new HttpResponseParams();
        params.setRequestParams(reqParams);
        params.setStatusCode(200);

        // No host header → no threat
        assertFalse(threatDetector.isUserAuthMismatchThreat(params, null));
    }

    // Tests for isInternalIp function
    @Test
    public void testIsInternalIp_class_a_private() {
        // RFC 1918: 10.0.0.0 - 10.255.255.255
        assertTrue(threatDetector.isInternalIp("10.0.0.0"));
        assertTrue(threatDetector.isInternalIp("10.0.0.1"));
        assertTrue(threatDetector.isInternalIp("10.255.255.255"));
        assertTrue(threatDetector.isInternalIp("10.100.50.25"));
    }

    @Test
    public void testIsInternalIp_class_a_private_with_port() {
        // RFC 1918 with port
        assertTrue(threatDetector.isInternalIp("10.0.0.1:8080"));
        assertTrue(threatDetector.isInternalIp("10.255.255.255:443"));
        assertTrue(threatDetector.isInternalIp("10.100.50.25:3000"));
    }

    @Test
    public void testIsInternalIp_loopback() {
        // Loopback: 127.x.x.x
        assertTrue(threatDetector.isInternalIp("127.0.0.1"));
        assertTrue(threatDetector.isInternalIp("127.0.0.0"));
        assertTrue(threatDetector.isInternalIp("127.255.255.255"));
        assertTrue(threatDetector.isInternalIp("127.1.1.1"));
    }

    @Test
    public void testIsInternalIp_loopback_with_port() {
        // Loopback with port
        assertTrue(threatDetector.isInternalIp("127.0.0.1:8080"));
        assertTrue(threatDetector.isInternalIp("127.0.0.1:443"));
        assertTrue(threatDetector.isInternalIp("127.255.255.255:3000"));
    }

    @Test
    public void testIsInternalIp_link_local() {
        // Link-local: 169.254.x.x
        assertTrue(threatDetector.isInternalIp("169.254.0.0"));
        assertTrue(threatDetector.isInternalIp("169.254.169.254"));
        assertTrue(threatDetector.isInternalIp("169.254.255.255"));
        assertTrue(threatDetector.isInternalIp("169.254.1.1"));
    }

    @Test
    public void testIsInternalIp_link_local_with_port() {
        // Link-local with port
        assertTrue(threatDetector.isInternalIp("169.254.169.254:8080"));
        assertTrue(threatDetector.isInternalIp("169.254.0.0:443"));
        assertTrue(threatDetector.isInternalIp("169.254.255.255:9000"));
    }

    @Test
    public void testIsInternalIp_class_b_private() {
        // RFC 1918: 172.16.0.0 - 172.31.255.255
        assertTrue(threatDetector.isInternalIp("172.16.0.0"));
        assertTrue(threatDetector.isInternalIp("172.16.0.1"));
        assertTrue(threatDetector.isInternalIp("172.31.255.255"));
        assertTrue(threatDetector.isInternalIp("172.20.100.50"));
    }

    @Test
    public void testIsInternalIp_class_b_private_with_port() {
        // RFC 1918: 172.16-31 with port
        assertTrue(threatDetector.isInternalIp("172.16.0.1:8080"));
        assertTrue(threatDetector.isInternalIp("172.31.255.255:443"));
        assertTrue(threatDetector.isInternalIp("172.20.100.50:3000"));
    }

    @Test
    public void testIsInternalIp_class_c_private() {
        // RFC 1918: 192.168.x.x
        assertTrue(threatDetector.isInternalIp("192.168.0.0"));
        assertTrue(threatDetector.isInternalIp("192.168.0.1"));
        assertTrue(threatDetector.isInternalIp("192.168.255.255"));
        assertTrue(threatDetector.isInternalIp("192.168.1.100"));
    }

    @Test
    public void testIsInternalIp_class_c_private_with_port() {
        // RFC 1918: 192.168 with port
        assertTrue(threatDetector.isInternalIp("192.168.0.1:8080"));
        assertTrue(threatDetector.isInternalIp("192.168.255.255:443"));
        assertTrue(threatDetector.isInternalIp("192.168.1.100:3000"));
    }

    @Test
    public void testIsInternalIp_shared_address_space() {
        // RFC 6598: Shared Address Space 100.64.0.0 - 100.127.255.255
        assertTrue(threatDetector.isInternalIp("100.64.0.0"));
        assertTrue(threatDetector.isInternalIp("100.64.0.1"));
        assertTrue(threatDetector.isInternalIp("100.127.255.255"));
        assertTrue(threatDetector.isInternalIp("100.100.100.100"));
    }

    @Test
    public void testIsInternalIp_shared_address_space_with_port() {
        // RFC 6598 with port
        assertTrue(threatDetector.isInternalIp("100.64.0.1:8080"));
        assertTrue(threatDetector.isInternalIp("100.127.255.255:443"));
        assertTrue(threatDetector.isInternalIp("100.100.100.100:3000"));
    }

    @Test
    public void testIsInternalIp_this_network() {
        // This network: 0.0.0.0
        assertTrue(threatDetector.isInternalIp("0.0.0.0"));
    }

    @Test
    public void testIsInternalIp_this_network_with_port() {
        // This network with port
        assertTrue(threatDetector.isInternalIp("0.0.0.0:8080"));
        assertTrue(threatDetector.isInternalIp("0.0.0.0:443"));
    }

    @Test
    public void testIsInternalIp_public_addresses() {
        // Public addresses should NOT match
        assertFalse(threatDetector.isInternalIp("8.8.8.8"));
        assertFalse(threatDetector.isInternalIp("1.1.1.1"));
        assertFalse(threatDetector.isInternalIp("208.67.222.222"));
    }

    @Test
    public void testIsInternalIp_public_addresses_with_port() {
        // Public addresses with port should NOT match
        assertFalse(threatDetector.isInternalIp("8.8.8.8:8080"));
        assertFalse(threatDetector.isInternalIp("1.1.1.1:443"));
        assertFalse(threatDetector.isInternalIp("208.67.222.222:53"));
    }

    @Test
    public void testIsInternalIp_invalid_ranges_class_b() {
        // 172.15 and 172.32 are outside the private range
        assertFalse(threatDetector.isInternalIp("172.15.0.0"));
        assertFalse(threatDetector.isInternalIp("172.32.0.0"));
        assertFalse(threatDetector.isInternalIp("172.15.255.255"));
        assertFalse(threatDetector.isInternalIp("172.32.255.255"));
    }

    @Test
    public void testIsInternalIp_invalid_shared_address_space() {
        // 100.63 and 100.128 are outside the shared address space
        assertFalse(threatDetector.isInternalIp("100.63.0.0"));
        assertFalse(threatDetector.isInternalIp("100.128.0.0"));
    }

    @Test
    public void testIsInternalIp_invalid_link_local() {
        // 169.253 and 169.255 are not link-local
        assertFalse(threatDetector.isInternalIp("169.253.0.0"));
        assertFalse(threatDetector.isInternalIp("169.255.0.0"));
    }

    @Test
    public void testIsInternalIp_empty_string() {
        // Empty string should not match
        assertFalse(threatDetector.isInternalIp(""));
    }

    @Test
    public void testIsInternalIp_invalid_format() {
        // Invalid formats should not match
        assertFalse(threatDetector.isInternalIp("192.168.1"));
        assertFalse(threatDetector.isInternalIp("192.168"));
        assertFalse(threatDetector.isInternalIp("192"));
        assertFalse(threatDetector.isInternalIp("invalid"));
        assertFalse(threatDetector.isInternalIp("not-an-ip"));
    }

    @Test
    public void testIsInternalIp_edge_case_broadcast() {
        // Broadcast addresses in private ranges should match if in range
        assertTrue(threatDetector.isInternalIp("192.168.1.255"));
        assertTrue(threatDetector.isInternalIp("10.0.0.255"));
        assertTrue(threatDetector.isInternalIp("172.16.0.255"));
    }

    @Test
    public void testIsInternalIp_edge_case_network_addresses() {
        // Network addresses in private ranges should match
        assertTrue(threatDetector.isInternalIp("192.168.0.0"));
        assertTrue(threatDetector.isInternalIp("10.0.0.0"));
        assertTrue(threatDetector.isInternalIp("172.16.0.0"));
    }
}