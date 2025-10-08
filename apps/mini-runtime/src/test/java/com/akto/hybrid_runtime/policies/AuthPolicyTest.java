package com.akto.hybrid_runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

public class AuthPolicyTest {

    public static HttpResponseParams generateHttpResponseParams(Map<String, List<String>> headers) {
        HttpRequestParams httpRequestParams = new HttpRequestParams("GET", "/a", "", headers, "", 0);
        return new HttpResponseParams("", 200, "", new HashMap<>(), "", httpRequestParams, 0, "0", false, HttpResponseParams.Source.OTHER, "", "");
    }

    List<CustomAuthType> customAuthTypes = new ArrayList<>();

    @Test
    public void testUnauthenticated() {
        Map<String, List<String>> headers = new HashMap<>();
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertTrue(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.UNAUTHENTICATED);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testBearer() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(AuthPolicy.AUTHORIZATION_HEADER_NAME, Collections.singletonList("Bearer woiefjwoeifw"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.BEARER);
        Assertions.assertEquals(1, apiInfo.getAllAuthTypesFound().size());
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testBasic() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(AuthPolicy.AUTHORIZATION_HEADER_NAME, Collections.singletonList("Basic woiefjwoeifw"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.BASIC);
        Assertions.assertEquals(1, apiInfo.getAllAuthTypesFound().size());
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testJwt() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("someRandom", Collections.singletonList("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmt1c2hAZ21haWwuY29tIiwiaWF0IjoxNjQwNjkzNDUzLCJleHAiOjE2NDEyMTE4NTN9.oTq5FEeTlNt1YjaZ9JA8qdymArxJ8unNI8m5HLYn4ECeFOKQCv8SWnQ6uvwbbWPHa6HOYeLoD-tvPyVq-c6jlyGNf7bno8cCMB5ldyJ-I--F1xVp0iWKCMtlgdS2DgwFBdaZ9mdLCP3eZuieQV2Za8Lrzw1G1CpgJ-3vkijTw3KurKSDLT5Zv8JQRSxwj_VLeuaVkhSjYVltzTfY5tkl3CO3vNmlz6HIc4shxFXowA30xxgL438V1ELamv85fyGXg2EMhk5XeRDXq1QiLPBsQZ28FSk5TJAn2Xc_pwWXBw-N2P6Y_Hh0bL7KXpErgKQNQiAfNFHFzAUbuLefD6dJKg"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.JWT);
        Assertions.assertEquals(1, apiInfo.getAllAuthTypesFound().size());
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    // API_KEY tests
    @Test
    public void testApiKeyWithHyphen() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-api-key", Collections.singletonList("some-random-key-value-12345"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.API_KEY);
        Assertions.assertEquals(1, apiInfo.getAllAuthTypesFound().size());
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testApiKeyWithUnderscore() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x_api_key", Collections.singletonList("some-random-key-value-12345"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.API_KEY);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testApiKeySimple() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("api_key", Collections.singletonList("some-random-key-value-12345"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.API_KEY);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testApiKeyWithHyphenSimple() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("api-key", Collections.singletonList("some-random-key-value-12345"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.API_KEY);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testApiKeyCamelCase() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("apiKey", Collections.singletonList("some-random-key-value-12345"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.API_KEY);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testPassKey() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-pass-key", Collections.singletonList("some-random-pass-value-12345"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.API_KEY);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testApiKeyUpperCase() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-API-KEY", Collections.singletonList("some-random-key-value-12345"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.API_KEY);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    // MTLS tests
    @Test
    public void testMtlsXClientCert() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Client-Cert", Collections.singletonList("-----BEGIN CERTIFICATE-----\nMIIDXTCCAkWgAwIBAgIJAKL0UG+dkP"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.MTLS);
        Assertions.assertEquals(1, apiInfo.getAllAuthTypesFound().size());
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testMtlsXSslClientCert() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-SSL-Client-Cert", Collections.singletonList("-----BEGIN CERTIFICATE-----\nMIIDXTCCAkWgAwIBAgIJAKL0UG+dkP"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.MTLS);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testMtlsSslCert() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-SSL-CERT", Collections.singletonList("cert-data-here"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.MTLS);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testMtlsClientDN() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Client-DN", Collections.singletonList("CN=client.example.com,O=Example Inc,C=US"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.MTLS);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testMtlsSslClientDN() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("SSL-Client-S-DN", Collections.singletonList("CN=client.example.com,O=Example Inc,C=US"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.MTLS);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testMtlsSslClientVerify() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-SSL-Client-Verify", Collections.singletonList("SUCCESS"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.MTLS);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testMtlsForwardedClientCert() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Forwarded-Client-Cert", Collections.singletonList("By=spiffe://cluster.local/ns/default/sa/frontend;Hash=abc123"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.MTLS);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testMtlsEmptyValue() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-Client-Cert", Collections.singletonList(""));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertTrue(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.UNAUTHENTICATED);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testMtlsCaseInsensitive() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-client-cert", Collections.singletonList("cert-data"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.MTLS);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    // Multiple auth types test
    @Test
    public void testMultipleAuthTypes() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("X-API-KEY", Collections.singletonList("my-api-key-123"));
        headers.put("X-Client-Cert", Collections.singletonList("cert-data"));
        headers.put(AuthPolicy.AUTHORIZATION_HEADER_NAME, Collections.singletonList("Bearer token123"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.API_KEY);
        s.add(ApiInfo.AuthType.MTLS);
        s.add(ApiInfo.AuthType.BEARER);
        Assertions.assertEquals(1, apiInfo.getAllAuthTypesFound().size());
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testApiKeyAndJwt() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-api-key", Collections.singletonList("my-api-key-123"));
        headers.put("jwt-token", Collections.singletonList("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmt1c2hAZ21haWwuY29tIiwiaWF0IjoxNjQwNjkzNDUzLCJleHAiOjE2NDEyMTE4NTN9.oTq5FEeTlNt1YjaZ9JA8qdymArxJ8unNI8m5HLYn4ECeFOKQCv8SWnQ6uvwbbWPHa6HOYeLoD-tvPyVq-c6jlyGNf7bno8cCMB5ldyJ-I--F1xVp0iWKCMtlgdS2DgwFBdaZ9mdLCP3eZuieQV2Za8Lrzw1G1CpgJ-3vkijTw3KurKSDLT5Zv8JQRSxwj_VLeuaVkhSjYVltzTfY5tkl3CO3vNmlz6HIc4shxFXowA30xxgL438V1ELamv85fyGXg2EMhk5XeRDXq1QiLPBsQZ28FSk5TJAn2Xc_pwWXBw-N2P6Y_Hh0bL7KXpErgKQNQiAfNFHFzAUbuLefD6dJKg"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams, apiInfo, null, customAuthTypes);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.API_KEY);
        s.add(ApiInfo.AuthType.JWT);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }
}
