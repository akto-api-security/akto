package com.akto.utils;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

public class TestUtils {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JsonFactory factory = mapper.getFactory();

    @Test
    public void testExtractRequestPayload_raw() throws IOException {
        try(JsonParser jp = factory.createParser("{\"mode\":\"raw\",\"raw\":\"{\\n    \\\"username\\\": \\\"bhavik@akto.io\\\",\\n    \\\"password\\\": \\\"Test123\\\"\\n}\",\"options\":{\"raw\":{\"language\":\"json\"}}}")){
            JsonNode actualObj = jp.readValueAsTree();
            String rawBody = Utils.extractRequestPayload(actualObj);
            assertTrue(rawBody.length() > 0);
            assertTrue(rawBody.contains("username"));
            assertTrue(rawBody.contains("password"));
        }
    }

    @Test
    public void testExtractRequestPayload_formdata() throws IOException {
        try(JsonParser jp = factory.createParser("{\"mode\":\"formdata\",\"formdata\":[{\"key\":\"file\",\"type\":\"file\",\"src\":\"/har_collection/test.har\"},{\"key\":\"file_length\",\"value\":\"test\",\"type\":\"text\"},{\"key\":\"fileName\",\"value\":\"test.har\",\"type\":\"text\",\"disabled\":true}]}")){
            JsonNode actualObj = jp.readValueAsTree();
            String rawBody = Utils.extractRequestPayload(actualObj);
            assertTrue(rawBody.length() > 0);
            assertTrue(rawBody.contains("file"));
            assertTrue(rawBody.contains("fileName"));
        }
    }

    @Test
    public void testExtractRequestPayload_urlencoded() throws IOException {
        try(JsonParser jp = factory.createParser("{\"mode\":\"urlencoded\",\"urlencoded\":[{\"key\":\"test\",\"value\":\"val\",\"type\":\"text\"}]}")){
            JsonNode actualObj = jp.readValueAsTree();
            String rawBody = Utils.extractRequestPayload(actualObj);
            assertTrue(rawBody.length() > 0);
            assertTrue(rawBody.contains("test"));
            assertTrue(rawBody.contains("val"));
        }
    }

    @Test
    public void testExtractRequestPayload_graphql() throws IOException {
        try(JsonParser jp = factory.createParser("{\"mode\":\"graphql\",\"graphql\":{\"query\":\"query ($code: ID!){\\n    country(code: $code){\\n        name\\n        native\\n        capital\\n    }\\n}\",\"variables\":\"{\\n    \\\"code\\\": \\\"IN\\\"\\n}\"}}")){
            JsonNode actualObj = jp.readValueAsTree();
            String rawBody = Utils.extractRequestPayload(actualObj);
            assertTrue(rawBody.length() > 0);
            assertTrue(rawBody.contains("country"));
            assertTrue(rawBody.contains("native"));
        }
    }

    @Test
    public void testExtractRequestPayload_binary() throws IOException {
        try(JsonParser jp = factory.createParser("{\"mode\":\"file\",\"file\":{\"src\":\"/Desktop/test.har\"}}")){
            JsonNode actualObj = jp.readValueAsTree();
            String rawBody = Utils.extractRequestPayload(actualObj);
            assertTrue(rawBody.length() > 0);
            assertTrue(rawBody.contains("har"));
        }
    }
}
