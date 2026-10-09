package com.akto.dto;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestCustomAuthType {

    private static CustomAuthType withHeaderKeys(String... keys) {
        CustomAuthType customAuthType = new CustomAuthType();
        customAuthType.setHeaderKeys(Arrays.asList(keys));
        return customAuthType;
    }

    @Test
    public void testNormalizeKeys() {
        Set<String> normalized = CustomAuthType.normalizeKeys(Arrays.asList(" X-Auth-Token ", "COOKIE"));
        assertEquals(new java.util.HashSet<>(Arrays.asList("x-auth-token", "cookie")), normalized);
    }

    @Test
    public void testMatchesIgnoringCaseAndWhitespace() {
        CustomAuthType customAuthType = withHeaderKeys(" X-Auth-Token", "SessionId ");
        Set<String> present = CustomAuthType.normalizeKeys(Arrays.asList("x-auth-token", "sessionid", "host"));
        assertTrue(customAuthType.hasAllHeaderKeysIn(present));
    }

    @Test
    public void testDoesNotMatchWhenKeyMissing() {
        CustomAuthType customAuthType = withHeaderKeys("x-auth-token", "x-csrf");
        Set<String> present = CustomAuthType.normalizeKeys(Collections.singletonList("X-Auth-Token"));
        assertFalse(customAuthType.hasAllHeaderKeysIn(present));
    }

    @Test
    public void testEmptyOrNullKeysNeverMatch() {
        Set<String> present = CustomAuthType.normalizeKeys(Collections.singletonList("x-auth-token"));
        assertFalse(withHeaderKeys().hasAllHeaderKeysIn(present));
        assertFalse(new CustomAuthType().hasAllHeaderKeysIn(present));
        assertFalse(withHeaderKeys("x-auth-token").hasAllHeaderKeysIn(Collections.emptySet()));
    }
}
