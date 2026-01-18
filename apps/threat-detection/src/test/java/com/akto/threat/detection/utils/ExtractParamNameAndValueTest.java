package com.akto.threat.detection.utils;

import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;
import com.akto.runtime.RuntimeUtil;
import com.akto.util.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ExtractParamNameAndValueTest {

    private ThreatDetector threatDetector;

    @BeforeEach
    void setUp() throws Exception {
        threatDetector = new ThreatDetector();
    }

    // ==================== Basic Param Extraction ====================

    @Test
    void testBasicExample_usersIntegerOrgString() {
        // Given: /users/INTEGER/org/STRING template
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER/org/STRING", URLMethods.Method.GET);
        String url = "/users/123/org/AKTO";

        // When
        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

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
        String url = "/users/456";

        // When
        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirst()).isEqualTo("users");
        assertThat(result.get(0).getSecond()).isEqualTo("456");
    }

    @Test
    void testMultipleParams_nestedPath() {
        // Given: /api/users/INTEGER/posts/INTEGER/comments/INTEGER
        URLTemplate template = RuntimeUtil.createUrlTemplate("/api/users/INTEGER/posts/INTEGER/comments/INTEGER", URLMethods.Method.GET);
        String url = "/api/users/100/posts/200/comments/300";

        // When
        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getFirst()).isEqualTo("users");
        assertThat(result.get(0).getSecond()).isEqualTo("100");
        assertThat(result.get(1).getFirst()).isEqualTo("posts");
        assertThat(result.get(1).getSecond()).isEqualTo("200");
        assertThat(result.get(2).getFirst()).isEqualTo("comments");
        assertThat(result.get(2).getSecond()).isEqualTo("300");
    }

    // ==================== URL Variations ====================

    @Test
    void testUrlWithQueryParams_queryParamsIgnored() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER", URLMethods.Method.GET);
        // Note: query params should be stripped before calling this method in production
        String url = "/users/789";

        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirst()).isEqualTo("users");
        assertThat(result.get(0).getSecond()).isEqualTo("789");
    }

    @Test
    void testUrlWithTrailingSlash() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER", URLMethods.Method.GET);
        String url = "/users/789/";

        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSecond()).isEqualTo("789");
    }

    // ==================== Edge Cases ====================

    @Test
    void testStaticUrlNoParams_returnsEmptyList() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/api/health", URLMethods.Method.GET);
        String url = "/api/health";

        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

        assertThat(result).isEmpty();
    }

    // ==================== Different Parameter Types ====================

    @Test
    void testStringParam() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/categories/STRING", URLMethods.Method.GET);
        String url = "/categories/electronics";

        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirst()).isEqualTo("categories");
        assertThat(result.get(0).getSecond()).isEqualTo("electronics");
    }

    @Test
    void testMixedParamTypes() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/users/INTEGER/profile/STRING", URLMethods.Method.GET);
        String url = "/users/42/profile/public";

        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

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
        String url = "/999/users";

        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirst()).isEqualTo("INTEGER");  // Falls back to type name
        assertThat(result.get(0).getSecond()).isEqualTo("999");
    }

    @Test
    void testConsecutiveParams_secondParamUsesTypeName() {
        // Template: /api/INTEGER/INTEGER (consecutive params)
        URLTemplate template = RuntimeUtil.createUrlTemplate("/api/INTEGER/INTEGER", URLMethods.Method.GET);
        String url = "/api/123/456";

        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

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
        String url = "/111/abc/333";

        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getFirst()).isEqualTo("INTEGER");  // No previous, fallback to type
        assertThat(result.get(1).getFirst()).isEqualTo("STRING");   // Previous is null, fallback to type
        assertThat(result.get(2).getFirst()).isEqualTo("INTEGER");  // Previous is null, fallback to type
    }

    // ==================== Multiple Templates ====================

    @Test
    void testComplexNestedPath() {
        URLTemplate template = RuntimeUtil.createUrlTemplate("/orders/INTEGER/items/INTEGER", URLMethods.Method.GET);
        String url = "/orders/500/items/10";

        List<Pair<String, String>> result = threatDetector.getUrlParamNamesAndValues(url, template);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFirst()).isEqualTo("orders");
        assertThat(result.get(0).getSecond()).isEqualTo("500");
        assertThat(result.get(1).getFirst()).isEqualTo("items");
        assertThat(result.get(1).getSecond()).isEqualTo("10");
    }
}
