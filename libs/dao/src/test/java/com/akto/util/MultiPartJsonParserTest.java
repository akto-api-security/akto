package com.akto.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Round-trip tests for multipart/form-data ↔ JSON conversion.
 * Tests verify that the parser correctly handles the multipart format and creates valid JSON.
 */
public class MultiPartJsonParserTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode parseJson(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    /**
     * Test 1: Simple text file upload - Verify valid JSON is created
     */
    @Test
    public void testMultipartToJson_PlainTextFile() throws Exception {
        String boundary = "----WebKitFormBoundaryMcmJ2v0quNuCBcFi";
        String contentTypeHeader = "multipart/form-data; boundary=" + boundary;

        String rawBody =
                "------WebKitFormBoundaryMcmJ2v0quNuCBcFi\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"test.txt\"\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "Hello World\r\n" +
                "------WebKitFormBoundaryMcmJ2v0quNuCBcFi--";

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Collections.singletonList(contentTypeHeader));

        // Parse multipart to JSON
        String json = HttpRequestResponseUtils.rawToJsonString(rawBody, headers);
        
        // Verify it's valid JSON
        assertNotNull("JSON should not be null", json);
        JsonNode root = parseJson(json);
        
        // Verify structure
        assertTrue("Should have 'file' field", root.has("file"));
        JsonNode fileNode = root.get("file");
        assertTrue("File should be an object", fileNode.isObject());
        
        // Verify file metadata
        assertEquals("test.txt", fileNode.get("filename").asText());
        assertEquals("text/plain", fileNode.get("contentType").asText());
        assertTrue("Should have content field", fileNode.has("content"));
        
        // Verify content is base64 encoded
        String base64Content = fileNode.get("content").asText();
        assertNotNull("Base64 content should not be null", base64Content);
        assertTrue("Base64 content should not be empty", base64Content.length() > 0);
        
        // Decode and verify content
        byte[] decoded = Base64.getDecoder().decode(base64Content);
        String content = new String(decoded);
        assertTrue("Content should contain 'Hello World'", content.contains("Hello World"));
    }

    /**
     * Test 2: Text file with multiple lines - Verify valid JSON is created
     */
    @Test
    public void testMultipartToJson_MultiLineTextFile() throws Exception {
        String boundary = "----------------------------526877191413728742038557";
        String contentTypeHeader = "multipart/form-data; boundary=" + boundary;

        String rawBody =
                "----------------------------526877191413728742038557\r\n" +
                "Content-Disposition: form-data; name=\"document\"; filename=\"notes.txt\"\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "Line 1\n" +
                "Line 2\n" +
                "Line 3\r\n" +
                "----------------------------526877191413728742038557--";

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Collections.singletonList(contentTypeHeader));

        // Parse multipart to JSON
        String json = HttpRequestResponseUtils.rawToJsonString(rawBody, headers);
        
        // Verify it's valid JSON
        assertNotNull("JSON should not be null", json);
        JsonNode root = parseJson(json);
        
        // Verify structure
        assertTrue("Should have 'document' field", root.has("document"));
        JsonNode fileNode = root.get("document");
        
        assertEquals("notes.txt", fileNode.get("filename").asText());
        assertEquals("text/plain", fileNode.get("contentType").asText());
        
        // Decode and verify content
        String base64Content = fileNode.get("content").asText();
        byte[] decoded = Base64.getDecoder().decode(base64Content);
        String content = new String(decoded);
        assertTrue("Content should contain Line 1", content.contains("Line 1"));
        assertTrue("Content should contain Line 2", content.contains("Line 2"));
        assertTrue("Content should contain Line 3", content.contains("Line 3"));
    }

    /**
     * Test 3: Mixed text fields and file - Verify valid JSON is created
     */
    @Test
    public void testMultipartToJson_MixedFieldsAndFile() throws Exception {
        String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
        String contentTypeHeader = "multipart/form-data; boundary=" + boundary;

        String rawBody =
                "------WebKitFormBoundary7MA4YWxkTrZu0gW\r\n" +
                "Content-Disposition: form-data; name=\"userId\"\r\n" +
                "\r\n" +
                "user123\r\n" +
                "------WebKitFormBoundary7MA4YWxkTrZu0gW\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"data.txt\"\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "Some file content\r\n" +
                "------WebKitFormBoundary7MA4YWxkTrZu0gW--";

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Collections.singletonList(contentTypeHeader));

        // Parse multipart to JSON
        String json = HttpRequestResponseUtils.rawToJsonString(rawBody, headers);
        
        // Verify it's valid JSON
        assertNotNull("JSON should not be null", json);
        JsonNode root = parseJson(json);
        
        // Verify text field
        assertTrue("Should have 'userId' field", root.has("userId"));
        assertEquals("user123", root.get("userId").asText());
        
        // Verify file field
        assertTrue("Should have 'file' field", root.has("file"));
        JsonNode fileNode = root.get("file");
        assertEquals("data.txt", fileNode.get("filename").asText());
        assertEquals("text/plain", fileNode.get("contentType").asText());
        
        // Decode and verify content
        String base64Content = fileNode.get("content").asText();
        byte[] decoded = Base64.getDecoder().decode(base64Content);
        String content = new String(decoded);
        assertTrue("Content should contain 'Some file content'", content.contains("Some file content"));
    }

    /**
     * Test 4: Round-trip with text file - Verify JSON → Multipart → JSON preserves data
     */
    @Test
    public void testRoundTrip_TextFile() throws Exception {
        String boundary = "----WebKitFormBoundaryABC123";
        String contentTypeHeader = "multipart/form-data; boundary=" + boundary;

        String rawBody =
                "------WebKitFormBoundaryABC123\r\n" +
                "Content-Disposition: form-data; name=\"attachment\"; filename=\"readme.txt\"\r\n" +
                "Content-Type: text/plain\r\n" +
                "\r\n" +
                "This is a test file.\nIt has multiple lines.\r\n" +
                "------WebKitFormBoundaryABC123--";

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("content-type", Collections.singletonList(contentTypeHeader));

        // Step 1: Parse multipart to JSON
        String json = HttpRequestResponseUtils.rawToJsonString(rawBody, headers);
        assertNotNull("JSON should not be null", json);
        
        JsonNode root = parseJson(json);
        assertTrue("Should have 'attachment' field", root.has("attachment"));
        
        JsonNode fileNode = root.get("attachment");
        assertEquals("readme.txt", fileNode.get("filename").asText());
        
        // Get original content
        String originalBase64 = fileNode.get("content").asText();
        
        // Step 2: Convert JSON back to multipart
        String reconstructed = HttpRequestResponseUtils.jsonToMultipart(json, boundary);
        assertNotNull("Reconstructed multipart should not be null", reconstructed);
        assertTrue("Should contain boundary", reconstructed.contains(boundary));
        assertTrue("Should contain filename", reconstructed.contains("readme.txt"));
        
        // Step 3: Parse reconstructed multipart back to JSON
        String json2 = HttpRequestResponseUtils.rawToJsonString(reconstructed, headers);
        JsonNode root2 = parseJson(json2);
        
        assertTrue("Round-trip should preserve attachment field", root2.has("attachment"));
        JsonNode fileNode2 = root2.get("attachment");
        
        assertEquals("Round-trip should preserve filename", 
                     "readme.txt", 
                     fileNode2.get("filename").asText());
        assertEquals("Round-trip should preserve content type", 
                     "text/plain", 
                     fileNode2.get("contentType").asText());
        
        // Verify content is preserved
        String roundTripBase64 = fileNode2.get("content").asText();
        assertEquals("Round-trip should preserve content", originalBase64, roundTripBase64);
        
        // Decode and verify actual content
        byte[] decoded = Base64.getDecoder().decode(roundTripBase64);
        String content = new String(decoded);
        assertTrue("Content should contain 'This is a test file'", content.contains("This is a test file"));
        assertTrue("Content should contain 'multiple lines'", content.contains("multiple lines"));
    }
}
