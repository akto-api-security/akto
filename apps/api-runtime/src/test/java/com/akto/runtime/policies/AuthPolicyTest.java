package com.akto.runtime.policies;

import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import org.junit.jupiter.api.Assertions;
import org.junit.Test;

import java.util.*;


public class AuthPolicyTest {

    public static HttpResponseParams generateHttpResponseParams(Map<String, List<String>> headers) {
        HttpRequestParams httpRequestParams = new HttpRequestParams("GET", "/a", "", headers, "", 0);
        return new HttpResponseParams("",200,"",new HashMap<>(),"",httpRequestParams ,0,"0",false, HttpResponseParams.Source.OTHER, "", "");
    }

    @Test
    public void testUnauthenticated() {
        Map<String, List<String>> headers = new HashMap<>();
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams,apiInfo, null);
        Assertions.assertTrue(result);
    }

    @Test
    public void testUnauthenticatedWithData() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(AuthPolicy.AUTHORIZATION_HEADER_NAME, Collections.singletonList("Bearer woiefjwoeifw w"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams,apiInfo, null);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.AUTHORIZATION_HEADER);
        Assertions.assertEquals(apiInfo.getAllAuthTypesFound().size(), 1);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testBearer() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(AuthPolicy.AUTHORIZATION_HEADER_NAME, Collections.singletonList("Bearer woiefjwoeifw"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams,apiInfo, null);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.BEARER);
        Assertions.assertEquals(apiInfo.getAllAuthTypesFound().size(), 1);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testBearerWithExistingUnauthenticated() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(AuthPolicy.AUTHORIZATION_HEADER_NAME, Collections.singletonList("Bearer woiefjwoeifw"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.UNAUTHENTICATED);
        apiInfo.getAllAuthTypesFound().add(s);
        boolean result = AuthPolicy.findAuthType(httpResponseParams,apiInfo, null);
        Assertions.assertFalse(result);
        Assertions.assertEquals(apiInfo.getAllAuthTypesFound().size(), 2);
        Set<ApiInfo.AuthType> s2 = new HashSet<>();
        s2.add(ApiInfo.AuthType.BEARER);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s2));
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testBasic() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(AuthPolicy.AUTHORIZATION_HEADER_NAME, Collections.singletonList("Basic woiefjwoeifw"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams,apiInfo, null);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.BASIC);
        Assertions.assertEquals(apiInfo.getAllAuthTypesFound().size(), 1);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testJwt() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("someRandom", Collections.singletonList("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmt1c2hAZ21haWwuY29tIiwiaWF0IjoxNjQwNjkzNDUzLCJleHAiOjE2NDEyMTE4NTN9.oTq5FEeTlNt1YjaZ9JA8qdymArxJ8unNI8m5HLYn4ECeFOKQCv8SWnQ6uvwbbWPHa6HOYeLoD-tvPyVq-c6jlyGNf7bno8cCMB5ldyJ-I--F1xVp0iWKCMtlgdS2DgwFBdaZ9mdLCP3eZuieQV2Za8Lrzw1G1CpgJ-3vkijTw3KurKSDLT5Zv8JQRSxwj_VLeuaVkhSjYVltzTfY5tkl3CO3vNmlz6HIc4shxFXowA30xxgL438V1ELamv85fyGXg2EMhk5XeRDXq1QiLPBsQZ28FSk5TJAn2Xc_pwWXBw-N2P6Y_Hh0bL7KXpErgKQNQiAfNFHFzAUbuLefD6dJKg"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams,apiInfo, null);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.JWT);
        Assertions.assertEquals(apiInfo.getAllAuthTypesFound().size(), 1);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

    @Test
    public void testMultipleHappy() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("someRandom", Collections.singletonList("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmt1c2hAZ21haWwuY29tIiwiaWF0IjoxNjQwNjkzNDUzLCJleHAiOjE2NDEyMTE4NTN9.oTq5FEeTlNt1YjaZ9JA8qdymArxJ8unNI8m5HLYn4ECeFOKQCv8SWnQ6uvwbbWPHa6HOYeLoD-tvPyVq-c6jlyGNf7bno8cCMB5ldyJ-I--F1xVp0iWKCMtlgdS2DgwFBdaZ9mdLCP3eZuieQV2Za8Lrzw1G1CpgJ-3vkijTw3KurKSDLT5Zv8JQRSxwj_VLeuaVkhSjYVltzTfY5tkl3CO3vNmlz6HIc4shxFXowA30xxgL438V1ELamv85fyGXg2EMhk5XeRDXq1QiLPBsQZ28FSk5TJAn2Xc_pwWXBw-N2P6Y_Hh0bL7KXpErgKQNQiAfNFHFzAUbuLefD6dJKg"));
        headers.put(AuthPolicy.AUTHORIZATION_HEADER_NAME, Collections.singletonList("Basic woiefjwoeifw"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        boolean result = AuthPolicy.findAuthType(httpResponseParams,apiInfo, null);
        Assertions.assertFalse(result);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.JWT);
        s.add(ApiInfo.AuthType.BASIC);
        Assertions.assertEquals(apiInfo.getAllAuthTypesFound().size(), 1);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }
    @Test
    public void testMultipleWithExistingData() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("someRandom", Collections.singletonList("eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmt1c2hAZ21haWwuY29tIiwiaWF0IjoxNjQwNjkzNDUzLCJleHAiOjE2NDEyMTE4NTN9.oTq5FEeTlNt1YjaZ9JA8qdymArxJ8unNI8m5HLYn4ECeFOKQCv8SWnQ6uvwbbWPHa6HOYeLoD-tvPyVq-c6jlyGNf7bno8cCMB5ldyJ-I--F1xVp0iWKCMtlgdS2DgwFBdaZ9mdLCP3eZuieQV2Za8Lrzw1G1CpgJ-3vkijTw3KurKSDLT5Zv8JQRSxwj_VLeuaVkhSjYVltzTfY5tkl3CO3vNmlz6HIc4shxFXowA30xxgL438V1ELamv85fyGXg2EMhk5XeRDXq1QiLPBsQZ28FSk5TJAn2Xc_pwWXBw-N2P6Y_Hh0bL7KXpErgKQNQiAfNFHFzAUbuLefD6dJKg"));
        headers.put(AuthPolicy.AUTHORIZATION_HEADER_NAME, Collections.singletonList("Basic woiefjwoeifw"));
        HttpResponseParams httpResponseParams = generateHttpResponseParams(headers);
        ApiInfo apiInfo = new ApiInfo(httpResponseParams);
        Set<ApiInfo.AuthType> s = new HashSet<>();
        s.add(ApiInfo.AuthType.JWT);
        s.add(ApiInfo.AuthType.BASIC);
        apiInfo.getAllAuthTypesFound().add(s);
        boolean result = AuthPolicy.findAuthType(httpResponseParams,apiInfo, null);
        Assertions.assertFalse(result);
        Assertions.assertEquals(apiInfo.getAllAuthTypesFound().size(), 1);
        Assertions.assertTrue(apiInfo.getAllAuthTypesFound().contains(s));
    }

}
