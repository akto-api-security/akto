package com.akto.utils;

import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.URLMethods;

import java.util.*;

/**
 * Object Mother pattern for creating test data for CustomAuthUtil tests.
 * Provides pre-configured test objects representing common test scenarios.
 */
public class CustomAuthTestDataMother {

    public static final int TEST_API_COLLECTION_ID_1 = 1111111111;
    public static final int TEST_API_COLLECTION_ID_2 = 22222222;
    public static final int TEST_API_COLLECTION_ID_3 = 3333333;
    public static final int TEST_API_COLLECTION_ID_4 = 44444444;

    // ==================== ApiInfo Creation ====================

    /**
     * Creates ApiInfo with no existing auth types (empty set).
     * URL: /api/users, Method: GET
     */
    public static ApiInfo createApiInfoWithNoAuth() {
        return createApiInfo(
            TEST_API_COLLECTION_ID_1,
            "https://vulnerable-server.akto.io/api/users",
            URLMethods.Method.GET,
            new HashSet<>()
        );
    }

    /**
     * Creates ApiInfo with existing JWT and AUTHORIZATION_HEADER auth types.
     * URL: /api/college/eco/students, Method: POST
     */
    public static ApiInfo createApiInfoWithSingleAuthSet() {
        return createApiInfo(
            TEST_API_COLLECTION_ID_2,
            "https://vulnerable-server.akto.io/api/college/eco/students",
            URLMethods.Method.POST,
            createAuthTypeSets(
                new ApiInfo.AuthType[]{ApiInfo.AuthType.JWT, ApiInfo.AuthType.AUTHORIZATION_HEADER}
            )
        );
    }

    /**
     * Creates ApiInfo with multiple auth type sets: [[JWT], [BASIC, API_KEY]].
     * URL: /api/products, Method: PUT
     */
    public static ApiInfo createApiInfoWithMultipleAuthSets() {
        return createApiInfo(
            TEST_API_COLLECTION_ID_3,
            "https://vulnerable-server.akto.io/api/products",
            URLMethods.Method.PUT,
            createAuthTypeSets(
                new ApiInfo.AuthType[]{ApiInfo.AuthType.JWT},
                new ApiInfo.AuthType[]{ApiInfo.AuthType.BASIC, ApiInfo.AuthType.API_KEY}
            )
        );
    }

    /**
     * Creates ApiInfo with multiple auth type sets for testing preservation and enhancement.
     * Initial state: [[JWT], [BASIC, AUTHORIZATION_HEADER]]
     * URL: /api/test/preserve-enhance, Method: POST
     */
    public static ApiInfo createApiInfoForPreserveAndEnhance() {
        return createApiInfo(
            TEST_API_COLLECTION_ID_4,
            "https://vulnerable-server.akto.io/api/test/preserve-enhance",
            URLMethods.Method.POST,
            createAuthTypeSets(
                new ApiInfo.AuthType[]{ApiInfo.AuthType.JWT},
                new ApiInfo.AuthType[]{ApiInfo.AuthType.BASIC, ApiInfo.AuthType.AUTHORIZATION_HEADER}
            )
        );
    }


    // ==================== SampleData Creation ====================



    /**
     * Creates SampleData for API 2 with JWT prefix token (2 samples).
     */
    public static SampleData createSampleDataWithJwtPrefix() {
        String sample1 = createSampleJson(
            TEST_API_COLLECTION_ID_2,
            "https://vulnerable-server.akto.io/api/college/eco/students",
            "POST",
            "{\\\"authorization\\\":\\\"JWT eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4ifQ.ToSrgQdEWaTVBphY9QMPBmo1zWga\\\",\\\"content-length\\\":\\\"2\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"User Access Denied\\\"}"
        );

        String sample2 = createSampleJson(
            TEST_API_COLLECTION_ID_2,
            "https://vulnerable-server.akto.io/api/college/eco/students",
            "POST",
            "{\\\"authorization\\\":\\\"JWT eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoibG9naW4ifQ.ToSrgQdEWaTVBphY9QMPBmo1zWga\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"Success\\\"}"
        );

        return createSampleData(
            TEST_API_COLLECTION_ID_2,
            "https://vulnerable-server.akto.io/api/college/eco/students",
            URLMethods.Method.POST,
            Arrays.asList(sample1, sample2)
        );
    }

    /**
     * Creates SampleData for API 3 with Basic auth and API key.
     */
    public static SampleData createSampleDataWithBasicAndApiKey() {
        String sample = createSampleJson(
            TEST_API_COLLECTION_ID_3,
            "https://vulnerable-server.akto.io/api/products",
            "PUT",
            "{\\\"authorization\\\":\\\"Basic dXNlcjpwYXNzd29yZA==\\\",\\\"x-api-key\\\":\\\"abc123xyz\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{\\\"name\\\":\\\"Product1\\\"}",
            "{\\\"id\\\":123}"
        );

        return createSampleData(
            TEST_API_COLLECTION_ID_3,
            "https://vulnerable-server.akto.io/api/products",
            URLMethods.Method.PUT,
            Arrays.asList(sample)
        );
    }

        /**
     * Creates SampleData for API 1 with Bearer JWT token in authorization header.
     */
    public static SampleData createSampleDataWithBearerToken(int apiCollectionId, String url, URLMethods.Method method) {
        String sample = createSampleJson(
            apiCollectionId,
            url,
            method.name(),
            "{\\\"authorization\\\":\\\"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"users\\\":[{\\\"id\\\":1,\\\"name\\\":\\\"John\\\"}]}"
        );

        return createSampleData(apiCollectionId, url, method, Arrays.asList(sample));
    }

    /**
     * Creates SampleData with no authentication headers.
     */
    public static SampleData createSampleDataWithNoAuth(int apiCollectionId, String url, URLMethods.Method method) {
        String sample = createSampleJson(
            apiCollectionId,
            url,
            method.name(),
            "{\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"Success\\\"}"
        );

        return createSampleData(apiCollectionId, url, method, Arrays.asList(sample));
    }

    /**
     * Creates SampleData with API key header.
     */
    public static SampleData createSampleDataWithApiKey(int apiCollectionId, String url, URLMethods.Method method) {
        String sample = createSampleJson(
            apiCollectionId,
            url,
            method.name(),
            "{\\\"x-api-key\\\":\\\"abc123xyz456\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"Success\\\"}"
        );

        return createSampleData(apiCollectionId, url, method, Arrays.asList(sample));
    }

    /**
     * Creates SampleData with Basic authentication.
     */
    public static SampleData createSampleDataWithBasicAuth(int apiCollectionId, String url, URLMethods.Method method) {
        String sample = createSampleJson(
            apiCollectionId,
            url,
            method.name(),
            "{\\\"authorization\\\":\\\"Basic dXNlcm5hbWU6cGFzc3dvcmQ=\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"Success\\\"}"
        );

        return createSampleData(apiCollectionId, url, method, Arrays.asList(sample));
    }

    /**
     * Creates SampleData with MTLS (client certificate).
     */
    public static SampleData createSampleDataWithMtls(int apiCollectionId, String url, URLMethods.Method method) {
        String sample = createSampleJson(
            apiCollectionId,
            url,
            method.name(),
            "{\\\"clientcert\\\":\\\"MIIDXTCCAkWgAwIBAgIJAKL0UG4+...\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"Success\\\"}"
        );

        return createSampleData(apiCollectionId, url, method, Arrays.asList(sample));
    }

    /**
     * Creates SampleData with session token.
     */
    public static SampleData createSampleDataWithSessionToken(int apiCollectionId, String url, URLMethods.Method method) {
        String sample = createSampleJson(
            apiCollectionId,
            url,
            method.name(),
            "{\\\"session-token\\\":\\\"sess_abc123xyz456789\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"Success\\\"}"
        );

        return createSampleData(apiCollectionId, url, method, Arrays.asList(sample));
    }

    /**
     * Creates SampleData with multiple auth types (Authorization + MTLS).
     */
    public static SampleData createSampleDataWithAuthorizationAndMtls(int apiCollectionId, String url, URLMethods.Method method) {
        String sample = createSampleJson(
            apiCollectionId,
            url,
            method.name(),
            "{\\\"authorization\\\":\\\"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U\\\",\\\"clientcert\\\":\\\"MIIDXTCCAkWgAwIBAgIJAKL0UG4+...\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"Success\\\"}"
        );

        return createSampleData(apiCollectionId, url, method, Arrays.asList(sample));
    }

    /**
     * Creates SampleData with Basic auth + API key.
     */
    public static SampleData createSampleDataWithBasicAndApiKey(int apiCollectionId, String url, URLMethods.Method method) {
        String sample = createSampleJson(
            apiCollectionId,
            url,
            method.name(),
            "{\\\"authorization\\\":\\\"Basic dXNlcm5hbWU6cGFzc3dvcmQ=\\\",\\\"x-api-key\\\":\\\"abc123xyz\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"Success\\\"}"
        );

        return createSampleData(apiCollectionId, url, method, Arrays.asList(sample));
    }

    /**
     * Creates SampleData with TWO samples: one with API_KEY, one with MTLS.
     * This is for testing when multiple different auth types come from different samples.
     */
    public static SampleData createSampleDataWithApiKeyAndMtlsSamples(int apiCollectionId, String url, URLMethods.Method method) {
        List<String> samples = new ArrayList<>();

        // Sample 1: API_KEY
        String apiKeySample = createSampleJson(
            apiCollectionId,
            url,
            method.name(),
            "{\\\"x-api-key\\\":\\\"abc123xyz456\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"Success\\\"}"
        );
        samples.add(apiKeySample);

        // Sample 2: MTLS
        String mtlsSample = createSampleJson(
            apiCollectionId,
            url,
            method.name(),
            "{\\\"clientcert\\\":\\\"MIIDXTCCAkWgAwIBAgIJAKL0UG4+...\\\",\\\"content-type\\\":\\\"application/json\\\"}",
            "{}",
            "{\\\"message\\\":\\\"Success\\\"}"
        );
        samples.add(mtlsSample);

        return createSampleData(apiCollectionId, url, method, samples);
    }


    // ==================== Helper Methods ====================

    private static ApiInfo createApiInfo(int apiCollectionId, String url, URLMethods.Method method,
                                         Set<Set<ApiInfo.AuthType>> allAuthTypesFound) {
        ApiInfo apiInfo = new ApiInfo(apiCollectionId, url, method);
        apiInfo.setAllAuthTypesFound(allAuthTypesFound);
        apiInfo.setLastSeen(1758562434);
        apiInfo.setCollectionIds(Arrays.asList(apiCollectionId));
        return apiInfo;
    }

    private static SampleData createSampleData(int apiCollectionId, String url, URLMethods.Method method,
                                               List<String> samples) {
        Key key = new Key(apiCollectionId, url, method, -1, 0, 0);
        SampleData sampleData = new SampleData(key, samples);
        sampleData.setCollectionIds(Arrays.asList(apiCollectionId));
        return sampleData;
    }

    private static String createSampleJson(int apiCollectionId, String url, String method,
                                           String requestHeaders, String requestPayload, String responsePayload) {
        return String.format(
            "{\"destIp\":null,\"method\":\"%s\",\"requestPayload\":\"%s\",\"responsePayload\":\"%s\"," +
            "\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/1.1\",\"akto_vxlan_id\":%d," +
            "\"path\":\"%s\",\"requestHeaders\":\"%s\",\"responseHeaders\":\"{}\"," +
            "\"time\":\"1758219946\",\"statusCode\":\"200\",\"status\":\"OK\"," +
            "\"akto_account_id\":\"1758179941\",\"is_pending\":\"false\"}",
            method, requestPayload, responsePayload, apiCollectionId, url, requestHeaders
        );
    }

    private static Set<Set<ApiInfo.AuthType>> createAuthTypeSets(ApiInfo.AuthType[]... authTypeSets) {
        Set<Set<ApiInfo.AuthType>> result = new HashSet<>();
        for (ApiInfo.AuthType[] authTypes : authTypeSets) {
            result.add(new HashSet<>(Arrays.asList(authTypes)));
        }
        return result;
    }
}
