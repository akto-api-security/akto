package com.akto.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class HttpRequestResponseUtilsTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testConvertFormUrlEncodedToJsonWithValidInput() throws Exception {
        String input = "username=john&password=secret123&email=john@example.com";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        assertEquals("john", jsonNode.get("username").asText());
        assertEquals("secret123", jsonNode.get("password").asText());
        assertEquals("john@example.com", jsonNode.get("email").asText());
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithSingleKeyValue() throws Exception {
        String input = "key=value";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        assertEquals("value", jsonNode.get("key").asText());
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithUrlEncodedCharacters() throws Exception {
        String input = "name=John+Doe&message=Hello%20World%21&email=test%40example.com";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        assertEquals("John Doe", jsonNode.get("name").asText());
        assertEquals("Hello World!", jsonNode.get("message").asText());
        assertEquals("test@example.com", jsonNode.get("email").asText());
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithSpecialCharacters() throws Exception {
        String input = "data=%7B%22key%22%3A%22value%22%7D&number=123";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        assertEquals("{\"key\":\"value\"}", jsonNode.get("data").asText());
        assertEquals("123", jsonNode.get("number").asText());
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithNullInput() {
        String input = null;
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertEquals(null, result);
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithEmptyString() {
        String input = "";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertEquals("", result);
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithWhitespaceOnly() {
        String input = "   ";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertEquals("   ", result);
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithNoEqualsSign() {
        String input = "justaplainstring";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        // Should return original string as it's not form-encoded
        assertEquals("justaplainstring", result);
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithJsonInput() {
        String input = "{\"key\":\"value\",\"number\":123}";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        // Should return original string as it's not form-encoded (no = sign)
        assertEquals("{\"key\":\"value\",\"number\":123}", result);
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithInvalidKeyValuePairs() {
        String input = "key1=value1&invalidpair&key2=value2";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        // Should still convert valid pairs
        assertNotNull(result);
        assertTrue(result.contains("key1"));
        assertTrue(result.contains("value1"));
        assertTrue(result.contains("key2"));
        assertTrue(result.contains("value2"));
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithEmptyValues() throws Exception {
        String input = "key1=&key2=value2&key3=";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        // Only key2 should be in the result (others have empty values)
        assertEquals("value2", jsonNode.get("key2").asText());
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithMultipleEquals() throws Exception {
        String input = "key1=value1&base64=data==&key2=value2";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        // Should handle the case where values contain '=' characters
        assertTrue(result.contains("key1"));
        assertTrue(result.contains("key2"));
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithOnlyEquals() {
        String input = "===";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        // Should return original string as no valid key-value pairs
        assertEquals("===", result);
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithComplexData() throws Exception {
        String input = "action=submit&id=12345&tags=tag1%2Ctag2%2Ctag3&active=true";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        assertEquals("submit", jsonNode.get("action").asText());
        assertEquals("12345", jsonNode.get("id").asText());
        assertEquals("tag1,tag2,tag3", jsonNode.get("tags").asText());
        assertEquals("true", jsonNode.get("active").asText());
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithTrailingAmpersand() throws Exception {
        String input = "key1=value1&key2=value2&";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        assertEquals("value1", jsonNode.get("key1").asText());
        assertEquals("value2", jsonNode.get("key2").asText());
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithLeadingAmpersand() throws Exception {
        String input = "&key1=value1&key2=value2";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        assertEquals("value1", jsonNode.get("key1").asText());
        assertEquals("value2", jsonNode.get("key2").asText());
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithUnicodeCharacters() throws Exception {
        String input = "name=%E4%B8%AD%E6%96%87&message=%E3%81%93%E3%82%93%E3%81%AB%E3%81%A1%E3%81%AF";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        assertEquals("中文", jsonNode.get("name").asText());
        assertEquals("こんにちは", jsonNode.get("message").asText());
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithNumericValues() throws Exception {
        String input = "age=30&price=99.99&quantity=100";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        assertEquals("30", jsonNode.get("age").asText());
        assertEquals("99.99", jsonNode.get("price").asText());
        assertEquals("100", jsonNode.get("quantity").asText());
    }

    @Test
    public void testConvertFormUrlEncodedToJsonWithBooleanLikeValues() throws Exception {
        String input = "isActive=true&isDeleted=false&isVisible=1";
        String result = HttpRequestResponseUtils.convertFormUrlEncodedToJson(input);

        assertNotNull(result);
        JsonNode jsonNode = mapper.readTree(result);
        assertEquals("true", jsonNode.get("isActive").asText());
        assertEquals("false", jsonNode.get("isDeleted").asText());
        assertEquals("1", jsonNode.get("isVisible").asText());
    }
}
