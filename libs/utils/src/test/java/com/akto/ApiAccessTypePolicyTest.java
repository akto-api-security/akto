package com.akto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.URLMethods;
import com.akto.runtime.policies.ApiAccessTypePolicy;
import org.junit.jupiter.api.*;

public class ApiAccessTypePolicyTest {

    public static List<String> privateCidrList = Arrays.asList("10.0.0.0/8", "172.16.0.0/12",
        "192.168.0.0/16", "127.0.0.0/8", "169.254.0.0/16", "224.0.0.0/4", "192.0.2.0/24", "198.51.100.0/24",
        "203.0.113.0/24", "255.255.255.255/32", "100.64.0.0/10", "192.88.99.0/24", "240.0.0.0/4");

    private HttpResponseParams createHttpResponseParams(Map<String, List<String>> headers,
            String sourceIP, String destIP, String direction) {
        HttpRequestParams requestParams = new HttpRequestParams(
            "GET", "http://example.com/api", "HTTP/1.1", headers, "", 1
        );
        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(requestParams);
        responseParams.setSourceIP(sourceIP);
        responseParams.setDestIP(destIP);
        responseParams.setDirection(direction);
        return responseParams;
    }

    private ApiInfo createApiInfo() {
        return new ApiInfo(1, "/api/test", URLMethods.Method.GET);
    }

    // ==================== PUBLIC Access Type Tests ====================

    @Test
    @DisplayName("PUBLIC: Incoming request with public IP in x-forwarded-for header")
    public void testPublicAccessType_XForwardedFor() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("8.8.8.8"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC));
    }

    @Test
    @DisplayName("PUBLIC: Incoming request with public sourceIP")
    public void testPublicAccessType_SourceIP() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();

        HttpResponseParams responseParams = createHttpResponseParams(headers, "8.8.8.8:5600", "1.1.1.1:4321", "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC));
    }

    @Test
    @DisplayName("PUBLIC: Incoming request with public IP in x-real-ip header")
    public void testPublicAccessType_XRealIp() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-real-ip", Arrays.asList("8.8.8.8"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC));
    }

    @Test
    @DisplayName("PUBLIC: Multiple IPs with at least one public (returns PUBLIC immediately)")
    public void testPublicAccessType_MultipleIpsWithOnePublic() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("10.0.0.1", "8.8.8.8"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC));
        assertEquals(1, apiInfo.getApiAccessTypes().size());
    }

    // ==================== PRIVATE Access Type Tests ====================

    @Test
    @DisplayName("PRIVATE: All IPs in 10.x.x.x range")
    public void testPrivateAccessType_10Range() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("10.0.0.1"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PRIVATE));
    }

    @Test
    @DisplayName("PRIVATE: All IPs in 172.16.x.x range")
    public void testPrivateAccessType_172Range() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("172.16.0.1"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PRIVATE));
    }

    @Test
    @DisplayName("PRIVATE: All IPs in 192.168.x.x range")
    public void testPrivateAccessType_192Range() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("192.168.1.1"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, "127.0.0.6:39215", "192.168.14.232:53132", "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PRIVATE));
    }

    @Test
    @DisplayName("PRIVATE: Loopback address 127.0.0.1")
    public void testPrivateAccessType_Loopback() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("127.0.0.1"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PRIVATE));
    }

    @Test
    @DisplayName("PRIVATE: Link-local address 169.254.x.x")
    public void testPrivateAccessType_LinkLocal() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("169.254.1.1"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PRIVATE));
    }

    @Test
    @DisplayName("PRIVATE: Multiple private IPs")
    public void testPrivateAccessType_MultiplePrivateIps() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("10.0.0.1"));
        headers.put("x-real-ip", Arrays.asList("192.168.1.1"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, "172.16.0.1", null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PRIVATE));
        assertEquals(1, apiInfo.getApiAccessTypes().size());
    }

    @Test
    @DisplayName("PRIVATE: Custom private CIDR from constructor")
    public void testPrivateAccessType_CustomCidr() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Arrays.asList("203.0.113.0/24"), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("203.0.113.50"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PRIVATE));
    }

    // ==================== PARTNER Access Type Tests ====================

    @Test
    @DisplayName("PARTNER: IP matches partner IP list")
    public void testPartnerAccessType_ExactMatch() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), Arrays.asList("14.14.113.100"));
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("14.14.113.100"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PARTNER));
    }

    @Test
    @DisplayName("PARTNER: IP with port matches partner IP (contains check)")
    public void testPartnerAccessType_IpWithPort() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), Arrays.asList("52.84.200.100"));
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("52.84.200.100:8080"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PARTNER));
    }

    @Test
    @DisplayName("PARTNER: Mix of private + partner IPs resolves to PARTNER")
    public void testPartnerAccessType_MixedWithPrivate() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), Arrays.asList("52.84.200.100"));
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("10.0.0.1", "52.84.200.100"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PARTNER));
    }

    // ==================== THIRD_PARTY Access Type Tests ====================

    @Test
    @DisplayName("THIRD_PARTY: Outgoing request with public IP and .com TLD")
    public void testThirdPartyAccessType_ComTld() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("8.8.8.8"));
        headers.put("host", Arrays.asList("api.example.com"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "2");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.THIRD_PARTY));
    }

    @Test
    @DisplayName("THIRD_PARTY: Outgoing request with public IP and .io TLD")
    public void testThirdPartyAccessType_IoTld() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("8.8.8.8"));
        headers.put("host", Arrays.asList("api.stripe.io"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "2");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.THIRD_PARTY));
    }

    @Test
    @DisplayName("PRIVATE: Outgoing request with internal K8s service host")
    public void testPrivateAccessType_K8sInternalService() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("8.8.8.8"));
        headers.put("host", Arrays.asList("my-service.default.svc.cluster.local"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "2");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PRIVATE));
    }

    @Test
    @DisplayName("PRIVATE: Outgoing request with internal hostname (no valid TLD)")
    public void testPrivateAccessType_InternalHostname() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("8.8.8.8"));
        headers.put("host", Arrays.asList("internal-api-server"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "2");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PRIVATE));
    }

    @Test
    @DisplayName("THIRD_PARTY: Outgoing request with host containing port")
    public void testThirdPartyAccessType_HostWithPort() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("8.8.8.8"));
        headers.put("host", Arrays.asList("api.example.com:8080"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "2");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.THIRD_PARTY));
    }

    // ==================== Edge Cases / IP Parsing Tests ====================

    @Test
    @DisplayName("EDGE: Comma-separated IPs in single header value")
    public void testEdgeCase_CommaSeparatedIps() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("10.0.0.1, 8.8.8.8"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC));
    }

    @Test
    @DisplayName("EDGE: IP with port in sourceIP is cleaned")
    public void testEdgeCase_SourceIpWithPort() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();

        HttpResponseParams responseParams = createHttpResponseParams(headers, "8.8.8.8:12345", null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC));
    }

    @Test
    @DisplayName("EDGE: Skip 0.0.0.0 (standard private IP)")
    public void testEdgeCase_SkipZeroIp() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("0.0.0.0", "10.0.0.1"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PRIVATE));
    }

    @Test
    @DisplayName("EDGE: Empty IP list - no access type set")
    public void testEdgeCase_EmptyIpList() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().isEmpty());
    }

    @Test
    @DisplayName("EDGE: Null sourceIP and destIP with 'null' string")
    public void testEdgeCase_NullStringIp() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();

        HttpResponseParams responseParams = createHttpResponseParams(headers, "null", "null", "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().isEmpty());
    }

    @Test
    @DisplayName("EDGE: IPs across multiple client IP headers")
    public void testEdgeCase_MultipleClientIpHeaders() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("10.0.0.1"));
        headers.put("x-real-ip", Arrays.asList("10.0.0.2"));
        headers.put("x-client-ip", Arrays.asList("8.8.8.8"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC));
    }

    @Test
    @DisplayName("EDGE: Direction parsing - invalid direction defaults to incoming")
    public void testEdgeCase_InvalidDirection() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-forwarded-for", Arrays.asList("8.8.8.8"));

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, null, "invalid");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC));
    }

    @Test
    @DisplayName("EDGE: destIP is also checked")
    public void testEdgeCase_DestIpChecked() {
        ApiAccessTypePolicy policy = new ApiAccessTypePolicy(Collections.emptyList(), null);
        policy.setPrivateCidrList(privateCidrList);
        Map<String, List<String>> headers = new HashMap<>();

        HttpResponseParams responseParams = createHttpResponseParams(headers, null, "8.8.8.8", "1");
        ApiInfo apiInfo = createApiInfo();

        policy.findApiAccessType(responseParams, apiInfo);

        assertTrue(apiInfo.getApiAccessTypes().contains(ApiAccessType.PUBLIC));
    }

    // ==================== Existing ipInCidr Tests ====================
    
    @Test
    public void testIpv4MatchSingleCidr() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("10.0.0.0/8"), null);
        assertTrue(p.ipInCidr("10.1.2.3"));
    }

    @Test
    public void testIpv4NoMatchSingleCidr() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("10.0.0.0/8"), null);
        assertFalse(p.ipInCidr("11.0.0.1"));
    }

    @Test
    public void testMultipleCidrsOneMatches() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("192.168.1.0/24", "172.16.0.0/12"), null);
        assertTrue(p.ipInCidr("172.31.255.255"));
        assertFalse(p.ipInCidr("8.8.8.8"));
    }

    @Test
    public void testEmptyCidrListReturnsFalse() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Collections.emptyList(), null);
        assertFalse(p.ipInCidr("10.0.0.1"));
    }

    @Test
    public void testBoundaryAddressesIncluded() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("192.168.1.0/24"), null);
        assertTrue(p.ipInCidr("192.168.1.0"));
        assertTrue(p.ipInCidr("192.168.1.255"));
    }

    @Test
    public void testIpv6Match() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("2001:db8::/32"), null);
        assertTrue(p.ipInCidr("2001:db8::1"));
    }

    @Test
    public void testIpv6NoMatch() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("2001:db8::/32"), null);
        assertFalse(p.ipInCidr("2001:db9::1"));
    }

    @Test
    public void testUpdateCidrsReflectsInIpInCidr() {
        ApiAccessTypePolicy p = new ApiAccessTypePolicy(Arrays.asList("10.0.0.0/8"), null);
        assertTrue(p.ipInCidr("10.2.3.4"));
        p.setPrivateCidrList(Arrays.asList("192.168.0.0/16"));
        assertFalse(p.ipInCidr("10.2.3.4"));
        assertTrue(p.ipInCidr("192.168.10.10"));
    }
}
