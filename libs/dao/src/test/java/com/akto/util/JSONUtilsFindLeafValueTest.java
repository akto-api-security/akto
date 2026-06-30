package com.akto.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class JSONUtilsFindLeafValueTest {

    @Test
    public void topLevelKey() {
        assertEquals("alice@example.com", JSONUtils.extractValueForKey("{\"email\":\"alice@example.com\",\"name\":\"Alice\"}", "email"));
    }

    @Test
    public void nestedKey() {
        assertEquals("bob@example.com", JSONUtils.extractValueForKey("{\"variables\":{\"input\":{\"email\":\"bob@example.com\"}}}", "email"));
    }

    @Test
    public void deeplyNestedKey() {
        assertEquals("found", JSONUtils.extractValueForKey("{\"a\":{\"b\":{\"c\":{\"d\":{\"target\":\"found\"}}}}}", "target"));
    }

    @Test
    public void keyNotPresent() {
        assertNull(JSONUtils.extractValueForKey("{\"variables\":{\"input\":{\"name\":\"Alice\"}}}", "email"));
    }

    @Test
    public void emptyPayload() {
        assertNull(JSONUtils.extractValueForKey("", "email"));
        assertNull(JSONUtils.extractValueForKey(null, "email"));
    }

    @Test
    public void numericValue() {
        assertEquals("42", JSONUtils.extractValueForKey("{\"data\":{\"count\":42}}", "count"));
    }

    @Test
    public void booleanValue() {
        assertEquals("true", JSONUtils.extractValueForKey("{\"active\":true,\"name\":\"x\"}", "active"));
    }

    @Test
    public void valueWithSpaceAfterColon() {
        assertEquals("val", JSONUtils.extractValueForKey("{\"key\": \"val\"}", "key"));
    }

    @Test
    public void graphqlAuthenticatePayload() {
        String payload = "{\"variables\":{\"input\":{\"password\":\"secret\",\"udid\":\"udid\",\"email\":\"user@test.com\"}},\"query\":\"mutation Login\",\"operationName\":\"Login\"}";
        assertEquals("user@test.com", JSONUtils.extractValueForKey(payload, "email"));
    }

    @Test
    public void valueAtEndOfObject() {
        assertEquals("last", JSONUtils.extractValueForKey("{\"a\":\"first\",\"b\":\"last\"}", "b"));
    }

    @Test
    public void graphqlMutationWithNestedVariables() {
        String payload = "{\"variables\":{\"input\":{\"password\":\"pass123\",\"udid\":\"device1\",\"email\":\"user@test.com\"}},\"query\":\"mutation Login($input: LoginInput!) {\\n  login(input: $input) {\\n    __typename\\n    ... on Session {\\n      id\\n      token\\n    }\\n    ... on EmailChallenge {\\n      id\\n      maskedEmail\\n    }\\n  }\\n}\\n\",\"operationName\":\"Login\"}";
        assertEquals("user@test.com", JSONUtils.extractValueForKey(payload, "email"));
    }

    @Test
    public void keyAppearsAsSubstringInAnotherKey() {
        // "user_email" comes before "email" — must not match substring
        String payload = "{\"user_email\":\"wrong@example.com\",\"email\":\"right@example.com\"}";
        assertEquals("right@example.com", JSONUtils.extractValueForKey(payload, "email"));
    }

    @Test
    public void keyAppearsInsideStringValue() {
        // "email" appears as part of a value, not as a key
        String payload = "{\"message\":\"please provide email address\",\"email\":\"actual@example.com\"}";
        assertEquals("actual@example.com", JSONUtils.extractValueForKey(payload, "email"));
    }

    @Test
    public void valueContainsColonAndQuotes() {
        String payload = "{\"email\":\"user+tag@example.com\",\"note\":\"key:value\"}";
        assertEquals("user+tag@example.com", JSONUtils.extractValueForKey(payload, "email"));
    }

    @Test
    public void valueContainsSpecialChars() {
        String payload = "{\"email\":\"o'brien&sons@example.com\"}";
        assertEquals("o'brien&sons@example.com", JSONUtils.extractValueForKey(payload, "email"));
    }

    @Test
    public void negativeNumber() {
        assertEquals("-99", JSONUtils.extractValueForKey("{\"score\":-99,\"name\":\"x\"}", "score"));
    }

    @Test
    public void nullValue() {
        assertEquals("null", JSONUtils.extractValueForKey("{\"email\":null,\"name\":\"x\"}", "email"));
    }

    @Test
    public void massiveNestedPayloadKeyAtEnd() {
        // Simulate a large payload where the target key is buried at the very end
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < 100; i++) {
            sb.append("\"field_").append(i).append("\":\"val_").append(i).append("\",");
        }
        sb.append("\"deep\":{\"nested\":{\"email\":\"deep@example.com\"}}}");
        assertEquals("deep@example.com", JSONUtils.extractValueForKey(sb.toString(), "email"));
    }

    @Test
    public void graphqlWithIntrospectionQuery() {
        // query string contains "email" as part of field names — must not confuse with the actual key
        String payload = "{\"variables\":{\"userId\":\"123\",\"email\":\"target@test.com\"},\"query\":\"query GetUser { user { email emailVerified primaryEmail } }\"}";
        assertEquals("target@test.com", JSONUtils.extractValueForKey(payload, "email"));
    }

    @Test
    public void unicodeInValue() {
        String payload = "{\"username\":\"\u00fc\u00e9\u00e7\u00e5\u00f1@example.com\"}";
        assertEquals("\u00fc\u00e9\u00e7\u00e5\u00f1@example.com", JSONUtils.extractValueForKey(payload, "username"));
    }

    @Test
    public void emptyStringValue() {
        assertEquals("", JSONUtils.extractValueForKey("{\"email\":\"\",\"name\":\"x\"}", "email"));
    }

    @Test
    public void multipleSpacesAfterColon() {
        assertEquals("val", JSONUtils.extractValueForKey("{\"key\":   \"val\"}", "key"));
    }

    @Test
    public void urlEncodedValueInsideJson() {
        String payload = "{\"redirect\":\"https://evil.com/steal?email=fake\",\"email\":\"real@test.com\"}";
        assertEquals("real@test.com", JSONUtils.extractValueForKey(payload, "email"));
    }

    @Test
    public void nullKey() {
        assertNull(JSONUtils.extractValueForKey("{\"email\":\"test@test.com\"}", null));
    }

    @Test
    public void payloadIsJustAValue() {
        assertNull(JSONUtils.extractValueForKey("\"just a string\"", "email"));
    }

    @Test
    public void floatingPointValue() {
        assertEquals("3.14", JSONUtils.extractValueForKey("{\"pi\":3.14,\"e\":2.71}", "pi"));
    }
}
