package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DigestAuthParamTests {

    /**
     * Test DigestAuthParam constructor with all parameters
     */
    @Test
    public void testDigestAuthParamConstructorWithAlgorithm() {
        DigestAuthParam digestAuthParam = new DigestAuthParam("testuser", "testpass", "http://api.example.com/auth", "POST", "SHA-256");

        assertEquals("Username should be set", "testuser", digestAuthParam.getUsername());
        assertEquals("Password should be set", "testpass", digestAuthParam.getPassword());
        assertEquals("Target URL should be set", "http://api.example.com/auth", digestAuthParam.getTargetUrl());
        assertEquals("Method should be set", "POST", digestAuthParam.getMethod());
        assertEquals("Algorithm should be set", "SHA-256", digestAuthParam.getAlgorithm());
    }

    /**
     * Test DigestAuthParam constructor with default algorithm
     */
    @Test
    public void testDigestAuthParamConstructorDefaultAlgorithm() {
        DigestAuthParam digestAuthParam = new DigestAuthParam("user", "pass", "http://example.com", "GET");

        assertEquals("Username should be set", "user", digestAuthParam.getUsername());
        assertEquals("Password should be set", "pass", digestAuthParam.getPassword());
        assertEquals("Target URL should be set", "http://example.com", digestAuthParam.getTargetUrl());
        assertEquals("Method should be set", "GET", digestAuthParam.getMethod());
        assertEquals("Should default to SHA-256 algorithm", "SHA-256", digestAuthParam.getAlgorithm());
    }

    /**
     * Test DigestAuthParam default constructor
     */
    @Test
    public void testDigestAuthParamDefaultConstructor() {
        DigestAuthParam digestAuthParam = new DigestAuthParam();

        assertEquals("Default method should be GET", "GET", digestAuthParam.getMethod());
        assertNull("Algorithm is null in default constructor", digestAuthParam.getAlgorithm());
        assertEquals("Default key should be Authorization", "Authorization", digestAuthParam.getKey());
        assertEquals("Default where should be HEADER", AuthParam.Location.HEADER, digestAuthParam.getWhere());
        assertEquals("Default showHeader should be true", Boolean.TRUE, digestAuthParam.getShowHeader());
    }

    /**
     * Test DigestAuthParam standard constructor (Location based)
     */
    @Test
    public void testDigestAuthParamStandardConstructor() {
        DigestAuthParam digestAuthParam = new DigestAuthParam(AuthParam.Location.HEADER, "X-Auth-Token", "test_token", true);

        assertEquals("Where should be HEADER", AuthParam.Location.HEADER, digestAuthParam.getWhere());
        assertEquals("Key should be X-Auth-Token", "X-Auth-Token", digestAuthParam.getKey());
        assertEquals("Value should be test_token", "test_token", digestAuthParam.getValue());
        assertEquals("ShowHeader should be true", Boolean.TRUE, digestAuthParam.getShowHeader());
        assertEquals("Default method should be GET", "GET", digestAuthParam.getMethod());
    }

    /**
     * Test removeAuthTokens removes authorization header
     */
    @Test
    public void testRemoveAuthTokens() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("authorization", java.util.Arrays.asList("Bearer token123"));
        headers.put("content-type", java.util.Arrays.asList("application/json"));

        OriginalHttpRequest request = new OriginalHttpRequest("http://example.com/api", "GET", "GET", "", headers, "HTTP/1.1");

        DigestAuthParam digestAuthParam = new DigestAuthParam("user", "pass", "http://example.com", "GET", "SHA-256");

        assertTrue("removeAuthTokens should return true", digestAuthParam.removeAuthTokens(request));
        assertNull("Authorization header should be removed", request.getHeaders().get("authorization"));
        assertNotNull("Other headers should remain", request.getHeaders().get("content-type"));
    }

    /**
     * Test authTokenPresent detects authorization header
     */
    @Test
    public void testAuthTokenPresentWithHeader() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("authorization", java.util.Arrays.asList("Digest username=test"));

        OriginalHttpRequest request = new OriginalHttpRequest("http://example.com/api", "GET", "GET", "", headers, "HTTP/1.1");

        DigestAuthParam digestAuthParam = new DigestAuthParam("user", "pass", "http://example.com", "GET", "SHA-256");

        assertTrue("authTokenPresent should return true when Authorization header exists", digestAuthParam.authTokenPresent(request));
    }

    /**
     * Test authTokenPresent returns false without authorization header
     */
    @Test
    public void testAuthTokenPresentWithoutHeader() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", java.util.Arrays.asList("application/json"));

        OriginalHttpRequest request = new OriginalHttpRequest("http://example.com/api", "GET", "GET", "", headers, "HTTP/1.1");

        DigestAuthParam digestAuthParam = new DigestAuthParam("user", "pass", "http://example.com", "GET", "SHA-256");

        assertFalse("authTokenPresent should return false when Authorization header does not exist", digestAuthParam.authTokenPresent(request));
    }

    /**
     * Test getValue returns the value field
     */
    @Test
    public void testGetValue() {
        DigestAuthParam digestAuthParam = new DigestAuthParam(AuthParam.Location.HEADER, "Authorization", "Bearer token123", true);

        assertEquals("getValue should return the value", "Bearer token123", digestAuthParam.getValue());
    }

    /**
     * Test getKey returns the key field
     */
    @Test
    public void testGetKey() {
        DigestAuthParam digestAuthParam = new DigestAuthParam(AuthParam.Location.HEADER, "X-Custom-Auth", "value", true);

        assertEquals("getKey should return the key", "X-Custom-Auth", digestAuthParam.getKey());
    }

    /**
     * Test getWhere returns the location
     */
    @Test
    public void testGetWhere() {
        DigestAuthParam digestAuthParam = new DigestAuthParam(AuthParam.Location.BODY, "auth_token", "value", false);

        assertEquals("getWhere should return BODY", AuthParam.Location.BODY, digestAuthParam.getWhere());
    }

    /**
     * Test getShowHeader returns the showHeader flag
     */
    @Test
    public void testGetShowHeader() {
        DigestAuthParam digestAuthParam = new DigestAuthParam(AuthParam.Location.HEADER, "Authorization", "value", false);

        assertEquals("getShowHeader should return false", Boolean.FALSE, digestAuthParam.getShowHeader());
    }

    /**
     * Test setValue updates the value field
     */
    @Test
    public void testSetValue() {
        DigestAuthParam digestAuthParam = new DigestAuthParam("user", "pass", "http://example.com", "GET", "SHA-256");

        digestAuthParam.setValue("new_value");

        assertEquals("setValue should update the value", "new_value", digestAuthParam.getValue());
    }

    /**
     * Test toString method
     */
    @Test
    public void testToString() {
        DigestAuthParam digestAuthParam = new DigestAuthParam("testuser", "testpass", "http://api.example.com/endpoint", "POST", "MD5");

        String toString = digestAuthParam.toString();

        assertTrue("toString should contain username", toString.contains("testuser"));
        assertTrue("toString should contain targetUrl", toString.contains("http://api.example.com/endpoint"));
        assertTrue("toString should contain method", toString.contains("POST"));
        assertTrue("toString should contain algorithm", toString.contains("MD5"));
    }

    /**
     * Test field getters and setters
     */
    @Test
    public void testFieldGettersAndSetters() {
        DigestAuthParam digestAuthParam = new DigestAuthParam();

        // Set fields
        digestAuthParam.setUsername("newuser");
        digestAuthParam.setPassword("newpass");
        digestAuthParam.setTargetUrl("http://new.example.com");
        digestAuthParam.setMethod("PUT");
        digestAuthParam.setAlgorithm("MD5");

        // Verify getters
        assertEquals("Username should be updated", "newuser", digestAuthParam.getUsername());
        assertEquals("Password should be updated", "newpass", digestAuthParam.getPassword());
        assertEquals("Target URL should be updated", "http://new.example.com", digestAuthParam.getTargetUrl());
        assertEquals("Method should be updated", "PUT", digestAuthParam.getMethod());
        assertEquals("Algorithm should be updated", "MD5", digestAuthParam.getAlgorithm());
    }

    /**
     * Test constants are defined correctly
     */
    @Test
    public void testConstants() {
        assertEquals("USERNAME_KEY should be 'username'", "username", DigestAuthParam.USERNAME_KEY);
        assertEquals("PASSWORD_KEY should be 'password'", "password", DigestAuthParam.PASSWORD_KEY);
        assertEquals("TARGET_URL_KEY should be 'targetUrl'", "targetUrl", DigestAuthParam.TARGET_URL_KEY);
        assertEquals("METHOD_KEY should be 'method'", "method", DigestAuthParam.METHOD_KEY);
        assertEquals("ALGORITHM_KEY should be 'algorithm'", "algorithm", DigestAuthParam.ALGORITHM_KEY);
    }

    /**
     * Test null handling in constructor
     */
    @Test
    public void testNullHandlingInConstructor() {
        DigestAuthParam digestAuthParam = new DigestAuthParam("user", "pass", "http://example.com", null, null);

        assertEquals("Null method should default to GET", "GET", digestAuthParam.getMethod());
        assertEquals("Null algorithm should default to SHA-256", "SHA-256", digestAuthParam.getAlgorithm());
    }

    /**
     * Test that default constructor values are set
     */
    @Test
    public void testDefaultConstructorInitialization() {
        DigestAuthParam digestAuthParam = new DigestAuthParam();

        assertNotNull("where should not be null", digestAuthParam.getWhere());
        assertNotNull("key should not be null", digestAuthParam.getKey());
        assertNotNull("value should not be null", digestAuthParam.getValue());
        assertNotNull("showHeader should not be null", digestAuthParam.getShowHeader());
        assertNotNull("method should not be null", digestAuthParam.getMethod());
    }
}
