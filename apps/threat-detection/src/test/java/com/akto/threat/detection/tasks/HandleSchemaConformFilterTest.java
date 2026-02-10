// package com.akto.threat.detection.tasks;

// import com.akto.dto.ApiInfo;
// import com.akto.dto.HttpRequestParams;
// import com.akto.dto.HttpResponseParams;
// import com.akto.dto.type.URLMethods;
// import com.akto.dto.type.URLTemplate;
// import com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError;
// import com.akto.runtime.RuntimeUtil;
// import com.akto.threat.detection.cache.AccountConfig;
// import com.akto.threat.detection.cache.AccountConfigurationCache;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.params.ParameterizedTest;
// import org.junit.jupiter.params.provider.ValueSource;

// import java.util.*;

// import static org.assertj.core.api.Assertions.assertThat;

// /**
//  * Test suite for handleSchemaConformFilter method in MaliciousTrafficDetectorTask.
//  * Tests URL validation against discovered traffic patterns (static URLs and templates).
//  */
// class HandleSchemaConformFilterTest {

//     @BeforeEach
//     void setUp() {
//         // Clear cache before each test to ensure test isolation
//         AccountConfigurationCache.getInstance().clear();
//     }

//     // ==================== Helper Methods ====================

//     private void setupTestConfig(Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods,
//                                  Map<Integer, List<URLTemplate>> templates) {
//         AccountConfig testConfig = new AccountConfig(
//             123,  // accountId
//             false,  // isRedacted
//             Collections.emptyList(),  // apiCollections
//             Collections.emptyList(),  // apiInfos
//             templates,
//             apiInfoUrlToMethods
//         );
//         AccountConfigurationCache.getInstance().setConfigForTesting(testConfig);
//     }

//     private HttpResponseParams createResponseParam(int statusCode, String url, String method) {
//         HttpResponseParams responseParam = new HttpResponseParams();
//         responseParam.setStatusCode(statusCode);
//         HttpRequestParams requestParams = new HttpRequestParams();
//         requestParams.setUrl(url);
//         requestParams.setMethod(method);
//         responseParam.setRequestParams(requestParams);
//         return responseParam;
//     }

//     private Map<String, Set<URLMethods.Method>> createApiUrlMap(String url, URLMethods.Method... methods) {
//         Set<URLMethods.Method> methodSet = new HashSet<>();
//         Collections.addAll(methodSet, methods);
//         Map<String, Set<URLMethods.Method>> map = new HashMap<>();
//         map.put(url, methodSet);
//         return map;
//     }

//     private Map<Integer, List<URLTemplate>> createTemplateMap(int collectionId, URLTemplate... templates) {
//         List<URLTemplate> templateList = new ArrayList<>();
//         Collections.addAll(templateList, templates);
//         Map<Integer, List<URLTemplate>> map = new HashMap<>();
//         map.put(collectionId, templateList);
//         return map;
//     }

//     // ==================== Category 1: Early Returns & Preconditions ====================

//     @Test
//     void testNon2xxStatusCode_returnsErrorsUnchanged() {
//         // Given
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET), Collections.emptyMap());
//         HttpResponseParams responseParam = createResponseParam(404, "/api/users", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//         assertThat(result).isSameAs(errors);
//     }

//     @ParameterizedTest
//     @ValueSource(ints = {199, 300, 404, 500})
//     void testVariousNon2xxStatusCodes_skipValidation(int statusCode) {
//         // Given
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET), Collections.emptyMap());
//         HttpResponseParams responseParam = createResponseParam(statusCode, "/api/users", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testEmptyCacheData_returnsErrorsUnchanged() {
//         // Given
//         setupTestConfig(Collections.emptyMap(), Collections.emptyMap());
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }

//     // ==================== Category 2: Static URL Matching ====================

//     @Test
//     void testStaticUrl_exactMatch_returnsNoErrors() {
//         // Given
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET, URLMethods.Method.POST), Collections.emptyMap());
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testStaticUrl_methodNotFound_returnsMethodError() {
//         // Given
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET, URLMethods.Method.POST), Collections.emptyMap());
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users", "DELETE");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users", URLMethods.Method.DELETE);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).hasSize(1);
//         SchemaConformanceError error = result.get(0);
//         assertThat(error.getSchemaPath()).isEqualTo("#/paths/api/users");
//         assertThat(error.getInstancePath()).isEqualTo("DELETE");
//         assertThat(error.getAttribute()).isEqualTo("method");
//         assertThat(error.getMessage()).contains("Method DELETE not available for path /api/users");
//     }

//     @Test
//     void testStaticUrl_urlNotFound_returnsUrlError() {
//         // Given
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET), Collections.emptyMap());
//         HttpResponseParams responseParam = createResponseParam(200, "/api/products", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/products", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).hasSize(1);
//         SchemaConformanceError error = result.get(0);
//         assertThat(error.getSchemaPath()).isEqualTo("#/paths");
//         assertThat(error.getInstancePath()).contains("/api/products");
//         assertThat(error.getAttribute()).isEqualTo("url");
//         assertThat(error.getMessage()).contains("not found in discovered traffic");
//     }

//     @Test
//     void testStaticUrl_differentCollection_treatedAsDifferentApi() {
//         // Given - API exists in collection 456 but not in 123
//         setupTestConfig(createApiUrlMap("456:/api/users", URLMethods.Method.GET), Collections.emptyMap());
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).hasSize(1);
//         assertThat(result.get(0).getMessage()).contains("not found in discovered traffic");
//     }

//     // ==================== Category 3: Template URL Matching ====================

//     @Test
//     void testTemplateUrl_fullMatch_returnsNoErrors() {
//         // Given - template pattern in BOTH apiInfoUrlToMethods and apiCollectionUrlTemplates
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/users/INTEGER", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));

//         // Actual static URL from incoming request
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users/123", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users/123", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testTemplateUrl_urlMatchMethodMismatch_returnsMethodError() {
//         // Given - template exists for POST but request is GET
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER", URLMethods.Method.POST);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/users/INTEGER", URLMethods.Method.POST);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));

//         // Actual static URL from incoming request
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users/456", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users/456", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).hasSize(1);
//         SchemaConformanceError error = result.get(0);
//         assertThat(error.getAttribute()).isEqualTo("method");
//         assertThat(error.getMessage()).contains("Method GET not available");
//     }

//     @Test
//     void testTemplateUrl_noMatch_returnsUrlError() {
//         // Given - template for different URL pattern
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/users/INTEGER", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));

//         // Actual static URL from incoming request - different pattern
//         HttpResponseParams responseParam = createResponseParam(200, "/api/products/123", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/products/123", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).hasSize(1);
//         assertThat(result.get(0).getAttribute()).isEqualTo("url");
//         assertThat(result.get(0).getMessage()).contains("not found in discovered traffic");
//     }

//     @Test
//     void testTemplateUrl_multipleTemplates_findsCorrectMatch() {
//         // Given - multiple templates in same collection
//         URLTemplate template1 = RuntimeUtil.createUrlTemplate("/api/users/INTEGER", URLMethods.Method.GET);
//         URLTemplate template2 = RuntimeUtil.createUrlTemplate("/api/products/INTEGER", URLMethods.Method.GET);
//         URLTemplate template3 = RuntimeUtil.createUrlTemplate("/api/orders/INTEGER/items/INTEGER", URLMethods.Method.POST);

//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = new HashMap<>();
//         apiInfoUrlToMethods.put("123:/api/users/INTEGER", Collections.singleton(URLMethods.Method.GET));
//         apiInfoUrlToMethods.put("123:/api/products/INTEGER", Collections.singleton(URLMethods.Method.GET));
//         apiInfoUrlToMethods.put("123:/api/orders/INTEGER/items/INTEGER", Collections.singleton(URLMethods.Method.POST));

//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template1, template2, template3));

//         // Actual static URL from incoming request
//         HttpResponseParams responseParam = createResponseParam(200, "/api/products/789", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/products/789", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testTemplateUrl_emptyCollection_returnsUrlError() {
//         // Given - no templates for collection 123, only for 456
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/other/INTEGER", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("456:/api/other/INTEGER", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(456, template));

//         // Actual static URL from incoming request for collection 123
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users/123", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users/123", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).hasSize(1);
//         assertThat(result.get(0).getAttribute()).isEqualTo("url");
//     }

//     // ==================== Category 4: Template Parameter Type Validation ====================

//     @Test
//     void testTemplateParameter_integerType_validValue() {
//         // Given
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/users/INTEGER", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users/12345", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users/12345", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testTemplateParameter_integerType_invalidValue() {
//         // Given
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/users/INTEGER", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users/abc", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users/abc", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then - should report URL not found since template doesn't match
//         assertThat(result).hasSize(1);
//         assertThat(result.get(0).getAttribute()).isEqualTo("url");
//     }

//     @Test
//     void testTemplateParameter_floatType_validValue() {
//         // Given
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/prices/FLOAT", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/prices/FLOAT", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));
//         HttpResponseParams responseParam = createResponseParam(200, "/api/prices/19.99", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/prices/19.99", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }


//     @Test
//     void testTemplateParameter_objectIdType_validValue() {
//         // Given
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/items/OBJECT_ID", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/items/OBJECT_ID", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));
//         HttpResponseParams responseParam = createResponseParam(200, "/api/items/507f1f77bcf86cd799439011", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/items/507f1f77bcf86cd799439011", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testTemplateParameter_stringType_matchesAnyValue() {
//         // Given
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/categories/STRING", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/categories/STRING", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));
//         HttpResponseParams responseParam = createResponseParam(200, "/api/categories/electronics", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/categories/electronics", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testTemplateParameter_multipleParameters_allValid() {
//         // Given
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER/posts/INTEGER", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/users/INTEGER/posts/INTEGER", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users/42/posts/999", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users/42/posts/999", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testTemplateParameter_multipleParameters_oneInvalid() {
//         // Given
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER/posts/INTEGER", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/users/INTEGER/posts/INTEGER", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users/abc/posts/999", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users/abc/posts/999", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then - should fail to match due to type mismatch
//         assertThat(result).hasSize(1);
//         assertThat(result.get(0).getAttribute()).isEqualTo("url");
//     }

//     @Test
//     void testTemplateParameter_mixedTypes_correctMatching() {
//         // Given - template with INTEGER, STRING parameters
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER/profile/STRING/active", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/users/INTEGER/profile/STRING/active", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users/123/profile/public/active", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users/123/profile/public/active", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testTemplateParameter_differentSegmentCount_noMatch() {
//         // Given
//         URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER", URLMethods.Method.GET);
//         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods = createApiUrlMap("123:/api/users/INTEGER", URLMethods.Method.GET);
//         setupTestConfig(apiInfoUrlToMethods, createTemplateMap(123, template));
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users/123/posts", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users/123/posts", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then
//         assertThat(result).hasSize(1);
//         assertThat(result.get(0).getAttribute()).isEqualTo("url");
//     }

//     // ==================== Category 5: URL Normalization ====================

//     @Test
//     void testUrlNormalization_queryParameters_removedBeforeMatching() {
//         // Given - apiInfoUrlToMethods contains normalized URL
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET), Collections.emptyMap());

//         // Actual request URL with query parameters (non-normalized)
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users?page=1&limit=10", "GET");
//         // ApiInfoKey also has the non-normalized URL (same as responseParam)
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users?page=1&limit=10", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then - should match after normalization removes query params
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testUrlNormalization_fragments_removedBeforeMatching() {
//         // Given - apiInfoUrlToMethods contains normalized URL
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET), Collections.emptyMap());

//         // Actual request URL with fragment (non-normalized)
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users#section1", "GET");
//         // ApiInfoKey also has the non-normalized URL (same as responseParam)
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users#section1", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then - should match after normalization removes fragment
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testUrlNormalization_trailingSlash_normalizedBeforeMatching() {
//         // Given - apiInfoUrlToMethods contains normalized URL (no trailing slash)
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET), Collections.emptyMap());

//         // Actual request URL with trailing slash (non-normalized)
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users/", "GET");
//         // ApiInfoKey also has the non-normalized URL (same as responseParam)
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users/", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then - should match after normalization removes trailing slash
//         assertThat(result).isEmpty();
//     }

//     @Test
//     void testUrlNormalization_combinedQueryAndFragment_bothRemoved() {
//         // Given - apiInfoUrlToMethods contains normalized URL
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET), Collections.emptyMap());

//         // Actual request URL with both query params and fragment (non-normalized)
//         HttpResponseParams responseParam = createResponseParam(200, "/api/users?page=1#top", "GET");
//         // ApiInfoKey also has the non-normalized URL (same as responseParam)
//         ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(123, "/api/users?page=1#top", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors = new ArrayList<>();

//         // When
//         List<SchemaConformanceError> result = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam, apiInfoKey, errors);

//         // Then - should match after normalization removes both query params and fragment
//         assertThat(result).isEmpty();
//     }

//     // ==================== Category 6: RequestValidator Behavior ====================

//     @Test
//     void testRequestValidator_clearsPreviousErrors() {
//         // Given - first request with error
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET), Collections.emptyMap());
//         HttpResponseParams responseParam1 = createResponseParam(200, "/api/invalid", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey1 = new ApiInfo.ApiInfoKey(123, "/api/invalid", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors1 = new ArrayList<>();

//         // When - first request generates error
//         List<SchemaConformanceError> result1 = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam1, apiInfoKey1, errors1);

//         // Then - first request has error
//         assertThat(result1).hasSize(1);

//         // Given - second request with valid data
//         setupTestConfig(createApiUrlMap("123:/api/users", URLMethods.Method.GET), Collections.emptyMap());
//         HttpResponseParams responseParam2 = createResponseParam(200, "/api/users", "GET");
//         ApiInfo.ApiInfoKey apiInfoKey2 = new ApiInfo.ApiInfoKey(123, "/api/users", URLMethods.Method.GET);
//         List<SchemaConformanceError> errors2 = new ArrayList<>();

//         // When - second request is valid
//         List<SchemaConformanceError> result2 = MaliciousTrafficDetectorTask.handleSchemaConformFilter(responseParam2, apiInfoKey2, errors2);

//         // Then - second request should have no errors (RequestValidator.addError clears previous errors)
//         assertThat(result2).isEmpty();
//     }
// }
