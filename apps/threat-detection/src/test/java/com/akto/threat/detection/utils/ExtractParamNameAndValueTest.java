package com.akto.threat.detection.utils;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.runtime.RuntimeUtil;
import com.akto.threat.detection.cache.AccountConfig;
import com.akto.threat.detection.cache.AccountConfigurationCache;
import com.akto.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractParamNameAndValueTest {

    private ThreatDetector threatDetector;

    @BeforeEach
    void setUp() throws Exception {
        AccountConfigurationCache.getInstance().clear();
        threatDetector = new ThreatDetector();
    }

    // ==================== Helper Methods ====================

    private void setupTestConfig(Map<Integer, List<URLTemplate>> templates) {
        AccountConfig testConfig = new AccountConfig(
            123,
            false,
            Collections.emptyList(),
            Collections.emptyList(),
            templates,
            Collections.emptyMap()
        );
        AccountConfigurationCache.getInstance().setConfigForTesting(testConfig);
    }

    private HttpResponseParams createResponseParam(String url, int apiCollectionId) {
        HttpResponseParams responseParam = new HttpResponseParams();
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setUrl(url);
        requestParams.setApiCollectionId(apiCollectionId);
        responseParam.setRequestParams(requestParams);
        return responseParam;
    }

    private Map<Integer, List<URLTemplate>> createTemplateMap(int collectionId, URLTemplate... templates) {
        List<URLTemplate> templateList = new ArrayList<>();
        Collections.addAll(templateList, templates);
        Map<Integer, List<URLTemplate>> map = new HashMap<>();
        map.put(collectionId, templateList);
        return map;
    }

    // ==================== Basic Param Extraction ====================

    @Test
    void testBasicExample_usersIntegerOrgString() {
        // Given: /users/INTEGER/org/STRING template
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER/org/STRING", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/users/123/org/AKTO", 123);

        // When
        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFirst()).isEqualTo("users");
        assertThat(result.get(0).getSecond()).isEqualTo("123");
        assertThat(result.get(1).getFirst()).isEqualTo("org");
        assertThat(result.get(1).getSecond()).isEqualTo("AKTO");
    }

    @Test
    void testSingleParam_usersInteger() {
        // Given: /users/INTEGER template
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/users/456", 123);

        // When
        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirst()).isEqualTo("users");
        assertThat(result.get(0).getSecond()).isEqualTo("456");
    }

    @Test
    void testMultipleParams_nestedPath() {
        // Given: /api/users/INTEGER/posts/INTEGER/comments/INTEGER
        URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER/posts/INTEGER/comments/INTEGER", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/api/users/100/posts/200/comments/300", 123);

        // When
        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getFirst()).isEqualTo("users");
        assertThat(result.get(0).getSecond()).isEqualTo("100");
        assertThat(result.get(1).getFirst()).isEqualTo("posts");
        assertThat(result.get(1).getSecond()).isEqualTo("200");
        assertThat(result.get(2).getFirst()).isEqualTo("comments");
        assertThat(result.get(2).getSecond()).isEqualTo("300");
    }

    // ==================== URL Normalization ====================

    @Test
    void testUrlWithQueryParams_removedBeforeMatching() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/users/789?page=1&limit=10", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirst()).isEqualTo("users");
        assertThat(result.get(0).getSecond()).isEqualTo("789");
    }

    @Test
    void testUrlWithFragment_removedBeforeMatching() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/users/789#section1", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSecond()).isEqualTo("789");
    }

    @Test
    void testUrlWithTrailingSlash_normalized() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/users/789/", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSecond()).isEqualTo("789");
    }

    @Test
    void testUrlWithLeadingSlash_normalized() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/users/789", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSecond()).isEqualTo("789");
    }

    // ==================== Edge Cases ====================

    @Test
    void testNoMatchingTemplate_returnsEmptyList() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/products/INTEGER", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/users/123", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).isEmpty();
    }

    @Test
    void testNoTemplatesForCollection_returnsEmptyList() {
        setupTestConfig(Collections.emptyMap());

        HttpResponseParams responseParam = createResponseParam("/users/123", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).isEmpty();
    }

    @Test
    void testDifferentCollection_returnsEmptyList() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(456, template));  // Template in collection 456

        HttpResponseParams responseParam = createResponseParam("/users/123", 123);  // Request for collection 123

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).isEmpty();
    }

    @Test
    void testStaticUrlNoParams_returnsEmptyList() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/api/health", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/api/health", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).isEmpty();
    }

    // ==================== Different Parameter Types ====================

    @Test
    void testStringParam() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/categories/STRING", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/categories/electronics", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirst()).isEqualTo("categories");
        assertThat(result.get(0).getSecond()).isEqualTo("electronics");
    }

    @Test
    void testMixedParamTypes() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER/profile/STRING", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/users/42/profile/public", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFirst()).isEqualTo("users");
        assertThat(result.get(0).getSecond()).isEqualTo("42");
        assertThat(result.get(1).getFirst()).isEqualTo("profile");
        assertThat(result.get(1).getSecond()).isEqualTo("public");
    }

    // ==================== Fallback Param Name ====================

    @Test
    void testParamAtStart_usesTypeName() {
        // Template where first token is parameterized: /INTEGER/users
        URLTemplate template = RuntimeUtil.createUrlTemplate("/INTEGER/users", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/999/users", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirst()).isEqualTo("INTEGER");  // Falls back to type name
        assertThat(result.get(0).getSecond()).isEqualTo("999");
    }

    @Test
    void testConsecutiveParams_secondParamUsesTypeName() {
        // Template: /api/INTEGER/INTEGER (consecutive params)
        // When previous token is also null, should fallback to type name
        URLTemplate template = RuntimeUtil.createUrlTemplate("/api/INTEGER/INTEGER", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/api/123/456", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFirst()).isEqualTo("api");       // Previous static segment
        assertThat(result.get(0).getSecond()).isEqualTo("123");
        assertThat(result.get(1).getFirst()).isEqualTo("INTEGER");   // Fallback to type name
        assertThat(result.get(1).getSecond()).isEqualTo("456");
    }

    @Test
    void testThreeConsecutiveParams() {
        // Template: /INTEGER/STRING/INTEGER (all params)
        URLTemplate template = RuntimeUtil.createUrlTemplate("/INTEGER/STRING/INTEGER", URLMethods.Method.GET);
        setupTestConfig(createTemplateMap(123, template));

        HttpResponseParams responseParam = createResponseParam("/111/abc/333", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getFirst()).isEqualTo("INTEGER");  // No previous, fallback to type
        assertThat(result.get(1).getFirst()).isEqualTo("STRING");   // Previous is null, fallback to type
        assertThat(result.get(2).getFirst()).isEqualTo("INTEGER");  // Previous is null, fallback to type
    }

    // ==================== Multiple Templates ====================

    @Test
    void testMultipleTemplates_matchesCorrectOne() {
        URLTemplate template1 = RuntimeUtil.createUrlTemplate("/users/INTEGER", URLMethods.Method.GET);
        URLTemplate template2 = RuntimeUtil.createUrlTemplate("/products/INTEGER", URLMethods.Method.GET);
        URLTemplate template3 = RuntimeUtil.createUrlTemplate("/orders/INTEGER/items/INTEGER", URLMethods.Method.GET);

        Map<Integer, List<URLTemplate>> templates = new HashMap<>();
        templates.put(123, Arrays.asList(template1, template2, template3));
        setupTestConfig(templates);

        HttpResponseParams responseParam = createResponseParam("/orders/500/items/10", 123);

        List<Pair<String, String>> result = threatDetector.extractParamNameAndValue(responseParam);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFirst()).isEqualTo("orders");
        assertThat(result.get(0).getSecond()).isEqualTo("500");
        assertThat(result.get(1).getFirst()).isEqualTo("items");
        assertThat(result.get(1).getSecond()).isEqualTo("10");
    }
}
