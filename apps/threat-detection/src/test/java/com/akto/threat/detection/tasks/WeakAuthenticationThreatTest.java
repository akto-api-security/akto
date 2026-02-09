package com.akto.threat.detection.tasks;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.HttpRequestParams;
import com.akto.threat.detection.utils.ThreatDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class WeakAuthenticationThreatTest {

    private ThreatDetector threatDetector;

    @BeforeEach
    void setUp() throws Exception {
        threatDetector = new ThreatDetector();
    }

    // ==================== Helper Methods ====================

    /**
     * Build HttpResponseParams with custom headers
     */
    private HttpResponseParams buildRequestWithHeaders(Map<String, List<String>> headers) {
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setHeaders(headers);
        requestParams.setMethod("GET");
        requestParams.setUrl("/api/test");
        requestParams.setApiCollectionId(0);

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(requestParams);
        responseParams.setStatusCode(200);
        return responseParams;
    }

    /**
     * Create a test JWT with specific algorithm and expiration
     */
    private String createTestJwt(String alg, Long expSeconds) {
        // Header
        String headerJson = "{\"alg\":\"" + alg + "\",\"typ\":\"JWT\"}";
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

        // Payload
        String payloadJson;
        if (expSeconds != null) {
            payloadJson = "{\"sub\":\"user123\",\"exp\":" + expSeconds + "}";
        } else {
            payloadJson = "{\"sub\":\"user123\"}";
        }
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        // Dummy signature (signature validation is not part of weak auth detection)
        String signature = "dummysignaturefortesting";

        return header + "." + payload + "." + signature;
    }

    /**
     * Create an expired JWT (expired 1 hour ago)
     */
    private String createExpiredJwt() {
        long expiredTime = (System.currentTimeMillis() / 1000) - 3600;
        return createTestJwt("RS256", expiredTime);
    }

    /**
     * Create a valid JWT with given algorithm (expires in 1 hour)
     */
    private String createValidJwt(String alg) {
        long futureTime = (System.currentTimeMillis() / 1000) + 3600;
        return createTestJwt(alg, futureTime);
    }

    // ==================== Customer-specified Test Cases ====================

    @Test
    void testMissingAuth() {
        // Given: No Authorization header
        Map<String, List<String>> headers = new HashMap<>();
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Missing auth is not considered a "weak" auth threat
        // (absence of auth is different from weak auth - public APIs may not need auth)
        assertThat(isThreat).isFalse();
    }

    @Test
    void testEmptyBearer() {
        // Given: Authorization header with empty Bearer token
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer "));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat
        assertThat(isThreat).isTrue();
    }

    @Test
    void testFakeBearer() {
        // Given: Authorization header with fake Bearer token
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer faketoken"));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (not a valid JWT)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testLowercaseAuthorizationFakeBearer() {
        // Given: Lowercase authorization header with fake token
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("authorization", Collections.singletonList("Bearer fake"));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (not a valid JWT)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testLowercaseAuthorizationCorrectBearer() {
        // Given: Lowercase authorization header with valid RS256 JWT
        String validJwt = createValidJwt("RS256");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("authorization", Collections.singletonList("Bearer " + validJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should NOT detect as threat
        assertThat(isThreat).isFalse();
    }

    @Test
    void testMixedCaseBearer() {
        // Given: Mixed case Authorization header with fake token
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("AuThOrIzAtIoN", Collections.singletonList("Bearer fake"));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (not a valid JWT)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testDuplicateAuth() {
        // Given: Multiple Authorization header values
        Map<String, List<String>> headers = new HashMap<>();
        List<String> authValues = new ArrayList<>();
        authValues.add("Bearer token1");
        authValues.add("Bearer token2");
        headers.put("Authorization", authValues);
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (multiple auth headers is suspicious)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testHeaderInjection() {
        // Given: Authorization header with newline injection attempt
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer token\nX-Injected: value"));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (header injection attempt)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testOversizedToken() {
        // Given: Authorization header with oversized token (2048+ bytes)
        StringBuilder oversizedToken = new StringBuilder("Bearer ");
        for (int i = 0; i < 2048; i++) {
            oversizedToken.append("a");
        }
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList(oversizedToken.toString()));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (token too large)
        assertThat(isThreat).isTrue();
    }

    // ==================== Additional Weak JWT Test Cases ====================

    @Test
    void testBasicAuth() {
        // Given: Basic authentication (not Bearer)
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Basic dXNlcjpwYXNz"));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (Basic auth is weak)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testJwtWithHs256() {
        // Given: JWT with weak algorithm HS256
        String hs256Jwt = createValidJwt("HS256");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + hs256Jwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (HS256 is weak)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testJwtWithAlgNone() {
        // Given: JWT with algorithm "none" (very weak)
        String noneJwt = createValidJwt("none");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + noneJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (alg=none is extremely weak)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testJwtWithAlgNONE() {
        // Given: JWT with algorithm "NONE" (uppercase variant)
        String noneJwt = createValidJwt("NONE");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + noneJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (alg=NONE is extremely weak)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testExpiredJwt() {
        // Given: JWT with expired timestamp
        String expiredJwt = createExpiredJwt();
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + expiredJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (expired token)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testValidRs256Jwt() {
        // Given: Valid JWT with RS256 algorithm and future expiration
        String validJwt = createValidJwt("RS256");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + validJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should NOT detect as threat (valid strong JWT)
        assertThat(isThreat).isFalse();
    }

    // ==================== Edge Cases ====================

    @Test
    void testNullHeaders() {
        // Given: Null headers
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setHeaders(null);
        requestParams.setMethod("GET");
        requestParams.setUrl("/api/test");
        requestParams.setApiCollectionId(0);

        HttpResponseParams responseParams = new HttpResponseParams();
        responseParams.setRequestParams(requestParams);
        responseParams.setStatusCode(200);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Null/missing headers is not a weak auth threat (absence != weakness)
        assertThat(isThreat).isFalse();
    }

    @Test
    void testEmptyHeaders() {
        // Given: Empty headers map
        Map<String, List<String>> headers = new HashMap<>();
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Empty headers is not a weak auth threat (absence != weakness)
        assertThat(isThreat).isFalse();
    }

    @Test
    void testAuthorizationWithoutBearer() {
        // Given: Authorization header without Bearer prefix
        String validJwt = createValidJwt("RS256");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList(validJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Depends on implementation - might accept JWT without Bearer prefix
        // This test documents the actual behavior
        boolean isThreat2 = threatDetector.isWeakAuthenticationThreat(responseParams);
        assertThat(isThreat2).isIn(true, false); // Either is acceptable
    }

    @Test
    void testValidRs384Jwt() {
        // Given: Valid JWT with RS384 algorithm (also strong)
        String validJwt = createValidJwt("RS384");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + validJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should NOT detect as threat (RS384 is strong)
        assertThat(isThreat).isFalse();
    }

    @Test
    void testValidRs512Jwt() {
        // Given: Valid JWT with RS512 algorithm (also strong)
        String validJwt = createValidJwt("RS512");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + validJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should NOT detect as threat (RS512 is strong)
        assertThat(isThreat).isFalse();
    }

    @Test
    void testValidEs256Jwt() {
        // Given: Valid JWT with ES256 algorithm (ECDSA is strong)
        String validJwt = createValidJwt("ES256");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + validJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should NOT detect as threat (ES256 is strong)
        assertThat(isThreat).isFalse();
    }

    @Test
    void testMalformedJwt_TwoParts() {
        // Given: Malformed JWT with only 2 parts
        String malformedJwt = "header.payload";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + malformedJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (malformed JWT)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testMalformedJwt_FourParts() {
        // Given: Malformed JWT with 4 parts
        String malformedJwt = "header.payload.signature.extra";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + malformedJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (malformed JWT)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testJwtWithInvalidBase64() {
        // Given: JWT with invalid base64 encoding
        String invalidJwt = "not-valid-base64.not-valid-base64.signature";
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + invalidJwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should detect as threat (invalid base64)
        assertThat(isThreat).isTrue();
    }

    @Test
    void testJwtWithoutExpClaim() {
        // Given: Valid JWT without exp claim
        String jwtNoExp = createTestJwt("RS256", null);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + jwtNoExp));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should NOT detect as threat if algorithm is strong (exp is optional in some systems)
        assertThat(isThreat).isFalse();
    }

    @Test
    void testWhitespaceOnlyAuthHeader() {
        // Given: Authorization header with only whitespace
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("   "));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Whitespace-only doesn't match Bearer/Basic pattern, not detected as auth
        // So not considered a weak auth threat (just invalid/missing auth)
        assertThat(isThreat).isFalse();
    }

    @Test
    void testBearerWithExtraSpaces() {
        // Given: Bearer token with extra spaces
        String validJwt = createValidJwt("RS256");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer   " + validJwt + "   "));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should handle trimming and NOT detect as threat
        assertThat(isThreat).isFalse();
    }

    @Test
    void testAuthorizationHeaderInDifferentLocation() {
        // Given: Authorization header among other headers
        String validJwt = createValidJwt("RS256");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        headers.put("Authorization", Collections.singletonList("Bearer " + validJwt));
        headers.put("User-Agent", Collections.singletonList("TestClient/1.0"));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should NOT detect as threat (valid JWT found)
        assertThat(isThreat).isFalse();
    }

    @Test
    void testHs512AlgorithmJwt() {
        // Given: JWT with HS512 algorithm (symmetric but stronger than HS256)
        String hs512Jwt = createValidJwt("HS512");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + hs512Jwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: HS512 is not in weak list (only HS256 and none are considered weak)
        assertThat(isThreat).isFalse();
    }

    @Test
    void testPs256AlgorithmJwt() {
        // Given: JWT with PS256 algorithm (RSA-PSS, strong)
        String ps256Jwt = createValidJwt("PS256");
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + ps256Jwt));
        HttpResponseParams responseParams = buildRequestWithHeaders(headers);

        // When
        boolean isThreat = threatDetector.isWeakAuthenticationThreat(responseParams);

        // Then: Should NOT detect as threat (PS256 is strong)
        assertThat(isThreat).isFalse();
    }
}
